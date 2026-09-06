package eu.hxreborn.pixelinjector.ui.reload

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.ui.component.SectionGap
import eu.hxreborn.pixelinjector.ui.component.SheetActions
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.component.StateChip
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.component.TileText
import eu.hxreborn.pixelinjector.ui.component.processNameForDisplay
import eu.hxreborn.pixelinjector.ui.component.sectionHeader
import eu.hxreborn.pixelinjector.ui.component.tileGroup
import eu.hxreborn.pixelinjector.ui.component.versionLabel
import eu.hxreborn.pixelinjector.ui.theme.AppText
import eu.hxreborn.pixelinjector.ui.viewmodel.AppTarget
import io.github.libxposed.service.HookedTarget

private val TargetRowPadding = 12.dp
private val CheckboxSlot = 40.dp
private const val FILTER_FROM = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetsSheet(
    targets: List<HookedTarget>,
    apps: List<AppTarget>,
    onDismiss: () -> Unit,
    onHotReload: (List<HookedTarget>) -> Unit,
    onRestart: (Set<String>) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var query by rememberSaveable { mutableStateOf("") }
    var reloadPids by remember(targets.map { it.pid }) {
        mutableStateOf(
            targets
                .filter { it.state == HookedTarget.State.STALE }
                .ifEmpty { targets }
                .map { it.pid }
                .toSet(),
        )
    }
    var restartPids by remember(apps.map { it.target.pid }) { mutableStateOf(emptySet<Int>()) }
    val visible = remember(targets, query) { targets.filter { it.processName.contains(query, ignoreCase = true) } }
    val visibleApps = remember(apps, query) { apps.filter { it.target.processName.contains(query, ignoreCase = true) } }
    val reloadTitle = stringResource(R.string.targets_group_reload)
    val reloadDetail = stringResource(R.string.targets_group_reload_detail)
    val restartTitle = stringResource(R.string.targets_group_restart)
    val restartDetail = stringResource(R.string.targets_group_restart_detail)
    LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) }

    fun toggle(
        pids: Set<Int>,
        pid: Int,
        on: Boolean,
    ): Set<Int> {
        haptic.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        return if (on) pids + pid else pids - pid
    }

    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
        SheetHeader(stringResource(R.string.targets_title), stringResource(R.string.targets_subtitle))
        if (targets.size + apps.size >= FILTER_FROM) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text(stringResource(R.string.filter_processes)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    )
                },
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier.fillMaxWidth(),
            ) {}
            Spacer(Modifier.height(SectionGap))
        }
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            if (visible.isNotEmpty()) {
                sectionHeader(reloadTitle, listOf(reloadDetail))
                tileGroup(visible, key = { it.pid }) { target, shape ->
                    TargetTile(
                        name = target.processName,
                        pid = target.pid,
                        uid = target.uid,
                        loadedVersionCode = target.loadedVersionCode,
                        shape = shape,
                        checked = target.pid in reloadPids,
                        onCheckedChange = { reloadPids = toggle(reloadPids, target.pid, it) },
                    )
                }
            }
            if (visibleApps.isNotEmpty()) {
                item(key = "apps-gap") { Spacer(Modifier.height(SectionGap)) }
                sectionHeader(restartTitle, listOf(restartDetail))
                tileGroup(visibleApps, key = { it.target.pid }) { app, shape ->
                    TargetTile(
                        name = app.target.processName,
                        pid = app.target.pid,
                        uid = app.target.uid,
                        loadedVersionCode = app.target.loadedVersionCode,
                        shape = shape,
                        checked = app.target.pid in restartPids,
                        onCheckedChange = { restartPids = toggle(restartPids, app.target.pid, it) },
                    )
                }
            }
            if (visible.isEmpty() && visibleApps.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.targets_empty),
                        style = AppText.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        val reload = targets.filter { it.pid in reloadPids }
        val restart = apps.filter { it.target.pid in restartPids }
        SheetActions {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            Button(
                enabled = reload.isNotEmpty() || restart.isNotEmpty(),
                colors =
                    if (restart.isEmpty()) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (restart.isNotEmpty()) onRestart(restart.mapTo(mutableSetOf()) { it.packageName })
                    if (reload.isNotEmpty()) onHotReload(reload) else onDismiss()
                },
            ) {
                val count = reload.size + restart.size
                Text(
                    when {
                        restart.isEmpty() -> stringResource(R.string.targets_apply_reload, count)
                        reload.isEmpty() -> stringResource(R.string.targets_apply_restart, count)
                        else -> stringResource(R.string.targets_apply_both, count)
                    },
                    style = AppText.button,
                )
            }
        }
    }
}

@Composable
private fun TargetTile(
    name: String,
    pid: Int,
    uid: Int,
    loadedVersionCode: Long,
    shape: RoundedCornerShape,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Tile(
        shape = shape,
        startPadding = TargetRowPadding,
        endPadding = TargetRowPadding,
        verticalPadding = TargetRowPadding,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.semantics { role = Role.Checkbox },
    ) {
        Box(Modifier.size(CheckboxSlot), contentAlignment = Alignment.Center) {
            Checkbox(checked = checked, onCheckedChange = null)
        }
        TileText(processNameForDisplay(name), "pid=$pid uid=$uid", supportingStyle = AppText.meta)
        if (loadedVersionCode < BuildConfig.VERSION_CODE) {
            StateChip(
                versionLabel(loadedVersionCode),
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
