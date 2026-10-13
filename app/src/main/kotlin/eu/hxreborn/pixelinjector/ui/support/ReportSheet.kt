package eu.hxreborn.pixelinjector.ui.support

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.support.ReportConfig
import eu.hxreborn.pixelinjector.support.ReportPreview
import eu.hxreborn.pixelinjector.support.SentReport
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.dashboard.TweakGroup
import eu.hxreborn.pixelinjector.ui.dashboard.tweakUis
import eu.hxreborn.pixelinjector.ui.theme.AppText

private const val MESSAGE_MAX = 500
private val SheetPadding = 16.dp
private val BlockGap = 16.dp
private val DisclosureRadius = 16.dp
private val ResultShapeSize = 64.dp
private val ResultIconSize = 32.dp
private val ExactTextMaxHeight = 220.dp
private val SendButtonHeight = 56.dp

sealed interface ReportPhase {
    data object Compose : ReportPhase

    data object Sending : ReportPhase

    data class Sent(
        val report: SentReport,
    ) : ReportPhase

    data class Waiting(
        val report: SentReport,
    ) : ReportPhase

    data class Rejected(
        val reason: String,
    ) : ReportPhase
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReportSheet(
    initialTweak: String?,
    replyTo: String?,
    preview: ReportPreview?,
    phase: ReportPhase,
    onSend: (message: String, tweak: String?) -> Unit,
    onTryNow: () -> Unit,
    onViewReports: () -> Unit,
    onClose: () -> Unit,
) {
    var message by rememberSaveable { mutableStateOf("") }
    var tweak by rememberSaveable { mutableStateOf(initialTweak) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var exact by rememberSaveable { mutableStateOf(false) }

    when (phase) {
        is ReportPhase.Sent -> {
            SentContent(onViewReports, onClose)
            return
        }

        else -> {}
    }
    Column(Modifier.padding(horizontal = SheetPadding)) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(BlockGap),
        ) {
            SheetHeader(stringResource(if (replyTo != null) R.string.report_reply_title else R.string.send_report))
            when (phase) {
                is ReportPhase.Waiting -> {
                    Notice(Icons.Outlined.Schedule, stringResource(R.string.report_waiting, ReportConfig.host), error = false)
                }

                is ReportPhase.Rejected -> {
                    Notice(Icons.Outlined.ErrorOutline, stringResource(R.string.report_rejected, phase.reason), error = true)
                }

                else -> {}
            }
            Column {
                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= MESSAGE_MAX) message = it },
                    label = { Text(stringResource(if (replyTo != null) R.string.report_your_answer else R.string.report_what_happened)) },
                    minLines = 3,
                    enabled = phase == ReportPhase.Compose || phase is ReportPhase.Rejected,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Row {
                            Text(stringResource(R.string.report_optional), Modifier.weight(1f))
                            Text("${message.length}/$MESSAGE_MAX")
                        }
                    },
                )
            }
            if (replyTo == null) TweakField(tweak, preview, onSelect = { tweak = it })
            Disclosure(preview, expanded, exact, messageLength = message.length, onToggle = { expanded = !expanded }, onExact = {
                exact =
                    !exact
            })
        }
        Footer(phase, ready = preview != null, onSend = { onSend(message.trim(), tweak) }, onTryNow = onTryNow, onClose = onClose)
    }
}

