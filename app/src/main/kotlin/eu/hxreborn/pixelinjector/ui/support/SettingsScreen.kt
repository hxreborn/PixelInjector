package eu.hxreborn.pixelinjector.ui.support

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.support.ReleaseCheck
import eu.hxreborn.pixelinjector.support.ReportBuilder
import eu.hxreborn.pixelinjector.support.ReportConfig
import eu.hxreborn.pixelinjector.support.ReportStatus
import eu.hxreborn.pixelinjector.support.SentReport
import eu.hxreborn.pixelinjector.ui.component.DetailScaffold
import eu.hxreborn.pixelinjector.ui.component.SectionGap
import eu.hxreborn.pixelinjector.ui.component.SectionHeader
import eu.hxreborn.pixelinjector.ui.component.StateChip
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.component.TileGap
import eu.hxreborn.pixelinjector.ui.component.TileLeading
import eu.hxreborn.pixelinjector.ui.component.TileText
import eu.hxreborn.pixelinjector.ui.component.readableGutter
import eu.hxreborn.pixelinjector.ui.component.tileShape
import eu.hxreborn.pixelinjector.ui.theme.AppText
import eu.hxreborn.pixelinjector.ui.util.DateStyle
import eu.hxreborn.pixelinjector.ui.util.LocalDateStyle
import java.time.LocalDate

private val ChevronSlot = 40.dp
private val CookieSize = 88.dp
private const val COOKIE_SPIN_MS = 8_000
private const val FULL_TURN = 360f
private val ConnectedButtonHeight = 52.dp
private val REPO_URL = "https://github.com/${ReportConfig.repo}"
private const val KOFI_URL = "https://ko-fi.com/hxreborn"
private const val LICENSE = "GPL-3.0"

class SettingsActions(
    val onCheck: () -> Unit,
    val onUpdate: () -> Unit,
    val onSendReport: () -> Unit,
    val onReports: () -> Unit,
    val onAutoCheckUpdates: (Boolean) -> Unit,
    val onUpdateNotifications: (Boolean) -> Unit,
    val onOpenNotificationSettings: () -> Unit,
    val onHideUnavailable: (Boolean) -> Unit,
    val onShowPackages: (Boolean) -> Unit,
    val onModuleLog: () -> Unit,
    val onUseSystemFont: (Boolean) -> Unit,
    val onDateFormat: () -> Unit,
    val onAbout: () -> Unit,
)

