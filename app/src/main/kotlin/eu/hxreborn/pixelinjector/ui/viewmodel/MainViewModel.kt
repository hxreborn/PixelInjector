package eu.hxreborn.pixelinjector.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.hxreborn.pixelinjector.App
import eu.hxreborn.pixelinjector.ModuleConstants
import eu.hxreborn.pixelinjector.ModuleConstants.FILES_PACKAGES
import eu.hxreborn.pixelinjector.ModuleConstants.GBOARD_PACKAGES
import eu.hxreborn.pixelinjector.ModuleConstants.LAUNCHER_PACKAGES
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.prefs.AppPrefs
import eu.hxreborn.pixelinjector.prefs.PrefSpec
import eu.hxreborn.pixelinjector.ui.dashboard.DashboardState
import eu.hxreborn.pixelinjector.ui.dashboard.HookedPackages
import eu.hxreborn.pixelinjector.ui.dashboard.TweakGroup
import eu.hxreborn.pixelinjector.ui.dashboard.restartOnChange
import eu.hxreborn.pixelinjector.ui.log.ModuleLogState
import eu.hxreborn.pixelinjector.ui.log.deriveErrors
import eu.hxreborn.pixelinjector.ui.log.parseModuleLog
import eu.hxreborn.pixelinjector.ui.reload.HotReloadRunner
import eu.hxreborn.pixelinjector.ui.reload.ReloadRun
import eu.hxreborn.pixelinjector.ui.util.LauncherApp
import eu.hxreborn.pixelinjector.ui.util.Root
import eu.hxreborn.pixelinjector.ui.util.loadLauncherApps
import io.github.libxposed.service.HookedTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SYSTEM_PROCESS = "system"
private val fixedPackages =
    mapOf(
        TweakGroup.SYSTEM_UI to HookedPackages(listOf(ModuleConstants.SYSTEMUI_PACKAGE), true),
        TweakGroup.SYSTEM to HookedPackages(listOf(SYSTEM_PROCESS), true),
    )
private const val PREFS_SUBSCRIPTION_TIMEOUT_MS = 5_000L
private const val RESTART_POLLS = 10
private const val RESTART_POLL_MS = 500L

data class HomeApp(
    val packageName: String,
    val label: String,
    val supported: Boolean,
    val loaded: Boolean,
)

