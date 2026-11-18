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
import java.util.Base64
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
    val romCodes: Map<String, RomCode> = emptyMap(),
    val unreadableRomCodes: Map<String, String> = emptyMap(),
) {
    fun romCodeFor(tweak: String?): RomCode? =
        if (tweak == null) romCodes.values.firstOrNull() else romCodes[tweak]

    fun unreadableRomCodeFor(tweak: String?): String? =
        when {
            romCodeFor(tweak) != null -> null
            tweak == null -> unreadableRomCodes.values.firstOrNull()
            else -> unreadableRomCodes[tweak]
        }
}

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
    val noteKey: Hpke.NoteKey,
    val romCode: RomCode?,
)

object ReportBuilder {
    private const val MAX_LOG_LINES = 4000
    private const val TOKEN_BYTES = 32
    const val MAX_ATTACHMENT_BYTES = 25 shl 20
    private const val ENVELOPE_VERSION: Byte = 1
    private const val SIGNER_BYTES = 4
    private val random = SecureRandom()

    fun preview(facts: ReportFacts): ReportPreview {
        val lines = facts.logLines?.takeLast(MAX_LOG_LINES).orEmpty()
        val redacted = Redactor.redact(lines.joinToString("\n"))
        return ReportPreview(facts, redacted.text, lines.size, redacted.hidden)
    }

    fun installedSigner(context: Context): String? =
        runCatching {
            val packageInfo =
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
            val cert =
                packageInfo.signingInfo
                    ?.apkContentsSigners
                    ?.firstOrNull()
                    ?.toByteArray() ?: return null
            sha256(cert).toHex()
        }.getOrNull()

    fun signer(context: Context): String? = installedSigner(context)?.take(SIGNER_BYTES * 2)

    val noteInfo: ByteArray =
        "reportkit/v1/note".toByteArray() + byteArrayOf(0) + ReportConfig.app.toByteArray()

    private val attachmentInfo: ByteArray =
        "reportkit/v1/attachment".toByteArray() + byteArrayOf(0) + ReportConfig.app.toByteArray()

    private fun envelopeHeader(): ByteArray =
        byteArrayOf(ENVELOPE_VERSION) + Hpke.decodeHex(ReportConfig.keyId)

    fun sealRomCode(code: RomCode): ByteArray {
        val header = envelopeHeader()
        val hpke =
            Hpke.seal(
                ReportConfig.publicKey,
                attachmentInfo,
                header + code.sha256.toByteArray(),
                code.zip(),
            )
        return header + hpke.enc + hpke.ciphertext
    }

    fun meta(
        context: Context,
        facts: ReportFacts,
        tweak: String?,
        notePublicKey: ByteArray? = null,
        romCode: RomCode? = null,
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
            notePublicKey?.let {
                put(
                    "noteKey",
                    Base64.getUrlEncoder().withoutPadding().encodeToString(it),
                )
            }
            put("log", if (facts.logLines.isNullOrEmpty()) "unavailable" else "attached")
            tweak?.let { put("tweak", it) }
            romCode?.let { put("attachment", it.toJson()) }
            missing?.let {
                put(
                    "fingerprintKey",
                    listOfNotNull(it.tweak, it.member, Build.ID).joinToString(":"),
                )
            }
                ?: tweak?.let { put("fingerprintKey", "$it:${Build.ID}") }
        }
    }

    fun encrypt(
        context: Context,
        preview: ReportPreview,
        message: String,
        tweak: String?,
        replyTo: String?,
        noteKey: Hpke.NoteKey,
    ): EncryptedReport {
        val romCode = preview.facts.romCodeFor(tweak).takeIf { replyTo == null }
        val metaBytes =
            meta(
                context,
                preview.facts,
                tweak,
                notePublicKey = noteKey.publicRaw,
                romCode = romCode,
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
        val header = envelopeHeader()
        val info = "reportkit/v1".toByteArray() + byteArrayOf(0) + ReportConfig.app.toByteArray()
        val hpke = Hpke.seal(ReportConfig.publicKey, info, header + metaBytes, plaintext)
        return EncryptedReport(
            id = UUID.randomUUID().toString(),
            meta = metaBytes,
            envelope = header + hpke.enc + hpke.ciphertext,
            readToken = token(),
            deleteToken = token(),
            noteKey = noteKey,
            romCode = romCode,
        )
    }

    private fun gzip(data: ByteArray): ByteArray =
        ByteArrayOutputStream()
            .also { out ->
                GZIPOutputStream(out).use { it.write(data) }
            }.toByteArray()

    private fun token(): String = ByteArray(TOKEN_BYTES).also(random::nextBytes).toHex()

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun tokenHash(tokenHex: String): String = sha256(Hpke.decodeHex(tokenHex)).toHex()

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
