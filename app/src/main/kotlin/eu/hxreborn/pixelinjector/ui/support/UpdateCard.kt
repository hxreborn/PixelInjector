package eu.hxreborn.pixelinjector.ui.support

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.support.Release
import eu.hxreborn.pixelinjector.support.SentReport
import eu.hxreborn.pixelinjector.ui.theme.AppText

private val FixMarkIndent = 14.dp
private const val SHORT_ID_LENGTH = 8
private val FixMarkIconSize = 16.dp
private val ExpandIconSize = 20.dp

@Composable
fun UpdateCard(
    release: Release,
    fixes: Map<String, List<SentReport>>,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(false) }
    val newLines = release.notesNew.filter { expanded || it in fixes }
    val fixedLines = release.notesFixed.filter { expanded || it in fixes }
    val otherLines = release.notesOther.filter { expanded || it in fixes }
    val total = release.notesNew.size + release.notesFixed.size + release.notesOther.size
    val hiddenWhenCollapsed = total - (release.notesNew + release.notesFixed + release.notesOther).count { it in fixes }
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.NewReleases, contentDescription = null)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.update_card_title, release.versionName),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    NoteGroup(stringResource(R.string.notes_new), newLines, fixes)
                    NoteGroup(stringResource(R.string.notes_fixed), fixedLines, fixes)
                    NoteGroup(stringResource(R.string.notes_other), otherLines, fixes)
                    if (hiddenWhenCollapsed > 0) {
                        val plural = if (hiddenWhenCollapsed == total) R.plurals.notes_count else R.plurals.notes_more
                        Row(
                            Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { expanded = !expanded },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (expanded) {
                                    stringResource(
                                        R.string.notes_less,
                                    )
                                } else {
                                    pluralStringResource(plural, hiddenWhenCollapsed, hiddenWhenCollapsed)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Icon(
                                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(ExpandIconSize),
                            )
                        }
                    }
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
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
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
    fixes: Map<String, List<SentReport>>,
) {
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        lines.forEach { line ->
            NoteLine(line)
            fixes[line]?.let { FixMark(it) }
        }
    }
}

@Composable
fun NoteLine(line: String) {
    Row {
        Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(FixMarkIndent).clearAndSetSemantics { })
        Text(line.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun FixMark(reports: List<SentReport>) {
    Row(
        Modifier.padding(start = FixMarkIndent),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(FixMarkIconSize))
        Text(
            stringResource(R.string.fixes_your_report, reports.joinToString(" ") { it.short ?: it.id.take(SHORT_ID_LENGTH) }),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
