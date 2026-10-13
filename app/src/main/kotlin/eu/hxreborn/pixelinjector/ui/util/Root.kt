package eu.hxreborn.pixelinjector.ui.util

import com.topjohnwu.superuser.Shell
import eu.hxreborn.pixelinjector.ModuleConstants
import java.io.File

object Root {
    sealed interface LogRead {
        data object Denied : LogRead

        data class Lines(
            val lines: List<String>,
            val processes: Map<Int, String>,
        ) : LogRead
    }

    private const val TAG_MARK = "PixelInjector"
    private const val PS_MARK = "__ps__"
    private val psRow = Regex("""^\s*(\d+)\s+(\S+)$""")

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

    sealed interface Install {
        data object Denied : Install

        data object Done : Install

        data class Failed(
            val message: String,
        ) : Install
    }

    fun installApk(apk: File): Install {
        if (!Shell.getShell().isRoot) {
            Shell.getCachedShell()?.close()
            return Install.Denied
        }
        val quoted = "'" + apk.path.replace("'", "'\\''") + "'"
        val result = Shell.cmd("cat $quoted | pm install -r -S ${apk.length()}").exec()
        val output = (result.out + result.err).joinToString(" ").trim()
        return if (result.isSuccess &&
            output.contains("Success")
        ) {
            Install.Done
        } else {
            Install.Failed(output.ifEmpty { "exit ${result.code}" })
        }
    }

    fun readModuleLog(): LogRead {
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
                    "ps -A -o PID,NAME 2>/dev/null",
                ).exec()
                .out
        val split = out.indexOf(PS_MARK).let { if (it < 0) out.size else it }
        val processes =
            out
                .drop(split + 1)
                .mapNotNull { row -> psRow.find(row)?.destructured?.let { (pid, name) -> pid.toInt() to name } }
                .toMap()
        return LogRead.Lines(out.take(split).filter { it.contains(TAG_MARK) }.distinct(), processes)
    }
}
