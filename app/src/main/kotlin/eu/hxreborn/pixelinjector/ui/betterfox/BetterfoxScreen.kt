package eu.hxreborn.pixelinjector.ui.betterfox

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.betterfox.BetterfoxList
import eu.hxreborn.pixelinjector.betterfox.BetterfoxOverride
import eu.hxreborn.pixelinjector.betterfox.BetterfoxPref
import eu.hxreborn.pixelinjector.betterfox.formatPrefValue
import eu.hxreborn.pixelinjector.betterfox.isUsable
import eu.hxreborn.pixelinjector.betterfox.resolveBetterfox
import eu.hxreborn.pixelinjector.support.BetterfoxUpdateState
import eu.hxreborn.pixelinjector.ui.component.DetailScaffold
import eu.hxreborn.pixelinjector.ui.component.SectionGap
import eu.hxreborn.pixelinjector.ui.component.SectionHeader
import eu.hxreborn.pixelinjector.ui.component.StateChip
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.component.TileGap
import eu.hxreborn.pixelinjector.ui.component.TileText
import eu.hxreborn.pixelinjector.ui.component.readableGutter
import eu.hxreborn.pixelinjector.ui.component.sectionHeader
import eu.hxreborn.pixelinjector.ui.component.tileShape
import eu.hxreborn.pixelinjector.ui.theme.AppText
import eu.hxreborn.pixelinjector.ui.util.verticalScrollbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

private const val FLASH_STAGGER_MS = 120L
private val RowEndPadding = 4.dp
private val LineLabelWidth = 64.dp
private val ValueGap = 8.dp
private val CardRadius = RoundedCornerShape(20.dp)
private val ListBottom = 24.dp

