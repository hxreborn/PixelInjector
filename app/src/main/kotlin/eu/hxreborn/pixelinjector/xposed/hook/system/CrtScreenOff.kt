package eu.hxreborn.pixelinjector.xposed.hook.system

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.provider.Settings
import android.util.Pair
import android.util.SparseArray
import android.view.Display
import android.view.SurfaceControl
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.optionalField
import eu.hxreborn.pixelinjector.util.orNullIfMissing
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.ceil

private const val TWEAK = "CrtScreenOff"
private const val COLOR_FADE = "com.android.server.display.ColorFade"
private const val LIGHT_REVEAL_SCRIM = "com.android.systemui.statusbar.LightRevealScrim"
private const val KEYGUARD_LIFECYCLES = "com.android.systemui.keyguard.KeyguardLifecyclesDispatcher"
private const val SYSTEMUI_DEPENDENCY = "com.android.systemui.Dependency"
private const val SYSTEMUI_DUMP = "com.android.systemui.dump"
private const val DOZE_PARAMETERS = "com.android.systemui.statusbar.phone.DozeParameters"
private const val SCREEN_TURNING_ON = 0
private const val SCREEN_TURNED_OFF = 3
private const val STARTED_WAKING_UP = 4
private const val STARTED_GOING_TO_SLEEP = 6
private const val DOZE_ALWAYS_ON = "doze_always_on"
private const val FULL_REVEAL = 1f
private const val NATURAL_SURFACE_LAYOUT = "$COLOR_FADE\$NaturalSurfaceLayout"
private const val DISPLAY_MANAGER_INTERNAL = "android.hardware.display.DisplayManagerInternal"
private const val DISPLAY_INFO = "android.view.DisplayInfo"
private const val DISPLAY_MANAGER_SERVICE = "com.android.server.display.DisplayManagerService"
private const val DISPLAY_MANAGER_LOCAL_SERVICE = "$DISPLAY_MANAGER_SERVICE\$LocalService"
private val DISPLAY_POWER_CONTROLLERS =
    listOf(
        "com.android.server.display.DisplayPowerController",
        "com.android.server.display.DisplayPowerController2",
    )
private const val STATE_CONTROLLER = "com.android.server.display.state.DisplayStateController"
private const val PROXIMITY_CONTROLLER =
    "com.android.server.display.DisplayPowerProximityStateController"
private const val POWER_REQUEST =
    "android.hardware.display.DisplayManagerInternal\$DisplayPowerRequest"
private const val OPLUS_SECONDARY_DISPLAY_ARG = 5
private const val POLICY_DOZE = 1
private const val POLICY_DIM = 2
private const val POLICY_BRIGHT = 3
private const val MODE_COOL_DOWN = 1
private const val MODE_FADE = 2
private const val COLOR_FADE_LAYER = 0x40000001
private const val CRT_DURATION_MS = 500L
private const val TYPE_NOTIFICATION_SHADE = 2040
private const val DENSITY_DEFAULT_DPI = 160f
private const val PIXEL_CENTER = 0.5f

private const val VERTICAL_PHASE_END = 0.5f
private const val HORIZONTAL_OVERSCAN_FRACTION = 0.03f
private const val MAX_FRINGE_ALPHA = 0.22f
private const val MAX_TRAIL_ALPHA = 0.30f
private const val MIN_GLOW_HALF_HEIGHT_PX = 4f
private const val GLOW_HALF_HEIGHT_DP = 5f
private const val FRINGE_OFFSET_DP = 2f

private val lifecycleLock = Any()
private var pendingHookRegistration: (() -> Unit)? = null
private var registeringHooks = false
private var preparingSurface = false
private var holdingReveal = false

@Volatile private var reloading = false

@Volatile private var enabledHooksInstalled = false

private val switch =
    Switch(Prefs.CRT_SCREEN_OFF) { enabled ->
        if (enabled) installEnabledHooks()
    }

internal val crtScreenOff =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEM, Target.SYSTEMUI),
        prefs = listOf(switch),
        saveState = ::prepareForReload,
        restoreBeforeInstall = ::restoreAwakeDisplayStates,
        onReloadRejected = ::resumeAfterRejectedReload,
        install = XposedModule::installCrtScreenOff,
    )

private fun prepareForReload(): Map<Any, Any> =
    synchronized(lifecycleLock) {
        check(!registeringHooks) { "CRT hook registration in progress" }
        check(!preparingSurface && crtSurfaces.isEmpty()) { "CRT surface in use" }
        check(!holdingReveal) { "CRT reveal hold in progress" }
        reloading = true
        awakeDisplayStates
    }

private fun resumeAfterRejectedReload() {
    synchronized(lifecycleLock) { reloading = false }
    installEnabledHooks()
}

