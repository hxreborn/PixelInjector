package eu.hxreborn.pixelinjector.support

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64

enum class ReportStatus(
    val wire: String,
) {
    WAITING("waiting"),
    RECEIVED("received"),
    INVESTIGATING("investigating"),
    NEED_INFO("need_info"),
    FIXED("fixed"),
    REJECTED("rejected"),
    ;

    companion object {
        fun of(wire: String): ReportStatus? = entries.firstOrNull { it.wire == wire }
    }
}

data class ReportEvent(
    val status: ReportStatus,
    val fixedIn: String?,
    val note: String?,
    val at: Long,
    val issue: Int? = null,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put(
                "status",
                status.wire,
            ).put("fixedIn", fixedIn)
            .put("note", note)
            .put("at", at)
            .put("issue", issue)

    companion object {
        fun from(json: JSONObject): ReportEvent? =
            ReportStatus.of(json.optString("status"))?.let {
                ReportEvent(
                    it,
                    json.optStringOrNull("fixedIn"),
                    json.optStringOrNull("note"),
                    json.optLong("at"),
                    json.optIntOrNull("issue"),
                )
            }

        fun list(
            array: JSONArray?,
            decodeNote: (JSONObject) -> String? = { it.optStringOrNull("note") },
        ): List<ReportEvent> =
            array
                ?.let { a ->
                    (0 until a.length()).mapNotNull { index ->
                        val json = a.optJSONObject(index) ?: return@mapNotNull null
                        from(json)?.copy(note = decodeNote(json))
                    }
                }.orEmpty()
    }
}

data class SentReport(
    val id: String,
    val short: String?,
    val tweak: String?,
    val sentAt: Long,
    val status: ReportStatus,
    val fixedIn: String?,
    val note: String?,
    val readToken: String,
    val deleteToken: String,
    val replyTo: String?,
    val build: String,
    val error: String? = null,
    val noteSeen: Boolean = true,
    val message: String? = null,
    val events: List<ReportEvent> = emptyList(),
    val noteKeyPrivate: String? = null,
    val noteKeyPublic: String? = null,
    val issue: Int? = null,
    val fixLine: String? = null,
    val romCode: RomCode? = null,
    val romCodeUploaded: Boolean = false,
    val romCodeFailed: String? = null,
) {
    val noteKey: Hpke.NoteKey? by lazy {
        if (noteKeyPrivate == null || noteKeyPublic == null) {
            null
        } else {
            runCatching {
                Hpke.NoteKey(
                    KeyWrap.unwrap(Base64.getDecoder().decode(noteKeyPrivate)),
                    Base64.getDecoder().decode(noteKeyPublic),
                )
            }.getOrNull()
        }
    }

    val lastActivity: Long get() = maxOf(sentAt, events.maxOfOrNull { it.at } ?: 0L)

    fun decodeNote(json: JSONObject): String? {
        val sealed = json.optStringOrNull("noteSealed") ?: return json.optStringOrNull("note")
        val key = noteKey ?: return null
        return runCatching {
            String(
                Hpke.open(
                    key,
                    ReportBuilder.noteInfo,
                    id.toByteArray(),
                    Base64.getDecoder().decode(sealed),
                ),
            )
        }.getOrNull()
    }

    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("short", short)
            .put("tweak", tweak)
            .put("sentAt", sentAt)
            .put("status", status.wire)
            .put("fixedIn", fixedIn)
            .put("note", note)
            .put("readToken", readToken)
            .put("deleteToken", deleteToken)
            .put("replyTo", replyTo)
            .put("build", build)
            .put("error", error)
            .put("noteSeen", noteSeen)
            .put("message", message)
            .put("events", JSONArray(events.map { it.toJson() }))
            .put("noteKeyPrivate", noteKeyPrivate)
            .put("noteKeyPublic", noteKeyPublic)
            .put("issue", issue)
            .put("fixLine", fixLine)
            .put("romCode", romCode?.toStoredJson())
            .put("romCodeUploaded", romCodeUploaded)
            .put("romCodeFailed", romCodeFailed)

    companion object {
        fun from(json: JSONObject): SentReport =
            SentReport(
                id = json.getString("id"),
                short = json.optStringOrNull("short"),
                tweak = json.optStringOrNull("tweak"),
                sentAt = json.getLong("sentAt"),
                status = ReportStatus.of(json.getString("status")) ?: ReportStatus.RECEIVED,
                fixedIn = json.optStringOrNull("fixedIn"),
                note = json.optStringOrNull("note"),
                readToken = json.getString("readToken"),
                deleteToken = json.getString("deleteToken"),
                replyTo = json.optStringOrNull("replyTo"),
                build = json.optString("build"),
                error = json.optStringOrNull("error"),
                noteSeen = json.optBoolean("noteSeen", true),
                message = json.optStringOrNull("message"),
                events = ReportEvent.list(json.optJSONArray("events")),
                noteKeyPrivate = json.optStringOrNull("noteKeyPrivate"),
                noteKeyPublic = json.optStringOrNull("noteKeyPublic"),
                issue = json.optIntOrNull("issue"),
                fixLine = json.optStringOrNull("fixLine"),
                romCode = RomCode.from(json.optJSONObject("romCode")),
                romCodeUploaded = json.optBoolean("romCodeUploaded"),
                romCodeFailed = json.optStringOrNull("romCodeFailed"),
            )
    }
}

fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null

fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).ifEmpty { null }

class ReportStore(
    root: File,
) {
    private val reportsDirectory = File(root, "reports").apply { mkdirs() }
    private val recordsFile = File(reportsDirectory, "ledger.json")

    fun read(): List<SentReport> =
        runCatching {
            val array = JSONArray(recordsFile.readText())
            List(array.length()) { SentReport.from(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())

    @Synchronized
    fun write(reports: List<SentReport>) {
        val pendingRecordsFile = File(reportsDirectory, "ledger.json.tmp")
        pendingRecordsFile.writeText(JSONArray(reports.map { it.toJson() }).toString())
        pendingRecordsFile.renameTo(recordsFile)
    }

    fun envelopeFile(id: String): File = File(reportsDirectory, "$id.bin")

    fun metadataFile(id: String): File = File(reportsDirectory, "$id.meta")

    fun deletePayload(id: String) {
        envelopeFile(id).delete()
        metadataFile(id).delete()
    }
}
