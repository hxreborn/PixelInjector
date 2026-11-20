package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import eu.hxreborn.pixelinjector.util.detail
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.xposed.Logger
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.ArrayList

private const val WINDOW_MANAGER_GLOBAL = "android.view.WindowManagerGlobal"
internal const val STATUS_BAR_VIEW = "com.android.systemui.statusbar.phone.PhoneStatusBarView"
internal const val KEYGUARD_STATE =
    "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl"
internal const val ICON_AREA_ID = "notification_icon_area"
private const val CLOCK = "com.android.systemui.statusbar.policy.Clock"
private const val QS_CLOCK_INDICATOR = "com.android.systemui.statusbar.policy.QSClockIndicatorView"
private const val START_SIDE_ID = "status_bar_start_side_except_heads_up"
private const val END_SIDE_ID = "status_bar_end_side_content"
private const val SYSTEM_ICONS_ID = "system_icons"
private const val CHIP_MARGIN_DP = 3

private fun View.childOf(ancestor: ViewGroup): View? {
    var child: View = this
    while (child.parent !== ancestor) child = child.parent as? View ?: return null
    return child
}

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun ViewGroup.idOf(name: String): Int =
    resources.getIdentifier(name, "id", context.packageName)

private fun startAnchor(bar: ViewGroup): Pair<ViewGroup, Int> {
    val iconArea = bar.findViewById<View>(bar.idOf(ICON_AREA_ID)) ?: error("no $ICON_AREA_ID")
    val startSide = bar.findViewById<View>(bar.idOf(START_SIDE_ID)) ?: iconArea.parent as ViewGroup
    val anchor = iconArea.childOf(startSide as ViewGroup) ?: iconArea
    val container = anchor.parent as ViewGroup
    return container to container.indexOfChild(anchor)
}

private fun endAnchor(
    bar: ViewGroup,
    tweak: String,
): Pair<ViewGroup, Int>? {
    val endSide = bar.findViewById<ViewGroup>(bar.idOf(END_SIDE_ID))
    val icons = bar.findViewById<View>(bar.idOf(SYSTEM_ICONS_ID))
    val anchor = endSide?.let { icons?.childOf(it) }
    if (endSide == null || anchor == null) {
        val member = if (endSide == null) END_SIDE_ID else SYSTEM_ICONS_ID
        Logger.error("target not found tweak=$tweak member=$member build=${Build.ID} reason=no-id")
        return null
    }
    return endSide to endSide.indexOfChild(anchor)
}

internal fun placeStatusBarChip(
    bar: ViewGroup,
    view: View,
    right: Boolean,
    width: Int,
    tweak: String,
) {
    (view.parent as? ViewGroup)?.removeView(view)
    val (container, index) = (if (right) endAnchor(bar, tweak) else null) ?: startAnchor(bar)
    val onEnd = container.id == bar.idOf(END_SIDE_ID)
    val margin = view.context.dp(CHIP_MARGIN_DP)
    container.addView(
        view,
        index,
        LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = margin
            if (onEnd) marginEnd = margin
            gravity = Gravity.CENTER_VERTICAL
        },
    )
    Logger.info("placed tweak=$tweak side=${if (onEnd) "right" else "left"}")
}

internal fun resolveStatusBarTint(cl: ClassLoader): List<Method> =
    listOf(CLOCK, QS_CLOCK_INDICATOR).mapNotNull { name ->
        resolving {
            cl
                .loadClass(
                    name,
                ).requiredMethod(
                    "onDarkChanged",
                    ArrayList::class.java,
                    Float::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
        }.getOrNull()
    }

internal fun XposedModule.installStatusBarTint(
    methods: List<Method>,
    tweak: String,
    onTint: (Int) -> Unit,
) {
    for (method in methods) {
        hook(method).intercept { chain ->
            val result = chain.proceed()
            isolateChipCallback(tweak, "tint") {
                onTint((chain.thisObject as? TextView)?.currentTextColor ?: chain.getArg(2) as Int)
            }
            result
        }
    }
}

internal inline fun isolateChipCallback(
    tweak: String,
    callback: String,
    action: () -> Unit,
) {
    try {
        action()
    } catch (e: Exception) {
        Logger.error("callback failed tweak=$tweak callback=$callback reason=${e.detail()}", e)
    } catch (e: LinkageError) {
        Logger.error("callback failed tweak=$tweak callback=$callback reason=${e.detail()}", e)
    }
}

private fun View.findInstance(type: Class<*>): View? {
    if (type.isInstance(this)) return this
    val group = this as? ViewGroup ?: return null
    for (index in 0 until group.childCount) {
        group.getChildAt(index).findInstance(type)?.let { return it }
    }
    return null
}

internal fun findLiveStatusBar(statusBarClass: Class<*>): ViewGroup? {
    val global = Class.forName(WINDOW_MANAGER_GLOBAL)
    val instance = global.getMethod("getInstance").invoke(null)
    val names = global.getMethod("getViewRootNames").invoke(instance) as Array<*>
    val getRootView = global.getMethod("getRootView", String::class.java)
    return names.firstNotNullOfOrNull { name ->
        (getRootView.invoke(instance, name) as? View)?.findInstance(statusBarClass) as? ViewGroup
    }
}
