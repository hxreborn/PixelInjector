package eu.hxreborn.pixelinjector.ui.reload

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import eu.hxreborn.pixelinjector.ui.component.tileGroup
import eu.hxreborn.pixelinjector.ui.component.versionLabel
import eu.hxreborn.pixelinjector.ui.theme.AppText
import eu.hxreborn.pixelinjector.ui.util.AppIconSize
import eu.hxreborn.pixelinjector.ui.util.rememberAppIcon
import io.github.libxposed.service.HookedTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TargetRowPadding = 12.dp
private val CheckboxSlot = 40.dp
private val SheetBottomPadding = 20.dp
private const val ACTIONS_FADE_END = 0.2f
private const val FILTER_FROM = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetsSheet(
    targets: List<HookedTarget>,
    onDismiss: () -> Unit,
    onHotReload: (List<HookedTarget>) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var query by rememberSaveable { mutableStateOf("") }
    var reloadPids by remember(targets.map { it.pid }) {
        mutableStateOf(
            targets
                .filter { it.state == HookedTarget.State.STALE }
                .map { it.pid }
                .toSet(),
        )
    }
    val visible = remember(targets, query) { targets.filter { it.processName.contains(query, ignoreCase = true) } }
    LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) }

    fun toggle(
        pids: Set<Int>,
        pid: Int,
        on: Boolean,
    ): Set<Int> {
        haptic.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        return if (on) pids + pid else pids - pid
    }

    var actionsHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Column(Modifier.padding(horizontal = 16.dp)) {
        SheetHeader(stringResource(R.string.targets_title), stringResource(R.string.targets_subtitle))
        if (targets.size >= FILTER_FROM) {
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
        Box(Modifier.weight(1f, fill = false)) {
            LazyColumn(contentPadding = PaddingValues(bottom = with(density) { actionsHeight.toDp() })) {
                if (visible.isNotEmpty()) {
                    tileGroup(visible, key = { it.pid }) { target, shape ->
                        TargetTile(
                            target = target,
                            shape = shape,
                            checked = target.pid in reloadPids,
                            onCheckedChange = { reloadPids = toggle(reloadPids, target.pid, it) },
                        )
                    }
                }
                if (visible.isEmpty()) {
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
            val sheetColor = MaterialTheme.colorScheme.surfaceContainerLow
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { actionsHeight = it.height }
                    .background(Brush.verticalGradient(0f to Color.Transparent, ACTIONS_FADE_END to sheetColor))
                    .padding(bottom = SheetBottomPadding),
            ) {
                SheetActions {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) }
                    Button(
                        shapes = ButtonDefaults.shapes(),
                        enabled = reload.isNotEmpty(),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onHotReload(reload)
                        },
                    ) {
                        Text(stringResource(R.string.targets_apply_reload, reload.size), style = AppText.button)
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetTile(
    target: HookedTarget,
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
        ProcessIcon(target.processName)
        val label = rememberProcessLabel(target.processName)
        val identity = "pid=${target.pid} uid=${target.uid}"
        TileText(
            title = label ?: processNameForDisplay(target.processName),
            supporting = if (label == null) identity else "${processNameForDisplay(target.processName)}\n$identity",
            supportingStyle = AppText.meta,
        )
        val stale = target.loadedVersionCode < BuildConfig.VERSION_CODE || target.state == HookedTarget.State.STALE
        if (stale) {
            StateChip(
                versionLabel(target.loadedVersionCode),
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun rememberProcessLabel(processName: String): String? {
    val context = LocalContext.current
    val label by produceState<String?>(null, processName) {
        value =
            withContext(Dispatchers.IO) {
                val pm = context.packageManager
                runCatching { pm.getApplicationInfo(processName.substringBefore(':'), 0).loadLabel(pm).toString() }.getOrNull()
            }
    }
    return label
}

@Composable
private fun ProcessIcon(processName: String) {
    val packageName = processName.substringBefore(':')
    val icon = rememberAppIcon(packageName, with(LocalDensity.current) { AppIconSize.roundToPx() })
    Box(Modifier.size(AppIconSize), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(AppIconSize))
        } else {
            Icon(Icons.Outlined.Android, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
