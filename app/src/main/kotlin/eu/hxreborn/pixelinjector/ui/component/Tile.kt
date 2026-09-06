package eu.hxreborn.pixelinjector.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.ui.theme.AppText

val SectionGap = 14.dp
val TileGap = 2.dp
private val TileStartPadding = 16.dp
private val TileEndPadding = 8.dp
private val TileVerticalPadding = 10.dp
private val TileMinHeight = 60.dp
private val TileSlotSpacing = 12.dp
private val TileOuterRadius = 20.dp
private val TileInnerRadius = 4.dp
private val SectionDetailGap = 6.dp
private const val SEGMENT_BREAK = "\u200B"

private val tileOnly = RoundedCornerShape(TileOuterRadius)
private val tileFirst =
    RoundedCornerShape(
        topStart = TileOuterRadius,
        topEnd = TileOuterRadius,
        bottomEnd = TileInnerRadius,
        bottomStart = TileInnerRadius,
    )
private val tileLast =
    RoundedCornerShape(
        topStart = TileInnerRadius,
        topEnd = TileInnerRadius,
        bottomEnd = TileOuterRadius,
        bottomStart = TileOuterRadius,
    )
private val tileMiddle = RoundedCornerShape(TileInnerRadius)

fun tileShape(
    count: Int,
    index: Int,
): RoundedCornerShape =
    when {
        count == 1 -> tileOnly
        index == 0 -> tileFirst
        index == count - 1 -> tileLast
        else -> tileMiddle
    }

@Composable
fun Tile(
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    startPadding: Dp = TileStartPadding,
    endPadding: Dp = TileEndPadding,
    verticalPadding: Dp = TileVerticalPadding,
    minHeight: Dp = TileMinHeight,
    onClick: (() -> Unit)? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val row: @Composable () -> Unit = {
        Row(
            modifier =
                Modifier
                    .heightIn(min = minHeight)
                    .padding(start = startPadding, end = endPadding, top = verticalPadding, bottom = verticalPadding),
            horizontalArrangement = Arrangement.spacedBy(TileSlotSpacing),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
    when {
        checked != null && onCheckedChange != null -> {
            Surface(
                checked = checked,
                onCheckedChange = onCheckedChange,
                shape = shape,
                color = color,
                modifier = modifier.fillMaxWidth(),
            ) { row() }
        }

        onClick != null -> {
            Surface(onClick = onClick, shape = shape, color = color, modifier = modifier.fillMaxWidth()) { row() }
        }

        else -> {
            Surface(shape = shape, color = color, modifier = modifier.fillMaxWidth()) { row() }
        }
    }
}

@Composable
fun RowScope.TileText(
    title: String,
    supporting: String? = null,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    supportingStyle: TextStyle = AppText.tileSupporting,
    singleLine: Boolean = false,
) {
    val maxLines = if (singleLine) 1 else Int.MAX_VALUE
    val overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip
    Column(Modifier.weight(1f)) {
        Text(title, style = AppText.tileTitle, color = MaterialTheme.colorScheme.onSurface, maxLines = maxLines, overflow = overflow)
        if (supporting != null) {
            Text(supporting, style = supportingStyle, color = supportingColor, maxLines = maxLines, overflow = overflow)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    details: List<String> = emptyList(),
) {
    FlowRow(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp).semantics { heading() },
        horizontalArrangement = Arrangement.spacedBy(SectionDetailGap),
        itemVerticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = AppText.subheader, color = MaterialTheme.colorScheme.primary)
        if (details.isNotEmpty()) {
            val detail = if (details.size == 1) "(${details[0]})" else details.joinToString(prefix = "[", postfix = "]")
            Text(
                detail.replace(".", ".$SEGMENT_BREAK"),
                style = AppText.appBarVersion,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun LazyListScope.sectionHeader(
    title: String,
    details: List<String> = emptyList(),
) {
    item(key = "section:$title") { SectionHeader(title, details) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun <T> LazyListScope.tileGroup(
    items: List<T>,
    key: (T) -> Any,
    content: @Composable (item: T, shape: RoundedCornerShape) -> Unit,
) {
    itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
        Column(
            Modifier.animateItem(
                fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            ),
        ) {
            content(item, tileShape(items.size, index))
            if (index < items.lastIndex) Spacer(Modifier.height(TileGap))
        }
    }
}
