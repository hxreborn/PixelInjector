package eu.hxreborn.pixelinjector.support

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Network
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.ModuleConstants
import eu.hxreborn.pixelinjector.ui.util.Root
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

sealed interface ReleaseCheck {
    val known: Release?

    data object Idle : ReleaseCheck {
        override val known: Release? = null
    }

    data class Checking(
        override val known: Release?,
    ) : ReleaseCheck

    data class Done(
        val release: Release?,
        val checkedAt: Long,
    ) : ReleaseCheck {
        override val known: Release? get() = release
    }

    data class Failed(
        val lastCheckedAt: Long?,
        override val known: Release?,
    ) : ReleaseCheck
}

sealed interface SendResult {
    data class Sent(
        val report: SentReport,
    ) : SendResult

    data class Waiting(
        val report: SentReport,
    ) : SendResult

    data class Rejected(
        val reason: String,
    ) : SendResult
}

class Support(
    private val context: Context,
) {
    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO +
                CoroutineExceptionHandler {
                    _,
                    e,
                    ->
                    Log.e(ModuleConstants.LOG_TAG, "support task failed reason=${e.message}", e)
                },
        )
    private val reportWriteDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val prefs = preferences(context)
    private val reportStore = ReportStore(context.filesDir)
    private val sendLock = Mutex()
    private val romCodeLock = Mutex()
    private val updater = Updater(context)

    private val _reports = MutableStateFlow(reportStore.read())
    val reports: StateFlow<List<SentReport>> = _reports.asStateFlow()

    private val _release = MutableStateFlow<ReleaseCheck>(cachedRelease())
    val release: StateFlow<ReleaseCheck> = _release.asStateFlow()

    @Volatile private var installSession = prefs.getInt(INSTALL_SESSION, NO_SESSION)

    private val _install = MutableStateFlow(UpdateInstall(restoreInstallState()))
    val install: StateFlow<UpdateInstall> = _install.asStateFlow()

    private val _installConfirm = MutableStateFlow<InstallConfirmation?>(null)
    val installConfirm: StateFlow<InstallConfirmation?> = _installConfirm.asStateFlow()
    private var installJob: Job? = null

    private val _autoCheckUpdates = MutableStateFlow(prefs.getBoolean(AUTO_CHECK_UPDATES, true))
    val autoCheckUpdates: StateFlow<Boolean> = _autoCheckUpdates.asStateFlow()

    private val _updateNotifications =
        MutableStateFlow(prefs.getBoolean(UPDATE_NOTIFICATIONS, false))
    val updateNotifications: StateFlow<Boolean> = _updateNotifications.asStateFlow()

    val updateNotificationsEnabled: Boolean
        get() = _autoCheckUpdates.value && _updateNotifications.value

    private val _updateNotificationScheduleFailed =
        MutableStateFlow(updateNotificationsEnabled && !UpdateWatch.isScheduled(context))
    val updateNotificationScheduleFailed = _updateNotificationScheduleFailed.asStateFlow()

    private val _postponedVersion = MutableStateFlow(prefs.getInt(POSTPONED_VERSION, 0))
    val postponedVersion: StateFlow<Int> = _postponedVersion.asStateFlow()

    var updateNoticePending: Boolean =
        (prefs.getInt(LAST_RUN_VERSION, 0) in 1 until BuildConfig.VERSION_CODE).also { pending ->
            if (!pending) prefs.edit { putInt(LAST_RUN_VERSION, BuildConfig.VERSION_CODE) }
        }
        private set

    fun consumeUpdateNotice(): Boolean {
        if (!updateNoticePending) return false
        updateNoticePending = false
        prefs.edit { putInt(LAST_RUN_VERSION, BuildConfig.VERSION_CODE) }
        return true
    }

    init {
        installJob = scope.launch { updater.clear() }
    }

    private fun restoreInstallState(): InstallStep {
        if (installSession == NO_SESSION) return InstallStep.Idle
        return if (updater.ownedSession(installSession) !=
            null
        ) {
            InstallStep.Recovering
        } else {
            InstallStep.Idle
        }
    }

    private fun trackInstallSession(session: Int) {
        if (!prefs.edit().putInt(INSTALL_SESSION, session).commit()) {
            prefs.edit { remove(INSTALL_SESSION) }
            throw IOException("could not save install session $session")
        }
        installSession = session
    }

    private fun clearInstallSession() {
        installSession = NO_SESSION
        prefs.edit { remove(INSTALL_SESSION) }
        _installConfirm.value = null
    }

    private fun cachedRelease(): ReleaseCheck {
        val body = prefs.getString(RELEASE_BODY, null) ?: return ReleaseCheck.Idle
        val release = runCatching { Release.parse(body) }.getOrNull()
        return ReleaseCheck.Done(release, prefs.getLong(RELEASE_CHECKED_AT, 0))
    }

    private fun updateReports(change: (List<SentReport>) -> List<SentReport>) {
        _reports.update(change)
        scope.launch(reportWriteDispatcher) { reportStore.write(_reports.value) }
    }

    private fun replaceReport(report: SentReport) =
        updateReports { list ->
            list.map { if (it.id == report.id) report else it }
        }

    fun setAutoCheckUpdates(enabled: Boolean) {
        prefs.edit { putBoolean(AUTO_CHECK_UPDATES, enabled) }
        _autoCheckUpdates.value = enabled
        setUpdateNotifications(_updateNotifications.value)
    }

    fun setUpdateNotifications(enabled: Boolean) {
        val scheduled = UpdateWatch.schedule(context, enabled && _autoCheckUpdates.value)
        val optedIn = enabled && (scheduled || _updateNotifications.value)
        if (optedIn != _updateNotifications.value) {
            prefs.edit { putBoolean(UPDATE_NOTIFICATIONS, optedIn) }
            _updateNotifications.value = optedIn
        }
        _updateNotificationScheduleFailed.value = !scheduled
    }

    fun postponeUpdate(versionCode: Int) {
        prefs.edit { putInt(POSTPONED_VERSION, versionCode) }
        _postponedVersion.value = versionCode
        if (prefs.getInt(NOTIFIED_VERSION, 0) <=
            versionCode
        ) {
            UpdateWatch.cancelNotification(context)
        }
    }

    fun noteKeyFor(replyTo: String?): Hpke.NoteKey =
        replyTo?.let { root -> _reports.value.firstOrNull { it.id == root }?.noteKey }
            ?: Hpke.newNoteKey()

    fun markNotesSeen() = updateReports { list -> list.map { it.copy(noteSeen = true) } }

    suspend fun send(
        encrypted: EncryptedReport,
        message: String,
        tweak: String?,
        replyTo: String?,
    ): SendResult {
        val report =
            SentReport(
                id = encrypted.id,
                short = null,
                tweak = tweak,
                sentAt = System.currentTimeMillis(),
                status = ReportStatus.WAITING,
                fixedIn = null,
                note = null,
                readToken = encrypted.readToken,
                deleteToken = encrypted.deleteToken,
                replyTo = replyTo,
                build = Build.ID,
                message = message.ifBlank { null },
                noteKeyPrivate =
                    Base64.getEncoder().encodeToString(
                        KeyWrap.wrap(encrypted.noteKey.privatePkcs8),
                    ),
                noteKeyPublic = Base64.getEncoder().encodeToString(encrypted.noteKey.publicRaw),
                romCode = encrypted.romCode,
            )
        reportStore.envelopeFile(report.id).writeBytes(encrypted.envelope)
        reportStore.metadataFile(report.id).writeBytes(encrypted.meta)
        updateReports { listOf(report) + it }
        return upload(report)
    }

    private suspend fun upload(report: SentReport): SendResult =
        sendLock.withLock {
            val current =
                _reports.value.firstOrNull { it.id == report.id }
                    ?: return@withLock SendResult.Rejected("report deleted")
            if (current.status != ReportStatus.WAITING) {
                return@withLock if (current.status == ReportStatus.REJECTED) {
                    SendResult.Rejected(current.error ?: "rejected")
                } else {
                    SendResult.Sent(current)
                }
            }
            val envelope = reportStore.envelopeFile(report.id).takeIf { it.exists() }?.readBytes()
            val meta = reportStore.metadataFile(report.id).takeIf { it.exists() }?.readBytes()
            if (envelope == null || meta == null) return SendResult.Rejected("missing payload")
            when (
                val reply =
                    ReportClient.putReport(
                        report.id,
                        meta,
                        envelope,
                        ReportBuilder.tokenHash(report.readToken),
                        ReportBuilder.tokenHash(report.deleteToken),
                        report.replyTo,
                    )
            ) {
                is ApiResponse.Ok -> {
                    val json = runCatching { JSONObject(reply.body) }.getOrNull()
                    if (json == null) {
                        Log.w(
                            ModuleConstants.LOG_TAG,
                            "report queued id=${report.id} reason=reply is not JSON code=${reply.code}",
                        )
                        replaceReport(report.copy(error = "reply is not JSON"))
                        return@withLock SendResult.Waiting(report)
                    }
                    Log.i(ModuleConstants.LOG_TAG, "report sent id=${report.id} code=${reply.code}")
                    val sent =
                        report.copy(
                            short = json.optStringOrNull("short"),
                            status = ReportStatus.RECEIVED,
                            error = null,
                        )
                    reportStore.deletePayload(report.id)
                    replaceReport(sent)
                    if (sent.romCode != null) scope.launch { uploadRomCode(sent.id) }
                    SendResult.Sent(sent)
                }

                is ApiResponse.Retryable -> {
                    Log.w(
                        ModuleConstants.LOG_TAG,
                        "report queued id=${report.id} reason=${reply.reason}",
                    )
                    replaceReport(report.copy(error = reply.reason))
                    SendResult.Waiting(report)
                }

                is ApiResponse.Refused -> {
                    Log.w(
                        ModuleConstants.LOG_TAG,
                        "report refused id=${report.id} code=${reply.code} reason=${reply.reason}",
                    )
                    reportStore.deletePayload(report.id)
                    replaceReport(report.copy(status = ReportStatus.REJECTED, error = reply.reason))
                    SendResult.Rejected(reply.reason)
                }
            }
        }

    suspend fun resend(report: SentReport): SendResult = upload(report)

    private fun updateReport(
        id: String,
        change: (SentReport) -> SentReport,
    ) = updateReports { list -> list.map { if (it.id == id) change(it) else it } }

    private suspend fun uploadRomCode(id: String) =
        romCodeLock.withLock {
            val report = _reports.value.firstOrNull { it.id == id } ?: return@withLock
            val code = report.romCode?.takeUnless { report.romCodeUploaded } ?: return@withLock
            val current = runCatching { RomCode.sha256Of(File(code.path)) }.getOrNull()
            if (current != code.sha256) {
                Log.w(ModuleConstants.LOG_TAG, "rom code changed id=$id kind=${code.kind}")
                return@withLock dropRomCode(id, "changed")
            }
            when (val state = ReportClient.hasAttachment(code.sha256)) {
                AttachmentState.Present -> {
                    Log.i(ModuleConstants.LOG_TAG, "rom code present id=$id kind=${code.kind}")
                    return@withLock updateReport(id) { it.copy(romCodeUploaded = true) }
                }

                is AttachmentState.Failed -> {
                    Log.w(
                        ModuleConstants.LOG_TAG,
                        "rom code queued id=$id kind=${code.kind} reason=${state.reason}",
                    )
                    return@withLock
                }

                AttachmentState.Absent -> {}
            }
            val envelope =
                runCatching { ReportBuilder.sealRomCode(code) }.getOrElse {
                    Log.w(
                        ModuleConstants.LOG_TAG,
                        "rom code unsealable id=$id kind=${code.kind} reason=${it.message}",
                    )
                    return@withLock dropRomCode(id, it.message ?: it.javaClass.simpleName)
                }
            if (envelope.size > ReportBuilder.MAX_ATTACHMENT_BYTES) {
                Log.w(
                    ModuleConstants.LOG_TAG,
                    "rom code too large id=$id kind=${code.kind} bytes=${envelope.size}",
                )
                return@withLock dropRomCode(id, "too large")
            }
            val reply =
                runCatching {
                    ReportClient.putAttachment(code.sha256, id, report.deleteToken, envelope)
                }.getOrElse { ApiResponse.Refused(0, it.message ?: it.javaClass.simpleName) }
            when (reply) {
                is ApiResponse.Ok -> {
                    Log.i(
                        ModuleConstants.LOG_TAG,
                        "rom code sent id=$id kind=${code.kind} code=${reply.code}",
                    )
                    updateReport(id) { it.copy(romCodeUploaded = true) }
                }

                is ApiResponse.Retryable -> {
                    Log.w(
                        ModuleConstants.LOG_TAG,
                        "rom code queued id=$id kind=${code.kind} reason=${reply.reason}",
                    )
                }

                is ApiResponse.Refused -> {
                    Log.w(
                        ModuleConstants.LOG_TAG,
                        "rom code refused id=$id kind=${code.kind} code=${reply.code} reason=${reply.reason}",
                    )
                    dropRomCode(id, reply.reason)
                }
            }
        }

    private fun dropRomCode(
        id: String,
        reason: String,
    ) = updateReport(id) { it.copy(romCode = null, romCodeFailed = reason) }

    fun retryWaiting() {
        scope.launch {
            _reports.value.filter { it.status == ReportStatus.WAITING }.forEach { upload(it) }
            _reports.value
                .filter {
                    it.romCode != null &&
                        !it.romCodeUploaded &&
                        it.status != ReportStatus.WAITING &&
                        it.status != ReportStatus.REJECTED
                }.forEach { uploadRomCode(it.id) }
        }
    }

    fun refreshStatuses() {
        scope.launch {
            val tracked =
                _reports.value.filter {
                    it.status != ReportStatus.WAITING &&
                        it.status != ReportStatus.REJECTED
                }
            if (tracked.isEmpty()) return@launch
            val reply =
                ReportClient.fetchReportStatuses(
                    tracked.map { it.id to it.readToken },
                ) as? ApiResponse.Ok
                    ?: return@launch
            val byId =
                runCatching {
                    ReportClient.parseStatuses(reply.body)
                }.getOrDefault(emptyList()).associateBy { it.optString("id") }
            updateReports { list ->
                list.mapNotNull { report ->
                    val json = byId[report.id] ?: return@mapNotNull report
                    val wire = json.optString("status")
                    if (wire == "gone") return@mapNotNull null
                    val note = report.decodeNote(json)
                    report.copy(
                        short = json.optStringOrNull("short") ?: report.short,
                        status = ReportStatus.of(wire) ?: report.status,
                        fixedIn = json.optStringOrNull("fixedIn"),
                        fixLine = json.optStringOrNull("fixLine"),
                        issue = json.optIntOrNull("issue"),
                        note = note,
                        noteSeen = report.noteSeen && note == report.note,
                        events =
                            ReportEvent
                                .list(json.optJSONArray("events"), report::decodeNote)
                                .ifEmpty { report.events },
                    )
                }
            }
        }
    }

    private val pendingDeletionJobs = ConcurrentHashMap<String, Job>()
    private val pendingReportDeletions = ConcurrentHashMap<String, List<SentReport>>()

    fun delete(report: SentReport) {
        val group = listOf(report) + _reports.value.filter { it.replyTo == report.id }
        val ids = group.map { it.id }.toSet()
        updateReports { list -> list.filterNot { it.id in ids } }
        pendingReportDeletions[report.id] = group
        pendingDeletionJobs[report.id] =
            scope.launch {
                delay(UNDO_WINDOW_MS)
                if (!pendingDeletionJobs.remove(report.id, coroutineContext.job)) return@launch
                pendingReportDeletions.remove(report.id)
                group.forEach { reportStore.deletePayload(it.id) }
                if (report.status != ReportStatus.WAITING &&
                    report.status != ReportStatus.REJECTED
                ) {
                    ReportClient.deleteReport(report.id, report.deleteToken)
                }
            }
    }

    fun restore(report: SentReport) {
        val pending = pendingDeletionJobs.remove(report.id) ?: return
        pending.cancel()
        val group = pendingReportDeletions.remove(report.id) ?: listOf(report)
        updateReports { list -> (list + group).sortedByDescending { it.sentAt } }
    }

    private fun releaseCheckDue(): Boolean {
        val now = System.currentTimeMillis()
        return _autoCheckUpdates.value &&
            now - prefs.getLong(RELEASE_CHECKED_AT, 0) >= CHECK_INTERVAL_MS &&
            now >= prefs.getLong(RELEASE_RETRY_AT, 0)
    }

    private fun startReleaseCheck(): ReleaseCheck? {
        val previous = _release.value
        if (previous is ReleaseCheck.Checking) return null
        return previous.takeIf { _release.compareAndSet(it, ReleaseCheck.Checking(it.known)) }
    }

    fun checkRelease(force: Boolean) {
        if (!force && !releaseCheckDue()) return
        val previous = startReleaseCheck() ?: return
        scope.launch { finishReleaseCheck(force, previous) }
    }

    fun releaseToNotify(
        network: Network?,
        checkActive: () -> Unit,
    ): Release? {
        checkActive()
        if (!updateNotificationsEnabled) return null
        if (releaseCheckDue()) {
            startReleaseCheck()?.let { finishReleaseCheck(false, it, network, checkActive) }
        }
        checkActive()
        if (!updateNotificationsEnabled) return null
        return _release.value.known.releaseToNotify(
            postponedVersionCode = _postponedVersion.value,
            notifiedVersionCode = prefs.getInt(NOTIFIED_VERSION, 0),
        )
    }

    fun markNotified(release: Release) {
        prefs.edit { putInt(NOTIFIED_VERSION, release.versionCode) }
    }

    private fun finishReleaseCheck(
        force: Boolean,
        previous: ReleaseCheck,
        network: Network? = null,
        checkActive: () -> Unit = {},
    ) {
        try {
            _release.value = fetchReleaseCheck(force, previous, network, checkActive)
        } catch (e: Exception) {
            _release.value = previous
            throw e
        }
    }

    private fun fetchReleaseCheck(
        force: Boolean,
        previous: ReleaseCheck,
        network: Network?,
        checkActive: () -> Unit,
    ): ReleaseCheck {
        val last = prefs.getLong(RELEASE_CHECKED_AT, 0)
        val reply = fetchRelease(network, checkActive)
        checkActive()
        return when (reply) {
            is ApiResponse.Ok -> {
                val release = runCatching { Release.parse(reply.body) }.getOrNull()
                val now = System.currentTimeMillis()
                val rollback =
                    prefs
                        .edit()
                        .putString(RELEASE_BODY, prefs.getString(RELEASE_BODY, null))
                        .putString(GITHUB_ETAG, prefs.getString(GITHUB_ETAG, null))
                        .apply {
                            if (prefs.contains(RELEASE_CHECKED_AT)) {
                                putLong(RELEASE_CHECKED_AT, last)
                            } else {
                                remove(RELEASE_CHECKED_AT)
                            }
                            if (prefs.contains(RELEASE_RETRY_AT)) {
                                putLong(RELEASE_RETRY_AT, prefs.getLong(RELEASE_RETRY_AT, 0))
                            } else {
                                remove(RELEASE_RETRY_AT)
                            }
                        }
                checkActive()
                val saved =
                    prefs
                        .edit()
                        .putString(RELEASE_BODY, reply.body)
                        .putString(GITHUB_ETAG, reply.etag)
                        .putLong(RELEASE_CHECKED_AT, now)
                        .remove(RELEASE_RETRY_AT)
                        .commit()
                if (saved) {
                    ReleaseCheck.Done(release, now)
                } else {
                    rollback.apply()
                    val failure = IOException("could not save release cache")
                    if (!force) throw failure
                    Log.w(ModuleConstants.LOG_TAG, "release check failed", failure)
                    ReleaseCheck.Failed(last.takeIf { it > 0 }, previous.known)
                }
            }

            else -> {
                Log.w(
                    ModuleConstants.LOG_TAG,
                    "release check failed source=github reason=$reply",
                )
                (reply as? ApiResponse.Retryable)?.afterSeconds?.let { seconds ->
                    checkActive()
                    prefs.edit {
                        putLong(
                            RELEASE_RETRY_AT,
                            System.currentTimeMillis() + seconds * MILLIS,
                        )
                    }
                }
                if (force) {
                    ReleaseCheck.Failed(last.takeIf { it > 0 }, previous.known)
                } else {
                    previous.takeUnless { it is ReleaseCheck.Checking }
                        ?: ReleaseCheck.Idle
                }
            }
        }
    }

    private fun fetchRelease(
        network: Network?,
        checkActive: () -> Unit,
    ): ApiResponse {
        checkActive()
        val worker = ReportClient.fetchRelease(network)
        checkActive()
        if (worker is ApiResponse.Ok) {
            val cached = prefs.getString(RELEASE_BODY, null)
            val body =
                Release.enrichWorkerNotes(worker.body, cached) {
                    checkActive()
                    (ReportClient.fetchGitHubRelease(etag = null, network) as? ApiResponse.Ok)?.body
                }
            return worker.copy(body = body, etag = null)
        }
        Log.w(ModuleConstants.LOG_TAG, "release check failed source=worker reason=$worker")
        val cached = prefs.getString(RELEASE_BODY, null)
        val cachedHasAsset = cached?.contains("\"asset\"") == true
        val etag = prefs.getString(GITHUB_ETAG, null)?.takeIf { cachedHasAsset }
        checkActive()
        val gitHub = ReportClient.fetchGitHubRelease(etag, network)
        checkActive()
        return when (gitHub) {
            is ApiResponse.Ok if gitHub.code == HttpURLConnection.HTTP_NOT_MODIFIED &&
                etag != null && cached != null -> {
                gitHub.copy(body = cached, etag = etag)
            }

            is ApiResponse.Ok -> {
                runCatching {
                    gitHub.copy(body = Release.fromGitHub(gitHub.body))
                }.getOrElse {
                    ApiResponse.Refused(
                        gitHub.code,
                        it.message ?: "bad release json",
                    )
                }
            }

            else -> {
                gitHub
            }
        }
    }

    fun startInstall(release: Release) {
        val asset = release.asset ?: return
        if (_install.value.step.busy) return
        if (installSession != NO_SESSION) {
            if (updater.ownedSession(installSession) != null) return
            clearInstallSession()
        }
        updater.preflight(release)?.let {
            failInstall(it)
            return
        }
        _install.value = UpdateInstall(InstallStep.Downloading(0, asset.size), release)
        val previous = installJob
        installJob =
            scope.launch {
                previous?.join()
                val job = coroutineContext.job
                val (file, sha256) =
                    try {
                        updater.download(asset, { !job.isActive }) {
                            updateInstallStep(InstallStep.Downloading(it, asset.size))
                        }
                    } catch (e: Exception) {
                        failInstall(e.toFailure(InstallStep.Failed.Cause.DOWNLOAD))
                        return@launch
                    }
                updateInstallStep(InstallStep.Verifying)
                val rejection =
                    try {
                        updater.verify(file, sha256, release)
                    } catch (e: Exception) {
                        e.toFailure(InstallStep.Failed.Cause.PACKAGE)
                    }
                if (rejection != null) {
                    file.delete()
                    failInstall(rejection)
                    return@launch
                }
                try {
                    updater.commit(file, ::trackInstallSession)
                } catch (e: Exception) {
                    failInstall(e.toFailure(InstallStep.Failed.Cause.INSTALL))
                }
            }
    }

    fun onInstallStatus(intent: Intent) {
        val session = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, NO_SESSION)
        if (session == NO_SESSION || session != installSession) {
            Log.i(
                ModuleConstants.LOG_TAG,
                "update install status ignored session=$session active=$installSession",
            )
            return
        }
        val status =
            intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE,
            )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) {
                    updateInstallStep(InstallStep.Recovering)
                } else {
                    updateInstallStep(InstallStep.Confirming)
                    _installConfirm.value = InstallConfirmation(session, confirm)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                clearInstallSession()
                updateInstallStep(InstallStep.Idle)
            }

            else -> {
                val detail = listOfNotNull(Updater.statusName(status), message).joinToString(" ")
                failInstall(InstallStep.Failed(InstallStep.Failed.Cause.INSTALL, detail))
            }
        }
    }

    private fun Exception.toFailure(cause: InstallStep.Failed.Cause) =
        InstallStep.Failed(cause, message ?: javaClass.simpleName)

    fun takeInstallConfirm(): InstallConfirmation? = _installConfirm.getAndUpdate { null }

    fun discardStagedUpdate() {
        if (_install.value.step ==
            InstallStep.Recovering
        ) {
            cancelInstallConfirmation(installSession, discard = true)
        }
    }

    fun installConfirmationUnavailable(
        session: Int,
        detail: String?,
    ) {
        cancelInstallConfirmation(session, discard = true)
        if (_install.value.step == InstallStep.Idle) {
            failInstall(InstallStep.Failed(InstallStep.Failed.Cause.INSTALL, detail))
        }
    }

    fun cancelInstallConfirmation(session: Int) =
        cancelInstallConfirmation(session, discard = false)

    private fun cancelInstallConfirmation(
        session: Int,
        discard: Boolean,
    ) {
        if (session == NO_SESSION || installSession != session) return
        val current = updater.ownedSession(session)
        if (current == null && !discard) {
            updateInstallStep(InstallStep.Recovering)
            return
        }
        if (!discard && current != null && current.isSealed && current.isActive) return
        try {
            if (current != null) updater.abandon(session)
        } catch (e: Exception) {
            Log.w(
                ModuleConstants.LOG_TAG,
                "update session cancel failed id=$session reason=${e.message}",
                e,
            )
            updateInstallStep(InstallStep.Recovering)
            return
        }
        clearInstallSession()
        updateInstallStep(InstallStep.Idle)
    }

    private fun failInstall(failure: InstallStep.Failed) {
        Log.w(
            ModuleConstants.LOG_TAG,
            "update install failed cause=${failure.cause} detail=${failure.detail}",
        )
        clearInstallSession()
        updateInstallStep(failure)
    }

    private fun updateInstallStep(step: InstallStep) {
        _install.update { UpdateInstall(step, it.release.takeIf { step.busy }) }
    }

    companion object {
        private const val AUTO_CHECK_UPDATES = "autoCheckUpdates"
        private const val UPDATE_NOTIFICATIONS = "updateNotifications"
        private const val RELEASE_BODY = "releaseBody"
        private const val GITHUB_ETAG = "githubEtag"
        private const val RELEASE_CHECKED_AT = "releaseCheckedAt"
        private const val RELEASE_RETRY_AT = "releaseRetryAt"
        private const val POSTPONED_VERSION = "postponedVersion"
        private const val LAST_RUN_VERSION = "lastRunVersion"
        private const val NOTIFIED_VERSION = "notifiedVersion"
        private const val INSTALL_SESSION = "installSession"
        private const val NO_SESSION = -1
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val UNDO_WINDOW_MS = 6_000L
        private const val MILLIS = 1000L

        internal fun scheduleSavedUpdateNotifications(context: Context) {
            val prefs = preferences(context)
            val enabled =
                prefs.getBoolean(AUTO_CHECK_UPDATES, true) &&
                    prefs.getBoolean(UPDATE_NOTIFICATIONS, false)
            UpdateWatch.schedule(context, enabled)
        }

        private fun preferences(context: Context) =
            context.getSharedPreferences("support", Context.MODE_PRIVATE)
    }
}