private fun installHooksOnEnable(register: () -> Unit) {
    synchronized(lifecycleLock) { pendingHookRegistration = register }
    installEnabledHooks()
}

private fun installEnabledHooks() {
    val register =
        synchronized(lifecycleLock) {
            if (reloading || !switch.enabled) return
            val register = pendingHookRegistration ?: return
            pendingHookRegistration = null
            registeringHooks = true
            register
        }
    try {
        register()
        enabledHooksInstalled = true
    } finally {
        synchronized(lifecycleLock) { registeringHooks = false }
    }
}

private class ColorFadeBindings(
    val mode: Field,
    val prepared: Field,
    val displayId: Field,
    val displayManagerInternal: Field,
    val displayLayerStack: Field,
    val displayWidth: Field,
    val displayHeight: Field,
    val surfaceControl: Field,
    val surfaceLayout: Field,
    val surfaceVisible: Field,
    val surfaceAlpha: Field,
    val transaction: Field,
    val dismiss: Method,
    val destroy: Method?,
    val naturalSurfaceLayout: Constructor<*>,
    val onDisplayTransaction: Method,
    val disposeSurfaceLayout: Method,
) {
    fun newSurfaceLayout(
        colorFade: Any,
        displayManagerInternal: Any,
        displayId: Int,
        container: SurfaceControl,
    ): Any =
        if (naturalSurfaceLayout.parameterCount == 4) {
            naturalSurfaceLayout.newInstance(
                colorFade,
                displayManagerInternal,
                displayId,
                container,
            )
        } else {
            naturalSurfaceLayout.newInstance(displayManagerInternal, displayId, container)
        }
}

private class DisplayInfoBindings(
    val getDisplayInfo: Method,
    val layerStack: Field,
    val logicalDensityDpi: Field,
    val naturalWidth: Method,
    val naturalHeight: Method,
)

private class SurfaceBindings(
    val setContainerLayer: Method,
    val setColorLayer: Method,
    val setColor: Method,
    val setLayerStack: Method,
    val setWindowCrop: Method,
)

private class OffAnimatorBindings(
    val displayManagerService: Field,
    val syncRoot: Field,
    val displayPowerControllers: Field,
    val colorFadeOffAnimators: List<Field>,
)

private class StateControllerBindings(
    val performScreenOffTransition: Field,
    val skipScreenOffTransition: Field?,
    val policy: Field,
    val requestedDozeState: Field,
    val overrideDozeState: Field?,
    val proximityController: Field,
    val displayId: Field,
    val screenOffBecauseOfProximity: Method,
    val resultState: Field?,
    val resultIsState: Boolean,
) {
    fun displayState(result: Any): Int =
        when {
            resultIsState -> result as Int
            resultState != null -> resultState.getInt(result)
            else -> (result as Pair<*, *>).first as Int
        }

    fun effectiveDozeState(
        controller: Any,
        request: Any,
    ): Int =
        overrideDozeState
            ?.getInt(controller)
            ?.takeUnless { it == Display.STATE_UNKNOWN }
            ?: requestedDozeState.getInt(request)

    fun isDefaultDisplay(controller: Any): Boolean =
        displayId.getInt(proximityController.get(controller)) == Display.DEFAULT_DISPLAY

    fun isScreenOffBecauseOfProximity(controller: Any): Boolean =
        screenOffBecauseOfProximity.invoke(proximityController.get(controller)) == true
}

private class CrtLayer(
    val red: Float,
    val green: Float,
    val blue: Float,
) {
    val rgb get() = floatArrayOf(red, green, blue)

    var left = 0f
    var top = 0f
    var right = 0f
    var bottom = 0f
    var alpha = 0f

    fun set(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        alpha: Float,
    ) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
        this.alpha = alpha
    }
}

private class CrtCollapseFrame {
    val maskTop = CrtLayer(0f, 0f, 0f)
    val maskBottom = CrtLayer(0f, 0f, 0f)
    val maskLeft = CrtLayer(0f, 0f, 0f)
    val maskRight = CrtLayer(0f, 0f, 0f)
    val trail = CrtLayer(1f, 1f, 1f)
    val redFringe = CrtLayer(1f, 0f, 0f)
    val blueFringe = CrtLayer(0f, 0f, 1f)
    val core = CrtLayer(1f, 1f, 1f)
    val layers =
        listOf(maskTop, maskBottom, maskLeft, maskRight, trail, redFringe, blueFringe, core)

