package eu.hxreborn.pixelinjector.ui.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
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
import eu.hxreborn.pixelinjector.ui.betterfox.BackupEntry
import eu.hxreborn.pixelinjector.ui.betterfox.BetterfoxActions
import eu.hxreborn.pixelinjector.ui.betterfox.BetterfoxBackupSheet
import eu.hxreborn.pixelinjector.ui.betterfox.BetterfoxPrefSheet
import eu.hxreborn.pixelinjector.ui.betterfox.BetterfoxRestoreConfirmSheet
import eu.hxreborn.pixelinjector.ui.betterfox.BetterfoxRestoreSheet
import eu.hxreborn.pixelinjector.ui.betterfox.BetterfoxRunSheet
import eu.hxreborn.pixelinjector.ui.betterfox.BetterfoxScreen
import eu.hxreborn.pixelinjector.ui.betterfox.shareIntent
import eu.hxreborn.pixelinjector.ui.dashboard.DashboardActions
import eu.hxreborn.pixelinjector.ui.dashboard.DashboardScreen
import eu.hxreborn.pixelinjector.ui.editor.AppsSheet
import eu.hxreborn.pixelinjector.ui.editor.DownloadChipSheet
import eu.hxreborn.pixelinjector.ui.editor.EmptyShadeSheet
import eu.hxreborn.pixelinjector.ui.editor.FingerprintIconSheet
import eu.hxreborn.pixelinjector.ui.editor.NowPlayingChipSheet
import eu.hxreborn.pixelinjector.ui.editor.RulesEditorScreen
import eu.hxreborn.pixelinjector.ui.editor.SoundSheet
import eu.hxreborn.pixelinjector.ui.log.ModuleLogScreen
import eu.hxreborn.pixelinjector.ui.reload.ProgressSheet
import eu.hxreborn.pixelinjector.ui.reload.TargetsSheet
import eu.hxreborn.pixelinjector.ui.support.AboutActions
import eu.hxreborn.pixelinjector.ui.support.AboutScreen
import eu.hxreborn.pixelinjector.ui.support.ChangelogScreen
import eu.hxreborn.pixelinjector.ui.support.DateFormatSheet
import eu.hxreborn.pixelinjector.ui.support.LicensesScreen
import eu.hxreborn.pixelinjector.ui.support.ReportPhase
import eu.hxreborn.pixelinjector.ui.support.ReportSheet
import eu.hxreborn.pixelinjector.ui.support.ReportsSheet
import eu.hxreborn.pixelinjector.ui.support.SettingsActions
import eu.hxreborn.pixelinjector.ui.support.SettingsScreen
import eu.hxreborn.pixelinjector.ui.support.UpdateActions
import eu.hxreborn.pixelinjector.ui.support.UpdateSheet
import eu.hxreborn.pixelinjector.ui.support.WhatsNewSheet
import eu.hxreborn.pixelinjector.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SlideDistance = 30.dp
private const val ENTERING_SCREEN_ABOVE = 1f
private const val LEAVING_SCREEN_BELOW = -1f
private const val PREDICTIVE_INITIAL_SCALE = 0.95f
private const val PREDICTIVE_EXIT_SCALE = 0.9f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppNavHost(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(Destination.Dashboard)
    val testNavigation by viewModel.testNavigation.collectAsStateWithLifecycle()
    LaunchedEffect(testNavigation) {
        val navigation = testNavigation ?: return@LaunchedEffect
        backStack.retainAll(listOf(Destination.Dashboard))
        backStack.addAll(navigation.destinations)
    }
    val sheetStrategy = remember { BottomSheetSceneStrategy<NavKey>() }
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val hideUnavailable by viewModel.hideUnavailable.collectAsStateWithLifecycle()
    val showPackages by viewModel.showPackages.collectAsStateWithLifecycle()
    val dateStyle by viewModel.dateStyle.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val targets by viewModel.reloadTargets.collectAsStateWithLifecycle()
    val run by viewModel.reload.collectAsStateWithLifecycle()
    val moduleLog by viewModel.moduleLog.collectAsStateWithLifecycle()
    val launcherApps by viewModel.launcherApps.collectAsStateWithLifecycle()
    val betterfox by viewModel.betterfox.screen.collectAsStateWithLifecycle()
    val betterfoxRun by viewModel.betterfox.run.collectAsStateWithLifecycle()
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
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val scaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    val enterFromEnd = slideInHorizontally(spatialSpec) { slideDistance } + fadeIn(effectsSpec)
    val exitToStart = slideOutHorizontally(spatialSpec) { -slideDistance }
    val enterFromStart = slideInHorizontally(spatialSpec) { -slideDistance }
    val exitToEnd = slideOutHorizontally(spatialSpec) { slideDistance } + fadeOut(effectsSpec)
    val forward = (enterFromEnd togetherWith exitToStart).apply { targetContentZIndex = ENTERING_SCREEN_ABOVE }
    val back = (enterFromStart togetherWith exitToEnd).apply { targetContentZIndex = LEAVING_SCREEN_BELOW }
    val predictiveBack =
        (
            (enterFromStart + scaleIn(scaleSpec, PREDICTIVE_INITIAL_SCALE)) togetherWith
                (exitToEnd + scaleOut(scaleSpec, PREDICTIVE_EXIT_SCALE))
        ).apply { targetContentZIndex = LEAVING_SCREEN_BELOW }

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        sceneStrategies = listOf(sheetStrategy),
        transitionSpec = { forward },
        popTransitionSpec = { back },
        predictivePopTransitionSpec = { predictiveBack },
        entryProvider =
            entryProvider<NavKey> {
                entry<Destination.Dashboard> {
                    DashboardScreen(
                        state = dashboard,
                        support = support,
                        updatedNotice = updatedNotice,
                        targetsOpen = targetsOpen,
                        hideUnavailable = hideUnavailable,
                        showPackages = showPackages,
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
                entry<Destination.FingerprintIconEditor>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                    FingerprintIconSheet(
                        style = prefs[Prefs.FINGERPRINT_ICON_STYLE],
                        hideCircle = prefs[Prefs.FINGERPRINT_HIDE_CIRCLE],
                        tint = prefs[Prefs.FINGERPRINT_TINT],
                        onStyleChange = { viewModel.save(Prefs.FINGERPRINT_ICON_STYLE, it) },
                        onHideCircleChange = { viewModel.save(Prefs.FINGERPRINT_HIDE_CIRCLE, it) },
                        onTintChange = { viewModel.save(Prefs.FINGERPRINT_TINT, it) },
                        onImagePicked = viewModel::saveFingerprintImage,
                    )
                }
                entry<Destination.NowPlayingChipEditor>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                    NowPlayingChipSheet(
                        side = prefs[Prefs.NOW_PLAYING_CHIP_SIDE],
                        width = prefs[Prefs.NOW_PLAYING_CHIP_WIDTH],
                        hideShade = prefs[Prefs.NOW_PLAYING_CHIP_HIDE_SHADE],
                        textColor = prefs[Prefs.NOW_PLAYING_CHIP_TEXT_COLOR],
                        chipColor = prefs[Prefs.NOW_PLAYING_CHIP_COLOR],
                        opacity = prefs[Prefs.NOW_PLAYING_CHIP_OPACITY],
                        onSideChange = { viewModel.save(Prefs.NOW_PLAYING_CHIP_SIDE, it) },
                        onWidthChange = { viewModel.save(Prefs.NOW_PLAYING_CHIP_WIDTH, it) },
                        onHideShadeChange = { viewModel.save(Prefs.NOW_PLAYING_CHIP_HIDE_SHADE, it) },
                        onTextColorChange = { viewModel.save(Prefs.NOW_PLAYING_CHIP_TEXT_COLOR, it) },
                        onChipColorChange = { viewModel.save(Prefs.NOW_PLAYING_CHIP_COLOR, it) },
                        onOpacityChange = { viewModel.save(Prefs.NOW_PLAYING_CHIP_OPACITY, it) },
                    )
                }
                entry<Destination.DownloadChipEditor>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                    DownloadChipSheet(
                        side = prefs[Prefs.DOWNLOAD_CHIP_SIDE],
                        icon = prefs[Prefs.DOWNLOAD_CHIP_ICON],
                        hideLocked = prefs[Prefs.DOWNLOAD_CHIP_HIDE_LOCKED],
                        hideHeadsUp = prefs[Prefs.DOWNLOAD_CHIP_HIDE_HEADS_UP],
                        hideStatusIcon = prefs[Prefs.DOWNLOAD_CHIP_HIDE_STATUS_ICON],
                        onSideChange = { viewModel.save(Prefs.DOWNLOAD_CHIP_SIDE, it) },
                        onIconChange = { viewModel.save(Prefs.DOWNLOAD_CHIP_ICON, it) },
                        onHideLockedChange = { viewModel.save(Prefs.DOWNLOAD_CHIP_HIDE_LOCKED, it) },
                        onHideHeadsUpChange = { viewModel.save(Prefs.DOWNLOAD_CHIP_HIDE_HEADS_UP, it) },
                        onHideStatusIconChange = { viewModel.save(Prefs.DOWNLOAD_CHIP_HIDE_STATUS_ICON, it) },
                    )
                }
                entry<Destination.BetterfoxEditor> { destination ->
                    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                    LaunchedEffect(destination.packageName) {
                        viewModel.betterfox.onOpen(destination.packageName)
                        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    if (betterfox.browser?.packageName == destination.packageName) {
                        BetterfoxScreen(
                            state = betterfox,
                            scrollTo = testNavigation?.scrollTo,
                            openMenu = testNavigation?.openMenu == true,
                            openInfo = testNavigation?.openInfo == true,
                            messages = viewModel.betterfox.messages,
                            actions =
                                BetterfoxActions(
                                    onBack = { if (backStack.lastOrNull() == destination) backStack.removeLastOrNull() },
                                    onOpenUrl = uriHandler::openUri,
                                    onPref = { push(Destination.BetterfoxPref(it)) },
                                    onEnabled = { viewModel.save(Prefs.FIREFOX_BETTERFOX[destination.packageName], it) },
                                    onClearOverrides = viewModel.betterfox::clearOverrides,
                                    onCheck = { viewModel.betterfox.checkForUpdate() },
                                    onBackUp = { push(Destination.BetterfoxBackup) },
                                    onRestore = {
                                        viewModel.betterfox.refreshBackups()
                                        push(Destination.BetterfoxRestore)
                                    },
                                    onResetAll = viewModel.betterfox::resetAll,
                                    onShare = { uri, name -> context.startActivity(shareIntent(context, uri, name)) },
                                    onUndoRestore = viewModel.betterfox::undoRestore,
                                    onUndoClear = viewModel.betterfox::undoClear,
                                ),
                        )
                    }
                    betterfoxRun?.let {
                        BetterfoxRunSheet(it, onCancel = viewModel.betterfox::cancelRun, onClose = viewModel.betterfox::dismissRun)
                    }
                }
                entry<Destination.BetterfoxPref>(metadata = BottomSheetSceneStrategy.bottomSheet()) { destination ->
                    val list = betterfox.list
                    val entry = list?.pref(destination.pref)
                    if (list == null || entry == null) {
                        LaunchedEffect(destination) { backStack.remove(destination) }
                    } else {
                        BetterfoxPrefSheet(
                            list = list,
                            entry = entry,
                            override = betterfox.overrides[entry.pref],
                            state = betterfox,
                            onOverrideChange = { viewModel.betterfox.setOverride(entry.pref, it) },
                            onOpenUrl = uriHandler::openUri,
                        )
                    }
                }
                entry<Destination.BetterfoxBackup>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                    betterfox.list?.let { list ->
                        BetterfoxBackupSheet(
                            state = betterfox,
                            list = list,
                            suggestedName = remember { viewModel.betterfox.suggestedName() },
                            onPickFolder = viewModel.betterfox::setFolder,
                            onSave = {
                                viewModel.betterfox.backUp(it)
                                backStack.remove(Destination.BetterfoxBackup)
                            },
                            onCancel = { backStack.remove(Destination.BetterfoxBackup) },
                        )
                    }
                }
                entry<Destination.BetterfoxRestore>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                    BetterfoxRestoreSheet(
                        backups = betterfox.backups,
                        onChooseFile = {
                            backStack.remove(Destination.BetterfoxRestore)
                            push(Destination.BetterfoxRestoreConfirm(it.toString()))
                        },
                        onNext = {
                            backStack.remove(Destination.BetterfoxRestore)
                            push(Destination.BetterfoxRestoreConfirm(it.uri.toString()))
                        },
                        onCancel = { backStack.remove(Destination.BetterfoxRestore) },
                    )
                }
                entry<Destination.BetterfoxRestoreConfirm>(metadata = BottomSheetSceneStrategy.bottomSheet()) { destination ->
                    val uri = remember(destination) { destination.uri.toUri() }
                    val entry by produceState<BackupEntry?>(null, destination) {
                        value = betterfox.backups.firstOrNull { it.uri == uri && it.backup != null }
                            ?: withContext(Dispatchers.IO) { viewModel.betterfox.readBackup(uri) }
                    }
                    val list = betterfox.list
                    val loaded = entry
                    if (loaded?.backup != null && list != null) {
                        val (changes, groups) =
                            remember(loaded, betterfox.overrides, betterfox.offGroups) {
                                viewModel.betterfox.changesFor(checkNotNull(loaded.backup))
                            }
                        BetterfoxRestoreConfirmSheet(
                            list = list,
                            entry = loaded,
                            changes = changes,
                            groups = groups,
                            browser = betterfox.browser?.label ?: stringResource(R.string.betterfox_browser),
                            onRestore = {
                                viewModel.betterfox.restore(loaded)
                                backStack.remove(destination)
                            },
                            onCancel = { backStack.remove(destination) },
                        )
                    } else {
                        Text(
                            stringResource(R.string.betterfox_not_a_backup),
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                        )
                    }
                }
                entry<Destination.Settings> {
                    SettingsScreen(
                        state = support,
                        hideUnavailable = hideUnavailable,
                        showPackages = showPackages,
                        dateStyle = dateStyle,
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
                                onAutoCheckUpdates = viewModel.support::setAutoCheckUpdates,
                                onHideUnavailable = viewModel::setHideUnavailable,
                                onShowPackages = viewModel::setShowPackages,
                                onModuleLog = {
                                    push(Destination.ModuleLog)
                                },
                                onDateFormat = { push(Destination.DateFormat) },
                                onAbout = { push(Destination.About) },
                            ),
                        onBack = { if (backStack.lastOrNull() == Destination.Settings) backStack.removeLastOrNull() },
                    )
                }
                entry<Destination.About> {
                    AboutScreen(
                        framework = support.framework,
                        actions =
                            AboutActions(
                                onWhatsNew = { push(Destination.WhatsNew) },
                                onChangelog = { push(Destination.Changelog) },
                                onLicenses = { push(Destination.Licenses) },
                                onOpenUrl = uriHandler::openUri,
                            ),
                        onBack = { if (backStack.lastOrNull() == Destination.About) backStack.removeLastOrNull() },
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
                        onRequestRoot = viewModel::openReport,
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
                        onOpenIssue = { uriHandler.openUri("https://github.com/${ReportConfig.repo}/issues/$it") },
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
                            fixes = support.fixesByLine,
                            step = support.install,
                            actions =
                                UpdateActions(
                                    onInstall = { viewModel.support.startInstall(release) },
                                    onInstallAnyway = { viewModel.support.installAnyway(release) },
                                    onOpenSettings = {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                                "package:${context.packageName}".toUri(),
                                            ),
                                        )
                                    },
                                    onGitHub = { uriHandler.openUri(release.url) },
                                    onCancel = viewModel.support::cancelInstall,
                                ),
                        )
                    }
                }
                entry<Destination.WhatsNew>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                    WhatsNewSheet(
                        onFullChangelog = {
                            backStack.remove(Destination.WhatsNew)
                            push(Destination.Changelog)
                        },
                        onClose = { backStack.remove(Destination.WhatsNew) },
                    )
                }
                entry<Destination.Changelog> {
                    ChangelogScreen(onBack = { if (backStack.size > 1) backStack.removeLastOrNull() })
                }
                entry<Destination.Licenses> {
                    LicensesScreen(onBack = { if (backStack.size > 1) backStack.removeLastOrNull() })
                }
                entry<Destination.DateFormat>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
                    DateFormatSheet(selected = dateStyle, onSelect = viewModel::setDateStyle)
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
