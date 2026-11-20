package eu.hxreborn.pixelinjector.xposed

import android.app.Application
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.SystemClock
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.InstallDiagnostics
import eu.hxreborn.pixelinjector.ModuleConstants
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.detail
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.xposed.hook.DexKit
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.io.File
import kotlin.concurrent.thread

private const val SAVED_CLASSLOADER = "classLoader"
private const val SAVED_DEXKIT = "dexkit"
private const val SAVED_GUARD_FILE = "guardFile"
private const val SAVED_HOST_APK = "hostApk"
private const val SYSTEM_SERVER_HOST = "system_server"
private const val ACTIVITY_THREAD = "android.app.ActivityThread"
private const val TIMER_JOIN_MS = 1_000L
private val PERSISTENT_TARGETS = setOf(Target.SYSTEM, Target.SYSTEMUI)

private const val SYSTEM_DIR_NAME = "system"
private const val STABLE_AFTER_MS = 60_000L

private class GuardSite(
    val file: File,
    val hostApk: String,
)

@PublishedApi
internal lateinit var module: PixelInjectorModule
    private set

class PixelInjectorModule : XposedModule() {
    private lateinit var process: String
    private var systemServer = false
    private var target: Target? = null
    private var classLoader: ClassLoader? = null
    private var remotePrefs: SharedPreferences? = null
    private var installed = false

    @Volatile private var installInProgress = false
    private var guardSite: GuardSite? = null
    private var stableTimer: Thread? = null

