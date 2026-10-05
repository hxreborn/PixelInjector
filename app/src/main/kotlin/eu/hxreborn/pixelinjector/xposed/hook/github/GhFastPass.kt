package eu.hxreborn.pixelinjector.xposed.hook.github

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier

private const val TWEAK = "GhFastPass"
private const val ACTIVITY = "com.github.android.twofactor.TwoFactorActivity"
private const val DIALOG = "com.github.android.twofactor.TwoFactorDialog"
private const val APPROVED = "FINISHED_APPROVED"

private val switch = Switch(Prefs.GH_FAST_PASS)

@Volatile
private var pendingActivity: WeakReference<Activity>? = null

internal val ghFastPass =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.GITHUB),
        prefs = listOf(switch),
        install = XposedModule::installGhFastPass,
    )

private class TwoFactorBindings(
    val onCreate: Method,
    val stateMapper: Method,
) {
    companion object {
        fun resolve(cl: ClassLoader): TwoFactorBindings {
            val dialog = cl.loadClass(DIALOG)
            val state =
                dialog.declaredClasses.firstOrNull { cls ->
                    cls.isEnum &&
                        cls.enumConstants.orEmpty().any { (it as Enum<*>).name == APPROVED }
                } ?: throw NoSuchFieldException("TwoFactorDialog.$APPROVED")
            val mapper =
                dialog.declaredMethods.firstOrNull { m ->
                    Modifier.isStatic(m.modifiers) && m.parameterCount == 1 && m.returnType == state
                } ?: throw NoSuchMethodException("TwoFactorDialog.stateMapper(?)")
            return TwoFactorBindings(
                onCreate = cl.loadClass(ACTIVITY).requiredMethod("onCreate", Bundle::class.java),
                stateMapper = mapper.apply { isAccessible = true },
            )
        }
    }
}

private fun XposedModule.installGhFastPass(cl: ClassLoader): Boolean {
    val bindings =
        runCatching { TwoFactorBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=${bindings.onCreate.signature()},${bindings.stateMapper.signature()}",
    )
    hook(bindings.onCreate).intercept { chain ->
        chain.proceed()
        pendingActivity = WeakReference(chain.thisObject as Activity)
    }
    hook(bindings.stateMapper).intercept { chain ->
        val result = chain.proceed()
        if (!switch.enabled || (result as? Enum<*>)?.name != APPROVED) return@intercept result
        val activity =
            pendingActivity?.get()?.takeUnless { it.isFinishing } ?: return@intercept result
        pendingActivity = null
        Logger.info("dialog dismissed tweak=$TWEAK")
        Handler(Looper.getMainLooper()).post { activity.finish() }
        result
    }
    return true
}
