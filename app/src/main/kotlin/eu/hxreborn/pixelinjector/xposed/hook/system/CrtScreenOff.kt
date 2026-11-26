package eu.hxreborn.pixelinjector.xposed.hook.system

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Application
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.os.SystemClock
import android.util.Pair
import android.util.SparseArray
import android.view.Choreographer
import android.view.Display
import android.view.SurfaceControl
import android.view.View
import android.view.ViewTreeObserver
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
import eu.hxreborn.pixelinjector.xposed.hook.DexKit
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.FutureTask
import kotlin.math.ceil

private const val TWEAK = "CrtScreenOff"
private const val COLOR_FADE = "com.android.server.display.ColorFade"
private const val LIGHT_REVEAL_SCRIM = "com.android.systemui.statusbar.LightRevealScrim"
private const val REVEAL_SCRIM_BINDER =
    "com.android.systemui.keyguard.ui.binder.LightRevealScrimViewBinder"
private const val REVEAL_AMOUNT_TRACE = "light_reveal_amount "
private const val KEYGUARD_LIFECYCLES = "com.android.systemui.keyguard.KeyguardLifecyclesDispatcher"
private const val SYSTEMUI_DEPENDENCY = "com.android.systemui.Dependency"
private const val SYSTEMUI_DUMP = "com.android.systemui.dump"
private const val DOZE_PARAMETERS = "com.android.systemui.statusbar.phone.DozeParameters"
private const val SCREEN_TURNING_ON = 0
private const val SCREEN_TURNED_OFF = 3
private const val STARTED_WAKING_UP = 4
private const val STARTED_GOING_TO_SLEEP = 6
private const val FULL_REVEAL = 1f
private const val NATURAL_SURFACE_LAYOUT = "$COLOR_FADE\$NaturalSurfaceLayout"
private const val DISPLAY_MANAGER_INTERNAL = "android.hardware.display.DisplayManagerInternal"
private const val DISPLAY_INFO = "android.view.DisplayInfo"
private const val DISPLAY_MANAGER_SERVICE = "com.android.server.display.DisplayManagerService"
private const val DISPLAY_MANAGER_LOCAL_SERVICE = "$DISPLAY_MANAGER_SERVICE\$LocalService"
private const val DISPLAY_POWER_STATE = "com.android.server.display.DisplayPowerState"
private const val DISPLAY_TRANSACTION_LISTENER =
    "$DISPLAY_MANAGER_INTERNAL\$DisplayTransactionListener"
private const val CALLBACK_TRAVERSAL = 3
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
private const val RELOAD_DRAIN_GRACE_MS = 1_500L
private const val RELOAD_DRAIN_MAX_MS = 7_000L
private const val RELOAD_DRAIN_POLL_MS = 16L
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
private var pendingRevealCommits = 0
private var revealCommitGeneration = 0L
private var dozeWork = 0

@Volatile private var uiCoordination: CrtUiCoordination? = null

@Volatile internal var systemCoordination: CrtSystemCoordination? = null
internal var crtSystemHost: Any? = null
private var crtUiContext: Context? = null
private var restoreUiCoordination: (() -> Unit)? = null
internal val restoreSystemCoordinations = mutableListOf<() -> Unit>()

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

private fun prepareForReload(): Map<Any, Any> {
    reserveForReload()
    val state =
        synchronized(lifecycleLock) {
            HashMap<Any, Any>().apply {
                put("awake", awakeDisplayStates)
                crtSystemHost?.let { put("systemHost", it) }
                crtUiContext?.let { put("uiContext", it) }
            }
        }
    val oldUiCoordination = uiCoordination
    oldUiCoordination?.close()
    uiCoordination = null
    val oldSystemCoordination = systemCoordination
    oldSystemCoordination?.close()
    systemCoordination = null
    return state
}

