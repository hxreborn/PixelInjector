package eu.hxreborn.pixelinjector.ui.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.support.Release
import eu.hxreborn.pixelinjector.support.SentReport
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.theme.AppText

private val ActionHeight = 56.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateSheet(
    release: Release,
    fixes: Map<String, List<SentReport>>,
    onOpenRelease: () -> Unit,
) {
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetHeader(stringResource(R.string.update_sheet_title, release.versionName))
        ReleaseNotes(release, fixes)
        Primary(stringResource(R.string.update_open_release), onOpenRelease)
    }
}

@Composable
private fun Primary(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth().heightIn(min = ActionHeight),
    ) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label, style = AppText.button)
    }
}