@Composable
private fun Notice(
    icon: ImageVector,
    text: String,
    error: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(DisclosureRadius),
        color = if (error) scheme.errorContainer else scheme.secondaryContainer,
        contentColor = if (error) scheme.onErrorContainer else scheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TweakField(
    tweak: String?,
    preview: ReportPreview?,
    onSelect: (String?) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val failed =
        preview
            ?.facts
            ?.missing
            ?.map { it.tweak }
            .orEmpty()
            .toSet()
    val title =
        tweak?.let { key -> tweakUis.firstOrNull { it.key == key }?.let { stringResource(it.title) } ?: key }
            ?: stringResource(R.string.report_tweak_none)
    val groups =
        listOf(
            TweakGroup.SYSTEM_UI to R.string.scope_systemui,
            TweakGroup.SYSTEM to R.string.scope_system,
            TweakGroup.LAUNCHER to R.string.scope_launcher,
            TweakGroup.GBOARD to R.string.scope_gboard,
            TweakGroup.DIALER to R.string.scope_dialer,
            TweakGroup.FILES to R.string.scope_files,
            TweakGroup.KDE_CONNECT to R.string.scope_kde_connect,
            TweakGroup.GITHUB to R.string.scope_github,
        )
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = title,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.report_tweak)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            supportingText = { Text(stringResource(R.string.report_tweak_help)) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.exposedDropdownSize()) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.report_tweak_none)) },
                onClick = {
                    onSelect(null)
                    open = false
                },
                trailingIcon = { if (tweak == null) Icon(Icons.Outlined.Check, contentDescription = null) },
            )
            groups.forEach { (group, label) ->
                val items = tweakUis.filter { it.group == group }
                if (items.isEmpty()) return@forEach
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                items.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(stringResource(item.title))
                                if (item.key in failed) {
                                    Text(
                                        stringResource(R.string.report_target_not_found),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                        leadingIcon =
                            if (item.key in
                                failed
                            ) {
                                ({ Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error) })
                            } else {
                                null
                            },
                        trailingIcon = { if (tweak == item.key) Icon(Icons.Outlined.Check, contentDescription = null) },
                        onClick = {
                            onSelect(item.key)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Disclosure(
    preview: ReportPreview?,
    expanded: Boolean,
    exact: Boolean,
    messageLength: Int,
    onToggle: () -> Unit,
    onExact: () -> Unit,
) {
    val noLog = preview?.facts?.rootDenied == true
    Surface(
        shape = RoundedCornerShape(DisclosureRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                Modifier.padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (noLog) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                } else {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.report_what_gets_sent), style = AppText.tileTitle)
                    Text(
                        stringResource(if (noLog) R.string.report_no_log else R.string.report_sent_summary),
                        style = AppText.tileSupporting,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val toggleState = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
                IconButton(onClick = onToggle, modifier = Modifier.semantics { stateDescription = toggleState }) {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(R.string.report_what_gets_sent),
                    )
                }
            }
            AnimatedVisibility(
                expanded,
                enter =
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit =
                    shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (exact && preview != null) {
                        ExactText(preview)
                    } else {
                        FieldGroup(
                            stringResource(R.string.report_private_group),
                            listOf(
                                stringResource(R.string.report_field_log) to
                                    if (noLog ||
                                        preview == null
                                    ) {
                                        stringResource(R.string.report_none)
                                    } else {
                                        pluralStringResource(R.plurals.report_lines, preview.logLineCount, preview.logLineCount)
                                    },
                                stringResource(R.string.report_field_message) to
                                    pluralStringResource(R.plurals.report_characters, messageLength, messageLength),
                                stringResource(R.string.report_field_versions) to stringResource(R.string.report_versions_value),
                            ),
                        )
                        val facts = preview?.facts
                        FieldGroup(
                            stringResource(R.string.report_public_group),
                            listOf(
                                stringResource(R.string.report_field_app) to BuildConfig.VERSION_NAME,
                                stringResource(R.string.report_field_phone) to "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
                                stringResource(R.string.report_field_build) to Build.ID,
                                stringResource(R.string.report_field_fingerprint) to Build.FINGERPRINT,
                                stringResource(R.string.report_field_android) to
                                    "${Build.VERSION.RELEASE} patch ${Build.VERSION.SECURITY_PATCH}",
                                stringResource(R.string.report_field_root) to
                                    stringResource(
                                        if (facts?.rootDenied ==
                                            true
                                        ) {
                                            R.string.report_root_denied
                                        } else {
                                            R.string.report_root_granted
                                        },
                                    ),
                                stringResource(R.string.report_field_tweaks) to stringResource(R.string.report_tweaks_value),
                                stringResource(R.string.report_field_framework) to
                                    (facts?.framework?.let { "${it.name} ${it.version}" } ?: stringResource(R.string.report_none)),
                                stringResource(R.string.report_field_failed) to
                                    (
                                        facts
                                            ?.missing
                                            ?.map { m ->
                                                tweakUis.firstOrNull { it.key == m.tweak }?.let { stringResource(it.title) }
                                                    ?: m.tweak
                                            }?.joinToString("\n")
                                            ?.ifEmpty { null } ?: stringResource(R.string.report_none)
                                    ),
                            ),
                        )
                    }
                    if (preview != null && preview.logLineCount > 0) {
                        TextButton(onClick = onExact, modifier = Modifier.offset(x = (-12).dp)) {
                            Text(stringResource(if (exact) R.string.report_hide_exact else R.string.report_show_exact))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldGroup(
    title: String,
    fields: List<Pair<String, String>>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        fields.forEach { (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(112.dp),
                )
                Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExactText(preview: ReportPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            pluralStringResource(R.plurals.report_exact_body, preview.hidden, preview.hidden),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = ExactTextMaxHeight)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
        ) {
            SelectionContainer { Text(preview.log.takeLast(EXACT_TAIL), style = AppText.console) }
        }
    }
}

private const val EXACT_TAIL = 20_000

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColumnScope.Footer(
    phase: ReportPhase,
    ready: Boolean,
    onSend: () -> Unit,
    onTryNow: () -> Unit,
    onClose: () -> Unit,
) {
    HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Column(Modifier.padding(top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                stringResource(R.string.report_consent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (phase) {
            ReportPhase.Compose, ReportPhase.Sending, is ReportPhase.Rejected -> {
                val sending = phase == ReportPhase.Sending
                Button(
                    onClick = onSend,
                    enabled = !sending && ready,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = SendButtonHeight),
                ) {
                    if (sending) {
                        LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.report_sending), style = AppText.button)
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.report_send), style = AppText.button)
                    }
                }
            }

            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onClose,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.done)) }
                    if (phase is ReportPhase.Waiting) {
                        Button(onClick = onTryNow, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.report_try_now))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SentContent(
    onViewReports: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(start = SheetPadding, end = SheetPadding, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = MaterialShapes.Cookie9Sided.toShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(ResultShapeSize),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(ResultIconSize)) }
            }
            Text(stringResource(R.string.report_sent), style = MaterialTheme.typography.headlineSmallEmphasized)
            Text(
                stringResource(R.string.report_sent_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(shape = RoundedCornerShape(DisclosureRadius), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NextStep(
                    Icons.Outlined.Inbox,
                    stringResource(R.string.status_received),
                    stringResource(R.string.report_now),
                    current = true,
                )
                Text(
                    stringResource(R.string.report_next),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NextStep(
                    Icons.Outlined.ManageSearch,
                    stringResource(R.string.status_investigating),
                    stringResource(R.string.report_next_looking),
                )
                NextStep(Icons.Outlined.HelpOutline, stringResource(R.string.status_need_info), stringResource(R.string.report_next_info))
                NextStep(
                    Icons.Outlined.CheckCircle,
                    stringResource(R.string.status_fixed_plain),
                    stringResource(R.string.report_next_fixed),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onViewReports, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.your_reports))
            }
            Button(onClick = onClose, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.done)) }
        }
    }
}

@Composable
private fun NextStep(
    icon: ImageVector,
    title: String,
    body: String,
    current: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            Text(title, style = AppText.tileTitle)
            Text(body, style = AppText.tileSupporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
