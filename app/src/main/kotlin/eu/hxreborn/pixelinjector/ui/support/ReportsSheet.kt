package eu.hxreborn.pixelinjector.ui.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.support.ReportStatus
import eu.hxreborn.pixelinjector.support.SentReport
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.component.tileGroup
import eu.hxreborn.pixelinjector.ui.theme.AppText
import kotlinx.coroutines.launch

private val LeadSize = 40.dp

@Composable
fun ReportsSheet(
    reports: List<SentReport>,
    onOpened: () -> Unit,
    onReply: (SentReport) -> Unit,
    onTryNow: () -> Unit,
    onDelete: (SentReport) -> Unit,
    onUndo: (SentReport) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deleted = stringResource(R.string.report_deleted)
    val undo = stringResource(R.string.undo)
    LaunchedEffect(Unit) { onOpened() }
    Box {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            SheetHeader(stringResource(R.string.your_reports))
            if (reports.isEmpty()) {
                Text(stringResource(R.string.reports_none), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                tileGroup(reports, key = { it.id }) { report, shape ->
                    ReportTile(
                        report = report,
                        shape = shape,
                        onReply = { onReply(report) },
                        onTryNow = onTryNow,
                        onDelete = {
                            onDelete(report)
                            snackbar.currentSnackbarData?.dismiss()
                            scope.launch {
                                if (snackbar.showSnackbar(deleted, undo, withDismissAction = false, duration = SnackbarDuration.Short) ==
                                    SnackbarResult.ActionPerformed
                                ) {
                                    onUndo(report)
                                }
                            }
                        },
                    )
                }
            }
            Text(
                stringResource(R.string.report_retention),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 12.dp),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Snackbar(it) }
    }
}

@Composable
private fun ReportTile(
    report: SentReport,
    shape: RoundedCornerShape,
    onReply: () -> Unit,
    onTryNow: () -> Unit,
    onDelete: () -> Unit,
) {
    val style = statusStyle(report)
    Tile(shape = shape, verticalPadding = 12.dp) {
        Surface(
            shape = CircleShape,
            color = style.container,
            contentColor = style.content,
            modifier = Modifier.size(LeadSize).align(Alignment.Top),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(style.icon, contentDescription = null) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(reportTitle(report), style = AppText.tileTitle)
            Text(
                "${style.label}  ${shortTime(report.sentAt)}",
                style = AppText.tileSupporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            report.note?.let { note ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(note, style = MaterialTheme.typography.bodyMedium)
                }
            }
            report.error?.takeIf { report.status == ReportStatus.REJECTED }?.let {
                Text(it, style = AppText.tileSupporting, color = MaterialTheme.colorScheme.error)
            }
            Text(
                listOfNotNull(report.short, report.build).joinToString("  "),
                style = AppText.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (report.status) {
                ReportStatus.NEED_INFO -> {
                    FilledTonalButton(onClick = onReply, shapes = ButtonDefaults.shapes(), modifier = Modifier.padding(top = 4.dp)) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Text(stringResource(R.string.report_reply), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                }

                ReportStatus.WAITING -> {
                    FilledTonalButton(onClick = onTryNow, shapes = ButtonDefaults.shapes(), modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(R.string.report_try_now), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                }

                else -> {}
            }
        }
        IconButton(onClick = onDelete, shapes = IconButtonDefaults.shapes(), modifier = Modifier.align(Alignment.Top)) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.report_delete, report.short ?: ""))
        }
    }
}