class AboutActions(
    val onWhatsNew: () -> Unit,
    val onChangelog: () -> Unit,
    val onLicenses: () -> Unit,
    val onOpenUrl: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    state: SupportState,
    updateNotifications: Boolean,
    updateNotificationScheduleFailed: Boolean,
    updateNotificationChannelBlocked: Boolean,
    showAppNotificationSettings: Boolean,
    hideUnavailable: Boolean,
    showPackages: Boolean,
    dateStyle: DateStyle,
    useSystemFont: Boolean,
    actions: SettingsActions,
    onBack: () -> Unit,
) {
    DetailScaffold(
        title = stringResource(R.string.settings),
        onBack = onBack,
        contentPadding = PaddingValues(horizontal = readableGutter(), vertical = 16.dp),
    ) { padding ->
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        ) {
            Group(stringResource(R.string.settings_updates), if (showAppNotificationSettings) 4 else 3) { index, shape ->
                when (index) {
                    0 -> {
                        VersionTile(state, shape, actions)
                    }

                    1 -> {
                        SwitchTile(
                            shape,
                            Icons.Outlined.Autorenew,
                            stringResource(R.string.auto_check_updates),
                            null,
                            state.autoCheckUpdates,
                            actions.onAutoCheckUpdates,
                        )
                    }

                    2 if !updateNotificationChannelBlocked -> {
                        SwitchTile(
                            shape,
                            Icons.Outlined.Notifications,
                            stringResource(R.string.update_notifications),
                            stringResource(
                                if (updateNotificationScheduleFailed) {
                                    R.string.update_notifications_schedule_failed
                                } else {
                                    R.string.update_notifications_summary
                                },
                            ),
                            updateNotifications,
                            actions.onUpdateNotifications,
                        )
                    }

                    else -> {
                        Tile(shape = shape, onClick = actions.onOpenNotificationSettings) {
                            TileLeading(Icons.Outlined.Notifications)
                            TileText(
                                stringResource(R.string.update_notifications_open_settings),
                                if (updateNotificationChannelBlocked) {
                                    stringResource(
                                        R.string.update_notifications_channel_blocked,
                                    )
                                } else {
                                    null
                                },
                            )
                            Chevron()
                        }
                    }
                }
            }
            Spacer(Modifier.height(SectionGap))
            Group(stringResource(R.string.settings_dashboard), 3) { index, shape ->
                when (index) {
                    0 -> {
                        SwitchTile(
                            shape,
                            Icons.Outlined.VisibilityOff,
                            stringResource(R.string.hide_unavailable),
                            stringResource(R.string.hide_unavailable_summary),
                            hideUnavailable,
                            actions.onHideUnavailable,
                        )
                    }

                    1 -> {
                        SwitchTile(
                            shape,
                            Icons.Outlined.Code,
                            stringResource(R.string.show_packages),
                            stringResource(R.string.show_packages_summary),
                            showPackages,
                            actions.onShowPackages,
                        )
                    }

                    else -> {
                        Tile(shape = shape, onClick = actions.onDateFormat) {
                            TileLeading(Icons.Outlined.CalendarMonth)
                            TileText(
                                stringResource(R.string.date_format),
                                dateStyle.format(LocalDate.now()),
                                supportingStyle = AppText.meta,
                            )
                            Chevron()
                        }
                    }
                }
            }
            Spacer(Modifier.height(SectionGap))
            Group(stringResource(R.string.settings_appearance), 1) { _, shape ->
                SwitchTile(
                    shape,
                    Icons.Outlined.FontDownload,
                    stringResource(R.string.use_system_font),
                    null,
                    useSystemFont,
                    actions.onUseSystemFont,
                )
            }
            Spacer(Modifier.height(SectionGap))
            Group(stringResource(R.string.settings_support), 3) { index, shape ->
                when (index) {
                    0 -> {
                        Tile(shape = shape, onClick = actions.onSendReport) {
                            TileLeading(Icons.Outlined.BugReport)
                            TileText(stringResource(R.string.send_report), stringResource(R.string.send_report_summary))
                            Chevron()
                        }
                    }

                    1 -> {
                        ReportsTile(state, shape, actions.onReports)
                    }

                    else -> {
                        Tile(shape = shape, onClick = actions.onModuleLog) {
                            TileLeading(Icons.AutoMirrored.Outlined.Notes)
                            TileText(stringResource(R.string.module_log))
                            Chevron()
                        }
                    }
                }
            }
            Spacer(Modifier.height(SectionGap))
            Group(stringResource(R.string.settings_about), 1) { _, shape ->
                LinkTile(
                    shape,
                    Icons.Outlined.Info,
                    stringResource(R.string.app_name),
                    "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    actions.onAbout,
                    supportingStyle = AppText.meta,
                )
            }
        }
    }
}

@Composable
fun AboutScreen(
    framework: String?,
    actions: AboutActions,
    onBack: () -> Unit,
) {
    DetailScaffold(
        title = stringResource(R.string.settings_about),
        onBack = onBack,
        contentPadding = PaddingValues(horizontal = readableGutter(), vertical = 16.dp),
    ) { padding ->
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        ) {
            IdentityCard(tileShape(1, 0), actions.onOpenUrl)
            Spacer(Modifier.height(SectionGap))
            Group(null, 5) { index, shape ->
                when (index) {
                    0 -> {
                        LinkTile(
                            shape,
                            Icons.Outlined.NewReleases,
                            stringResource(R.string.whats_new),
                            stringResource(R.string.whats_new_summary, BuildConfig.VERSION_NAME),
                            actions.onWhatsNew,
                        )
                    }

                    1 -> {
                        LinkTile(
                            shape,
                            Icons.Outlined.History,
                            stringResource(R.string.changelog),
                            stringResource(R.string.changelog_summary),
                            actions.onChangelog,
                        )
                    }

                    2 -> {
                        LinkTile(
                            shape,
                            ImageVector.vectorResource(R.drawable.ic_github_inset),
                            stringResource(R.string.source_code),
                            ReportConfig.repo,
                            { actions.onOpenUrl(REPO_URL) },
                            external = true,
                            supportingStyle = AppText.meta,
                        )
                    }

                    3 -> {
                        LinkTile(
                            shape,
                            Icons.Outlined.Lightbulb,
                            stringResource(R.string.open_issue),
                            stringResource(R.string.open_issue_summary),
                            { actions.onOpenUrl("$REPO_URL/issues/new/choose") },
                            external = true,
                        )
                    }

                    else -> {
                        LinkTile(
                            shape,
                            Icons.Outlined.Description,
                            stringResource(R.string.licenses),
                            stringResource(R.string.licenses_summary),
                            actions.onLicenses,
                        )
                    }
                }
            }
            Spacer(Modifier.height(SectionGap))
            DeviceGroup(framework)
            ContributorsSection(actions.onOpenUrl)
        }
    }
}

