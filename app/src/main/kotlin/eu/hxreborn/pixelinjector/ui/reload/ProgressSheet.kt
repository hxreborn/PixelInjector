package eu.hxreborn.pixelinjector.ui.reload

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.ui.component.Console
import eu.hxreborn.pixelinjector.ui.component.SectionGap
import eu.hxreborn.pixelinjector.ui.component.SheetActions
import eu.hxreborn.pixelinjector.ui.component.StateChip
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.component.TileGap
import eu.hxreborn.pixelinjector.ui.component.installedVersionLabel
import eu.hxreborn.pixelinjector.ui.component.processNameForDisplay
import eu.hxreborn.pixelinjector.ui.component.tileShape
import eu.hxreborn.pixelinjector.ui.component.toneColor
import eu.hxreborn.pixelinjector.ui.theme.AppText
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val Tick = 1.seconds
private val SlowAfter = 30.seconds
private val ResultIconSize = 40.dp
private val ResultIndicatorSize = 38.dp
private val ResultStartPadding = 8.dp
private val ResultEndPadding = 12.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProgressSheet(
    run: ReloadRun,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    val done by rememberUpdatedState(run.done)
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || done },
        )
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(run.done) {
        while (!run.done) {
            delay(Tick)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) }
    val seen = remember { mutableStateSetOf<Int>() }
    LaunchedEffect(run.finished, run.done) {
        run.rows.forEachIndexed { index, row ->
            if (row.finished && seen.add(index)) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                when (toneOf(row.status)) {
                    Tone.OK -> haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    Tone.BAD -> haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    else -> Unit
                }
            }
        }
        if (run.done) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    ModalBottomSheet(
        onDismissRequest = { if (run.done) onClose() },
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = run.done),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxHeight(),
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        ) {
            Column(
                Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.targets_group_reload), style = AppText.progressTitle)
                    Text(
                        "[${run.current}/${run.total}]",
                        style = AppText.progressCounter,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val progress by animateFloatAsState(
                    targetValue = if (run.total == 0) 0f else run.finished.toFloat() / run.total,
                    animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                    label = "reload-progress",
                )
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
                run.rows.forEachIndexed { index, row ->
                    ResultTile(row, tileShape(run.rows.size, index), now)
                }
            }
            Console(run.lines, Modifier.weight(1f).padding(top = SectionGap))
            SheetActions {
                if (!run.done) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                FilledTonalButton(onClick = {
                    val clip = ClipData.newPlainText("PixelInjector hot reload", formatLines(run.lines))
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                }) { Text(stringResource(R.string.copy)) }
                TextButton(onClick = onClose, enabled = run.done) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ResultTile(
    row: ReloadRow,
    shape: RoundedCornerShape,
    now: Long,
) {
    val scheme = MaterialTheme.colorScheme
    val tone = toneOf(row.status)
    val pid = row.target.pid
    val supporting =
        when (row.status) {
            null -> "pid=$pid not queued"
            ReloadStatus.RELOADING -> "pid=$pid waiting on HotReloadCallback" + waitSuffix(now - row.startedAt)
            ReloadStatus.SUCCEEDED -> "pid=$pid loaded $installedVersionLabel"
            ReloadStatus.PROCESS_DIED -> "pid=$pid gone before reload. Loads $installedVersionLabel on next start."
            ReloadStatus.UNSUPPORTED -> "pid=$pid not hot reloadable. Force stop it to load $installedVersionLabel."
            ReloadStatus.THROWN -> "hotReloadModule threw ${row.message}"
            else -> "pid=$pid ${row.message ?: "old module refused"}"
        }
    Tile(shape = shape, startPadding = ResultStartPadding, endPadding = ResultEndPadding) {
        Crossfade(
            targetState = row.status,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            modifier = Modifier.size(ResultIconSize),
            label = "result-icon",
        ) { status ->
            Box(Modifier.size(ResultIconSize), contentAlignment = Alignment.Center) {
                when {
                    status == ReloadStatus.RELOADING -> LoadingIndicator(Modifier.size(ResultIndicatorSize))
                    status == null -> Icon(Icons.Outlined.RemoveCircleOutline, null, tint = scheme.onSurfaceVariant)
                    toneOf(status) == Tone.OK -> Icon(Icons.Outlined.CheckCircle, null, tint = scheme.tertiary)
                    toneOf(status) == Tone.WARN -> Icon(Icons.Outlined.Info, null, tint = scheme.secondary)
                    else -> Icon(Icons.Outlined.Cancel, null, tint = scheme.error)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(processNameForDisplay(row.target.processName), style = AppText.tileTitle)
            Text(supporting, style = AppText.meta, color = toneColor(tone, scheme.onSurfaceVariant))
        }
        Crossfade(
            targetState = row.status,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "result-chip",
        ) { status ->
            if (status != null) {
                val (container, content) =
                    when (toneOf(status)) {
                        Tone.OK -> scheme.tertiaryContainer to scheme.onTertiaryContainer
                        Tone.WARN -> scheme.secondaryContainer to scheme.onSecondaryContainer
                        Tone.BAD -> scheme.errorContainer to scheme.onErrorContainer
                        Tone.NEUTRAL -> scheme.primaryContainer to scheme.onPrimaryContainer
                    }
                StateChip(status.name, container, content)
            }
        }
    }
}

private fun waitSuffix(elapsedMs: Long): String {
    val elapsed = elapsedMs.milliseconds
    return if (elapsed >= SlowAfter) " ${elapsed.inWholeSeconds}s" else ""
}
