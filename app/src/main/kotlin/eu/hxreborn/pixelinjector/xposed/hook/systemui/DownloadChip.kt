package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.app.KeyguardManager
import android.app.Notification
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.findFieldUpward
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.optionalField
import eu.hxreborn.pixelinjector.util.optionalMethod
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.requiredMethods
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.Value
import eu.hxreborn.pixelinjector.xposed.hook.DexKit
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

private const val TWEAK = "DownloadChip"
private const val STATUS_BAR_VIEW = "com.android.systemui.statusbar.phone.PhoneStatusBarView"
private const val CLOCK = "com.android.systemui.statusbar.policy.Clock"
private const val ICON_AREA_ID = "notification_icon_area"
private const val START_SIDE_ID = "status_bar_start_side_except_heads_up"
private const val END_SIDE_ID = "status_bar_end_side_content"
private const val SYSTEM_ICONS_ID = "system_icons"
private const val ICON_CONTAINER = "com.android.systemui.statusbar.phone.NotificationIconContainer"
private const val STATUS_BAR_ICON = "com.android.systemui.statusbar.StatusBarIconView"
private const val STATUS_BAR_PACKAGE = "com.android.systemui.statusbar"
private const val KEYGUARD_STATE = "$STATUS_BAR_PACKAGE.policy.KeyguardStateControllerImpl"
private const val HEADS_UP_MANAGER = "$STATUS_BAR_PACKAGE.notification.headsup.HeadsUpManagerImpl"
private const val HEADS_UP_BASE_MANAGER = "com.android.systemui.statusbar.policy.BaseHeadsUpManager"
private const val WINDOW_MANAGER_GLOBAL = "android.view.WindowManagerGlobal"
private const val STATE_CHIP = "chip"
private const val STATE_LISTENER = "listener"
private const val CHIP_WIDTH_DP = 56
private const val CHIP_PADDING_DP = 4
private const val CHIP_MARGIN_DP = 3
private const val ICON_SIZE_DP = 10
private const val ICON_GAP_DP = 3
private const val BAR_HEIGHT_DP = 3
private const val PILL_ALPHA = 0x33
private const val TRACK_ALPHA = 0x55
private const val ALPHA_SHIFT = 24
private const val ALPHA_MASK = 0x00FFFFFF

@Volatile private var chip: LinearLayout? = null

@Volatile private var listener: WeakReference<NotificationListenerService>? = null

@Volatile private var tint = Color.WHITE

@Volatile private var trackedNotification: StatusBarNotification? = null

@Volatile private var chipShown = false

@Volatile private var iconAreaId = 0

@Volatile private var locked = false

@Volatile private var headsUp = false

private var statusBar: WeakReference<ViewGroup>? = null

private val trackedNotificationKey: String? get() = trackedNotification?.key

private val switch =
    Switch(Prefs.DOWNLOAD_CHIP) { resync() }
private val rightSide =
    Value(Prefs.DOWNLOAD_CHIP_SIDE, onLoad = { chip?.post(::place) }) {
        it ==
            Prefs.DOWNLOAD_CHIP_RIGHT
    }
private val appIcon =
    Value(Prefs.DOWNLOAD_CHIP_ICON, onLoad = { chip?.post(::refreshIcon) }) {
        it ==
            Prefs.DOWNLOAD_CHIP_APP
    }
private val hideLocked = Switch(Prefs.DOWNLOAD_CHIP_HIDE_LOCKED) { chip?.post(::render) }
private val hideHeadsUp = Switch(Prefs.DOWNLOAD_CHIP_HIDE_HEADS_UP) { chip?.post(::render) }
private val hideStatusIcon =
    Switch(Prefs.DOWNLOAD_CHIP_HIDE_STATUS_ICON) { chip?.post(::relayoutIcons) }