@Composable
private fun Group(
    title: String?,
    count: Int,
    content: @Composable (Int, RoundedCornerShape) -> Unit,
) {
    if (title != null) SectionHeader(title)
    Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
        repeat(count) { content(it, tileShape(count, it)) }
    }
}

@Composable
private fun SwitchTile(
    shape: RoundedCornerShape,
    icon: ImageVector,
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Tile(
        shape = shape,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.semantics { role = Role.Switch },
    ) {
        TileLeading(icon)
        TileText(title, summary)
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
private fun Chevron() {
    Box(Modifier.width(ChevronSlot), contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private enum class VersionAction { Update, Checking, Check, Retry }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VersionTile(
    state: SupportState,
    shape: RoundedCornerShape,
    actions: SettingsActions,
) {
    val release = state.release
    val available = state.available
    val haptic = LocalHapticFeedback.current
    var checkRequested by remember { mutableStateOf(false) }
    LaunchedEffect(release) {
        if (checkRequested && release !is ReleaseCheck.Checking) {
            checkRequested = false
            haptic.performHapticFeedback(if (release is ReleaseCheck.Failed) HapticFeedbackType.Reject else HapticFeedbackType.Confirm)
        }
    }
    val onCheck = {
        checkRequested = true
        actions.onCheck()
    }
    val hasUpdate = available != null
    val onClick = if (hasUpdate) actions.onUpdate else onCheck
    Tile(shape = shape, onClick = onClick) {
        TileLeading(Icons.Outlined.NewReleases)
        val supporting =
            when {
                available != null -> {
                    stringResource(R.string.update_available_short, available.versionName)
                }

                release is ReleaseCheck.Checking -> {
                    stringResource(R.string.update_checking)
                }

                release is ReleaseCheck.Failed -> {
                    release.lastCheckedAt?.let { stringResource(R.string.update_check_failed, LocalDateStyle.current.formatDateTime(it)) }
                        ?: stringResource(R.string.update_check_failed_never)
                }

                release is ReleaseCheck.Done -> {
                    stringResource(R.string.update_up_to_date, LocalDateStyle.current.formatDateTime(release.checkedAt))
                }

                else -> {
                    stringResource(R.string.update_never_checked)
                }
            }
        val failed = release is ReleaseCheck.Failed && !hasUpdate
        TileText(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            supporting,
            supportingColor =
                when {
                    failed -> MaterialTheme.colorScheme.error
                    hasUpdate -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        val action =
            when {
                hasUpdate -> VersionAction.Update
                release is ReleaseCheck.Checking -> VersionAction.Checking
                failed -> VersionAction.Retry
                else -> VersionAction.Check
            }
        val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val sizeSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
        AnimatedContent(
            targetState = action,
            transitionSpec = {
                fadeIn(fadeSpec).togetherWith(fadeOut(fadeSpec)).using(SizeTransform(clip = false) { _, _ -> sizeSpec })
            },
            contentAlignment = Alignment.CenterEnd,
            label = "version-action",
        ) { target ->
            when (target) {
                VersionAction.Update -> {
                    FilledTonalButton(
                        onClick = actions.onUpdate,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.clearAndSetSemantics { },
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.update))
                    }
                }

                VersionAction.Checking -> {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(32.dp)) }
                }

                VersionAction.Check, VersionAction.Retry -> {
                    IconButton(onClick = onCheck, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            if (target == VersionAction.Retry) Icons.Outlined.ErrorOutline else Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.update_check),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportsTile(
    state: SupportState,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    Tile(shape = shape, onClick = onClick) {
        TileLeading(Icons.Outlined.Inbox)
        val count =
            if (state.conversations.isEmpty()) {
                stringResource(R.string.reports_none)
            } else {
                pluralStringResource(R.plurals.reports_count, state.conversations.size, state.conversations.size)
            }
        val waiting = state.conversations.count { it.status == ReportStatus.NEED_INFO && !it.noteSeen }
        val reply = if (waiting > 0) pluralStringResource(R.plurals.reports_need_reply, waiting, waiting) else null
        val tertiary = MaterialTheme.colorScheme.tertiary
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.your_reports), style = AppText.tileTitle)
            Text(
                buildAnnotatedString {
                    append(count)
                    reply?.let {
                        append("  ")
                        withStyle(SpanStyle(color = tertiary, fontWeight = FontWeight.Medium)) { append(it) }
                    }
                },
                style = AppText.tileSupporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Chevron()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IdentityCard(
    shape: RoundedCornerShape,
    onOpenUrl: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = shape, color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val rotation by rememberInfiniteTransition(label = "cookie").animateFloat(
                initialValue = 0f,
                targetValue = FULL_TURN,
                animationSpec = infiniteRepeatable(tween(COOKIE_SPIN_MS, easing = LinearEasing)),
                label = "rotation",
            )
            Box(Modifier.size(CookieSize), contentAlignment = Alignment.Center) {
                Surface(
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    color = scheme.primary,
                    modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation },
                ) {}
                Icon(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(
                    stringResource(R.string.about_tagline),
                    style = AppText.tileSupporting,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = { onOpenUrl("$REPO_URL/releases/tag/v${BuildConfig.VERSION_NAME}") },
                    label = { Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = AppText.meta) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Sell,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
                AssistChip(
                    onClick = { onOpenUrl("$REPO_URL/blob/main/LICENSE") },
                    label = { Text(LICENSE, style = AppText.meta) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Balance,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                Button(
                    onClick = { onOpenUrl(REPO_URL) },
                    shapes =
                        ButtonDefaults.shapes(
                            ButtonGroupDefaults.connectedLeadingButtonShape,
                            ButtonGroupDefaults.connectedLeadingButtonPressShape,
                        ),
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = ConnectedButtonHeight),
                ) {
                    Icon(Icons.Outlined.StarOutline, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.star_on_github), style = AppText.button)
                }
                FilledTonalButton(
                    onClick = { onOpenUrl(KOFI_URL) },
                    shapes =
                        ButtonDefaults.shapes(
                            ButtonGroupDefaults.connectedTrailingButtonShape,
                            ButtonGroupDefaults.connectedTrailingButtonPressShape,
                        ),
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = ConnectedButtonHeight),
                ) {
                    Icon(Icons.Outlined.Coffee, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.kofi), style = AppText.button)
                }
            }
        }
    }
}

@Composable
private fun LinkTile(
    shape: RoundedCornerShape,
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    external: Boolean = false,
    supportingStyle: TextStyle = AppText.tileSupporting,
) {
    Tile(shape = shape, onClick = onClick) {
        TileLeading(icon)
        TileText(title, summary, supportingStyle = supportingStyle)
        if (external) {
            Box(Modifier.width(ChevronSlot), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Chevron()
        }
    }
}

@Composable
private fun DeviceGroup(framework: String?) {
    val context = LocalContext.current
    val signer = remember { ReportBuilder.installedSigner(context) }
    val rows =
        listOfNotNull(
            framework?.let { Triple(Icons.Outlined.Extension, R.string.settings_framework, it.replace("\n", " · ")) },
            Triple(Icons.Outlined.Smartphone, R.string.android_build, "${Build.DEVICE} · ${Build.ID}"),
        )
    Group(stringResource(R.string.settings_this_device), rows.size + if (signer != null) 1 else 0) { index, shape ->
        val row = rows.getOrNull(index)
        if (row != null) {
            Tile(shape = shape) {
                TileLeading(row.first)
                TileText(stringResource(row.second), row.third, supportingStyle = AppText.meta)
            }
        } else if (signer != null) {
            SignerTile(signer, shape)
        }
    }
}

@Composable
private fun SignerTile(
    signer: String,
    shape: RoundedCornerShape,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val isReleaseKey = signer == BuildConfig.REPORT_SIGNER
    val title = stringResource(R.string.signing_key)
    var expanded by rememberSaveable { mutableStateOf(false) }
    Tile(shape = shape, onClick = { expanded = !expanded }) {
        TileLeading(Icons.Outlined.Key)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
                Text(title, style = AppText.tileTitle, color = colors.onSurface)
                if (isReleaseKey) {
                    StateChip(stringResource(R.string.signing_key_release), colors.primaryContainer, colors.onPrimaryContainer)
                } else {
                    StateChip(stringResource(R.string.signing_key_other), colors.errorContainer, colors.onErrorContainer)
                }
            }
            Text(
                fingerprint(signer),
                style = AppText.meta,
                color = colors.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.MiddleEllipsis,
            )
        }
        IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
            context
                .getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText(title, signer))
        }) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy), tint = colors.onSurfaceVariant)
        }
    }
}

private val ChipGap = 8.dp
private const val FINGERPRINT_GROUP = 4

private fun fingerprint(hash: String): String = hash.uppercase().chunked(FINGERPRINT_GROUP).joinToString(" ")
