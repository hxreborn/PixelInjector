package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.os.Build
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TWEAK = "WarmTiles"
private const val TILE_SERVICES = "com.android.systemui.qs.external.TileServices"
private const val SAVED_LIVE = "live"
private const val SAVED_ORIGINAL = "original"

@Volatile private var current: TileServicesBindings? = null

private val switch =
    Switch(Prefs.WARM_TILES) { _ ->
        current?.let { b -> b.live?.get()?.let { live -> b.apply(live, "applied") } }
    }

internal val warmTiles =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(switch),
        saveState = {
            current?.let { b ->
                hashMapOf(
                    SAVED_LIVE to b.live?.get(),
                    SAVED_ORIGINAL to b.original,
                )
            }
        },
        restoreState = ::restoreWarmTiles,
        install = XposedModule::installWarmTiles,
    )

private class TileServicesBindings(
    val cls: Class<*>,
    val maxBound: Field,
    val recalculateBindAllowance: Method,
) {
    val cap: Any =
        if (maxBound.type == Byte::class.javaPrimitiveType) Byte.MAX_VALUE else Int.MAX_VALUE

    @Volatile var live: WeakReference<Any>? = null

    @Volatile var original: Any? = null

    companion object {
        fun resolve(cl: ClassLoader): TileServicesBindings {
            val cls = cl.loadClass(TILE_SERVICES)
            return TileServicesBindings(
                cls,
                cls.requiredField("mMaxBound"),
                cls.requiredMethod("recalculateBindAllowance"),
            )
        }
    }

    fun capture(instance: Any) {
        live = WeakReference(instance)
        original = runCatching { maxBound.get(instance) }.getOrNull()?.takeIf { it != cap }
        if (switch.enabled) apply(instance, "installed")
    }

    fun apply(
        instance: Any,
        verb: String,
    ) {
        val value = if (switch.enabled) cap else original ?: return
        runCatching { maxBound.set(instance, value) }
            .onSuccess { Logger.info("$verb tweak=$TWEAK mMaxBound=$value") }
            .onFailure { Logger.error("$verb failed tweak=$TWEAK reason=${it.message}", it) }
    }
}

private fun XposedModule.installWarmTiles(cl: ClassLoader): Boolean {
    val b =
        runCatching { TileServicesBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=${b.maxBound.signature()}," +
            b.recalculateBindAllowance.signature(),
    )
    current = b
    hook(b.recalculateBindAllowance).intercept { chain ->
        val instance = chain.thisObject
        if (instance != null && b.live?.get() !== instance) {
            runCatching { b.capture(instance) }
                .onFailure { Logger.error("capture failed tweak=$TWEAK reason=${it.message}", it) }
        }
        chain.proceed()
    }
    return true
}

private fun restoreWarmTiles(saved: Any?) {
    val b = current ?: return
    val map = saved as? Map<*, *>
    val carried = map?.get(SAVED_LIVE)
    if (carried == null || !b.cls.isInstance(carried)) {
        Logger.warn("restore skipped tweak=$TWEAK reason=no-live")
        return
    }
    b.live = WeakReference(carried)
    b.original = map[SAVED_ORIGINAL]
    b.apply(carried, "carried")
}
