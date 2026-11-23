package eu.hxreborn.pixelinjector.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.ui.theme.AppText

@Composable
fun SheetHeader(
    title: String,
    subtitle: String? = null,
    action: @Composable FlowRowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(Modifier.padding(top = 4.dp, bottom = 12.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = AppText.sheetTitle)
            action()
        }
        if (subtitle != null) {
            Text(subtitle, style = AppText.sheetSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

@Composable
fun ResetToDefaultButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(
            Icons.Outlined.SettingsBackupRestore,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Text(
            stringResource(R.string.reset_to_default),
            style = AppText.button,
            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
        )
    }
}

@Composable
fun SheetActions(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        content = content,
    )
}
