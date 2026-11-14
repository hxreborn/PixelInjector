package eu.hxreborn.pixelinjector.xposed

import android.app.Application
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.os.SystemClock
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.xposed.hook.DexKit
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

private const val SAVED_CLASSLOADER = "classLoader"
private const val SAVED_DEXKIT = "dexkit"
private const val SAVED_GUARD_FILE = "guardFile"
private const val SAVED_HOST_APK = "hostApk"
private const val SYSTEM_SERVER_HOST = "system_server"
private const val ACTIVITY_THREAD = "android.app.ActivityThread"
private const val TIMER_JOIN_MS = 1_000L
private val PERSISTENT_TARGETS = setOf(Target.SYSTEM, Target.SYSTEMUI)

private const val SYSTEM_DATA_DIR = "/data/system"
private const val USER_DE_DIR = "/data/user_de"
private const val UIDS_PER_USER = 100_000
private const val BOOT_ID_FILE = "/proc/sys/kernel/random/boot_id"
private const val GUARD_FILE_PREFIX = "pixelinjector-guard-"
private const val GUARD_FILE_SUFFIX = ".properties"
private const val STABLE_AFTER_MS = 60_000L
private const val MS_PER_MINUTE = 60_000L

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
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            val target = target ?: return@OnSharedPreferenceChangeListener
            try {
                loadHookPrefs(sp, target, process, key)
            } catch (e: Exception) {
                Logger.error("prefs reload failed reason=${e.message}", e)
            } catch (e: LinkageError) {
                Logger.error("prefs reload failed reason=${e.message}", e)
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
                guardFile(SYSTEM_DATA_DIR, process),
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
        return GuardSite(guardFile(dataDir, process), info.sourceDir.orEmpty())
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
        for (tweak in target?.let(::tweaksFor).orEmpty()) {
            runCatching { tweak.saveState() }
                .onSuccess { if (it != null) state[tweak.key] = it }
                .onFailure {
                    Logger.error(
                        "save failed tweak=${tweak.key} reason=${it.message}",
                        it,
                    )
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
        runCatching { super.onHotReloaded(param) }
            .onFailure {
                Logger.warn(
                    "hot reload old hook removal failed reason=${it.message}",
                    it,
                )
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

    private fun reloadGuardSite(
        saved: Map<*, *>,
        systemServer: Boolean,
        processName: String,
    ): GuardSite {
        val file =
            (saved[SAVED_GUARD_FILE] as? String)?.let(::File)
                ?: guardFile(hostDataDir(systemServer, processName), processName)
        val hostApk =
            saved[SAVED_HOST_APK] as? String
                ?: if (systemServer) SYSTEM_SERVER_HOST else runningHostApk()
        return GuardSite(file, hostApk)
    }

    private fun runningHostApk(): String =
        resolving {
            val application =
                Class
                    .forName(
                        ACTIVITY_THREAD,
                    ).getMethod("currentApplication")
                    .invoke(null)
            (application as? Application)?.applicationInfo?.sourceDir
        }.getOrNull().orEmpty()

    private fun hostDataDir(
        systemServer: Boolean,
        processName: String,
    ): String =
        if (systemServer) {
            SYSTEM_DATA_DIR
        } else {
            "$USER_DE_DIR/${Process.myUid() / UIDS_PER_USER}/${processName.substringBefore(':')}"
        }

    private fun guardFile(
        dir: String,
        processName: String,
    ): File = File(dir, GUARD_FILE_PREFIX + processName.replace(':', '_') + GUARD_FILE_SUFFIX)

    private fun bootId(): String =
        try {
            File(BOOT_ID_FILE).readText().trim()
        } catch (e: IOException) {
            Logger.warn("boot id unreadable reason=${e.message}")
            ((System.currentTimeMillis() - SystemClock.elapsedRealtime()) / MS_PER_MINUTE)
                .toString()
        }

    private fun openGuard(
        site: GuardSite,
        countEarlyDeaths: Boolean,
    ): CrashGuard? =
        try {
            CrashGuard
                .open(
                    site.file,
                    "${BuildConfig.VERSION_CODE}|${Build.FINGERPRINT}|${site.hostApk}",
                    bootId(),
                    "${Process.myPid()}-${SystemClock.elapsedRealtimeNanos()}",
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
            "guard proc=$process earlyDeaths=${guard.earlyDeaths} safeMode=${guard.safeMode} " +
                "quarantined=${guard.quarantined.sorted().joinToString(",")}",
        )
    }

    private fun guardUnavailable(
        file: File,
        cause: Throwable,
    ): CrashGuard? {
        Logger.error(
            "guard unavailable proc=$process file=${file.path} reason=${cause.message}",
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
            Logger.error("guard write failed $detail reason=${e.message}", e)
            false
        } catch (e: LinkageError) {
            Logger.error("guard write failed $detail reason=${e.message}", e)
            false
        }

    private fun installGuarded(
        tweak: Tweak,
        cl: ClassLoader,
        guard: CrashGuard,
    ): Boolean {
        if (tweak.key in guard.quarantined) return skipInstall(tweak, "quarantined")
        if (!guardWrite("tweak=${tweak.key}") { guard.installing(tweak.key) }) {
            return skipInstall(tweak, "guard-unavailable")
        }
        return installIsolated(tweak, cl)
    }

    private fun skipInstall(
        tweak: Tweak,
        reason: String,
    ): Boolean {
        Logger.error("install failed tweak=${tweak.key} build=${Build.ID} reason=$reason")
        return false
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

    private fun installIsolated(
        tweak: Tweak,
        cl: ClassLoader,
    ): Boolean =
        try {
            tweak.install(this, cl)
        } catch (e: Exception) {
            installFailed(tweak, e)
        } catch (e: LinkageError) {
            installFailed(tweak, e)
        }

    private fun installFailed(
        tweak: Tweak,
        cause: Throwable,
    ): Boolean {
        Logger.error(
            "install failed tweak=${tweak.key} build=${Build.ID} reason=${cause.message}",
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
        Logger.info(
            "install proc=$process pid=${Process.myPid()} api=$apiVersion " +
                "version=${BuildConfig.VERSION_CODE} origin=$origin " +
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
        val guard = guardSite?.let { openGuard(it, countEarlyDeaths) }
        if (guard == null || guard.safeMode) {
            val reason = if (guard == null) "guard-unavailable" else "safe-mode"
            for (tweak in tweaksFor(target)) skipInstall(tweak, reason)
            return
        }
        runCatching { getRemotePreferences(Prefs.GROUP) }
            .onSuccess { prefs ->
                remotePrefs = prefs
                prefs.registerOnSharedPreferenceChangeListener(prefsListener)
                loadHookPrefs(prefs, target, process)
            }.onFailure {
                Logger.warn("remote prefs unavailable defaults=true reason=${it.message}", it)
            }
        val tweaks = tweaksFor(target)
        val summary =
            try {
                tweaks.associate { tweak ->
                    tweak.key to installGuarded(tweak, cl, guard)
                }
            } finally {
                DexKit.release()
            }
        guardWrite("proc=$process") { guard.installed() }
        markStableLater(guard)
        if (saved != null) {
            for (tweak in tweaks) {
                runCatching { tweak.restoreState(saved[tweak.key]) }
                    .onFailure {
                        Logger.error("restore failed tweak=${tweak.key} reason=${it.message}", it)
                    }
            }
        }
        Logger.info(
            "hooks installed " + summary.entries.joinToString(" ") { "${it.key}=${it.value}" },
        )
    }
}
