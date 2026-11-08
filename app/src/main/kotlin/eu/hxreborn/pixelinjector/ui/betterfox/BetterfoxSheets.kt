package eu.hxreborn.pixelinjector.ui.betterfox

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.betterfox.BACKUP_SUFFIX
import eu.hxreborn.pixelinjector.betterfox.BetterfoxAbout
import eu.hxreborn.pixelinjector.betterfox.BetterfoxBackup
import eu.hxreborn.pixelinjector.betterfox.BetterfoxList
import eu.hxreborn.pixelinjector.betterfox.BetterfoxOverride
import eu.hxreborn.pixelinjector.betterfox.BetterfoxPref
import eu.hxreborn.pixelinjector.betterfox.formatPrefValue
import eu.hxreborn.pixelinjector.betterfox.isUsable
import eu.hxreborn.pixelinjector.ui.component.SectionHeader
import eu.hxreborn.pixelinjector.ui.component.SheetActions
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.component.StateChip
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.component.TileGap
import eu.hxreborn.pixelinjector.ui.component.TileText
import eu.hxreborn.pixelinjector.ui.component.tileShape
import eu.hxreborn.pixelinjector.ui.theme.AppText
import eu.hxreborn.pixelinjector.ui.util.LocalDateStyle
import java.time.ZoneId

private val SheetGutter = 16.dp
private val SheetBottom = 24.dp
private val SheetGap = 14.dp
private val ToggleHeight = 56.dp
private val LeadSize = 40.dp
private val LeadRadius = RoundedCornerShape(12.dp)
private val LinkShape = RoundedCornerShape(20.dp)
private val ArrowSize = 14.dp
private val ValueGap = 8.dp
private val ExplanationGap = 10.dp
private val MinFittedSize = 9.sp
private val LeadStart = 12.dp
private val radioRole = Modifier.semantics { role = Role.RadioButton }

