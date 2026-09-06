package eu.hxreborn.pixelinjector.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.ui.component.DetailScaffold
import eu.hxreborn.pixelinjector.ui.component.SectionGap
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.component.tileGroup
import eu.hxreborn.pixelinjector.ui.component.tileShape
import eu.hxreborn.pixelinjector.ui.theme.AppText
import eu.hxreborn.pixelinjector.ui.theme.RobotoMono

private val ruleShape = Regex("""[^:]+:[^:]+(:.*)?""")
private val FormatEndPadding = 16.dp
private val RuleEndPadding = 4.dp

@Composable
fun RulesEditorScreen(
    rules: String,
    onRulesChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    val lines =
        remember(rules) {
            rules
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }
    val input = rememberTextFieldState()
    val mono = AppText.tileTitle.copy(fontFamily = RobotoMono)
    val commit = { next: List<String> -> onRulesChange(next.joinToString("\n")) }
    val add = {
        val rule = input.text.toString().trim()
        if (rule.isNotEmpty() && rule !in lines) {
            commit(lines + rule)
            input.clearText()
        }
    }
    DetailScaffold(
        title = stringResource(R.string.tweak_new_task_rules),
        subtitle = stringResource(R.string.tweak_new_task_rules_desc),
        onBack = onBack,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { padding ->
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = padding) {
            item(key = "format") {
                Tile(shape = tileShape(1, 0), endPadding = FormatEndPadding) {
                    Column {
                        Text(stringResource(R.string.rules_format), style = mono, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            stringResource(R.string.rules_format_help),
                            style = AppText.tileSupporting,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(SectionGap))
            }
            if (lines.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.rules_empty),
                        style = AppText.tileSupporting,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            tileGroup(lines, key = { it }) { rule, shape ->
                Tile(shape = shape, endPadding = RuleEndPadding) {
                    Column(Modifier.weight(1f)) {
                        Text(rule, style = mono, color = MaterialTheme.colorScheme.onSurface)
                        if (!ruleShape.matches(rule)) {
                            Text(
                                stringResource(R.string.rule_invalid),
                                style = AppText.tileSupporting,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    IconButton(onClick = { commit(lines - rule) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.rule_delete))
                    }
                }
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                state = input,
                modifier = Modifier.weight(1f),
                textStyle = mono,
                placeholder = { Text("com.source.app:com.target.app:ir", fontFamily = RobotoMono) },
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                onKeyboardAction = { add() },
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = add) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.rule_add))
            }
        }
    }
}
