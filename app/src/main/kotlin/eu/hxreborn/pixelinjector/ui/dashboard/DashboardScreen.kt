package eu.hxreborn.pixelinjector.ui.dashboard

import android.icu.text.ListFormatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.prefs.AppPrefs
import eu.hxreborn.pixelinjector.prefs.BoolPref
import eu.hxreborn.pixelinjector.prefs.IntPref
import eu.hxreborn.pixelinjector.sound.SystemSounds
import eu.hxreborn.pixelinjector.ui.component.AppGlyph
import eu.hxreborn.pixelinjector.ui.component.SectionGap
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.component.TileLeading
import eu.hxreborn.pixelinjector.ui.component.TileSwitch
import eu.hxreborn.pixelinjector.ui.component.TileText
import eu.hxreborn.pixelinjector.ui.component.compactHeight
import eu.hxreborn.pixelinjector.ui.component.readableGutter
import eu.hxreborn.pixelinjector.ui.component.sectionHeader
import eu.hxreborn.pixelinjector.ui.component.tileGroup
import eu.hxreborn.pixelinjector.ui.component.versionLabel
import eu.hxreborn.pixelinjector.ui.log.isRootSleepFailure
import eu.hxreborn.pixelinjector.ui.log.processKey
import eu.hxreborn.pixelinjector.ui.navigation.Destination
import eu.hxreborn.pixelinjector.ui.support.SupportState
import eu.hxreborn.pixelinjector.ui.support.UpdateCard
import eu.hxreborn.pixelinjector.ui.theme.AppText
import eu.hxreborn.pixelinjector.ui.util.verticalScrollbar
import eu.hxreborn.pixelinjector.ui.viewmodel.UpdateNotice
import eu.hxreborn.pixelinjector.ui.viewmodel.UpdateNoticeTarget
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val TopBarExpandedHeight = 152.dp
private val TitleBottomPadding = 28.dp
private val ToolbarOverlap = (-20).dp
private val HeaderGlyphSlot = 24.dp
private val ToolbarClearance = 96.dp
private val ToolbarButtonSize = 48.dp
private val ToolbarGap = 4.dp
private val ChevronSize = 22.dp
private val LinkChevronSlot = 40.dp
private val ChildStartPadding = 52.dp
private val ChildShape = RoundedCornerShape(0.dp)
private val ChildMinHeight = 48.dp
private val ChildVerticalPadding = 6.dp
private val ChildValueGap = 12.dp
private val ChildValueMaxWidth = 160.dp
private val SliderLabelEndPadding = 8.dp
private const val CHILD_DIVIDER_ALPHA = 0.5f
private val LeadingButtonPadding = PaddingValues(start = 16.dp, end = 20.dp)
private val ReportButtonInset = (-12).dp
private const val CHEVRON_OPEN_ROTATION = 180f
private const val RELOAD_COUNT_ALPHA = 0.85f

private class Section(
    val group: TweakGroup,
    val title: String,
    val packages: List<String>,
    val scoped: Boolean,
    val browser: String?,
) {
    val key: String get() = browser ?: group.name

    fun errorKey(tweak: String): String = browser?.let { processKey(tweak, it) } ?: tweak
}

private class TweakRow(
    val toggle: DashTile.Toggle,
    val children: List<DashTile>,
)

private sealed interface DashTile {
    val title: Int

    class Toggle(
        val tweak: TweakUi,
        val pref: BoolPref,
        val checked: Boolean,
        val screen: Destination?,
        val errorKey: String,
    ) : DashTile {
        override val title: Int get() = tweak.title
    }

    class Link(
        val editor: TweakEditor,
        val summary: String,
    ) : DashTile {
        override val title: Int get() = editor.title
    }

    class Slide(
        val slider: TweakSlider,
        val value: Int,
    ) : DashTile {
        override val title: Int get() = slider.title
    }

    class Sound(
        val sound: TweakSound,
        val path: String,
    ) : DashTile {
        override val title: Int get() = sound.title
    }
}

