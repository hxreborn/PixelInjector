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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.support.InstallStep
import eu.hxreborn.pixelinjector.support.Release
import eu.hxreborn.pixelinjector.support.SentReport
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.theme.AppText

private val ActionHeight = 56.dp
private val FailureTextSpacing = 4.dp
private const val PERCENT = 100

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateSheet(
    release: Release?,
    fixes: Map<String, List<SentReport>>,
    install: InstallStep,
    onInstall: () -> Unit,
    onDiscardStagedUpdate: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetHeader(
            if (release == null) stringResource(R.string.update) else stringResource(R.string.update_sheet_title, release.versionName),
        )
        if (release != null) ReleaseNotes(release, fixes)
        if (install == InstallStep.Recovering) {
            Text(stringResource(R.string.update_staged_summary), style = MaterialTheme.typography.bodyMedium)
            Primary(stringResource(R.string.update_discard_staged), Icons.Outlined.Close, true, onDiscardStagedUpdate)
            return@Column
        }
        if (!install.busy && release?.asset == null) {
            Primary(stringResource(R.string.update_open_release), Icons.AutoMirrored.Outlined.OpenInNew, true, onOpenRelease)
            return@Column
        }
        if (install is InstallStep.Failed) Failure(install)
        Primary(
            installLabel(install),
            Icons.Outlined.Download,
            !install.busy,
            onInstall,
        )
        if (install is InstallStep.Failed) {
            TextButton(onClick = onOpenRelease, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_open_release))
            }
        }
    }
}

@Composable
private fun installLabel(install: InstallStep): String =
    when (install) {
        is InstallStep.Downloading -> {
            val percent = if (install.total > 0) (install.bytes * PERCENT / install.total).toInt() else 0
            stringResource(R.string.update_downloading, percent)
        }

        InstallStep.Verifying -> {
            stringResource(R.string.update_verifying)
        }

        is InstallStep.Failed -> {
            stringResource(R.string.update_try_again)
        }

        InstallStep.Confirming -> {
            stringResource(R.string.update_confirming)
        }

        InstallStep.Idle, InstallStep.Recovering -> {
            stringResource(R.string.update_install)
        }
    }

@Composable
private fun Failure(failure: InstallStep.Failed) {
    val title =
        when (failure.cause) {
            InstallStep.Failed.Cause.DOWNLOAD -> R.string.update_failed_download
            InstallStep.Failed.Cause.ASSET -> R.string.update_failed_asset
            InstallStep.Failed.Cause.NO_CHECKSUM -> R.string.update_failed_no_checksum
            InstallStep.Failed.Cause.CHECKSUM -> R.string.update_failed_checksum
            InstallStep.Failed.Cause.PACKAGE -> R.string.update_failed_package
            InstallStep.Failed.Cause.VERSION -> R.string.update_failed_version
            InstallStep.Failed.Cause.SIGNER -> R.string.update_failed_signer
            InstallStep.Failed.Cause.INSTALL -> R.string.update_failed_install
        }
    Column(verticalArrangement = Arrangement.spacedBy(FailureTextSpacing)) {
        Text(stringResource(title), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        failure.detail?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = AppText.count)
        }
    }
}

@Composable
private fun Primary(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
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
