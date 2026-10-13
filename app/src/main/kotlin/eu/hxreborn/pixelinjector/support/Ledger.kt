package eu.hxreborn.pixelinjector.support

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
) {
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
            )
    }
}

fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) {
        null
    } else {
        optString(key).ifEmpty {
            null
        }
    }

class Ledger(
    root: File,
) {
    private val dir = File(root, "reports").apply { mkdirs() }
    private val index = File(dir, "ledger.json")

    fun read(): List<SentReport> =
        runCatching {
            val array = JSONArray(index.readText())
            List(array.length()) { SentReport.from(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())

    @Synchronized
    fun write(reports: List<SentReport>) {
        val tmp = File(dir, "ledger.json.tmp")
        tmp.writeText(JSONArray(reports.map { it.toJson() }).toString())
        tmp.renameTo(index)
    }

    fun envelope(id: String): File = File(dir, "$id.bin")

    fun meta(id: String): File = File(dir, "$id.meta")

    fun deletePayload(id: String) {
        envelope(id).delete()
        meta(id).delete()
    }
}
