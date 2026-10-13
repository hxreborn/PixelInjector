package eu.hxreborn.pixelinjector.support

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import eu.hxreborn.pixelinjector.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.GZIPOutputStream

data class MissingTarget(
    val tweak: String,
    val member: String?,
    val reason: String?,
)

data class Framework(
    val name: String,
    val version: String,
    val api: Int,
)

class ReportFacts(
    val framework: Framework?,
    val rootDenied: Boolean,
    val logLines: List<String>?,
    val tweaks: Map<String, String>,
    val missing: List<MissingTarget>,
    val packages: Collection<String>,
)

class ReportPreview(
    val facts: ReportFacts,
    val log: String,
    val logLineCount: Int,
    val hidden: Int,
)

class EncryptedReport(
    val id: String,
    val meta: ByteArray,
    val envelope: ByteArray,
    val readToken: String,
    val deleteToken: String,
)

object ReportBuilder {
    private const val MAX_LOG_LINES = 4000
    private const val TOKEN_BYTES = 32
    private const val ENVELOPE_VERSION: Byte = 1
    private const val SIGNER_BYTES = 4
    private val random = SecureRandom()

    fun preview(facts: ReportFacts): ReportPreview {
        val lines = facts.logLines?.takeLast(MAX_LOG_LINES).orEmpty()
        val redacted = Redactor.redact(lines.joinToString("\n"))
        return ReportPreview(facts, redacted.text, lines.size, redacted.hidden)
    }

    fun signer(context: Context): String? =
        runCatching {
            val info =
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
            val cert =
                info.signingInfo
                    ?.apkContentsSigners
                    ?.firstOrNull()
                    ?.toByteArray() ?: return null
            sha256(cert).copyOf(SIGNER_BYTES).toHex()
        }.getOrNull()

    fun meta(
        context: Context,
        facts: ReportFacts,
        tweak: String?,
        forReport: Boolean,
    ): JSONObject {
        val missing =
            facts.missing.firstOrNull { it.tweak == tweak }
                ?: facts.missing.firstOrNull().takeIf { tweak == null }
        return JSONObject().apply {
            put("schema", 1)
            put("app", ReportConfig.app)
            put("versionName", BuildConfig.VERSION_NAME)
            put("versionCode", BuildConfig.VERSION_CODE)
            put("signer", signer(context))
            put(
                "device",
                JSONObject()
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("model", Build.MODEL)
                    .put("codename", Build.DEVICE),
            )
            put("buildId", Build.ID)
            put("fingerprint", Build.FINGERPRINT)
            put("securityPatch", Build.VERSION.SECURITY_PATCH)
            put("sdk", Build.VERSION.SDK_INT)
            facts.framework?.let {
                put(
                    "framework",
                    JSONObject().put("name", it.name).put("version", it.version).put("api", it.api),
                )
            }
            put("root", if (facts.rootDenied) "denied" else "ok")
            put("tweaks", JSONObject(facts.tweaks))
            put(
                "missing",
                JSONArray(
                    facts.missing.map {
                        JSONObject()
                            .put(
                                "tweak",
                                it.tweak,
                            ).put("member", it.member)
                            .put("reason", it.reason)
                    },
                ),
            )
            if (forReport) {
                put("log", if (facts.logLines.isNullOrEmpty()) "unavailable" else "attached")
                tweak?.let { put("tweak", it) }
                missing?.let {
                    put(
                        "fingerprintKey",
                        listOfNotNull(it.tweak, it.member, Build.ID).joinToString(":"),
                    )
                }
                    ?: tweak?.let { put("fingerprintKey", "$it:${Build.ID}") }
            }
        }
    }

    fun encrypt(
        context: Context,
        preview: ReportPreview,
        message: String,
        tweak: String?,
        replyTo: String?,
    ): EncryptedReport {
        val metaBytes =
            meta(
                context,
                preview.facts,
                tweak,
                forReport = true,
            ).toString().toByteArray()
        val versions = JSONObject()
        preview.facts.packages.forEach { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }.getOrNull()?.let {
                versions.put(pkg, "${it.versionName} (${it.longVersionCode})")
            }
        }
        val plaintext =
            gzip(
                JSONObject()
                    .put("message", message)
                    .put("log", preview.log)
                    .put("versions", versions)
                    .put("replyTo", replyTo)
                    .toString()
                    .toByteArray(),
            )
        val header = byteArrayOf(ENVELOPE_VERSION) + Hpke.hex(ReportConfig.keyId)
        val info = "reportkit/v1".toByteArray() + byteArrayOf(0) + ReportConfig.app.toByteArray()
        val hpke = Hpke.seal(ReportConfig.publicKey, info, header + metaBytes, plaintext)
        return EncryptedReport(
            id = UUID.randomUUID().toString(),
            meta = metaBytes,
            envelope = header + hpke.enc + hpke.ciphertext,
            readToken = token(),
            deleteToken = token(),
        )
    }

    private fun gzip(data: ByteArray): ByteArray =
        ByteArrayOutputStream()
            .also { out ->
                GZIPOutputStream(out).use { it.write(data) }
            }.toByteArray()

    private fun token(): String = ByteArray(TOKEN_BYTES).also(random::nextBytes).toHex()

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun tokenHash(tokenHex: String): String = sha256(Hpke.hex(tokenHex)).toHex()

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
