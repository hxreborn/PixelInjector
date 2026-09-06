package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.os.Build
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.findFieldUpward
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.module
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

private const val TWEAK = "ExpandNotifications"
private const val ROW = "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow"

@Volatile private var current: RowBindings? = null

private val switch = Switch(Prefs.EXPAND_NOTIFICATIONS) { if (it) current?.hookOnce() }

internal val expandNotifications =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(switch),
        install = XposedModule::installExpandNotifications,
    )

private class RowBindings(
    val isExpanded: Method,
    val shouldShowPublic: Method,
    val isPromotedOngoing: Method,
    val onKeyguard: Field,
    val hasUserChangedExpansion: Field,
) {
    private val hooked = AtomicBoolean(false)

    companion object {
        fun resolve(cl: ClassLoader): RowBindings {
            val row = cl.loadClass(ROW)
            return RowBindings(
                isExpanded = row.requiredMethod("isExpanded", Boolean::class.javaPrimitiveType),
                shouldShowPublic = row.requiredMethod("shouldShowPublic"),
                isPromotedOngoing = row.requiredMethod("isPromotedOngoing"),
                onKeyguard =
                    row.findFieldUpward("mOnKeyguard")
                        ?: throw NoSuchFieldException("ExpandableNotificationRow.mOnKeyguard"),
                hasUserChangedExpansion = row.requiredField("mHasUserChangedExpansion"),
            )
        }
    }

    fun hookOnce() {
        if (!hooked.compareAndSet(false, true)) return
        module.hook(isExpanded).intercept { chain ->
            val expanded = chain.proceed()
            if (!switch.enabled || expanded == true) return@intercept expanded
            runCatching { forceExpanded(chain.thisObject, chain.getArg(0) as Boolean) }
                .getOrElse {
                    Logger.error("apply failed tweak=$TWEAK reason=${it.message}", it)
                    expanded
                }
        }
    }

    private fun forceExpanded(
        row: Any,
        allowOnKeyguard: Boolean,
    ): Boolean {
        if (hasUserChangedExpansion.getBoolean(row)) return false
        if (onKeyguard.getBoolean(row) && !allowOnKeyguard) return false
        if (isPromotedOngoing.invoke(row) == true) return false
        return shouldShowPublic.invoke(row) != true
    }
}

private fun XposedModule.installExpandNotifications(cl: ClassLoader): Boolean {
    val b =
        runCatching { RowBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${b.isExpanded.signature()}")
    current = b
    if (switch.enabled) b.hookOnce()
    return true
}