private fun rows(
    section: Section,
    prefs: AppPrefs,
    hidden: Set<String>,
): List<TweakRow> =
    tweakUis.filter { it.group == section.group && section.errorKey(it.key) !in hidden }.map { tweak ->
        val pref = tweak.pref(section.browser)
        TweakRow(
            DashTile.Toggle(tweak, pref, prefs[pref], tweak.screen(section.browser), section.errorKey(tweak.key)),
            listOfNotNull(
                tweak.editor?.let { DashTile.Link(it, it.summary(prefs)) },
                tweak.slider?.let { DashTile.Slide(it, prefs[it.pref]) },
                tweak.sound?.let { DashTile.Sound(it, prefs[it.pref]) },
            ),
        )
    }

@Composable
private fun updateNoticeText(targets: List<UpdateNoticeTarget>): String {
    if (targets.isEmpty()) return stringResource(R.string.updated_version, BuildConfig.VERSION_NAME)
    val olderVersion = targets.map { it.olderVersionCode }.distinct().singleOrNull()
    val labels =
        targets.map { target ->
            val version = target.olderVersionCode
            if (olderVersion == null && version != null) {
                stringResource(R.string.updated_target_version, target.label, versionLabel(version))
            } else {
                target.label
            }
        }
    val targetList = ListFormatter.getInstance(LocalConfiguration.current.locales[0]).format(labels)
    return if (olderVersion == null) {
        pluralStringResource(R.plurals.updated_needs_reload, targets.size, BuildConfig.VERSION_NAME, targetList)
    } else {
        pluralStringResource(
            R.plurals.updated_older_version,
            targets.size,
            BuildConfig.VERSION_NAME,
            targetList,
            versionLabel(olderVersion),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardScreen(
    state: DashboardState,
    support: SupportState,
    updatedNotice: UpdateNotice?,
    targetsOpen: Boolean,
    hideUnavailable: Boolean,
    showPackages: Boolean,
    actions: DashboardActions,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val toolbarScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = FloatingToolbarExitDirection.Bottom)
    var resumedBefore by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (resumedBefore) actions.onRefresh() else resumedBefore = true
        actions.onResumeSupport()
        onPauseOrDispose {}
    }
    val snackbar = remember { SnackbarHostState() }
    val reloadLabel = stringResource(R.string.reload)
    val updatedText = updatedNotice?.let { updateNoticeText(it.retryTargets) }
    val noticeScope = rememberCoroutineScope()
    LaunchedEffect(updatedNotice) {
        val notice = updatedNotice ?: return@LaunchedEffect
        val text = updatedText ?: return@LaunchedEffect
        val action = reloadLabel.takeIf { notice.retryTargets.isNotEmpty() }
        if (!actions.onConsumeUpdateNotice()) return@LaunchedEffect
        noticeScope.launch {
            val result =
                snackbar.showSnackbar(
                    text,
                    action,
                    duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) actions.onRetryUpdateReload(notice)
        }
    }
    val homeApp = state.homeApp
    val launcherTitle = homeApp?.takeIf { it.supported }?.label ?: stringResource(R.string.scope_launcher)
    val launcherError =
        when {
            homeApp == null || !homeApp.supported -> stringResource(R.string.home_app_unsupported, homeApp?.label ?: "?")
            !homeApp.loaded -> stringResource(R.string.home_app_not_loaded, homeApp.label)
            else -> null
        }
    val sectionTitles =
        listOf(
            TweakGroup.SYSTEM_UI to stringResource(R.string.scope_systemui),
            TweakGroup.SYSTEM to stringResource(R.string.scope_system),
            TweakGroup.LAUNCHER to launcherTitle,
            TweakGroup.GBOARD to stringResource(R.string.scope_gboard),
            TweakGroup.DIALER to stringResource(R.string.scope_dialer),
            TweakGroup.FILES to stringResource(R.string.scope_files),
            TweakGroup.KDE_CONNECT to stringResource(R.string.scope_kde_connect),
            TweakGroup.GITHUB to stringResource(R.string.scope_github),
            TweakGroup.FIREFOX to stringResource(R.string.scope_firefox),
        ).filter { (group, _) -> group == TweakGroup.LAUNCHER || group in state.packages }
    val sections =
        sectionTitles.flatMap { (group, title) ->
            val hooked = state.packages[group]
            if (group == TweakGroup.FIREFOX && hooked != null) {
                hooked.packages.map { pkg -> Section(group, hooked.labels[pkg] ?: pkg, listOf(pkg), hooked.scoped, pkg) }
            } else {
                listOf(Section(group, title, hooked?.packages.orEmpty(), hooked?.scoped != false, null))
            }
        }
    val errorLines =
        buildMap {
            state.errorLines.forEach { (tweak, line) ->
                val error =
                    if (isRootSleepFailure(line)) {
                        TileError(stringResource(R.string.sleep_root_failed, launcherTitle), reportable = false)
                    } else {
                        TileError(stringResource(R.string.tweak_failed), reportable = true)
                    }
                put(tweak, error)
            }
            state.errors.forEach { (tweak, error) ->
                if (error.externalFingerprintIcon && tweak in state.errorLines) return@forEach
                val message =
                    if (error.externalFingerprintIcon) {
                        stringResource(R.string.fingerprint_icon_outside_systemui)
                    } else {
                        stringResource(R.string.target_not_found, error.build)
                    }
                put(tweak, TileError(message, reportable = true))
            }
            launcherError?.let { error ->
                tweakUis.filter { it.group == TweakGroup.LAUNCHER }.forEach { put(it.key, TileError(error, reportable = false)) }
            }
            sections.filterNot { it.scoped }.forEach { section ->
                val error = TileError(stringResource(R.string.home_app_not_loaded, section.title), reportable = false)
                tweakUis.filter { it.group == section.group }.forEach { put(section.errorKey(it.key), error) }
            }
        }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    val updateCardShown = support.available != null && support.showUpdateCard
    LaunchedEffect(updateCardShown) {
        if (updateCardShown && listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection).nestedScroll(toolbarScrollBehavior),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding().padding(bottom = ToolbarClearance)) },
        topBar = {
            TwoRowsTopAppBar(
                title = { expanded ->
                    Text(
                        stringResource(R.string.app_name),
                        style = if (expanded) AppText.appBarTitle else MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                },
                subtitle = { expanded ->
                    if (expanded) {
                        Text(
                            "v${BuildConfig.VERSION_NAME}",
                            style = AppText.appBarVersion,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, bottom = TitleBottomPadding),
                        )
                    }
                },
                expandedHeight = if (compactHeight()) TopAppBarDefaults.TopAppBarExpandedHeight else TopBarExpandedHeight,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val gutter = readableGutter()
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            Toolbar(
                bound = state.bound,
                reloadCount = state.reloadCount,
                targetsOpen = targetsOpen,
                onOpenSettings = actions.onOpenSettings,
                badge =
                    when {
                        support.needsReply -> stringResource(R.string.settings_reply_badge)
                        support.available != null -> stringResource(R.string.settings_update_badge)
                        else -> null
                    },
                onHotReload = actions.onHotReload,
                onToggleTargets = actions.onToggleTargets,
                scrollBehavior = toolbarScrollBehavior,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .offset(y = ToolbarOverlap)
                        .zIndex(1f),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().verticalScrollbar(listState),
                contentPadding = PaddingValues(start = gutter, end = gutter, bottom = ToolbarClearance + navBottom),
            ) {
                if (!state.bound) {
                    item(key = "not-bound") {
                        if (state.everBound) NotBoundSurface(actions.onRestart) else FirstRunSurface()
                        Spacer(Modifier.height(SectionGap))
                    }
                }
                val release = support.available
                if (release != null && support.showUpdateCard) {
                    item(key = "update") {
                        UpdateCard(
                            release,
                            support.fixesByLine,
                            onLater = { actions.onLater(release.versionCode) },
                            onUpdate = actions.onUpdate,
                        )
                        Spacer(Modifier.height(SectionGap))
                    }
                }
                if (state.rootDenied) {
                    item(key = "root-denied") {
                        AlertSurface(Icons.Outlined.Terminal, stringResource(R.string.root_missing), actions.onRefresh)
                        Spacer(Modifier.height(SectionGap))
                    }
                }
                val hidden = if (hideUnavailable) errorLines.keys else emptySet()
                sections
                    .map { section -> section to rows(section, state.prefs, hidden) }
                    .filter { (_, tiles) -> tiles.isNotEmpty() }
                    .forEachIndexed { index, (section, tiles) ->
                        if (index > 0) item(key = "gap:${section.key}") { Spacer(Modifier.height(SectionGap)) }
                        scope(
                            section,
                            showPackages,
                            tiles,
                            errorLines,
                            actions.onToggle,
                            actions.onSlide,
                            actions.onOpen,
                            actions.onReport,
                        )
                    }
            }
        }
    }
}