data class AppTarget(
    val target: HookedTarget,
    val packageName: String,
)

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = App.from(application)
    private val runner = HotReloadRunner(viewModelScope, application.filesDir)

    val prefs: StateFlow<AppPrefs> =
        app.prefs.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PREFS_SUBSCRIPTION_TIMEOUT_MS), app.prefs.snapshot())

    val bound: StateFlow<Boolean> =
        app.service.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, app.service.value != null)

    val everBound: StateFlow<Boolean> = app.everBound

    private val targets = MutableStateFlow<List<HookedTarget>>(emptyList())
    private val scope = MutableStateFlow<Set<String>?>(null)

    private fun reloadable(target: HookedTarget): Boolean =
        target.processName == SYSTEM_PROCESS || target.processName.startsWith(ModuleConstants.SYSTEMUI_PACKAGE)

    val reloadTargets: StateFlow<List<HookedTarget>> =
        targets.map { list -> list.filter(::reloadable) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun pendingTargets(list: List<HookedTarget>): List<HookedTarget> =
        list.filter { it.state == HookedTarget.State.STALE }.ifEmpty { list }

    val reloadCount: StateFlow<Int> =
        reloadTargets
            .map { list -> list.count { it.state == HookedTarget.State.STALE } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val reload: StateFlow<ReloadRun?> = runner.state

    val homeApp: StateFlow<HomeApp?> =
        targets
            .map(::resolveHomeApp)
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Eagerly, resolveHomeApp(emptyList()))

    val hookedPackages: StateFlow<Map<TweakGroup, HookedPackages>> =
        combine(scope, targets, homeApp) { scope, targets, home -> hookedPackages(scope, targets, home) }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Eagerly, fixedPackages)

    private fun hookedPackages(
        scope: Set<String>?,
        targets: List<HookedTarget>,
        home: HomeApp?,
    ): Map<TweakGroup, HookedPackages> {
        val running = targets.mapTo(mutableSetOf()) { it.processName.substringBefore(':') }

        fun hooked(candidates: Set<String>): HookedPackages? {
            val installed = candidates.filter(::installed)
            val scoped = installed.filter { it in running || scope == null || it in scope }
            return when {
                scoped.isNotEmpty() -> HookedPackages(scoped, true)
                installed.isNotEmpty() -> HookedPackages(installed, false)
                else -> null
            }
        }
        return buildMap {
            putAll(fixedPackages)
            home?.let { put(TweakGroup.LAUNCHER, HookedPackages(listOf(it.packageName), true)) }
            hooked(GBOARD_PACKAGES)?.let { put(TweakGroup.GBOARD, it) }
            hooked(setOf(ModuleConstants.DIALER_PACKAGE))?.let { put(TweakGroup.DIALER, it) }
            hooked(FILES_PACKAGES)?.let { put(TweakGroup.FILES, it) }
        }
    }

    private fun installed(packageName: String): Boolean =
        runCatching { getApplication<Application>().packageManager.getPackageInfo(packageName, 0) }.isSuccess

    private fun resolveHomeApp(targets: List<HookedTarget>): HomeApp? {
        val pm = getApplication<Application>().packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved =
            runCatching { pm.resolveActivity(home, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())) }
                .getOrNull() ?: return null
        val packageName = resolved.activityInfo?.packageName ?: return null
        return HomeApp(
            packageName = packageName,
            label = resolved.loadLabel(pm).toString(),
            supported = packageName in LAUNCHER_PACKAGES,
            loaded = targets.any { it.processName == packageName },
        )
    }

    private val _launcherApps = MutableStateFlow<List<LauncherApp>?>(null)
    val launcherApps: StateFlow<List<LauncherApp>?> = _launcherApps.asStateFlow()

    fun refreshLauncherApps() {
        viewModelScope.launch { _launcherApps.value = loadLauncherApps(getApplication()) }
    }

    private val _moduleLog = MutableStateFlow(ModuleLogState())
    val moduleLog: StateFlow<ModuleLogState> = _moduleLog.asStateFlow()

    val appTargets: StateFlow<List<AppTarget>> =
        targets
            .map { list -> list.filterNot(::reloadable).map { AppTarget(it, it.processName.substringBefore(':')) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()

    val dashboard: StateFlow<DashboardState> =
        combine(
            combine(prefs, bound, everBound) { prefs, bound, everBound -> Triple(prefs, bound, everBound) },
            reloadCount,
            errors,
            combine(homeApp, hookedPackages) { home, packages -> home to packages },
            moduleLog,
        ) { (prefs, bound, everBound), reloadCount, errors, (homeApp, packages), log ->
            DashboardState(prefs, bound, everBound, reloadCount, errors, homeApp, packages, log.denied)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(PREFS_SUBSCRIPTION_TIMEOUT_MS),
            DashboardState(
                prefs.value,
                bound.value,
                everBound.value,
                reloadCount.value,
                errors.value,
                homeApp.value,
                hookedPackages.value,
                false,
            ),
        )

    init {
        refreshLauncherApps()
        refreshModuleLog()
        viewModelScope.launch {
            app.service.collect { service ->
                if (service == null) {
                    targets.value = emptyList()
                    scope.value = null
                    runner.onServiceDied()
                } else {
                    refreshTargets()
                }
            }
        }
    }

    fun refreshTargets() {
        val service = app.service.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            targets.value =
                runCatching { service.runningTargets }
                    .onFailure { Log.w(ModuleConstants.LOG_TAG, "getRunningTargets failed reason=${it.message}", it) }
                    .getOrDefault(emptyList())
            scope.value =
                runCatching { service.scope.toSet() }
                    .onFailure { Log.w(ModuleConstants.LOG_TAG, "getScope failed reason=${it.message}", it) }
                    .getOrNull()
        }
    }

    fun refreshModuleLog() {
        _moduleLog.update { it.copy(loading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val state =
                when (val read = runCatching { Root.readModuleLog() }.getOrElse { Root.LogRead.Denied }) {
                    Root.LogRead.Denied -> {
                        ModuleLogState(loading = false, denied = true)
                    }

                    is Root.LogRead.Lines -> {
                        ModuleLogState(loading = false, entries = parseModuleLog(read.lines, read.processes))
                    }
                }
            _moduleLog.value = state
            _errors.value = deriveErrors(state.entries)
        }
    }

    fun hotReload(selected: List<HookedTarget>) {
        val service = app.service.value ?: return
        runner.start(service, selected)
    }

    fun hotReloadPending() = hotReload(pendingTargets(reloadTargets.value))

    fun cancelHotReload() = runner.cancel()

    fun dismissHotReload() {
        runner.dismiss()
        refreshTargets()
        refreshModuleLog()
    }

    fun <T : Any> save(
        pref: PrefSpec<T>,
        value: T,
    ) {
        app.prefs.save(pref, value)
        restartOnChange[pref]?.let(::restartApps)
    }

    fun restartApps(packages: Set<String>) {
        viewModelScope.launch {
            val stopped = withContext(Dispatchers.IO) { runCatching { Root.forceStop(packages) }.getOrNull() }
            announceForceStop(stopped)
            repeat(RESTART_POLLS) {
                delay(RESTART_POLL_MS)
                refreshTargets()
                if (stopped.isNullOrEmpty() || targets.value.any { it.processName.substringBefore(':') in stopped }) return@launch
            }
        }
    }

    private fun announceForceStop(stopped: List<String>?) {
        val context = getApplication<Application>()
        val text =
            when {
                stopped == null -> {
                    refreshModuleLog()
                    context.getString(R.string.root_denied)
                }

                stopped.isEmpty() -> {
                    return
                }

                else -> {
                    context.getString(R.string.force_stopped, stopped.joinToString(" and ", transform = ::label))
                }
            }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    private fun label(packageName: String): String {
        val pm = getApplication<Application>().packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }
            .getOrDefault(packageName)
    }

    fun restart() = Process.killProcess(Process.myPid())

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer { MainViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!) }
            }
    }
}
