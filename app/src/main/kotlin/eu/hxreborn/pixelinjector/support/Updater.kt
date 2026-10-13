package eu.hxreborn.pixelinjector.support

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.support.ReportBuilder.toHex
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class Release(
    val versionName: String,
    val versionCode: Int,
    val publishedAt: String?,
    val url: String,
    val notesNew: List<String>,
    val notesFixed: List<String>,
    val notesOther: List<String>,
    val asset: Asset?,
) {
    data class Asset(
        val name: String,
        val size: Long,
        val sha256: String?,
        val url: String,
    )

    val newer: Boolean get() = versionCode > BuildConfig.VERSION_CODE

    companion object {
        private const val MAJOR = 10_000
        private const val MINOR = 100

        fun code(versionName: String): Int {
            val parts =
                versionName.removePrefix("v").split('.').map {
                    it.takeWhile(Char::isDigit).toIntOrNull()
                        ?: 0
                }
            return parts.getOrElse(0) { 0 } * MAJOR + parts.getOrElse(1) { 0 } * MINOR +
                parts.getOrElse(2) { 0 }
        }

        private fun JSONObject.strings(key: String): List<String> =
            optJSONArray(key)
                ?.let { array ->
                    List(array.length()) { array.getString(it) }
                }.orEmpty()

        private val bulletCleanups =
            listOf(
                Regex("""^\[`?[0-9a-f]{7,40}`?]\([^)]*\)\s*""") to "",
                Regex("""^\*\*[^*]+:\*\*\s*""") to "",
                Regex("""^\*\([^)]*\)\*\s*""") to "",
                Regex("""\s+by @\S+(\s+in\s+\[[^]]*]\([^)]*\))?\s*$""") to "",
                Regex("""(?i)(\s*,?\s*(closes\s+)?\(?\[[^]]*]\([^)]*\)\)?)+\s*$""") to "",
                Regex("""\[([^]]*)]\([^)]*\)""") to "$1",
                Regex("""\*\*|`""") to "",
            )

        private fun cleanBullet(text: String): String =
            bulletCleanups
                .fold(
                    text,
                ) { acc, (regex, replacement) -> regex.replace(acc, replacement) }
                .trim()

        private fun notes(body: String): JSONObject {
            val groups =
                mapOf(
                    "new" to mutableListOf<String>(),
                    "fixed" to mutableListOf(),
                    "other" to mutableListOf(),
                )
            var bucket: String? = null
            body.lines().forEach { line ->
                val heading = Regex("""^###\s+(.+?)\s*$""").find(line)
                if (heading != null) {
                    bucket =
                        when (heading.groupValues[1]) {
                            "Features" -> "new"
                            "Bug Fixes" -> "fixed"
                            else -> "other"
                        }
                    return@forEach
                }
                if (Regex("""^#{1,2}\s""").containsMatchIn(line)) bucket = null
                val bullet = Regex("""^\s*[*-]\s+(.+)$""").find(line)
                bucket?.let { key ->
                    bullet?.let {
                        groups.getValue(key) +=
                            cleanBullet(it.groupValues[1])
                    }
                }
            }
            return JSONObject().apply {
                groups.forEach { (key, lines) ->
                    put(key, org.json.JSONArray(lines))
                }
            }
        }

        fun fromGitHub(
            body: String,
            assetPattern: Regex,
        ): String {
            val json = JSONObject(body)
            val tag = json.getString("tag_name")
            val assets = json.optJSONArray("assets")
            val asset =
                assets
                    ?.let { array ->
                        List(array.length()) { array.getJSONObject(it) }
                    }?.firstOrNull { assetPattern.containsMatchIn(it.getString("name")) }
            return JSONObject()
                .put("tag", tag)
                .put("versionName", tag.removePrefix("v"))
                .put("publishedAt", json.optStringOrNull("published_at"))
                .put("url", json.optString("html_url"))
                .put("notes", notes(json.optString("body")))
                .put(
                    "asset",
                    asset?.let {
                        JSONObject()
                            .put("name", it.getString("name"))
                            .put("size", it.getLong("size"))
                            .put("sha256", it.optStringOrNull("digest")?.removePrefix("sha256:"))
                            .put("url", it.getString("browser_download_url"))
                    },
                ).toString()
        }

        fun parse(body: String): Release {
            val json = JSONObject(body)
            val versionName = json.getString("versionName")
            val notes = json.optJSONObject("notes") ?: JSONObject()
            val asset =
                json.optJSONObject("asset")?.let {
                    Asset(
                        it.getString("name"),
                        it.getLong("size"),
                        it.optStringOrNull("sha256"),
                        it.getString("url"),
                    )
                }
            return Release(
                versionName = versionName,
                versionCode = code(versionName),
                publishedAt = json.optStringOrNull("publishedAt"),
                url = json.optString("url"),
                notesNew = notes.strings("new"),
                notesFixed = notes.strings("fixed"),
                notesOther = notes.strings("other"),
                asset = asset,
            )
        }
    }
}

