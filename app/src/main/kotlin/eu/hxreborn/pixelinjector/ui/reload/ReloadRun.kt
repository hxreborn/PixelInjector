package eu.hxreborn.pixelinjector.ui.reload

import io.github.libxposed.service.HookedTarget

enum class Tone { NEUTRAL, OK, WARN, BAD }

enum class ReloadStatus { RELOADING, SUCCEEDED, FAILED, UNSUPPORTED, IN_PROGRESS, PROCESS_DIED, THROWN }

data class LogLine(
    val epochMs: Long,
    val text: String,
    val tone: Tone = Tone.NEUTRAL,
)

data class ReloadRow(
    val target: HookedTarget,
    val status: ReloadStatus? = null,
    val message: String? = null,
    val startedAt: Long = 0L,
) {
    val pending: Boolean get() = status == ReloadStatus.RELOADING
    val finished: Boolean get() = status != null && status != ReloadStatus.RELOADING
}

data class ReloadRun(
    val rows: List<ReloadRow>,
    val lines: List<LogLine> = emptyList(),
    val done: Boolean = false,
) {
    val total: Int get() = rows.size
    val finished: Int get() = rows.count { it.finished }
    val current: Int get() = rows.indexOfFirst { it.pending }.let { if (it < 0) finished else it + 1 }
}

fun toneOf(status: ReloadStatus?): Tone =
    when (status) {
        ReloadStatus.SUCCEEDED -> Tone.OK
        ReloadStatus.PROCESS_DIED, ReloadStatus.UNSUPPORTED -> Tone.WARN
        ReloadStatus.FAILED, ReloadStatus.THROWN -> Tone.BAD
        else -> Tone.NEUTRAL
    }
