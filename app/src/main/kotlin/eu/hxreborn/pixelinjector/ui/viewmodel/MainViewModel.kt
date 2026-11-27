package eu.hxreborn.pixelinjector.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.hxreborn.pixelinjector.App
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.ModuleConstants
import eu.hxreborn.pixelinjector.ModuleConstants.FILES_PACKAGES
import eu.hxreborn.pixelinjector.ModuleConstants.GBOARD_PACKAGES
import eu.hxreborn.pixelinjector.ModuleConstants.LAUNCHER_PACKAGES
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.icons.FingerprintStyles
import eu.hxreborn.pixelinjector.prefs.AppPrefs
import eu.hxreborn.pixelinjector.prefs.PrefSpec
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.support.EXTRA_OPEN_UPDATE
import eu.hxreborn.pixelinjector.support.EXTRA_UPDATE_VERSION
import eu.hxreborn.pixelinjector.support.Framework
import eu.hxreborn.pixelinjector.support.LogArchive
import eu.hxreborn.pixelinjector.support.MissingTarget
import eu.hxreborn.pixelinjector.support.ReportBuilder
import eu.hxreborn.pixelinjector.support.ReportFacts
import eu.hxreborn.pixelinjector.support.ReportPreview
import eu.hxreborn.pixelinjector.support.RomCode
import eu.hxreborn.pixelinjector.support.SendResult
import eu.hxreborn.pixelinjector.support.SentReport
import eu.hxreborn.pixelinjector.ui.betterfox.BetterfoxController
import eu.hxreborn.pixelinjector.ui.dashboard.DashboardState
import eu.hxreborn.pixelinjector.ui.dashboard.HookedPackages
import eu.hxreborn.pixelinjector.ui.dashboard.TweakGroup
import eu.hxreborn.pixelinjector.ui.dashboard.restartOnChange
import eu.hxreborn.pixelinjector.ui.dashboard.tweakUis
import eu.hxreborn.pixelinjector.ui.editor.fingerprintImageFile
import eu.hxreborn.pixelinjector.ui.log.ModuleLogState
import eu.hxreborn.pixelinjector.ui.log.deriveErrorLines
import eu.hxreborn.pixelinjector.ui.log.deriveErrors
import eu.hxreborn.pixelinjector.ui.log.installFailureBodies
import eu.hxreborn.pixelinjector.ui.log.parseModuleLog
import eu.hxreborn.pixelinjector.ui.log.safeModeSkips
import eu.hxreborn.pixelinjector.ui.navigation.Destination
import eu.hxreborn.pixelinjector.ui.navigation.TestNavigation
import eu.hxreborn.pixelinjector.ui.navigation.testBetterfoxCommand
import eu.hxreborn.pixelinjector.ui.navigation.testNavigation
import eu.hxreborn.pixelinjector.ui.reload.HotReloadRunner
import eu.hxreborn.pixelinjector.ui.reload.ReloadRun
import eu.hxreborn.pixelinjector.ui.support.ReportPhase
import eu.hxreborn.pixelinjector.ui.support.SupportState
import eu.hxreborn.pixelinjector.ui.util.DateStyle
import eu.hxreborn.pixelinjector.ui.util.LauncherApp
import eu.hxreborn.pixelinjector.ui.util.Root
import eu.hxreborn.pixelinjector.ui.util.loadLauncherApps
import eu.hxreborn.pixelinjector.util.detail
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

private const val TEST_NAVIGATION_INTENT = "testNavigationIntent"
private const val SYSTEM_PROCESS = "system"
private const val FINGERPRINT_IMAGE_PX = 512
private val fixedPackages =
    mapOf(
        TweakGroup.SYSTEM_UI to HookedPackages(listOf(ModuleConstants.SYSTEMUI_PACKAGE), true),
        TweakGroup.SYSTEM to HookedPackages(listOf(SYSTEM_PROCESS), true),
    )
private const val PREFS_SUBSCRIPTION_TIMEOUT_MS = 5_000L
private const val RESTART_POLLS = 10
private const val RESTART_POLL_MS = 500L
private const val UPDATE_TARGET_POLL_MS = 500L
private const val UPDATE_TARGET_TIMEOUT_MS = 8_000L

data class HomeApp(
    val packageName: String,
    val label: String,
    val supported: Boolean,
    val loaded: Boolean,
)

