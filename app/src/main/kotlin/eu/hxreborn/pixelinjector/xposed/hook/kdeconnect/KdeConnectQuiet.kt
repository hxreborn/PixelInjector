package eu.hxreborn.pixelinjector.xposed.hook.kdeconnect

import android.net.wifi.WifiManager
import android.os.Build
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

private const val TWEAK = "KdeConnectQuiet"

private val switch = Switch(Prefs.KDE_CONNECT_QUIET)

internal val kdeConnectQuiet =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.KDE_CONNECT),
        prefs = listOf(switch),
        install = XposedModule::installKdeConnectQuiet,
    )

private class LockBindings(
    val acquire: Method,
    val release: Method,
) {
    companion object {
        fun resolve(): LockBindings {
            val lock = WifiManager.MulticastLock::class.java
            return LockBindings(
                acquire = lock.requiredMethod("acquire"),
                release = lock.requiredMethod("release"),
            )
        }
    }
}

private fun XposedModule.installKdeConnectQuiet(cl: ClassLoader): Boolean {
    val bindings =
        resolving { LockBindings.resolve() }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=${bindings.acquire.signature()},${bindings.release.signature()}",
    )
    val acquireSkipLogged = AtomicBoolean(false)
    hook(bindings.acquire).intercept { chain ->
        if (!switch.enabled) return@intercept chain.proceed()
        if (acquireSkipLogged.compareAndSet(false, true)) {
            Logger.info("multicast lock acquire skipped tweak=$TWEAK")
        } else {
            Logger.debug { "multicast lock acquire skipped tweak=$TWEAK" }
        }
        null
    }
    hook(bindings.release).intercept { chain ->
        if ((chain.thisObject as WifiManager.MulticastLock).isHeld) chain.proceed() else null
    }
    return true
}