internal val downloadChip =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(switch, rightSide, appIcon, hideLocked, hideHeadsUp, hideStatusIcon),
        install = XposedModule::installDownloadChip,
        saveState = {
            hashMapOf<String, Any?>(STATE_CHIP to chip, STATE_LISTENER to listener?.get())
        },
        restoreState = { saved ->
            val map = saved as? Map<*, *>
            chip = map?.get(STATE_CHIP) as? LinearLayout
            (map?.get(STATE_LISTENER) as? NotificationListenerService)?.let {
                listener = WeakReference(it)
            }
            resync()
        },
    )

private class NotificationListenerBindings(
    val posted: List<Method>,
    val removed: List<Method>,
    val connected: List<Method>,
) {
    fun isListenerInstance(service: Any?): Boolean =
        posted.any { it.declaringClass.isInstance(service) }

    companion object {
        fun resolve(cl: ClassLoader): NotificationListenerBindings {
            val rejectedListeners = mutableListOf<String>()
            val postedCandidates = findPostedCallbacks(cl)
            val listeners =
                postedCandidates.filter(::isListenerCallback).mapNotNull { posted ->
                    resolving { resolveListenerCallbacks(posted) }.getOrElse {
                        rejectedListeners += "${posted.declaringClass.name}:${it.member()}"
                        null
                    }
                }
            if (listeners.isEmpty()) {
                throw NoSuchMethodException(
                    "NotificationListenerService.onNotificationPosted(" +
                        "StatusBarNotification,RankingMap) candidates=${postedCandidates.size} " +
                        "rejected=${rejectedListeners.joinToString()}",
                )
            }
            return NotificationListenerBindings(
                posted = listeners.flatMap { it.posted }.distinct(),
                removed = listeners.flatMap { it.removed }.distinct(),
                connected = listeners.flatMap { it.connected }.distinct(),
            )
        }

        private fun findPostedCallbacks(cl: ClassLoader): List<Method> =
            DexKit.methods(TWEAK, cl) { bridge ->
                bridge.findMethod {
                    matcher {
                        name("onNotificationPosted")
                        returnType("void")
                        paramTypes(
                            StatusBarNotification::class.java.name,
                            NotificationListenerService.RankingMap::class.java.name,
                        )
                    }
                }
            }

        private fun resolveListenerCallbacks(posted: Method): NotificationListenerBindings {
            val service = posted.declaringClass
            val removed =
                service.methods.filter {
                    it.name == "onNotificationRemoved" &&
                        it.parameterTypes.firstOrNull() == StatusBarNotification::class.java &&
                        isListenerCallback(it)
                }
            if (removed.isEmpty()) {
                throw NoSuchMethodException("${service.name}.onNotificationRemoved")
            }
            val connected = service.getMethod("onListenerConnected")
            if (!isListenerCallback(connected)) {
                throw NoSuchMethodException("${service.name}.onListenerConnected")
            }
            return NotificationListenerBindings(listOf(posted), removed, listOf(connected))
        }

        private fun isListenerCallback(method: Method): Boolean =
            method.declaringClass != NotificationListenerService::class.java &&
                NotificationListenerService::class.java.isAssignableFrom(method.declaringClass) &&
                method.returnType == Void.TYPE &&
                Modifier.isPublic(method.modifiers) &&
                !Modifier.isAbstract(method.modifiers) &&
                !Modifier.isStatic(method.modifiers)
    }
}

