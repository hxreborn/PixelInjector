package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.content.Context
import android.os.Build
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.hook.SleepTrigger
import eu.hxreborn.pixelinjector.xposed.module
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val TWEAK = "StatusBarDoubleTap"
private const val STATUS_BAR_VIEW = "com.android.systemui.statusbar.phone.PhoneStatusBarView"

@Volatile private var current: StatusBarBindings? = null

private val trigger = SleepTrigger(TWEAK)
private val switch = Switch(Prefs.STATUS_BAR_DOUBLE_TAP) { if (it) current?.hookOnce() }

internal val statusBarDoubleTap =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(switch),
        install = XposedModule::installStatusBarDoubleTap,
    )

private class StatusBarBindings(
    val onTouchEvent: Method,
) {
    private val detectors = WeakHashMap<View, GestureDetector>()
    private val hooked = AtomicBoolean(false)

    fun hookOnce() {
        if (!hooked.compareAndSet(false, true)) return
        module.hook(onTouchEvent).intercept { chain ->
            if (switch.enabled) {
                runCatching {
                    val view = chain.thisObject as View
                    detectors
                        .getOrPut(view) { doubleTapDetector(view.context) }
                        .onTouchEvent(chain.getArg(0) as MotionEvent)
                }.onFailure { Logger.error("gesture failed tweak=$TWEAK reason=${it.message}", it) }
            }
            chain.proceed()
        }
    }

    companion object {
        fun resolve(cl: ClassLoader): StatusBarBindings {
            val view = cl.loadClass(STATUS_BAR_VIEW)
            return StatusBarBindings(
                onTouchEvent = view.requiredMethod("onTouchEvent", MotionEvent::class.java),
            )
        }
    }
}

private fun XposedModule.installStatusBarDoubleTap(cl: ClassLoader): Boolean {
    val b =
        runCatching { StatusBarBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${b.onTouchEvent.signature()}")
    current = b
    if (switch.enabled) b.hookOnce()
    return true
}

private fun doubleTapDetector(context: Context): GestureDetector =
    GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                trigger.sleep(context)
                return true
            }
        },
    )