class BetterfoxActions(
    val onBack: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onPref: (String) -> Unit,
    val onEnabled: (Boolean) -> Unit,
    val onClearOverrides: () -> Unit,
    val onCheck: () -> Unit,
    val onBackUp: () -> Unit,
    val onRestore: () -> Unit,
    val onResetAll: () -> Unit,
    val onShare: (Uri, String) -> Unit,
    val onUndoRestore: () -> Unit,
    val onUndoClear: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BetterfoxScreen(
    state: BetterfoxState,
    messages: SharedFlow<BetterfoxMessage>,
    actions: BetterfoxActions,
    scrollTo: String? = null,
    openMenu: Boolean = false,
    openInfo: Boolean = false,
) {
    val snackbar = remember { SnackbarHostState() }
    var changes by remember { mutableStateOf<List<String>?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var showInfo by rememberSaveable(openInfo) { mutableStateOf(openInfo) }
    val context = LocalContext.current
    val currentActions by rememberUpdatedState(actions)
    val scope = rememberCoroutineScope()
    LaunchedEffect(messages) {
        messages.collect { message ->
            snackbar.currentSnackbarData?.dismiss()
            val text = messageText(context, message)
            val action = messageAction(context, message)
            val duration =
                if (message == BetterfoxMessage.Update(BetterfoxUpdateState.Checking)) {
                    SnackbarDuration.Indefinite
                } else {
                    SnackbarDuration.Short
                }
            scope.launch {
                val result = snackbar.showSnackbar(text, action, withDismissAction = false, duration = duration)
                if (result != SnackbarResult.ActionPerformed) return@launch
                when (message) {
                    is BetterfoxMessage.Saved -> {
                        currentActions.onShare(message.uri, message.name)
                    }

                    is BetterfoxMessage.Restored -> {
                        currentActions.onUndoRestore()
                    }

                    is BetterfoxMessage.Cleared -> {
                        currentActions.onUndoClear()
                    }

                    is BetterfoxMessage.Update -> {
                        when (val update = message.state) {
                            is BetterfoxUpdateState.Updated -> {
                                changes = update.changed
                            }

                            BetterfoxUpdateState.Unreachable, BetterfoxUpdateState.Rejected, is BetterfoxUpdateState.Missing -> {
                                currentActions.onCheck()
                            }

                            else -> {
                                Unit
                            }
                        }
                    }

                    else -> {
                        Unit
                    }
                }
            }
        }
    }
    val gutter = readableGutter()
    Box(Modifier.fillMaxSize()) {
        DetailScaffold(
            title = stringResource(R.string.betterfox_title),
            onBack = actions.onBack,
            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = ListBottom),
            actions = {
                IconButton(onClick = { showInfo = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.betterfox_about))
                }
                BetterfoxMenu(
                    initiallyOpen = openMenu,
                    onCheck = actions.onCheck,
                    onBackUp = actions.onBackUp,
                    onRestore = actions.onRestore,
                    onResetAll = { confirmReset = true },
                )
            },
        ) { padding ->
            val list = state.list
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                return@DetailScaffold
            }
            val listState = rememberLazyListState()
            LaunchedEffect(scrollTo, list) { scrollTo?.let { itemIndex(state, list, it) }?.let { listState.scrollToItem(it) } }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).verticalScrollbar(listState),
                contentPadding = padding,
            ) {
                item(key = "enabled") {
                    MasterSwitch(state.enabled, actions.onEnabled)
                    Spacer(Modifier.height(SectionGap))
                }
                notices(state, list, actions)
                for (group in list.groups) {
                    groupSection(group, list, state, actions)
                }
                overridesSection(list, state, actions)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) { data ->
            Snackbar(data)
        }
    }
    changes?.let { lines ->
        AlertDialog(
            onDismissRequest = { changes = null },
            title = { Text(stringResource(R.string.betterfox_changes)) },
            text = { Text(lines.joinToString("\n").ifEmpty { stringResource(R.string.betterfox_no_pref_changes) }, style = AppText.meta) },
            confirmButton = { TextButton(onClick = { changes = null }) { Text(stringResource(R.string.close)) } },
        )
    }
    val infoList = state.list
    if (showInfo && infoList != null) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            title = { Text(stringResource(R.string.betterfox_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.betterfox_about_body,
                        state.browser?.label ?: stringResource(R.string.betterfox_browser),
                        infoList.prefs.size,
                        infoList.betterfox,
                        infoList.gecko,
                    ),
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = { actions.onOpenUrl(infoList.treeUrl) },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_github),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.betterfox_open_github))
                }
            },
            dismissButton = { TextButton(onClick = { showInfo = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.betterfox_reset_title)) },
            text = {
                Text(
                    stringResource(R.string.betterfox_reset_text, state.browser?.label ?: stringResource(R.string.betterfox_browser)),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    actions.onResetAll()
                }) { Text(stringResource(R.string.betterfox_reset_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun messageText(
    context: Context,
    message: BetterfoxMessage,
): String =
    when (message) {
        is BetterfoxMessage.Saved -> {
            context.getString(R.string.betterfox_saved, message.folder)
        }

        is BetterfoxMessage.Restored -> {
            context.resources.getQuantityString(R.plurals.betterfox_restored, message.overrides, message.overrides, message.browser)
        }

        is BetterfoxMessage.Cleared -> {
            context.resources.getQuantityString(R.plurals.betterfox_cleared, message.count, message.count)
        }

        BetterfoxMessage.Reset -> {
            context.getString(R.string.betterfox_reset_done)
        }

        is BetterfoxMessage.Update -> {
            when (val update = message.state) {
                BetterfoxUpdateState.Checking -> context.getString(R.string.betterfox_update_checking)
                BetterfoxUpdateState.Current -> context.getString(R.string.betterfox_update_current)
                is BetterfoxUpdateState.Updated -> context.getString(R.string.betterfox_update_updated, update.betterfox, update.revision)
                BetterfoxUpdateState.Unreachable -> context.getString(R.string.betterfox_update_unreachable)
                is BetterfoxUpdateState.Missing -> context.getString(R.string.betterfox_update_missing)
                BetterfoxUpdateState.Rejected -> context.getString(R.string.betterfox_update_rejected)
                BetterfoxUpdateState.Idle -> ""
            }
        }
    }

private fun messageAction(
    context: Context,
    message: BetterfoxMessage,
): String? =
    when (message) {
        is BetterfoxMessage.Saved -> {
            context.getString(R.string.betterfox_share)
        }

        is BetterfoxMessage.Restored, is BetterfoxMessage.Cleared -> {
            context.getString(R.string.undo)
        }

        is BetterfoxMessage.Update -> {
            when (message.state) {
                is BetterfoxUpdateState.Updated -> {
                    context.getString(R.string.betterfox_see_changes)
                }

                BetterfoxUpdateState.Unreachable, BetterfoxUpdateState.Rejected, is BetterfoxUpdateState.Missing -> {
                    context.getString(R.string.betterfox_retry)
                }

                else -> {
                    null
                }
            }
        }

        else -> {
            null
        }
    }

@Composable
private fun BetterfoxMenu(
    initiallyOpen: Boolean,
    onCheck: () -> Unit,
    onBackUp: () -> Unit,
    onRestore: () -> Unit,
    onResetAll: () -> Unit,
) {
    var open by remember(initiallyOpen) { mutableStateOf(initiallyOpen) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.betterfox_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun pick(action: () -> Unit): () -> Unit =
                {
                    open = false
                    action()
                }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.betterfox_menu_check)) },
                leadingIcon = { Icon(Icons.Outlined.Sync, contentDescription = null) },
                onClick = pick(onCheck),
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.betterfox_menu_backup)) },
                leadingIcon = { Icon(Icons.Outlined.Backup, contentDescription = null) },
                onClick = pick(onBackUp),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.betterfox_menu_restore)) },
                leadingIcon = { Icon(Icons.Outlined.SettingsBackupRestore, contentDescription = null) },
                onClick = pick(onRestore),
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.betterfox_menu_reset)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null) },
                onClick = pick(onResetAll),
            )
        }
    }
}

