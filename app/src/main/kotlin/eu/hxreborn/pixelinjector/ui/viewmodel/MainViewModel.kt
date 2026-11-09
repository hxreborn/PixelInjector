package eu.hxreborn.pixelinjector.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
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
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.icons.FingerprintStyles
import eu.hxreborn.pixelinjector.prefs.AppPrefs
import eu.hxreborn.pixelinjector.prefs.PrefSpec
import eu.hxreborn.pixelinjector.prefs.Prefs
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
import eu.hxreborn.pixelinjector.ui.log.parseModuleLog
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    private val reloadRunner = HotReloadRunner(viewModelScope, application.filesDir)
    val support = app.support
    private val logArchive = LogArchive(application.filesDir)
    private val logLock = Mutex()
    private val fingerprintImageLock = Mutex()

    @Volatile private var rawLog: List<String>? = null

    val prefs: StateFlow<AppPrefs> =
        app.prefs.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PREFS_SUBSCRIPTION_TIMEOUT_MS), app.prefs.snapshot())

    val bound: StateFlow<Boolean> =
        app.service.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, app.service.value != null)

    val everBound: StateFlow<Boolean> = app.everBound
    val hideUnavailable: StateFlow<Boolean> = app.hideUnavailable

    fun setHideUnavailable(hide: Boolean) = app.setHideUnavailable(hide)

    val showPackages: StateFlow<Boolean> = app.showPackages

    fun setShowPackages(show: Boolean) = app.setShowPackages(show)

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

    private fun selectReloadTargets(list: List<HookedTarget>): List<HookedTarget> =
        list.filter { it.state == HookedTarget.State.STALE }.ifEmpty { list }

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

    private val _testNavigation = MutableStateFlow<TestNavigation?>(null)
    val testNavigation: StateFlow<TestNavigation?> = _testNavigation.asStateFlow()

    fun handleTestIntent(intent: Intent?) {
        testNavigation(intent)?.let { navigation ->
            navigation.destinations.filterIsInstance<Destination.BetterfoxEditor>().lastOrNull()?.let {
                betterfox.onOpen(it.packageName)
            }
            _testNavigation.value = navigation
        }
        testBetterfoxCommand(intent)?.let(betterfox::runCommand)
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
            combine(
                support.release,
                support.reports,
            ) { release, reports -> release to reports },
            support.autoCheckUpdates,
            support.postponedVersion,
            support.install,
            bound,
        ) { (release, reports), autoCheck, postponed, install, _ ->
            SupportState(release, reports, autoCheck, postponed, install, frameworkLabel())
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SupportState(
                support.release.value,
                support.reports.value,
                support.autoCheckUpdates.value,
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
        val (romCodes, unreadableRomCodes) = romCodes(missing)
        return ReportFacts(
            framework = service?.let { Framework(it.frameworkName, it.frameworkVersion, it.apiVersion) },
            rootDenied = log.denied,
            logLines = rawLog,
            tweaks =
                tweakUis.associate {
                    it.key to
                        when {
                            it.prefs.none { pref -> prefsNow[pref] } -> "off"
                            it.key in errors -> "miss"
                            it.key in errorLines.value -> "error"
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

    private fun romCodes(missing: List<MissingTarget>): Pair<Map<String, RomCode>, Map<String, String>> {
        if (!RomCode.wanted) return emptyMap<String, RomCode>() to emptyMap()
        val found = mutableMapOf<String, RomCode?>()
        val unreadable = mutableMapOf<String, String>()
        val codes =
            missing
                .mapNotNull { target ->
                    val kind =
                        when (tweakUis.firstOrNull { it.key == target.tweak }?.group) {
                            TweakGroup.SYSTEM_UI -> RomCode.SYSTEMUI
                            TweakGroup.SYSTEM -> RomCode.SERVICES
                            else -> return@mapNotNull null
                        }
                    val code =
                        found.getOrPut(kind) {
                            runCatching { RomCode.find(getApplication(), kind) }
                                .onFailure { Log.w(ModuleConstants.LOG_TAG, "rom code unreadable kind=$kind reason=${it.message}") }
                                .getOrNull()
                        }
                    if (code == null) unreadable[target.tweak] = kind
                    code?.let { target.tweak to it }
                }.toMap()
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
                logLock.withLock { if (rawLog == null || _moduleLog.value.denied) loadModuleLog() }
                _reportPreview.value = ReportBuilder.preview(reportFacts())
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
                    }.onFailure { Log.e(ModuleConstants.LOG_TAG, "report failed reason=${it.message}", it) }
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
            app.service.collect { service ->
                if (service == null) {
                    targets.value = emptyList()
                    scopedPackages.value = null
                    reloadRunner.onServiceDied()
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
                runCatching { service.runningTargets.filter { it.hasTarget() } }
                    .onFailure { Log.w(ModuleConstants.LOG_TAG, "getRunningTargets failed reason=${it.message}", it) }
                    .getOrDefault(emptyList())
            announceUpdate(targets.value)
            scopedPackages.value =
                runCatching { service.scope.toSet() }
                    .onFailure { Log.w(ModuleConstants.LOG_TAG, "getScope failed reason=${it.message}", it) }
                    .getOrNull()
        }
    }

    fun refreshModuleLog() {
        viewModelScope.launch(Dispatchers.IO) { logLock.withLock { loadModuleLog() } }
    }

    private fun loadModuleLog() {
        _moduleLog.update { it.copy(loading = true) }
        when (val read = runCatching { Root.readModuleLog() }.getOrElse { Root.LogRead.Denied }) {
            Root.LogRead.Denied -> {
                rawLog = null
                _moduleLog.value = ModuleLogState(loading = false, denied = true)
                _errors.value = deriveErrors(emptyList())
                errorLines.value = emptyMap()
            }

            is Root.LogRead.Lines -> {
                val lines = logArchive.merge(read.lines)
                rawLog = lines
                _moduleLog.value = ModuleLogState(loading = false, entries = parseModuleLog(lines, read.processes))
                val current = parseModuleLog(read.lines, read.processes)
                _errors.value = deriveErrors(current)
                errorLines.value = deriveErrorLines(current, read.processes.keys)
            }
        }
    }

    fun hotReload(selected: List<HookedTarget>) {
        val service = app.service.value ?: return
        reloadRunner.start(service, selected)
    }

    fun hotReloadPending() = hotReload(selectReloadTargets(reloadTargets.value))

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
                initializer { MainViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!) }
            }
    }
}
