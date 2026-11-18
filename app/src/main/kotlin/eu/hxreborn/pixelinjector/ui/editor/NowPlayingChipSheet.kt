package eu.hxreborn.pixelinjector.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
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
private val FieldGap = 8.dp
private val SwatchSize = 28.dp
private val SwatchBorderWidth = 1.dp
private const val SHEET_HEIGHT = 0.8f
private const val HEX_DIGITS = 6
private const val HEX_RADIX = 16
private const val RGB_MASK = 0x00FFFFFF
private const val OPAQUE_ALPHA = 0xFF000000.toInt()

@Composable
fun NowPlayingChipSheet(
    side: String,
    width: Int,
    hideShade: Boolean,
    textColor: Int,
    chipColor: Int,
    opacity: Int,
    onSideChange: (String) -> Unit,
    onWidthChange: (Int) -> Unit,
    onHideShadeChange: (Boolean) -> Unit,
    onTextColorChange: (Int) -> Unit,
    onChipColorChange: (Int) -> Unit,
    onOpacityChange: (Int) -> Unit,
) {
    var titleWidth by remember(width) { mutableFloatStateOf(width.toFloat()) }
    var chipOpacity by remember(opacity) {
        mutableFloatStateOf(opacity.coerceIn(Prefs.NOW_PLAYING_OPACITY_MIN, Prefs.NOW_PLAYING_OPACITY_MAX).toFloat())
    }
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
        ChipColorTile(stringResource(R.string.now_playing_chip_text_color), textColor, onTextColorChange)
        ChipColorTile(stringResource(R.string.now_playing_chip_color), chipColor, onChipColorChange)
        Tile(shape = TileShape) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.now_playing_chip_opacity), style = AppText.tileTitle)
                Text(stringResource(R.string.now_playing_chip_opacity_value, chipOpacity.roundToInt()), style = AppText.tileTitle)
                Slider(
                    value = chipOpacity,
                    onValueChange = { chipOpacity = it },
                    onValueChangeFinished = { onOpacityChange(chipOpacity.roundToInt()) },
                    valueRange = Prefs.NOW_PLAYING_OPACITY_MIN.toFloat()..Prefs.NOW_PLAYING_OPACITY_MAX.toFloat(),
                )
            }
        }
    }
}

@Composable
private fun ChipColorTile(
    title: String,
    color: Int,
    onColorChange: (Int) -> Unit,
) {
    val follows = color == Prefs.NOW_PLAYING_COLOR_FOLLOWS_STATUS_BAR
    var hexInput by remember(color) { mutableStateOf(if (follows) "" else "%06X".format(color and RGB_MASK)) }
    Tile(shape = TileShape) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FieldGap)) {
            Text(title, style = AppText.tileTitle, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(FieldGap), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(SwatchSize)
                        .clip(CircleShape)
                        .background(if (follows) Color.Transparent else Color(color))
                        .border(SwatchBorderWidth, MaterialTheme.colorScheme.outline, CircleShape),
                )
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        hexInput = input.filter { it.isHexDigit() }.take(HEX_DIGITS).uppercase()
                        when (hexInput.length) {
                            0 -> onColorChange(Prefs.NOW_PLAYING_COLOR_FOLLOWS_STATUS_BAR)
                            HEX_DIGITS -> onColorChange(OPAQUE_ALPHA or hexInput.toInt(HEX_RADIX))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = AppText.meta,
                    prefix = if (hexInput.isEmpty()) null else ({ Text("#", style = AppText.meta) }),
                    placeholder = { Text(stringResource(R.string.now_playing_chip_color_follows), style = AppText.meta) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                TextButton(
                    onClick = {
                        hexInput = ""
                        onColorChange(Prefs.NOW_PLAYING_COLOR_FOLLOWS_STATUS_BAR)
                    },
                    enabled = !follows,
                ) {
                    Text(stringResource(R.string.now_playing_chip_color_reset), style = AppText.button)
                }
            }
        }
    }
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