private enum class ValueSource(
    val label: Int,
) {
    STOCK(R.string.betterfox_chip_stock),
    BETTERFOX(R.string.betterfox_mode_betterfox),
    CUSTOM(R.string.betterfox_chip_custom),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BetterfoxPrefSheet(
    list: BetterfoxList,
    entry: BetterfoxPref,
    override: BetterfoxOverride?,
    state: BetterfoxState,
    onOverrideChange: (BetterfoxOverride?) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val usable = override?.let { isUsable(entry, it) } ?: true
    val pick =
        when {
            override == BetterfoxOverride.Stock -> ValueSource.STOCK
            override is BetterfoxOverride.Custom && usable -> ValueSource.CUSTOM
            else -> ValueSource.BETTERFOX
        }
    val custom = (override as? BetterfoxOverride.Custom)?.value?.takeIf { usable }
    Column(
        Modifier.padding(start = SheetGutter, end = SheetGutter, bottom = SheetBottom).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SheetGap),
    ) {
        Column(Modifier.padding(horizontal = 8.dp)) {
            Text(entry.pref, style = AppText.tileTitle.copy(fontFamily = AppText.meta.fontFamily))
            Text(entry.note, style = AppText.sheetSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            val options = ValueSource.entries
            options.forEachIndexed { index, option ->
                val value =
                    when (option) {
                        ValueSource.STOCK -> formatPrefValue(entry.stock)
                        ValueSource.BETTERFOX -> formatPrefValue(entry.value)
                        ValueSource.CUSTOM -> custom?.let(::formatPrefValue)
                    }
                ToggleButton(
                    checked = pick == option,
                    onCheckedChange = {
                        if (pick == option) return@ToggleButton
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onOverrideChange(
                            when (option) {
                                ValueSource.STOCK -> BetterfoxOverride.Stock
                                ValueSource.BETTERFOX -> null
                                ValueSource.CUSTOM -> BetterfoxOverride.Custom(entry.value)
                            },
                        )
                    },
                    shapes =
                        when {
                            index == 0 -> {
                                ButtonGroupDefaults.connectedLeadingButtonShapes()
                            }

                            index == options.lastIndex -> {
                                ButtonGroupDefaults
                                    .connectedTrailingButtonShapes()
                            }

                            else -> {
                                ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                        },
                    modifier = Modifier.weight(1f).heightIn(min = ToggleHeight).then(radioRole),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FittedText(stringResource(option.label), AppText.button)
                        if (value != null) FittedText(value, AppText.meta)
                    }
                }
            }
        }
        if (pick == ValueSource.CUSTOM && custom != null) {
            CustomValue(entry, custom) { onOverrideChange(BetterfoxOverride.Custom(it)) }
        }
        val (source, current) = configuredValue(entry, override, state)
        Tile(shape = LinkShape) {
            TileText(stringResource(R.string.betterfox_current_value), formatPrefValue(current), supportingStyle = AppText.meta)
            SourceChip(source)
        }
        if (!entry.about.isEmpty) BetterfoxExplanation(entry.about, onOpenUrl)
        val url = list.sourceUrl(entry)
        Tile(shape = LinkShape, onClick = { onOpenUrl(url) }) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.betterfox_source), style = AppText.tileTitle)
                Text(url, style = AppText.meta, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CustomValue(
    entry: BetterfoxPref,
    current: Any,
    onChange: (Any) -> Unit,
) {
    if (entry.value is Boolean) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            listOf(true, false).forEachIndexed { index, option ->
                ToggleButton(
                    checked = current == option,
                    onCheckedChange = { if (current != option) onChange(option) },
                    shapes =
                        if (index == 0) {
                            ButtonGroupDefaults.connectedLeadingButtonShapes()
                        } else {
                            ButtonGroupDefaults.connectedTrailingButtonShapes()
                        },
                    modifier = Modifier.weight(1f).then(radioRole),
                ) { Text(option.toString(), style = AppText.button) }
            }
        }
        return
    }
    var draft by remember(entry.pref) { mutableStateOf(current.toString()) }
    val parsed: Any? = if (entry.value is Int) draft.trim().toIntOrNull() else draft
    val commit = { if (parsed != null && parsed != current) onChange(parsed) }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text(stringResource(R.string.betterfox_custom_value)) },
        isError = parsed == null,
        singleLine = true,
        textStyle = AppText.tileTitle.copy(fontFamily = AppText.meta.fontFamily),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (entry.value is Int) KeyboardType.Number else KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) commit() },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BetterfoxBackupSheet(
    state: BetterfoxState,
    list: BetterfoxList,
    suggestedName: String,
    onPickFolder: (Uri) -> Unit,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(suggestedName) }
    var saving by remember { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(onPickFolder) }
    val valid = name.endsWith(BACKUP_SUFFIX) && '/' !in name && name.length > 8
    Column(
        Modifier.padding(start = SheetGutter, end = SheetGutter, bottom = SheetBottom).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SheetGap),
    ) {
        SheetHeader(stringResource(R.string.betterfox_menu_backup), stringResource(R.string.betterfox_backup_desc))
        Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
            val rows =
                listOf(
                    stringResource(R.string.betterfox_overrides) to state.overrides.size.toString(),
                    stringResource(R.string.betterfox_info_list) to stringResource(R.string.betterfox_list_name, list.betterfox),
                )
            rows.forEachIndexed { index, (title, value) ->
                Tile(shape = tileShape(rows.size, index)) {
                    TileText(title)
                    TileValue(value, AppText.tileSupporting)
                }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.trim() },
            label = { Text(stringResource(R.string.betterfox_file_name)) },
            isError = !valid,
            singleLine = true,
            textStyle = AppText.meta,
            modifier = Modifier.fillMaxWidth(),
        )
        Tile(shape = LinkShape, startPadding = LeadStart) {
            LeadIcon(Icons.Outlined.Folder)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.betterfox_save_to), style = AppText.tileTitle)
                Text(state.folder, style = AppText.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { folderPicker.launch(null) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.betterfox_change))
            }
        }
        SheetActions {
            TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) }
            LoadingButton(
                label = stringResource(R.string.betterfox_save),
                busy = saving,
                enabled = valid,
            ) {
                saving = true
                onSave(name)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingButton(
    label: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = enabled && !busy, shapes = ButtonDefaults.shapes()) {
        AnimatedContent(targetState = busy, label = "morph") { running ->
            if (running) {
                LoadingIndicator(Modifier.size(ButtonDefaults.IconSize * 1.5f), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(label)
            }
        }
    }
}

@Composable
internal fun LeadIcon(
    icon: ImageVector,
    selected: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = LeadRadius,
        color = if (selected) scheme.primary else scheme.surfaceContainerHighest,
        contentColor = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier.size(LeadSize),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BetterfoxRestoreSheet(
    backups: List<BackupEntry>,
    onChooseFile: (Uri) -> Unit,
    onNext: (BackupEntry) -> Unit,
    onCancel: () -> Unit,
) {
    val usable = backups.filter { it.backup != null }
    var selected by rememberSaveable { mutableStateOf(usable.firstOrNull()?.uri?.toString()) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onChooseFile) }
    val dateStyle = LocalDateStyle.current
    Column(
        Modifier.padding(start = SheetGutter, end = SheetGutter, bottom = SheetBottom).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SheetGap),
    ) {
        SheetHeader(stringResource(R.string.betterfox_menu_restore), stringResource(R.string.betterfox_restore_desc))
        Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
            val count = usable.size + 1
            usable.forEachIndexed { index, entry ->
                val backup = checkNotNull(entry.backup)
                val isSelected = selected == entry.uri.toString()
                Tile(
                    shape = tileShape(count, index),
                    startPadding = LeadStart,
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    onClick = { selected = entry.uri.toString() },
                    modifier = radioRole,
                ) {
                    LeadIcon(Icons.Outlined.Description, selected = isSelected)
                    Column(Modifier.weight(1f)) {
                        Text(
                            dateStyle.formatDateTime(backup.createdMillis()),
                            style = AppText.tileTitle,
                        )
                        Text(
                            if (backup.overrides.isEmpty()) {
                                stringResource(R.string.betterfox_backup_summary_none, backup.betterfox)
                            } else {
                                pluralStringResource(
                                    R.plurals.betterfox_backup_summary,
                                    backup.overrides.size,
                                    backup.overrides.size,
                                    backup.betterfox,
                                )
                            },
                            style = AppText.tileSupporting,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(entry.name, style = AppText.chip, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            Tile(shape = tileShape(count, count - 1), startPadding = LeadStart, onClick = { filePicker.launch(arrayOf("*/*")) }) {
                LeadIcon(Icons.Outlined.FileOpen)
                TileText(stringResource(R.string.betterfox_choose_file))
            }
        }
        SheetActions {
            TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) }
            Button(
                onClick = { usable.firstOrNull { it.uri.toString() == selected }?.let(onNext) },
                enabled = selected != null && usable.any { it.uri.toString() == selected },
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.betterfox_menu_restore)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BetterfoxRestoreConfirmSheet(
    list: BetterfoxList,
    entry: BackupEntry,
    changes: List<OverrideChange>,
    groups: List<Pair<String, Boolean>>,
    browser: String,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
) {
    val backup = checkNotNull(entry.backup)
    val dateStyle = LocalDateStyle.current
    var restoring by remember { mutableStateOf(false) }
    Column(
        Modifier.padding(start = SheetGutter, end = SheetGutter, bottom = SheetBottom).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SheetGap),
    ) {
        SheetHeader(
            stringResource(R.string.betterfox_restore_this),
            dateStyle.formatDateTime(backup.createdMillis()),
        )
        Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
            SectionHeader(stringResource(R.string.betterfox_what_changes))
            val count = changes.size + groups.size
            if (count == 0) {
                Text(
                    stringResource(R.string.betterfox_nothing_changes),
                    style = AppText.tileSupporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            groups.forEachIndexed { index, (group, on) ->
                Tile(shape = tileShape(count, index)) {
                    TileText(
                        stringResource(R.string.betterfox_use_group, group),
                        stringResource(if (on) R.string.betterfox_turns_on else R.string.betterfox_turns_off),
                    )
                }
            }
            changes.forEachIndexed { index, change ->
                Tile(shape = tileShape(count, groups.size + index)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(change.pref, style = AppText.meta)
                        ChangeLine(list, change)
                    }
                }
            }
        }
        Text(
            stringResource(R.string.betterfox_restore_restarts, browser),
            style = AppText.tileSupporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        SheetActions {
            TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) }
            LoadingButton(stringResource(R.string.betterfox_menu_restore), busy = restoring, enabled = true) {
                restoring = true
                onRestore()
            }
        }
    }
}

@Composable
private fun ChangeLine(
    list: BetterfoxList,
    change: OverrideChange,
) {
    val scheme = MaterialTheme.colorScheme
    val to = change.to
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ValueGap), itemVerticalAlignment = Alignment.CenterVertically) {
        if (to == BetterfoxOverride.Stock) {
            Text(displayValue(list, change.pref, to), style = AppText.meta, color = scheme.onSurfaceVariant)
        } else {
            Text(displayValue(list, change.pref, change.from), style = AppText.meta, color = scheme.onSurfaceVariant)
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = scheme.outline,
                modifier = Modifier.size(ArrowSize),
            )
            Text(
                displayValue(list, change.pref, to),
                style = AppText.meta.copy(fontWeight = AppText.chip.fontWeight),
                color = if (to == null) scheme.primary else scheme.tertiary,
            )
        }
        val chip =
            when (to) {
                null -> R.string.betterfox_mode_betterfox
                BetterfoxOverride.Stock -> R.string.betterfox_chip_stock
                is BetterfoxOverride.Custom -> R.string.betterfox_chip_custom
            }
        StateChip(stringResource(chip), scheme.tertiaryContainer, scheme.onTertiaryContainer)
    }
}

