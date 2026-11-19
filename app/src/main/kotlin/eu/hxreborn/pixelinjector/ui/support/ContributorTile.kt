package eu.hxreborn.pixelinjector.ui.support

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.ui.component.SectionGap
import eu.hxreborn.pixelinjector.ui.component.SectionHeader
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.component.TileGap
import eu.hxreborn.pixelinjector.ui.component.tileShape
import eu.hxreborn.pixelinjector.ui.theme.AppText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DiffAddedLight = Color(0xFF005F40)
private val DiffAddedDark = Color(0xFF76DAAA)
private val AvatarSize = 40.dp
private val OwnerMinHeight = 20.dp
private val OwnerPadding = 8.dp
private val DiffItemGap = 6.dp
private val DiffBlockSize = 9.dp
private val DiffBlockGap = 2.dp
private val HatchGap = 3.dp
private val HatchStroke = 1.dp
private const val DIFF_BLOCK_COUNT = 5

@Composable
internal fun ContributorsSection(onOpenUrl: (String) -> Unit) {
    val assets = LocalContext.current.applicationContext.assets
    val contributors by produceState(emptyList<Contributor>(), assets) {
        value = withContext(Dispatchers.IO) { ContributorSnapshot.load(assets) }
    }
    if (contributors.isEmpty()) return

    Spacer(Modifier.height(SectionGap))
    SectionHeader(stringResource(R.string.contributors))
    Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
        contributors.forEachIndexed { index, contributor ->
            ContributorTile(contributor, tileShape(contributors.size, index), onOpenUrl)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContributorTile(
    contributor: Contributor,
    shape: RoundedCornerShape,
    onOpenUrl: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val avatarShape = MaterialShapes.Cookie12Sided.toShape()
    val ownerLabel = stringResource(R.string.contributor_owner)
    val title =
        if (contributor.owner) {
            stringResource(R.string.contributor_owner_name, contributor.login, ownerLabel)
        } else {
            contributor.login
        }
    val description = stringResource(R.string.contributor_diff_summary, title, contributor.added, contributor.removed)
    Tile(shape = shape, onClick = { onOpenUrl(contributor.url) }) {
        val avatar = contributor.avatar
        if (avatar != null) {
            Image(
                bitmap = avatar,
                contentDescription = null,
                modifier = Modifier.size(AvatarSize).clip(avatarShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Surface(
                modifier = Modifier.size(AvatarSize).clearAndSetSemantics {},
                shape = avatarShape,
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(contributor.login.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = description }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(OwnerPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    contributor.login,
                    modifier = Modifier.weight(1f, fill = false),
                    style = AppText.tileTitle,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (contributor.owner) {
                    Surface(shape = CircleShape, color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
                        Box(
                            modifier = Modifier.heightIn(min = OwnerMinHeight).padding(horizontal = OwnerPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                ownerLabel,
                                style =
                                    MaterialTheme.typography.labelSmall.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                                    ),
                            )
                        }
                    }
                }
            }
            ContributorDiffstat(contributor.added, contributor.removed)
        }
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

@Composable
private fun ContributorDiffstat(
    added: Long,
    removed: Long,
) {
    val addedColor = if (isSystemInDarkTheme()) DiffAddedDark else DiffAddedLight
    val removedColor = MaterialTheme.colorScheme.error
    val neutralColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val style = AppText.tileSupporting.copy(fontFeatureSettings = "tnum")
    val total = added + removed
    val addedBlocks = if (total == 0L) 0 else (DIFF_BLOCK_COUNT * added / total).toInt()
    val removedBlocks = if (total == 0L) 0 else (DIFF_BLOCK_COUNT * removed / total).toInt()
    val density = LocalDensity.current
    val hatch =
        remember(density) {
            val block = with(density) { DiffBlockSize.toPx() }
            val gap = with(density) { HatchGap.toPx() }
            Path().apply {
                var offset = gap
                while (offset < block * 2) {
                    moveTo(offset, 0f)
                    lineTo(offset - block, block)
                    offset += gap
                }
            }
        }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(DiffItemGap),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.contributor_diff_added, added), style = style, color = addedColor)
        Text(stringResource(R.string.contributor_diff_removed, removed), style = style, color = removedColor)
        Canvas(
            Modifier
                .size(DiffBlockSize * DIFF_BLOCK_COUNT + DiffBlockGap * (DIFF_BLOCK_COUNT - 1), DiffBlockSize)
                .clearAndSetSemantics {},
        ) {
            val block = DiffBlockSize.toPx()
            val stride = block + DiffBlockGap.toPx()
            repeat(DIFF_BLOCK_COUNT) { index ->
                val left = index * stride
                val color = if (index < addedBlocks) addedColor else neutralColor
                drawRect(color, topLeft = Offset(left, 0f), size = Size(block, block))
                if (index in addedBlocks until (addedBlocks + removedBlocks)) {
                    clipRect(left = left, right = left + block) {
                        translate(left = left) {
                            drawPath(hatch, removedColor, style = Stroke(width = HatchStroke.toPx()))
                        }
                    }
                }
            }
        }
    }
}
