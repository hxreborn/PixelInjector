package eu.hxreborn.pixelinjector.ui.editor

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SignalWifi4Bar
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.ui.component.EditorSwitchTile
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.theme.AppText
import eu.hxreborn.pixelinjector.ui.util.verticalScrollbar

private val TileShape = RoundedCornerShape(20.dp)
private val SectionGap = 16.dp
private val SheetGutter = 16.dp
private val SheetBottom = 20.dp
private val TileGap = 2.dp
private val ChoiceGap = 8.dp
private val StripShape = RoundedCornerShape(28.dp)
private val StripHeight = 56.dp
private val StripPadding = 20.dp
private val StripGap = 12.dp
private val StatusGap = 6.dp
private val StatusIcon = 16.dp
private val PillWidth = 56.dp
private val PillHeight = 18.dp
private val PillPadding = 4.dp
private val PillIcon = 12.dp
private val PillGap = 3.dp
private val BarHeight = 3.dp
private val AppIconShape = RoundedCornerShape(3.dp)
private const val PILL_ALPHA = 0.2f
private const val TRACK_ALPHA = 0.35f
private const val SHEET_HEIGHT_FRACTION = 0.8f
private const val PROGRESS_MILLIS = 3000

@Composable
fun DownloadChipSheet(
    side: String,
    icon: String,
    hideLocked: Boolean,
    hideHeadsUp: Boolean,
    hideStatusIcon: Boolean,
    onSideChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onHideLockedChange: (Boolean) -> Unit,
    onHideHeadsUpChange: (Boolean) -> Unit,
    onHideStatusIconChange: (Boolean) -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxHeight(SHEET_HEIGHT_FRACTION)
            .padding(start = SheetGutter, end = SheetGutter, bottom = SheetBottom)
            .verticalScrollbar(scroll)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        SheetHeader(stringResource(R.string.tweak_download_chip))
        Strip(
            right = side == Prefs.DOWNLOAD_CHIP_RIGHT,
            appIcon = icon == Prefs.DOWNLOAD_CHIP_APP,
            hideStatusIcon = hideStatusIcon,
        )
        ChipSideTile(side, onSideChange)
        ChipChoiceTile(
            title = stringResource(R.string.download_chip_icon),
            options =
                listOf(
                    Prefs.DOWNLOAD_CHIP_NOTIFICATION to stringResource(R.string.download_chip_notification),
                    Prefs.DOWNLOAD_CHIP_APP to stringResource(R.string.download_chip_app),
                ),
            selected = icon,
            onSelect = onIconChange,
        )
        Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
            EditorSwitchTile(stringResource(R.string.download_chip_hide_locked), hideLocked, onHideLockedChange)
            EditorSwitchTile(stringResource(R.string.download_chip_hide_heads_up), hideHeadsUp, onHideHeadsUpChange)
            EditorSwitchTile(stringResource(R.string.download_chip_hide_status_icon), hideStatusIcon, onHideStatusIconChange)
        }
    }
}

@Composable
internal fun ChipSideTile(
    side: String,
    onSideChange: (String) -> Unit,
) = ChipChoiceTile(
    title = stringResource(R.string.download_chip_side),
    options =
        listOf(
            Prefs.DOWNLOAD_CHIP_LEFT to stringResource(R.string.download_chip_left),
            Prefs.DOWNLOAD_CHIP_RIGHT to stringResource(R.string.download_chip_right),
        ),
    selected = side,
    onSelect = onSideChange,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChipChoiceTile(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Tile(shape = TileShape) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChoiceGap)) {
            Text(title, style = AppText.tileTitle, color = MaterialTheme.colorScheme.onSurface)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                options.forEachIndexed { index, (value, label) ->
                    ToggleButton(
                        checked = value == selected,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onSelect(value)
                        },
                        shapes =
                            when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    ) {
                        Text(label, style = AppText.button, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Strip(
    right: Boolean,
    appIcon: Boolean,
    hideStatusIcon: Boolean,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .height(StripHeight)
            .clip(StripShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = StripPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StripGap),
    ) {
        Text("12:30", style = AppText.tileTitle, color = ink)
        if (!right) Pill(appIcon, ink)
        Row(horizontalArrangement = Arrangement.spacedBy(StatusGap)) {
            Icon(Icons.Outlined.Notifications, null, Modifier.size(StatusIcon), tint = ink)
            if (!hideStatusIcon) Icon(Icons.Outlined.Download, null, Modifier.size(StatusIcon), tint = ink)
        }
        Spacer(Modifier.weight(1f))
        if (right) Pill(appIcon, ink)
        Icon(Icons.Outlined.SignalWifi4Bar, null, Modifier.size(StatusIcon), tint = ink)
        Icon(Icons.Outlined.BatteryFull, null, Modifier.size(StatusIcon), tint = ink)
    }
}

@Composable
private fun Pill(
    appIcon: Boolean,
    ink: Color,
) {
    val progress =
        rememberInfiniteTransition(label = "progress").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(PROGRESS_MILLIS, easing = LinearEasing), RepeatMode.Restart),
            label = "progress",
        )
    Row(
        Modifier
            .width(PillWidth)
            .height(PillHeight)
            .clip(CircleShape)
            .background(ink.copy(alpha = PILL_ALPHA))
            .padding(horizontal = PillPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PillGap),
    ) {
        if (appIcon) {
            Icon(
                Icons.Outlined.Android,
                null,
                Modifier.size(PillIcon).clip(AppIconShape).background(MaterialTheme.colorScheme.primaryContainer),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            Icon(Icons.Outlined.Download, null, Modifier.size(PillIcon), tint = ink)
        }
        Box(
            Modifier
                .weight(1f)
                .height(BarHeight)
                .clip(CircleShape)
                .background(ink.copy(alpha = TRACK_ALPHA))
                .drawBehind { drawRect(ink, size = size.copy(width = size.width * progress.value)) },
        )
    }
}
