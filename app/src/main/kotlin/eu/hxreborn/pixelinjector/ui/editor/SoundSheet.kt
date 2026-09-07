package eu.hxreborn.pixelinjector.ui.editor

import android.media.Ringtone
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.sound.SystemSounds
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.component.TileText
import eu.hxreborn.pixelinjector.ui.component.tileGroup
import eu.hxreborn.pixelinjector.ui.util.drawVerticalScrollbar

private const val SHEET_HEIGHT_FRACTION = 0.8f
private val SoundRowVerticalPadding = 8.dp
private val radioRole = Modifier.semantics { role = Role.RadioButton }

@Composable
fun SoundSheet(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val none = stringResource(R.string.sound_none)
    val sounds = remember { listOf("") + SystemSounds.list() }
    val listState = rememberLazyListState()
    var ringtone by remember { mutableStateOf<Ringtone?>(null) }
    DisposableEffect(Unit) { onDispose { ringtone?.stop() } }

    Column(Modifier.fillMaxHeight(SHEET_HEIGHT_FRACTION).padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
        SheetHeader(stringResource(R.string.tweak_biometric_sound), stringResource(R.string.tweak_biometric_sound_desc))
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).drawVerticalScrollbar(listState),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            tileGroup(sounds, key = { it }) { path, shape ->
                val checked = path == selected
                Tile(
                    shape = shape,
                    verticalPadding = SoundRowVerticalPadding,
                    checked = checked,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        ringtone?.stop()
                        ringtone = path.takeIf { it.isNotEmpty() }?.let { SystemSounds.play(context, it) }
                        onSelect(path)
                    },
                    modifier = radioRole,
                ) {
                    TileText(if (path.isEmpty()) none else SystemSounds.label(path), singleLine = true)
                    RadioButton(selected = checked, onClick = null)
                }
            }
        }
    }
}