    fun update(
        level: Float,
        width: Float,
        height: Float,
        density: Float,
    ) {
        layers.forEach { it.set(0f, 0f, 0f, 0f, 0f) }
        val amount = level.coerceIn(0f, 1f)
        if (amount >= 1f) return
        val progress = 1f - amount
        val vertical = (progress / VERTICAL_PHASE_END).coerceIn(0f, 1f)
        val beam = ((progress - VERTICAL_PHASE_END) / (1f - VERTICAL_PHASE_END)).coerceIn(0f, 1f)
        val centerX = width / 2f
        val centerY = height / 2f
        val beamHalfHeight = maxOf(1f, density)
        val halfApertureHeight = maxOf(beamHalfHeight, centerY * (1f - vertical * vertical))
        val halfApertureWidth =
            (centerX + width * HORIZONTAL_OVERSCAN_FRACTION) * (1f - beam * beam)
        val apertureLeft = centerX - halfApertureWidth
        val apertureTop = centerY - halfApertureHeight
        val apertureRight = centerX + halfApertureWidth
        val apertureBottom = centerY + halfApertureHeight
        maskTop.set(0f, 0f, width, apertureTop, 1f)
        maskBottom.set(0f, apertureBottom, width, height, 1f)
        if (apertureLeft > 0f) maskLeft.set(0f, apertureTop, apertureLeft, apertureBottom, 1f)
        if (apertureRight < width) {
            maskRight.set(apertureRight, apertureTop, width, apertureBottom, 1f)
        }
        val emission = if (beam == 0f || beam == 1f) 0f else 4f * beam * (1f - beam)
        if (emission > 0f) {
            setBeam(apertureLeft, apertureRight, centerY, beamHalfHeight, density, emission)
        }
    }

    private fun setBeam(
        left: Float,
        right: Float,
        centerY: Float,
        beamHalfHeight: Float,
        density: Float,
        emission: Float,
    ) {
        val glowHalfHeight = maxOf(MIN_GLOW_HALF_HEIGHT_PX, GLOW_HALF_HEIGHT_DP * density)
        val fringeOffset = maxOf(1f, FRINGE_OFFSET_DP * density)
        val coreTop = centerY - beamHalfHeight
        val coreBottom = centerY + beamHalfHeight
        trail.set(
            left,
            centerY - glowHalfHeight,
            right,
            centerY + glowHalfHeight,
            emission * MAX_TRAIL_ALPHA,
        )
        redFringe.set(left, coreTop - fringeOffset, right, coreTop, emission * MAX_FRINGE_ALPHA)
        blueFringe.set(
            left,
            coreBottom,
            right,
            coreBottom + fringeOffset,
            emission * MAX_FRINGE_ALPHA,
        )
        core.set(left, coreTop, right, coreBottom, emission)
    }
}

private class CrtSurface(
    val container: SurfaceControl,
    private val layers: List<SurfaceControl>,
    private val width: Float,
    private val height: Float,
    private val density: Float,
    val animator: ObjectAnimator,
) {
    private val stockDuration = animator.duration
    private val stockInterpolator = animator.interpolator
    private val frame = CrtCollapseFrame()
    private val crop = Rect()

    var drawFailed = false

    fun draw(
        level: Float,
        transaction: SurfaceControl.Transaction,
    ) {
        frame.update(level, width, height, density)
        frame.layers.forEachIndexed { index, layer ->
            val surface = layers[index]
            crop.set(
                pixelEdge(layer.left),
                pixelEdge(layer.top),
                pixelEdge(layer.right),
                pixelEdge(layer.bottom),
            )
            val hasPixels = !crop.isEmpty
            transaction.setAlpha(surface, if (hasPixels) layer.alpha else 0f)
            if (hasPixels) transaction.setCrop(surface, crop)
        }
        showContainer(transaction)
    }

    fun showBlack(setColor: Method) {
        val layer = layers.first { it.isValid }
        crop.set(0, 0, width.toInt(), height.toInt())
        SurfaceControl.Transaction().use { transaction ->
            setColor.invoke(transaction, layer, frame.maskTop.rgb)
            transaction
                .setLayer(layer, layers.size)
                .setCrop(layer, crop)
                .setAlpha(layer, 1f)
                .setVisibility(layer, true)
            showContainer(transaction)
        }
    }

    fun restoreAnimator() {
        animator.duration = stockDuration
        animator.interpolator = stockInterpolator
    }

    fun release() {
        layers.forEach(SurfaceControl::release)
    }

    private fun showContainer(transaction: SurfaceControl.Transaction) {
        transaction
            .setLayer(container, COLOR_FADE_LAYER)
            .setAlpha(container, 1f)
            .setVisibility(container, true)
            .apply()
    }
}

private fun pixelEdge(edge: Float): Int = ceil(edge - PIXEL_CENTER).toInt()

private val crtSurfaces: MutableMap<Any, CrtSurface> = Collections.synchronizedMap(WeakHashMap())