class UpdateNotice(
    val retryTargets: List<UpdateNoticeTarget>,
    internal val service: XposedService,
)

class UpdateNoticeTarget(
    val label: String,
    val olderVersionCode: Long?,
    internal val target: HookedTarget,
)

private data class TargetProcess(
    val pid: Int,
    val uid: Int,
    val name: String,
)

private val HookedTarget.process: TargetProcess get() = TargetProcess(pid, uid, processName)

private val HookedTarget.olderVersionCode: Long?
    get() = loadedVersionCode.takeIf { it in 1 until BuildConfig.VERSION_CODE.toLong() }

private val HookedTarget.needsUpdateReload: Boolean
    get() = state != HookedTarget.State.RELOADING && (olderVersionCode != null || state == HookedTarget.State.FAILED)

private val HookedTarget.hasUpdateReloadResult: Boolean
    get() = loadedVersionCode == BuildConfig.VERSION_CODE.toLong() || state == HookedTarget.State.FAILED

class MainViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {
    private val app = App.from(application)
    private val reloadRunner = HotReloadRunner(viewModelScope, application.filesDir)
    val support = app.support
    private val logArchive = LogArchive(application.filesDir)
    private val logLock = Mutex()
    private val fingerprintImageLock = Mutex()

    @Volatile private var rawLog: List<String>? = null

    @Volatile private var installFailures: Map<String, Map<String, String>> = emptyMap()

