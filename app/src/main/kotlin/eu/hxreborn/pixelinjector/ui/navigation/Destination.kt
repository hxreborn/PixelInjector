package eu.hxreborn.pixelinjector.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Destination : NavKey {
    @Serializable
    data object Dashboard : Destination

    @Serializable
    data object Targets : Destination

    @Serializable
    data object RulesEditor : Destination

    @Serializable
    data object AppsEditor : Destination

    @Serializable
    data object PillShotApps : Destination

    @Serializable
    data object SoundEditor : Destination

    @Serializable
    data object EmptyShadeEditor : Destination

    @Serializable
    data object FingerprintIconEditor : Destination

    @Serializable
    data object NowPlayingChipEditor : Destination

    @Serializable
    data object DownloadChipEditor : Destination

    @Serializable
    data class BetterfoxEditor(
        val packageName: String,
    ) : Destination

    @Serializable
    data class BetterfoxPref(
        val pref: String,
    ) : Destination

    @Serializable
    data object BetterfoxBackup : Destination

    @Serializable
    data object BetterfoxRestore : Destination

    @Serializable
    data class BetterfoxRestoreConfirm(
        val uri: String,
    ) : Destination

    @Serializable
    data object ModuleLog : Destination

    @Serializable
    data object Settings : Destination

    @Serializable
    data class Report(
        val tweak: String? = null,
        val replyTo: String? = null,
    ) : Destination

    @Serializable
    data object Reports : Destination

    @Serializable
    data object Update : Destination

    @Serializable
    data object WhatsNew : Destination

    @Serializable
    data object Changelog : Destination

    @Serializable
    data object DateFormat : Destination

    @Serializable
    data object Licenses : Destination

    @Serializable
    data object About : Destination
}