private var awakeDisplayStates: MutableMap<Any, Any> =
    Collections.synchronizedMap(WeakHashMap())

@Suppress("UNCHECKED_CAST")
private fun restoreAwakeDisplayStates(saved: Any?) {
    if (saved is MutableMap<*, *>) awakeDisplayStates = saved as MutableMap<Any, Any>
}

private fun XposedModule.installCrtScreenOff(cl: ClassLoader): Boolean {
    val fade = resolving { cl.loadClass(COLOR_FADE) }.getOrNull() ?: return installRevealHold(cl)
    val draw =
        resolving { fade.requiredMethod("draw", Float::class.javaPrimitiveType) }
            .getOrElse { return missing("ColorFade#draw(float)", it) }
    val prepare =
        resolving {
            fade.requiredMethod("prepare", Context::class.java, Int::class.javaPrimitiveType)
        }.getOrElse { return missing("ColorFade#prepare(Context,int)", it) }
    val fadeBindings =
        resolving {
            val layout = cl.loadClass(NATURAL_SURFACE_LAYOUT)
            ColorFadeBindings(
                mode = fade.requiredField("mMode"),
                prepared = fade.requiredField("mPrepared"),
                displayId = fade.requiredField("mDisplayId"),
                displayManagerInternal = fade.requiredField("mDisplayManagerInternal"),
                displayLayerStack = fade.requiredField("mDisplayLayerStack"),
                displayWidth = fade.requiredField("mDisplayWidth"),
                displayHeight = fade.requiredField("mDisplayHeight"),
                surfaceControl = fade.requiredField("mSurfaceControl"),
                surfaceLayout = fade.requiredField("mSurfaceLayout"),
                surfaceVisible = fade.requiredField("mSurfaceVisible"),
                surfaceAlpha = fade.requiredField("mSurfaceAlpha"),
                transaction = fade.requiredField("mTransaction"),
                dismiss = fade.requiredMethod("dismiss"),
                destroy = orNullIfMissing { fade.requiredMethod("destroy") },
                naturalSurfaceLayout =
                    layout.declaredConstructors
                        .single { it.parameterTypes.last() == SurfaceControl::class.java }
                        .apply { isAccessible = true },
                onDisplayTransaction =
                    layout.requiredMethod(
                        "onDisplayTransaction",
                        SurfaceControl.Transaction::class.java,
                    ),
                disposeSurfaceLayout = layout.requiredMethod("dispose"),
            )
        }.getOrElse { return missing("ColorFade surface members", it) }
    val displayInfoBindings =
        resolving {
            val info = cl.loadClass(DISPLAY_INFO)
            DisplayInfoBindings(
                getDisplayInfo =
                    cl
                        .loadClass(DISPLAY_MANAGER_INTERNAL)
                        .requiredMethod("getDisplayInfo", Int::class.javaPrimitiveType),
                layerStack = info.requiredField("layerStack"),
                logicalDensityDpi = info.requiredField("logicalDensityDpi"),
                naturalWidth = info.requiredMethod("getNaturalWidth"),
                naturalHeight = info.requiredMethod("getNaturalHeight"),
            )
        }.getOrElse { return missing("DisplayInfo", it) }
    val surfaceBindings =
        resolving {
            val builder = SurfaceControl.Builder::class.java
            val transaction = SurfaceControl.Transaction::class.java
            val surface = SurfaceControl::class.java
            val int = Int::class.javaPrimitiveType
            SurfaceBindings(
                setContainerLayer = builder.requiredMethod("setContainerLayer"),
                setColorLayer = builder.requiredMethod("setColorLayer"),
                setColor = transaction.requiredMethod("setColor", surface, FloatArray::class.java),
                setLayerStack = transaction.requiredMethod("setLayerStack", surface, int),
                setWindowCrop = transaction.requiredMethod("setWindowCrop", surface, int, int),
            )
        }.getOrElse { return missing("SurfaceControl colour layer members", it) }
    val animatorBindings =
        resolving {
            OffAnimatorBindings(
                displayManagerService =
                    cl.loadClass(DISPLAY_MANAGER_LOCAL_SERVICE).requiredField("this\$0"),
                syncRoot = cl.loadClass(DISPLAY_MANAGER_SERVICE).requiredField("mSyncRoot"),
                displayPowerControllers =
                    cl.loadClass(DISPLAY_MANAGER_SERVICE).requiredField("mDisplayPowerControllers"),
                colorFadeOffAnimators =
                    DISPLAY_POWER_CONTROLLERS
                        .mapNotNull { name ->
                            try {
                                cl.loadClass(name).optionalField("mColorFadeOffAnimator")
                            } catch (_: ClassNotFoundException) {
                                null
                            }
                        }.ifEmpty { throw NoSuchFieldException("mColorFadeOffAnimator") },
            )
        }.getOrElse { return missing("DisplayPowerController#mColorFadeOffAnimator", it) }
    val stateController =
        resolving { cl.loadClass(STATE_CONTROLLER) }
            .getOrElse { return missing("DisplayStateController", it) }
    val updateDisplayState =
        resolving {
            val request = cl.loadClass(POWER_REQUEST)
            val flag = Boolean::class.javaPrimitiveType
            val int = Int::class.javaPrimitiveType
            // OxygenOS 16 services.jar BP2A.250605.015
            orNullIfMissing {
                stateController.requiredMethod(
                    "updateDisplayState",
                    request,
                    flag,
                    flag,
                    int,
                    int,
                    flag,
                    String::class.java,
                    flag,
                )
            } ?: stateController.requiredMethod("updateDisplayState", request, flag, flag)
        }.getOrElse { return missing("DisplayStateController#updateDisplayState", it) }
    val stateBindings =
        resolving {
            val resultType = updateDisplayState.returnType
            val resultIsState = resultType == Int::class.javaPrimitiveType
            val proximity = cl.loadClass(PROXIMITY_CONTROLLER)
            StateControllerBindings(
                stateController.requiredField("mPerformScreenOffTransition"),
                stateController.optionalField("mShouldSkipScreenOffTransition"),
                cl.loadClass(POWER_REQUEST).requiredField("policy"),
                cl.loadClass(POWER_REQUEST).requiredField("dozeScreenState"),
                stateController.optionalField("mDozeStateOverride"),
                stateController.requiredField("mDisplayPowerProximityStateController"),
                proximity.requiredField("mDisplayId"),
                proximity.requiredMethod("isScreenOffBecauseOfProximity"),
                resultType
                    .takeUnless { resultIsState || it == Pair::class.java }
                    ?.requiredField("state"),
                resultIsState,
            )
        }.getOrElse { return missing("DisplayStateController#mPerformScreenOffTransition", it) }
    val crt = CrtColorFade(fadeBindings, displayInfoBindings, surfaceBindings, animatorBindings)
    Logger.info(
        "resolved tweak=$TWEAK members=${draw.signature()},${prepare.signature()}," +
            "${fadeBindings.dismiss.signature()},${updateDisplayState.signature()}",
    )
    hook(updateDisplayState).intercept { chain ->
        val result = chain.proceed()
        val enabled = switch.enabled && enabledHooksInstalled
        guarded(result) {
            val controller = chain.thisObject!!
            if (!stateBindings.isDefaultDisplay(controller)) return@guarded result
            val request = chain.getArg(0)!!
            val policy = stateBindings.policy.getInt(request)
            val state = stateBindings.displayState(result!!)
            when (policy) {
                POLICY_DIM, POLICY_BRIGHT -> {
                    if (state == Display.STATE_ON) {
                        awakeDisplayStates[controller] = result
                    } else {
                        awakeDisplayStates.remove(controller)
                    }
                }

                POLICY_DOZE -> {
                    val awakeResult = awakeDisplayStates[controller] ?: return@guarded result
                    if (!enabled) {
                        awakeDisplayStates.remove(controller)
                        return@guarded result
                    }
                    val skip = stateBindings.skipScreenOffTransition?.getBoolean(controller) == true
                    if (!skip && state == Display.STATE_ON) return@guarded result
                    val dozeState = stateBindings.effectiveDozeState(controller, request)
                    if (!skip && state == Display.STATE_DOZE &&
                        dozeState == Display.STATE_UNKNOWN
                    ) {
                        return@guarded if (reloading) result else awakeResult
                    }
                    awakeDisplayStates.remove(controller)
                    val isDisplayEnabled = chain.getArg(1) as Boolean
                    val isDisplayInTransition = chain.getArg(2) as Boolean
                    val isSecondaryDisplayEnabled =
                        chain.args.getOrNull(OPLUS_SECONDARY_DISPLAY_ARG) == true
                    if (!reloading && !skip && state == Display.STATE_OFF &&
                        dozeState == Display.STATE_OFF &&
                        isDisplayEnabled && !isDisplayInTransition && !isSecondaryDisplayEnabled &&
                        !stateBindings.isScreenOffBecauseOfProximity(controller)
                    ) {
                        stateBindings.performScreenOffTransition.setBoolean(controller, true)
                    }
                }

                else -> {
                    awakeDisplayStates.remove(controller)
                }
            }
            result
        }
    }
    installHooksOnEnable {
        hook(draw).intercept { chain ->
            val colorFade = chain.thisObject!!
            val surface = crt.ownedSurface(colorFade) ?: return@intercept chain.proceed()
            crt.draw(colorFade, surface, chain.getArg(0) as Float)
        }
        hook(fadeBindings.dismiss).intercept { chain ->
            val colorFade = chain.thisObject!!
            val surface = crt.ownedSurface(colorFade) ?: return@intercept chain.proceed()
            guarded(Unit) { if (surface.animator.isStarted) surface.animator.cancel() }
            try {
                chain.proceed()
            } finally {
                crt.releaseAfterDismiss(colorFade)
            }
        }
        fadeBindings.destroy?.let { destroy ->
            hook(destroy).intercept { chain ->
                val colorFade = chain.thisObject!!
                try {
                    chain.proceed()
                } finally {
                    crtSurfaces.remove(colorFade)?.let { guarded(Unit) { it.release() } }
                }
            }
        }
        hook(prepare).intercept { chain ->
            val colorFade = chain.thisObject!!
            val mode = chain.getArg(1) as Int
            val prepared =
                switch.enabled && (mode == MODE_COOL_DOWN || mode == MODE_FADE) &&
                    crt.prepare(colorFade, mode)
            if (prepared) return@intercept true
            crt.restoreStockFade(colorFade)
            chain.proceed()
        }
    }
    return true
}