private data class TileError(
    val text: String,
    val reportable: Boolean,
)

private fun LazyListScope.scope(
    section: Section,
    showPackages: Boolean,
    tiles: List<TweakRow>,
    errors: Map<String, TileError>,
    onToggle: (BoolPref, Boolean) -> Unit,
    onSlide: (IntPref, Int) -> Unit,
    onOpen: (Destination) -> Unit,
    onReport: (String?) -> Unit,
) {
    val iconPackageName = if (section.group == TweakGroup.SYSTEM) "android" else section.packages.firstOrNull()
    val groupSymbol = groupSymbols[section.group]
    sectionHeader(section.title, if (showPackages) section.packages else emptyList(), section.key) {
        Box(Modifier.size(HeaderGlyphSlot), contentAlignment = Alignment.Center) { AppGlyph(iconPackageName, groupSymbol) }
        Spacer(Modifier.width(0.dp))
    }
    tileGroup(tiles, key = { "${section.key}:${it.toggle.title}" }, animatePlacement = false) { row, shape ->
        val toggle = row.toggle
        Column(Modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)) {
            ToggleTile(toggle, errors[toggle.errorKey], tweakSymbol(toggle.tweak.key), onToggle, onOpen, onReport)
            AnimatedVisibility(
                visible = toggle.checked && row.children.isNotEmpty(),
                enter =
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit =
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
            ) {
                Column(Modifier.padding(start = ChildStartPadding)) {
                    row.children.forEach { child ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = CHILD_DIVIDER_ALPHA))
                        when (child) {
                            is DashTile.Link -> LinkTile(child, onOpen)
                            is DashTile.Slide -> SlideTile(child, onSlide)
                            is DashTile.Sound -> SoundTile(child, onOpen)
                            is DashTile.Toggle -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChildTile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Tile(
        shape = ChildShape,
        modifier = modifier,
        color = Color.Transparent,
        startPadding = 0.dp,
        verticalPadding = ChildVerticalPadding,
        minHeight = ChildMinHeight,
        onClick = onClick,
        content = content,
    )
}

@Composable
private fun RowScope.ChildText(
    title: String,
    description: String?,
) {
    Column(Modifier.weight(1f)) {
        Text(title, style = AppText.tileSupporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (description != null) {
            Text(description, style = AppText.tileSupporting, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ChildValue(text: String) {
    Text(
        text,
        style = AppText.tileSupporting,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = ChildValueGap).widthIn(max = ChildValueMaxWidth),
    )
}

@Composable
private fun ChildChevron() {
    Box(Modifier.width(LinkChevronSlot), contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SlideTile(
    tile: DashTile.Slide,
    onSlide: (IntPref, Int) -> Unit,
) {
    var value by remember(tile.value) { mutableIntStateOf(tile.value) }
    val range = tile.slider.range
    val start = range.first.toFloat()
    val end = range.last.toFloat()
    ChildTile {
        Column(Modifier.weight(1f)) {
            Row(Modifier.padding(end = SliderLabelEndPadding), verticalAlignment = Alignment.CenterVertically) {
                ChildText(stringResource(tile.title), stringResource(tile.slider.description))
                ChildValue(value.toString())
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { value = it.roundToInt() },
                onValueChangeFinished = { onSlide(tile.slider.pref, value) },
                valueRange = start..end,
                steps = (range.last - range.first) / tile.slider.step - 1,
            )
        }
    }
}

@Composable
private fun ToggleTile(
    tile: DashTile.Toggle,
    error: TileError?,
    icon: ImageVector?,
    onToggle: (BoolPref, Boolean) -> Unit,
    onOpen: (Destination) -> Unit,
    onReport: (String?) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val screen = tile.screen
    Tile(
        shape = ChildShape,
        color = Color.Transparent,
        onClick = screen?.let { { onOpen(it) } },
        checked = tile.checked.takeIf { screen == null },
        onCheckedChange = {
            haptic.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onToggle(tile.pref, it)
        },
        modifier = if (screen == null) Modifier.semantics { role = Role.Switch } else Modifier,
    ) {
        TileLeading(icon)
        if (error != null) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TileText(stringResource(tile.title), error.text, supportingColor = MaterialTheme.colorScheme.error)
                }
                if (error.reportable) {
                    TextButton(
                        onClick = { onReport(tile.tweak.key) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.offset(x = ReportButtonInset),
                    ) {
                        Text(stringResource(R.string.report), style = AppText.button)
                    }
                }
            }
        } else {
            TileText(stringResource(tile.title), stringResource(tile.tweak.description))
        }
        if (screen != null) {
            ChildChevron()
            return@Tile
        }
        TileSwitch(tile.checked)
    }
}

@Composable
private fun SoundTile(
    tile: DashTile.Sound,
    onOpen: (Destination) -> Unit,
) {
    ChildTile(onClick = { onOpen(tile.sound.destination) }) {
        val label = if (tile.path.isEmpty()) stringResource(R.string.sound_none) else SystemSounds.label(tile.path)
        ChildText(stringResource(tile.title), null)
        ChildValue(label)
        ChildChevron()
    }
}

@Composable
private fun LinkTile(
    tile: DashTile.Link,
    onOpen: (Destination) -> Unit,
) {
    ChildTile(onClick = { onOpen(tile.editor.destination) }) {
        ChildText(stringResource(tile.title), tile.editor.description?.let { stringResource(it) })
        ChildValue(tile.summary)
        ChildChevron()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Toolbar(
    bound: Boolean,
    reloadCount: Int,
    targetsOpen: Boolean,
    onOpenSettings: () -> Unit,
    badge: String?,
    onHotReload: () -> Unit,
    onToggleTargets: () -> Unit,
    scrollBehavior: FloatingToolbarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        scrollBehavior = scrollBehavior,
    ) {
        IconButton(onClick = onOpenSettings, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(ToolbarButtonSize)) {
            BadgedBox(badge = { if (badge != null) Badge() }) {
                Icon(Icons.Outlined.Settings, contentDescription = badge ?: stringResource(R.string.settings))
            }
        }
        Spacer(Modifier.width(ToolbarGap))
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = onHotReload,
                    enabled = bound,
                    modifier = Modifier.height(ToolbarButtonSize),
                    contentPadding = LeadingButtonPadding,
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.reload), style = AppText.button)
                    if (reloadCount > 0) {
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(
                            reloadCount.toString(),
                            style = AppText.buttonCount,
                            modifier = Modifier.graphicsLayer { alpha = RELOAD_COUNT_ALPHA },
                        )
                    }
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = targetsOpen,
                    onCheckedChange = { onToggleTargets() },
                    modifier = Modifier.size(ToolbarButtonSize),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    val rotation by animateFloatAsState(
                        if (targetsOpen) CHEVRON_OPEN_ROTATION else 0f,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "chevron",
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.targets_sheet_open),
                        modifier = Modifier.size(ChevronSize).graphicsLayer { rotationZ = rotation },
                    )
                }
            },
        )
    }
}

@Composable
private fun FirstRunSurface() {
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.RestartAlt, contentDescription = null)
            Text(stringResource(R.string.first_run), style = AppText.tileTitle)
        }
    }
}

@Composable
private fun NotBoundSurface(onRestart: () -> Unit) {
    AlertSurface(Icons.Outlined.ErrorOutline, stringResource(R.string.not_bound), onRestart)
}

@Composable
private fun AlertSurface(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Text(text, style = AppText.tileTitle)
        }
    }
}
