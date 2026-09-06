package eu.hxreborn.pixelinjector.ui.reload

import android.util.Log
import eu.hxreborn.pixelinjector.ModuleConstants
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.HotReloadResult
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class HotReloadRunner(
    private val scope: CoroutineScope,
    private val filesDir: File,
) {
    private val _state = MutableStateFlow<ReloadRun?>(null)
    val state: StateFlow<ReloadRun?> = _state.asStateFlow()

    private var job: Job? = null

    @Volatile
    private var cancelled = false

    fun start(
        service: XposedService,
        targets: List<HookedTarget>,
    ) {
        if (job?.isActive == true || targets.isEmpty()) return
        cancelled = false
        val startedAt = System.currentTimeMillis()
        _state.value = ReloadRun(rows = targets.map { ReloadRow(it) })
        val n = targets.size
        log("hot reload $n target" + if (n == 1) "" else "s")
        job =
            scope.launch {
                var succeeded = 0
                var failed = 0
                for ((i, target) in targets.withIndex()) {
                    if (cancelled) break
                    updateRow(i) { it.copy(status = ReloadStatus.RELOADING, startedAt = System.currentTimeMillis()) }
                    log("[${i + 1}/$n] proc=${target.processName} pid=${target.pid} uid=${target.uid}", level = Log.DEBUG)
                    val result = withContext(Dispatchers.IO) { runCatching { awaitReload(service, target) } }
                    val status = result.fold({ ReloadStatus.valueOf(it.status().name) }, { ReloadStatus.THROWN })
                    val message = result.fold({ it.message() }, { it.message ?: it.javaClass.simpleName })
                    if (status == ReloadStatus.SUCCEEDED) succeeded++ else failed++
                    updateRow(i) { it.copy(status = status, message = message) }
                    log("result status=$status message=$message", toneOf(status), level = Log.DEBUG)
                }
                log("done succeeded=$succeeded failed=$failed")
                _state.update { it?.copy(done = true) }
                _state.value?.let { run ->
                    runCatching { writeReloadLog(filesDir, startedAt, run.lines) }.onFailure {
                        Log.w(
                            ModuleConstants.LOG_TAG,
                            "reload log write failed reason=${it.message}",
                        )
                    }
                }
            }
    }

    private suspend fun awaitReload(
        service: XposedService,
        target: HookedTarget,
    ): HotReloadResult =
        suspendCancellableCoroutine { cont ->
            service.hotReloadModule(target, null) { _, result -> if (cont.isActive) cont.resume(result) }
        }

    fun cancel() {
        cancelled = true
    }

    fun onServiceDied() {
        if (_state.value?.done != false) return
        job?.cancel()
        log("service died", Tone.BAD)
        _state.update { it?.copy(done = true) }
    }

    fun dismiss() {
        if (_state.value?.done == true) _state.value = null
    }

    private fun updateRow(
        index: Int,
        transform: (ReloadRow) -> ReloadRow,
    ) {
        _state.update { run -> run?.copy(rows = run.rows.mapIndexed { i, row -> if (i == index) transform(row) else row }) }
    }

    private fun log(
        text: String,
        tone: Tone = Tone.NEUTRAL,
        level: Int = Log.INFO,
    ) {
        Log.println(level, ModuleConstants.LOG_TAG, "reload $text")
        _state.update { run -> run?.copy(lines = run.lines + LogLine(System.currentTimeMillis(), text, tone)) }
    }
}
