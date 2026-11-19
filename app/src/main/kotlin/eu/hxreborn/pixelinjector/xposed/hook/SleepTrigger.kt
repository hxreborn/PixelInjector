package eu.hxreborn.pixelinjector.xposed.hook

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import eu.hxreborn.pixelinjector.xposed.Logger
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private const val ROOT_SLEEP_COMMAND = "input keyevent ${KeyEvent.KEYCODE_SLEEP}"

internal class SleepTrigger(
    private val tweak: String,
    private val viaRoot: Boolean,
) {
    private val pending = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    fun sleep(context: Context) {
        if (!pending.compareAndSet(false, true)) return
        try {
            context
                .getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            if (viaRoot) {
                thread(name = "PixelInjector-$tweak") {
                    try {
                        sleepWithRoot()
                    } finally {
                        pending.set(false)
                    }
                }
            } else {
                try {
                    lockAndSleepInProcess(context)
                } finally {
                    pending.set(false)
                }
            }
        } catch (e: Exception) {
            pending.set(false)
            sleepFailed(e)
        } catch (e: LinkageError) {
            pending.set(false)
            sleepFailed(e)
        }
    }

    private fun sleepWithRoot() {
        try {
            val (code, output) = runRootCommand(ROOT_SLEEP_COMMAND)
            if (code == 0) {
                Logger.info("sleep path=root tweak=$tweak exit=0")
            } else {
                Logger.error("sleep path=root tweak=$tweak exit=$code output=$output")
            }
        } catch (e: Exception) {
            sleepFailed(e)
        } catch (e: LinkageError) {
            sleepFailed(e)
        }
    }

    private fun lockAndSleepInProcess(context: Context) {
        val windowManager =
            Class
                .forName("android.view.WindowManagerGlobal")
                .getMethod("getWindowManagerService")
                .invoke(null)
        windowManager.javaClass
            .getMethod("lockNow", Bundle::class.java)
            .invoke(windowManager, null)
        val power = context.getSystemService(PowerManager::class.java)
        PowerManager::class.java
            .getMethod("goToSleep", Long::class.javaPrimitiveType)
            .invoke(power, SystemClock.uptimeMillis())
        Logger.info("sleep path=lockNow tweak=$tweak")
    }

    private fun sleepFailed(cause: Throwable) {
        val path = if (viaRoot) "root" else "lockNow"
        Logger.error(
            "sleep failed path=$path tweak=$tweak reason=${cause.cause?.message ?: cause.message}",
            cause,
        )
    }
}

private fun runRootCommand(command: String): Pair<Int, String> {
    val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
    try {
        return process.inputStream.use { input ->
            process.outputStream.close()
            if (!process.waitFor(5, TimeUnit.SECONDS)) throw TimeoutException("su timeout")
            val text =
                input
                    .bufferedReader()
                    .readText()
                    .trim()
                    .replace('\n', ' ')
            process.exitValue() to text
        }
    } finally {
        if (process.isAlive) process.destroyForcibly()
    }
}
