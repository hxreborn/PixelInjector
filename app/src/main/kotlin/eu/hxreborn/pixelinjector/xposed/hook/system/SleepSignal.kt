package eu.hxreborn.pixelinjector.xposed.hook.system

import android.os.Binder
import android.os.Build
import android.os.SystemClock
import eu.hxreborn.pixelinjector.ModuleConstants.SLEEP_CALLERS
import eu.hxreborn.pixelinjector.util.optionalMethod
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.hook.SLEEP_LEVEL
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

private const val TWEAK = "SleepSignal"
private const val BINDER_SERVICE = "com.android.server.power.PowerManagerService\$BinderService"

internal val sleepSignal =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEM),
        install = XposedModule::installSleepSignal,
    )

private class PowerBindings(
    val levelChecks: List<Method>,
    val goToSleep: Method,
    val getPackageManager: Method,
    val packagesForUid: Method,
) {
    companion object {
        fun resolve(cl: ClassLoader): PowerBindings {
            val binderService = cl.loadClass(BINDER_SERVICE)
            return PowerBindings(
                levelChecks =
                    listOfNotNull(
                        binderService.requiredMethod(
                            "isWakeLockLevelSupported",
                            Int::class.javaPrimitiveType,
                        ),
                        binderService.optionalMethod("isWakeLockLevelSupportedWithDisplayId", 2),
                    ),
                goToSleep =
                    binderService.requiredMethod(
                        "goToSleep",
                        Long::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                    ),
                getPackageManager =
                    cl.loadClass("android.app.AppGlobals").requiredMethod("getPackageManager"),
                packagesForUid =
                    cl
                        .loadClass("android.content.pm.IPackageManager")
                        .requiredMethod("getPackagesForUid", Int::class.javaPrimitiveType),
            )
        }
    }

    fun fromAllowedCaller(uid: Int): Boolean {
        val packages =
            runCatching {
                val packageManager = getPackageManager.invoke(null) ?: return false
                packagesForUid.invoke(packageManager, uid) as Array<*>?
            }.getOrNull().orEmpty()
        return packages.any { it in SLEEP_CALLERS }
    }

    fun sleep(binderService: Any) {
        val token = Binder.clearCallingIdentity()
        try {
            goToSleep.invoke(binderService, SystemClock.uptimeMillis(), 0, 0)
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }
}

private fun XposedModule.installSleepSignal(cl: ClassLoader): Boolean {
    val b =
        runCatching { PowerBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=${b.levelChecks.joinToString(",") { it.signature() }}",
    )
    for (method in b.levelChecks) {
        hook(method).intercept { chain ->
            if (chain.getArg(0) as Int != SLEEP_LEVEL) return@intercept chain.proceed()
            val uid = Binder.getCallingUid()
            if (!b.fromAllowedCaller(uid)) {
                Logger.warn("sleep rejected uid=$uid tweak=$TWEAK")
                return@intercept false
            }
            runCatching { b.sleep(chain.thisObject) }
                .onSuccess { Logger.info("sleep uid=$uid tweak=$TWEAK") }
                .onFailure {
                    Logger.error(
                        "sleep failed uid=$uid tweak=$TWEAK reason=${it.cause?.message ?: it.message}",
                        it,
                    )
                }.isSuccess
        }
    }
    return true
}
