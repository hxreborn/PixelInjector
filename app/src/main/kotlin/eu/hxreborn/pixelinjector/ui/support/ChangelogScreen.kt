package eu.hxreborn.pixelinjector.ui.support

import androidx.annotation.StringRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material.icons.outlined.Webhook
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.support.Release
import eu.hxreborn.pixelinjector.support.SentReport
import eu.hxreborn.pixelinjector.ui.component.DetailScaffold
import eu.hxreborn.pixelinjector.ui.component.SheetActions
import eu.hxreborn.pixelinjector.ui.component.SheetHeader
import eu.hxreborn.pixelinjector.ui.component.TileGap
import eu.hxreborn.pixelinjector.ui.component.readableGutter
import eu.hxreborn.pixelinjector.ui.component.tileShape
import eu.hxreborn.pixelinjector.ui.theme.AppText
import eu.hxreborn.pixelinjector.ui.util.LocalDateStyle
import eu.hxreborn.pixelinjector.ui.util.verticalScrollbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CHANGELOG_ASSET = "CHANGELOG.md"
private val ChipRadius = 8.dp
private val ScrollbarGutter = 12.dp
private val ReleaseCardPadding = 12.dp
private val SectionIconSize = 20.dp
private val SectionIconSpacing = 6.dp
private val BadgeHeight = 22.dp
private val BadgeIconSize = 14.dp
private val NoteMinHeight = 48.dp

private enum class NoteArea(
    val scopes: Set<String?>,
    @param:StringRes val title: Int,
    val icon: ImageVector,
) {
    TWEAKS(setOf("tweak"), R.string.changelog_area_tweaks, Icons.Outlined.Tune),
    HOOKS(setOf("hook", "module"), R.string.changelog_area_hooks, Icons.Outlined.Webhook),
    REPORTS(setOf("report"), R.string.changelog_area_reports, Icons.Outlined.BugReport),
    UPDATES(setOf("update"), R.string.changelog_area_updates, Icons.Outlined.SystemUpdate),
    LOG(setOf("log"), R.string.changelog_area_log, Icons.AutoMirrored.Outlined.Article),
    RELOAD(setOf("reload"), R.string.changelog_area_reload, Icons.Outlined.Refresh),
    INTERFACE(setOf("ui"), R.string.changelog_area_ui, Icons.Outlined.Palette),
    APP(setOf("app"), R.string.changelog_area_app, Icons.Outlined.Smartphone),
    DEPENDENCIES(setOf("deps"), R.string.changelog_area_dependencies, Icons.Outlined.Extension),
    UTILITIES(setOf("util"), R.string.changelog_area_utilities, Icons.Outlined.Handyman),
    OTHER(emptySet(), R.string.changelog_area_other, Icons.Outlined.Code),
    ;

    companion object {
        fun of(scope: String?): NoteArea = entries.firstOrNull { scope in it.scopes } ?: OTHER
    }
}

@Composable
fun rememberChangelog(): List<Release>? {
    val context = LocalContext.current
    return produceState<List<Release>?>(null) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    Release.changelog(
                        context.assets
                            .open(CHANGELOG_ASSET)
                            .bufferedReader()
                            .use { it.readText() },
                    )
                }.getOrDefault(emptyList())
            }
    }.value
}

