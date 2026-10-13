package eu.hxreborn.pixelinjector.ui.support

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.ReportGmailerrorred
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.support.InstallStep
import eu.hxreborn.pixelinjector.support.Release
import eu.hxreborn.pixelinjector.support.ReleaseCheck
import eu.hxreborn.pixelinjector.support.ReportStatus
import eu.hxreborn.pixelinjector.support.SentReport
import eu.hxreborn.pixelinjector.ui.dashboard.tweakUis
import java.text.DateFormat
import java.util.Date

@Immutable
data class SupportState(
    val release: ReleaseCheck,
    val reports: List<SentReport>,
    val shareHookStatus: Boolean,
    val postponedVersion: Int,
    val install: InstallStep,
    val framework: String?,
) {
    val available: Release? get() = release.known?.takeIf { it.newer }
    val conversations: List<SentReport> get() =
        reports.filter { report ->
            report.replyTo == null ||
                reports.none { it.id == report.replyTo }
        }

    val needsReply: Boolean get() = reports.any { it.status == ReportStatus.NEED_INFO && !it.noteSeen }
    val showUpdateCard: Boolean get() = available?.let { it.versionCode > postponedVersion } == true

    val fixedByUpdate: List<SentReport>
        get() =
            available
                ?.versionName
                ?.let { version ->
                    reports.filter { it.status == ReportStatus.FIXED && it.fixedIn == version }
                }.orEmpty()
}

class StatusStyle(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color,
)

@Composable
fun statusStyle(report: SentReport): StatusStyle = statusStyle(report.status, report.fixedIn)

@Composable
fun statusStyle(
    status: ReportStatus,
    fixedIn: String?,
): StatusStyle {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        ReportStatus.WAITING -> {
            StatusStyle(
                stringResource(R.string.status_waiting),
                Icons.Outlined.Schedule,
                scheme.surfaceContainerHighest,
                scheme.onSurfaceVariant,
            )
        }

        ReportStatus.RECEIVED -> {
            StatusStyle(
                stringResource(R.string.status_received),
                Icons.Outlined.Inbox,
                scheme.surfaceContainerHighest,
                scheme.onSurfaceVariant,
            )
        }

        ReportStatus.INVESTIGATING -> {
            StatusStyle(
                stringResource(R.string.status_investigating),
                Icons.Outlined.ManageSearch,
                scheme.secondaryContainer,
                scheme.onSecondaryContainer,
            )
        }

        ReportStatus.NEED_INFO -> {
            StatusStyle(
                stringResource(R.string.status_need_info),
                Icons.Outlined.HelpOutline,
                scheme.tertiaryContainer,
                scheme.onTertiaryContainer,
            )
        }

        ReportStatus.FIXED -> {
            StatusStyle(
                fixedIn?.let { stringResource(R.string.status_fixed, it) } ?: stringResource(R.string.status_fixed_plain),
                Icons.Outlined.CheckCircle,
                scheme.primaryContainer,
                scheme.onPrimaryContainer,
            )
        }

        ReportStatus.REJECTED -> {
            StatusStyle(
                stringResource(R.string.status_rejected),
                Icons.Outlined.ReportGmailerrorred,
                scheme.errorContainer,
                scheme.onErrorContainer,
            )
        }
    }
}

@Composable
fun reportTitle(report: SentReport): String =
    report.tweak?.let { key -> tweakUis.firstOrNull { it.key == key }?.let { stringResource(it.title) } ?: key }
        ?: stringResource(R.string.report_general)

fun shortTime(epochMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))

fun bytesLabel(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return if (mb >= 1) "%.1f MB".format(mb) else "%d KB".format(bytes / 1024)
}
