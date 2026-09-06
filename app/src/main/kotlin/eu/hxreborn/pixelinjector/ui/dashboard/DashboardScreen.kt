package eu.hxreborn.pixelinjector.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.prefs.AppPrefs
import eu.hxreborn.pixelinjector.prefs.BoolPref
import eu.hxreborn.pixelinjector.prefs.IntPref
import eu.hxreborn.pixelinjector.ui.component.SectionGap
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.component.TileText
import eu.hxreborn.pixelinjector.ui.component.installedVersionLabel
import eu.hxreborn.pixelinjector.ui.component.sectionHeader
import eu.hxreborn.pixelinjector.ui.component.tileGroup
import eu.hxreborn.pixelinjector.ui.navigation.Destination
import eu.hxreborn.pixelinjector.ui.theme.AppText
import kotlin.math.roundToInt

private val TopBarExpandedHeight = 152.dp
private val TitleBottomPadding = 28.dp
private val ToolbarOverlap = (-20).dp
private val ToolbarClearance = 96.dp
private val ToolbarButtonSize = 48.dp
private val ToolbarGap = 4.dp
private val ChevronSize = 22.dp
private val LinkChevronSlot = 40.dp
private val LeadingButtonPadding = PaddingValues(start = 16.dp, end = 20.dp)
private const val CHEVRON_OPEN_ROTATION = 180f
private const val RELOAD_COUNT_ALPHA = 0.85f
private const val PERCENT_MAX = 100
private const val PERCENT_STEP = 10
private const val SLIDER_STEPS = PERCENT_MAX / PERCENT_STEP - 1

private sealed interface DashTile {
    val title: Int

    class Toggle(
        val tweak: TweakUi,
        val checked: Boolean,
    ) : DashTile {
        override val title: Int get() = tweak.title
    }

    class Link(
        val editor: TweakEditor,
        val count: Int,
    ) : DashTile {
        override val title: Int get() = editor.title
    }

    class Slide(
        val slider: TweakSlider,
        val value: Int,
    ) : DashTile {
        override val title: Int get() = slider.title
    }
}