private class CrtColorFade(
    private val fade: ColorFadeBindings,
    private val info: DisplayInfoBindings,
    private val surfaces: SurfaceBindings,
    private val animator: OffAnimatorBindings,
) {
    fun ownedSurface(colorFade: Any): CrtSurface? =
        crtSurfaces[colorFade]?.takeIf {
            guarded(true) { fade.surfaceControl.get(colorFade) === it.container }
        }

    fun prepare(
        colorFade: Any,
        mode: Int,
    ): Boolean =
        guarded(false) {
            if (fade.displayId.getInt(colorFade) != Display.DEFAULT_DISPLAY) return@guarded false
            synchronized(lifecycleLock) {
                if (reloading || preparingSurface) return@guarded false
                preparingSurface = true
            }
            try {
                if (ownedSurface(colorFade) != null) return@guarded true
                if (fade.surfaceControl.get(colorFade) != null) fade.dismiss.invoke(colorFade)
                releaseAfterDismiss(colorFade)
                val offAnimator = offAnimator(colorFade) ?: return@guarded false
                createSurface(colorFade, mode, offAnimator)
            } finally {
                synchronized(lifecycleLock) { preparingSurface = false }
            }
        }

    fun restoreStockFade(colorFade: Any) {
        guarded(Unit) {
            if (ownedSurface(colorFade) != null) fade.dismiss.invoke(colorFade)
            releaseAfterDismiss(colorFade)
        }
    }

    fun releaseAfterDismiss(colorFade: Any) {
        val surface = crtSurfaces[colorFade] ?: return
        guarded(Unit) { surface.restoreAnimator() }
        guarded(Unit) { surface.release() }
        crtSurfaces.remove(colorFade)
    }

    fun draw(
        colorFade: Any,
        surface: CrtSurface,
        level: Float,
    ): Boolean {
        if (surface.drawFailed) return false
        val drawn =
            guarded(false) {
                if (!fade.prepared.getBoolean(colorFade)) return@guarded false
                surface.draw(level, fade.transaction.get(colorFade) as SurfaceControl.Transaction)
                fade.surfaceVisible.setBoolean(colorFade, true)
                fade.surfaceAlpha.setFloat(colorFade, 1f)
                true
            }
        if (!drawn) {
            surface.drawFailed = true
            guarded(Unit) { surface.showBlack(surfaces.setColor) }
            guarded(Unit) { if (surface.animator.isStarted) surface.animator.end() }
        }
        return drawn
    }

    private fun createSurface(
        colorFade: Any,
        mode: Int,
        offAnimator: ObjectAnimator,
    ): Boolean {
        val displayManagerInternal = fade.displayManagerInternal.get(colorFade)!!
        val displayId = fade.displayId.getInt(colorFade)
        val displayInfo =
            info.getDisplayInfo.invoke(displayManagerInternal, displayId) ?: return false
        val layerStack = info.layerStack.getInt(displayInfo)
        val width = info.naturalWidth.invoke(displayInfo) as Int
        val height = info.naturalHeight.invoke(displayInfo) as Int
        val density = info.logicalDensityDpi.getInt(displayInfo) / DENSITY_DEFAULT_DPI
        val created = ArrayList<SurfaceControl>()
        var surfaceLayout: Any? = null
        var surfacePrepared = false
        try {
            val container =
                SurfaceControl
                    .Builder()
                    .setName("ColorFade CRT")
                    .also { surfaces.setContainerLayer.invoke(it) }
                    .build()
                    .also(created::add)
            SurfaceControl.Transaction().use { transaction ->
                CrtCollapseFrame().layers.forEachIndexed { index, layer ->
                    val surface =
                        SurfaceControl
                            .Builder()
                            .setName("ColorFade CRT layer")
                            .also { surfaces.setColorLayer.invoke(it) }
                            .setParent(container)
                            .build()
                            .also(created::add)
                    surfaces.setColor.invoke(transaction, surface, layer.rgb)
                    transaction
                        .setLayer(surface, index)
                        .setAlpha(surface, 0f)
                        .setVisibility(surface, true)
                }
                surfaces.setLayerStack.invoke(transaction, container, layerStack)
                surfaces.setWindowCrop.invoke(transaction, container, width, height)
                val layout =
                    fade.newSurfaceLayout(colorFade, displayManagerInternal, displayId, container)
                surfaceLayout = layout
                fade.onDisplayTransaction.invoke(layout, transaction)
                transaction.apply()
            }
            fade.mode.setInt(colorFade, mode)
            fade.displayLayerStack.setInt(colorFade, layerStack)
            fade.displayWidth.setInt(colorFade, width)
            fade.displayHeight.setInt(colorFade, height)
            fade.surfaceLayout.set(colorFade, surfaceLayout)
            fade.surfaceControl.set(colorFade, container)
            fade.prepared.setBoolean(colorFade, true)
            crtSurfaces[colorFade] =
                CrtSurface(
                    container,
                    created.drop(1),
                    width.toFloat(),
                    height.toFloat(),
                    density,
                    offAnimator,
                )
            offAnimator.duration = CRT_DURATION_MS
            offAnimator.interpolator = LinearInterpolator()
            surfacePrepared = true
            return true
        } finally {
            if (!surfacePrepared) {
                crtSurfaces.remove(colorFade)?.let { guarded(Unit) { it.restoreAnimator() } }
                surfaceLayout?.let { layout ->
                    guarded(Unit) { fade.disposeSurfaceLayout.invoke(layout) }
                    guarded(Unit) {
                        fade.surfaceLayout.set(colorFade, null)
                        fade.surfaceControl.set(colorFade, null)
                        fade.prepared.setBoolean(colorFade, false)
                    }
                }
                created.forEach(SurfaceControl::release)
            }
        }
    }

    private fun offAnimator(colorFade: Any): ObjectAnimator? {
        val service = animator.displayManagerService.get(fade.displayManagerInternal.get(colorFade))
        val controller =
            synchronized(animator.syncRoot.get(service)!!) {
                (animator.displayPowerControllers.get(service) as SparseArray<*>)
                    .get(fade.displayId.getInt(colorFade))
            } ?: return null
        return animator.colorFadeOffAnimators
            .firstOrNull { it.declaringClass.isInstance(controller) }
            ?.get(controller) as? ObjectAnimator
    }
}

