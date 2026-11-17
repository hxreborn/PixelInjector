package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.content.Context
import android.media.Ringtone
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.view.View
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.sound.SystemSounds
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.Value
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean
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

private val soundPending = AtomicBoolean(false)

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
                    resolving { cl.loadClass(it) }.getOrNull()
                } ?: throw ClassNotFoundException("AuthContainerView")
            if (!View::class.java.isAssignableFrom(view)) {
                throw NoSuchMethodException("AuthContainerView extends View")
            }
            val candidates =
                view.declaredMethods.filter {
                    !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE &&
                        it.parameterCount == 0 &&
                        (it.name == ANIMATED_IN || it.name.startsWith("$ANIMATED_IN$"))
                }
            val animatedIn =
                candidates.firstOrNull { it.name == ANIMATED_IN }
                    ?: candidates.singleOrNull()
                    ?: throw NoSuchMethodException(
                        "AuthContainerView.$ANIMATED_IN candidates=${candidates.size}",
                    )
            val playToken =
                resolving {
                    cl
                        .loadClass(MULTISENSORY)
                        .requiredMethod(
                            PLAY_TOKEN,
                            Int::class.javaPrimitiveType,
                            Boolean::class.javaPrimitiveType,
                            VibrationAttributes::class.java,
                        ).also {
                            if (Modifier.isStatic(it.modifiers) || it.returnType != Void.TYPE) {
                                throw NoSuchMethodException(
                                    "MultisensoryManager.playToken(int,boolean,VibrationAttributes):void",
                                )
                            }
                        }
                }.getOrElse {
                    Logger.warn(
                        "unlock sound kept tweak=$TWEAK member=MultisensoryManager#$PLAY_TOKEN " +
                            "build=${Build.ID} reason=${it.reason()}",
                    )
                    null
                }
            return AuthBindings(animatedIn.apply { isAccessible = true }, playToken)
        }
    }
}

private fun XposedModule.installBiometricBypass(cl: ClassLoader): Boolean {
    val bindings =
        resolving { AuthBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=${bindings.animatedIn.signature()} " +
            "quiet=${bindings.playToken != null}",
    )
    bindings.playToken?.let { token ->
        hook(token).intercept { chain ->
            if (!switch.enabled) return@intercept chain.proceed()
            val quiet =
                confirmedAt != 0L && sound.value != null &&
                    chain.getArg(0) == TOKEN_UNLOCK &&
                    SystemClock.uptimeMillis() - confirmedAt <= SUPPRESS_MS
            if (!quiet) return@intercept chain.proceed()
            confirmedAt = 0
            Logger.info("unlock sound skipped tweak=$TWEAK")
            val args = chain.args.toTypedArray<Any?>()
            args[1] = false
            chain.proceed(args)
        }
    }
    hook(bindings.animatedIn).intercept { chain ->
        val result = chain.proceed()
        if (!switch.enabled) return@intercept result
        confirmSafely {
            val view = chain.thisObject as View
            val id = bindings.confirmId(view)
            if (id == 0) {
                Logger.warn("confirm skipped tweak=$TWEAK reason=no-id")
            } else {
                clickConfirm(view, id, SystemClock.uptimeMillis())
            }
        }
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
            view.postOnAnimation {
                confirmSafely { clickConfirm(view, id, start) }
            }
        }
    }
}

private inline fun confirmSafely(action: () -> Unit) {
    try {
        action()
    } catch (e: Exception) {
        Logger.error("confirm failed tweak=$TWEAK reason=${e.message}", e)
    } catch (e: LinkageError) {
        Logger.error("confirm failed tweak=$TWEAK reason=${e.message}", e)
    }
}

private fun playSound(context: Context) {
    val path = sound.value ?: return
    if (!soundPending.compareAndSet(false, true)) return
    try {
        thread(name = "PixelInjector-$TWEAK-sound") {
            try {
                ringtone?.stop()
                ringtone = SystemSounds.play(context, path) ?: error("no ringtone")
            } catch (e: Exception) {
                Logger.warn("sound failed tweak=$TWEAK path=$path reason=${e.message}", e)
            } catch (e: LinkageError) {
                Logger.warn("sound failed tweak=$TWEAK path=$path reason=${e.message}", e)
            } finally {
                soundPending.set(false)
            }
        }
    } catch (e: Exception) {
        soundPending.set(false)
        Logger.warn("sound worker failed tweak=$TWEAK reason=${e.message}", e)
    } catch (e: LinkageError) {
        soundPending.set(false)
        Logger.warn("sound worker failed tweak=$TWEAK reason=${e.message}", e)
    }
}
