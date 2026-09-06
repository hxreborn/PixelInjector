package eu.hxreborn.pixelinjector.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.component.Tile
import eu.hxreborn.pixelinjector.ui.component.TileText
import eu.hxreborn.pixelinjector.ui.component.tileGroup
import eu.hxreborn.pixelinjector.ui.theme.AppText
import eu.hxreborn.pixelinjector.ui.util.AppIconSize
import eu.hxreborn.pixelinjector.ui.util.LauncherApp
import eu.hxreborn.pixelinjector.ui.util.drawVerticalScrollbar
import eu.hxreborn.pixelinjector.ui.util.rememberAppIcon

private const val SHEET_HEIGHT_FRACTION = 0.8f
private val EmptyStatePadding = PaddingValues(horizontal = 32.dp, vertical = 40.dp)
private val EmptyIconSize = 48.dp
private val AppRowHorizontalPadding = 12.dp
private val AppRowVerticalPadding = 8.dp
private val checkboxRole = Modifier.semantics { role = Role.Checkbox }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppsSheet(
    apps: List<LauncherApp>?,
    allowed: Set<String>,
    onAllowedChange: (Set<String>) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(allowed)
    val onToggle = { packageName: String, on: Boolean ->
        haptic.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        onAllowedChange(if (on) current + packageName else current - packageName)
    }
    var query by rememberSaveable { mutableStateOf("") }
    val pinned = remember(apps) { allowed }
    val visible =
        remember(apps, query, pinned) {
            apps
                ?.filter { app ->
                    query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true)
                }?.sortedByDescending { it.packageName in pinned }
        }
    val listState = rememberLazyListState()
    val iconSizePx = with(LocalDensity.current) { AppIconSize.roundToPx() }

    Column(Modifier.fillMaxHeight(SHEET_HEIGHT_FRACTION).imePadding().padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
        SheetHeader(stringResource(R.string.tweak_clipboard_apps), stringResource(R.string.tweak_clipboard_desc)) {
            Text(
                pluralStringResource(R.plurals.apps_selected, allowed.size, allowed.size),
                style = AppText.meta,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text(stringResource(R.string.filter_apps)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon =
                        if (query.isEmpty()) {
                            null
                        } else {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.clear_filter))
                                }
                            }
                        },
                )
            },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier.fillMaxWidth(),
        ) {}
        Spacer(Modifier.height(8.dp))
        if (visible == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Column
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).drawVerticalScrollbar(listState),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            if (visible.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier.fillParentMaxWidth().padding(EmptyStatePadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Outlined.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(EmptyIconSize),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.apps_filter_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { query = "" }) { Text(stringResource(R.string.clear_filter)) }
                    }
                }
            }
            tileGroup(visible, key = { it.packageName }) { app, shape ->
                val checked = app.packageName in allowed
                val icon = rememberAppIcon(app.packageName, iconSizePx)
                Tile(
                    shape = shape,
                    startPadding = AppRowHorizontalPadding,
                    endPadding = AppRowHorizontalPadding,
                    verticalPadding = AppRowVerticalPadding,
                    checked = checked,
                    onCheckedChange = { onToggle(app.packageName, it) },
                    modifier = checkboxRole,
                ) {
                    Box(Modifier.size(AppIconSize), contentAlignment = Alignment.Center) {
                        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(AppIconSize)) }
                    }
                    TileText(app.label, app.packageName, singleLine = true)
                    Checkbox(checked = checked, onCheckedChange = null)
                }
            }
        }
    }
}