private class ChipBindings(
    val onFinishInflate: Method,
    val listenerCallbacks: NotificationListenerBindings,
    val onDarkChanged: Method?,
    val keyguardState: Method?,
    val headsUpChanges: List<Method>,
    val headsUpQuery: Method?,
    val iconHider: IconHider?,
) {
    companion object {
        fun resolve(cl: ClassLoader): ChipBindings {
            val listenerCallbacks = NotificationListenerBindings.resolve(cl)
            val headsUpManager =
                resolving { cl.loadClass(HEADS_UP_MANAGER) }.getOrNull()
                    ?: resolving { cl.loadClass(HEADS_UP_BASE_MANAGER) }.getOrNull()
            return ChipBindings(
                onFinishInflate = cl.loadClass(STATUS_BAR_VIEW).requiredMethod("onFinishInflate"),
                listenerCallbacks = listenerCallbacks,
                onDarkChanged =
                    resolving {
                        cl.loadClass(CLOCK).requiredMethod(
                            "onDarkChanged",
                            java.util.ArrayList::class.java,
                            Float::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType,
                        )
                    }.getOrNull(),
                keyguardState =
                    resolving {
                        cl.loadClass(KEYGUARD_STATE).requiredMethod(
                            "notifyKeyguardState",
                            Boolean::class.javaPrimitiveType,
                            Boolean::class.javaPrimitiveType,
                        )
                    }.getOrNull(),
                headsUpChanges = headsUpManager?.let(::headsUpChangeMethods).orEmpty(),
                headsUpQuery = headsUpManager?.let(::headsUpQueryMethod),
                iconHider = resolving { IconHider.resolve(cl) }.getOrNull(),
            )
        }
    }
}

private class IconHider(
    val calculate: Method,
    val states: Field,
    val notification: Field,
    val appear: Field,
    val hidden: Field,
) {
    fun hide(
        container: ViewGroup,
        key: String,
    ): List<Triple<Any, Float, Boolean>> {
        if ((container.parent as? View)?.id != iconAreaId) return emptyList()
        val map = states.get(container) as? Map<*, *> ?: return emptyList()
        val saved = mutableListOf<Triple<Any, Float, Boolean>>()
        for (index in 0 until container.childCount) {
            val icon = container.getChildAt(index)
            if ((notification.get(icon) as? StatusBarNotification)?.key != key) continue
            val state = map[icon] ?: continue
            saved += Triple(state, appear.getFloat(state), hidden.getBoolean(state))
            appear.setFloat(state, 0f)
            hidden.setBoolean(state, true)
        }
        return saved
    }

    fun restore(saved: List<Triple<Any, Float, Boolean>>) {
        for ((state, amount, wasHidden) in saved) {
            appear.setFloat(state, amount)
            hidden.setBoolean(state, wasHidden)
        }
    }

    companion object {
        fun resolve(cl: ClassLoader): IconHider {
            val container = cl.loadClass(ICON_CONTAINER)
            val state = cl.loadClass("$ICON_CONTAINER\$IconState")
            return IconHider(
                calculate =
                    container.optionalMethod("calculateIconXTranslations", 0)
                        ?: error("no calculate"),
                states = container.optionalField("mIconStates") ?: error("no states"),
                notification =
                    cl.loadClass(STATUS_BAR_ICON).findFieldUpward("mNotification")
                        ?: error("no notification"),
                appear = state.findFieldUpward("iconAppearAmount") ?: error("no appear"),
                hidden = state.findFieldUpward("hidden") ?: error("no hidden"),
            )
        }
    }
}

private fun findInheritedMethod(
    type: Class<*>,
    name: String,
): Method? =
    generateSequence(type) {
        it.superclass
    }.firstNotNullOfOrNull { it.optionalMethod(name) }

private fun headsUpChangeMethods(manager: Class<*>): List<Method> =
    listOf(
        "onEntryAdded",
        "onEntryRemoved",
        "showNotification",
    ).mapNotNull { findInheritedMethod(manager, it) }

private fun headsUpQueryMethod(manager: Class<*>): Method? =
    generateSequence(manager) { it.superclass }.firstNotNullOfOrNull {
        it.optionalMethod("hasPinnedHeadsUp", 0) ?: it.optionalMethod("hasNotifications", 0)
    }

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private fun buildChip(context: Context): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        val pad = context.dp(CHIP_PADDING_DP)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply { cornerRadius = Float.MAX_VALUE }
        addView(
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams =
                    LinearLayout
                        .LayoutParams(context.dp(ICON_SIZE_DP), context.dp(ICON_SIZE_DP))
                        .apply { marginEnd = context.dp(ICON_GAP_DP) }
            },
        )
        addView(
            ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = false
                layoutParams = LinearLayout.LayoutParams(0, context.dp(BAR_HEIGHT_DP), 1f)
            },
        )
        applyTint(this)
    }

