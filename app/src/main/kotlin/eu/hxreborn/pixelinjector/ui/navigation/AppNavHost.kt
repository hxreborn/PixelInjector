package eu.hxreborn.pixelinjector.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.ui.dashboard.DashboardActions
import eu.hxreborn.pixelinjector.ui.dashboard.DashboardScreen
import eu.hxreborn.pixelinjector.ui.editor.AppsSheet
import eu.hxreborn.pixelinjector.ui.editor.RulesEditorScreen
import eu.hxreborn.pixelinjector.ui.editor.SoundSheet
import eu.hxreborn.pixelinjector.ui.log.ModuleLogScreen
import eu.hxreborn.pixelinjector.ui.reload.ProgressSheet
import eu.hxreborn.pixelinjector.ui.reload.TargetsSheet
import eu.hxreborn.pixelinjector.ui.viewmodel.MainViewModel

private val SlideDistance = 30.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppNavHost(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(Destination.Dashboard)
    val sheetStrategy = remember { BottomSheetSceneStrategy<NavKey>() }
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val targets by viewModel.reloadTargets.collectAsStateWithLifecycle()
    val apps by viewModel.appTargets.collectAsStateWithLifecycle()
    val run by viewModel.reload.collectAsStateWithLifecycle()
    val moduleLog by viewModel.moduleLog.collectAsStateWithLifecycle()
    val launcherApps by viewModel.launcherApps.collectAsStateWithLifecycle()
    val targetsOpen = backStack.lastOrNull() is Destination.Targets
    val actions =
        remember(viewModel, backStack) {
            DashboardActions(
                onToggle = viewModel::save,
                onSlide = viewModel::save,
                onRestart = viewModel::restart,
                onRefresh = {
                    viewModel.refreshTargets()
                    viewModel.refreshModuleLog()
                    viewModel.refreshLauncherApps()
                },
                onOpenLog = { backStack.add(Destination.ModuleLog) },
                onOpen = { backStack.add(it) },
                onHotReload = viewModel::hotReloadPending,
                onToggleTargets = {
                    if (backStack.lastOrNull() is Destination.Targets) backStack.removeLastOrNull() else backStack.add(Destination.Targets)
                },
            )
        }
    val slideDistance = with(LocalDensity.current) { SlideDistance.roundToPx() }
    val enterSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val exitSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val enterEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val exitEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategies = listOf(sheetStrategy),
        transitionSpec = {
            (slideInHorizontally(enterSpatialSpec) { slideDistance } + fadeIn(enterEffectsSpec)) togetherWith
                (slideOutHorizontally(exitSpatialSpec) { -slideDistance } + fadeOut(exitEffectsSpec))
        },
        popTransitionSpec = {
            (slideInHorizontally(enterSpatialSpec) { -slideDistance } + fadeIn(enterEffectsSpec)) togetherWith
                (slideOutHorizontally(exitSpatialSpec) { slideDistance } + fadeOut(exitEffectsSpec))
        },
        predictivePopTransitionSpec = {
            (slideInHorizontally(enterSpatialSpec) { -slideDistance } + fadeIn(enterEffectsSpec)) togetherWith
                (slideOutHorizontally(exitSpatialSpec) { slideDistance } + fadeOut(exitEffectsSpec))
        },
        entryProvider =
            entryProvider<NavKey> {
                entry<Destination.Dashboard> {
                    DashboardScreen(state = dashboard, targetsOpen = targetsOpen, actions = actions)
                    run?.let { ProgressSheet(it, onCancel = viewModel::cancelHotReload, onClose = viewModel::dismissHotReload) }
                }
                entry<Destination.Targets>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                    TargetsSheet(
                        targets = targets,
                        apps = apps,
                        onDismiss = { backStack.removeLastOrNull() },
                        onHotReload = {
                            backStack.removeLastOrNull()
                            viewModel.hotReload(it)
                        },
                        onRestart = viewModel::restartApps,
                    )
                }
                entry<Destination.RulesEditor> {
                    RulesEditorScreen(
                        rules = prefs[Prefs.NEW_TASK_RULES],
                        onRulesChange = { viewModel.save(Prefs.NEW_TASK_RULES, it) },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<Destination.AppsEditor>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                    AppsSheet(
                        apps = launcherApps,
                        allowed = prefs[Prefs.CLIPBOARD_APPS],
                        onAllowedChange = { viewModel.save(Prefs.CLIPBOARD_APPS, it) },
                    )
                }
                entry<Destination.SoundEditor>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                    SoundSheet(
                        selected = prefs[Prefs.BIOMETRIC_SOUND],
                        onSelect = { viewModel.save(Prefs.BIOMETRIC_SOUND, it) },
                    )
                }
                entry<Destination.ModuleLog> {
                    ModuleLogScreen(
                        state = moduleLog,
                        onRefresh = viewModel::refreshModuleLog,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            },
    )
}