    @Volatile private var activeGuard: CrashGuard? = null

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            val target = target ?: return@OnSharedPreferenceChangeListener
            try {
                loadHookPrefs(sp, target, process, key)
            } catch (e: Exception) {
                Logger.error("prefs reload failed reason=${e.detail()}", e)
            } catch (e: LinkageError) {
                Logger.error("prefs reload failed reason=${e.detail()}", e)
            }
        }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        module = this
        process = param.processName
        systemServer = param.isSystemServer
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        installTweaks(
            process,
            systemServer = true,
            param.classLoader,
            origin = "boot",
            GuardSite(
                InstallDiagnostics.file(systemDataDir(), process),
                SYSTEM_SERVER_HOST,
            ),
        )
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return
        if (installed) {
            Logger.debug {
                "install skipped proc=$process pkg=${param.packageName} reason=already-installed"
            }
            return
        }
        installTweaks(
            process,
            systemServer,
            param.classLoader,
            origin = "boot",
            packageGuardSite(param.applicationInfo),
        )
    }

    private fun packageGuardSite(info: ApplicationInfo?): GuardSite? {
        val dataDir = info?.deviceProtectedDataDir ?: return null
        return GuardSite(InstallDiagnostics.file(File(dataDir), process), info.sourceDir.orEmpty())
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        if (installInProgress) {
            Logger.warn("hot reload rejected reason=install-in-progress")
            return false
        }
        val cl = classLoader
        val target = target
        if (cl == null) {
            Logger.warn("hot reload rejected reason=no-classloader")
            return false
        }
        stableTimer?.let {
            it.interrupt()
            it.join(TIMER_JOIN_MS)
            if (it.isAlive) {
                Logger.warn("hot reload rejected reason=guard-timer-alive")
                return false
            }
        }
        val state = HashMap<String, Any?>()
        val attempted = ArrayList<Tweak>()
        for (tweak in target?.let(::tweaksFor).orEmpty()) {
            attempted.add(tweak)
            try {
                tweak.saveState()?.let { state[tweak.key] = it }
            } catch (e: Exception) {
                return saveFailed(tweak, e, attempted)
            } catch (e: LinkageError) {
                return saveFailed(tweak, e, attempted)
            }
        }
        state[SAVED_CLASSLOADER] = cl
        state[SAVED_DEXKIT] = DexKit.save()
        guardSite?.let {
            state[SAVED_GUARD_FILE] = it.file.path
            state[SAVED_HOST_APK] = it.hostApk
        }
        param.setSavedInstanceState(state)
        remotePrefs?.unregisterOnSharedPreferenceChangeListener(prefsListener)
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        module = this
        val hooksLeft = param.oldHookHandles.count { !unhookIsolated(it) }
        if (hooksLeft > 0) {
            Logger.error("hot reload aborted reason=old-hooks-left count=$hooksLeft")
            return
        }
        val saved = param.savedInstanceState as? Map<*, *>
        val cl = saved?.get(SAVED_CLASSLOADER) as? ClassLoader
        if (cl == null) {
            Logger.error("hot reload aborted reason=no-classloader")
            return
        }
        DexKit.restore(saved[SAVED_DEXKIT])
        installTweaks(
            param.processName,
            param.isSystemServer,
            cl,
            origin = "reload",
            reloadGuardSite(saved, param.isSystemServer, param.processName),
            saved,
        )
    }

    private fun saveFailed(
        tweak: Tweak,
        cause: Throwable,
        attempted: List<Tweak>,
    ): Boolean {
        Logger.error(
            "hot reload rejected reason=save-failed tweak=${tweak.key} cause=${cause.detail()}",
            cause,
        )
        attempted.forEach(::resumeAfterRejection)
        activeGuard?.let(::markStableLater)
        return false
    }

    private fun resumeAfterRejection(tweak: Tweak) {
        try {
            tweak.onReloadRejected()
        } catch (e: Exception) {
            resumeFailed(tweak, e)
        } catch (e: LinkageError) {
            resumeFailed(tweak, e)
        }
    }

    private fun resumeFailed(
        tweak: Tweak,
        cause: Throwable,
    ) = Logger.error("resume failed tweak=${tweak.key} reason=${cause.detail()}", cause)

    private fun unhookIsolated(handle: HookHandle): Boolean =
        try {
            handle.unhook()
            true
        } catch (e: Exception) {
            oldHookRemovalFailed(handle, e)
        } catch (e: LinkageError) {
            oldHookRemovalFailed(handle, e)
        }

    private fun oldHookRemovalFailed(
        handle: HookHandle,
        cause: Throwable,
    ): Boolean {
        Logger.error(
            "hot reload old hook removal failed member=${handle.executable} reason=${cause.detail()}",
            cause,
        )
        return false
    }

    private fun restoreFailed(
        tweak: Tweak,
        cause: Throwable,
    ) = Logger.error("restore failed tweak=${tweak.key} reason=${cause.detail()}", cause)

    private fun loadRemotePrefs(target: Target) {
        try {
            val prefs = getRemotePreferences(Prefs.GROUP)
            remotePrefs = prefs
            prefs.registerOnSharedPreferenceChangeListener(prefsListener)
            loadHookPrefs(prefs, target, process)
        } catch (e: Exception) {
            remotePrefsUnavailable(e)
        } catch (e: LinkageError) {
            remotePrefsUnavailable(e)
        }
    }

    private fun remotePrefsUnavailable(cause: Throwable) =
        Logger.warn("remote prefs unavailable defaults=true reason=${cause.detail()}", cause)

    private fun reloadGuardSite(
        saved: Map<*, *>,
        systemServer: Boolean,
        processName: String,
    ): GuardSite? {
        val application = runningApplication()
        val dataDir =
            if (systemServer) systemDataDir() else application?.deviceProtectedDataDir?.let(::File)
        val file =
            (saved[SAVED_GUARD_FILE] as? String)?.let(::File)
                ?: dataDir?.let { InstallDiagnostics.file(it, processName) }
                ?: return null
        val hostApk =
            saved[SAVED_HOST_APK] as? String
                ?: if (systemServer) SYSTEM_SERVER_HOST else application?.sourceDir.orEmpty()
        return GuardSite(file, hostApk)
    }

    private fun runningApplication(): ApplicationInfo? =
        resolving {
            val application =
                Class
                    .forName(
                        ACTIVITY_THREAD,
                    ).getMethod("currentApplication")
                    .invoke(null)
            (application as? Application)?.applicationInfo
        }.getOrNull()

    private fun systemDataDir(): File = File(Environment.getDataDirectory(), SYSTEM_DIR_NAME)

    private fun openGuard(
        site: GuardSite,
        countEarlyDeaths: Boolean,
        generation: String,
    ): CrashGuard? =
        try {
            CrashGuard
                .open(
                    site.file,
                    "${BuildConfig.VERSION_CODE}|${Build.FINGERPRINT}|${site.hostApk}",
                    { System.currentTimeMillis() - SystemClock.elapsedRealtime() },
                    generation,
                    countEarlyDeaths,
                ).also(::logGuard)
        } catch (e: Exception) {
            guardUnavailable(site.file, e)
        } catch (e: LinkageError) {
            guardUnavailable(site.file, e)
        }

    private fun logGuard(guard: CrashGuard) {
        val build = Build.ID
        guard.crashedDuringInstall?.let {
            Logger.error(
                "quarantined tweak=$it proc=$process build=$build reason=crashed-during-install",
            )
        }
        if (guard.safeMode) {
            Logger.error(
                "safe mode proc=$process build=$build earlyDeaths=${guard.earlyDeaths} " +
                    "reason=crash-loop",
            )
        }
        Logger.info(
            "guard proc=$process earlyDeaths=${guard.earlyDeaths} safeMode=${guard.safeMode}" +
                Logger.field("quarantined", guard.quarantined.sorted().joinToString(",")),
        )
    }

    private fun guardUnavailable(
        file: File,
        cause: Throwable,
    ): CrashGuard? {
        Logger.error(
            "guard unavailable proc=$process file=${file.path} reason=${cause.detail()}",
            cause,
        )
        return null
    }

    private fun guardWrite(
        detail: String,
        write: () -> Unit,
    ): Boolean =
        try {
            write()
            true
        } catch (e: Exception) {
            Logger.error("guard write failed $detail reason=${e.detail()}", e)
            false
        } catch (e: LinkageError) {
            Logger.error("guard write failed $detail reason=${e.detail()}", e)
            false
        }

    private fun installGuarded(
        tweak: Tweak,
        cl: ClassLoader,
        guard: CrashGuard,
        savedState: Any?,
    ): Boolean {
        if (tweak.key in guard.quarantined) return skipInstall(tweak, "quarantined")
        if (!guardWrite("tweak=${tweak.key}") { guard.installing(tweak.key) }) {
            return skipInstall(tweak, "guard-unavailable")
        }
        return try {
            tweak.restoreBeforeInstall(savedState)
            tweak.install(this, cl)
        } catch (e: Exception) {
            installFailed(tweak, e)
        } catch (e: LinkageError) {
            installFailed(tweak, e)
        }
    }

    private fun skipInstall(
        tweak: Tweak,
        reason: String,
    ): Boolean {
        Logger.error("install failed tweak=${tweak.key} build=${Build.ID} reason=$reason")
        return false
    }

    internal fun recordDeferredInstallFailure(
        tweak: String,
        message: String,
    ) {
        val guard = activeGuard ?: return
        guardWrite("proc=$process") {
            guard.recordDeferredFailure(BuildConfig.VERSION_CODE, tweak, message)
        }
    }

    private fun markStableLater(guard: CrashGuard) {
        stableTimer =
            thread(isDaemon = true, name = "PixelInjector-guard") {
                try {
                    Thread.sleep(STABLE_AFTER_MS)
                } catch (_: InterruptedException) {
                    return@thread
                }
                guardWrite("proc=$process") { guard.stable() }
                Logger.debug { "guard stable proc=$process afterMs=$STABLE_AFTER_MS" }
            }
    }

    private fun installFailed(
        tweak: Tweak,
        cause: Throwable,
    ): Boolean {
        Logger.error(
            "install failed tweak=${tweak.key} build=${Build.ID} reason=${cause.detail()}",
            cause,
        )
        return false
    }

    private fun installTweaks(
        process: String,
        systemServer: Boolean,
        cl: ClassLoader,
        origin: String,
        guardSite: GuardSite?,
        saved: Map<*, *>? = null,
    ) {
        installInProgress = true
        try {
            installTweakSet(process, systemServer, cl, origin, guardSite, saved)
        } finally {
            installInProgress = false
        }
    }

    private fun installTweakSet(
        process: String,
        systemServer: Boolean,
        cl: ClassLoader,
        origin: String,
        guardSite: GuardSite?,
        saved: Map<*, *>?,
    ) {
        classLoader = cl
        installed = true
        this.process = process
        val generation = "${Process.myPid()}-${SystemClock.elapsedRealtimeNanos()}"
        Logger.info(
            "install proc=$process pid=${Process.myPid()} api=$apiVersion " +
                "version=${BuildConfig.VERSION_CODE} origin=$origin generation=$generation " +
                "framework=$frameworkName/$frameworkVersionCode",
        )
        val target = Target.of(process, systemServer)
        this.target = target
        if (target == null) {
            Logger.debug { "install skipped proc=$process reason=no-target" }
            return
        }
        this.guardSite = guardSite
        val countEarlyDeaths = origin == "boot" && target in PERSISTENT_TARGETS
        val guard = guardSite?.let { openGuard(it, countEarlyDeaths, generation) }
        val tweaks = tweaksFor(target)
        val failures = mutableMapOf<String, String>()
        if (guard == null || guard.safeMode) {
            val reason =
                if (guard == null) "guard-unavailable" else ModuleConstants.SAFE_MODE_REASON
            Logger.captureInstallFailures(failures) {
                for (tweak in tweaks) skipInstall(tweak, reason)
            }
            if (guard != null) {
                guardWrite("proc=$process") { guard.installed(BuildConfig.VERSION_CODE, failures) }
            }
            return
        }
        activeGuard = guard
        val summary =
            Logger.captureInstallFailures(failures) {
                try {
                    loadRemotePrefs(target)
                    tweaks.associate { tweak ->
                        tweak.key to installGuarded(tweak, cl, guard, saved?.get(tweak.key))
                    }
                } finally {
                    DexKit.release()
                }
            }
        val outcomes =
            summary.mapValues { (tweak, installed) ->
                failures[tweak]
                    ?: if (installed) "installed" else "skipped"
            }
        guardWrite("proc=$process") { guard.installed(BuildConfig.VERSION_CODE, outcomes) }
        markStableLater(guard)
        if (saved != null) {
            for (tweak in tweaks) {
                if (summary[tweak.key] != true) continue
                try {
                    tweak.restoreState(saved[tweak.key])
                } catch (e: Exception) {
                    restoreFailed(tweak, e)
                } catch (e: LinkageError) {
                    restoreFailed(tweak, e)
                }
            }
        }
        Logger.info(
            "hooks installed " + summary.entries.joinToString(" ") { "${it.key}=${it.value}" },
        )
    }
}
