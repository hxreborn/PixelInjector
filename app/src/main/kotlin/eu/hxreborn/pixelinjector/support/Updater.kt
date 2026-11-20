package eu.hxreborn.pixelinjector.support

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import eu.hxreborn.pixelinjector.App
import eu.hxreborn.pixelinjector.support.ReportBuilder.toHex
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface InstallStep {
    data object Idle : InstallStep

    data class Downloading(
        val bytes: Long,
        val total: Long,
    ) : InstallStep

    data object Verifying : InstallStep

    data object Confirming : InstallStep

    data object Recovering : InstallStep

    val busy: Boolean
        get() = this is Downloading || this == Verifying || this == Confirming || this == Recovering

    data class Failed(
        val cause: Cause,
        val detail: String?,
    ) : InstallStep {
        enum class Cause {
            DOWNLOAD,
            ASSET,
            NO_CHECKSUM,
            CHECKSUM,
            PACKAGE,
            VERSION,
            SIGNER,
            INSTALL,
        }
    }
}

data class UpdateInstall(
    val step: InstallStep,
    val release: Release? = null,
)

data class InstallConfirmation(
    val session: Int,
    val intent: Intent,
)

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        App.from(context).support.onInstallStatus(intent)
    }
}

class Updater(
    private val context: Context,
) {
    private val dir = File(context.cacheDir, UPDATE_DIR)
    private val installer get() = context.packageManager.packageInstaller

    fun clear() {
        dir.deleteRecursively()
    }

    fun download(
        asset: Release.Asset,
        cancelled: () -> Boolean,
        onProgress: (Long) -> Unit,
    ): Pair<File, String> {
        clear()
        if (!dir.mkdirs()) throw IOException("cannot create ${dir.path}")
        val file = File(dir, DOWNLOAD_NAME)
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = URL(asset.url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("http ${connection.responseCode}")
            }
            var bytes = 0L
            var reported = 0L
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        if (cancelled()) throw IOException("cancelled")
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        bytes += read
                        if (asset.size > 0 &&
                            bytes > asset.size
                        ) {
                            throw IOException("larger than ${asset.size} bytes")
                        }
                        if (bytes - reported >= PROGRESS_STEP_BYTES) {
                            reported = bytes
                            onProgress(bytes)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            file.delete()
            throw e
        } finally {
            connection.disconnect()
        }
        return file to digest.digest().toHex()
    }

    fun preflight(release: Release): InstallStep.Failed? {
        val asset = release.asset
        return when {
            asset == null || asset.name != Release.apkName(release.versionName) ||
                !asset.url.startsWith(HTTPS) -> {
                InstallStep.Failed(InstallStep.Failed.Cause.ASSET, asset?.url)
            }

            asset.sha256 == null -> {
                InstallStep.Failed(InstallStep.Failed.Cause.NO_CHECKSUM, null)
            }

            else -> {
                null
            }
        }
    }

    fun verify(
        file: File,
        sha256: String,
        release: Release,
    ): InstallStep.Failed? {
        if (!release.asset?.sha256.equals(sha256, ignoreCase = true)) {
            return InstallStep.Failed(InstallStep.Failed.Cause.CHECKSUM, sha256)
        }
        val archive = readArchive(file)
        return when {
            archive == null || archive.packageName != context.packageName -> {
                InstallStep.Failed(InstallStep.Failed.Cause.PACKAGE, archive?.packageName)
            }

            archive.longVersionCode != release.versionCode.toLong() ||
                archive.versionName != release.versionName -> {
                InstallStep.Failed(
                    InstallStep.Failed.Cause.VERSION,
                    "${archive.versionName} (${archive.longVersionCode})",
                )
            }

            !continuesInstalledSigner(archive) -> {
                InstallStep.Failed(InstallStep.Failed.Cause.SIGNER, null)
            }

            else -> {
                null
            }
        }
    }

    private fun readArchive(file: File): PackageInfo? =
        context.packageManager.getPackageArchiveInfo(
            file.path,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )

    private fun continuesInstalledSigner(archive: PackageInfo): Boolean {
        val incoming = archive.signingInfo ?: return false
        val installed =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo ?: return false
        val installedSigners = installed.apkContentsSigners.toSet()
        if (installedSigners.isEmpty()) return false
        return if (incoming.hasMultipleSigners() || installed.hasMultipleSigners()) {
            incoming.apkContentsSigners.toSet() == installedSigners
        } else {
            installedSigners.single() in incoming.signingCertificateHistory
        }
    }

    fun commit(
        file: File,
        onSession: (Int) -> Unit,
    ) {
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(file.length())
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        var sessionId: Int? = null
        try {
            val createdSession = installer.createSession(params)
            sessionId = createdSession
            onSession(createdSession)
            installer.openSession(createdSession).use { session ->
                file.inputStream().use { input ->
                    session.openWrite(APK_ENTRY, 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val callback =
                    PendingIntent.getBroadcast(
                        context,
                        createdSession,
                        Intent(context, UpdateInstallReceiver::class.java),
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                session.commit(callback.intentSender)
            }
        } catch (e: Exception) {
            sessionId?.let { runCatching { installer.abandonSession(it) } }
            throw e
        } finally {
            file.delete()
        }
    }

    fun ownedSession(session: Int): PackageInstaller.SessionInfo? =
        installer.getSessionInfo(session)?.takeIf {
            it.appPackageName == context.packageName &&
                it.installerPackageName == context.packageName
        }

    fun abandon(session: Int) {
        installer.abandonSession(session)
    }

    companion object {
        private const val UPDATE_DIR = "update"
        private const val HTTPS = "https://"
        private const val DOWNLOAD_NAME = "update.apk"
        private const val APK_ENTRY = "base.apk"
        private const val TIMEOUT_MS = 30_000
        private const val BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_STEP_BYTES = 256 * 1024L

        fun statusName(status: Int): String =
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> "STATUS_PENDING_USER_ACTION"
                PackageInstaller.STATUS_FAILURE -> "STATUS_FAILURE"
                PackageInstaller.STATUS_FAILURE_BLOCKED -> "STATUS_FAILURE_BLOCKED"
                PackageInstaller.STATUS_FAILURE_ABORTED -> "STATUS_FAILURE_ABORTED"
                PackageInstaller.STATUS_FAILURE_INVALID -> "STATUS_FAILURE_INVALID"
                PackageInstaller.STATUS_FAILURE_CONFLICT -> "STATUS_FAILURE_CONFLICT"
                PackageInstaller.STATUS_FAILURE_STORAGE -> "STATUS_FAILURE_STORAGE"
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "STATUS_FAILURE_INCOMPATIBLE"
                PackageInstaller.STATUS_FAILURE_TIMEOUT -> "STATUS_FAILURE_TIMEOUT"
                else -> "status $status"
            }
    }
}
