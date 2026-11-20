package eu.hxreborn.pixelinjector.support

import android.util.Log
import eu.hxreborn.pixelinjector.ModuleConstants
import eu.hxreborn.pixelinjector.ui.log.latestInstallLogs
import eu.hxreborn.pixelinjector.ui.log.parseModuleLogLine
import java.io.File

class LogArchive(
    root: File,
) {
    private val file = File(root, "module-log.txt")

    @Synchronized
    fun merge(fresh: List<String>): List<String> {
        val kept = runCatching { file.readLines() }.getOrDefault(emptyList())
        val freshLines = fresh.toSet()
        val lines = kept.filterNot(freshLines::contains) + freshLines
        val merged = retainInstallLogs(lines)
        if (merged == kept) return kept
        val pendingArchiveFile = File(file.parentFile, "${file.name}.tmp")
        val written =
            runCatching {
                pendingArchiveFile.writeText(merged.joinToString("\n", postfix = "\n"))
                pendingArchiveFile.renameTo(file)
            }.getOrDefault(false)
        if (!written) Log.w(ModuleConstants.LOG_TAG, "log archive write failed path=${file.path}")
        return merged
    }

    private fun retainInstallLogs(lines: List<String>): List<String> {
        if (lines.size <= MAX_LINES) return lines
        val diagnostics =
            lines
                .asSequence()
                .filter {
                    "install proc=" in it || "target not found" in it ||
                        "install failed" in it
                }.mapNotNull(::parseModuleLogLine)
        val installs = latestInstallLogs(diagnostics).values
        val retained =
            buildSet {
                installs.forEach { add(it.start.raw) }
                installs.forEach { install -> install.failures.values.forEach { add(it.raw) } }
            }
        val remaining = (MAX_LINES - retained.size).coerceAtLeast(0)
        val recent =
            lines
                .asReversed()
                .asSequence()
                .filterNot(retained::contains)
                .take(remaining)
        val kept = (retained.asSequence() + recent).take(MAX_LINES).toSet()
        return lines.filter(kept::contains)
    }

    companion object {
        private const val MAX_LINES = 3000
    }
}