private fun reserveForReload() {
    val animationMillis =
        (CRT_DURATION_MS * ValueAnimator.getDurationScale())
            .toLong()
            .coerceIn(0L, RELOAD_DRAIN_MAX_MS - RELOAD_DRAIN_GRACE_MS)
    val deadline = SystemClock.uptimeMillis() + animationMillis + RELOAD_DRAIN_GRACE_MS
    var loggedWait = false
    while (true) {
        val blocker =
            synchronized(lifecycleLock) {
                reloadBlocker() ?: run {
                    reloading = true
                    return
                }
            }
        val remaining = deadline - SystemClock.uptimeMillis()
        check(
            remaining > 0 && Looper.myLooper() !== Looper.getMainLooper() &&
                systemCoordination?.isDisplayThread() != true,
        ) { blocker }
        if (!loggedWait) {
            Logger.info("waiting for CRT before reload tweak=$TWEAK reason=$blocker")
            loggedWait = true
        }
        Thread.sleep(minOf(remaining, RELOAD_DRAIN_POLL_MS))
    }
}

private fun reloadBlocker(): String? =
    when {
        registeringHooks -> "CRT hook registration in progress"
        preparingSurface || crtSurfaces.isNotEmpty() -> "CRT surface in use"
        holdingReveal -> "CRT reveal hold in progress"
        pendingRevealCommits != 0 -> "CRT reveal frame in progress"
        dozeWork != 0 -> "CRT doze hook in progress"
        systemCoordination?.isTransitionPending() == true -> "CRT AOD transition in progress"
        else -> null
    }

internal fun beginCrtDozeWork(): Boolean =
    synchronized(lifecycleLock) {
        if (reloading) return false
        dozeWork++
        true
    }

internal fun endCrtDozeWork() {
    synchronized(lifecycleLock) { dozeWork-- }
}

internal fun crtSystemCoordination(
    host: Any,
    create: () -> CrtSystemCoordination,
): CrtSystemCoordination? =
    synchronized(lifecycleLock) {
        if (reloading) return null
        systemCoordination ?: create().also {
            crtSystemHost = host
            systemCoordination = it
            it.start()
        }
    }

