package eu.hxreborn.pixelinjector.ui.dashboard

import androidx.compose.runtime.Immutable
import eu.hxreborn.pixelinjector.prefs.AppPrefs
import eu.hxreborn.pixelinjector.prefs.BoolPref
import eu.hxreborn.pixelinjector.prefs.IntPref
import eu.hxreborn.pixelinjector.ui.navigation.Destination
import eu.hxreborn.pixelinjector.ui.viewmodel.HomeApp

@Immutable
data class HookedPackages(
    val packages: List<String>,
    val scoped: Boolean,
)

@Immutable
data class DashboardState(
    val prefs: AppPrefs,
    val bound: Boolean,
    val everBound: Boolean,
    val reloadCount: Int,
    val errors: Map<String, String>,
    val homeApp: HomeApp?,
    val packages: Map<TweakGroup, HookedPackages>,
    val rootDenied: Boolean,
)

class DashboardActions(
    val onToggle: (BoolPref, Boolean) -> Unit,
    val onSlide: (IntPref, Int) -> Unit,
    val onRestart: () -> Unit,
    val onRefresh: () -> Unit,
    val onOpenLog: () -> Unit,
    val onOpen: (Destination) -> Unit,
    val onHotReload: () -> Unit,
    val onToggleTargets: () -> Unit,
)
