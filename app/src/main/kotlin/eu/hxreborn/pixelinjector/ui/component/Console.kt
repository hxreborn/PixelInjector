package eu.hxreborn.pixelinjector.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.ui.reload.LogLine
import eu.hxreborn.pixelinjector.ui.reload.Tone
import eu.hxreborn.pixelinjector.ui.reload.logTimestamp
import eu.hxreborn.pixelinjector.ui.theme.AppText
import java.util.Date

@Composable
fun toneColor(
    tone: Tone,
    neutral: Color,
): Color =
    when (tone) {
        Tone.OK -> MaterialTheme.colorScheme.tertiary
        Tone.WARN -> MaterialTheme.colorScheme.secondary
        Tone.BAD -> MaterialTheme.colorScheme.error
        Tone.NEUTRAL -> neutral
    }

@Composable
fun Console(
    lines: List<LogLine>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(lines) { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(logTimestamp.format(Date(line.epochMs)), style = AppText.console, color = MaterialTheme.colorScheme.outline)
                    Text(
                        processNameForDisplay(line.text),
                        style = AppText.console,
                        color = toneColor(line.tone, MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