private fun LazyListScope.notices(
    state: BetterfoxState,
    list: BetterfoxList,
    actions: BetterfoxActions,
) {
    state.newerGecko?.let { browser ->
        item(key = "gecko") {
            Tile(shape = CardRadius, color = MaterialTheme.colorScheme.errorContainer) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                TileText(
                    stringResource(R.string.betterfox_gecko_newer, browser.label, browser.gecko.orEmpty()),
                    stringResource(R.string.betterfox_gecko_list, list.gecko),
                    supportingColor = MaterialTheme.colorScheme.onErrorContainer,
                )
                FilledTonalButton(
                    onClick = actions.onCheck,
                    shapes = ButtonDefaults.shapes(),
                    colors =
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) { Text(stringResource(R.string.betterfox_check)) }
            }
            Spacer(Modifier.height(SectionGap))
        }
    }
}

private fun LazyListScope.groupSection(
    group: String,
    list: BetterfoxList,
    state: BetterfoxState,
    actions: BetterfoxActions,
) {
    val prefs = list.prefs.filter { it.group == group }
    val count = prefs.size
    sectionHeader(group)
    itemsIndexed(prefs, key = { _, entry -> entry.pref }) { index, entry ->
        PrefRow(
            entry = entry,
            override = state.overrides[entry.pref],
            state = state,
            flashDelay = state.flash.prefs.indexOf(entry.pref),
            flashToken = state.flash.token,
            shape = tileShape(count, index),
            onClick = { actions.onPref(entry.pref) },
        )
        if (index < prefs.lastIndex) Spacer(Modifier.height(TileGap))
    }
    item(key = "gap:$group") { Spacer(Modifier.height(SectionGap)) }
}

private fun LazyListScope.overridesSection(
    list: BetterfoxList,
    state: BetterfoxState,
    actions: BetterfoxActions,
) {
    val overridden = state.overrides.keys.sorted()
    if (overridden.isEmpty()) return
    titledSection(R.string.betterfox_your_overrides)
    val count = overridden.size + 1
    itemsIndexed(overridden, key = { _, pref -> "override:$pref" }) { index, pref ->
        val entry = list.pref(pref)
        val override = state.overrides.getValue(pref)
        OverrideRow(pref, entry, override, state, tileShape(count, index)) { actions.onPref(pref) }
        Spacer(Modifier.height(TileGap))
    }
    item(key = "clear") {
        Tile(shape = tileShape(count, count - 1), onClick = actions.onClearOverrides) {
            Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            TileText(stringResource(R.string.betterfox_clear_overrides))
        }
    }
}

private fun LazyListScope.titledSection(title: Int) {
    item(key = "section:$title") {
        SectionHeader(stringResource(title))
    }
}

