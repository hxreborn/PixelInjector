package eu.hxreborn.pixelinjector.support

import eu.hxreborn.pixelinjector.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class Release(
    val versionName: String,
    val versionCode: Int,
    val publishedAt: String?,
    val url: String,
    val notesNew: List<String>,
    val notesFixed: List<String>,
    val notesOther: List<String>,
    val notes: List<Note> = emptyList(),
    val asset: Asset? = null,
) {
    data class Asset(
        val name: String,
        val size: Long,
        val sha256: String?,
        val url: String,
    )

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
        private const val NOTE_ENTRIES = "noteEntries"

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

        private fun kindForSection(section: String): Note.Kind =
            when (section) {
                "Features" -> Note.Kind.NEW
                "Bug Fixes" -> Note.Kind.FIXED
                else -> Note.Kind.OTHER
            }

        private fun parseNotes(body: String): List<Note> {
            val notes = mutableListOf<Note>()
            var section: Pair<Note.Kind, String>? = null
            body.lines().forEach { line ->
                val heading = Regex("""^###\s+(.+?)\s*$""").find(line)
                if (heading != null) {
                    val title = heading.groupValues[1]
                    section = kindForSection(title) to title
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

        private fun notesByKind(entries: List<Note>): JSONObject =
            JSONObject().apply {
                Note.Kind.entries.forEach { kind ->
                    put(
                        kind.key,
                        JSONArray(entries.filter { it.kind == kind }.map { it.text }),
                    )
                }
            }

        fun apkName(versionName: String): String = "pixelinjector-v$versionName-arm64-v8a.apk"

        private fun releaseUrl(versionName: String): String =
            "https://github.com/${ReportConfig.repo}/releases/tag/v$versionName"

        fun fromGitHub(body: String): String {
            val json = JSONObject(body)
            val tag = json.getString("tag_name")
            val entries = parseNotes(json.optString("body"))
            val structuredNotes =
                entries.map { note ->
                    JSONObject()
                        .put("kind", note.kind.key)
                        .put("section", note.section)
                        .put("scope", note.scope)
                        .put("text", note.text)
                        .put("commitUrl", note.commitUrl)
                }
            val apkName = apkName(tag.removePrefix("v"))
            val asset =
                json.optJSONArray("assets")?.let { array ->
                    (0 until array.length())
                        .map(array::getJSONObject)
                        .firstOrNull { it.getString("name") == apkName }
                }
            return JSONObject()
                .put("tag", tag)
                .put("versionName", tag.removePrefix("v"))
                .put("publishedAt", json.optStringOrNull("published_at"))
                .put("url", json.optString("html_url"))
                .put("notes", notesByKind(entries))
                .put(NOTE_ENTRIES, JSONArray(structuredNotes))
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

        internal fun enrichWorkerNotes(
            body: String,
            cached: String?,
            fetchGitHub: () -> String?,
        ): String {
            val worker = runCatching { JSONObject(body) }.getOrNull() ?: return body
            val version = worker.optString("versionName")
            if (versionCodeOf(version) <= BuildConfig.VERSION_CODE ||
                worker.structuredNotes() != null
            ) {
                return body
            }
            val entries =
                matchingNotes(cached, version)
                    ?: matchingNotes(fetchGitHubNotes(fetchGitHub), version)
                    ?: return body
            return worker.put(NOTE_ENTRIES, entries).toString()
        }

        private fun fetchGitHubNotes(fetchGitHub: () -> String?): String? =
            try {
                fetchGitHub()?.let { runCatching { fromGitHub(it) }.getOrNull() }
            } catch (_: IOException) {
                null
            }

        private fun matchingNotes(
            body: String?,
            version: String,
        ): JSONArray? {
            val release = body?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
            return release.takeIf { it.optString("versionName") == version }?.structuredNotes()
        }

        private fun JSONObject.structuredNotes(): JSONArray? =
            optJSONArray(NOTE_ENTRIES)?.takeIf { it.readNotes().isNotEmpty() }

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

        private fun JSONArray.readNotes(): List<Note> =
            (0 until length()).mapNotNull { index ->
                val entry = optJSONObject(index) ?: return@mapNotNull null
                val text = entry.optStringOrNull("text") ?: return@mapNotNull null
                val section = entry.optString("section")
                val kindKey = entry.optString("kind")
                val kind =
                    Note.Kind.entries.firstOrNull { it.key == kindKey } ?: kindForSection(section)
                val commitUrl =
                    entry.optStringOrNull("commitUrl")?.takeIf { it.startsWith("https://") }
                Note(
                    kind = kind,
                    section = section,
                    scope = entry.optStringOrNull("scope"),
                    text = text,
                    commitUrl = commitUrl,
                )
            }

        fun parse(body: String): Release {
            val json = JSONObject(body)
            val versionName = json.getString("versionName")
            val notes = json.optJSONObject("notes") ?: JSONObject()
            val entries = json.optJSONArray(NOTE_ENTRIES)?.readNotes().orEmpty()
            val linesByKind =
                Note.Kind.entries.associateWith { kind ->
                    if (notes.optJSONArray(kind.key) == null) {
                        entries.filter { it.kind == kind }.map { it.text }
                    } else {
                        notes.strings(kind.key)
                    }
                }
            val structuredLines = entries.map { it.kind to it.text }.toSet()
            val legacyNotes =
                Note.Kind.entries.flatMap { kind ->
                    linesByKind
                        .getValue(kind)
                        .filterNot { kind to it in structuredLines }
                        .map { Note(kind, "", null, it, null) }
                }
            return Release(
                versionName = versionName,
                versionCode = versionCodeOf(versionName),
                publishedAt = json.optStringOrNull("publishedAt"),
                url = json.optString("url").ifEmpty { releaseUrl(versionName) },
                notesNew = linesByKind.getValue(Note.Kind.NEW),
                notesFixed = linesByKind.getValue(Note.Kind.FIXED),
                notesOther = linesByKind.getValue(Note.Kind.OTHER),
                notes = entries + legacyNotes,
                asset =
                    json.optJSONObject("asset")?.let {
                        Asset(
                            it.getString("name"),
                            it.getLong("size"),
                            it.optStringOrNull("sha256"),
                            it.getString("url"),
                        )
                    },
            )
        }
    }
}

internal fun Release?.releaseToNotify(
    postponedVersionCode: Int,
    notifiedVersionCode: Int,
): Release? =
    this?.takeIf {
        it.newer && it.versionCode > postponedVersionCode && it.versionCode > notifiedVersionCode
    }
