package eu.hxreborn.pixelinjector.xposed.hook.launcher

import android.content.res.Resources
import android.os.Build
import android.util.SparseIntArray
import android.view.View
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.optionalMethod
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.module
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

private const val TWEAK = "HideSearchBar"
private const val HOTSEAT = "com.android.launcher3.Hotseat"
private const val QSB_HEIGHT = "qsb_widget_height"
private const val UNKNOWN = 0
private const val ZERO = 1
private const val KEEP = 2

@Volatile private var current: HotseatBindings? = null

private val switch =
    Switch(Prefs.HIDE_SEARCH_BAR) {
        if (it) current?.hookOnce()
        current?.refresh()
    }

internal val hideSearchBar =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.LAUNCHER),
        prefs = listOf(switch),
        install = XposedModule::installHideSearchBar,
    )

private class HotseatBindings(
    val constructor: Constructor<*>,
    val qsb: Field,
    val setInsets: Method?,
    val getDimensionPixelSize: Method,
) {
    @Volatile var live: WeakReference<View>? = null
    private val verdicts = SparseIntArray()
    private val hooked = AtomicBoolean(false)

    fun capture(hotseat: Any) {
        val view = qsb.get(hotseat) as View? ?: return
        if (live?.get() !== view) live = WeakReference(view)
        applyTo(view)
    }

    fun refresh() {
        val view = live?.get() ?: return
        view.post {
            runCatching {
                applyTo(view)
                Logger.info("applied tweak=$TWEAK hidden=${switch.enabled}")
            }.onFailure { Logger.error("apply failed tweak=$TWEAK reason=${it.message}", it) }
        }
    }

    fun hookOnce() {
        if (!hooked.compareAndSet(false, true)) return
        module.hook(getDimensionPixelSize).intercept { chain ->
            if (!switch.enabled) return@intercept chain.proceed()
            val resources = chain.thisObject as? Resources ?: return@intercept chain.proceed()
            if (zeroed(resources, chain.getArg(0) as Int)) 0 else chain.proceed()
        }
    }

    private fun applyTo(view: View) {
        view.visibility = if (switch.enabled) View.GONE else View.VISIBLE
    }

    private fun zeroed(
        resources: Resources,
        id: Int,
    ): Boolean {
        val cached = synchronized(verdicts) { verdicts.get(id, UNKNOWN) }
        if (cached != UNKNOWN) return cached == ZERO
        val name = runCatching { resources.getResourceEntryName(id) }.getOrDefault("")
        val zero = name == QSB_HEIGHT
        synchronized(verdicts) { verdicts.put(id, if (zero) ZERO else KEEP) }
        return zero
    }

    companion object {
        fun resolve(cl: ClassLoader): HotseatBindings {
            val hotseat = cl.loadClass(HOTSEAT)
            val constructor =
                hotseat.declaredConstructors
                    .maxByOrNull { it.parameterCount }
                    ?.apply { isAccessible = true }
                    ?: throw NoSuchMethodException("Hotseat.<init>")
            val setInsets = hotseat.optionalMethod("setInsets", 1)
            if (setInsets == null) {
                Logger.warn(
                    "optional member absent tweak=$TWEAK member=Hotseat#setInsets reason=no-method",
                )
            }
            return HotseatBindings(
                constructor = constructor,
                qsb = hotseat.requiredField("mQsb"),
                setInsets = setInsets,
                getDimensionPixelSize =
                    Resources::class.java.requiredMethod(
                        "getDimensionPixelSize",
                        Int::class.javaPrimitiveType,
                    ),
            )
        }
    }
}

private fun XposedModule.installHideSearchBar(cl: ClassLoader): Boolean {
    val b =
        runCatching { HotseatBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=" +
            listOfNotNull(
                b.qsb.signature(),
                b.setInsets?.signature(),
                b.getDimensionPixelSize.signature(),
            ).joinToString(","),
    )
    current = b
    for (member in listOfNotNull<Executable>(b.constructor, b.setInsets)) {
        hook(member).intercept { chain ->
            val result = chain.proceed()
            runCatching { chain.thisObject?.let(b::capture) }
                .onFailure { Logger.error("apply failed tweak=$TWEAK reason=${it.message}", it) }
            result
        }
    }
    if (switch.enabled) b.hookOnce()
    return true
}
