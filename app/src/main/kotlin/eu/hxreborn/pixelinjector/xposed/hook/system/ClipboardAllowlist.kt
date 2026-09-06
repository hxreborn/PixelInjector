package eu.hxreborn.pixelinjector.xposed.hook.system

import android.os.Build
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.Value
import io.github.libxposed.api.XposedModule

private const val TWEAK = "ClipboardAllowlist"
private const val SERVICE = "com.android.server.clipboard.ClipboardService"

private val switch = Switch(Prefs.CLIPBOARD)
private val apps = Value(Prefs.CLIPBOARD_APPS) { HashSet(it) }

internal val clipboardAllowlist =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEM),
        prefs = listOf(switch, apps),
        install = XposedModule::installClipboardAllowlist,
    )

private fun XposedModule.installClipboardAllowlist(cl: ClassLoader): Boolean {
    val isDefaultIme =
        runCatching {
            cl
                .loadClass(SERVICE)
                .requiredMethod("isDefaultIme", Int::class.javaPrimitiveType, String::class.java)
        }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=ClipboardService#isDefaultIme(int,String) " +
                    "build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${isDefaultIme.signature()}")
    hook(isDefaultIme).intercept { chain ->
        if (!switch.enabled) return@intercept chain.proceed()
        if (chain.getArg(1) in apps.value) true else chain.proceed()
    }
    return true
}
