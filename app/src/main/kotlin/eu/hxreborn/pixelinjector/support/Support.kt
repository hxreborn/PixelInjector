package eu.hxreborn.pixelinjector.support

import android.content.Context
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
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
    private val ledgerWriter = Dispatchers.IO.limitedParallelism(1)
    private val prefs = context.getSharedPreferences("support", Context.MODE_PRIVATE)
    private val ledger = Ledger(context.filesDir)
    private val sendLock = Mutex()
    val updater = Updater(context)

    private val _reports = MutableStateFlow(ledger.read())
    val reports: StateFlow<List<SentReport>> = _reports.asStateFlow()

    private val _release = MutableStateFlow<ReleaseCheck>(cachedRelease())
    val release: StateFlow<ReleaseCheck> = _release.asStateFlow()

    private val _install = MutableStateFlow<InstallStep>(InstallStep.Idle)
    val install: StateFlow<InstallStep> = _install.asStateFlow()

    private val _shareHookStatus = MutableStateFlow(prefs.getBoolean(SHARE_HOOK_STATUS, true))
    val shareHookStatus: StateFlow<Boolean> = _shareHookStatus.asStateFlow()

    private val _postponedVersion = MutableStateFlow(prefs.getInt(POSTPONED_VERSION, 0))
    val postponedVersion: StateFlow<Int> = _postponedVersion.asStateFlow()

    val updatedFrom: Int? =
        prefs.getInt(LAST_RUN_VERSION, 0).takeIf { it in 1 until BuildConfig.VERSION_CODE }.also {
            prefs.edit { putInt(LAST_RUN_VERSION, BuildConfig.VERSION_CODE) }
        }

    private fun cachedRelease(): ReleaseCheck {
        val body = prefs.getString(RELEASE_BODY, null) ?: return ReleaseCheck.Idle
        val release = runCatching { Release.parse(body) }.getOrNull()
        return ReleaseCheck.Done(release, prefs.getLong(RELEASE_CHECKED_AT, 0))
    }

    private fun mutate(change: (List<SentReport>) -> List<SentReport>) {
        val next = _reports.updateAndGet(change)
        scope.launch(ledgerWriter) { ledger.write(next) }
    }

    private fun replace(report: SentReport) =
        mutate { list ->
            list.map {
                if (it.id ==
                    report.id
                ) {
                    report
                } else {
                    it
                }
            }
        }

    fun setShareHookStatus(enabled: Boolean) {
        prefs.edit { putBoolean(SHARE_HOOK_STATUS, enabled) }
        _shareHookStatus.value = enabled
    }

    fun postponeUpdate(versionCode: Int) {
        prefs.edit { putInt(POSTPONED_VERSION, versionCode) }
        _postponedVersion.value = versionCode
    }

    fun markNotesSeen() = mutate { list -> list.map { it.copy(noteSeen = true) } }

    suspend fun send(
        encrypted: EncryptedReport,
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
            )
        ledger.envelope(report.id).writeBytes(encrypted.envelope)
        ledger.meta(report.id).writeBytes(encrypted.meta)
        mutate { listOf(report) + it }
        return upload(report)
    }

    private suspend fun upload(report: SentReport): SendResult =
        sendLock.withLock {
            val current =
                _reports.value.firstOrNull { it.id == report.id }
                    ?: return@withLock SendResult.Rejected("report deleted")
            if (current.status != ReportStatus.WAITING) {
                return@withLock if (current.status ==
                    ReportStatus.REJECTED
                ) {
                    SendResult.Rejected(current.error ?: "rejected")
                } else {
                    SendResult.Sent(current)
                }
            }
            val envelope = ledger.envelope(report.id).takeIf { it.exists() }?.readBytes()
            val meta = ledger.meta(report.id).takeIf { it.exists() }?.readBytes()
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
                        replace(report.copy(error = "reply is not JSON"))
                        return@withLock SendResult.Waiting(report)
                    }
                    Log.i(ModuleConstants.LOG_TAG, "report sent id=${report.id} code=${reply.code}")
                    val sent =
                        report.copy(
                            short = json.optStringOrNull("short"),
                            status = ReportStatus.RECEIVED,
                            error = null,
                        )
                    ledger.deletePayload(report.id)
                    replace(sent)
                    SendResult.Sent(sent)
                }

                is ApiResponse.Retryable -> {
                    Log.w(
                        ModuleConstants.LOG_TAG,
                        "report queued id=${report.id} reason=${reply.reason}",
                    )
                    replace(report.copy(error = reply.reason))
                    SendResult.Waiting(report)
                }

                is ApiResponse.Refused -> {
                    Log.w(
                        ModuleConstants.LOG_TAG,
                        "report refused id=${report.id} code=${reply.code} reason=${reply.reason}",
                    )
                    ledger.deletePayload(report.id)
                    replace(report.copy(status = ReportStatus.REJECTED, error = reply.reason))
                    SendResult.Rejected(reply.reason)
                }
            }
        }

    suspend fun resend(report: SentReport): SendResult = upload(report)

    fun retryWaiting() {
        scope.launch {
            _reports.value.filter { it.status == ReportStatus.WAITING }.forEach { upload(it) }
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
                ReportClient.statuses(tracked.map { it.id to it.readToken }) as? ApiResponse.Ok
                    ?: return@launch
            val byId =
                runCatching {
                    ReportClient.parseStatuses(reply.body)
                }.getOrDefault(emptyList()).associateBy { it.optString("id") }
            mutate { list ->
                list.mapNotNull { report ->
                    val json = byId[report.id] ?: return@mapNotNull report
                    val wire = json.optString("status")
                    if (wire == "gone") return@mapNotNull null
                    val note = json.optStringOrNull("note")
                    report.copy(
                        short = json.optStringOrNull("short") ?: report.short,
                        status = ReportStatus.of(wire) ?: report.status,
                        fixedIn = json.optStringOrNull("fixedIn"),
                        note = note,
                        noteSeen = report.noteSeen && note == report.note,
                    )
                }
            }
        }
    }

    private val pendingDeletes = ConcurrentHashMap<String, Job>()

    fun delete(report: SentReport) {
        mutate { list -> list.filterNot { it.id == report.id } }
        pendingDeletes[report.id] =
            scope.launch {
                delay(UNDO_WINDOW_MS)
                if (!pendingDeletes.remove(report.id, coroutineContext.job)) return@launch
                ledger.deletePayload(report.id)
                if (report.status != ReportStatus.WAITING &&
                    report.status != ReportStatus.REJECTED
                ) {
                    ReportClient.delete(report.id, report.deleteToken)
                }
            }
    }

    fun restore(report: SentReport) {
        val pending = pendingDeletes.remove(report.id) ?: return
        pending.cancel()
        mutate { list -> (list + report).sortedByDescending { it.sentAt } }
    }

    fun checkRelease(force: Boolean) {
        val last = prefs.getLong(RELEASE_CHECKED_AT, 0)
        if (!force && System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        if (_release.value is ReleaseCheck.Checking) return
        val previous = _release.value
        _release.value = ReleaseCheck.Checking(previous.known)
        scope.launch {
            _release.value =
                when (val reply = fetchRelease()) {
                    is ApiResponse.Ok -> {
                        val release = runCatching { Release.parse(reply.body) }.getOrNull()
                        val now = System.currentTimeMillis()
                        prefs.edit {
                            putString(RELEASE_BODY, reply.body)
                            putLong(RELEASE_CHECKED_AT, now)
                        }
                        ReleaseCheck.Done(release, now)
                    }

                    else -> {
                        Log.w(
                            ModuleConstants.LOG_TAG,
                            "release check failed source=github reason=$reply",
                        )
                        if (force) {
                            ReleaseCheck.Failed(last.takeIf { it > 0 }, previous.known)
                        } else {
                            previous.takeUnless { it is ReleaseCheck.Checking }
                                ?: ReleaseCheck.Idle
                        }
                    }
                }
            refreshStatuses()
        }
    }

    private fun fetchRelease(): ApiResponse {
        val worker = ReportClient.release()
        if (worker is ApiResponse.Ok) return worker
        Log.w(ModuleConstants.LOG_TAG, "release check failed source=worker reason=$worker")
        return when (val gitHub = ReportClient.gitHubRelease()) {
            is ApiResponse.Ok -> {
                runCatching {
                    ApiResponse.Ok(
                        gitHub.code,
                        Release.fromGitHub(gitHub.body, ReportConfig.assetPattern),
                    )
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

    fun postHookStatus(meta: () -> JSONObject?) {
        if (!_shareHookStatus.value) return
        val stamp = "${BuildConfig.VERSION_CODE}:${Build.ID}"
        if (prefs.getString(HOOK_STATUS_SENT, null) == stamp) return
        scope.launch {
            val body = meta() ?: return@launch
            if (ReportClient.postStatus(body.toString().toByteArray()) is ApiResponse.Ok) {
                prefs.edit {
                    putString(HOOK_STATUS_SENT, stamp)
                }
            }
        }
    }

    fun startInstall(release: Release) {
        val asset = release.asset ?: return
        installJob?.cancel()
        installJob =
            scope.launch {
                val job = coroutineContext.job
                _install.value = InstallStep.Downloading(0, asset.size, "")
                val downloaded =
                    runCatching {
                        updater.download(asset, { !job.isActive }) {
                            if (job.isActive) {
                                _install.value =
                                    it
                            }
                        }
                    }.getOrElse {
                        if (!job.isActive) return@launch
                        val bytes = (_install.value as? InstallStep.Downloading)?.bytes ?: 0
                        _install.value =
                            InstallStep.DownloadFailed(bytes, it.message ?: it.javaClass.simpleName)
                        return@launch
                    }
                val (file, hash) = downloaded
                val rejection = verify(file, hash, asset.sha256)
                if (!job.isActive) return@launch
                if (rejection != null) {
                    file.delete()
                    _install.value = rejection
                    return@launch
                }
                verifiedApk = file
                installVerified(file)
            }
    }

    private fun verify(
        file: File,
        hash: String,
        expected: String?,
    ): InstallStep.Rejected? {
        expected ?: return InstallStep.Rejected(InstallStep.Rejected.Cause.NO_CHECKSUM, null, hash)
        val installed = updater.installedSigners()
        _install.value = InstallStep.Verifying(expected, hash, installed.firstOrNull(), null)
        if (!expected.equals(hash, ignoreCase = true)) {
            return InstallStep.Rejected(InstallStep.Rejected.Cause.CHECKSUM, expected, hash)
        }
        val archive = updater.archiveInfo(file)
        val fileSigners = updater.signers(archive)
        _install.value =
            InstallStep.Verifying(
                expected,
                hash,
                installed.firstOrNull(),
                fileSigners.firstOrNull(),
            )
        return when {
            archive?.packageName != context.packageName -> {
                InstallStep.Rejected(
                    InstallStep.Rejected.Cause.PACKAGE,
                    context.packageName,
                    archive?.packageName,
                )
            }

            fileSigners.isEmpty() || fileSigners != installed -> {
                InstallStep.Rejected(
                    InstallStep.Rejected.Cause.SIGNER,
                    installed.firstOrNull(),
                    fileSigners.firstOrNull(),
                    checksum = hash,
                )
            }

            else -> {
                null
            }
        }
    }

    private var verifiedApk: File? = null

    private fun installVerified(file: File) {
        _install.value = InstallStep.Installing
        when (val root = Root.installApk(file)) {
            Root.Install.Done -> {
                return
            }

            is Root.Install.Failed -> {
                _install.value = InstallStep.InstallFailed(root.message)
            }

            Root.Install.Denied -> {
                if (!updater.canRequestInstalls()) {
                    _install.value = InstallStep.NeedsPermission
                } else {
                    runCatching { updater.install(file) { _install.value = it } }
                        .onFailure {
                            _install.value =
                                InstallStep.InstallFailed(it.message ?: it.javaClass.simpleName)
                        }
                }
            }
        }
    }

    fun resumeAfterInstallPermission() {
        val file = verifiedApk ?: return
        if (_install.value is InstallStep.NeedsPermission &&
            updater.canRequestInstalls()
        ) {
            scope.launch { installVerified(file) }
        }
    }

    private var installJob: Job? = null

    fun cancelInstall() {
        installJob?.cancel()
        _install.value = InstallStep.Idle
    }

    fun resetInstall() {
        if (_install.value !is InstallStep.Downloading &&
            _install.value !is InstallStep.Installing
        ) {
            _install.value = InstallStep.Idle
        }
    }

    companion object {
        private const val SHARE_HOOK_STATUS = "shareHookStatus"
        private const val HOOK_STATUS_SENT = "hookStatusSent"
        private const val RELEASE_BODY = "releaseBody"
        private const val RELEASE_CHECKED_AT = "releaseCheckedAt"
        private const val POSTPONED_VERSION = "postponedVersion"
        private const val LAST_RUN_VERSION = "lastRunVersion"
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val UNDO_WINDOW_MS = 6_000L
    }
}
