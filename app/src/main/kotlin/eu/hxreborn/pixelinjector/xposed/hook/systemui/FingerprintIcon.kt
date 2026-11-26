package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.util.StateSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import eu.hxreborn.pixelinjector.ModuleConstants
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.icons.FingerprintStyles
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.detail
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
import kotlin.concurrent.thread

private const val TWEAK = "FingerprintIcon"
private const val ICON_VIEW = "com.android.systemui.keyguard.ui.view.DeviceEntryIconView"
private const val UDFPS_VIEW = "com.android.systemui.biometrics.UdfpsKeyguardView"
private const val SAMSUNG_LOCK_ICON_VIEW = "com.android.keyguard.SecLockIconView"
private const val TRANSSION_OPTICAL_HELPER =
    "com.transsion.keyguard.biometricfunc.KeyguardFingerPrintOpticalHelper"
private const val TRANSSION_FINGERPRINT_STATE =
    "com.transsion.aod.dreams.controller.FingerprintUIStateController"
private const val OPLUS_ICON_VIEW =
    "com.oplus.systemui.biometrics.finger.udfps.OnScreenFingerprintIcon"
private const val ACCENT_ATTR = "wallpaperTextColorAccent"
private const val NO_ICON_VIEW_REASON = "keyguard-shown-without-DeviceEntryIconView"
private const val OPAQUE = 255
private const val MAX_CUSTOM_ICON_SIZE_PX = 1024
private const val MAX_CUSTOM_SOURCE_SIZE_PX = 32768

@Volatile private var current: FingerprintBindings? = null

private val switch = Switch(Prefs.FINGERPRINT_ICON) { current?.updateAll() }
private val style = Value(Prefs.FINGERPRINT_ICON_STYLE, onLoad = { current?.updateAll() }) { it }
private val hideCircle = Switch(Prefs.FINGERPRINT_HIDE_CIRCLE) { current?.updateAll() }
private val tint = Switch(Prefs.FINGERPRINT_TINT) { current?.updateAll() }

internal val fingerprintIcon =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(switch, style, hideCircle, tint),
        install = XposedModule::installFingerprintIcon,
        saveState = { current?.carry() },
        onReloadRejected = { current?.resume() },
        restoreState = { saved -> (saved as? Map<*, *>)?.let { current?.restore(it) } },
    )

private class FingerprintSwap(
    stock: Drawable,
    private val fingerprintStates: List<IntArray>,
) : DrawableWrapper(stock) {
    var replacement: Drawable? = null
        set(value) {
            field = value
            value?.bounds = square(bounds)
            paintReplacement()
        }

    var accentColor: (() -> Int?)? = null
        set(value) {
            field = value
            paintReplacement()
        }

    override fun setTintList(colors: ColorStateList?) {
        super.setTintList(colors)
        paintReplacement()
    }

    private fun paintReplacement() {
        val color = accentColor?.invoke()
        if (color != null) replacement?.setTint(color) else replacement?.setTintList(null)
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

    fun stock(view: View): Drawable? = null

    fun restoreStock(
        view: View,
        stock: Drawable,
    ) = Unit

    fun apply(
        view: View,
        replacement: () -> Drawable?,
        accentColor: (() -> Int?)?,
        hidden: Boolean,
        circleScale: Float,
    ): Boolean
}

private class DeviceEntryTarget(
    val iconView: Field,
    val bgView: Field,
    val getIconState: Method,
    val fingerprint: Any,
    val missingReason: String,
) : IconTarget {
    override val viewClass: Class<*> = iconView.declaringClass
    override val members = "${iconView.signature()},${bgView.signature()}"
    override val refresh: Method? = null

    @Volatile private var fingerprintStates: List<IntArray>? = null

    override fun stock(view: View): Drawable? =
        (iconView.get(view) as? ImageView)?.drawable?.let(::unwrap)

    override fun restoreStock(
        view: View,
        stock: Drawable,
    ) {
        val icon = iconView.get(view) as? ImageView ?: return
        val wrapper = icon.drawable as? DrawableWrapper ?: return
        if (wrapper !is FingerprintSwap && wrapper.drawable === stock) icon.setImageDrawable(stock)
    }

    private fun fingerprintStates(view: View): List<IntArray> =
        fingerprintStates
            ?: listOf(false, true)
                .map { getIconState.invoke(view, fingerprint, it) as IntArray }
                .also { fingerprintStates = it }

    override fun apply(
        view: View,
        replacement: () -> Drawable?,
        accentColor: (() -> Int?)?,
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
            wrapper.accentColor = accentColor
            wrapper.replacement = swap
        } else if (icon.drawable !== stock) {
            icon.setImageDrawable(stock)
        }
        bg.scaleX = circleScale
        bg.scaleY = circleScale
        return true
    }

    private fun unwrap(drawable: Drawable): Drawable =
        (drawable as? FingerprintSwap)?.drawable ?: drawable
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
        accentColor: (() -> Int?)?,
        hidden: Boolean,
        circleScale: Float,
    ): Boolean {
        val bg = bgProtection.get(view) as? ImageView ?: return false
        fingerprints.map { it.get(view) as ImageView }.forEach { fingerprint ->
            fingerprint.drawAsForeground(replacement(), accentColor, hidden)
        }
        bg.scaleX = circleScale
        bg.scaleY = circleScale
        return true
    }
}

