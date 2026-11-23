package eu.hxreborn.pixelinjector.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.prefs.NowPlayingChipFont
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.component.TileText
import eu.hxreborn.pixelinjector.ui.theme.AppText
import kotlin.math.roundToInt
import android.graphics.Color as PlatformColor

private val TileShape = RoundedCornerShape(20.dp)
private val SheetPadding = 16.dp
private val SectionGap = 16.dp
private val FieldGap = 8.dp
private val SwatchTouchSize = 48.dp
private val SwatchSize = 32.dp
private val OutlineBorderWidth = 1.dp
private val SwatchSelectedRingWidth = 3.dp
private val GradientTrackHeight = 16.dp
private val PreviewBarHeight = 36.dp
private val PreviewBarPadding = 12.dp
private val PreviewChipPadding = 4.dp
private val PreviewIconSize = 12.dp
private val PreviewIconGap = 3.dp
private val PreviewTitleSize = 11.sp
private const val SHEET_HEIGHT = 0.8f
private const val HEX_DIGITS = 6
private const val HEX_RADIX = 16
private const val RGB_MASK = 0x00FFFFFF
private const val OPAQUE_ALPHA = 0xFF000000.toInt()
private const val HUE_MAX_DEGREES = 360f
private const val HUE_STOP_SPACING_DEGREES = 60
private const val FOLLOWS_FILL_SPLIT = 0.5f

private val followsStatusBarFill =
    Brush.horizontalGradient(
        0f to Color.White,
        FOLLOWS_FILL_SPLIT to Color.White,
        FOLLOWS_FILL_SPLIT to Color.Black,
        1f to Color.Black,
    )
private val hueColors = (0..HUE_MAX_DEGREES.toInt() step HUE_STOP_SPACING_DEGREES).map { Color.hsv(it.toFloat(), 1f, 1f) }
private val customColorFill = Brush.sweepGradient(hueColors)

@Composable
fun NowPlayingChipSheet(
    side: String,
    width: Int,
    titleFont: NowPlayingChipFont,
    textColor: Int,
    chipColor: Int,
    opacity: Int,
    onSideChange: (String) -> Unit,
    onWidthChange: (Int) -> Unit,
    onTitleFontChange: (NowPlayingChipFont) -> Unit,
    onTextColorChange: (Int) -> Unit,
    onChipColorChange: (Int) -> Unit,
    onOpacityChange: (Int) -> Unit,
) {
    var titleWidth by remember(width) { mutableFloatStateOf(width.toFloat()) }
    var chipOpacity by remember(opacity) {
        mutableFloatStateOf(opacity.coerceIn(Prefs.NOW_PLAYING_OPACITY_MIN, Prefs.NOW_PLAYING_OPACITY_MAX).toFloat())
    }
    var textColorDraft by remember(textColor) { mutableIntStateOf(textColor) }
    var chipColorDraft by remember(chipColor) { mutableIntStateOf(chipColor) }
    Column(
        Modifier.fillMaxHeight(SHEET_HEIGHT).padding(SheetPadding),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        SheetHeader(stringResource(R.string.tweak_now_playing_chip))
        ChipPreview(
            textColor = textColorDraft,
            chipColor = chipColorDraft,
            opacity = chipOpacity.roundToInt(),
            font = titleFont,
            titleWidth = titleWidth.roundToInt(),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SectionGap),
        ) {
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
            ChipFontTile(titleFont, onTitleFontChange)
            ChipColorTile(
                title = stringResource(R.string.now_playing_chip_text_color),
                color = textColorDraft,
                onDraftChange = { textColorDraft = it },
                onColorChange = onTextColorChange,
            )
            ChipColorTile(
                title = stringResource(R.string.now_playing_chip_color),
                color = chipColorDraft,
                onDraftChange = { chipColorDraft = it },
                onColorChange = onChipColorChange,
            )
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
}

@Composable
private fun ChipPreview(
    textColor: Int,
    chipColor: Int,
    opacity: Int,
    font: NowPlayingChipFont,
    titleWidth: Int,
) {
    val fontFamily = remember(font) { FontFamily(font.typeface()) }
    val barShape = MaterialTheme.shapes.medium
    Row(horizontalArrangement = Arrangement.spacedBy(FieldGap)) {
        listOf(Color.Black to Color.White, Color.White to Color.Black).forEach { (barColor, statusBarTint) ->
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = PreviewBarHeight)
                    .clip(barShape)
                    .background(barColor)
                    .border(OutlineBorderWidth, MaterialTheme.colorScheme.outlineVariant, barShape)
                    .padding(horizontal = PreviewBarPadding, vertical = PreviewChipPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                PreviewChip(
                    textColor = customColor(textColor) ?: statusBarTint,
                    pillColor = (customColor(chipColor) ?: statusBarTint).copy(alpha = opacity / Prefs.NOW_PLAYING_OPACITY_MAX.toFloat()),
                    fontFamily = fontFamily,
                    titleWidth = titleWidth,
                )
            }
        }
    }
}

