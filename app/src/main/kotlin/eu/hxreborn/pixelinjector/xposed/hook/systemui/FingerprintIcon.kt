package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.StateSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.icons.FingerprintStyles
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.Value
import eu.hxreborn.pixelinjector.xposed.module
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.zip.ZipFile

private const val TWEAK = "FingerprintIcon"
private const val ICON_VIEW = "com.android.systemui.keyguard.ui.view.DeviceEntryIconView"
private const val UDFPS_VIEW = "com.android.systemui.biometrics.UdfpsKeyguardView"
private const val SAMSUNG_LOCK_ICON_VIEW = "com.android.keyguard.SecLockIconView"
private const val OPAQUE = 255

@Volatile private var current: FingerprintBindings? = null

private val switch = Switch(Prefs.FINGERPRINT_ICON) { current?.updateAll() }
private val style = Value(Prefs.FINGERPRINT_ICON_STYLE, onLoad = { current?.updateAll() }) { it }
private val hideCircle = Switch(Prefs.FINGERPRINT_HIDE_CIRCLE) { current?.updateAll() }

internal val fingerprintIcon =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(switch, style, hideCircle),
        install = XposedModule::installFingerprintIcon,
        saveState = { current?.let { ArrayList<View>(it.views) } },
        restoreState = { saved -> (saved as? List<*>)?.let { current?.restore(it) } },
    )

private class FingerprintSwap(
    stock: Drawable,
    private val fingerprintStates: List<IntArray>,
) : DrawableWrapper(stock) {
    var replacement: Drawable? = null
        set(value) {
            field = value
            value?.bounds = square(bounds)
            invalidateSelf()
        }

    private fun isFingerprint(state: IntArray): Boolean =
        fingerprintStates.any {
            StateSet.stateSetMatches(it, state)
        }

    override fun draw(canvas: Canvas) {
        if (isFingerprint(state)) replacement?.draw(canvas) else super.draw(canvas)
    }

    override fun onStateChange(state: IntArray): Boolean {
        val wasFingerprint = isFingerprint(drawable?.state ?: StateSet.NOTHING)
        val changed = super.onStateChange(state)
        if (wasFingerprint != isFingerprint(state)) drawable?.jumpToCurrentState()
        return changed
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        replacement?.bounds = square(bounds)
    }

    private fun square(bounds: Rect): Rect {
        val half = minOf(bounds.width(), bounds.height()) / 2
        return Rect(
            bounds.centerX() - half,
            bounds.centerY() - half,
            bounds.centerX() + half,
            bounds.centerY() + half,
        )
    }
}

private sealed interface IconTarget {
    val viewClass: Class<*>
    val members: String
    val refresh: Method?

    fun apply(
        view: View,
        replacement: () -> Drawable?,
        hidden: Boolean,
        circleScale: Float,
    ): Boolean
}

private class DeviceEntryTarget(
    val iconView: Field,
    val bgView: Field,
    val getIconState: Method,
    val fingerprint: Any,
) : IconTarget {
    override val viewClass: Class<*> = iconView.declaringClass
    override val members = "${iconView.signature()},${bgView.signature()}"
    override val refresh: Method? = null

    @Volatile private var fingerprintStates: List<IntArray>? = null

    private fun fingerprintStates(view: View): List<IntArray> =
        fingerprintStates
            ?: listOf(false, true)
                .map { getIconState.invoke(view, fingerprint, it) as IntArray }
                .also { fingerprintStates = it }

    override fun apply(
        view: View,
        replacement: () -> Drawable?,
        hidden: Boolean,
        circleScale: Float,
    ): Boolean {
        val icon = iconView.get(view) as ImageView
        val bg = bgView.get(view) as ImageView
        val swap = replacement()
        val stock = unwrap(icon.drawable)
        if (swap != null || hidden) {
            val wrapper =
                icon.drawable as? FingerprintSwap
                    ?: FingerprintSwap(stock, fingerprintStates(view)).also(icon::setImageDrawable)
            wrapper.replacement = swap
        } else if (icon.drawable !== stock) {
            icon.setImageDrawable(stock)
        }
        bg.scaleX = circleScale
        bg.scaleY = circleScale
        return true
    }

    private fun unwrap(drawable: Drawable): Drawable =
        if (drawable is DrawableWrapper &&
            drawable.javaClass.name == FingerprintSwap::class.java.name
        ) {
            drawable.drawable ?: drawable
        } else {
            drawable
        }
}

