package eu.hxreborn.pixelinjector.ui.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.support.InstallStep
import eu.hxreborn.pixelinjector.support.Release
import eu.hxreborn.pixelinjector.support.SentReport
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.theme.AppText

private val BlockRadius = 16.dp
private val ActionHeight = 56.dp
private const val HASH_HEAD = 8
private const val HASH_TAIL = 4

class UpdateActions(
    val onInstall: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onGitHub: () -> Unit,
    val onCancel: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateSheet(
    release: Release,
    fixed: List<SentReport>,
    step: InstallStep,
    actions: UpdateActions,
) {
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val size = release.asset?.size?.let(::bytesLabel)
        SheetHeader(
            stringResource(R.string.update_sheet_title, release.versionName),
            size?.let { stringResource(R.string.update_from_github, it) },
        )
        when (step) {
            InstallStep.Idle, InstallStep.NeedsPermission -> {
                fixed.forEach { report ->
                    Block(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Icons.Outlined.Check) {
                        Text(stringResource(R.string.fixes_your_report), style = AppText.tileTitle)
                        Text(listOfNotNull(reportTitle(report), report.short).joinToString("  "), style = AppText.tileSupporting)
                    }
                }
                Notes(stringResource(R.string.notes_new), release.notesNew)
                Notes(stringResource(R.string.notes_fixed), release.notesFixed)
                Notes(stringResource(R.string.notes_other), release.notesOther)
                if (step == InstallStep.NeedsPermission) {
                    Block(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.onSurface, Icons.Outlined.Shield) {
                        Text(stringResource(R.string.update_allow_title), style = AppText.tileTitle)
                        Text(
                            stringResource(R.string.update_allow_body),
                            style = AppText.tileSupporting,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Primary(stringResource(R.string.update_open_settings), Icons.AutoMirrored.Outlined.OpenInNew, actions.onOpenSettings)
                } else {
                    if (release.asset == null) {
                        Text(
                            stringResource(R.string.update_no_asset),
                            style = AppText.tileSupporting,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Primary(
                        stringResource(R.string.update_install),
                        Icons.Outlined.Download,
                        actions.onInstall,
                        enabled =
                            release.asset != null,
                    )
                    GitHubLink(actions.onGitHub)
                }
            }

            is InstallStep.Downloading -> {
                val fraction = if (step.total > 0) step.bytes.toFloat() / step.total else 0f
                LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Row {
                    Text(
                        stringResource(R.string.update_progress, bytesLabel(step.bytes), bytesLabel(step.total)),
                        style = AppText.meta,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${(fraction * 100).toInt()}%", style = AppText.meta)
                }
                VerifyLog(release.asset?.sha256, step.partialHash, hashing = true, installed = null, file = null, signerChecking = false)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.update_installer_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = actions.onCancel, shapes = ButtonDefaults.shapes(), modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.cancel))
                }
            }

            is InstallStep.Verifying -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(Modifier.size(32.dp))
                    Text(stringResource(R.string.update_verifying), style = MaterialTheme.typography.bodyMedium)
                }
                VerifyLog(
                    step.expected,
                    step.actual,
                    hashing = false,
                    installed = step.installedSigner,
                    file = step.fileSigner,
                    signerChecking =
                        step.fileSigner == null,
                )
            }

            is InstallStep.Rejected -> {
                val body =
                    when (step.cause) {
                        InstallStep.Rejected.Cause.CHECKSUM -> R.string.update_bad_checksum
                        InstallStep.Rejected.Cause.SIGNER -> R.string.update_bad_signer
                        InstallStep.Rejected.Cause.PACKAGE -> R.string.update_bad_package
                        InstallStep.Rejected.Cause.NO_CHECKSUM -> R.string.update_no_checksum
                    }
                Block(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Outlined.GppBad) {
                    Text(stringResource(R.string.update_not_installed), style = AppText.tileTitle)
                    Text(stringResource(body), style = AppText.tileSupporting)
                }
                when (step.cause) {
                    InstallStep.Rejected.Cause.CHECKSUM -> {
                        VerifyLog(
                            step.expected,
                            step.actual,
                            hashing = false,
                            installed = null,
                            file = null,
                            signerChecking = false,
                            failed = true,
                        )
                    }

                    InstallStep.Rejected.Cause.SIGNER -> {
                        VerifyLog(
                            step.checksum,
                            step.checksum,
                            hashing = false,
                            installed = step.expected,
                            file = step.actual,
                            signerChecking = false,
                            failed = true,
                        )
                    }

                    else -> {}
                }
                Primary(stringResource(R.string.update_download_again), Icons.Outlined.Download, actions.onInstall)
                GitHubLink(actions.onGitHub)
            }

            is InstallStep.DownloadFailed -> {
                Block(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Outlined.ErrorOutline) {
                    Text(stringResource(R.string.update_download_failed, bytesLabel(step.bytes)), style = AppText.tileTitle)
                    Text(stringResource(R.string.update_download_failed_body), style = AppText.tileSupporting)
                    Text(step.reason, style = AppText.meta)
                }
                Primary(stringResource(R.string.try_again), Icons.Outlined.Refresh, actions.onInstall)
                GitHubLink(actions.onGitHub)
            }

            InstallStep.Installing -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(Modifier.size(32.dp))
                    Text(stringResource(R.string.update_waiting_installer), style = MaterialTheme.typography.bodyMedium)
                }
            }

            is InstallStep.InstallFailed -> {
                Block(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Outlined.ErrorOutline) {
                    Text(stringResource(R.string.update_install_failed, step.message), style = AppText.tileSupporting)
                }
                Primary(stringResource(R.string.try_again), Icons.Outlined.Refresh, actions.onInstall)
                GitHubLink(actions.onGitHub)
            }
        }
    }
}

