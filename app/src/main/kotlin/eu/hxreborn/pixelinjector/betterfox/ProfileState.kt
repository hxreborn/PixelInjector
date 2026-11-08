package eu.hxreborn.pixelinjector.betterfox

import org.json.JSONException
import org.json.JSONObject

private val PREF_LINE = Regex("^user_pref\\(\"([^\"]+)\",")
private val LINES = Charsets.ISO_8859_1

class ProfileBackupMeta(
    val taken: Long,
    val userJsSha256: String?,
    val prefs: Map<String, String?>,
) {
    fun withPrefs(added: Map<String, String?>) =
        ProfileBackupMeta(
            taken,
            userJsSha256,
            added + prefs,
        )

    fun encode(): String =
        JSONObject()
            .put("taken", taken)
            .put("userJs", userJsSha256 ?: JSONObject.NULL)
            .put(
                "prefs",
                JSONObject().also { root ->
                    prefs.forEach { (name, line) ->
                        root.put(
                            name,
                            line ?: JSONObject.NULL,
                        )
                    }
                },
            ).toString()

    companion object {
        fun decode(json: String): ProfileBackupMeta? =
            try {
                val root = JSONObject(json)
                val prefs = root.getJSONObject("prefs")
                ProfileBackupMeta(
                    taken = root.getLong("taken"),
                    userJsSha256 = if (root.isNull("userJs")) null else root.getString("userJs"),
                    prefs =
                        prefs.keys().asSequence().associateWith {
                            if (prefs.isNull(it)) null else prefs.getString(it)
                        },
                )
            } catch (e: JSONException) {
                null
            }
    }
}

fun touchedPrefLines(
    prefsJs: ByteArray,
    names: Set<String>,
): Map<String, String?> {
    val found = HashMap<String, String>()
    for (line in String(prefsJs, LINES).split("\n")) {
        val name = PREF_LINE.find(line)?.groupValues?.get(1) ?: continue
        if (name in names) found[name] = line
    }
    return names.associateWith { found[it] }
}

fun restoreTouchedPrefs(
    prefsJs: ByteArray,
    original: Map<String, String?>,
): ByteArray {
    val text = String(prefsJs, LINES)
    val endsWithNewline = text.endsWith("\n")
    val body = if (endsWithNewline) text.dropLast(1) else text
    val lines = if (body.isEmpty()) emptyList() else body.split("\n")
    val written = HashSet<String>()
    val kept =
        lines.mapNotNull { line ->
            val name = PREF_LINE.find(line)?.groupValues?.get(1)
            when {
                name == null || name !in original -> line
                original[name] != null && written.add(name) -> original[name]
                else -> null
            }
        }
    val missing =
        original
            .filter { (name, line) ->
                line != null && name !in written
            }.values
            .filterNotNull()
    val restored =
        (kept + missing).joinToString("\n") +
            if (endsWithNewline || missing.isNotEmpty()) "\n" else ""
    return restored.toByteArray(LINES)
}