private class OnScreenIconTarget(
    override val refresh: Method,
) : IconTarget {
    override val viewClass: Class<*> = refresh.declaringClass
    override val members = refresh.signature()

    override fun apply(
        view: View,
        replacement: () -> Drawable?,
        accentColor: (() -> Int?)?,
        hidden: Boolean,
        circleScale: Float,
    ): Boolean {
        val icon = view as ImageView
        val stock = icon.drawable
        icon.foregroundGravity = Gravity.CENTER
        icon.drawAsForeground(
            swap = stock?.let { replacement() },
            accentColor = accentColor,
            hidden = hidden,
            sizeSource =
                GradientDrawable().apply {
                    stock?.let { setSize(it.intrinsicWidth, it.intrinsicHeight) }
                },
        )
        return true
    }
}

private fun ImageView.drawAsForeground(
    swap: Drawable?,
    accentColor: (() -> Int?)?,
    hidden: Boolean,
    sizeSource: Drawable = ColorDrawable(Color.TRANSPARENT),
) {
    imageAlpha = if (swap != null || hidden) 0 else OPAQUE
    foreground =
        swap?.let {
            FingerprintSwap(
                sizeSource,
                listOf(StateSet.WILD_CARD),
            ).apply {
                this.accentColor = accentColor
                this.replacement = it
            }
        }
}

private class CachedIcon(
    val style: String,
    val drawable: Drawable?,
)

