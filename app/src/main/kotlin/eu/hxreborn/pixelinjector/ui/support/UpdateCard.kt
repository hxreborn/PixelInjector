package eu.hxreborn.pixelinjector.ui.support

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.support.Release
import eu.hxreborn.pixelinjector.support.SentReport
import eu.hxreborn.pixelinjector.ui.theme.AppText

private const val LINES_PER_GROUP = 2
private const val MARK_ALPHA = 0.12f

@Composable
fun UpdateCard(
    release: Release,
    fixed: List<SentReport>,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shown = release.notesNew.take(LINES_PER_GROUP) + release.notesFixed.take(LINES_PER_GROUP)
    val more = release.notesNew.size + release.notesFixed.size + release.notesOther.size - shown.size
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.update_card_title, stringResource(R.string.app_name), release.versionName),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    fixed.forEach { report ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(scheme.onPrimaryContainer.copy(alpha = MARK_ALPHA), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Column {
                                Text(stringResource(R.string.fixes_your_report), style = MaterialTheme.typography.labelLarge)
                                Text(reportTitle(report), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    NoteGroup(stringResource(R.string.notes_new), release.notesNew.take(LINES_PER_GROUP))
                    NoteGroup(stringResource(R.string.notes_fixed), release.notesFixed.take(LINES_PER_GROUP))
                    if (more > 0) Text(pluralStringResource(R.plurals.notes_more, more, more), style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(
                    onClick = onLater,
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.later), color = scheme.onPrimaryContainer) }
                Button(
                    onClick = onUpdate,
                    shapes = ButtonDefaults.shapes(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = scheme.onPrimaryContainer,
                            contentColor = scheme.primaryContainer,
                        ),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.update), style = AppText.button)
                }
            }
        }
    }
}

@Composable
private fun NoteGroup(
    title: String,
    lines: List<String>,
) {
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        lines.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
    }
}
