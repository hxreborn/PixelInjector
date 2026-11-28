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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Terminal
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.support.ReportConfig
import eu.hxreborn.pixelinjector.support.ReportPreview
import eu.hxreborn.pixelinjector.support.RomCode
import eu.hxreborn.pixelinjector.support.SentReport
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.component.compactHeight
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
    homeAppLabel: String,
    phase: ReportPhase,
    message: String,
    onMessageChange: (String) -> Unit,
    onSend: (message: String, tweak: String?) -> Unit,
    onTryNow: () -> Unit,
    onRequestRoot: () -> Unit,
    onViewReports: () -> Unit,
    onClose: () -> Unit,
) {
    var tweak by rememberSaveable { mutableStateOf(initialTweak) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var exact by rememberSaveable { mutableStateOf(false) }

    if (phase is ReportPhase.Sent) {
        SentContent(onViewReports, onClose)
        return
    }
    val footerScrolls = compactHeight()
    val unreadable = preview?.facts?.unreadableRomCodeFor(tweak).takeIf { replyTo == null }
    val needsRoot = replyTo == null && preview?.facts?.rootDenied == true
    val homeAppNeedsRoot = replyTo == null && tweak != null && tweak in preview?.facts?.rootSleepFailed.orEmpty()
    val notBound = replyTo == null && preview != null && preview.facts.framework == null
    val footer: @Composable () -> Unit = {
        Footer(
            phase,
            ready = preview != null && unreadable == null && !needsRoot && !homeAppNeedsRoot,
            onSend = { onSend(message.trim(), tweak) },
            onTryNow = onTryNow,
            onClose = onClose,
        )
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
                    onValueChange = {
                        when {
                            it.length <= MESSAGE_MAX -> onMessageChange(it)
                            message.length < MESSAGE_MAX -> onMessageChange(it.take(MESSAGE_MAX))
                        }
                    },
                    label = { Text(stringResource(if (replyTo != null) R.string.report_your_answer else R.string.report_what_happened)) },
                    minLines = 3,
                    enabled = phase == ReportPhase.Compose || phase is ReportPhase.Rejected,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText =
                        if (message.isEmpty()) {
                            null
                        } else {
                            { Text("${message.length}/$MESSAGE_MAX", Modifier.fillMaxWidth(), textAlign = TextAlign.End) }
                        },
                )
            }
            if (needsRoot) {
                Notice(
                    Icons.Outlined.Terminal,
                    stringResource(R.string.report_needs_root),
                    error = true,
                    action = stringResource(R.string.report_request_root) to onRequestRoot,
                )
            }
            if (homeAppNeedsRoot) {
                Notice(Icons.Outlined.Terminal, stringResource(R.string.sleep_root_failed, homeAppLabel), error = true)
            }
            if (notBound) {
                Notice(Icons.Outlined.WarningAmber, stringResource(R.string.report_not_bound), error = false)
            }
            if (replyTo == null) TweakField(tweak, preview, onSelect = { tweak = it })
            val romCode = preview?.facts?.romCodeFor(tweak).takeIf { replyTo == null }
            unreadable?.let {
                val file = if (it == RomCode.SERVICES) "services.jar" else "SystemUI"
                Notice(Icons.Outlined.ErrorOutline, stringResource(R.string.report_rom_code_unreadable, file), error = true)
            }
            if (romCode != null) Notice(Icons.Outlined.Code, stringResource(R.string.report_rom_code, romCode.name), error = false)
            Disclosure(preview, romCode, expanded, exact, messageLength = message.length, onToggle = { expanded = !expanded }, onExact = {
                exact =
                    !exact
            })
            if (footerScrolls) footer()
        }
        if (!footerScrolls) footer()
    }
}

