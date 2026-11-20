package eu.hxreborn.pixelinjector.xposed.hook.dialer

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.detail
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.hook.DexKit
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

private const val TWEAK = "DialerDechip"
private const val ANCHOR = "exception thrown when producing chip"
private const val FEEDBACK_CHIP = "TRANSCRIPT_AUDIO_FEEDBACK"
private const val FEEDBACK_CHANNEL = "phone_feedback"

private val switch = Switch(Prefs.DIALER_DECHIP)

internal val dialerDechip =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.DIALER),
        prefs = listOf(switch),
        install = XposedModule::installDialerDechip,
    )

private class ChipList(
    val holder: Field?,
    val list: Field,
    val copyFactory: Method?,
)

private class FeedbackBindings(
    val adapters: List<Method>,
    val notify: Method,
) {
    private val chipLists = ConcurrentHashMap<Class<*>, List<ChipList>>()
    private val enumFields = ConcurrentHashMap<Class<*>, List<Field>>()

    fun removeFeedbackChips(model: Any) {
        for (chipList in chipLists.getOrPut(model.javaClass) { chipListsOf(model.javaClass) }) {
            val holder = chipList.holder
            val owner = if (holder == null) model else holder.get(model) ?: continue
            val chips = chipList.list.get(owner) as? List<*> ?: continue
            if (chips.none { it != null && isFeedback(it) }) continue
            val kept = chips.filterNot { it != null && isFeedback(it) }
            chipList.list.set(owner, chipList.copyFactory?.invoke(null, kept) ?: ArrayList(kept))
        }
    }

    private fun chipListsOf(cls: Class<*>): List<ChipList> =
        instanceFields(cls).flatMap { field ->
            when {
                isList(field.type) -> {
                    listOfNotNull(chipList(null, field))
                }

                field.type.classLoader === cls.classLoader && !field.type.isEnum -> {
                    instanceFields(field.type)
                        .filter { isList(it.type) }
                        .mapNotNull { chipList(field, it) }
                }

                else -> {
                    emptyList()
                }
            }
        }

    private fun chipList(
        holder: Field?,
        list: Field,
    ): ChipList? {
        if (list.type.isAssignableFrom(ArrayList::class.java)) return ChipList(holder, list, null)
        val copyFactory =
            list.type.declaredMethods.firstOrNull {
                Modifier.isStatic(it.modifiers) &&
                    it.returnType == list.type &&
                    it.parameterTypes.contentEquals(arrayOf(Collection::class.java))
            }
        if (copyFactory == null) {
            Logger.error(
                "strip skipped tweak=$TWEAK field=${list.declaringClass.name}#${list.name} " +
                    "reason=no-copy-factory type=${list.type.name}",
            )
            return null
        }
        copyFactory.isAccessible = true
        return ChipList(holder, list, copyFactory)
    }

    private fun isFeedback(chip: Any): Boolean =
        enumFields
            .getOrPut(chip.javaClass) { instanceFields(chip.javaClass).filter { it.type.isEnum } }
            .any { (it.get(chip) as? Enum<*>)?.name.equals(FEEDBACK_CHIP, ignoreCase = true) }

    private fun isList(type: Class<*>): Boolean = List::class.java.isAssignableFrom(type)

    private fun instanceFields(cls: Class<*>): List<Field> =
        cls.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .onEach { it.isAccessible = true }

    companion object {
        fun resolve(cl: ClassLoader): FeedbackBindings {
            val adapters =
                DexKit.methods(
                    TWEAK,
                    cl,
                ) { it.findMethod { matcher { usingStrings(ANCHOR) } } }
            if (adapters.isEmpty()) throw NoSuchMethodException("usingStrings($ANCHOR)")
            val notify =
                NotificationManager::class.java.requiredMethod(
                    "notify",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Notification::class.java,
                )
            return FeedbackBindings(adapters, notify)
        }
    }
}

private fun XposedModule.installDialerDechip(cl: ClassLoader): Boolean {
    val bindings =
        resolving { FeedbackBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=" +
            (bindings.adapters + bindings.notify).joinToString(",") { it.signature() },
    )
    for (adapter in bindings.adapters) {
        hook(adapter).intercept { chain ->
            val enabled = switch.enabled
            val model = chain.proceed()
            if (enabled && model != null) {
                try {
                    bindings.removeFeedbackChips(model)
                } catch (e: Exception) {
                    Logger.error("strip failed tweak=$TWEAK reason=${e.detail()}", e)
                } catch (e: LinkageError) {
                    Logger.error("strip failed tweak=$TWEAK reason=${e.detail()}", e)
                }
            }
            model
        }
    }
    hook(bindings.notify).intercept { chain ->
        if (!switch.enabled) return@intercept chain.proceed()
        if ((chain.getArg(2) as? Notification)?.channelId != FEEDBACK_CHANNEL) {
            return@intercept chain.proceed()
        }
        Logger.info("notification skipped tweak=$TWEAK" + Logger.field("tag", chain.getArg(0)))
        null
    }
    return true
}
