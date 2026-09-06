package eu.hxreborn.pixelinjector.ui.reload

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val KEEP = 20

val logTimestamp = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

fun formatLines(lines: List<LogLine>): String = lines.joinToString("\n") { "${logTimestamp.format(Date(it.epochMs))} ${it.text}" }

fun writeReloadLog(
    filesDir: File,
    startedAt: Long,
    lines: List<LogLine>,
) {
    val dir = File(filesDir, "reload").apply { mkdirs() }
    File(dir, "$startedAt.log").writeText(formatLines(lines) + "\n")
    dir
        .listFiles { f -> f.extension == "log" }
        ?.sortedByDescending { it.name }
        ?.drop(KEEP)
        ?.forEach { it.delete() }
}
