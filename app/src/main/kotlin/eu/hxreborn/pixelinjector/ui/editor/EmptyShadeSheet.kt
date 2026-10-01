package eu.hxreborn.pixelinjector.ui.editor

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.icons.ShadeIcons
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.theme.AppText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ICON_ASSET = "shade_icons.tsv"
private const val STOCK_ICON = "trophy"
private const val NO_ICON_NAME = "no_icon"
private const val SHEET_HEIGHT_FRACTION = 0.8f
private val IconGap = 8.dp
private val IconCell = 56.dp
private val IconSize = 24.dp
private val SectionGap = 16.dp

private class ShadeIcon(
    val name: String,
    val iconPath: String,
    val bitmap: ImageBitmap,
)

@Composable
fun EmptyShadeSheet(
    text: String,
    iconPath: String,
    onTextChange: (String) -> Unit,
    onIconPathChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val iconPx = with(LocalDensity.current) { IconSize.roundToPx() }
    val icons by produceState(emptyList<ShadeIcon>()) {
        value = withContext(Dispatchers.Default) { loadShadeIcons(context, iconPx) }
    }
    val input = rememberTextFieldState(text)
    val commit by rememberUpdatedState { onTextChange(input.text.toString().trim()) }
    DisposableEffect(Unit) { onDispose { commit() } }

    Column(Modifier.fillMaxHeight(SHEET_HEIGHT_FRACTION).imePadding().padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
        SheetHeader(stringResource(R.string.tweak_empty_shade_edit), stringResource(R.string.tweak_empty_shade_desc))
        OutlinedTextField(
            state = input,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.empty_shade_text_label)) },
            placeholder = { Text(stringResource(R.string.empty_shade_text_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onKeyboardAction = { commit() },
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        Text(
            stringResource(R.string.empty_shade_icon_label),
            style = AppText.tileSupporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = SectionGap, bottom = IconGap),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(IconCell),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(IconGap),
            verticalArrangement = Arrangement.spacedBy(IconGap),
        ) {
            items(icons, key = { it.name }) { item ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FilledTonalIconToggleButton(
                        checked = item.iconPath == iconPath,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onIconPathChange(item.iconPath)
                        },
                    ) {
                        Icon(item.bitmap, contentDescription = item.name.replace('_', ' '), modifier = Modifier.size(IconSize))
                    }
                }
            }
        }
    }
}

private fun loadShadeIcons(
    context: Context,
    iconPx: Int,
): List<ShadeIcon> {
    fun shadeIcon(
        name: String,
        pathData: String,
    ): ShadeIcon {
        val bitmap = ShadeIcons.fromPathData(pathData).toBitmap(iconPx, iconPx).asImageBitmap()
        return ShadeIcon(name, if (name == STOCK_ICON) "" else pathData, bitmap)
    }
    val assetIcons =
        context.assets.open(ICON_ASSET).bufferedReader().useLines { lines ->
            lines
                .filter(String::isNotBlank)
                .map { it.split('\t', limit = 2) }
                .map { (name, pathData) -> shadeIcon(name, pathData) }
                .toList()
        }
    return listOf(shadeIcon(NO_ICON_NAME, ShadeIcons.NO_ICON)) + assetIcons
}