    val prefs: StateFlow<AppPrefs> =
        app.prefs.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PREFS_SUBSCRIPTION_TIMEOUT_MS), app.prefs.snapshot())

    val bound: StateFlow<Boolean> =
        app.service.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, app.service.value != null)

    val everBound: StateFlow<Boolean> = app.everBound
    val hideUnavailable: StateFlow<Boolean> = app.hideUnavailable

    fun setHideUnavailable(hide: Boolean) = app.setHideUnavailable(hide)

    val showPackages: StateFlow<Boolean> = app.showPackages

    fun setShowPackages(show: Boolean) = app.setShowPackages(show)

    val useSystemFont: StateFlow<Boolean> = app.useSystemFont

    fun setUseSystemFont(enabled: Boolean) = app.setUseSystemFont(enabled)

    val dateStyle: StateFlow<DateStyle> = app.dateStyle

    fun setDateStyle(style: DateStyle) = app.setDateStyle(style)

    private val targets = MutableStateFlow<List<HookedTarget>>(emptyList())
    private val scopedPackages = MutableStateFlow<Set<String>?>(null)

    private fun targetRank(target: HookedTarget): Int =
        when {
            target.processName == SYSTEM_PROCESS -> 0
            target.processName.startsWith(ModuleConstants.SYSTEMUI_PACKAGE) -> 1
            else -> 2
        }

    private fun HookedTarget.hasTarget(): Boolean =
        Target.of(processName, systemServer = uid == Process.SYSTEM_UID && processName == SYSTEM_PROCESS) != null

    val reloadTargets: StateFlow<List<HookedTarget>> =
        targets
            .map { list -> list.sortedWith(compareBy(::targetRank, HookedTarget::getProcessName)) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val reloadCount: StateFlow<Int> =
        reloadTargets
            .map { list -> list.count { it.state == HookedTarget.State.STALE } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val reload: StateFlow<ReloadRun?> = reloadRunner.state

    val homeApp: StateFlow<HomeApp?> =
        targets
            .map(::resolveHomeApp)
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Eagerly, resolveHomeApp(emptyList()))

    val hookedPackages: StateFlow<Map<TweakGroup, HookedPackages>> =
        combine(scopedPackages, targets, homeApp) { scopedPackages, targets, home -> hookedPackages(scopedPackages, targets, home) }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Eagerly, fixedPackages)

    private fun hookedPackages(
        scopedPackages: Set<String>?,
        targets: List<HookedTarget>,
        home: HomeApp?,
    ): Map<TweakGroup, HookedPackages> {
        val running = targets.mapTo(mutableSetOf()) { it.processName.substringBefore(':') }

        fun hooked(candidates: Set<String>): HookedPackages? {
            val installed = candidates.filter(::installed)
            val scoped = installed.filter { it in running || scopedPackages == null || it in scopedPackages }
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
            hooked(ModuleConstants.FIREFOX_PACKAGES)?.let { put(TweakGroup.FIREFOX, it.copy(labels = it.packages.associateWith(::label))) }
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

    val betterfox = BetterfoxController(application, viewModelScope, ::restartApps)

    private val _testNavigation = MutableStateFlow(testNavigation(savedState.get<Any>(TEST_NAVIGATION_INTENT) as? Intent))
    val testNavigation: StateFlow<TestNavigation?> = _testNavigation.asStateFlow()
    private val testDestinations = Channel<List<Destination>>(Channel.CONFLATED)
    val testNavigationRequests = testDestinations.receiveAsFlow()

    fun handleTestIntent(intent: Intent?) {
        testNavigation(intent)?.let { navigation ->
            navigation.destinations.filterIsInstance<Destination.BetterfoxEditor>().lastOrNull()?.let {
                betterfox.onOpen(it.packageName)
            }
            savedState[TEST_NAVIGATION_INTENT] = intent
            _testNavigation.value = navigation
            testDestinations.trySend(navigation.destinations)
        }
        testBetterfoxCommand(intent)?.let(betterfox::runCommand)
    }

    private val _updateRequested = MutableStateFlow(false)
    val updateRequested: StateFlow<Boolean> = _updateRequested.asStateFlow()

    fun handleUpdateIntent(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_OPEN_UPDATE, false)) return
        val versionCode = intent.getIntExtra(EXTRA_UPDATE_VERSION, 0)
        app.support.clearUpdateNotification(versionCode)
        _updateRequested.value = true
    }

    fun consumeUpdateRequest() {
        _updateRequested.value = false
    }

    private val _moduleLog = MutableStateFlow(ModuleLogState())
    val moduleLog: StateFlow<ModuleLogState> = _moduleLog.asStateFlow()

    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()
    private val errorLines = MutableStateFlow<Map<String, String>>(emptyMap())

    val dashboard: StateFlow<DashboardState> =
        combine(
            combine(prefs, bound, everBound) { prefs, bound, everBound -> Triple(prefs, bound, everBound) },
            reloadCount,
            combine(errors, errorLines) { errors, lines -> errors to lines },
            combine(homeApp, hookedPackages) { home, packages -> home to packages },
            moduleLog,
        ) { (prefs, bound, everBound), reloadCount, (errors, errorLines), (homeApp, packages), log ->
            DashboardState(prefs, bound, everBound, reloadCount, errors, errorLines, homeApp, packages, log.denied)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(PREFS_SUBSCRIPTION_TIMEOUT_MS),
            DashboardState(
                prefs.value,
                bound.value,
                everBound.value,
                reloadCount.value,
                errors.value,
                errorLines.value,
                homeApp.value,
                hookedPackages.value,
                false,
            ),
        )

    private fun frameworkLabel(): String? =
        app.service.value?.let { service ->
            val version = Regex("""^\S+(?: \(\d+\))?""").find(service.frameworkVersion)?.value ?: service.frameworkVersion
            "${service.frameworkName} $version\nAPI ${service.apiVersion}"
        }

    val supportState: StateFlow<SupportState> =
        combine(
            support.release,
            support.reports,
            support.autoCheckUpdates,
            support.postponedVersion,
            bound,
        ) { release, reports, autoCheck, postponed, _ ->
            SupportState(release, reports, autoCheck, postponed, frameworkLabel())
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SupportState(
                support.release.value,
                support.reports.value,
                support.autoCheckUpdates.value,
                support.postponedVersion.value,
                null,
            ),
        )

    private val _updatedNotice = MutableStateFlow<UpdateNotice?>(null)
    val updatedNotice: StateFlow<UpdateNotice?> = _updatedNotice.asStateFlow()

    fun consumeUpdatedNotice(): Boolean {
        _updatedNotice.value = null
        return support.consumeUpdateNotice()
    }

    private suspend fun announceUpdate(service: XposedService) {
        _updatedNotice.update { notice -> notice?.takeIf { it.service === service } }
        if (_updatedNotice.value != null || !support.updateNoticePending) return
        val settledTargets = awaitUpdateTargets(service)
        val retryTargets =
            withContext(Dispatchers.IO) {
                settledTargets.filter { it.needsUpdateReload }.map { target ->
                    val name =
                        if (target.processName == SYSTEM_PROCESS) {
                            app.getString(R.string.scope_system)
                        } else {
                            label(target.processName.substringBefore(':'))
                        }
                    UpdateNoticeTarget(name, target.olderVersionCode, target)
                }
            }
        if (app.service.value !== service || !support.updateNoticePending) return
        targets.value = settledTargets
        _updatedNotice.value = UpdateNotice(retryTargets, service)
    }

    private suspend fun awaitUpdateTargets(service: XposedService): List<HookedTarget> {
        val deadline = SystemClock.elapsedRealtime() + UPDATE_TARGET_TIMEOUT_MS
        while (true) {
            val runningTargets = withContext(Dispatchers.IO) { readTargets(service) }
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (runningTargets != null && runningTargets.none { it.state == HookedTarget.State.RELOADING } &&
                (remaining <= 0 || runningTargets.all { it.hasUpdateReloadResult })
            ) {
                return runningTargets
            }
            delay(if (remaining > 0) minOf(UPDATE_TARGET_POLL_MS, remaining) else UPDATE_TARGET_POLL_MS)
        }
    }

    fun reportFacts(): ReportFacts {
        val service = app.service.value
        val log = _moduleLog.value
        val errors = _errors.value
        val prefsNow = prefs.value
        val skipped = safeModeSkips(installFailures)
        val missesByProcess =
            installFailures.mapValues { (_, failures) ->
                failures.filterValues { it.startsWith("target not found") }
            }
        val missing =
            missesByProcess.values
                .flatMap { failures ->
                    failures.map { (tweak, body) ->
                        MissingTarget(
                            tweak,
                            logField(body, "member")?.trim('"')?.substringAfterLast('.')?.substringAfterLast(' '),
                            logField(body, "reason"),
                        )
                    }
                }.distinctBy { it.tweak }
        val (romCodes, unreadableRomCodes) =
            romCodes(missesByProcess.mapValues { (_, misses) -> misses.keys })
        val installDiagnosticLines =
            installFailures.flatMap { (process, failures) ->
                failures.values.map { "install diagnostic proc=$process version=${BuildConfig.VERSION_CODE} $it" }
            }
        return ReportFacts(
            framework = service?.let { Framework(it.frameworkName, it.frameworkVersion, it.apiVersion) },
            rootDenied = log.denied,
            logLines = rawLog?.plus(installDiagnosticLines),
            tweaks =
                tweakUis.associate {
                    it.key to
                        when {
                            it.prefs.none { pref -> prefsNow[pref] } -> "off"
                            it.key in errors && it.key !in skipped -> "miss"
                            it.key in errorLines.value -> "error"
                            it.key in skipped -> "skip"
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
            romCodes = romCodes,
            unreadableRomCodes = unreadableRomCodes,
        )
    }

    private fun romCodes(missesByProcess: Map<String, Set<String>>): Pair<Map<String, RomCode>, Map<String, String>> {
        if (!RomCode.wanted) return emptyMap<String, RomCode>() to emptyMap()
        val detectedKinds = mutableMapOf<String, MutableSet<String>>()
        for ((process, tweaks) in missesByProcess) {
            val kind =
                when (process.substringBefore(':')) {
                    ModuleConstants.SYSTEMUI_PACKAGE -> RomCode.SYSTEMUI
                    SYSTEM_PROCESS -> RomCode.SERVICES
                    else -> continue
                }
            tweaks.forEach { detectedKinds.getOrPut(it, ::mutableSetOf).add(kind) }
        }
        val kindsByTweak =
            tweakUis
                .mapNotNull { tweak ->
                    val groupKind =
                        when (tweak.group) {
                            TweakGroup.SYSTEM_UI -> RomCode.SYSTEMUI
                            TweakGroup.SYSTEM -> RomCode.SERVICES
                            else -> null
                        }
                    val detected = detectedKinds[tweak.key].orEmpty()
                    val kind = groupKind?.takeIf(detected::contains) ?: detected.firstOrNull() ?: groupKind
                    kind?.let { tweak.key to it }
                }.toMap()
        val codeByKind =
            kindsByTweak.values.toSet().associateWith { kind ->
                runCatching { RomCode.find(getApplication(), kind) }
                    .onFailure { Log.w(ModuleConstants.LOG_TAG, "rom code unreadable kind=$kind reason=${it.detail()}") }
                    .getOrNull()
            }
        val codes = mutableMapOf<String, RomCode>()
        val unreadable = mutableMapOf<String, String>()
        for ((tweak, kind) in kindsByTweak) {
            val code = codeByKind[kind]
            if (code == null) unreadable[tweak] = kind else codes[tweak] = code
        }
        return codes to unreadable
    }

    private fun logField(
        body: String,
        key: String,
    ): String? = Regex("""\b$key=("[^"]*"|\S+)""").find(body)?.groupValues?.get(1)

    private val _reportPreview = MutableStateFlow<ReportPreview?>(null)
    val reportPreview: StateFlow<ReportPreview?> = _reportPreview.asStateFlow()

    private val _reportPhase = MutableStateFlow<ReportPhase>(ReportPhase.Compose)
    val reportPhase: StateFlow<ReportPhase> = _reportPhase.asStateFlow()

    private var reportJob: Job? = null

    private val _reportDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    val reportDrafts: StateFlow<Map<String, String>> = _reportDrafts.asStateFlow()

    fun saveReportDraft(
        replyTo: String?,
        message: String,
    ) {
        _reportDrafts.update { it + (replyTo.orEmpty() to message) }
    }

    fun openReport() {
        reportJob?.cancel()
        _reportPhase.value = ReportPhase.Compose
        _reportPreview.value = null
        reportJob =
            viewModelScope.launch(Dispatchers.IO) {
                val facts =
                    logLock.withLock {
                        loadModuleLog()
                        reportFacts()
                    }
                _reportPreview.value = ReportBuilder.preview(facts)
            }
    }

    fun retryReport(report: SentReport) {
        reportJob?.cancel()
        _reportPhase.value = ReportPhase.Sending
        reportJob = viewModelScope.launch(Dispatchers.IO) { showSendResult(support.resend(report)) }
    }

    private fun CoroutineScope.showSendResult(result: SendResult) {
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
                    runCatching {
                        support.send(
                            ReportBuilder.encrypt(getApplication(), preview, message, tweak, replyTo, support.noteKeyFor(replyTo)),
                            message,
                            tweak,
                            replyTo,
                        )
                    }.onFailure { Log.e(ModuleConstants.LOG_TAG, "report failed reason=${it.detail()}", it) }
                        .getOrElse { SendResult.Rejected(it.message ?: it.javaClass.simpleName) }
                if (result !is SendResult.Rejected) _reportDrafts.update { it - replyTo.orEmpty() }
                showSendResult(result)
            }
    }

    fun onResumeSupport() {
        betterfox.onResume(support.autoCheckUpdates.value)
        support.checkRelease(force = false)
        support.refreshStatuses()
        support.retryWaiting()
    }

    init {
        refreshLauncherApps()
        refreshModuleLog()
        viewModelScope.launch {
            app.service.collectLatest { service ->
                if (service == null) {
                    targets.value = emptyList()
                    scopedPackages.value = null
                    _updatedNotice.value = null
                    reloadRunner.onServiceDied()
                } else {
                    refreshTargets()
                    announceUpdate(service)
                }
            }
        }
    }

    fun refreshTargets() {
        val service = app.service.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            targets.value = readTargets(service).orEmpty()
            scopedPackages.value =
                runCatching { service.scope.toSet() }
                    .onFailure { Log.w(ModuleConstants.LOG_TAG, "getScope failed reason=${it.detail()}", it) }
                    .getOrNull()
        }
    }

    private fun readTargets(service: XposedService): List<HookedTarget>? =
        runCatching { service.runningTargets.filter { it.hasTarget() } }
            .onFailure { Log.w(ModuleConstants.LOG_TAG, "getRunningTargets failed reason=${it.detail()}", it) }
            .getOrNull()

    fun refreshModuleLog() {
        viewModelScope.launch(Dispatchers.IO) { logLock.withLock { loadModuleLog() } }
    }

    private fun loadModuleLog() {
        _moduleLog.update { it.copy(loading = true) }
        when (val read = runCatching { Root.readModuleLog() }.getOrElse { Root.LogRead.Denied }) {
            Root.LogRead.Denied -> {
                rawLog = null
                _moduleLog.value = ModuleLogState(loading = false, denied = true)
                installFailures = emptyMap()
                _errors.value = emptyMap()
                errorLines.value = emptyMap()
            }

            is Root.LogRead.Lines -> {
                val lines = logArchive.merge(read.lines)
                rawLog = lines
                val entries = parseModuleLog(lines, read.processes)
                _moduleLog.value = ModuleLogState(loading = false, entries = entries)
                val current = parseModuleLog(read.lines, read.processes)
                installFailures = installFailureBodies(entries, read.installDiagnostics)
                _errors.value = deriveErrors(installFailures)
                errorLines.value = deriveErrorLines(current, read.processes.keys)
            }
        }
    }

    fun hotReload(selected: List<HookedTarget>) {
        val service = app.service.value ?: return
        reloadRunner.start(service, selected)
    }

    fun hotReloadStale() = hotReload(reloadTargets.value.filter { it.state == HookedTarget.State.STALE })

    fun retryUpdateReload(notice: UpdateNotice) {
        val service = notice.service
        viewModelScope.launch {
            if (app.service.value !== service) return@launch
            val runningTargets = withContext(Dispatchers.IO) { readTargets(service) } ?: return@launch
            val retryProcesses = runningTargets.filter { it.needsUpdateReload }.mapTo(mutableSetOf()) { it.process }
            val retryTargets = notice.retryTargets.map { it.target }.filter { it.process in retryProcesses }
            if (app.service.value === service && retryTargets.isNotEmpty()) reloadRunner.start(service, retryTargets)
        }
    }

    fun cancelHotReload() = reloadRunner.cancel()

    fun dismissHotReload() {
        reloadRunner.dismiss()
        refreshTargets()
        refreshModuleLog()
    }

    fun <T : Any> save(
        pref: PrefSpec<T>,
        value: T,
    ) {
        val betterfoxPackage = Prefs.FIREFOX_BETTERFOX.packageOf(pref)
        if (betterfoxPackage != null && value == true) {
            betterfox.enable(betterfoxPackage)
            return
        }
        commit(pref, value)
    }

    fun resetToDefault(prefs: List<PrefSpec<*>>) {
        prefs.forEach { commitDefault(it) }
    }

    private fun <T : Any> commitDefault(pref: PrefSpec<T>) = commit(pref, pref.default)

    private fun <T : Any> commit(
        pref: PrefSpec<T>,
        value: T,
    ) {
        app.prefs.save(pref, value)
        restartOnChange[pref]?.let(::restartApps)
    }

    fun saveFingerprintImage(uri: Uri) {
        viewModelScope.launch {
            fingerprintImageLock.withLock { writeFingerprintImage(uri) }
        }
    }

    private suspend fun writeFingerprintImage(uri: Uri) {
        val context = getApplication<Application>()
        val saved =
            withContext(Dispatchers.IO) {
                runCatching {
                    val service = checkNotNull(app.service.value) { "service unbound" }
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    val bitmap =
                        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                            val scale = FINGERPRINT_IMAGE_PX.toFloat() / maxOf(info.size.width, info.size.height)
                            if (scale < 1f) {
                                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                            }
                        }
                    val png = ByteArrayOutputStream()
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)) { "png compress failed" }
                    val bytes = png.toByteArray()
                    service.openRemoteFile(FingerprintStyles.IMAGE_FILE).use { fd ->
                        FileOutputStream(fd.fileDescriptor).use {
                            it.channel.truncate(0)
                            it.write(bytes)
                        }
                    }
                    fingerprintImageFile(context).writeBytes(bytes)
                }.onFailure { Log.e(ModuleConstants.LOG_TAG, "fingerprint image failed", it) }.isSuccess
            }
        if (saved) {
            save(Prefs.FINGERPRINT_ICON_STYLE, FingerprintStyles.customStyle(System.currentTimeMillis()))
        } else {
            Toast.makeText(context, R.string.fingerprint_image_failed, Toast.LENGTH_SHORT).show()
        }
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
                initializer {
                    val savedStateExtras = MutableCreationExtras(this).apply { this[DEFAULT_ARGS_KEY] = Bundle.EMPTY }
                    MainViewModel(
                        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!,
                        savedStateExtras.createSavedStateHandle(),
                    )
                }
            }
    }
}