@Composable
private fun Notice(
    icon: ImageVector,
    text: String,
    error: Boolean,
    action: Pair<String, () -> Unit>? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(DisclosureRadius),
        color = if (error) scheme.errorContainer else scheme.secondaryContainer,
        contentColor = if (error) scheme.onErrorContainer else scheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = if (action == null) 16.dp else 4.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            action?.let { (label, onClick) ->
                TextButton(
                    onClick = onClick,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
                ) {
                    Text(label)
                }
            }
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
            TweakGroup.FIREFOX to R.string.scope_firefox,
        )
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = title,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.report_tweak)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
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
                            if (item.key in failed) {
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
    romCode: RomCode?,
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
                IconButton(
                    onClick = onToggle,
                    shapes = IconButtonDefaults.shapes(),
                    modifier =
                        Modifier.semantics {
                            stateDescription =
                                toggleState
                        },
                ) {
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
                            Icons.Outlined.Lock,
                            stringResource(R.string.report_private_group),
                            listOf(
                                stringResource(R.string.report_field_log) to
                                    if (noLog || preview == null) {
                                        stringResource(R.string.report_none)
                                    } else {
                                        pluralStringResource(R.plurals.report_lines, preview.logLineCount, preview.logLineCount)
                                    },
                                stringResource(R.string.report_field_message) to
                                    pluralStringResource(R.plurals.report_characters, messageLength, messageLength),
                                stringResource(R.string.report_field_versions) to stringResource(R.string.report_versions_value),
                            ) + listOfNotNull(romCode?.let { stringResource(R.string.report_field_rom_code) to it.name }),
                        )
                        val facts = preview?.facts
                        FieldGroup(
                            Icons.Outlined.LockOpen,
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
                                        if (facts?.rootDenied == true) R.string.report_root_denied else R.string.report_root_granted,
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
                        FieldGroup(
                            Icons.Outlined.Key,
                            stringResource(R.string.report_crypto_group),
                            links = CryptoDocs,
                            fields =
                                listOf(
                                    stringResource(R.string.report_crypto_scheme) to stringResource(R.string.report_crypto_scheme_value),
                                    stringResource(R.string.report_crypto_kem) to stringResource(R.string.report_crypto_kem_value),
                                    stringResource(R.string.report_crypto_aead) to stringResource(R.string.report_crypto_aead_value),
                                    stringResource(R.string.report_crypto_key) to ReportConfig.keyId,
                                    stringResource(R.string.report_crypto_device) to stringResource(R.string.report_crypto_device_value),
                                    stringResource(R.string.report_crypto_replies) to stringResource(R.string.report_crypto_replies_value),
                                    stringResource(R.string.report_crypto_server) to
                                        stringResource(R.string.report_crypto_server_value, ReportConfig.host),
                                    stringResource(
                                        R.string.report_crypto_redacted,
                                    ) to stringResource(R.string.report_crypto_redacted_value),
                                ),
                        )
                    }
                    Text(
                        stringResource(R.string.report_opt_in),
                        style = AppText.tileSupporting,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.report_retention),
                        style = AppText.tileSupporting,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (preview != null && preview.logLineCount > 0) {
                        TextButton(onClick = onExact, shapes = ButtonDefaults.shapes(), modifier = Modifier.offset(x = (-12).dp)) {
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
    icon: ImageVector,
    title: String,
    fields: List<Pair<String, String>>,
    links: Map<Int, Pair<String, String>> = emptyMap(),
) {
    val linkStyles = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        fields.forEachIndexed { index, (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(FIELD_LABEL_WEIGHT),
                )
                Text(
                    linked(value, links[index], linkStyles),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f - FIELD_LABEL_WEIGHT),
                )
            }
        }
    }
}

private const val FIELD_LABEL_WEIGHT = 0.4f

private val CryptoDocs =
    mapOf(
        0 to ("RFC 9180" to "https://www.rfc-editor.org/rfc/rfc9180"),
        1 to ("X25519" to "https://www.rfc-editor.org/rfc/rfc7748"),
        2 to ("AES-256-GCM" to "https://csrc.nist.gov/pubs/sp/800/38/d/final"),
        4 to ("associated data" to "https://www.rfc-editor.org/rfc/rfc9180#section-5.2"),
        5 to ("Android Keystore" to "https://developer.android.com/privacy-and-security/keystore"),
    )

private fun linked(
    value: String,
    link: Pair<String, String>?,
    styles: TextLinkStyles,
): AnnotatedString =
    buildAnnotatedString {
        val at = link?.let { value.indexOf(it.first) } ?: -1
        if (link == null || at < 0) {
            append(value)
            return@buildAnnotatedString
        }
        append(value.substring(0, at))
        withLink(LinkAnnotation.Url(link.second, styles)) { append(link.first) }
        append(value.substring(at + link.first.length))
    }

@Composable
private fun ExactText(preview: ReportPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (preview.log.length > EXACT_TAIL) {
                stringResource(R.string.report_exact_tail, EXACT_TAIL, preview.log.length)
            } else {
                stringResource(R.string.report_exact_body)
            },
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
private fun Footer(
    phase: ReportPhase,
    ready: Boolean,
    onSend: () -> Unit,
    onTryNow: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.padding(top = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onViewReports, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.your_reports))
            }
            Button(onClick = onClose, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.done)) }
        }
    }
}
