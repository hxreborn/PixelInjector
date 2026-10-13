package eu.hxreborn.pixelinjector.ui.navigation

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.support.ReportConfig
import eu.hxreborn.pixelinjector.ui.dashboard.DashboardActions
import eu.hxreborn.pixelinjector.ui.dashboard.DashboardScreen
import eu.hxreborn.pixelinjector.ui.editor.AppsSheet
import eu.hxreborn.pixelinjector.ui.editor.EmptyShadeSheet
import eu.hxreborn.pixelinjector.ui.editor.RulesEditorScreen
import eu.hxreborn.pixelinjector.ui.editor.SoundSheet
import eu.hxreborn.pixelinjector.ui.log.ModuleLogScreen
import eu.hxreborn.pixelinjector.ui.reload.ProgressSheet
import eu.hxreborn.pixelinjector.ui.reload.TargetsSheet
import eu.hxreborn.pixelinjector.ui.support.ReportPhase
import eu.hxreborn.pixelinjector.ui.support.ReportSheet
import eu.hxreborn.pixelinjector.ui.support.ReportsSheet
import eu.hxreborn.pixelinjector.ui.support.SettingsActions
import eu.hxreborn.pixelinjector.ui.support.SettingsScreen
import eu.hxreborn.pixelinjector.ui.support.UpdateActions
import eu.hxreborn.pixelinjector.ui.support.UpdateSheet
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
    val run by viewModel.reload.collectAsStateWithLifecycle()
    val moduleLog by viewModel.moduleLog.collectAsStateWithLifecycle()
    val launcherApps by viewModel.launcherApps.collectAsStateWithLifecycle()
    val support by viewModel.supportState.collectAsStateWithLifecycle()
    val reportPreview by viewModel.reportPreview.collectAsStateWithLifecycle()
    val reportPhase by viewModel.reportPhase.collectAsStateWithLifecycle()
    val reportDrafts by viewModel.reportDrafts.collectAsStateWithLifecycle()
    val updatedNotice by viewModel.updatedNotice.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    fun push(destination: NavKey) {
        if (backStack.lastOrNull() != destination) backStack.add(destination)
    }

    fun openReport(destination: Destination.Report) {
        if (backStack.lastOrNull() is Destination.Report) return
        viewModel.openReport()
        backStack.add(destination)
    }
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
                onOpenLog = { push(Destination.ModuleLog) },
                onOpen = { push(it) },
                onHotReload = viewModel::hotReloadPending,
                onToggleTargets = {
                    if (backStack.lastOrNull() is Destination.Targets) backStack.removeLastOrNull() else backStack.add(Destination.Targets)
                },
                onOpenSettings = { push(Destination.Settings) },
                onReport = { openReport(Destination.Report(tweak = it)) },
                onUpdate = {
                    viewModel.support.resetInstall()
                    push(Destination.Update)
                },
                onLater = { viewModel.support.postponeUpdate(it) },
                onResumeSupport = viewModel::onResumeSupport,
                onUpdatedNoticeShown = viewModel::consumeUpdatedNotice,
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
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
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
                    DashboardScreen(
                        state = dashboard,
                        support = support,
                        updatedNotice = updatedNotice,
                        targetsOpen = targetsOpen,
                        actions = actions,
                    )
                    run?.let { ProgressSheet(it, onCancel = viewModel::cancelHotReload, onClose = viewModel::dismissHotReload) }
                }
                entry<Destination.Targets>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                    TargetsSheet(
                        targets = targets,
                        onDismiss = { backStack.removeLastOrNull() },
                        onHotReload = {
                            backStack.removeLastOrNull()
                            viewModel.hotReload(it)
                        },
                    )
                }
                entry<Destination.RulesEditor> {
                    RulesEditorScreen(
                        rules = prefs[Prefs.NEW_TASK_RULES],
                        onRulesChange = { viewModel.save(Prefs.NEW_TASK_RULES, it) },
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                    )
                }
                entry<Destination.AppsEditor>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                    AppsSheet(
                        title = R.string.tweak_clipboard_apps,
                        description = R.string.tweak_clipboard_desc,
                        apps = launcherApps,
                        selected = prefs[Prefs.CLIPBOARD_APPS],
                        onSelectedChange = { viewModel.save(Prefs.CLIPBOARD_APPS, it) },
                    )
                }
                entry<Destination.PillShotApps>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                    AppsSheet(
                        title = R.string.tweak_pill_shot_apps,
                        description = R.string.tweak_pill_shot_apps_desc,
                        apps = launcherApps,
                        selected = prefs[Prefs.PILL_SHOT_APPS],
                        onSelectedChange = { viewModel.save(Prefs.PILL_SHOT_APPS, it) },
                        onlySelected = prefs[Prefs.PILL_SHOT_ONLY_SELECTED],
                        onOnlySelectedChange = { viewModel.save(Prefs.PILL_SHOT_ONLY_SELECTED, it) },
                    )
                }
                entry<Destination.SoundEditor>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                    SoundSheet(
                        selected = prefs[Prefs.BIOMETRIC_SOUND],
                        onSelect = { viewModel.save(Prefs.BIOMETRIC_SOUND, it) },
                    )
                }
                entry<Destination.EmptyShadeEditor>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                    EmptyShadeSheet(
                        text = prefs[Prefs.EMPTY_SHADE_TEXT],
                        iconPath = prefs[Prefs.EMPTY_SHADE_ICON_PATH],
                        onTextChange = { viewModel.save(Prefs.EMPTY_SHADE_TEXT, it) },
                        onIconPathChange = { viewModel.save(Prefs.EMPTY_SHADE_ICON_PATH, it) },
                    )
                }
                entry<Destination.Settings> {
                    SettingsScreen(
                        state = support,
                        actions =
                            SettingsActions(
                                onCheck = { viewModel.support.checkRelease(force = true) },
                                onUpdate = {
                                    viewModel.support.resetInstall()
                                    push(Destination.Update)
                                },
                                onSendReport = {
                                    openReport(Destination.Report())
                                },
                                onReports = {
                                    push(Destination.Reports)
                                },
                                onShareHookStatus = viewModel.support::setShareHookStatus,
                                onModuleLog = {
                                    push(Destination.ModuleLog)
                                },
                                onGitHub = { uriHandler.openUri("https://github.com/${ReportConfig.repo}") },
                            ),
                        onBack = { if (backStack.lastOrNull() == Destination.Settings) backStack.removeLastOrNull() },
                    )
                }
                entry<Destination.Report>(metadata = BottomSheetSceneStrategy.bottomSheet()) { destination ->
                    LaunchedEffect(destination) { if (reportPreview == null) viewModel.openReport() }
                    ReportSheet(
                        initialTweak = destination.tweak,
                        replyTo = destination.replyTo,
                        preview = reportPreview,
                        phase = reportPhase,
                        message = reportDrafts[destination.replyTo.orEmpty()].orEmpty(),
                        onMessageChange = { viewModel.saveReportDraft(destination.replyTo, it) },
                        onSend = { message, tweak -> viewModel.sendReport(message, tweak, destination.replyTo) },
                        onTryNow = { (reportPhase as? ReportPhase.Waiting)?.report?.let(viewModel::retryReport) },
                        onViewReports = {
                            backStack.remove(destination)
                            push(Destination.Reports)
                        },
                        onClose = { backStack.remove(destination) },
                    )
                }
                entry<Destination.Reports>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                    ReportsSheet(
                        reports = support.reports,
                        onOpened = {
                            viewModel.support.refreshStatuses()
                            viewModel.support.markNotesSeen()
                        },
                        onReply = {
                            backStack.remove(Destination.Reports)
                            openReport(Destination.Report(tweak = it.tweak, replyTo = it.id))
                        },
                        onTryNow = { viewModel.support.retryWaiting() },
                        onDelete = viewModel.support::delete,
                        onUndo = viewModel.support::restore,
                    )
                }
                entry<Destination.Update>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                    val release = support.available
                    if (release == null) {
                        LaunchedEffect(Unit) { backStack.remove(Destination.Update) }
                    } else {
                        LifecycleResumeEffect(release) {
                            viewModel.support.resumeAfterInstallPermission()
                            onPauseOrDispose {}
                        }
                        UpdateSheet(
                            release = release,
                            fixed = support.fixedByUpdate,
                            step = support.install,
                            actions =
                                UpdateActions(
                                    onInstall = { viewModel.support.startInstall(release) },
                                    onOpenSettings = {
                                        context.startActivity(
                                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()),
                                        )
                                    },
                                    onGitHub = { uriHandler.openUri(release.url) },
                                    onCancel = viewModel.support::cancelInstall,
                                ),
                        )
                    }
                }
                entry<Destination.ModuleLog> {
                    ModuleLogScreen(
                        state = moduleLog,
                        onRefresh = viewModel::refreshModuleLog,
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                    )
                }
            },
    )
}