private class UdfpsTarget(
    val fingerprints: List<Field>,
    val bgProtection: Field,
    override val refresh: Method,
) : IconTarget {
    override val viewClass: Class<*> = bgProtection.declaringClass
    override val members = (fingerprints + bgProtection).joinToString(",") { it.signature() }

    override fun apply(
        view: View,
        replacement: () -> Drawable?,
        hidden: Boolean,
        circleScale: Float,
    ): Boolean {
        val bg = bgProtection.get(view) as? ImageView ?: return false
        fingerprints.map { it.get(view) as ImageView }.forEach { fingerprint ->
            val swap = replacement()
            fingerprint.imageAlpha = if (swap != null || hidden) 0 else OPAQUE
            fingerprint.foreground =
                swap?.let {
                    FingerprintSwap(
                        ColorDrawable(Color.TRANSPARENT),
                        listOf(StateSet.WILD_CARD),
                    ).apply {
                        this.replacement = it
                    }
                }
        }
        bg.scaleX = circleScale
        bg.scaleY = circleScale
        return true
    }
}

private class FingerprintBindings(
    val target: IconTarget,
) {
    val views: MutableSet<View> =
        Collections.synchronizedSet(
            Collections.newSetFromMap(WeakHashMap()),
        )

    @Volatile private var cachedIcon: Pair<String, Drawable?>? = null

    fun register(view: View) {
        views += view
        view.post { updateView(view) }
    }

    fun updateAll() {
        cachedIcon = null
        synchronized(views) { views.toList() }.forEach { view -> view.post { updateView(view) } }
    }

    fun registerAttached() {
        runCatching {
            val global = Class.forName("android.view.WindowManagerGlobal")
            val roots =
                global
                    .requiredField(
                        "mViews",
                    ).get(global.getMethod("getInstance").invoke(null)) as List<*>
            val found = roots.filterIsInstance<View>().flatMap { iconViewsIn(it) }
            found.forEach(::register)
            Logger.info("attached tweak=$TWEAK views=${found.size}")
        }.onFailure { Logger.error("attached failed tweak=$TWEAK reason=${it.message}", it) }
    }

    private fun iconViewsIn(view: View): List<View> =
        when {
            target.viewClass.isInstance(view) -> {
                listOf(view)
            }

            view is ViewGroup -> {
                (0 until view.childCount).flatMap {
                    iconViewsIn(
                        view.getChildAt(it),
                    )
                }
            }

            else -> {
                emptyList()
            }
        }

    fun restore(saved: List<*>) {
        saved.filterIsInstance<View>().forEach(::register)
        Logger.info("carried tweak=$TWEAK views=${saved.size}")
    }

    private fun newReplacement(
        view: View,
        style: String,
    ): Drawable? {
        val cached = cachedIcon
        val prototype = if (cached?.first == style) cached.second else loadIcon(view, style)
        return prototype?.constantState?.newDrawable(view.resources)?.mutate()
    }

    private fun loadIcon(
        view: View,
        style: String,
    ): Drawable? {
        val drawable =
            if (FingerprintStyles.isCustom(style)) {
                module.openRemoteFile(FingerprintStyles.IMAGE_FILE).use { fd ->
                    BitmapFactory
                        .decodeFileDescriptor(
                            fd.fileDescriptor,
                        )?.let { BitmapDrawable(view.resources, it) }
                }
            } else {
                ZipFile(module.moduleApplicationInfo.sourceDir).use { zip ->
                    zip.getEntry("assets/${FingerprintStyles.assetPath(style)}")?.let { entry ->
                        zip.getInputStream(entry).use(BitmapFactory::decodeStream)?.let {
                            BitmapDrawable(view.resources, it)
                        }
                    }
                }
            }
        if (drawable == null) {
            Logger.error("icon failed tweak=$TWEAK style=$style reason=no-drawable")
        }
        cachedIcon = style to drawable
        return drawable
    }

    private fun updateView(view: View) {
        runCatching {
            val style = style.value
            val swapping =
                switch.enabled && style != FingerprintStyles.STOCK &&
                    style != FingerprintStyles.NONE
            val applied =
                target.apply(
                    view,
                    replacement = { if (swapping) newReplacement(view, style) else null },
                    hidden = switch.enabled && style == FingerprintStyles.NONE,
                    circleScale = if (switch.enabled && hideCircle.enabled) 0f else 1f,
                )
            if (!applied) return@runCatching
            val styleLabel = style.ifEmpty { "stock" }
            Logger.info(
                "applied tweak=$TWEAK style=$styleLabel hideCircle=${hideCircle.enabled} on=${switch.enabled}",
            )
        }.onFailure { Logger.error("apply failed tweak=$TWEAK reason=${it.message}", it) }
    }

    companion object {
        fun resolve(cl: ClassLoader): FingerprintBindings =
            FingerprintBindings(
                try {
                    deviceEntry(cl)
                } catch (_: ClassNotFoundException) {
                    udfps(cl)
                },
            )

        private fun deviceEntry(cl: ClassLoader): DeviceEntryTarget {
            val view = cl.loadClass(ICON_VIEW)
            val iconType = cl.loadClass("$ICON_VIEW\$IconType")
            val getIconState =
                view.requiredMethod(
                    "getIconState",
                    iconType,
                    Boolean::class.javaPrimitiveType,
                )
            return DeviceEntryTarget(
                iconView = view.requiredField("iconView"),
                bgView = view.requiredField("bgView"),
                getIconState = getIconState,
                fingerprint =
                    iconType.enumConstants.first {
                        (it as Enum<*>).name == "FINGERPRINT"
                    },
            )
        }

        // Android 14 SystemUI AP2A.240905.003
        private fun udfps(cl: ClassLoader): UdfpsTarget {
            val view =
                try {
                    cl.loadClass("${UDFPS_VIEW}Legacy")
                } catch (_: ClassNotFoundException) {
                    cl.loadClass(UDFPS_VIEW)
                }
            return UdfpsTarget(
                fingerprints =
                    listOf(
                        view.requiredField("mLockScreenFp"),
                        view.requiredField("mAodFp"),
                    ),
                bgProtection = view.requiredField("mBgProtection"),
                refresh = view.requiredMethod("updateColor"),
            )
        }
    }
}

private fun XposedModule.installFingerprintIcon(cl: ClassLoader): Boolean {
    val samsungLockIcon =
        resolving {
            try {
                cl.loadClass(SAMSUNG_LOCK_ICON_VIEW)
            } catch (_: ClassNotFoundException) {
                null
            }
        }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    if (samsungLockIcon != null) {
        Logger.error(
            "target not found tweak=$TWEAK member=${samsungLockIcon.simpleName} " +
                "build=${Build.ID} " +
                "reason=Samsung-draws-fingerprint-icon-outside-SystemUI",
        )
        return false
    }
    val bindings =
        resolving { FingerprintBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${bindings.target.members}")
    current = bindings
    bindings.target.refresh?.let { refresh ->
        hook(refresh).intercept { chain ->
            val result = chain.proceed()
            (chain.thisObject as? View)?.let(bindings::register)
            result
        }
    }
    for (constructor in bindings.target.viewClass.declaredConstructors) {
        hook(constructor).intercept { chain ->
            val result = chain.proceed()
            (chain.thisObject as? View)?.let(bindings::register)
            result
        }
    }
    Handler(Looper.getMainLooper()).post { bindings.registerAttached() }
    return true
}
