package eu.hxreborn.pixelinjector.ui.log

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.ui.component.DetailScaffold
import eu.hxreborn.pixelinjector.ui.component.processNameForDisplay
import eu.hxreborn.pixelinjector.ui.theme.AppText
import eu.hxreborn.pixelinjector.ui.util.drawVerticalScrollbar
import kotlinx.coroutines.launch

private val levelFilters = listOf('V' to R.string.filter_all, 'W' to R.string.log_filter_warnings, 'E' to R.string.log_filter_errors)

private const val LEVEL_ORDER = "VDIWEF"
private const val ALL_LEVELS = 'V'
private val StripeWidth = 3.dp
private val JumpButtonClearance = 64.dp
private val PanSlack = 32.dp
private val HangingIndent = 12.sp
private const val PROCESS_TAG_ALPHA = 0.2f
private const val VERBOSE_LEVEL_ALPHA = 0.5f

private fun rank(level: Char): Int = LEVEL_ORDER.indexOf(level).coerceAtLeast(0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModuleLogScreen(
    state: ModuleLogState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var minLevel by rememberSaveable { mutableStateOf(ALL_LEVELS) }
    var wrap by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { onRefresh() }
    val tweaks = remember(state.entries) { state.tweaks }
    val active = filter?.takeIf { it in tweaks }
    val visible =
        remember(state.entries, active, query, minLevel) {
            val floor = rank(minLevel)
            state.entries.filter { entry ->
                (active == null || entry.tweak == active) &&
                    rank(entry.level) >= floor &&
                    (query.isBlank() || entry.body.contains(query, ignoreCase = true) || entry.process.contains(query, ignoreCase = true))
            }
        }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = visible.lastIndex.coerceAtLeast(0))
    val expanded = remember { mutableStateSetOf<LogEntry>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val range by
        remember {
            derivedStateOf {
                val info = listState.layoutInfo
                info.visibleItemsInfo.takeIf { it.isNotEmpty() }?.let {
                    Triple(
                        it.first().index + 1,
                        it.last().index + 1,
                        info.totalItemsCount,
                    )
                }
            }
        }
    val showJump by remember { derivedStateOf { listState.canScrollForward || listState.canScrollBackward } }
    LaunchedEffect(visible.size) { if (visible.isNotEmpty()) listState.scrollToItem(visible.lastIndex) }
    val panState = rememberScrollState()
    LaunchedEffect(wrap) { if (wrap) panState.scrollTo(0) }
    val textMeasurer = rememberTextMeasurer()
    val widestPx =
        remember(visible, textMeasurer, wrap) {
            if (wrap) return@remember 0
            visible.maxByOrNull { it.process.length + it.body.length }?.let { longest ->
                textMeasurer.measure("I 00:00:00  ${longest.process}   ${longest.body}", AppText.console).size.width
            } ?: 0
        }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    DetailScaffold(
        title = stringResource(R.string.module_log),
        subtitle =
            (range ?: Triple(minOf(1, visible.size), visible.size, visible.size)).let { (first, last, total) ->
                stringResource(R.string.log_lines_range, first, last, total)
            },
        onBack = onBack,
        actions = {
            IconToggleButton(checked = wrap, onCheckedChange = { wrap = it }) {
                Icon(Icons.AutoMirrored.Outlined.WrapText, contentDescription = stringResource(R.string.log_wrap))
            }
            LevelMenu(minLevel) { minLevel = it }
            IconButton(
                onClick = {
                    val text = visible.joinToString("\n") { "${it.time} ${it.pid} ${it.level} ${it.body}" }
                    val clip = ClipData.newPlainText("PixelInjector module log", text)
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                },
                enabled = visible.isNotEmpty(),
            ) { Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy)) }
            IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh)) }
        },
    ) { _ ->
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text(stringResource(R.string.log_search)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon =
                        if (query.isEmpty()) {
                            null
                        } else {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.clear_search))
                                }
                            }
                        },
                )
            },
            expanded = false,
            onExpandedChange = {},
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
        ) {}
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = active == null, onClick = { filter = null }, label = { Text(stringResource(R.string.filter_all)) })
            tweaks.forEach { tweak ->
                FilterChip(selected = active == tweak, onClick = { filter = tweak }, label = { Text(tweak, style = AppText.chip) })
            }
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = maxOf(navBottom, 16.dp)),
        ) {
            when {
                state.loading && state.entries.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }

                state.denied -> {
                    Message(stringResource(R.string.root_denied))
                }

                state.entries.isEmpty() -> {
                    Message(stringResource(R.string.log_empty))
                }

                visible.isEmpty() -> {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
                        Message(stringResource(R.string.log_filter_empty))
                        TextButton(onClick = {
                            query = ""
                            minLevel = ALL_LEVELS
                            filter = null
                        }) { Text(stringResource(R.string.clear_filters)) }
                    }
                }

                else -> {
                    val bodyStyle =
                        remember(
                            wrap,
                        ) { if (wrap) AppText.console.copy(textIndent = TextIndent(restLine = HangingIndent)) else AppText.console }
                    BoxWithConstraints(Modifier.fillMaxWidth().drawVerticalScrollbar(listState)) {
                        val listWidth = with(LocalDensity.current) { maxOf(maxWidth, widestPx.toDp() + PanSlack) }
                        val viewportWidth = maxWidth
                        Box(if (wrap) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().horizontalScroll(panState)) {
                            SelectionContainer {
                                LazyColumn(
                                    state = listState,
                                    modifier = if (wrap) Modifier.fillMaxWidth() else Modifier.width(listWidth),
                                    contentPadding = PaddingValues(top = 12.dp, bottom = if (showJump) JumpButtonClearance else 12.dp),
                                ) {
                                    items(visible) { entry ->
                                        EntryRow(
                                            entry = entry,
                                            wrap = wrap,
                                            expanded = entry in expanded,
                                            viewportWidth = viewportWidth,
                                            bodyStyle = bodyStyle,
                                            onToggle = { if (!expanded.remove(entry)) expanded.add(entry) },
                                            onCopy = {
                                                val clip = ClipData.newPlainText("PixelInjector log line", entry.raw())
                                                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                                            },
                                            onTag = { filter = it },
                                        )
                                    }
                                }
                            }
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showJump,
                            modifier = Modifier.align(Alignment.BottomEnd),
                            enter =
                                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                                    scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec()),
                            exit =
                                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                                    scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()),
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SmallFloatingActionButton(onClick = { scope.launch { listState.scrollToItem(0) } }) {
                                    Icon(Icons.Outlined.VerticalAlignTop, contentDescription = stringResource(R.string.log_jump_top))
                                }
                                SmallFloatingActionButton(onClick = { scope.launch { listState.scrollToItem(visible.lastIndex) } }) {
                                    Icon(Icons.Outlined.VerticalAlignBottom, contentDescription = stringResource(R.string.log_jump_bottom))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelMenu(
    minLevel: Char,
    onSelect: (Char) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Outlined.FilterList,
                contentDescription = stringResource(R.string.log_filter_level),
                tint = if (minLevel == ALL_LEVELS) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            levelFilters.forEach { (level, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        onSelect(level)
                        open = false
                    },
                    trailingIcon = if (level == minLevel) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                )
            }
        }
    }
}

