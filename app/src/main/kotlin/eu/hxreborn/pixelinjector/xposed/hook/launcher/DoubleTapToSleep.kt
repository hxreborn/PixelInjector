package eu.hxreborn.pixelinjector.xposed.hook.launcher

import android.content.Context
import android.os.Build
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.hook.SleepTrigger
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

private const val TWEAK = "DoubleTapToSleep"
private const val LISTENER = "com.android.launcher3.touch.WorkspaceTouchListener"

private val trigger = SleepTrigger(TWEAK)
private val switch = Switch(Prefs.DOUBLE_TAP_TO_SLEEP)

internal val doubleTapToSleep =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.LAUNCHER),
        prefs = listOf(switch),
        install = XposedModule::installDoubleTapToSleep,
    )

private class LauncherBindings(
    val listener: Class<*>,
    val onDoubleTap: Method,
    private val currentApplication: Method,
) {
    fun application(): Context? =
        runCatching {
            currentApplication.invoke(null) as? Context
        }.getOrNull()

    companion object {
        fun resolve(cl: ClassLoader): LauncherBindings {
            val listener = cl.loadClass(LISTENER)
            val onDoubleTap =
                listener.methods.firstOrNull { it.name == "onDoubleTap" && it.parameterCount == 1 }
                    ?: throw NoSuchMethodException("WorkspaceTouchListener.onDoubleTap")
            val currentApplication =
                Class
                    .forName("android.app.ActivityThread", false, cl)
                    .getMethod("currentApplication")
            return LauncherBindings(listener, onDoubleTap, currentApplication)
        }
    }
}

private fun XposedModule.installDoubleTapToSleep(cl: ClassLoader): Boolean {
    val b =
        runCatching { LauncherBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${b.onDoubleTap.signature()}")
    hook(b.onDoubleTap).intercept { chain ->
        if (!switch.enabled || !b.listener.isInstance(chain.thisObject)) {
            return@intercept chain.proceed()
        }
        runCatching { b.application()?.let(trigger::sleep) }
            .onFailure { Logger.error("sleep failed tweak=$TWEAK reason=${it.message}", it) }
        chain.proceed()
    }
    return true
}
