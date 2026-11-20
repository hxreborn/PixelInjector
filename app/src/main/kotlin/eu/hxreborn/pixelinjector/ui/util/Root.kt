package eu.hxreborn.pixelinjector.ui.util

import android.os.Environment
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.topjohnwu.superuser.Shell
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.InstallDiagnostics
import eu.hxreborn.pixelinjector.ModuleConstants
import eu.hxreborn.pixelinjector.Target
import java.io.File

object Root {
    internal sealed interface LogRead {
        data object Denied : LogRead

        data class Lines(
            val lines: List<String>,
            val processes: Map<Int, String>,
            val installDiagnostics: Map<String, InstallDiagnostics>,
        ) : LogRead
    }

    private const val TAG_MARK = "PixelInjector"
    private const val PS_MARK = "__ps__"
    private const val DIAGNOSTICS_MARK = "__install_diagnostics__ "
    private const val UID_RANGE_PER_USER = 100_000
    private val psRow = Regex("""^\s*(\d+)\s+(\d+)\s+(\S+)$""")

    private class RunningProcess(
        val pid: Int,
        val uid: Int,
        val name: String,
    )

    fun forceStop(packages: Set<String>): List<String>? {
        if (!Shell.getShell().isRoot) {
            Shell.getCachedShell()?.close()
            return null
        }
        val script =
            packages.joinToString("\n") { pkg ->
                "pidof $pkg >/dev/null 2>&1 && am force-stop $pkg && echo $pkg"
            }
        return Shell
            .cmd(script)
            .exec()
            .out
            .map { it.trim() }
            .filter { it in packages }
    }

    fun copyReadable(
        source: String,
        target: File,
    ): Boolean {
        if (!Shell.getShell().isRoot) {
            Shell.getCachedShell()?.close()
            return false
        }
        target.delete()
        val from = shellQuote(source)
        val to = shellQuote(target.path)
        return Shell.cmd("cat $from > $to && chmod 644 $to").exec().isSuccess
    }

    internal fun readModuleLog(): LogRead {
        if (!Shell.getShell().isRoot) {
            Shell.getCachedShell()?.close()
            return LogRead.Denied
        }
        val out =
            Shell
                .cmd(
                    "cat /data/adb/*/log/modules_*.log 2>/dev/null",
                    "logcat -d -s $TAG_MARK:V 2>/dev/null",
                    "echo $PS_MARK",
                    "ps -A -o PID,UID,NAME 2>/dev/null",
                ).exec()
                .out
        val split = out.indexOf(PS_MARK).let { if (it < 0) out.size else it }
        val processes =
            out.drop(split + 1).mapNotNull { row ->
                val (pid, uid, name) = psRow.find(row)?.destructured ?: return@mapNotNull null
                RunningProcess(pid.toInt(), uid.toInt(), name)
            }
        val diagnostics =
            runCatching { readInstallDiagnostics(processes) }
                .onFailure { Log.w(ModuleConstants.LOG_TAG, "install diagnostics read failed", it) }
                .getOrDefault(emptyMap())
        return LogRead.Lines(
            out.take(split).filter { it.contains(TAG_MARK) }.distinct(),
            processes.associate { it.pid to it.name },
            diagnostics,
        )
    }

    private fun readInstallDiagnostics(processes: List<RunningProcess>): Map<String, InstallDiagnostics> {
        val data = Environment.getDataDirectory()
        val user = Process.myUid() / UID_RANGE_PER_USER
        val packages = File(data, "user_de/$user")
        val commands =
            buildList {
                for (running in processes) {
                    val name = running.name
                    val system = name == "system_server"
                    if (Target.of(name, system) == null || (!system && running.uid / UID_RANGE_PER_USER != user)) continue
                    val process = if (system) "system" else name
                    val directory = if (system) File(data, "system") else File(packages, name.substringBefore(':'))
                    val file = InstallDiagnostics.file(directory, process)
                    add("echo ${shellQuote("$DIAGNOSTICS_MARK${running.pid} $process")}; cat ${shellQuote(file.path)} 2>/dev/null")
                }
            }
        if (commands.isEmpty()) return emptyMap()
        val output = Shell.cmd(*commands.toTypedArray()).exec().out
        val bootInstant = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val headers = output.indices.filter { output[it].startsWith(DIAGNOSTICS_MARK) }
        return buildMap {
            headers.forEachIndexed { index, header ->
                val identity = output[header].removePrefix(DIAGNOSTICS_MARK)
                val pid = identity.substringBefore(' ').toIntOrNull() ?: return@forEachIndexed
                val process = identity.substringAfter(' ')
                val contents = output.subList(header + 1, headers.getOrNull(index + 1) ?: output.size).joinToString("\n")
                val diagnostics = InstallDiagnostics.read(contents, pid, BuildConfig.VERSION_CODE, bootInstant)
                if (diagnostics != null) put(process, diagnostics)
            }
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