@Composable
private fun MasterSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Tile(
        shape = CardRadius,
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        checked = checked,
        onCheckedChange = {
            haptic.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onCheckedChange(it)
        },
        modifier = Modifier.semantics { role = Role.Switch },
    ) {
        TileText(stringResource(R.string.betterfox_use))
        Switch(
            checked = checked,
            onCheckedChange = null,
            thumbContent = {
                Icon(
                    if (checked) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
        )
    }
}

@Composable
private fun PrefRow(
    entry: BetterfoxPref,
    override: BetterfoxOverride?,
    state: BetterfoxState,
    flashDelay: Int,
    flashToken: Long,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val base = MaterialTheme.colorScheme.surfaceContainer
    val highlight = MaterialTheme.colorScheme.tertiaryContainer
    val color = remember { Animatable(base) }
    val flashSpec = MaterialTheme.motionScheme.slowEffectsSpec<Color>()
    LaunchedEffect(flashToken, base) {
        if (flashDelay < 0) {
            color.snapTo(base)
            return@LaunchedEffect
        }
        delay(flashDelay * FLASH_STAGGER_MS)
        color.animateTo(highlight, flashSpec)
        color.animateTo(base, flashSpec)
    }
    Tile(
        shape = shape,
        color = color.value,
        endPadding = RowEndPadding,
        onClick = onClick,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.pref, style = AppText.meta, color = MaterialTheme.colorScheme.onSurface)
            Text(entry.note, style = AppText.tileSupporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ValueLine(entry, override, state)
        }
        IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = stringResource(R.string.betterfox_about_pref, entry.pref),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OverrideRow(
    pref: String,
    entry: BetterfoxPref?,
    override: BetterfoxOverride,
    state: BetterfoxState,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    Tile(shape = shape, endPadding = RowEndPadding, onClick = onClick.takeIf { entry != null }) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(pref, style = AppText.meta, color = MaterialTheme.colorScheme.onSurface)
            if (entry != null && isUsable(entry, override)) {
                ValueLine(entry, override, state)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ValueGap), itemVerticalAlignment = Alignment.CenterVertically) {
                    if (override is BetterfoxOverride.Custom) {
                        Text(formatPrefValue(override.value), style = AppText.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StateChip(
                        stringResource(R.string.betterfox_chip_unused),
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        if (entry != null) {
            IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.betterfox_edit_pref, pref),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

enum class Source { DEFAULT, CUSTOM, UNUSED, BETTERFOX }

fun configuredValue(
    entry: BetterfoxPref,
    override: BetterfoxOverride?,
    state: BetterfoxState,
): Pair<Source, Any?> {
    val custom = (override as? BetterfoxOverride.Custom)?.value
    return when {
        !state.enabled || entry.group in state.offGroups || override == BetterfoxOverride.Stock -> Source.DEFAULT to entry.stock
        override != null && !isUsable(entry, override) -> Source.UNUSED to entry.value
        custom != null -> Source.CUSTOM to custom
        else -> Source.BETTERFOX to entry.value
    }
}

@Composable
private fun ValueLine(
    entry: BetterfoxPref,
    override: BetterfoxOverride?,
    state: BetterfoxState,
) {
    val scheme = MaterialTheme.colorScheme
    val (source, shown) = configuredValue(entry, override, state)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ValueGap), itemVerticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.betterfox_in_use),
                style = AppText.tileSupporting,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.width(LineLabelWidth),
            )
            Text(formatPrefValue(shown), style = AppText.meta.copy(fontWeight = FontWeight.Bold), color = scheme.onSurface)
            SourceChip(source)
        }
        if (source != Source.BETTERFOX && entry.value != shown) {
            Row(horizontalArrangement = Arrangement.spacedBy(ValueGap), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.betterfox_mode_betterfox),
                    style = AppText.tileSupporting,
                    color = scheme.outline,
                    modifier = Modifier.width(LineLabelWidth),
                )
                Text(
                    stringResource(R.string.betterfox_not_applied, formatPrefValue(entry.value)),
                    style = AppText.meta,
                    color = scheme.outline,
                )
            }
        }
    }
}

@Composable
fun SourceChip(source: Source) {
    val scheme = MaterialTheme.colorScheme
    when (source) {
        Source.DEFAULT -> {
            StateChip(stringResource(R.string.betterfox_chip_gecko), scheme.tertiaryContainer, scheme.onTertiaryContainer)
        }

        Source.CUSTOM -> {
            StateChip(stringResource(R.string.betterfox_chip_custom), scheme.tertiaryContainer, scheme.onTertiaryContainer)
        }

        Source.UNUSED -> {
            StateChip(stringResource(R.string.betterfox_chip_unused), scheme.errorContainer, scheme.onErrorContainer)
        }

        Source.BETTERFOX -> {
            StateChip(stringResource(R.string.betterfox_mode_betterfox), scheme.primaryContainer, scheme.onPrimaryContainer)
        }
    }
}

fun shareIntent(
    context: Context,
    uri: Uri,
    name: String,
): Intent =
    Intent.createChooser(
        Intent(Intent.ACTION_SEND)
            .setType("text/javascript")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newUri(context.contentResolver, name, uri) },
        name,
    )

private fun itemIndex(
    state: BetterfoxState,
    list: BetterfoxList,
    pref: String,
): Int? {
    var index = 1 + if (state.newerGecko != null) 1 else 0
    for (group in list.groups) {
        val prefs = list.prefs.filter { it.group == group }
        val position = prefs.indexOfFirst { it.pref == pref }
        if (position >= 0) return index + 1 + position
        index += prefs.size + 2
    }
    return if (pref == "overrides") index else null
}