@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val loaded = rememberChangelog()
    val releases = loaded.orEmpty().distinctBy { it.versionName }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val atEnd by remember { derivedStateOf { !listState.canScrollForward } }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    DetailScaffold(
        title = stringResource(R.string.changelog),
        subtitle = loaded?.let { { pluralStringResource(R.plurals.changelog_count, releases.size, releases.size) } },
        onBack = onBack,
        contentPadding = PaddingValues(0.dp),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().verticalScrollbar(listState),
                contentPadding =
                    PaddingValues(
                        start = readableGutter(),
                        end = readableGutter() + ScrollbarGutter,
                        top = 8.dp,
                        bottom = navBottom + 88.dp,
                    ),
            ) {
                itemsIndexed(releases, key = { _, release -> release.versionName }) { index, release ->
                    if (index > 0) Spacer(Modifier.height(TileGap))
                    ReleaseSection(release, tileShape(releases.size, index))
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = listState.canScrollBackward || listState.canScrollForward,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = navBottom + 16.dp),
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            if (atEnd) listState.scrollToItem(0) else listState.scrollToItem(releases.lastIndex, Int.MAX_VALUE)
                        }
                    },
                ) {
                    Icon(
                        if (atEnd) Icons.Outlined.VerticalAlignTop else Icons.Outlined.VerticalAlignBottom,
                        contentDescription = stringResource(if (atEnd) R.string.log_jump_top else R.string.log_jump_bottom),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReleaseSection(
    release: Release,
    shape: Shape,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .padding(ReleaseCardPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 4.dp).semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(release.versionName, style = MaterialTheme.typography.titleLargeEmphasized)
            Text(
                LocalDateStyle.current.formatIso(release.publishedAt).orEmpty(),
                style = AppText.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (release.versionName == BuildConfig.VERSION_NAME) InstalledChip()
        }
        ReleaseNotes(release)
    }
}

@Composable
private fun InstalledChip() {
    Surface(shape = RoundedCornerShape(ChipRadius), color = MaterialTheme.colorScheme.primary) {
        Text(
            stringResource(R.string.changelog_installed),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private enum class NoteSection(
    @param:StringRes val title: Int,
) {
    FEATURES(R.string.release_notes_features),
    FIXES(R.string.release_notes_fixes),
    PERFORMANCE(R.string.release_notes_performance),
    SECURITY(R.string.release_notes_security),
    OTHER(R.string.release_notes_other),
    ;

    companion object {
        fun of(note: Release.Note): NoteSection =
            when (note.kind) {
                Release.Note.Kind.NEW -> {
                    FEATURES
                }

                Release.Note.Kind.FIXED -> {
                    FIXES
                }

                Release.Note.Kind.OTHER -> {
                    when (note.section) {
                        "Performance Improvements" -> PERFORMANCE
                        "Security" -> SECURITY
                        else -> OTHER
                    }
                }
            }
    }
}

@Composable
internal fun ReleaseNotes(
    release: Release,
    fixes: Map<String, List<SentReport>> = emptyMap(),
) {
    val bySection = release.notes.groupBy(NoteSection::of)
    Column {
        NoteSection.entries.forEach { section ->
            val notes = bySection[section] ?: return@forEach
            ReleaseNoteHeading(section)
            notes.sortedBy { NoteArea.of(it.scope).ordinal }.forEach { note ->
                NoteRow(note)
                fixes[note.text]?.let { reports ->
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                        FixMark(reports)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseNoteHeading(section: NoteSection) {
    val scheme = MaterialTheme.colorScheme
    val (icon, tint) =
        when (section) {
            NoteSection.FEATURES -> painterResource(R.drawable.ic_changelog_auto_awesome) to scheme.primary
            NoteSection.FIXES -> painterResource(R.drawable.ic_changelog_build) to scheme.tertiary
            NoteSection.PERFORMANCE -> rememberVectorPainter(Icons.Outlined.Speed) to scheme.primary
            NoteSection.SECURITY -> rememberVectorPainter(Icons.Outlined.Security) to scheme.tertiary
            NoteSection.OTHER -> painterResource(R.drawable.ic_changelog_list_alt) to scheme.onSurfaceVariant
        }
    Row(
        Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 2.dp).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SectionIconSpacing),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(SectionIconSize))
        Text(
            stringResource(section.title),
            style = MaterialTheme.typography.labelMediumEmphasized,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoteRow(note: Release.Note) {
    val uriHandler = LocalUriHandler.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = NoteMinHeight)
            .clip(RoundedCornerShape(ChipRadius))
            .then(
                note.commitUrl?.let {
                    Modifier.clickable(onClickLabel = stringResource(R.string.changelog_open_commit), role = Role.Button) {
                        uriHandler.openUri(it)
                    }
                } ?: Modifier,
            ).padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AreaChip(note)
        Text(
            note.text.replaceFirstChar(Char::uppercase),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AreaChip(note: Release.Note) {
    val area = NoteArea.of(note.scope)
    Surface(
        shape = RoundedCornerShape(ChipRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.heightIn(min = BadgeHeight).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(area.icon, contentDescription = null, modifier = Modifier.size(BadgeIconSize))
            Text(
                stringResource(area.title),
                style = MaterialTheme.typography.labelSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun WhatsNewSheet(
    onFullChangelog: () -> Unit,
    onClose: () -> Unit,
) {
    val releases = rememberChangelog()
    val installed = releases?.firstOrNull { it.versionName == BuildConfig.VERSION_NAME }
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetHeader(
            stringResource(R.string.whats_new_title, BuildConfig.VERSION_NAME),
            installed?.let { LocalDateStyle.current.formatIso(it.publishedAt) }?.let { stringResource(R.string.whats_new_released, it) },
        )
        when {
            installed != null -> {
                ReleaseNotes(installed)
            }

            releases != null -> {
                Text(
                    stringResource(R.string.whats_new_missing),
                    style = AppText.tileSupporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SheetActions {
            TextButton(onClick = onClose, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.close), style = AppText.button)
            }
            FilledTonalButton(onClick = onFullChangelog, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.changelog_see_full), style = AppText.button)
            }
        }
    }
}
