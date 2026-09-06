package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.os.Build
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

private const val TWEAK = "ScreenshotSound"
private const val PLAY_LAMBDA =
    "com.android.systemui.screenshot.ScreenshotSoundControllerImpl\$playScreenshotSound\$2"
private const val CONTROLLER = "com.android.systemui.screenshot.ScreenshotController"
private const val UNIT = "kotlin.Unit"

private val switch = Switch(Prefs.SCREENSHOT_SOUND)

internal val screenshotSound =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI, Target.SCREENSHOT),
        prefs = listOf(switch),
        install = XposedModule::installScreenshotSound,
    )

private class SoundBindings(
    val play: Method,
    val skipped: Any?,
) {
    val member: String
        get() = "${play.declaringClass.name.substringAfterLast('.')}#${play.name}"

    companion object {
        fun resolve(cl: ClassLoader): SoundBindings {
            val lambda = runCatching { cl.loadClass(PLAY_LAMBDA) }.getOrNull()
            if (lambda != null) {
                return SoundBindings(
                    play = lambda.requiredMethod("invokeSuspend", Any::class.java),
                    skipped = cl.loadClass(UNIT).getField("INSTANCE").get(null),
                )
            }
            return SoundBindings(
                play = cl.loadClass(CONTROLLER).requiredMethod("playCameraSoundIfNeeded"),
                skipped = null,
            )
        }
    }
}

private fun XposedModule.installScreenshotSound(cl: ClassLoader): Boolean {
    val b =
        runCatching { SoundBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${b.member}")
    hook(b.play).intercept { chain ->
        if (!switch.enabled) return@intercept chain.proceed()
        Logger.info("sound skipped tweak=$TWEAK")
        b.skipped
    }
    return true
}
