package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.media.MediaActionSound
import android.media.MediaPlayer
import android.os.Build
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.orNullIfMissing
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.hook.DexKit
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

private const val TWEAK = "ScreenshotSound"
private const val PLAY_LAMBDA =
    "com.android.systemui.screenshot.ScreenshotSoundControllerImpl\$playScreenshotSound\$2"
private const val CONTROLLER = "com.android.systemui.screenshot.ScreenshotController"
private const val SAMSUNG_SOUND = "com.android.systemui.screenshot.sep.ScreenshotCaptureSound"
private const val TRANSSION_VIEW = "com.android.systemui.screenshot.TrScreenshotView"
private const val UNIT = "kotlin.Unit"
private const val PLAY_FAILED_MESSAGE = "Screenshot sound failed to play"
private const val INVOKE_SUSPEND = "invokeSuspend"
private const val MOTO_SESSION =
    "com.android.systemui.screenshot.MotoGlobalScreenshot\$DisplayScreenshotSession"
private const val MOTO_CALLBACK_MESSAGE = "long screenshot get null finalBitmap"

private val switch = Switch(Prefs.SCREENSHOT_SOUND)
private val screenshotPlayback = ThreadLocal<Boolean>()

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
    val playbackCallback: Method? = null,
    val soundId: Int? = null,
) {
    val member: String
        get() = "${play.declaringClass.name.substringAfterLast('.')}#${play.name}"

    companion object {
        fun resolve(cl: ClassLoader): List<SoundBindings> {
            if (orNullIfMissing { cl.loadClass(MOTO_SESSION) } != null) {
                val callback =
                    try {
                        DexKit.method(
                            "$TWEAK.motorola",
                            cl,
                            "run usingStrings($MOTO_CALLBACK_MESSAGE)",
                        ) { bridge ->
                            bridge.findMethod {
                                matcher { name("run").usingStrings(MOTO_CALLBACK_MESSAGE) }
                            }
                        }
                    } catch (_: NoSuchMethodException) {
                        null
                    }
                callback?.let {
                    return listOf(
                        SoundBindings(
                            play =
                                MediaActionSound::class.java.requiredMethod(
                                    "play",
                                    Int::class.javaPrimitiveType,
                                ),
                            skipped = null,
                            playbackCallback = it,
                            soundId = MediaActionSound.SHUTTER_CLICK,
                        ),
                    )
                }
            }
            if (orNullIfMissing { cl.loadClass(TRANSSION_VIEW) } != null) {
                val callback =
                    try {
                        DexKit.method(
                            "$TWEAK.transsion",
                            cl,
                            "run invoking MediaPlayer.start using $CONTROLLER",
                        ) { bridge ->
                            bridge.findMethod {
                                matcher {
                                    name("run").paramCount(0)
                                    addUsingField { type(CONTROLLER) }
                                    addInvoke {
                                        declaredClass(
                                            MediaPlayer::class.java,
                                        ).name("start").paramCount(0)
                                    }
                                }
                            }
                        }
                    } catch (_: NoSuchMethodException) {
                        null
                    }
                callback?.let {
                    return listOf(
                        SoundBindings(
                            play = MediaPlayer::class.java.requiredMethod("start"),
                            skipped = null,
                            playbackCallback = it,
                        ),
                    )
                }
            }
            val samsung =
                orNullIfMissing { cl.loadClass(SAMSUNG_SOUND) }
                    ?.requiredMethod("play", Boolean::class.javaPrimitiveType)
                    ?.let { SoundBindings(play = it, skipped = null) }
            val standard = resolving { resolveStandard(cl) }
            return listOfNotNull(samsung, standard.getOrNull()).ifEmpty {
                throw checkNotNull(standard.exceptionOrNull())
            }
        }

        private fun resolveStandard(cl: ClassLoader): SoundBindings {
            val unit = { cl.loadClass(UNIT).getField("INSTANCE").get(null) }
            orNullIfMissing {
                cl
                    .loadClass(
                        PLAY_LAMBDA,
                    ).requiredMethod(INVOKE_SUSPEND, Any::class.java)
            }?.let { return SoundBindings(play = it, skipped = unit()) }
            orNullIfMissing { cl.loadClass(CONTROLLER).requiredMethod("playCameraSoundIfNeeded") }
                ?.let { return SoundBindings(play = it, skipped = null) }
            return SoundBindings(play = findPlayMethodByString(cl), skipped = unit())
        }
    }
}

private fun findPlayMethodByString(cl: ClassLoader): Method =
    DexKit.method(TWEAK, cl, "$INVOKE_SUSPEND usingStrings($PLAY_FAILED_MESSAGE)") {
        it.findMethod { matcher { name(INVOKE_SUSPEND).usingStrings(PLAY_FAILED_MESSAGE) } }
    }

private fun XposedModule.installScreenshotSound(cl: ClassLoader): Boolean {
    val bindings =
        resolving { SoundBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${bindings.joinToString(",") { it.member }}")
    for (binding in bindings) {
        binding.playbackCallback?.let { callback ->
            hook(callback).intercept { chain ->
                if (!switch.enabled || screenshotPlayback.get() == true) {
                    return@intercept chain.proceed()
                }
                screenshotPlayback.set(true)
                try {
                    chain.proceed()
                } finally {
                    screenshotPlayback.remove()
                }
            }
            deoptimize(callback)
        }
        hook(binding.play).intercept { chain ->
            if (!switch.enabled) return@intercept chain.proceed()
            if (binding.playbackCallback != null && screenshotPlayback.get() != true) {
                return@intercept chain.proceed()
            }
            if (binding.soundId != null && chain.getArg(0) != binding.soundId) {
                return@intercept chain.proceed()
            }
            Logger.info("sound skipped tweak=$TWEAK")
            binding.skipped
        }
    }
    return true
}
