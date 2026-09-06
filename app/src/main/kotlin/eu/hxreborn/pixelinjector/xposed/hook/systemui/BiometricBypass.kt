package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.os.Build
import android.os.SystemClock
import android.view.View
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

private const val TWEAK = "BiometricBypass"
private const val ANIMATED_IN = "onDialogAnimatedIn"
private const val CONFIRM_ID = "button_confirm"
private const val MAX_WAIT_MS = 1_500L
private val CONTAINER_VIEWS =
    listOf(
        "com.android.systemui.biometrics.prompt.ui.AuthContainerView",
        "com.android.systemui.biometrics.AuthContainerView",
    )

private val switch = Switch(Prefs.BIOMETRIC_BYPASS)

internal val biometricBypass =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(switch),
        install = XposedModule::installBiometricBypass,
    )

private class AuthBindings(
    val animatedIn: Method,
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
            return AuthBindings(animatedIn.apply { isAccessible = true })
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
    Logger.info("resolved tweak=$TWEAK members=${b.animatedIn.signature()}")
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
            button.performClick()
            Logger.info("confirm clicked tweak=$TWEAK latencyMs=$elapsed")
        }

        elapsed >= MAX_WAIT_MS -> {
            Logger.warn("confirm skipped tweak=$TWEAK reason=no-button waitMs=$elapsed")
        }

        else -> {
            view.postOnAnimation { clickConfirm(view, id, start) }
        }
    }
}
