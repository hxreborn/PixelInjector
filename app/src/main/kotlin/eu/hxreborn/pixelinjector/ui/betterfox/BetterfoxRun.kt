package eu.hxreborn.pixelinjector.ui.betterfox

import android.net.Uri
import eu.hxreborn.pixelinjector.betterfox.BetterfoxBackup
import eu.hxreborn.pixelinjector.betterfox.BetterfoxList
import eu.hxreborn.pixelinjector.betterfox.BetterfoxOverride
import eu.hxreborn.pixelinjector.support.BetterfoxUpdateState
import eu.hxreborn.pixelinjector.support.Browser
import eu.hxreborn.pixelinjector.ui.reload.LogLine
import eu.hxreborn.pixelinjector.ui.reload.ReloadStatus

enum class RunKind { BACKUP, RESTORE }

enum class StepIcon { GROUP, OVERRIDES, FILE, RESTART }

data class RunStep(
    val title: String,
    val detail: String,
    val icon: StepIcon,
    val status: ReloadStatus? = null,
)

data class BetterfoxRun(
    val kind: RunKind,
    val steps: List<RunStep>,
    val lines: List<LogLine> = emptyList(),
    val done: Boolean = false,
    val failed: Boolean = false,
) {
    val total: Int get() = steps.size
    val finished: Int get() = steps.count { it.status != null && it.status != ReloadStatus.RELOADING }
    val current: Int get() = steps.indexOfFirst { it.status == ReloadStatus.RELOADING }.let { if (it < 0) finished else it + 1 }
}

class BackupEntry(
    val uri: Uri,
    val name: String,
    val backup: BetterfoxBackup?,
)

sealed interface BetterfoxMessage {
    data class Saved(
        val folder: String,
        val uri: Uri,
        val name: String,
    ) : BetterfoxMessage

    data class Restored(
        val overrides: Int,
        val browser: String?,
    ) : BetterfoxMessage

    data class Update(
        val state: BetterfoxUpdateState,
    ) : BetterfoxMessage

    data class Cleared(
        val count: Int,
    ) : BetterfoxMessage

    data object Reset : BetterfoxMessage
}

data class BetterfoxState(
    val list: BetterfoxList? = null,
    val enabled: Boolean = false,
    val offGroups: Set<String> = emptySet(),
    val overrides: Map<String, BetterfoxOverride> = emptyMap(),
    val browser: Browser? = null,
    val browsers: List<Browser> = emptyList(),
    val newerGecko: Browser? = null,
    val update: BetterfoxUpdateState = BetterfoxUpdateState.Idle,
    val folder: String = "",
    val backups: List<BackupEntry> = emptyList(),
    val flash: Flash = Flash(),
)

data class Flash(
    val token: Long = 0,
    val prefs: List<String> = emptyList(),
)

data class OverrideChange(
    val pref: String,
    val from: BetterfoxOverride?,
    val to: BetterfoxOverride?,
)