private fun applyTint(view: LinearLayout) {
    val color = tint
    (view.background as GradientDrawable).setColor(withAlpha(color, PILL_ALPHA))
    (view.getChildAt(0) as ImageView).imageTintList =
        if (appIcon.value) null else ColorStateList.valueOf(color)
    (view.getChildAt(1) as ProgressBar).apply {
        progressTintList = ColorStateList.valueOf(color)
        progressBackgroundTintList = ColorStateList.valueOf(withAlpha(color, TRACK_ALPHA))
    }
}

private fun withAlpha(
    color: Int,
    alpha: Int,
): Int = (alpha shl ALPHA_SHIFT) or (color and ALPHA_MASK)

private fun hasProgress(notification: Notification): Boolean {
    val extras = notification.extras ?: return false
    return extras.containsKey(Notification.EXTRA_PROGRESS) &&
        extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 &&
        !extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
}

private fun resync() {
    chip?.post {
        trackedNotification = null
        render()
        if (switch.enabled) replay()
    }
}

private fun render() {
    val shown =
        switch.enabled &&
            trackedNotificationKey != null &&
            !(hideLocked.enabled && locked) &&
            !(hideHeadsUp.enabled && headsUp)
    chip?.visibility = if (shown) View.VISIBLE else View.GONE
    if (shown != chipShown) {
        chipShown = shown
        relayoutIcons()
    }
}

private fun relayoutIcons() {
    val area = statusBar?.get()?.findViewById<ViewGroup>(iconAreaId) ?: return
    for (index in 0 until area.childCount) area.getChildAt(index).requestLayout()
}

private fun resolveIcon(
    context: Context,
    sbn: StatusBarNotification,
): Drawable? =
    if (appIcon.value) {
        runCatching { context.packageManager.getApplicationIcon(sbn.packageName) }.getOrNull()
    } else {
        sbn.notification.smallIcon?.loadDrawable(context)
    }

private fun refreshIcon() {
    val view = chip ?: return
    val sbn = trackedNotification ?: return applyTint(view)
    (view.getChildAt(0) as ImageView).setImageDrawable(resolveIcon(view.context, sbn))
    applyTint(view)
}

private fun setLocked(value: Boolean) {
    chip?.post {
        locked = value
        render()
    }
}

private fun setHeadsUp(value: Boolean) {
    chip?.post {
        headsUp = value
        render()
    }
}

private fun replay() {
    runCatching { listener?.get()?.activeNotifications?.forEach(::onPosted) }
        .onFailure { Logger.error("replay failed tweak=$TWEAK reason=${it.message}", it) }
}

private fun postNotificationUpdate(
    callback: String,
    sbn: StatusBarNotification,
    update: (StatusBarNotification) -> Unit,
) {
    chip?.post {
        try {
            update(sbn)
        } catch (e: Exception) {
            Logger.error(
                "notification callback failed tweak=$TWEAK callback=$callback " +
                    "key=${sbn.key} reason=${e.message}",
                e,
            )
        } catch (e: LinkageError) {
            Logger.error(
                "notification callback failed tweak=$TWEAK callback=$callback " +
                    "key=${sbn.key} reason=${e.message}",
                e,
            )
        }
    }
}

private fun onPosted(sbn: StatusBarNotification) {
    val view = chip ?: return
    if (!switch.enabled) return
    val notification = sbn.notification
    if (!hasProgress(notification)) {
        if (sbn.key == trackedNotificationKey) release()
        return
    }
    if (trackedNotificationKey != null && trackedNotificationKey != sbn.key) return
    val extras = notification.extras
    if (trackedNotificationKey == null) {
        trackedNotification = sbn
        refreshIcon()
    }
    (view.getChildAt(1) as ProgressBar).apply {
        max = extras.getInt(Notification.EXTRA_PROGRESS_MAX)
        progress = extras.getInt(Notification.EXTRA_PROGRESS)
    }
    render()
}