private fun resumeAfterRejectedReload() {
    synchronized(lifecycleLock) { reloading = false }
    installEnabledHooks()
    val ui = uiCoordination
    if (ui?.isClosed() == true) {
        ui.whenStopped {
            synchronized(lifecycleLock) {
                if (uiCoordination === ui) uiCoordination = null
                if (!reloading) restoreUiCoordination?.invoke()
            }
        }
    } else {
        restoreUiCoordination?.invoke()
    }
    val system = systemCoordination
    if (system?.isClosed() == true) {
        system.whenStopped {
            synchronized(lifecycleLock) {
                if (systemCoordination === system) systemCoordination = null
                if (!reloading) restoreSystemCoordinations.forEach { it() }
            }
        }
    } else {
        restoreSystemCoordinations.forEach { it() }
    }
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
    val hasInlinedPrepare: Boolean,
) {
    fun newSurfaceLayout(
        colorFade: Any,
        displayManagerInternal: Any,
        displayId: Int,
        container: SurfaceControl,
        width: Int,
        height: Int,
    ): Any =
        when {
            hasInlinedPrepare -> {
                naturalSurfaceLayout.newInstance(
                    displayManagerInternal,
                    displayId,
                    container,
                    null,
                    null,
                    width,
                    height,
                )
            }

            naturalSurfaceLayout.parameterCount == 4 -> {
                naturalSurfaceLayout.newInstance(
                    colorFade,
                    displayManagerInternal,
                    displayId,
                    container,
                )
            }

            else -> {
                naturalSurfaceLayout.newInstance(displayManagerInternal, displayId, container)
            }
        }

    fun disposeLayout(
        colorFade: Any,
        layout: Any,
    ) {
        if (hasInlinedPrepare) {
            disposeSurfaceLayout.invoke(displayManagerInternal.get(colorFade), layout)
        } else {
            disposeSurfaceLayout.invoke(layout)
        }
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
    val screenOffBecauseOfProximity: (Any) -> Boolean,
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
        screenOffBecauseOfProximity(proximityController.get(controller)!!)
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
private val awakeAodStates: MutableMap<Any, Any> = Collections.synchronizedMap(WeakHashMap())

@Suppress("UNCHECKED_CAST")
private fun restoreAwakeDisplayStates(saved: Any?) {
    if (saved is MutableMap<*, *>) {
        val awake = saved["awake"]
        awakeDisplayStates = (awake as? MutableMap<Any, Any>) ?: saved as MutableMap<Any, Any>
        crtSystemHost = saved["systemHost"]
        crtUiContext = saved["uiContext"] as? Context
    }
}

internal fun consumeCrtAwakeState(controller: Any): Boolean {
    val awake = awakeDisplayStates.remove(controller) != null
    val aod = awakeAodStates.remove(controller) != null
    return awake || aod
}

private fun XposedModule.installCrtScreenOff(cl: ClassLoader): Boolean {
    val fade = resolving { cl.loadClass(COLOR_FADE) }.getOrNull() ?: return installRevealHold(cl)
    val draw =
        resolving { fade.requiredMethod("draw", Float::class.javaPrimitiveType) }
            .getOrElse { return missing("ColorFade#draw(float)", it) }
    val prepare =
        resolving {
            orNullIfMissing {
                fade.requiredMethod("prepare", Context::class.java, Int::class.javaPrimitiveType)
            }
        }.getOrElse { return missing("ColorFade#prepare(Context,int)", it) }
    val displayPowerPrepare =
        if (prepare == null) {
            resolving { DisplayPowerFadePrepare.resolve(cl, fade, draw) }
                .getOrElse { return missing("DisplayPowerState#prepareColorFade(Context,int)", it) }
        } else {
            null
        }
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
                destroy =
                    if (displayPowerPrepare != null) {
                        fade.requiredMethod("destroySurface")
                    } else {
                        orNullIfMissing { fade.requiredMethod("destroy") }
                    },
                naturalSurfaceLayout =
                    if (displayPowerPrepare != null) {
                        val int = Int::class.javaPrimitiveType
                        layout
                            .getDeclaredConstructor(
                                cl.loadClass(DISPLAY_MANAGER_INTERNAL),
                                int,
                                SurfaceControl::class.java,
                                cl.loadClass("android.graphics.BLASTBufferQueue"),
                                SurfaceControl::class.java,
                                int,
                                int,
                            ).apply { isAccessible = true }
                    } else {
                        layout.declaredConstructors
                            .single { it.parameterTypes.last() == SurfaceControl::class.java }
                            .apply { isAccessible = true }
                    },
                onDisplayTransaction =
                    layout.requiredMethod(
                        "onDisplayTransaction",
                        SurfaceControl.Transaction::class.java,
                    ),
                disposeSurfaceLayout =
                    if (displayPowerPrepare != null) {
                        cl.loadClass(DISPLAY_MANAGER_INTERNAL).requiredMethod(
                            "unregisterDisplayTransactionListener",
                            cl.loadClass(DISPLAY_TRANSACTION_LISTENER),
                        )
                    } else {
                        layout.requiredMethod("dispose")
                    },
                hasInlinedPrepare = displayPowerPrepare != null,
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
            val proximityCheck: (Any) -> Boolean =
                if (displayPowerPrepare != null) {
                    val field = proximity.requiredField("mScreenOffBecauseOfProximity")
                    field::getBoolean
                } else {
                    proximity.requiredMethod("isScreenOffBecauseOfProximity").let { method ->
                        { controller -> method.invoke(controller) == true }
                    }
                }
            StateControllerBindings(
                stateController.requiredField("mPerformScreenOffTransition"),
                stateController.optionalField("mShouldSkipScreenOffTransition"),
                cl.loadClass(POWER_REQUEST).requiredField("policy"),
                cl.loadClass(POWER_REQUEST).requiredField("dozeScreenState"),
                stateController.optionalField("mDozeStateOverride"),
                stateController.requiredField("mDisplayPowerProximityStateController"),
                proximity.requiredField("mDisplayId"),
                proximityCheck,
                resultType
                    .takeUnless { resultIsState || it == Pair::class.java }
                    ?.requiredField("state"),
                resultIsState,
            )
        }.getOrElse { return missing("DisplayStateController#mPerformScreenOffTransition", it) }
    val crt = CrtColorFade(fadeBindings, displayInfoBindings, surfaceBindings, animatorBindings)
    val prepareMethod = displayPowerPrepare?.prepare ?: checkNotNull(prepare)
    Logger.info(
        "resolved tweak=$TWEAK members=${draw.signature()},${prepareMethod.signature()}," +
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
                    awakeAodStates.remove(controller)
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
                    if (!skip && state != Display.STATE_OFF &&
                        (systemCoordination?.armedGeneration() ?: 0L) != 0L
                    ) {
                        awakeAodStates[controller] = awakeResult
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
        val aodInstalled =
            displayPowerPrepare == null &&
                installCrtDozeEntry(cl, {
                    switch.enabled && !reloading
                }, stateBindings::effectiveDozeState) { fade ->
                    crt.ownedSurface(fade) != null
                }
        if (!aodInstalled) {
            Logger.warn("crt aod unavailable tweak=$TWEAK build=${Build.ID}")
        }
        hook(draw).intercept { chain ->
            val colorFade = chain.thisObject!!
            val surface = crt.ownedSurface(colorFade) ?: return@intercept chain.proceed()
            val drawn = crt.draw(colorFade, surface, chain.getArg(0) as Float)
            if (displayPowerPrepare == null) drawn else null
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
                    if (displayPowerPrepare != null) {
                        crt.releaseAfterDismiss(colorFade)
                    } else {
                        crtSurfaces.remove(colorFade)?.let { guarded(Unit) { it.release() } }
                    }
                }
            }
        }
        if (displayPowerPrepare != null) {
            installDisplayPowerFadePrepare(displayPowerPrepare, crt)
        } else {
            hook(prepareMethod).intercept { chain ->
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
    }
    return true
}

private class DisplayPowerFadePrepare(
    val animateScreenStateChange: Method,
    val powerState: Field,
    val prepare: Method,
    val colorFade: Field,
    val screenState: Field,
    private val prepared: Field,
    private val ready: Field,
    private val drawPending: Field,
    private val choreographer: Field,
    private val drawRunnable: Field,
    private val postCallback: Method,
) {
    fun scheduleDraw(power: Any) {
        val wasPrepared = prepared.getBoolean(power)
        val wasReady = ready.getBoolean(power)
        val wasPending = drawPending.getBoolean(power)
        var scheduled = false
        try {
            prepared.setBoolean(power, true)
            ready.setBoolean(power, false)
            if (!wasPending) {
                drawPending.setBoolean(power, true)
                postCallback.invoke(
                    choreographer.get(power),
                    CALLBACK_TRAVERSAL,
                    drawRunnable.get(power),
                    null,
                )
            }
            scheduled = true
        } finally {
            if (!scheduled) {
                prepared.setBoolean(power, wasPrepared)
                ready.setBoolean(power, wasReady)
                drawPending.setBoolean(power, wasPending)
            }
        }
    }

    companion object {
        fun resolve(
            cl: ClassLoader,
            fade: Class<*>,
            draw: Method,
        ): DisplayPowerFadePrepare {
            if (draw.returnType !=
                Void.TYPE
            ) {
                throw NoSuchMethodException("ColorFade.draw(float):void")
            }
            val power = cl.loadClass(DISPLAY_POWER_STATE)
            val controller = cl.loadClass(DISPLAY_POWER_CONTROLLERS.first())
            val int = Int::class.javaPrimitiveType
            val colorFade = power.requiredField("mColorFade")
            if (colorFade.type !=
                fade
            ) {
                throw NoSuchFieldException("DisplayPowerState.mColorFade:ColorFade")
            }
            return DisplayPowerFadePrepare(
                controller.requiredMethod(
                    "animateScreenStateChange",
                    int,
                    int,
                    Boolean::class.javaPrimitiveType,
                ),
                controller.requiredField("mPowerState"),
                power.requiredMethod("prepareColorFade", Context::class.java, int),
                colorFade,
                power.requiredField("mScreenState"),
                power.requiredField("mColorFadePrepared"),
                power.requiredField("mColorFadeReady"),
                power.requiredField("mColorFadeDrawPending"),
                power.requiredField("mChoreographer"),
                power.requiredField("mColorFadeDrawRunnable"),
                Choreographer::class.java.requiredMethod(
                    "postCallback",
                    int,
                    Runnable::class.java,
                    Any::class.java,
                ),
            )
        }
    }
}

private fun XposedModule.installDisplayPowerFadePrepare(
    bindings: DisplayPowerFadePrepare,
    crt: CrtColorFade,
) {
    val screenOffPower = ThreadLocal<Any>()
    hook(bindings.animateScreenStateChange).intercept { chain ->
        if (!switch.enabled || chain.getArg(0) != Display.STATE_OFF || chain.getArg(2) != true) {
            return@intercept chain.proceed()
        }
        val power = guarded(null) { bindings.powerState.get(chain.thisObject) }
        val previous = screenOffPower.get()
        screenOffPower.set(power)
        try {
            chain.proceed()
        } finally {
            if (previous == null) screenOffPower.remove() else screenOffPower.set(previous)
        }
    }
    hook(bindings.prepare).intercept { chain ->
        val power = chain.thisObject!!
        val colorFade =
            guarded(null) { bindings.colorFade.get(power) } ?: return@intercept chain.proceed()
        if (switch.enabled && screenOffPower.get() === power &&
            guarded(false) { bindings.screenState.getInt(power) == Display.STATE_ON } &&
            crt.prepare(colorFade, chain.getArg(1) as Int)
        ) {
            val scheduled =
                guarded(false) {
                    bindings.scheduleDraw(power)
                    true
                }
            if (scheduled) return@intercept true
        }
        crt.restoreStockFade(colorFade)
        chain.proceed()
    }
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
            systemCoordination?.failedAfterBlack()
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
                    fade.newSurfaceLayout(
                        colorFade,
                        displayManagerInternal,
                        displayId,
                        container,
                        width,
                        height,
                    )
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
                    guarded(Unit) { fade.disposeLayout(colorFade, layout) }
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
    val revealAmount =
        resolving {
            val scrim = cl.loadClass(LIGHT_REVEAL_SCRIM)
            val setter =
                orNullIfMissing {
                    scrim.requiredMethod(
                        "setRevealAmount",
                        Float::class.javaPrimitiveType,
                    )
                }
            if (setter != null) {
                RevealAmountBinding(setter, null)
            } else {
                val emit =
                    DexKit.method(TWEAK, cl, "$REVEAL_SCRIM_BINDER emit") {
                        it.findMethod { matcher { name("emit").usingStrings(REVEAL_AMOUNT_TRACE) } }
                    }
                if (!emit.declaringClass.name.startsWith("$REVEAL_SCRIM_BINDER\$") ||
                    emit.parameterTypes.toList() !=
                    listOf(Any::class.java, cl.loadClass("kotlin.coroutines.Continuation"))
                ) {
                    throw NoSuchMethodException("$REVEAL_SCRIM_BINDER emit(Object,Continuation)")
                }
                val scrimField =
                    emit.declaringClass.declaredFields
                        .singleOrNull { it.type == scrim }
                        ?.apply { isAccessible = true }
                        ?: throw NoSuchFieldException(
                            "${emit.declaringClass.simpleName} LightRevealScrim",
                        )
                RevealAmountBinding(emit, scrimField)
            }
        }.getOrElse { return missing("LightRevealScrim#setRevealAmount(float)", it) }
    val lifecycle =
        resolving { KeyguardHandlerBinding.resolve(cl) }.getOrElse {
            return missing("KeyguardLifecyclesDispatcher handler", it)
        }
    val samsungAodSetting =
        revealAmount.scrimField != null && Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    val alwaysOnDisplay =
        if (samsungAodSetting) {
            null
        } else {
            resolving { bindAlwaysOnDisplay(cl) }.getOrElse {
                Logger.warn(
                    "AOD lookup failed tweak=$TWEAK member=DozeParameters build=${Build.ID} " +
                        "reason=${it.reason()}",
                )
                null
            }
        }
    val revealHold = ScreenOffRevealHold(revealAmount, alwaysOnDisplay, samsungAodSetting)
    val applicationAttach =
        resolving {
            Application::class.java.requiredMethod("attach", Context::class.java)
        }.getOrNull()
    val currentApplication =
        resolving {
            cl.loadClass("android.app.ActivityThread").requiredMethod("currentApplication")
        }.getOrNull()
    restoreUiCoordination = {
        guarded(Unit) {
            val context = crtUiContext ?: currentApplication?.invoke(null) as? Context
            context?.let { revealHold.ensureCoordination(it) }
        }
    }
    Logger.info(
        "resolved tweak=$TWEAK members=${revealAmount.method.signature()}," +
            lifecycle.handleMessage.signature(),
    )
    installHooksOnEnable {
        applicationAttach?.let { attach ->
            hook(attach).intercept { chain ->
                val result = chain.proceed()
                guarded(Unit) { revealHold.ensureCoordination(chain.thisObject as Context) }
                result
            }
        }
        hook(revealAmount.method).intercept { chain ->
            val receiver = chain.thisObject!!
            val scrimField = revealAmount.scrimField
            val scrim =
                if (scrimField == null) {
                    receiver as View
                } else {
                    guarded(null) { scrimField.get(receiver) as? View }
                        ?: return@intercept chain.proceed()
                }
            val amount = (chain.getArg(0) as? Number)?.toFloat() ?: return@intercept chain.proceed()
            val collector = receiver.takeIf { scrimField != null }
            val continuation = collector?.let { chain.getArg(1) }
            if (revealHold.hold(scrim, amount, collector, continuation)) {
                val args = chain.args.toTypedArray<Any?>()
                args[0] = FULL_REVEAL
                return@intercept chain.proceed(args)
            }
            chain.proceed()
        }
        hook(lifecycle.handleMessage).intercept { chain ->
            guarded(Unit) {
                if (lifecycle.isKeyguardHandler(chain.thisObject)) {
                    revealHold.onLifecycleMessage((chain.getArg(0) as Message).what)
                }
            }
            chain.proceed()
        }
        restoreUiCoordination?.invoke()
    }
    return true
}

private class KeyguardHandlerBinding(
    val handleMessage: Method,
    val isKeyguardHandler: (Any?) -> Boolean,
) {
    companion object {
        fun resolve(cl: ClassLoader): KeyguardHandlerBinding {
            val handler =
                orNullIfMissing { cl.loadClass("$KEYGUARD_LIFECYCLES\$KeyguardLifecycleHandler") }
                    ?: orNullIfMissing { cl.loadClass("$KEYGUARD_LIFECYCLES\$1") }
            if (handler != null) {
                return KeyguardHandlerBinding(
                    handler.requiredMethod("handleMessage", Message::class.java),
                ) { true }
            }
            val merged = cl.loadClass(KEYGUARD_LIFECYCLES).requiredField("mHandler").type
            val screenLifecycle = cl.loadClass("com.android.systemui.keyguard.ScreenLifecycle")
            val screen = merged.requiredField("screenRecordingUris")
            return KeyguardHandlerBinding(
                merged.requiredMethod("handleMessage", Message::class.java),
            ) {
                screenLifecycle.isInstance(screen.get(it))
            }
        }
    }
}

private class RevealAmountBinding(
    val method: Method,
    val scrimField: Field?,
) {
    fun restore(
        scrim: View,
        request: RevealRequest,
    ) {
        if (scrimField == null) {
            method.invoke(scrim, request.amount)
        } else {
            request.collector?.get()?.let {
                method.invoke(it, request.amount, request.continuation)
            }
        }
    }
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
    private val revealAmount: RevealAmountBinding,
    private val alwaysOnDisplay: AlwaysOnDisplayBindings?,
    private val samsungAodSetting: Boolean,
) {
    private val requestedAmounts = WeakHashMap<View, RevealRequest>()
    private var holdingAod = false

    fun onLifecycleMessage(what: Int) {
        when (what) {
            STARTED_GOING_TO_SLEEP -> {
                val alwaysOn = alwaysOnDisplay?.isEnabled() ?: uiCoordination?.alwaysOnSetting()
                synchronized(lifecycleLock) {
                    if (!reloading && switch.enabled) {
                        holdingAod =
                            revealAmount.scrimField == null &&
                            alwaysOn == true && uiCoordination?.arm() == true
                        if (alwaysOn == false || holdingAod) holdingReveal = true
                    }
                }
            }

            SCREEN_TURNED_OFF -> {
                if (!holdingAod) releaseHeldAmounts()
            }

            STARTED_WAKING_UP, SCREEN_TURNING_ON -> {
                uiCoordination?.cancel()
                holdingAod = false
                synchronized(lifecycleLock) { holdingReveal = false }
                requestedAmounts.clear()
            }
        }
    }

    fun hold(
        scrim: View,
        amount: Float,
        collector: Any?,
        continuation: Any?,
    ): Boolean {
        if (Looper.myLooper() !== Looper.getMainLooper()) return false
        ensureCoordination(scrim.context)
        if ((switch.enabled || holdingAod) && holdingReveal &&
            guarded(false) { screenOffWithoutAod(scrim) }
        ) {
            requestedAmounts[scrim] =
                RevealRequest(amount, collector?.let(::WeakReference), continuation)
            if (holdingAod) uiCoordination?.heldAmountChanged()
            return true
        }
        requestedAmounts.remove(scrim)
        return false
    }

    fun ensureCoordination(context: Context) {
        synchronized(lifecycleLock) {
            if (uiCoordination == null && !reloading && switch.enabled) {
                crtUiContext = context
                uiCoordination =
                    CrtUiCoordination(
                        context,
                        samsungAodSetting,
                        {
                            requestedAmounts.isNotEmpty() &&
                                requestedAmounts.values.all {
                                    it.amount <= 0f
                                }
                        },
                        { committed -> releaseHeldAmounts(committed) },
                    ).also { it.start() }
            }
        }
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
        if (holdingAod) return true
        val aodEnabled = alwaysOnDisplay?.isEnabled() ?: uiCoordination?.alwaysOnSetting()
        return aodEnabled == false
    }

    private fun releaseHeldAmounts(committed: (() -> Unit)? = null) {
        val wasHoldingAod = holdingAod
        holdingAod = false
        synchronized(lifecycleLock) {
            holdingReveal = false
            val amounts = requestedAmounts.toMap()
            requestedAmounts.clear()
            if (wasHoldingAod) {
                Logger.info(
                    "crt aod reveal amounts tweak=$TWEAK amounts=${amounts.values.map {
                        it.amount
                    }}",
                )
            }
            if (amounts.isEmpty()) committed?.invoke()
            val callbacks = ArrayList<Triple<View, ViewTreeObserver, FutureTask<Unit>>>()
            val handler = Handler(Looper.getMainLooper())
            var timeout: Runnable? = null
            var timedOut = false
            var registrationFailed = false
            var reservationReleased = false
            val commitGeneration =
                if (committed != null && amounts.isNotEmpty()) {
                    pendingRevealCommits++
                    val generation = ++revealCommitGeneration
                    val timeoutAction =
                        Runnable {
                            synchronized(lifecycleLock) {
                                timedOut = true
                                callbacks.forEach { (view, original, callback) ->
                                    callback.cancel(false)
                                    guarded(Unit) {
                                        val current = view.viewTreeObserver
                                        if (original.isAlive) {
                                            original
                                                .unregisterFrameCommitCallback(
                                                    callback,
                                                )
                                        }
                                        if (current !== original && current.isAlive) {
                                            current.unregisterFrameCommitCallback(callback)
                                        }
                                    }
                                }
                                callbacks.clear()
                                if (!reservationReleased) {
                                    reservationReleased = true
                                    pendingRevealCommits--
                                }
                                if (revealCommitGeneration == generation) {
                                    revealCommitGeneration++
                                }
                            }
                        }
                    timeout = timeoutAction
                    handler.postDelayed(timeoutAction, REVEAL_COMMIT_TIMEOUT_MS)
                    generation
                } else {
                    0L
                }
            amounts.forEach { (scrim, request) ->
                val applied =
                    guarded(false) {
                        if (committed != null) {
                            val observer = scrim.viewTreeObserver
                            lateinit var callback: FutureTask<Unit>
                            callback =
                                FutureTask(
                                    Runnable {
                                        val current =
                                            synchronized(lifecycleLock) {
                                                callbacks.removeAll { it.third === callback }
                                                if (callbacks.isEmpty() && !reservationReleased) {
                                                    reservationReleased = true
                                                    timeout?.let(handler::removeCallbacks)
                                                    pendingRevealCommits--
                                                    val current =
                                                        revealCommitGeneration == commitGeneration
                                                    if (timedOut &&
                                                        current
                                                    ) {
                                                        revealCommitGeneration++
                                                    }
                                                    current && !timedOut && !registrationFailed
                                                } else {
                                                    false
                                                }
                                            }
                                        if (current) committed()
                                    },
                                    Unit,
                                )
                            observer.registerFrameCommitCallback(callback)
                            callbacks.add(Triple(scrim, observer, callback))
                        }
                        revealAmount.restore(scrim, request)
                        true
                    }
                if (!applied) registrationFailed = true
            }
        }
    }
}

private class RevealRequest(
    val amount: Float,
    val collector: WeakReference<Any>?,
    val continuation: Any?,
)

private const val REVEAL_COMMIT_TIMEOUT_MS = 1_000L

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
