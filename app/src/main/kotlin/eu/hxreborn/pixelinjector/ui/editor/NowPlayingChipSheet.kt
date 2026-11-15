package eu.hxreborn.pixelinjector.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.theme.AppText
import kotlin.math.roundToInt

private val TileShape = RoundedCornerShape(20.dp)
private val SheetPadding = 16.dp
private val SectionGap = 16.dp
private const val SHEET_HEIGHT = 0.8f

@Composable
fun NowPlayingChipSheet(
    side: String,
    width: Int,
    hideShade: Boolean,
    onSideChange: (String) -> Unit,
    onWidthChange: (Int) -> Unit,
    onHideShadeChange: (Boolean) -> Unit,
) {
    var titleWidth by remember(width) { mutableFloatStateOf(width.toFloat()) }
    Column(
        Modifier.fillMaxHeight(SHEET_HEIGHT).padding(SheetPadding).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        SheetHeader(stringResource(R.string.tweak_now_playing_chip))
        ChipChoiceTile(
            title = stringResource(R.string.download_chip_side),
            options =
                listOf(
                    Prefs.DOWNLOAD_CHIP_LEFT to stringResource(R.string.download_chip_left),
                    Prefs.DOWNLOAD_CHIP_RIGHT to stringResource(R.string.download_chip_right),
                ),
            selected = side,
            onSelect = onSideChange,
        )
        Tile(shape = TileShape) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.now_playing_chip_width), style = AppText.tileTitle)
                Text(stringResource(R.string.now_playing_chip_width_value, titleWidth.roundToInt()), style = AppText.tileTitle)
                Slider(
                    value = titleWidth,
                    onValueChange = { titleWidth = it },
                    onValueChangeFinished = { onWidthChange(titleWidth.roundToInt()) },
                    valueRange = Prefs.NOW_PLAYING_WIDTH_MIN.toFloat()..Prefs.NOW_PLAYING_WIDTH_MAX.toFloat(),
                )
            }
        }
        ChipSwitchTile(stringResource(R.string.now_playing_chip_hide_shade), hideShade, onHideShadeChange)
    }
}
