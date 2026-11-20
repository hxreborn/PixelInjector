package eu.hxreborn.pixelinjector.support

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
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

    val busy: Boolean
        get() = this is Downloading || this == Verifying || this == Confirming

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

data class InstallHandoff(
    val attempt: Long,
    val intent: Intent,
)

data class UpdateInstall(
    val step: InstallStep,
    val release: Release? = null,
)

class UpdateFileProvider : FileProvider()

class Updater(
    private val context: Context,
) {
    private val dir = File(context.cacheDir, UPDATE_DIR)
    private val authority = "${context.packageName}.update"

    fun clear() {
        dir.deleteRecursively()
    }

    fun clearStale() {
        val staleBefore = System.currentTimeMillis() - STALE_AFTER_MS
        dir.listFiles()?.filter { it.lastModified() < staleBefore }?.forEach(File::delete)
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

    fun installIntent(file: File): Intent? {
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(FileProvider.getUriForFile(context, authority, file), APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val systemInstaller =
            context.packageManager
                .queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(
                        (PackageManager.MATCH_SYSTEM_ONLY or PackageManager.MATCH_DEFAULT_ONLY)
                            .toLong(),
                    ),
                ).firstOrNull()
                ?.activityInfo
                ?.packageName ?: return null
        return intent.setPackage(systemInstaller)
    }

    companion object {
        private const val UPDATE_DIR = "update"
        private const val HTTPS = "https://"
        private const val DOWNLOAD_NAME = "update.apk"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val TIMEOUT_MS = 30_000
        private const val BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_STEP_BYTES = 256 * 1024L
        private const val STALE_AFTER_MS = 24 * 60 * 60 * 1000L
    }
}