@Composable
private fun PreviewChip(
    textColor: Color,
    pillColor: Color,
    fontFamily: FontFamily,
    titleWidth: Int,
) {
    Row(
        Modifier.clip(CircleShape).background(pillColor).padding(PreviewChipPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(PreviewIconSize).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(Modifier.width(PreviewIconGap))
        Text(
            stringResource(R.string.now_playing_chip_preview_title),
            modifier = Modifier.widthIn(max = titleWidth.dp),
            color = textColor,
            fontSize = PreviewTitleSize,
            fontFamily = fontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChipFontTile(
    font: NowPlayingChipFont,
    onSelect: (NowPlayingChipFont) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val options = remember { NowPlayingChipFont.entries.filter { it.isAvailable } }
    Box {
        Tile(shape = TileShape, onClick = { open = true }) {
            TileText(
                title = stringResource(R.string.now_playing_chip_font),
                supporting = stringResource(font.labelRes),
                supportingStyle = AppText.tileSupporting.copy(fontFamily = remember(font) { FontFamily(font.typeface()) }),
            )
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, option ->
                SelectableDropdownMenuItem(
                    selected = option == font,
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                    text = {
                        Text(
                            stringResource(option.labelRes),
                            fontFamily = remember(option) { FontFamily(option.typeface()) },
                        )
                    },
                    shapes = MenuDefaults.itemShape(index, options.size),
                )
            }
        }
    }
}

@Composable
private fun ChipColorTile(
    title: String,
    color: Int,
    onDraftChange: (Int) -> Unit,
    onColorChange: (Int) -> Unit,
) {
    val follows = color == Prefs.NOW_PLAYING_COLOR_FOLLOWS_STATUS_BAR
    val presets = presetColors()
    val isCustom = !follows && color !in presets
    val customSeed = if (isCustom) color else MaterialTheme.colorScheme.primary.toArgb()
    var customEditorOpen by rememberSaveable { mutableStateOf(isCustom) }
    val choose = { chosen: Int ->
        customEditorOpen = false
        onDraftChange(chosen)
        onColorChange(chosen)
    }
    Tile(shape = TileShape) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FieldGap)) {
            Column {
                Text(title, style = AppText.tileTitle, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (follows) stringResource(R.string.now_playing_chip_color_follows) else color.hexCode(),
                    style = if (follows) AppText.tileSupporting else AppText.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow {
                ColorSwatch(
                    fill = followsStatusBarFill,
                    description = stringResource(R.string.now_playing_chip_color_follows),
                    selected = follows && !customEditorOpen,
                    onClick = { choose(Prefs.NOW_PLAYING_COLOR_FOLLOWS_STATUS_BAR) },
                )
                presets.forEach { preset ->
                    ColorSwatch(
                        fill = SolidColor(Color(preset)),
                        description = preset.hexCode(),
                        selected = color == preset && !customEditorOpen,
                        onClick = { choose(preset) },
                    )
                }
                ColorSwatch(
                    fill = customColorFill,
                    description = stringResource(R.string.now_playing_chip_color_custom),
                    selected = customEditorOpen || isCustom,
                    onClick = {
                        choose(customSeed)
                        customEditorOpen = true
                    },
                )
            }
            AnimatedVisibility(customEditorOpen) {
                CustomColorEditor(
                    color = color,
                    onDraftChange = onDraftChange,
                    onColorChange = onColorChange,
                )
            }
        }
    }
}

@Composable
private fun presetColors(): List<Int> =
    listOf(
        PlatformColor.WHITE,
        PlatformColor.BLACK,
        colorResource(android.R.color.system_accent1_200).toArgb(),
        colorResource(android.R.color.system_accent3_200).toArgb(),
    )

@Composable
private fun ColorSwatch(
    fill: Brush,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val ringColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        Modifier
            .size(SwatchTouchSize)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().border(SwatchSelectedRingWidth, ringColor, CircleShape))
        Box(
            Modifier
                .size(SwatchSize)
                .clip(CircleShape)
                .background(fill)
                .border(OutlineBorderWidth, MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}

@Composable
private fun CustomColorEditor(
    color: Int,
    onDraftChange: (Int) -> Unit,
    onColorChange: (Int) -> Unit,
) {
    var hsv by remember { mutableStateOf(color.toHsv()) }
    LaunchedEffect(color) {
        if (hsv.toColorInt() != color) hsv = color.toHsv()
    }
    var hexInput by remember(color) { mutableStateOf(color.hexCode().removePrefix("#")) }
    val updateDraft = { next: Hsv ->
        hsv = next
        onDraftChange(next.toColorInt())
    }
    val commitDraft = { onColorChange(hsv.toColorInt()) }
    Column(verticalArrangement = Arrangement.spacedBy(FieldGap)) {
        GradientSlider(
            label = stringResource(R.string.now_playing_chip_color_hue),
            value = hsv.hue,
            range = 0f..HUE_MAX_DEGREES,
            fill = Brush.horizontalGradient(hueColors),
            onValueChange = { updateDraft(hsv.copy(hue = it)) },
            onValueChangeFinished = commitDraft,
        )
        GradientSlider(
            label = stringResource(R.string.now_playing_chip_color_saturation),
            value = hsv.saturation,
            range = 0f..1f,
            fill = Brush.horizontalGradient(listOf(Color.hsv(hsv.hue, 0f, hsv.value), Color.hsv(hsv.hue, 1f, hsv.value))),
            onValueChange = { updateDraft(hsv.copy(saturation = it)) },
            onValueChangeFinished = commitDraft,
        )
        GradientSlider(
            label = stringResource(R.string.now_playing_chip_color_brightness),
            value = hsv.value,
            range = 0f..1f,
            fill = Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hsv.hue, hsv.saturation, 1f))),
            onValueChange = { updateDraft(hsv.copy(value = it)) },
            onValueChangeFinished = commitDraft,
        )
        OutlinedTextField(
            value = hexInput,
            onValueChange = { input ->
                hexInput = input.filter { it.isHexDigit() }.take(HEX_DIGITS).uppercase()
                if (hexInput.length == HEX_DIGITS) {
                    val typed = OPAQUE_ALPHA or hexInput.toInt(HEX_RADIX)
                    hsv = typed.toHsv()
                    onDraftChange(typed)
                    onColorChange(typed)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = AppText.meta,
            label = { Text(stringResource(R.string.now_playing_chip_color_hex)) },
            prefix = { Text("#", style = AppText.meta) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        )
    }
}

@Composable
private fun GradientSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    fill: Brush,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column {
        Text(label, style = AppText.tileSupporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(GradientTrackHeight)
                    .clip(CircleShape)
                    .background(fill)
                    .border(OutlineBorderWidth, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.semantics { contentDescription = label },
                onValueChangeFinished = onValueChangeFinished,
                valueRange = range,
                colors =
                    SliderDefaults.colors(
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
            )
        }
    }
}

private data class Hsv(
    val hue: Float,
    val saturation: Float,
    val value: Float,
) {
    fun toColorInt(): Int = PlatformColor.HSVToColor(floatArrayOf(hue, saturation, value))
}

private fun Int.toHsv(): Hsv {
    val components = FloatArray(3)
    PlatformColor.colorToHSV(this, components)
    return Hsv(components[0], components[1], components[2])
}

private fun Int.hexCode(): String = "#%06X".format(this and RGB_MASK)

private fun customColor(stored: Int): Color? = stored.takeIf { (it and OPAQUE_ALPHA) == OPAQUE_ALPHA }?.let(::Color)

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