private fun LogEntry.raw(): String = "$time $pid $level $body"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: LogEntry,
    wrap: Boolean,
    expanded: Boolean,
    viewportWidth: Dp,
    bodyStyle: TextStyle,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onTag: (String) -> Unit,
) {
    val accent = levelColor(entry.level)
    val outline = MaterialTheme.colorScheme.outline
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val stripe = with(LocalDensity.current) { StripeWidth.toPx() }
    val tag by rememberUpdatedState(onTag)
    val line =
        remember(entry, accent, outline, onSurface, primary) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(entry.level) }
                append(' ')
                withStyle(SpanStyle(color = outline)) { append(entry.time) }
                append(' ')
                withStyle(
                    SpanStyle(background = accent.copy(alpha = PROCESS_TAG_ALPHA)),
                ) { append(" ${processNameForDisplay(entry.process)} ") }
                append("  ")
                withStyle(SpanStyle(color = onSurface)) {
                    val tweak = entry.tweak
                    val token = "tweak=$tweak"
                    val at = if (tweak == null) -1 else entry.body.indexOf(token)
                    if (tweak == null || at < 0) {
                        append(entry.body)
                    } else {
                        append(entry.body, 0, at)
                        withLink(LinkAnnotation.Clickable(tweak, TextLinkStyles(SpanStyle(color = primary))) { tag(tweak) }) {
                            append(token)
                        }
                        append(entry.body, at + token.length, entry.body.length)
                    }
                }
            }
        }
    val open = wrap || expanded
    Text(
        line,
        style = bodyStyle,
        softWrap = open,
        maxLines = if (open) Int.MAX_VALUE else 1,
        overflow = TextOverflow.Clip,
        modifier =
            Modifier
                .then(if (expanded && !wrap) Modifier.width(viewportWidth) else Modifier.fillMaxWidth())
                .combinedClickable(onClick = onToggle, onLongClick = onCopy)
                .drawBehind { drawRect(accent, size = Size(stripe, size.height)) }
                .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
    )
}

@Composable
private fun levelColor(level: Char): Color =
    when (level) {
        'E', 'F' -> MaterialTheme.colorScheme.error
        'W' -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = VERBOSE_LEVEL_ALPHA)
    }

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = AppText.tileSupporting,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
