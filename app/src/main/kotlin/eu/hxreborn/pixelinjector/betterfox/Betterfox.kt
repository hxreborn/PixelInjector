package eu.hxreborn.pixelinjector.betterfox

import org.json.JSONException
import org.json.JSONObject

const val BETTERFOX_SCHEMA = 2
const val BETTERFOX_ASSET = "betterfox.json"
const val BETTERFOX_MAX_BYTES = 256 * 1024
const val BETTERFOX_REPO = "https://github.com/yokoffing/Betterfox"

private const val MAX_PREFS = 512
private const val MAX_TEXT = 160
private const val MAX_STRING_VALUE = 256
private const val UNSET = "unset"
private const val MAX_ABOUT = 1200
private const val MAX_LINKS = 10
private val ABOUT_TAG = Regex("[A-Z][A-Z-]{1,20}")
private val PREF_NAME = Regex("[A-Za-z0-9_.\\-]{1,128}")
private val SOURCE_FILE = Regex("[A-Za-z]{1,32}\\.js")
private val VERSION = Regex("\\d{1,4}(\\.\\d{1,4}){0,3}")

class BetterfoxPref(
    val pref: String,
    val value: Any,
    val group: String,
    val note: String,
    val stock: Any?,
    val file: String,
    val line: Int,
    val about: BetterfoxAbout = BetterfoxAbout(),
) {
    val isStock: Boolean get() = stock == value
}

class BetterfoxTag(
    val tag: String,
    val text: String,
)

class BetterfoxAbout(
    val text: String? = null,
    val tags: List<BetterfoxTag> = emptyList(),
    val links: List<String> = emptyList(),
    val inline: String? = null,
) {
    val isEmpty: Boolean get() = text == null && tags.isEmpty() && links.isEmpty() && inline == null
}

