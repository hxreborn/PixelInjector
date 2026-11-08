package eu.hxreborn.pixelinjector.ui.betterfox

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import eu.hxreborn.pixelinjector.App
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.ModuleConstants
import eu.hxreborn.pixelinjector.ModuleConstants.FIREFOX_PACKAGES
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.betterfox.BETTERFOX_ASSET
import eu.hxreborn.pixelinjector.betterfox.BetterfoxBackup
import eu.hxreborn.pixelinjector.betterfox.BetterfoxList
import eu.hxreborn.pixelinjector.betterfox.BetterfoxOverride
import eu.hxreborn.pixelinjector.betterfox.chooseList
import eu.hxreborn.pixelinjector.betterfox.decodeOverrides
import eu.hxreborn.pixelinjector.betterfox.encodeOverrides
import eu.hxreborn.pixelinjector.betterfox.formatPrefValue
import eu.hxreborn.pixelinjector.betterfox.geckoMajor
import eu.hxreborn.pixelinjector.betterfox.parsePrefValue
import eu.hxreborn.pixelinjector.prefs.PrefSpec
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.support.BetterfoxStatusException
import eu.hxreborn.pixelinjector.support.BetterfoxUpdateState
import eu.hxreborn.pixelinjector.support.Browser
import eu.hxreborn.pixelinjector.support.fetchBetterfox
import eu.hxreborn.pixelinjector.support.installedBrowsers
import eu.hxreborn.pixelinjector.ui.dashboard.restartOnChange
import eu.hxreborn.pixelinjector.ui.reload.LogLine
import eu.hxreborn.pixelinjector.ui.reload.ReloadStatus
import eu.hxreborn.pixelinjector.ui.reload.Tone
import eu.hxreborn.pixelinjector.ui.util.BackupDocument
import eu.hxreborn.pixelinjector.ui.util.BackupFolder
import eu.hxreborn.pixelinjector.ui.util.Root
import eu.hxreborn.pixelinjector.ui.util.documentName
import eu.hxreborn.pixelinjector.ui.util.readDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.IOException
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.days

private const val STATE_PREFS = "betterfox"
private const val FOLDER = "folder"
private const val CHECKED_AT = "checkedAt"
private const val KIB = 1024
private val CheckInterval = 1.days