private fun BetterfoxBackup.createdMillis(): Long = created.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
private fun RowScope.TileValue(
    value: String,
    style: TextStyle,
) {
    Text(
        value,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(1f, fill = false),
    )
}

@Composable
private fun FittedText(
    text: String,
    style: TextStyle,
) {
    val color = LocalContentColor.current
    BasicText(
        text,
        style = style,
        color = { color },
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = MinFittedSize, maxFontSize = style.fontSize),
    )
}

@Composable
private fun BetterfoxExplanation(
    about: BetterfoxAbout,
    onOpenUrl: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val hasText = about.text != null || about.inline != null || about.tags.isNotEmpty()
    if (hasText) {
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(ExplanationGap)) {
            about.text?.let { Text(it, style = AppText.sheetSubtitle, color = scheme.onSurface) }
            about.inline?.let { Text(it, style = AppText.tileSupporting, color = scheme.onSurfaceVariant) }
            about.tags.forEach { tag ->
                val warning = tag.tag == "WARNING"
                Row(horizontalArrangement = Arrangement.spacedBy(ValueGap)) {
                    StateChip(
                        tagLabel(tag.tag),
                        if (warning) scheme.errorContainer else scheme.secondaryContainer,
                        if (warning) scheme.onErrorContainer else scheme.onSecondaryContainer,
                    )
                    Text(tag.text, style = AppText.tileSupporting, color = if (warning) scheme.error else scheme.onSurfaceVariant)
                }
            }
        }
    }
    if (about.links.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
        SectionHeader(stringResource(R.string.betterfox_sources))
        about.links.forEachIndexed { index, link ->
            val uri = Uri.parse(link)
            Tile(shape = tileShape(about.links.size, index), onClick = { onOpenUrl(link) }) {
                val host = uri.host.orEmpty().removePrefix("www.")
                TileText(linkTitle(uri) ?: host, link.removePrefix("https://").removePrefix("http://"), supportingStyle = AppText.meta)
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun tagLabel(tag: String): String =
    when (tag) {
        "WARNING" -> stringResource(R.string.betterfox_tag_warning)
        "SETTING" -> stringResource(R.string.betterfox_tag_setting)
        "TEST" -> stringResource(R.string.betterfox_tag_test)
        "ALTERNATIVE" -> stringResource(R.string.betterfox_tag_alternative)
        "TIP" -> stringResource(R.string.betterfox_tag_tip)
        "NOTE" -> stringResource(R.string.betterfox_tag_note)
        else -> tag.lowercase().replace('-', ' ').replaceFirstChar { it.uppercase() }
    }

private fun linkTitle(uri: Uri): String? =
    uri.pathSegments
        .maxByOrNull { it.length }
        ?.replace(Regex("\\.(html?|php|aspx?)$"), "")
        ?.replace('_', ' ')
        ?.replace('-', ' ')
        ?.takeIf { it.contains(' ') && it.any(Char::isLetter) }
        ?.replaceFirstChar { it.uppercase() }
