package eu.hxreborn.pixelinjector.ui.support

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
private val BubbleRadius = 16.dp
private const val BUBBLE_WIDTH = 0.9f
private val ThreadToggleInset = (-12).dp
private const val SHORT_ID = 8

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
    var confirming by remember { mutableStateOf<SentReport?>(null) }
    LaunchedEffect(Unit) { onOpened() }
    confirming?.let { report ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.report_delete_title, report.short ?: report.id.take(SHORT_ID))) },
            text = { Text(stringResource(R.string.report_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = null
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
                ) { Text(stringResource(R.string.report_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    Box {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            SheetHeader(stringResource(R.string.your_reports))
            if (reports.isEmpty()) {
                Text(stringResource(R.string.reports_none), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                val ids = reports.map { it.id }.toSet()
                val replies = reports.filter { it.replyTo in ids }.groupBy { it.replyTo }
                tileGroup(reports.filterNot { it.replyTo in ids }, key = { it.id }) { report, shape ->
                    ReportTile(
                        report = report,
                        replies = replies[report.id].orEmpty(),
                        shape = shape,
                        onReply = { onReply(report) },
                        onTryNow = onTryNow,
                        onDelete = { confirming = report },
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
    replies: List<SentReport>,
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
            var open by rememberSaveable(report.id, report.status) { mutableStateOf(report.status == ReportStatus.NEED_INFO) }
            TextButton(onClick = { open = !open }, modifier = Modifier.offset(x = ThreadToggleInset)) {
                Text(stringResource(if (open) R.string.report_thread_hide else R.string.report_thread_show))
            }
            AnimatedVisibility(
                open,
                enter =
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit =
                    shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            ) {
                Thread(report, replies)
            }
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

private data class ThreadEntry(
    val at: Long,
    val fromYou: Boolean,
    val label: String,
    val text: String?,
)

@Composable
private fun Thread(
    report: SentReport,
    replies: List<SentReport>,
) {
    val you = stringResource(R.string.report_from_you)
    val entries =
        buildList {
            add(ThreadEntry(report.sentAt, fromYou = true, you, report.message))
            report.events.forEach { add(ThreadEntry(it.at, fromYou = false, statusStyle(it.status, it.fixedIn).label, it.note)) }
            replies.forEach { add(ThreadEntry(it.sentAt, fromYou = true, you, it.message)) }
        }.sortedBy { it.at }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEach { entry ->
            val scheme = MaterialTheme.colorScheme
            Surface(
                shape = RoundedCornerShape(BubbleRadius),
                color = if (entry.fromYou) scheme.secondaryContainer else scheme.surfaceContainerHighest,
                contentColor = if (entry.fromYou) scheme.onSecondaryContainer else scheme.onSurface,
                modifier = Modifier.fillMaxWidth(BUBBLE_WIDTH).align(if (entry.fromYou) Alignment.End else Alignment.Start),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${entry.label}  ${shortTime(entry.at)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (entry.fromYou) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                    )
                    entry.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        ?: if (entry.fromYou) {
                            Text(
                                stringResource(R.string.report_no_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        } else {
                            Unit
                        }
                }
            }
        }
    }
}