private class FingerprintBindings(
    val target: IconTarget,
    private val currentApplication: Method,
) {
    val views: MutableSet<View> =
        Collections.synchronizedSet(
            Collections.newSetFromMap(WeakHashMap()),
        )

    @Volatile private var cachedIcon: CachedIcon? = null

    private val iconLoads = mutableMapOf<String, Thread>()
    private var retired = false

    private var viewSeen = false
    private var keyguardChecked = false
    private var keyguardWatch: KeyguardWatch? = null

    @Synchronized
    fun register(
        view: View,
        carriedStock: Drawable? = null,
    ) {
        if (retired) return
        if (!viewSeen) {
            viewSeen = true
            if (keyguardWatch != null) {
                Handler(Looper.getMainLooper()).post(::stopWatchingUnlock)
            }
        }
        views += view
        view.post {
            synchronized(this) {
                if (!retired) {
                    carriedStock?.let { target.restoreStock(view, it) }
                    updateView(view)
                }
            }
        }
    }

    @Synchronized
    fun carry(): HashMap<View, Drawable?> {
        retired = true
        stopWatchingUnlock()
        iconLoads.values.forEach(Thread::interrupt)
        val savedViews = synchronized(views) { views.toList() }
        return savedViews.associateWithTo(HashMap()) { target.stock(it) }
    }

    @Synchronized
    fun resume() {
        retired = false
        updateAll()
        watchUnlock()
    }

    @Synchronized
    fun updateAll() {
        if (retired) return
        synchronized(views) { views.toList() }.forEach { view -> view.post { updateView(view) } }
    }

    fun registerAttached() {
        try {
            val global = Class.forName("android.view.WindowManagerGlobal")
            val roots =
                global
                    .requiredField(
                        "mViews",
                    ).get(global.getMethod("getInstance").invoke(null)) as List<*>
            val found = roots.filterIsInstance<View>().flatMap { iconViewsIn(it) }
            found.forEach(::register)
            Logger.info("attached tweak=$TWEAK views=${found.size}")
            watchUnlock()
        } catch (e: Exception) {
            Logger.error("attached failed tweak=$TWEAK reason=${e.detail()}", e)
        } catch (e: LinkageError) {
            Logger.error("attached failed tweak=$TWEAK reason=${e.detail()}", e)
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun watchUnlock() {
        if (retired || viewSeen || keyguardChecked || target !is DeviceEntryTarget) return
        if (keyguardWatch != null) return
        val context = currentApplication.invoke(null) as? Context ?: return
        if (context.getSystemService(UserManager::class.java)?.isSystemUser != true) return
        if (context.checkSelfPermission(Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Logger.debug { "keyguard watch unavailable tweak=$TWEAK reason=no-permission" }
            return
        }
        val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return
        val watch = KeyguardWatch(keyguard)
        keyguard.addKeyguardLockedStateListener(context.mainExecutor, watch)
        keyguardWatch = watch
    }

    private fun reportMissingIcon() {
        val entry = target as? DeviceEntryTarget ?: return
        keyguardChecked = true
        stopWatchingUnlock()
        val message =
            "target not found tweak=$TWEAK member=${entry.viewClass.simpleName} " +
                "build=${Build.ID} reason=${entry.missingReason}"
        module.recordDeferredInstallFailure(TWEAK, message)
        Logger.error(message)
    }

    @Synchronized
    private fun stopWatchingUnlock() {
        val watch = keyguardWatch ?: return
        keyguardWatch = null
        try {
            watch.stop()
        } catch (e: Exception) {
            keyguardWatchFailed(e)
        } catch (e: LinkageError) {
            keyguardWatchFailed(e)
        }
    }

    private fun keyguardWatchFailed(cause: Throwable) {
        Logger.error("keyguard watch failed tweak=$TWEAK reason=${cause.detail()}", cause)
    }

    private inner class KeyguardWatch(
        private val keyguard: KeyguardManager,
    ) : KeyguardManager.KeyguardLockedStateListener {
        private val getCurrentUser = ActivityManager::class.java.getMethod("getCurrentUser")
        private val isDeviceSecure =
            KeyguardManager::class.java.getMethod(
                "isDeviceSecure",
                Int::class.javaPrimitiveType,
            )
        private var secureLockUser =
            if (keyguard.isKeyguardLocked) secureUser(currentUser()) else null

        override fun onKeyguardLockedStateChanged(locked: Boolean) {
            synchronized(this@FingerprintBindings) {
                if (this !== keyguardWatch || viewSeen) return
                val lockedUser = secureLockUser
                secureLockUser = null
                try {
                    val user = currentUser()
                    if (locked) {
                        secureLockUser = secureUser(user)
                    } else if (lockedUser == user) {
                        reportMissingIcon()
                    }
                } catch (e: Exception) {
                    keyguardWatchFailed(e)
                } catch (e: LinkageError) {
                    keyguardWatchFailed(e)
                }
            }
        }

        private fun currentUser(): Int = getCurrentUser.invoke(null) as Int

        private fun secureUser(user: Int): Int? =
            user.takeIf { isDeviceSecure.invoke(keyguard, it) == true }

        @SuppressLint("MissingPermission")
        fun stop() {
            keyguard.removeKeyguardLockedStateListener(this)
        }
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

    fun restore(saved: Map<*, *>) {
        for ((view, stock) in saved) {
            if (view is View) register(view, stock as? Drawable)
        }
        Logger.info("carried tweak=$TWEAK views=${saved.size}")
    }

    private fun loadAsync(
        resources: Resources,
        requestedStyle: String,
    ) {
        synchronized(this) {
            if (retired || requestedStyle in iconLoads) return
            val worker =
                thread(start = false, isDaemon = true, name = "PixelInjector-FingerprintIcon") {
                    val loadedIcon = loadIcon(resources, requestedStyle)
                    synchronized(this) {
                        iconLoads.remove(requestedStyle)
                        if (!retired && requestedStyle == style.value) {
                            cachedIcon = loadedIcon
                            updateAll()
                        }
                    }
                }
            iconLoads[requestedStyle] = worker
            worker.start()
        }
    }

    private fun loadIcon(
        resources: Resources,
        style: String,
    ): CachedIcon {
        val drawable =
            try {
                decodeIcon(resources, style)
            } catch (e: Exception) {
                Logger.error("icon failed tweak=$TWEAK style=$style reason=${e.detail()}", e)
                null
            } catch (e: LinkageError) {
                Logger.error("icon failed tweak=$TWEAK style=$style reason=${e.detail()}", e)
                null
            }
        if (drawable == null) {
            Logger.error("icon failed tweak=$TWEAK style=$style reason=no-drawable")
        }
        return CachedIcon(style, drawable)
    }

    private fun decodeIcon(
        resources: Resources,
        style: String,
    ): Drawable? =
        if (FingerprintStyles.isCustom(style)) {
            loadCustomIcon(resources)
        } else {
            ZipFile(module.moduleApplicationInfo.sourceDir).use { zip ->
                zip.getEntry("assets/${FingerprintStyles.assetPath(style)}")?.let { entry ->
                    zip.getInputStream(entry).use(BitmapFactory::decodeStream)?.let {
                        BitmapDrawable(resources, it)
                    }
                }
            }
        }

    private fun loadCustomIcon(resources: Resources): Drawable? {
        val options =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                inSampleSize = 1
            }
        val bitmap =
            module.openRemoteFile(FingerprintStyles.IMAGE_FILE).use { fd ->
                BitmapFactory.decodeFileDescriptor(fd.fileDescriptor, null, options)
                if (options.outWidth !in 1..MAX_CUSTOM_SOURCE_SIZE_PX ||
                    options.outHeight !in 1..MAX_CUSTOM_SOURCE_SIZE_PX
                ) {
                    Logger.warn(
                        "icon rejected tweak=$TWEAK width=${options.outWidth} height=${options.outHeight}",
                    )
                    return null
                }
                while (maxOf(options.outWidth, options.outHeight) >
                    MAX_CUSTOM_ICON_SIZE_PX * options.inSampleSize
                ) {
                    options.inSampleSize *= 2
                }
                options.inJustDecodeBounds = false
                BitmapFactory.decodeFileDescriptor(fd.fileDescriptor, null, options)
            } ?: return null
        if (bitmap.width > MAX_CUSTOM_ICON_SIZE_PX || bitmap.height > MAX_CUSTOM_ICON_SIZE_PX) {
            bitmap.recycle()
            Logger.warn(
                "icon rejected tweak=$TWEAK reason=decoded-size size=$MAX_CUSTOM_ICON_SIZE_PX",
            )
            return null
        }
        return BitmapDrawable(resources, bitmap)
    }

    @Synchronized
    private fun updateView(view: View) {
        if (retired) return
        try {
            val style = style.value
            val swapping =
                switch.enabled && style != FingerprintStyles.STOCK &&
                    style != FingerprintStyles.NONE
            val cached = cachedIcon?.takeIf { it.style == style }
            if (swapping && cached == null) return loadAsync(view.resources, style)
            val accentColor =
                view.context.takeIf { swapping && tint.enabled }?.let { context ->
                    { resolveAccentColor(context) }
                }
            val applied =
                target.apply(
                    view,
                    replacement = {
                        if (swapping) {
                            cached
                                ?.drawable
                                ?.constantState
                                ?.newDrawable(view.resources)
                                ?.mutate()
                        } else {
                            null
                        }
                    },
                    accentColor = accentColor,
                    hidden = switch.enabled && style == FingerprintStyles.NONE,
                    circleScale = if (switch.enabled && hideCircle.enabled) 0f else 1f,
                )
            if (!applied) return
            val styleLabel = style.ifEmpty { "stock" }
            val tintLabel =
                when {
                    !tint.enabled -> "off"
                    accentColor == null -> "no-icon"
                    else -> accentColor()?.let { "#%08x".format(it) } ?: "missing"
                }
            Logger.info(
                "applied tweak=$TWEAK style=$styleLabel hideCircle=${hideCircle.enabled} tint=$tintLabel on=${switch.enabled}",
            )
        } catch (e: Exception) {
            Logger.error("apply failed tweak=$TWEAK reason=${e.detail()}", e)
        } catch (e: LinkageError) {
            Logger.error("apply failed tweak=$TWEAK reason=${e.detail()}", e)
        }
    }

    companion object {
        fun resolve(cl: ClassLoader): FingerprintBindings =
            FingerprintBindings(
                onScreenIcon(cl)
                    ?: try {
                        deviceEntry(cl)
                    } catch (_: ClassNotFoundException) {
                        udfps(cl)
                    },
                Class
                    .forName("android.app.ActivityThread", false, cl)
                    .getMethod("currentApplication"),
            )

        // OxygenOS 16 SystemUI 16.00.12 BP2A.250605.015
        private fun onScreenIcon(cl: ClassLoader): OnScreenIconTarget? {
            val view =
                try {
                    cl.loadClass(OPLUS_ICON_VIEW)
                } catch (_: ClassNotFoundException) {
                    return null
                }
            return OnScreenIconTarget(
                view.requiredMethod("setVisibility", Int::class.javaPrimitiveType),
            )
        }

        private fun deviceEntry(cl: ClassLoader): DeviceEntryTarget {
            val view = cl.loadClass(ICON_VIEW)
            val iconType = cl.loadClass("$ICON_VIEW\$IconType")
            val getIconState =
                view.requiredMethod(
                    "getIconState",
                    iconType,
                    Boolean::class.javaPrimitiveType,
                )
            val transsionOptical =
                resolving {
                    cl
                        .loadClass(TRANSSION_OPTICAL_HELPER)
                        .requiredMethod(
                            "setKeyguardClientVisible",
                            Boolean::class.javaPrimitiveType,
                        )
                    cl
                        .loadClass(
                            TRANSSION_FINGERPRINT_STATE,
                        ).requiredField("OPTICAL_FINGERPRINT")
                        .getBoolean(null)
                }.getOrDefault(false)
            return DeviceEntryTarget(
                iconView = view.requiredField("iconView"),
                bgView = view.requiredField("bgView"),
                getIconState = getIconState,
                fingerprint =
                    iconType.enumConstants.first {
                        (it as Enum<*>).name == "FINGERPRINT"
                    },
                missingReason =
                    if (transsionOptical) {
                        ModuleConstants.TRANSSION_FINGERPRINT_REASON
                    } else {
                        NO_ICON_VIEW_REASON
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

private fun resolveAccentColor(context: Context): Int? {
    val attr =
        context.resources.getIdentifier(
            ACCENT_ATTR,
            "attr",
            ModuleConstants.SYSTEMUI_PACKAGE,
        )
    if (attr == 0) return null
    return context.obtainStyledAttributes(intArrayOf(attr)).use {
        if (it.hasValue(0)) it.getColor(0, 0) else null
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
                "reason=${ModuleConstants.SAMSUNG_FINGERPRINT_REASON}",
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