private fun rows(
    group: TweakGroup,
    prefs: AppPrefs,
): List<DashTile> =
    tweakUis.filter { it.group == group }.flatMap { tweak ->
        val checked = prefs[tweak.pref]
        listOfNotNull(
            DashTile.Toggle(tweak, checked),
            tweak.editor?.takeIf { checked }?.let { DashTile.Link(it, it.count(prefs)) },
            tweak.slider?.takeIf { checked }?.let { DashTile.Slide(it, prefs[it.pref]) },
        )
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardScreen(
    state: DashboardState,
    targetsOpen: Boolean,
    actions: DashboardActions,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var resumedBefore by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (resumedBefore) actions.onRefresh() else resumedBefore = true
        onPauseOrDispose {}
    }
    val homeApp = state.homeApp
    val launcherTitle = homeApp?.takeIf { it.supported }?.label ?: stringResource(R.string.scope_launcher)
    val launcherError =
        when {
            homeApp == null || !homeApp.supported -> stringResource(R.string.home_app_unsupported, homeApp?.label ?: "?")
            !homeApp.loaded -> stringResource(R.string.home_app_not_loaded, homeApp.label)
            else -> null
        }
    val sections =
        listOf(
            TweakGroup.SYSTEM_UI to stringResource(R.string.scope_systemui),
            TweakGroup.SYSTEM to stringResource(R.string.scope_system),
            TweakGroup.LAUNCHER to launcherTitle,
            TweakGroup.GBOARD to stringResource(R.string.scope_gboard),
            TweakGroup.DIALER to stringResource(R.string.scope_dialer),
            TweakGroup.FILES to stringResource(R.string.scope_files),
        ).filter { (group, _) -> group == TweakGroup.LAUNCHER || group in state.packages }
    val errorLines =
        buildMap {
            state.errors.forEach { (tweak, build) -> put(tweak, stringResource(R.string.target_not_found, build)) }
            launcherError?.let { error -> tweakUis.filter { it.group == TweakGroup.LAUNCHER }.forEach { put(it.key, error) } }
            sections.forEach { (group, title) ->
                if (state.packages[group]?.scoped == false) {
                    val error = stringResource(R.string.home_app_not_loaded, title)
                    tweakUis.filter { it.group == group }.forEach { put(it.key, error) }
                }
            }
        }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TwoRowsTopAppBar(
                title = { Text(stringResource(R.string.app_name), style = AppText.appBarTitle, modifier = Modifier.padding(start = 8.dp)) },
                subtitle = { expanded ->
                    if (expanded) {
                        Text(
                            "v$installedVersionLabel",
                            style = AppText.appBarVersion,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, bottom = TitleBottomPadding),
                        )
                    }
                },
                expandedHeight = TopBarExpandedHeight,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            Toolbar(
                bound = state.bound,
                reloadCount = state.reloadCount,
                targetsOpen = targetsOpen,
                onOpenLog = actions.onOpenLog,
                onHotReload = actions.onHotReload,
                onToggleTargets = actions.onToggleTargets,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .offset(y = ToolbarOverlap)
                        .zIndex(1f),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = ToolbarClearance + navBottom),
            ) {
                if (!state.bound) {
                    item(key = "not-bound") {
                        if (state.everBound) NotBoundSurface(actions.onRestart) else FirstRunSurface()
                        Spacer(Modifier.height(SectionGap))
                    }
                }
                if (state.rootDenied) {
                    item(key = "root-denied") {
                        AlertSurface(Icons.Outlined.Terminal, stringResource(R.string.root_missing), actions.onRefresh)
                        Spacer(Modifier.height(SectionGap))
                    }
                }
                sections.forEachIndexed { index, (group, title) ->
                    if (index > 0) item(key = "gap:$group") { Spacer(Modifier.height(SectionGap)) }
                    scope(
                        title,
                        state.packages[group]?.packages.orEmpty(),
                        rows(group, state.prefs),
                        errorLines,
                        actions.onToggle,
                        actions.onSlide,
                        actions.onOpen,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.scope(
    title: String,
    packages: List<String>,
    tiles: List<DashTile>,
    errors: Map<String, String>,
    onToggle: (BoolPref, Boolean) -> Unit,
    onSlide: (IntPref, Int) -> Unit,
    onOpen: (Destination) -> Unit,
) {
    sectionHeader(title, packages)
    tileGroup(tiles, key = { it.title }) { tile, shape ->
        when (tile) {
            is DashTile.Toggle -> ToggleTile(tile, shape, tile.tweak.errorKeys.firstNotNullOfOrNull { errors[it] }, onToggle)
            is DashTile.Link -> LinkTile(tile, shape, onOpen)
            is DashTile.Slide -> SlideTile(tile, shape, onSlide)
        }
    }
}

@Composable
private fun SlideTile(
    tile: DashTile.Slide,
    shape: RoundedCornerShape,
    onSlide: (IntPref, Int) -> Unit,
) {
    var value by remember(tile.value) { mutableIntStateOf(tile.value) }
    Tile(shape = shape) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TileText(stringResource(tile.title), stringResource(tile.slider.description))
                Text(
                    value.toString(),
                    style = AppText.count,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { value = it.roundToInt() },
                onValueChangeFinished = { onSlide(tile.slider.pref, value) },
                valueRange = 0f..PERCENT_MAX.toFloat(),
                steps = SLIDER_STEPS,
            )
        }
    }
}

@Composable
private fun ToggleTile(
    tile: DashTile.Toggle,
    shape: RoundedCornerShape,
    error: String?,
    onToggle: (BoolPref, Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Tile(
        shape = shape,
        checked = tile.checked,
        onCheckedChange = {
            haptic.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onToggle(tile.tweak.pref, it)
        },
        modifier = Modifier.semantics { role = Role.Switch },
    ) {
        if (error != null) {
            TileText(stringResource(tile.title), error, supportingColor = MaterialTheme.colorScheme.error)
        } else {
            TileText(stringResource(tile.title), stringResource(tile.tweak.description))
        }
        Switch(
            checked = tile.checked,
            onCheckedChange = null,
            thumbContent = {
                Icon(
                    if (tile.checked) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
        )
    }
}

@Composable
private fun LinkTile(
    tile: DashTile.Link,
    shape: RoundedCornerShape,
    onOpen: (Destination) -> Unit,
) {
    Tile(shape = shape, onClick = { onOpen(tile.editor.destination) }) {
        TileText(stringResource(tile.title), tile.editor.description?.let { stringResource(it) })
        Text(
            tile.count.toString(),
            style = AppText.count,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp),
        )
        Box(Modifier.width(LinkChevronSlot), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Toolbar(
    bound: Boolean,
    reloadCount: Int,
    targetsOpen: Boolean,
    onOpenLog: () -> Unit,
    onHotReload: () -> Unit,
    onToggleTargets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        IconButton(onClick = onOpenLog, modifier = Modifier.size(ToolbarButtonSize)) {
            Icon(Icons.AutoMirrored.Outlined.Notes, contentDescription = stringResource(R.string.module_log))
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
