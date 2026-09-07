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
    data object SoundEditor : Destination

    @Serializable
    data object ModuleLog : Destination
}
