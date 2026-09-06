package eu.hxreborn.pixelinjector.xposed.hook.dialer

import android.os.Build
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.hook.DexKit
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

private const val TWEAK = "DialerDechip"
private const val ANCHOR = "exception thrown when producing chip"
private const val FEEDBACK_CHIP = "TRANSCRIPT_AUDIO_FEEDBACK"

private val switch = Switch(Prefs.DIALER_DECHIP)

internal val dialerDechip =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.DIALER),
        prefs = listOf(switch),
        install = XposedModule::installDialerDechip,
    )

private class ChipBindings(
    val adapters: List<Method>,
) {
    private val listFields = ConcurrentHashMap<Class<*>, List<Field>>()
    private val enumFields = ConcurrentHashMap<Class<*>, List<Field>>()

    fun strip(model: Any) {
        val fields =
            listFields.getOrPut(model.javaClass) {
                fieldsOf(model.javaClass) { List::class.java.isAssignableFrom(it) }
            }
        for (field in fields) {
            val list = field.get(model) as? MutableList<*> ?: continue
            runCatching { list.removeIf { chip -> chip != null && isFeedback(chip) } }
        }
    }

    private fun isFeedback(chip: Any): Boolean =
        enumFields
            .getOrPut(chip.javaClass) { fieldsOf(chip.javaClass) { it.isEnum } }
            .any { (it.get(chip) as? Enum<*>)?.name.equals(FEEDBACK_CHIP, ignoreCase = true) }

    private fun fieldsOf(
        cls: Class<*>,
        typed: (Class<*>) -> Boolean,
    ): List<Field> = cls.declaredFields.filter { typed(it.type) }.onEach { it.isAccessible = true }

    companion object {
        fun resolve(cl: ClassLoader): ChipBindings =
            DexKit.query(TWEAK, cl) { bridge ->
                val adapters =
                    bridge
                        .findMethod { matcher { usingStrings(ANCHOR) } }
                        .mapNotNull { runCatching { it.getMethodInstance(cl) }.getOrNull() }
                if (adapters.isEmpty()) throw NoSuchMethodException("usingStrings($ANCHOR)")
                ChipBindings(adapters)
            }
    }
}

private fun XposedModule.installDialerDechip(cl: ClassLoader): Boolean {
    val b =
        runCatching { ChipBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${b.adapters.joinToString(",") { it.signature() }}")
    for (adapter in b.adapters) {
        hook(adapter).intercept { chain ->
            val model = chain.proceed()
            if (switch.enabled && model != null) {
                runCatching { b.strip(model) }
                    .onFailure {
                        Logger.error(
                            "strip failed tweak=$TWEAK reason=${it.message}",
                            it,
                        )
                    }
            }
            model
        }
    }
    return true
}