private fun onRemoved(sbn: StatusBarNotification) {
    if (sbn.key == trackedNotificationKey) release()
}

private fun release() {
    trackedNotification = null
    render()
    if (switch.enabled) replay()
}

private fun View.childOf(ancestor: ViewGroup): View? {
    var child: View = this
    while (child.parent !== ancestor) child = child.parent as? View ?: return null
    return child
}

private fun ViewGroup.idOf(name: String): Int =
    resources.getIdentifier(name, "id", context.packageName)

private fun startAnchor(bar: ViewGroup): Pair<ViewGroup, Int> {
    val iconArea = bar.findViewById<View>(iconAreaId) ?: error("no $ICON_AREA_ID")
    val startSide = bar.findViewById<View>(bar.idOf(START_SIDE_ID)) ?: iconArea.parent as ViewGroup
    val anchor = iconArea.childOf(startSide as ViewGroup) ?: iconArea
    val container = anchor.parent as ViewGroup
    return container to container.indexOfChild(anchor)
}

private fun endAnchor(bar: ViewGroup): Pair<ViewGroup, Int>? {
    val endSide = bar.findViewById<ViewGroup>(bar.idOf(END_SIDE_ID))
    val icons = bar.findViewById<View>(bar.idOf(SYSTEM_ICONS_ID))
    val anchor = endSide?.let { icons?.childOf(it) }
    if (endSide == null || anchor == null) {
        val member = if (endSide == null) END_SIDE_ID else SYSTEM_ICONS_ID
        Logger.error("target not found tweak=$TWEAK member=$member build=${Build.ID} reason=no-id")
        return null
    }
    return endSide to endSide.indexOfChild(anchor)
}

private fun place() {
    val bar = statusBar?.get() ?: return
    val view = chip ?: return
    runCatching {
        (view.parent as? ViewGroup)?.removeView(view)
        val (container, index) = (if (rightSide.value) endAnchor(bar) else null) ?: startAnchor(bar)
        val margin = view.context.dp(CHIP_MARGIN_DP)
        val onEnd = container.id == bar.idOf(END_SIDE_ID)
        val params =
            LinearLayout
                .LayoutParams(
                    view.context.dp(CHIP_WIDTH_DP),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = margin
                    if (onEnd) marginEnd = margin
                    gravity = Gravity.CENTER_VERTICAL
                }
        container.addView(view, index, params)
        Logger.info("placed tweak=$TWEAK side=${if (onEnd) "right" else "left"}")
    }.onFailure { Logger.error("place failed tweak=$TWEAK reason=${it.message}", it) }
}

private fun attach(bar: ViewGroup) {
    iconAreaId = bar.idOf(ICON_AREA_ID)
    statusBar = WeakReference(bar)
    chip = chip?.takeIf { it.context === bar.context } ?: buildChip(bar.context)
    locked = bar.context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
    place()
    Logger.info("attached tweak=$TWEAK")
    resync()
}

private fun View.findInstance(type: Class<*>): View? {
    if (type.isInstance(this)) return this
    val group = this as? ViewGroup ?: return null
    for (index in 0 until group.childCount) {
        group.getChildAt(index).findInstance(type)?.let { return it }
    }
    return null
}

private fun findLiveStatusBar(statusBarClass: Class<*>): ViewGroup? {
    val global = Class.forName(WINDOW_MANAGER_GLOBAL)
    val instance = global.getMethod("getInstance").invoke(null)
    val names = global.getMethod("getViewRootNames").invoke(instance) as Array<*>
    val getRootView = global.getMethod("getRootView", String::class.java)
    return names.firstNotNullOfOrNull { name ->
        (getRootView.invoke(instance, name) as? View)?.findInstance(statusBarClass) as? ViewGroup
    }
}

private fun attachLive(statusBarClass: Class<*>) {
    if (statusBar != null) return
    runCatching { findLiveStatusBar(statusBarClass)?.let(::attach) }
        .onFailure { Logger.error("attach failed tweak=$TWEAK reason=${it.message}", it) }
}

