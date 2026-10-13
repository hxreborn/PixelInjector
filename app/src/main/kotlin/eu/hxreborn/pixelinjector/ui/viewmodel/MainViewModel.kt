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
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.ModuleConstants
import eu.hxreborn.pixelinjector.ModuleConstants.FILES_PACKAGES
import eu.hxreborn.pixelinjector.ModuleConstants.GBOARD_PACKAGES
import eu.hxreborn.pixelinjector.ModuleConstants.LAUNCHER_PACKAGES
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.prefs.AppPrefs
import eu.hxreborn.pixelinjector.prefs.PrefSpec
import eu.hxreborn.pixelinjector.support.Framework
import eu.hxreborn.pixelinjector.support.LogArchive
import eu.hxreborn.pixelinjector.support.MissingTarget
import eu.hxreborn.pixelinjector.support.ReportBuilder
import eu.hxreborn.pixelinjector.support.ReportFacts
import eu.hxreborn.pixelinjector.support.ReportPreview
import eu.hxreborn.pixelinjector.support.SendResult
import eu.hxreborn.pixelinjector.support.SentReport
import eu.hxreborn.pixelinjector.ui.dashboard.DashboardState
import eu.hxreborn.pixelinjector.ui.dashboard.HookedPackages
import eu.hxreborn.pixelinjector.ui.dashboard.TweakGroup
import eu.hxreborn.pixelinjector.ui.dashboard.restartOnChange
import eu.hxreborn.pixelinjector.ui.dashboard.tweakUis
import eu.hxreborn.pixelinjector.ui.log.ModuleLogState
import eu.hxreborn.pixelinjector.ui.log.deriveErrors
import eu.hxreborn.pixelinjector.ui.log.parseModuleLog
import eu.hxreborn.pixelinjector.ui.reload.HotReloadRunner
import eu.hxreborn.pixelinjector.ui.reload.ReloadRun
import eu.hxreborn.pixelinjector.ui.support.ReportPhase
import eu.hxreborn.pixelinjector.ui.support.SupportState
import eu.hxreborn.pixelinjector.ui.util.LauncherApp
import eu.hxreborn.pixelinjector.ui.util.Root
import eu.hxreborn.pixelinjector.ui.util.loadLauncherApps
import io.github.libxposed.service.HookedTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.isActive
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

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = App.from(application)
    private val runner = HotReloadRunner(viewModelScope, application.filesDir)
    val support = app.support
    private val logArchive = LogArchive(application.filesDir)
    private var rawLog: List<String>? = null

    val prefs: StateFlow<AppPrefs> =
        app.prefs.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PREFS_SUBSCRIPTION_TIMEOUT_MS), app.prefs.snapshot())

    val bound: StateFlow<Boolean> =
        app.service.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, app.service.value != null)

    val everBound: StateFlow<Boolean> = app.everBound

    private val targets = MutableStateFlow<List<HookedTarget>>(emptyList())
    private val scope = MutableStateFlow<Set<String>?>(null)

    private fun targetRank(target: HookedTarget): Int =
        when {
            target.processName == SYSTEM_PROCESS -> 0
            target.processName.startsWith(ModuleConstants.SYSTEMUI_PACKAGE) -> 1
            else -> 2
        }

    val reloadTargets: StateFlow<List<HookedTarget>> =
        targets
            .map { list -> list.sortedWith(compareBy(::targetRank, HookedTarget::getProcessName)) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
            hooked(setOf(ModuleConstants.KDE_CONNECT_PACKAGE))?.let { put(TweakGroup.KDE_CONNECT, it) }
            hooked(setOf(ModuleConstants.GITHUB_PACKAGE))?.let { put(TweakGroup.GITHUB, it) }
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

    private fun frameworkLabel(): String? =
        app.service.value?.let { "${it.frameworkName} ${it.frameworkVersion}  API ${it.apiVersion}  ${android.os.Build.ID}" }

    val supportState: StateFlow<SupportState> =
        combine(
            combine(
                support.release,
                support.reports,
                support.shareHookStatus,
            ) { release, reports, share -> Triple(release, reports, share) },
            support.postponedVersion,
            support.install,
            bound,
        ) { (release, reports, share), postponed, install, _ ->
            SupportState(release, reports.filter { it.replyTo == null }, share, postponed, install, frameworkLabel())
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SupportState(
                support.release.value,
                support.reports.value,
                support.shareHookStatus.value,
                support.postponedVersion.value,
                support.install.value,
                null,
            ),
        )

    private val _updatedNotice = MutableStateFlow<Pair<Int, Int>?>(null)
    val updatedNotice: StateFlow<Pair<Int, Int>?> = _updatedNotice.asStateFlow()
    private var updateNoticeShown = false

    fun consumeUpdatedNotice() {
        _updatedNotice.value = null
    }

    private fun announceUpdate(list: List<HookedTarget>) {
        if (updateNoticeShown || support.updatedFrom == null || list.isEmpty()) return
        updateNoticeShown = true
        val stale = list.count { it.loadedVersionCode < BuildConfig.VERSION_CODE }
        _updatedNotice.value = (list.size - stale) to stale
    }

    fun reportFacts(): ReportFacts {
        val service = app.service.value
        val log = _moduleLog.value
        val errors = _errors.value
        val prefsNow = prefs.value
        val missing =
            log.entries
                .filter { it.body.startsWith("target not found") && it.tweak != null && it.tweak in errors }
                .map { entry ->
                    MissingTarget(
                        entry.tweak.orEmpty(),
                        logField(entry.body, "member")?.trim('"')?.substringAfterLast('.')?.substringAfterLast(' '),
                        logField(entry.body, "reason"),
                    )
                }.distinctBy { it.tweak }
        return ReportFacts(
            framework = service?.let { Framework(it.frameworkName, it.frameworkVersion, it.apiVersion) },
            rootDenied = log.denied,
            logLines = rawLog,
            tweaks =
                tweakUis.associate {
                    it.key to
                        when {
                            !prefsNow[it.pref] -> "off"
                            it.key in errors -> "miss"
                            else -> "ok"
                        }
                },
            missing = missing,
            packages =
                targets.value
                    .map {
                        it.processName.substringBefore(
                            ':',
                        )
                    }.toSet() + ModuleConstants.SYSTEMUI_PACKAGE - SYSTEM_PROCESS,
        )
    }

    private fun logField(
        body: String,
        key: String,
    ): String? = Regex("""\b$key=("[^"]*"|\S+)""").find(body)?.groupValues?.get(1)

    private fun postHookStatus() {
        if (_moduleLog.value.loading) return
        support.postHookStatus { ReportBuilder.meta(getApplication(), reportFacts(), null, forReport = false) }
    }

    private val _reportPreview = MutableStateFlow<ReportPreview?>(null)
    val reportPreview: StateFlow<ReportPreview?> = _reportPreview.asStateFlow()

    private val _reportPhase = MutableStateFlow<ReportPhase>(ReportPhase.Compose)
    val reportPhase: StateFlow<ReportPhase> = _reportPhase.asStateFlow()

    private var reportJob: Job? = null

    fun openReport() {
        reportJob?.cancel()
        _reportPhase.value = ReportPhase.Compose
        _reportPreview.value = null
        reportJob =
            viewModelScope.launch(Dispatchers.IO) {
                if (rawLog == null || _moduleLog.value.denied) loadModuleLog()
                _reportPreview.value = ReportBuilder.preview(reportFacts())
            }
    }

    fun retryReport(report: SentReport) {
        reportJob?.cancel()
        _reportPhase.value = ReportPhase.Sending
        reportJob = viewModelScope.launch(Dispatchers.IO) { showResult(support.resend(report)) }
    }

    private fun CoroutineScope.showResult(result: SendResult) {
        if (!isActive) return
        _reportPhase.value =
            when (result) {
                is SendResult.Sent -> ReportPhase.Sent(result.report)
                is SendResult.Waiting -> ReportPhase.Waiting(result.report)
                is SendResult.Rejected -> ReportPhase.Rejected(result.reason)
            }
    }

    fun sendReport(
        message: String,
        tweak: String?,
        replyTo: String?,
    ) {
        val preview = _reportPreview.value ?: return
        _reportPhase.value = ReportPhase.Sending
        reportJob =
            viewModelScope.launch(Dispatchers.IO) {
                val result =
                    runCatching { support.send(ReportBuilder.encrypt(getApplication(), preview, message, tweak, replyTo), tweak, replyTo) }
                        .onFailure { Log.e(ModuleConstants.LOG_TAG, "report failed reason=${it.message}", it) }
                        .getOrElse { SendResult.Rejected(it.message ?: it.javaClass.simpleName) }
                showResult(result)
            }
    }

    fun onResumeSupport() {
        support.checkRelease(force = false)
        support.refreshStatuses()
        support.retryWaiting()
        postHookStatus()
    }

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
            announceUpdate(targets.value)
            scope.value =
                runCatching { service.scope.toSet() }
                    .onFailure { Log.w(ModuleConstants.LOG_TAG, "getScope failed reason=${it.message}", it) }
                    .getOrNull()
        }
    }

    fun refreshModuleLog() {
        viewModelScope.launch(Dispatchers.IO) { loadModuleLog() }
    }

    private fun loadModuleLog() {
        _moduleLog.update { it.copy(loading = true) }
        when (val read = runCatching { Root.readModuleLog() }.getOrElse { Root.LogRead.Denied }) {
            Root.LogRead.Denied -> {
                rawLog = null
                _moduleLog.value = ModuleLogState(loading = false, denied = true)
                _errors.value = deriveErrors(emptyList())
            }

            is Root.LogRead.Lines -> {
                val lines = logArchive.merge(read.lines)
                rawLog = lines
                _moduleLog.value = ModuleLogState(loading = false, entries = parseModuleLog(lines, read.processes))
                _errors.value = deriveErrors(parseModuleLog(read.lines, read.processes))
            }
        }
        postHookStatus()
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