class BetterfoxController(
    private val application: Application,
    private val scope: CoroutineScope,
    private val restart: (Set<String>) -> Unit,
) {
    private val app = App.from(application)
    private val state = application.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
    private val runLock = Mutex()
    private var runJob: Job? = null

    private val bundled = MutableStateFlow<BetterfoxList?>(null)
    private val update = MutableStateFlow<BetterfoxUpdateState>(BetterfoxUpdateState.Idle)
    private val browsers = MutableStateFlow<List<Browser>>(emptyList())
    private val selected = MutableStateFlow<String?>(null)
    private val folder = MutableStateFlow(state.getString(FOLDER, null)?.toUri())
    private val backups = MutableStateFlow<List<BackupEntry>>(emptyList())
    private val flash = MutableStateFlow(Flash())
    private var changeSequence = -1
    private var lastCleared: Map<String, BetterfoxOverride> = emptyMap()
    private var beforeRestore: Pair<Set<String>, String>? = null
    private var pendingMessage: BetterfoxMessage? = null
    private var pendingFlash: List<String>? = null

    private val _run = MutableStateFlow<BetterfoxRun?>(null)
    val run: StateFlow<BetterfoxRun?> = _run.asStateFlow()

    private val _messages = MutableSharedFlow<BetterfoxMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<BetterfoxMessage> = _messages.asSharedFlow()

    val list: StateFlow<BetterfoxList?> =
        combine(bundled, app.prefs.state) { bundled, prefs ->
            bundled?.let { chooseList(it, BetterfoxList.parseOrNull(prefs[Prefs.FIREFOX_BETTERFOX_LIST])) }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    val screen: StateFlow<BetterfoxState> =
        combine(
            combine(list, app.prefs.state, update) { list, prefs, update -> Triple(list, prefs, update) },
            combine(browsers, selected) { browsers, selected -> browsers to selected },
            combine(folder, backups, flash) { folder, backups, flash -> Triple(folder, backups, flash) },
        ) { (list, prefs, update), (browsers, selected), (folder, backups, flash) ->
            val primary = browsers.firstOrNull { it.packageName == selected }
            val pkg = primary?.packageName ?: selected
            val listMajor = list?.gecko?.let(::geckoMajor)
            BetterfoxState(
                list = list,
                enabled = pkg != null && prefs[Prefs.FIREFOX_BETTERFOX[pkg]],
                offGroups = pkg?.let { prefs[Prefs.FIREFOX_BETTERFOX_OFF_GROUPS[it]] }.orEmpty(),
                overrides = pkg?.let { decodeOverrides(prefs[Prefs.FIREFOX_BETTERFOX_OVERRIDES[it]]) }.orEmpty(),
                browser = primary,
                browsers = browsers,
                newerGecko =
                    primary?.takeIf { browser ->
                        val major = browser.gecko?.let(::geckoMajor)
                        major != null && listMajor != null && major > listMajor
                    },
                update = update,
                folder = BackupFolder(application.contentResolver, folder).label,
                backups = backups,
                flash = flash,
            )
        }.stateIn(scope, SharingStarted.Eagerly, BetterfoxState())

    init {
        scope.launch(Dispatchers.IO) {
            bundled.value = application.assets.open(BETTERFOX_ASSET).use { BetterfoxList.parse(it.readBytes().decodeToString()) }
        }
        refreshBrowsers()
    }

    private fun <T : Any> commit(
        pref: PrefSpec<T>,
        value: T,
    ) {
        app.prefs.save(pref, value)
        restartOnChange[pref]?.let(restart)
    }

    fun refreshBrowsers() {
        scope.launch(Dispatchers.IO) {
            val changed = application.packageManager.getChangedPackages(maxOf(changeSequence, 0))
            val firefoxChanged = changed?.packageNames?.any { it in FIREFOX_PACKAGES } ?: false
            if (changeSequence >= 0 && !firefoxChanged) {
                changed?.let { changeSequence = it.sequenceNumber }
                return@launch
            }
            changeSequence = changed?.sequenceNumber ?: changeSequence.coerceAtLeast(0)
            browsers.value = installedBrowsers(application, FIREFOX_PACKAGES.toList())
            Log.i(
                ModuleConstants.LOG_TAG,
                "betterfox browsers ${browsers.value.joinToString(" ") { "${it.packageName}=${it.gecko}" }}",
            )
        }
    }

    private fun currentBrowser(): Browser? = browsers.value.firstOrNull { it.packageName == selected.value }

    private fun currentPackage(): String = checkNotNull(selected.value) { "no browser selected" }

    fun onOpen(packageName: String) {
        selected.value = packageName
        refreshBackups()
    }

    fun onResume(autoCheck: Boolean) {
        refreshBrowsers()
        val last = state.getLong(CHECKED_AT, 0L)
        if (autoCheck && System.currentTimeMillis() - last > CheckInterval.inWholeMilliseconds) checkForUpdate(quiet = true)
    }

    fun setGroup(
        group: String,
        on: Boolean,
    ) {
        val pref = Prefs.FIREFOX_BETTERFOX_OFF_GROUPS[currentPackage()]
        val offGroups = app.prefs.snapshot()[pref]
        commit(pref, if (on) offGroups - group else offGroups + group)
    }

    fun setOverride(
        pref: String,
        override: BetterfoxOverride?,
    ) {
        val overridesPref = Prefs.FIREFOX_BETTERFOX_OVERRIDES[currentPackage()]
        val overrides = decodeOverrides(app.prefs.snapshot()[overridesPref])
        val next = if (override == null) overrides - pref else overrides + (pref to override)
        if (next != overrides) commit(overridesPref, encodeOverrides(next))
    }

    fun clearOverrides() {
        val overridesPref = Prefs.FIREFOX_BETTERFOX_OVERRIDES[currentPackage()]
        val overrides = decodeOverrides(app.prefs.snapshot()[overridesPref])
        if (overrides.isEmpty()) return
        lastCleared = overrides
        commit(overridesPref, "")
        _messages.tryEmit(BetterfoxMessage.Cleared(overrides.size))
    }

    fun undoClear() {
        if (lastCleared.isNotEmpty()) commit(Prefs.FIREFOX_BETTERFOX_OVERRIDES[currentPackage()], encodeOverrides(lastCleared))
    }

    fun undoRestore() {
        val (offGroups, overrides) = beforeRestore ?: return
        val pkg = currentPackage()
        app.prefs.save(Prefs.FIREFOX_BETTERFOX_OFF_GROUPS[pkg], offGroups)
        commit(Prefs.FIREFOX_BETTERFOX_OVERRIDES[pkg], overrides)
    }

    fun setFolder(tree: Uri) {
        application.contentResolver.takePersistableUriPermission(
            tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        state.edit { putString(FOLDER, tree.toString()) }
        folder.value = tree
        refreshBackups()
    }

    fun suggestedName(): String = BetterfoxBackup.fileName(currentPackage(), LocalDateTime.now())

    fun refreshBackups() {
        scope.launch(Dispatchers.IO) {
            val store = BackupFolder(application.contentResolver, folder.value)
            backups.value =
                try {
                    store
                        .list()
                        .map { doc -> BackupEntry(doc.uri, doc.name, parseOrNull { store.read(doc.uri) }) }
                        .sortedByDescending { it.backup?.created ?: LocalDateTime.MIN }
                } catch (e: SecurityException) {
                    Log.w(ModuleConstants.LOG_TAG, "betterfox backups unreadable reason=${e.message}", e)
                    emptyList()
                } catch (e: IOException) {
                    Log.w(ModuleConstants.LOG_TAG, "betterfox backups unreadable reason=${e.message}", e)
                    emptyList()
                }
        }
    }

    fun readBackup(uri: Uri): BackupEntry? =
        parseOrNull { readDocument(application.contentResolver, uri) }
            ?.let { BackupEntry(uri, documentName(application.contentResolver, uri), it) }

    private fun parseOrNull(read: () -> String): BetterfoxBackup? =
        try {
            BetterfoxBackup.parse(read())
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IOException) {
            null
        }

    fun enable(packageName: String) {
        commit(Prefs.FIREFOX_BETTERFOX[packageName], true)
    }

    fun resetAll() {
        val pkg = currentPackage()
        app.prefs.save(Prefs.FIREFOX_BETTERFOX[pkg], false)
        commit(Prefs.FIREFOX_BETTERFOX_RESET[pkg], UUID.randomUUID().toString())
        _messages.tryEmit(BetterfoxMessage.Reset)
    }

    fun checkForUpdate(
        quiet: Boolean = false,
        url: String = BuildConfig.BETTERFOX_URL,
    ) {
        val current = list.value ?: return
        if (update.value == BetterfoxUpdateState.Checking) return
        update.value = BetterfoxUpdateState.Checking
        if (!quiet) _messages.tryEmit(BetterfoxMessage.Update(BetterfoxUpdateState.Checking))
        scope.launch(Dispatchers.IO) {
            val outcome =
                try {
                    val fetched = fetchBetterfox(url)
                    state.edit { putLong(CHECKED_AT, System.currentTimeMillis()) }
                    if (fetched.list.revision > current.revision) {
                        commit(Prefs.FIREFOX_BETTERFOX_LIST, fetched.json)
                        BetterfoxUpdateState.Updated(fetched.list.betterfox, fetched.list.revision, changedPrefs(current, fetched.list))
                    } else {
                        BetterfoxUpdateState.Current
                    }
                } catch (e: BetterfoxStatusException) {
                    Log.w(ModuleConstants.LOG_TAG, "betterfox update missing url=$url status=${e.status}")
                    BetterfoxUpdateState.Missing(e.status)
                } catch (e: IOException) {
                    Log.w(ModuleConstants.LOG_TAG, "betterfox update failed url=$url reason=${e.message}")
                    BetterfoxUpdateState.Unreachable
                } catch (e: IllegalArgumentException) {
                    Log.w(ModuleConstants.LOG_TAG, "betterfox update rejected url=$url reason=${e.message}")
                    BetterfoxUpdateState.Rejected
                }
            Log.i(ModuleConstants.LOG_TAG, "betterfox update outcome=$outcome")
            update.value = outcome
            if (!quiet || outcome is BetterfoxUpdateState.Updated) _messages.tryEmit(BetterfoxMessage.Update(outcome))
        }
    }

    private fun changedPrefs(
        old: BetterfoxList,
        new: BetterfoxList,
    ): List<String> {
        val before = old.prefs.associateBy { it.pref }
        val after = new.prefs.associateBy { it.pref }
        return buildList {
            for ((pref, entry) in after) {
                val previous = before[pref]
                when {
                    previous == null -> add("+ $pref ${formatPrefValue(entry.value)}")
                    previous.value != entry.value -> add("$pref ${formatPrefValue(previous.value)} → ${formatPrefValue(entry.value)}")
                }
            }
            for (pref in before.keys - after.keys) add("- $pref")
        }
    }

    fun runCommand(command: String) {
        Log.i(ModuleConstants.LOG_TAG, "betterfox command $command")
        val name = command.substringBefore(':')
        val argument = command.substringAfter(':', "")
        when (name) {
            "backup" -> {
                backUp(argument.ifEmpty { suggestedName() })
            }

            "restore" -> {
                scope.launch(Dispatchers.IO) { readBackup(argument.toUri())?.let(::restore) }
            }

            "reset" -> {
                resetAll()
            }

            "check" -> {
                checkForUpdate(url = argument.ifEmpty { BuildConfig.BETTERFOX_URL })
            }

            "clear" -> {
                clearOverrides()
            }

            "enable" -> {
                if (argument == "off") commit(Prefs.FIREFOX_BETTERFOX[currentPackage()], false) else enable(currentPackage())
            }

            "dismiss" -> {
                dismissRun()
            }

            "group" -> {
                setGroup(argument.substringBefore('='), argument.substringAfter('=') == "on")
            }

            "override" -> {
                val pref = argument.substringBefore('=')
                val value = argument.substringAfter('=')
                setOverride(
                    pref,
                    when (value) {
                        "stock" -> BetterfoxOverride.Stock
                        "betterfox" -> null
                        else -> BetterfoxOverride.Custom(requireNotNull(parsePrefValue(value)) { "value $value" })
                    },
                )
            }

            else -> {
                Log.w(ModuleConstants.LOG_TAG, "betterfox command unknown $command")
            }
        }
    }

    fun dismissRun() {
        val finished = _run.value ?: return
        if (!finished.done) return
        _run.value = null
        pendingMessage?.let { _messages.tryEmit(it) }
        pendingMessage = null
        pendingFlash?.let { flash.value = Flash(System.currentTimeMillis(), it) }
        pendingFlash = null
    }

    fun cancelRun() {
        runJob?.cancel()
    }

    fun backUp(name: String) {
        val list = list.value ?: return
        if (_run.value != null) return
        val prefs = app.prefs.snapshot()
        val pkg = currentPackage()
        val offGroupsPref = Prefs.FIREFOX_BETTERFOX_OFF_GROUPS[pkg]
        val overridesPref = Prefs.FIREFOX_BETTERFOX_OVERRIDES[pkg]
        val offGroups = prefs[offGroupsPref]
        val overrides = decodeOverrides(prefs[overridesPref])
        val store = BackupFolder(application.contentResolver, folder.value)
        val browser = currentBrowser()
        val steps =
            groupSteps(list, offGroups) +
                listOf(
                    RunStep(
                        application.getString(R.string.betterfox_step_writing_overrides),
                        overrideCount(overrides.size),
                        StepIcon.OVERRIDES,
                    ),
                    RunStep(application.getString(R.string.betterfox_step_writing_backup), "${store.folderName}/$name", StepIcon.FILE),
                    RunStep(application.getString(R.string.betterfox_step_checking_backup), waiting(), StepIcon.FILE),
                )
        startRun(RunKind.BACKUP, steps) { recorder ->
            recorder.log("read ${offGroupsPref.key} ${JSONArray(offGroups.sorted())}")
            recorder.log("read ${overridesPref.key} ${prefs[overridesPref].ifEmpty { "{}" }}")
            val backup =
                BetterfoxBackup(
                    created = LocalDateTime.now(),
                    app = "PixelInjector ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    betterfox = list.betterfox,
                    revision = list.revision,
                    browser = pkg,
                    browserVersion = browser?.versionName?.ifEmpty { null } ?: "unknown",
                    gecko = browser?.gecko ?: list.gecko,
                    offGroups = offGroups,
                    overrides = overrides,
                )
            list.groups.forEachIndexed { index, group ->
                recorder.step(index) {
                    if (group in offGroups) {
                        recorder.log("$group off")
                    } else {
                        list.prefs.filter { it.group == group }.forEach {
                            recorder.log("user_pref(\"${it.pref}\", ${formatPrefValue(it.value)})")
                        }
                    }
                }
            }
            val base = list.groups.size
            recorder.step(base) { recorder.logOverrides(list, overrides) }
            val text = backup.encode(list)
            var written: BackupDocument? = null
            recorder.step(base + 1) {
                recorder.log("writing ${store.folderName}/$name")
                val doc = store.write(name, text)
                written = doc
                recorder.detail(base + 1, "${store.folderName}/${doc.name}")
                recorder.log("wrote ${doc.uri} ${text.length} bytes")
            }
            recorder.step(base + 2) {
                val doc = checkNotNull(written)
                val read = BetterfoxBackup.parse(store.read(doc.uri))
                check(read.overrides == overrides && read.offGroups == offGroups) { "read back differs" }
                recorder.detail(base + 2, application.getString(R.string.betterfox_backup_matches))
                recorder.log(
                    "read back ${read.overrides.size} overrides groups-off ${read.offGroups.sorted().joinToString(" ").ifEmpty { "none" }}",
                )
                pendingMessage = BetterfoxMessage.Saved(store.folderName, doc.uri, doc.name)
            }
            refreshBackups()
        }
    }

    fun changesFor(backup: BetterfoxBackup): Pair<List<OverrideChange>, List<Pair<String, Boolean>>> {
        val prefs = app.prefs.snapshot()
        val pkg = currentPackage()
        val current = decodeOverrides(prefs[Prefs.FIREFOX_BETTERFOX_OVERRIDES[pkg]])
        val offGroups = prefs[Prefs.FIREFOX_BETTERFOX_OFF_GROUPS[pkg]]
        val overrides =
            (current.keys + backup.overrides.keys)
                .sorted()
                .filter { current[it] != backup.overrides[it] }
                .map { OverrideChange(it, current[it], backup.overrides[it]) }
        val groups =
            (list.value?.groups.orEmpty())
                .filter { (it in offGroups) != (it in backup.offGroups) }
                .map { it to (it !in backup.offGroups) }
        return overrides to groups
    }

    fun restore(entry: BackupEntry) {
        val list = list.value ?: return
        val backup = entry.backup ?: return
        if (_run.value != null) return
        val prefs = app.prefs.snapshot()
        val pkg = currentPackage()
        val offGroupsPref = Prefs.FIREFOX_BETTERFOX_OFF_GROUPS[pkg]
        val overridesPref = Prefs.FIREFOX_BETTERFOX_OVERRIDES[pkg]
        beforeRestore = prefs[offGroupsPref] to prefs[overridesPref]
        val (changes, _) = changesFor(backup)
        val running = listOf(pkg)
        val browserLabel = currentBrowser()?.label ?: application.getString(R.string.betterfox_browser)
        val offGroups = backup.offGroups.filterTo(sortedSetOf()) { it in list.groups }
        val steps =
            listOf(RunStep(application.getString(R.string.betterfox_step_reading_backup), entry.name, StepIcon.FILE)) +
                groupSteps(list, offGroups) +
                listOf(
                    RunStep(
                        application.getString(R.string.betterfox_step_restoring_overrides),
                        overrideCount(backup.overrides.size),
                        StepIcon.OVERRIDES,
                    ),
                    RunStep(
                        application.getString(R.string.betterfox_step_restarting, browserLabel),
                        running.joinToString(" ") { "am force-stop $it" },
                        StepIcon.RESTART,
                    ),
                )
        startRun(RunKind.RESTORE, steps) { recorder ->
            recorder.step(0) {
                val size = readDocument(application.contentResolver, entry.uri).length
                recorder.log("read ${entry.name} ${(size + KIB - 1) / KIB} KB")
                recorder.log("header pixelinjector-betterfox 1 made by ${backup.app}")
                val relation = if (backup.revision == list.revision) "matches" else "differs from"
                recorder.log("file list revision ${backup.revision} $relation installed revision ${list.revision}")
            }
            val overrides = backup.overrides
            list.groups.forEachIndexed { index, group ->
                recorder.step(index + 1) {
                    if (group in offGroups) {
                        recorder.log("$group off")
                    } else {
                        list.prefs.filter { it.group == group }.forEach { entryPref ->
                            recorder.log("${entryPref.pref} ${displayValue(list, entryPref.pref, overrides[entryPref.pref])}")
                        }
                    }
                }
            }
            val base = list.groups.size + 1
            recorder.step(base) {
                val encoded = encodeOverrides(overrides)
                app.prefs.save(offGroupsPref, offGroups)
                recorder.log("set ${offGroupsPref.key} ${JSONArray(offGroups.toList())}")
                app.prefs.save(overridesPref, encoded)
                recorder.log("set ${overridesPref.key} ${encoded.ifEmpty { "{}" }}")
                recorder.logOverrides(list, overrides)
            }
            recorder.step(base + 1) {
                val stopped = Root.forceStop(setOf(pkg)) ?: error(application.getString(R.string.root_denied))
                recorder.log("su -c am force-stop $pkg ${if (pkg in stopped) "stopped" else "not running"}")
                recorder.detail(
                    base + 1,
                    stopped.joinToString(" ") { "am force-stop $it" }.ifEmpty { application.getString(R.string.betterfox_not_running) },
                )
            }
            pendingMessage = BetterfoxMessage.Restored(overrides.size, browserLabel)
            pendingFlash = changes.map { it.pref }
        }
    }

    private fun groupSteps(
        list: BetterfoxList,
        offGroups: Set<String>,
    ): List<RunStep> =
        list.groups.map { group ->
            val count = list.prefs.count { it.group == group }
            val detail =
                if (group in offGroups) {
                    application.getString(R.string.betterfox_group_off)
                } else {
                    application.resources.getQuantityString(R.plurals.betterfox_preferences, count, count)
                }
            RunStep(group, detail, StepIcon.GROUP)
        }

    private fun overrideCount(count: Int) = application.resources.getQuantityString(R.plurals.betterfox_overrides, count, count)

    private fun waiting() = application.getString(R.string.betterfox_waiting)

    private fun startRun(
        kind: RunKind,
        steps: List<RunStep>,
        body: suspend (RunRecorder) -> Unit,
    ) {
        pendingMessage = null
        pendingFlash = null
        _run.value = BetterfoxRun(kind, steps)
        runJob =
            scope.launch(Dispatchers.IO) {
                runLock.withLock {
                    val recorder = RunRecorder()
                    try {
                        body(recorder)
                        _run.update { it?.copy(done = true) }
                    } catch (e: CancellationException) {
                        recorder.log("cancelled", Tone.WARN)
                        _run.update { it?.copy(done = true, failed = true) }
                        pendingMessage = null
                        throw e
                    } catch (e: Exception) {
                        Log.w(ModuleConstants.LOG_TAG, "betterfox ${kind.name.lowercase()} failed reason=${e.message}", e)
                        recorder.log("failed ${e.message}", Tone.BAD)
                        _run.update { it?.copy(done = true, failed = true) }
                        pendingMessage = null
                        pendingFlash = null
                    }
                    Log.i(ModuleConstants.LOG_TAG, "betterfox ${kind.name.lowercase()} done failed=${_run.value?.failed}")
                }
            }
    }

    private inner class RunRecorder {
        fun log(
            text: String,
            tone: Tone = Tone.NEUTRAL,
        ) {
            _run.update { it?.copy(lines = it.lines + LogLine(System.currentTimeMillis(), text, tone)) }
        }

        fun logOverrides(
            list: BetterfoxList,
            overrides: Map<String, BetterfoxOverride>,
        ) {
            overrides.toSortedMap().forEach { (pref, override) ->
                log("override $pref ${displayValue(list, pref, null)} → ${displayValue(list, pref, override)}")
            }
        }

        fun detail(
            index: Int,
            text: String,
        ) {
            setStep(index) { it.copy(detail = text) }
        }

        suspend fun step(
            index: Int,
            block: suspend () -> Unit,
        ) {
            setStep(index) { it.copy(status = ReloadStatus.RELOADING) }
            try {
                block()
            } catch (e: Exception) {
                setStep(index) { it.copy(status = ReloadStatus.FAILED, detail = e.message ?: it.detail) }
                throw e
            }
            setStep(index) { it.copy(status = ReloadStatus.SUCCEEDED) }
        }

        private fun setStep(
            index: Int,
            change: (RunStep) -> RunStep,
        ) {
            _run.update { run -> run?.copy(steps = run.steps.mapIndexed { i, step -> if (i == index) change(step) else step }) }
        }
    }
}

fun displayValue(
    list: BetterfoxList,
    pref: String,
    override: BetterfoxOverride?,
): String {
    val entry = list.pref(pref)
    return when (override) {
        null -> formatPrefValue(entry?.value)
        BetterfoxOverride.Stock -> formatPrefValue(entry?.stock)
        is BetterfoxOverride.Custom -> formatPrefValue(override.value)
    }
}