private fun XposedModule.installDownloadChip(cl: ClassLoader): Boolean {
    val bindings =
        resolving { ChipBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    val listenerCallbacks = bindings.listenerCallbacks
    val headsUpResolved = bindings.headsUpChanges.isNotEmpty() && bindings.headsUpQuery != null
    Logger.info(
        "resolved tweak=$TWEAK members=${bindings.onFinishInflate.signature()}," +
            "${listenerCallbacks.posted.joinToString { it.signature() }}," +
            "${listenerCallbacks.removed.size}xonNotificationRemoved," +
            "${listenerCallbacks.connected.joinToString { it.signature() }}," +
            "${bindings.onDarkChanged?.signature() ?: "no-tint"}," +
            "${bindings.keyguardState?.signature() ?: "no-lock"}," +
            "${if (headsUpResolved) "heads-up" else "no-heads-up"}," +
            if (bindings.iconHider != null) "hide-icon" else "no-hide-icon",
    )
    hook(bindings.onFinishInflate).intercept { chain ->
        val result = chain.proceed()
        runCatching { attach(chain.thisObject as ViewGroup) }
            .onFailure { Logger.error("attach failed tweak=$TWEAK reason=${it.message}", it) }
        result
    }
    for (method in listenerCallbacks.posted) {
        hook(method).intercept { chain ->
            val result = chain.proceed()
            if (!listenerCallbacks.isListenerInstance(chain.thisObject)) return@intercept result
            listener = WeakReference(chain.thisObject as NotificationListenerService)
            val sbn = chain.getArg(0) as StatusBarNotification
            postNotificationUpdate("onNotificationPosted", sbn, ::onPosted)
            result
        }
    }
    for (method in listenerCallbacks.removed) {
        hook(method).intercept { chain ->
            val result = chain.proceed()
            if (!listenerCallbacks.isListenerInstance(chain.thisObject)) return@intercept result
            val sbn = chain.getArg(0) as StatusBarNotification
            postNotificationUpdate("onNotificationRemoved", sbn, ::onRemoved)
            result
        }
    }
    for (method in listenerCallbacks.connected) {
        hook(method).intercept { chain ->
            val result = chain.proceed()
            if (!listenerCallbacks.isListenerInstance(chain.thisObject)) return@intercept result
            listener = WeakReference(chain.thisObject as NotificationListenerService)
            resync()
            result
        }
    }
    bindings.onDarkChanged?.let { method ->
        hook(method).intercept { chain ->
            val result = chain.proceed()
            tint = chain.getArg(2) as Int
            chip?.let { view -> view.post { applyTint(view) } }
            result
        }
    }
    bindings.keyguardState?.let { method ->
        hook(method).intercept { chain ->
            val result = chain.proceed()
            setLocked(chain.getArg(0) as Boolean)
            result
        }
    }
    val query = bindings.headsUpQuery
    if (query != null && headsUpResolved) {
        for (method in bindings.headsUpChanges) {
            hook(method).intercept { chain ->
                val result = chain.proceed()
                (
                    runCatching {
                        query.invoke(
                            chain.thisObject,
                        )
                    }.getOrNull() as? Boolean
                )?.let(::setHeadsUp)
                result
            }
        }
    }
    bindings.iconHider?.let { hider ->
        hook(hider.calculate).intercept { chain ->
            val key = if (hideStatusIcon.enabled && chipShown) trackedNotificationKey else null
            val saved =
                key
                    ?.let {
                        runCatching {
                            hider.hide(
                                chain.thisObject as ViewGroup,
                                it,
                            )
                        }.getOrNull()
                    }.orEmpty()
            try {
                chain.proceed()
            } finally {
                runCatching { hider.restore(saved) }
            }
        }
    }
    val statusBarClass = bindings.onFinishInflate.declaringClass
    Handler(Looper.getMainLooper()).post { attachLive(statusBarClass) }
    return true
}
