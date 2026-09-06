package eu.hxreborn.pixelinjector.xposed.hook

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.KeyEvent
import eu.hxreborn.pixelinjector.xposed.Logger
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal const val SLEEP_LEVEL = 0x5049_5853

internal class SleepTrigger(
    private val tweak: String,
) {
    @SuppressLint("MissingPermission")
    fun sleep(context: Context) {
        context
            .getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        val signaled =
            context
                .getSystemService(PowerManager::class.java)
                .isWakeLockLevelSupported(SLEEP_LEVEL)
        if (signaled) Logger.info("sleep path=signal tweak=$tweak") else thread { sleepWithRoot() }
    }

    private fun sleepWithRoot() {
        val code = runCatching { su("input keyevent ${KeyEvent.KEYCODE_SLEEP}") }.getOrDefault(-1)
        val level = if (code == 0) Log.INFO else Log.ERROR
        Logger.log(level, "sleep path=root tweak=$tweak exit=$code")
    }
}

private fun su(cmd: String): Int {
    val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
    p.outputStream.close()
    if (!p.waitFor(5, TimeUnit.SECONDS)) {
        p.destroyForcibly()
        return -1
    }
    return p.exitValue()
}