sealed interface InstallStep {
    data object Idle : InstallStep

    data object NeedsPermission : InstallStep

    data class Downloading(
        val bytes: Long,
        val total: Long,
        val partialHash: String,
    ) : InstallStep

    data class DownloadFailed(
        val bytes: Long,
        val reason: String,
    ) : InstallStep

    data class Verifying(
        val expected: String,
        val actual: String,
        val installedSigner: String?,
        val fileSigner: String?,
    ) : InstallStep

    data class Rejected(
        val cause: Cause,
        val expected: String?,
        val actual: String?,
        val checksum: String? = null,
    ) : InstallStep {
        enum class Cause { CHECKSUM, SIGNER, PACKAGE, NO_CHECKSUM }
    }

    data object Installing : InstallStep

    data class InstallFailed(
        val message: String,
    ) : InstallStep
}

class Updater(
    private val context: Context,
) {
    private val dir = File(context.cacheDir, "update")

    fun canRequestInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun download(
        asset: Release.Asset,
        cancelled: () -> Boolean,
        onProgress: (InstallStep.Downloading) -> Unit,
    ): Pair<File, String> {
        dir.listFiles()?.forEach(File::delete)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create ${dir.path}")
        val file = File(dir, DOWNLOAD_NAME)
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = URL(asset.url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode !=
                HttpURLConnection.HTTP_OK
            ) {
                throw IOException("http ${connection.responseCode}")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: asset.size
            var bytes = 0L
            var lastReport = 0L
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        if (cancelled()) throw IOException("cancelled")
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        bytes += read
                        if (bytes - lastReport >= REPORT_EVERY || bytes == total) {
                            lastReport = bytes
                            val partial = (digest.clone() as MessageDigest).digest().toHex()
                            onProgress(InstallStep.Downloading(bytes, total, partial))
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return file to digest.digest().toHex()
    }

    @Suppress("DEPRECATION")
    fun archiveInfo(file: File): PackageInfo? {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        return context.packageManager.getPackageArchiveInfo(
            file.path,
            PackageManager.PackageInfoFlags.of(flags.toLong()),
        )
    }

    fun installedSigners(): Set<String> =
        signers(
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            ),
        )

    @Suppress("DEPRECATION")
    fun signers(info: PackageInfo?): Set<String> {
        info ?: return emptySet()
        val certs = info.signingInfo?.apkContentsSigners ?: info.signatures ?: return emptySet()
        return certs.map { ReportBuilder.sha256(it.toByteArray()).toHex() }.toSet()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun install(
        file: File,
        onResult: (InstallStep) -> Unit,
    ) {
        val installer = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(file.length())
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        val sessionId = installer.createSession(params)
        val action = "${context.packageName}.INSTALL_RESULT.$sessionId"
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context,
                    intent: Intent,
                ) {
                    when (
                        val status =
                            intent.getIntExtra(
                                PackageInstaller.EXTRA_STATUS,
                                PackageInstaller.STATUS_FAILURE,
                            )
                    ) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            intent
                                .getParcelableExtra(
                                    Intent.EXTRA_INTENT,
                                    Intent::class.java,
                                )?.let {
                                    ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                        }

                        PackageInstaller.STATUS_SUCCESS -> {
                            ctx.unregisterReceiver(this)
                        }

                        else -> {
                            ctx.unregisterReceiver(this)
                            val message =
                                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                                    ?: "status $status"
                            onResult(
                                if (status ==
                                    PackageInstaller.STATUS_FAILURE_ABORTED
                                ) {
                                    InstallStep.Idle
                                } else {
                                    InstallStep.InstallFailed(message)
                                },
                            )
                        }
                    }
                }
            }
        context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        try {
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val statusIntent =
                    PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        Intent(action).setPackage(context.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                session.commit(statusIntent.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            runCatching { context.unregisterReceiver(receiver) }
            throw e
        }
    }

    companion object {
        private const val DOWNLOAD_NAME = "update.apk"
        private const val TIMEOUT_MS = 30_000
        private const val BUFFER = 64 * 1024
        private const val REPORT_EVERY = 256 * 1024L
    }
}