private fun XposedModule.installRevealHold(cl: ClassLoader): Boolean {
    val setRevealAmount =
        resolving {
            cl
                .loadClass(LIGHT_REVEAL_SCRIM)
                .requiredMethod("setRevealAmount", Float::class.javaPrimitiveType)
        }.getOrElse { return missing("LightRevealScrim#setRevealAmount(float)", it) }
    val handleLifecycleMessage =
        resolving {
            val handler =
                orNullIfMissing { cl.loadClass("$KEYGUARD_LIFECYCLES\$KeyguardLifecycleHandler") }
                    ?: cl.loadClass("$KEYGUARD_LIFECYCLES\$1")
            handler.requiredMethod("handleMessage", Message::class.java)
        }.getOrElse { return missing("KeyguardLifecyclesDispatcher handler", it) }
    val alwaysOnDisplay =
        resolving { bindAlwaysOnDisplay(cl) }.getOrElse {
            Logger.warn(
                "AOD lookup failed tweak=$TWEAK member=DozeParameters build=${Build.ID} " +
                    "reason=${it.reason()}",
            )
            null
        }
    val revealHold = ScreenOffRevealHold(setRevealAmount, alwaysOnDisplay)
    Logger.info(
        "resolved tweak=$TWEAK members=${setRevealAmount.signature()}," +
            handleLifecycleMessage.signature(),
    )
    installHooksOnEnable {
        hook(setRevealAmount).intercept { chain ->
            val scrim = chain.thisObject as View
            val amount = chain.getArg(0) as Float
            if (revealHold.hold(scrim, amount)) {
                return@intercept chain.proceed(arrayOf<Any?>(FULL_REVEAL))
            }
            chain.proceed()
        }
        hook(handleLifecycleMessage).intercept { chain ->
            val message = chain.getArg(0) as Message
            guarded(Unit) { revealHold.onLifecycleMessage(message.what) }
            chain.proceed()
        }
    }
    return true
}

