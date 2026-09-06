package eu.hxreborn.pixelinjector.xposed.hook.gboard

import android.content.res.Configuration
import android.content.res.TypedArray
import android.graphics.Color
import android.os.Build
import android.util.SparseIntArray
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.module
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

private const val TWEAK = "GboardBlack"
private const val SURFACE_PREFIX = "system_surface_container"
private const val HIGH_VARIANT = "high"
private const val UNKNOWN = 0
private const val BLACK = 1
private const val KEEP = 2

@Volatile private var current: TypedArrayBindings? = null

private val switch = Switch(Prefs.GBOARD_BLACK) { if (it) current?.hookOnce() }

internal val gboardBlack =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.GBOARD),
        prefs = listOf(switch),
        install = XposedModule::installGboardBlack,
    )

private class TypedArrayBindings(
    val getColor: Method,
) {
    private val verdicts = SparseIntArray()
    private val hooked = AtomicBoolean(false)

    fun hookOnce() {
        if (!hooked.compareAndSet(false, true)) return
        module.hook(getColor).intercept { chain ->
            if (!switch.enabled) return@intercept chain.proceed()
            val result = chain.proceed()
            val array = chain.thisObject as? TypedArray ?: return@intercept result
            val config = array.resources?.configuration ?: return@intercept result
            if (config.uiMode and Configuration.UI_MODE_NIGHT_MASK !=
                Configuration.UI_MODE_NIGHT_YES
            ) {
                return@intercept result
            }
            val id = array.getResourceId(chain.getArg(0) as Int, 0)
            if (id != 0 && paintsBlack(array, id)) Color.BLACK else result
        }
        Logger.debug { "hooked tweak=$TWEAK" }
    }

    private fun paintsBlack(
        array: TypedArray,
        id: Int,
    ): Boolean {
        val cached = synchronized(verdicts) { verdicts.get(id, UNKNOWN) }
        if (cached != UNKNOWN) return cached == BLACK
        val name = runCatching { array.resources.getResourceEntryName(id) }.getOrDefault("")
        val black = name.startsWith(SURFACE_PREFIX) && !name.contains(HIGH_VARIANT)
        synchronized(verdicts) { verdicts.put(id, if (black) BLACK else KEEP) }
        return black
    }

    companion object {
        fun resolve(): TypedArrayBindings =
            TypedArrayBindings(
                TypedArray::class.java.requiredMethod(
                    "getColor",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                ),
            )
    }
}

private fun XposedModule.installGboardBlack(cl: ClassLoader): Boolean {
    val b =
        runCatching { TypedArrayBindings.resolve() }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${b.getColor.signature()}")
    current = b
    if (switch.enabled) b.hookOnce()
    return true
}
