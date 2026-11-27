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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.support.Release
import eu.hxreborn.pixelinjector.support.SentReport
import eu.hxreborn.pixelinjector.support.assetDownloadUrl
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.theme.AppText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ActionHeight = 56.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateSheet(
    release: Release,
    fixes: Map<String, List<SentReport>>,
    onOpenUrl: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var resolving by remember { mutableStateOf(false) }
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetHeader(
            stringResource(R.string.update_sheet_title, release.versionName),
        )
        ReleaseNotes(release, fixes)
        val apkUrl = release.apkUrl
        if (apkUrl == null) {
            Primary(stringResource(R.string.update_open_release), Icons.AutoMirrored.Outlined.OpenInNew) {
                onOpenUrl(release.url)
            }
            return@Column
        }
        Text(
            stringResource(R.string.update_browser_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Primary(stringResource(R.string.update_install), Icons.Outlined.Download, enabled = !resolving) {
            resolving = true
            scope.launch {
                val downloadUrl = withContext(Dispatchers.IO) { assetDownloadUrl(apkUrl) }
                resolving = false
                onOpenUrl(downloadUrl)
            }
        }
    }
}

@Composable
private fun Primary(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth().heightIn(min = ActionHeight),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label, style = AppText.button)
    }
}