class BetterfoxList(
    val revision: Int,
    val betterfox: String,
    val gecko: String,
    val prefs: List<BetterfoxPref>,
) {
    val groups: List<String> = prefs.map { it.group }.distinct()

    fun pref(name: String): BetterfoxPref? = prefs.firstOrNull { it.pref == name }

    fun sourceUrl(entry: BetterfoxPref): String =
        "$BETTERFOX_REPO/blob/$betterfox/${entry.file}#L${entry.line}"

    val treeUrl: String get() = "$BETTERFOX_REPO/tree/$betterfox"

    companion object {
        fun parse(json: String): BetterfoxList {
            require(json.length <= BETTERFOX_MAX_BYTES) { "list size" }
            try {
                val root = JSONObject(json)
                require(root.optInt("schema", -1) == BETTERFOX_SCHEMA) { "list schema" }
                val revision = root.getInt("revision")
                require(revision >= 1) { "list revision" }
                val gecko = root.getString("gecko")
                require(VERSION.matches(gecko)) { "list gecko" }
                val array = root.getJSONArray("prefs")
                require(array.length() in 1..MAX_PREFS) { "pref count" }
                val seen = HashSet<String>()
                val prefs =
                    List(array.length()) { index ->
                        parsePref(array.getJSONObject(index)).also {
                            require(seen.add(it.pref)) { "duplicate pref ${it.pref}" }
                        }
                    }
                val allowedPrefs = prefs.filterNot { isExcludedSecurityPref(it.pref) }
                require(allowedPrefs.isNotEmpty()) { "pref count after security exclusions" }
                return BetterfoxList(revision, requiredText(root, "betterfox"), gecko, allowedPrefs)
            } catch (e: JSONException) {
                throw IllegalArgumentException("list shape", e)
            }
        }

        fun parseOrNull(json: String): BetterfoxList? =
            if (json.isEmpty()) {
                null
            } else {
                try {
                    parse(json)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }

        private fun parsePref(entry: JSONObject): BetterfoxPref {
            val pref = entry.getString("pref")
            require(PREF_NAME.matches(pref)) { "pref name $pref" }
            val value = entry.get("value")
            require(isPrefValue(value)) { "pref value of $pref" }
            val stock = entry.get("stock").takeUnless { it == JSONObject.NULL }
            require(stock == null || isPrefValue(stock)) { "stock value of $pref" }
            val file = entry.getString("file")
            require(SOURCE_FILE.matches(file)) { "source file of $pref" }
            val line = entry.getInt("line")
            require(line >= 1) { "source line of $pref" }
            return BetterfoxPref(
                pref,
                value,
                requiredText(entry, "group"),
                requiredText(entry, "note"),
                stock,
                file,
                line,
                entry.optJSONObject("about")?.let { parseAbout(pref, it) } ?: BetterfoxAbout(),
            )
        }

        private fun parseAbout(
            pref: String,
            about: JSONObject,
        ): BetterfoxAbout {
            fun text(value: String): String {
                require(value.isNotBlank() && value.length <= MAX_ABOUT) { "about text of $pref" }
                return value
            }
            val tags = about.optJSONArray("tags")
            val links = about.optJSONArray("links")
            require((links?.length() ?: 0) <= MAX_LINKS) { "about links of $pref" }
            return BetterfoxAbout(
                text = about.optString("text").takeIf { it.isNotEmpty() }?.let(::text),
                tags =
                    List(tags?.length() ?: 0) { index ->
                        val tag = tags!!.getJSONObject(index)
                        BetterfoxTag(
                            tag.getString("tag").also {
                                require(ABOUT_TAG.matches(it)) { "about tag of $pref" }
                            },
                            text(tag.getString("text")),
                        )
                    },
                links =
                    List(links?.length() ?: 0) { index ->
                        links!!.getString(index).also {
                            require(
                                it.startsWith("https://") || it.startsWith("http://"),
                            ) { "about link of $pref" }
                        }
                    },
                inline = about.optString("inline").takeIf { it.isNotEmpty() }?.let(::text),
            )
        }

        private fun requiredText(
            entry: JSONObject,
            name: String,
        ): String {
            val value = entry.getString(name)
            require(value.isNotEmpty() && value.length <= MAX_TEXT) { "$name text" }
            return value
        }
    }
}

private fun isExcludedSecurityPref(pref: String): Boolean =
    pref.startsWith("browser.safebrowsing.") ||
        pref == "security.OCSP.enabled" ||
        pref == "security.csp.reporting.enabled"

fun isPrefValue(value: Any?): Boolean =
    value is Boolean || value is Int || (value is String && value.length <= MAX_STRING_VALUE)

fun formatPrefValue(value: Any?): String =
    when (value) {
        null -> UNSET
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }

fun parsePrefValue(text: String): Any? {
    val raw = text.trim()
    return when {
        raw == "true" -> {
            true
        }

        raw == "false" -> {
            false
        }

        raw.length >= 2 && raw.first() == '"' && raw.last() == '"' -> {
            try {
                JSONObject("{\"v\":$raw}").getString("v").takeIf { isPrefValue(it) }
            } catch (e: JSONException) {
                null
            }
        }

        else -> {
            raw.toIntOrNull()
        }
    }
}

fun geckoMajor(version: String): Int? = version.substringBefore('.').toIntOrNull()

fun chooseList(
    bundled: BetterfoxList,
    fetched: BetterfoxList?,
): BetterfoxList = if (fetched != null && fetched.revision > bundled.revision) fetched else bundled

sealed interface BetterfoxOverride {
    data object Stock : BetterfoxOverride

    data class Custom(
        val value: Any,
    ) : BetterfoxOverride
}

fun decodeOverrides(json: String): Map<String, BetterfoxOverride> {
    if (json.isEmpty()) return emptyMap()
    return try {
        val root = JSONObject(json)
        buildMap {
            for (pref in root.keys()) {
                val raw = root.get(pref)
                when {
                    raw == JSONObject.NULL -> put(pref, BetterfoxOverride.Stock)
                    isPrefValue(raw) -> put(pref, BetterfoxOverride.Custom(raw))
                }
            }
        }
    } catch (e: JSONException) {
        emptyMap()
    }
}

fun encodeOverrides(overrides: Map<String, BetterfoxOverride>): String =
    if (overrides.isEmpty()) {
        ""
    } else {
        JSONObject()
            .also { root ->
                for ((pref, override) in overrides) {
                    root.put(
                        pref,
                        when (override) {
                            BetterfoxOverride.Stock -> JSONObject.NULL
                            is BetterfoxOverride.Custom -> override.value
                        },
                    )
                }
            }.toString()
    }

fun isUsable(
    entry: BetterfoxPref?,
    override: BetterfoxOverride,
): Boolean =
    entry != null &&
        (override !is BetterfoxOverride.Custom || override.value::class == entry.value::class)

fun resolveBetterfox(
    list: BetterfoxList,
    disabledGroups: Set<String>,
    overrides: Map<String, BetterfoxOverride>,
): Map<String, Any> =
    buildMap {
        for (entry in list.prefs) {
            if (entry.group in disabledGroups) continue
            when (val override = overrides[entry.pref]) {
                null -> {
                    put(entry.pref, entry.value)
                }

                BetterfoxOverride.Stock -> {
                    Unit
                }

                is BetterfoxOverride.Custom -> {
                    if (isUsable(entry, override)) put(entry.pref, override.value)
                }
            }
        }
    }