private class AlwaysOnDisplayBindings(
    val dependency: Field,
    val dumpManager: Field,
    val dumpables: Field,
    val dumpable: Field,
    val getAlwaysOn: Method,
) {
    fun isEnabled(): Boolean? {
        val systemUiDependency = dependency.get(null) ?: return null
        val manager = dumpManager.get(systemUiDependency) ?: return null
        val parameters =
            synchronized(manager) {
                val entries = dumpables.get(manager) as Map<*, *>
                entries["DozeParameters"]?.let { dumpable.get(it) }
            } ?: return null
        return getAlwaysOn.invoke(parameters) as Boolean
    }
}

private fun bindAlwaysOnDisplay(cl: ClassLoader): AlwaysOnDisplayBindings {
    val dependency = cl.loadClass(SYSTEMUI_DEPENDENCY)
    val dumpManager =
        orNullIfMissing { cl.loadClass("$SYSTEMUI_DUMP.DumpManagerImpl") }
            ?: cl.loadClass("$SYSTEMUI_DUMP.DumpManager")
    val entry =
        orNullIfMissing { cl.loadClass("$SYSTEMUI_DUMP.DumpsysEntry\$DumpableEntry") }
            ?: cl.loadClass("$SYSTEMUI_DUMP.RegisteredDumpable")
    return AlwaysOnDisplayBindings(
        dependency.requiredField("sDependency"),
        dependency.requiredField("mDumpManager"),
        dumpManager.requiredField("dumpables"),
        entry.requiredField("dumpable"),
        cl.loadClass(DOZE_PARAMETERS).requiredMethod("getAlwaysOn"),
    )
}

