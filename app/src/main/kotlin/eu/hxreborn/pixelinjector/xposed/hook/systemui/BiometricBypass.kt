package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.content.Context
import android.media.Ringtone
import android.os.Build
import android.os.SystemClock
import android.view.View
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.sound.SystemSounds
import eu.hxreborn.pixelinjector.util.optionalMethod
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.Value
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import kotlin.concurrent.thread

private const val TWEAK = "BiometricBypass"
private const val ANIMATED_IN = "onDialogAnimatedIn"
private const val CONFIRM_ID = "button_confirm"
private const val MULTISENSORY = "android.os.multisensory.MultisensoryManager"
private const val PLAY_TOKEN = "playToken"
private const val TOKEN_UNLOCK = 9
private const val SUPPRESS_MS = 2_000L
private const val MAX_WAIT_MS = 1_500L
private val CONTAINER_VIEWS =
    listOf(
        "com.android.systemui.biometrics.prompt.ui.AuthContainerView",
        "com.android.systemui.biometrics.AuthContainerView",
    )

private val switch = Switch(Prefs.BIOMETRIC_BYPASS)
private val sound = Value(Prefs.BIOMETRIC_SOUND) { it.takeIf(String::isNotEmpty) }

@Volatile
private var ringtone: Ringtone? = null

@Volatile
private var confirmedAt = 0L

internal val biometricBypass =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(switch, sound),
        install = XposedModule::installBiometricBypass,
    )

private class AuthBindings(
    val animatedIn: Method,
    val playToken: Method?,
) {
    @Volatile
    var confirmId: Int = 0

    fun confirmId(view: View): Int {
        if (confirmId == 0) {
            val context = view.context
            confirmId = context.resources.getIdentifier(CONFIRM_ID, "id", context.packageName)
        }
        return confirmId
    }

    companion object {
        fun resolve(cl: ClassLoader): AuthBindings {
            val view =
                CONTAINER_VIEWS.firstNotNullOfOrNull {
                    runCatching { cl.loadClass(it) }.getOrNull()
                } ?: throw ClassNotFoundException("AuthContainerView")
            val animatedIn =
                view.declaredMethods.firstOrNull {
                    it.parameterCount == 0 &&
                        (it.name == ANIMATED_IN || it.name.startsWith("$ANIMATED_IN$"))
                } ?: throw NoSuchMethodException("AuthContainerView.$ANIMATED_IN")
            val playToken =
                runCatching { cl.loadClass(MULTISENSORY).optionalMethod(PLAY_TOKEN, 3) }.getOrNull()
            return AuthBindings(animatedIn.apply { isAccessible = true }, playToken)
        }
    }
}

private fun XposedModule.installBiometricBypass(cl: ClassLoader): Boolean {
    val b =
        runCatching { AuthBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=${b.animatedIn.signature()} " +
            "quiet=${b.playToken != null}",
    )
    b.playToken?.let { token ->
        hook(token).intercept { chain ->
            val quiet =
                sound.value != null &&
                    chain.getArg(0) == TOKEN_UNLOCK &&
                    SystemClock.uptimeMillis() - confirmedAt <= SUPPRESS_MS
            if (!quiet) return@intercept chain.proceed()
            confirmedAt = 0
            Logger.info("unlock sound skipped tweak=$TWEAK")
            chain.proceed(arrayOf(chain.getArg(0), false, chain.getArg(2)))
        }
    }
    hook(b.animatedIn).intercept { chain ->
        val result = chain.proceed()
        if (!switch.enabled) return@intercept result
        runCatching {
            val view = chain.thisObject as View
            val id = b.confirmId(view)
            if (id == 0) {
                Logger.warn("confirm skipped tweak=$TWEAK reason=no-id")
            } else {
                clickConfirm(view, id, SystemClock.uptimeMillis())
            }
        }.onFailure { Logger.error("confirm failed tweak=$TWEAK reason=${it.message}", it) }
        result
    }
    return true
}

private fun clickConfirm(
    view: View,
    id: Int,
    start: Long,
) {
    val button = view.findViewById<View>(id)
    val elapsed = SystemClock.uptimeMillis() - start
    when {
        !switch.enabled -> {
            return
        }

        button != null && button.isShown -> {
            if (button.callOnClick()) {
                confirmedAt = SystemClock.uptimeMillis()
                playSound(view.context)
                Logger.info("confirm clicked tweak=$TWEAK latencyMs=$elapsed")
            } else {
                Logger.warn("confirm skipped tweak=$TWEAK reason=no-listener")
            }
        }

        elapsed >= MAX_WAIT_MS -> {
            Logger.warn("confirm skipped tweak=$TWEAK reason=no-button waitMs=$elapsed")
        }

        else -> {
            view.postOnAnimation { clickConfirm(view, id, start) }
        }
    }
}

private fun playSound(context: Context) {
    val path = sound.value ?: return
    thread {
        runCatching {
            ringtone?.stop()
            ringtone = SystemSounds.play(context, path) ?: error("no ringtone")
        }.onFailure { Logger.warn("sound failed tweak=$TWEAK path=$path reason=${it.message}") }
    }
}
