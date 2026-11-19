package eu.hxreborn.pixelinjector.ui.dashboard

import androidx.compose.runtime.Immutable
import eu.hxreborn.pixelinjector.prefs.AppPrefs
import eu.hxreborn.pixelinjector.prefs.BoolPref
import eu.hxreborn.pixelinjector.prefs.IntPref
import eu.hxreborn.pixelinjector.ui.navigation.Destination
import eu.hxreborn.pixelinjector.ui.viewmodel.HomeApp
import eu.hxreborn.pixelinjector.ui.viewmodel.UpdateNotice

@Immutable
data class HookedPackages(
    val packages: List<String>,
    val scoped: Boolean,
    val labels: Map<String, String> = emptyMap(),
)

@Immutable
data class DashboardState(
    val prefs: AppPrefs,
    val bound: Boolean,
    val everBound: Boolean,
    val reloadCount: Int,
    val errors: Map<String, String>,
    val errorLines: Map<String, String>,
    val homeApp: HomeApp?,
    val packages: Map<TweakGroup, HookedPackages>,
    val rootDenied: Boolean,
)

class DashboardActions(
    val onToggle: (BoolPref, Boolean) -> Unit,
    val onSlide: (IntPref, Int) -> Unit,
    val onRestart: () -> Unit,
    val onRefresh: () -> Unit,
    val onOpen: (Destination) -> Unit,
    val onHotReload: () -> Unit,
    val onRetryUpdateReload: (UpdateNotice) -> Unit,
    val onToggleTargets: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onReport: (String?) -> Unit,
    val onUpdate: () -> Unit,
    val onLater: (Int) -> Unit,
    val onResumeSupport: () -> Unit,
    val onConsumeUpdateNotice: () -> Boolean,
)