private class ScreenOffRevealHold(
    private val setRevealAmount: Method,
    private val alwaysOnDisplay: AlwaysOnDisplayBindings?,
) {
    private val requestedAmounts = WeakHashMap<View, Float>()

    fun onLifecycleMessage(what: Int) {
        when (what) {
            STARTED_GOING_TO_SLEEP -> {
                val alwaysOn = alwaysOnDisplay?.isEnabled() == true
                synchronized(lifecycleLock) {
                    if (!reloading && switch.enabled && !alwaysOn) holdingReveal = true
                }
            }

            SCREEN_TURNED_OFF -> {
                synchronized(lifecycleLock) {
                    holdingReveal = false
                    val amounts = requestedAmounts.toMap()
                    requestedAmounts.clear()
                    amounts.forEach { (scrim, amount) ->
                        guarded(Unit) { setRevealAmount.invoke(scrim, amount) }
                    }
                }
            }

            STARTED_WAKING_UP, SCREEN_TURNING_ON -> {
                synchronized(lifecycleLock) { holdingReveal = false }
                requestedAmounts.clear()
            }
        }
    }

    fun hold(
        scrim: View,
        amount: Float,
    ): Boolean {
        if (Looper.myLooper() !== Looper.getMainLooper()) return false
        if (switch.enabled && holdingReveal && guarded(false) { screenOffWithoutAod(scrim) }) {
            requestedAmounts[scrim] = amount
            return true
        }
        requestedAmounts.remove(scrim)
        return false
    }

    private fun screenOffWithoutAod(scrim: View): Boolean {
        val window = scrim.rootView.layoutParams as? WindowManager.LayoutParams ?: return false
        if (window.type != TYPE_NOTIFICATION_SHADE) return false
        val display = scrim.display ?: return false
        val power = scrim.context.getSystemService(PowerManager::class.java) ?: return false
        if (display.displayId != Display.DEFAULT_DISPLAY ||
            display.state != Display.STATE_ON ||
            power.isInteractive
        ) {
            return false
        }
        val aodEnabled =
            alwaysOnDisplay?.isEnabled()
                ?: (Settings.Secure.getInt(scrim.context.contentResolver, DOZE_ALWAYS_ON, 1) != 0)
        return !aodEnabled
    }
}

private inline fun <T> guarded(
    fallback: T,
    block: () -> T,
): T =
    try {
        block()
    } catch (e: Exception) {
        Logger.error("crt skipped tweak=$TWEAK reason=${e.reason()}", e)
        fallback
    } catch (e: LinkageError) {
        Logger.error("crt skipped tweak=$TWEAK reason=${e.reason()}", e)
        fallback
    }

private fun missing(
    member: String,
    cause: Throwable,
): Boolean {
    Logger.error(
        "target not found tweak=$TWEAK member=$member build=${Build.ID} reason=${cause.reason()}",
    )
    return false
}
