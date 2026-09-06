package eu.hxreborn.pixelinjector.ui.log

import eu.hxreborn.pixelinjector.BuildConfig

data class LogEntry(
    val time: String,
    val pid: Int,
    val level: Char,
    val tweak: String?,
    val process: String,
    val body: String,
)

data class ModuleLogState(
    val loading: Boolean = true,
    val denied: Boolean = false,
    val entries: List<LogEntry> = emptyList(),
) {
    val tweaks: List<String> get() = entries.mapNotNull { it.tweak }.distinct().sorted()
}

private val logcatHead = Regex("""^(?:\d\d-\d\d )?(\d\d:\d\d:\d\d)\.\d+\s+(\d+)\s+\d+\s+([VDIWEF])\s""")
private val fileHead = Regex("""(\d\d:\d\d:\d\d)\.\d+\s+\d+:\s*(\d+):\s*\d+\s+([VDIWEF])/""")
private val fileTag = Regex("""\bPixelInjector,[^]]*]\s""")
private val tweakRegex = Regex("""\btweak=(\w+)""")

private fun shortProcess(name: String): String =
    when (name) {
        "system_server" -> "system"
        BuildConfig.APPLICATION_ID -> "app"
        else -> name.substringAfterLast('.')
    }

fun parseModuleLog(
    lines: List<String>,
    processes: Map<Int, String> = emptyMap(),
): List<LogEntry> {
    val procByPid = processes.mapValuesTo(mutableMapOf()) { shortProcess(it.value) }
    return lines.mapNotNull { line ->
        val head = logcatHead.find(line) ?: fileHead.find(line) ?: return@mapNotNull null
        val (time, pid, level) = head.destructured
        val body =
            fileTag.find(line)?.let { line.substring(it.range.last + 1) }
                ?: line.substringAfter("PixelInjector").substringAfter(": ").removePrefix("${BuildConfig.APPLICATION_ID}: ")
        val id = pid.toInt()
        when {
            body.startsWith("reload ") || body.startsWith("service bound") -> {
                procByPid[id] = "app"
            }

            body.startsWith("install proc=") || body.startsWith("prefs reloaded proc=") -> {
                field(body, "proc")?.let { procByPid[id] = it.substringAfterLast('.') }
            }
        }
        LogEntry(time, id, level[0], tweakRegex.find(body)?.groupValues?.get(1), procByPid[id] ?: pid, body)
    }
}

private fun field(
    body: String,
    key: String,
): String? = Regex("""\b$key=(\S+)""").find(body)?.groupValues?.get(1)

fun deriveErrors(entries: List<LogEntry>): Map<String, String> {
    val procByPid = mutableMapOf<Int, String>()
    val errorsByProc = mutableMapOf<String, MutableMap<String, String>>()
    for ((_, pid, _, tweak1, _, body) in entries) {
        when {
            body.startsWith("install proc=") -> {
                val proc = field(body, "proc") ?: continue
                if (field(body, "version") == BuildConfig.VERSION_CODE.toString()) {
                    procByPid[pid] = proc
                    errorsByProc[proc] = mutableMapOf()
                } else {
                    procByPid.remove(pid)
                }
            }

            body.startsWith("target not found") || body.startsWith("install failed") -> {
                val proc = procByPid[pid] ?: continue
                val tweak = tweak1 ?: continue
                errorsByProc.getOrPut(proc) { mutableMapOf() }[tweak] = field(body, "build") ?: "?"
            }
        }
    }
    return errorsByProc.values.fold(mutableMapOf()) { acc, m -> acc.also { it.putAll(m) } }
}
