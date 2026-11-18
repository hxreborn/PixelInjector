package eu.hxreborn.pixelinjector.support

import eu.hxreborn.pixelinjector.BuildConfig
import org.json.JSONObject

data class Release(
    val versionName: String,
    val versionCode: Int,
    val publishedAt: String?,
    val url: String,
    val notesNew: List<String>,
    val notesFixed: List<String>,
    val notesOther: List<String>,
    val notes: List<Note> = emptyList(),
) {
    data class Note(
        val kind: Kind,
        val section: String,
        val scope: String?,
        val text: String,
        val commitUrl: String?,
    ) {
        enum class Kind(
            val key: String,
        ) {
            NEW("new"),
            FIXED("fixed"),
            OTHER("other"),
        }
    }

    val newer: Boolean get() = versionCode > BuildConfig.VERSION_CODE

    companion object {
        private const val MAJOR = 10_000
        private const val MINOR = 100

        fun versionCodeOf(versionName: String): Int {
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

        private val bulletScope = Regex("""^\*\*([^*]+):\*\*""")
        private val bulletCommit =
            Regex("""\(\[`?[0-9a-f]{7,40}`?]\((https://[^)]+/commit/[0-9a-f]{7,40})\)\)""")

        private fun parseNotes(body: String): List<Note> {
            val notes = mutableListOf<Note>()
            var section: Pair<Note.Kind, String>? = null
            body.lines().forEach { line ->
                val heading = Regex("""^###\s+(.+?)\s*$""").find(line)
                if (heading != null) {
                    val title = heading.groupValues[1]
                    val kind =
                        when (title) {
                            "Features" -> Note.Kind.NEW
                            "Bug Fixes" -> Note.Kind.FIXED
                            else -> Note.Kind.OTHER
                        }
                    section = kind to title
                    return@forEach
                }
                if (Regex("""^#{1,2}\s""").containsMatchIn(line)) section = null
                val (kind, title) = section ?: return@forEach
                val bullet =
                    Regex("""^\s*[*-]\s+(.+)$""").find(line)?.groupValues?.get(1) ?: return@forEach
                notes +=
                    Note(
                        kind = kind,
                        section = title,
                        scope = bulletScope.find(bullet)?.groupValues?.get(1),
                        text = cleanBullet(bullet),
                        commitUrl = bulletCommit.find(bullet)?.groupValues?.get(1),
                    )
            }
            return notes
        }

        private fun notes(body: String): JSONObject {
            val parsed = parseNotes(body)
            return JSONObject().apply {
                Note.Kind.entries.forEach { kind ->
                    put(
                        kind.key,
                        org.json.JSONArray(parsed.filter { it.kind == kind }.map { it.text }),
                    )
                }
            }
        }

        private fun releaseUrl(versionName: String): String =
            "https://github.com/${ReportConfig.repo}/releases/tag/v$versionName"

        fun fromGitHub(body: String): String {
            val json = JSONObject(body)
            val tag = json.getString("tag_name")
            return JSONObject()
                .put("tag", tag)
                .put("versionName", tag.removePrefix("v"))
                .put("publishedAt", json.optStringOrNull("published_at"))
                .put("url", json.optString("html_url"))
                .put("notes", notes(json.optString("body")))
                .toString()
        }

        private val changelogHeading =
            Regex(
                """^#{1,2} \[?(\d+\.\d+\.\d+)]?\S*\s*\((\d{4}-\d{2}-\d{2})\)""",
                RegexOption.MULTILINE,
            )

        fun changelog(markdown: String): List<Release> {
            val headings = changelogHeading.findAll(markdown).toList()
            return headings.mapIndexed { index, heading ->
                val (versionName, date) = heading.destructured
                val end = headings.getOrNull(index + 1)?.range?.first ?: markdown.length
                val notes = parseNotes(markdown.substring(heading.range.last + 1, end))
                Release(
                    versionName = versionName,
                    versionCode = versionCodeOf(versionName),
                    publishedAt = date,
                    url = releaseUrl(versionName),
                    notesNew = notes.filter { it.kind == Note.Kind.NEW }.map { it.text },
                    notesFixed = notes.filter { it.kind == Note.Kind.FIXED }.map { it.text },
                    notesOther = notes.filter { it.kind == Note.Kind.OTHER }.map { it.text },
                    notes = notes,
                )
            }
        }

        fun parse(body: String): Release {
            val json = JSONObject(body)
            val versionName = json.getString("versionName")
            val notes = json.optJSONObject("notes") ?: JSONObject()
            return Release(
                versionName = versionName,
                versionCode = versionCodeOf(versionName),
                publishedAt = json.optStringOrNull("publishedAt"),
                url = json.optString("url").ifEmpty { releaseUrl(versionName) },
                notesNew = notes.strings("new"),
                notesFixed = notes.strings("fixed"),
                notesOther = notes.strings("other"),
            )
        }
    }
}