@Composable
private fun Notes(
    title: String,
    lines: List<String>,
) {
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        lines.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Block(
    container: Color,
    content: Color,
    icon: ImageVector,
    body: @Composable () -> Unit,
) {
    Surface(shape = RoundedCornerShape(BlockRadius), color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null)
            Column { body() }
        }
    }
}

@Composable
private fun Primary(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
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

@Composable
private fun GitHubLink(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.update_view_github))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    }
}

private fun short(hash: String?): String =
    when {
        hash.isNullOrEmpty() -> "?"
        hash.length <= HASH_HEAD + HASH_TAIL -> hash
        else -> "${hash.take(HASH_HEAD)}…${hash.takeLast(HASH_TAIL)}"
    }

@Composable
private fun VerifyLog(
    expected: String?,
    actual: String?,
    hashing: Boolean,
    installed: String?,
    file: String?,
    signerChecking: Boolean,
    failed: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(12.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (expected != null || actual != null) {
                Text(stringResource(R.string.vlog_checksum), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                LogRow(stringResource(R.string.vlog_release), short(expected), null)
                val hashState =
                    when {
                        hashing -> LogState.Busy(stringResource(R.string.vlog_hashing))
                        expected != null && expected.equals(actual, ignoreCase = true) -> LogState.Match
                        else -> LogState.Mismatch
                    }
                LogRow(
                    stringResource(R.string.vlog_file),
                    if (hashing) actual.orEmpty().take(HASH_HEAD + 4) + "…" else short(actual),
                    hashState,
                )
            }
            if (installed != null || file != null || hashing || !failed) {
                Text(
                    stringResource(R.string.vlog_signing_key),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                when {
                    hashing -> {
                        LogRow(stringResource(R.string.vlog_file), stringResource(R.string.vlog_waiting), null, dim = true)
                    }

                    else -> {
                        LogRow(stringResource(R.string.vlog_installed), short(installed), null)
                        val signerState =
                            when {
                                signerChecking -> LogState.Busy(stringResource(R.string.vlog_checking))
                                installed != null && installed == file -> LogState.Match
                                else -> LogState.Mismatch
                            }
                        LogRow(stringResource(R.string.vlog_file), short(file), signerState)
                    }
                }
            }
        }
    }
}

private sealed interface LogState {
    data object Match : LogState

    data object Mismatch : LogState

    data class Busy(
        val label: String,
    ) : LogState
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LogRow(
    key: String,
    value: String,
    state: LogState?,
    dim: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = AppText.meta, color = scheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(value, style = AppText.meta, color = if (dim) scheme.onSurfaceVariant else scheme.onSurface, modifier = Modifier.weight(1f))
        when (state) {
            LogState.Match -> {
                StateLabel(Icons.Outlined.CheckCircle, stringResource(R.string.vlog_match), scheme.primary)
            }

            LogState.Mismatch -> {
                StateLabel(Icons.Outlined.ErrorOutline, stringResource(R.string.vlog_mismatch), scheme.error)
            }

            is LogState.Busy -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LoadingIndicator(Modifier.size(18.dp))
                    Text(state.label, style = AppText.meta, color = scheme.onSurfaceVariant)
                }
            }

            null -> {}
        }
    }
}

@Composable
private fun StateLabel(
    icon: ImageVector,
    label: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, style = AppText.meta, color = color)
    }
}
