package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.BatteryManager
import android.os.Build
import android.text.SpannableStringBuilder
import android.view.View
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.findFieldUpward
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.optionalMethod
import eu.hxreborn.pixelinjector.util.orNullIfMissing
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Locale
import kotlin.math.abs

private const val TWEAK = "ChargingDetails"
private const val GOOGLE_CONTROLLER =
    "com.google.android.systemui.statusbar.KeyguardIndicationControllerGoogle"
private const val CONTROLLER = "com.android.systemui.statusbar.KeyguardIndicationController"
private const val SAMSUNG_CONTROLLER =
    "com.android.systemui.statusbar.KeyguardSecIndicationController"
private const val SAMSUNG_EVENT = "com.android.systemui.statusbar.IndicationEventType"
private const val SAMSUNG_POSITION = "com.android.systemui.statusbar.IndicationPosition"
private const val ROTATE_CONTROLLER =
    "com.android.systemui.keyguard.KeyguardIndicationRotateTextViewController"
private const val INDICATION = "com.android.systemui.keyguard.KeyguardIndication"
private const val INDICATION_TYPE_BATTERY = 3
private const val SAMSUNG_EVENT_ARG = 1
private const val SAMSUNG_TEXT_ARG = 2
private const val MICRO = 1_000_000f
private const val MILLI = 1_000f
private const val TENTHS = 10f

private val switch = Switch(Prefs.CHARGING_DETAILS)

internal val chargingDetails =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(switch),
        install = XposedModule::installChargingDetails,
    )

private class IndicationBindings(
    val methods: List<Method>,
    val context: Field,
    val message: Field?,
    val batteryEvents: Set<Any> = emptySet(),
) {
    fun detailed(
        owner: Any?,
        text: CharSequence,
    ): CharSequence? {
        val hostContext =
            when (val value = owner?.let(context::get)) {
                is Context -> value
                is View -> value.context
                else -> null
            } ?: return null
        return details(hostContext)?.let { SpannableStringBuilder(text).append('\n').append(it) }
    }

    companion object {
        fun resolve(cl: ClassLoader): IndicationBindings {
            orNullIfMissing { cl.loadClass(SAMSUNG_CONTROLLER) }?.let { controller ->
                val event = cl.loadClass(SAMSUNG_EVENT)
                val position = cl.loadClass(SAMSUNG_POSITION)
                return IndicationBindings(
                    methods =
                        listOf(
                            controller.requiredMethod(
                                "addIndication",
                                position,
                                event,
                                CharSequence::class.java,
                                ColorStateList::class.java,
                            ),
                            controller.requiredMethod(
                                "addIndicationTimeout",
                                position,
                                event,
                                CharSequence::class.java,
                                ColorStateList::class.java,
                                Boolean::class.javaPrimitiveType,
                            ),
                        ),
                    context =
                        controller.findFieldUpward("mContext")
                            ?: throw NoSuchFieldException("${controller.simpleName}.mContext"),
                    message = null,
                    batteryEvents =
                        listOf("BATTERY", "BATTERY_RESTING")
                            .map { checkNotNull(event.getField(it).get(null)) }
                            .toSet(),
                )
            }
            val controller =
                resolving {
                    cl.loadClass(GOOGLE_CONTROLLER)
                }.getOrElse { cl.loadClass(CONTROLLER) }
            val computePower =
                controller
                    .optionalMethod("computePowerIndication", 0)
                    ?.takeUnless { Modifier.isAbstract(it.modifiers) }
            if (computePower != null) {
                return IndicationBindings(
                    methods = listOf(computePower),
                    context =
                        controller.findFieldUpward("mContext")
                            ?: throw NoSuchFieldException("${controller.simpleName}.mContext"),
                    message = null,
                )
            }
            // HyperOS 3 SystemUI OS3.0.303 BP2A.250605.031.A3
            val rotate = cl.loadClass(ROTATE_CONTROLLER)
            val indication = cl.loadClass(INDICATION)
            return IndicationBindings(
                methods =
                    listOf(
                        rotate.requiredMethod(
                            "updateIndication",
                            Int::class.javaPrimitiveType,
                            indication,
                            Boolean::class.javaPrimitiveType,
                        ),
                    ),
                context =
                    rotate.findFieldUpward("mView")
                        ?: throw NoSuchFieldException("${rotate.simpleName}.mView"),
                message = indication.requiredField("mMessage"),
            )
        }
    }
}

private fun details(context: Context): String? {
    val battery =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val batteryManager = context.getSystemService(BatteryManager::class.java) ?: return null
    val amps =
        abs(batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)) / MICRO
    val volts = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / MILLI
    val celsius = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / TENTHS
    return String.format(
        Locale.ROOT,
        "%.1f W • %.2f V • %.2f A • %.1f °C",
        amps * volts,
        volts,
        amps,
        celsius,
    )
}

private fun XposedModule.installChargingDetails(cl: ClassLoader): Boolean {
    val bindings =
        resolving { IndicationBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${bindings.methods.joinToString { it.signature() }}")
    for (method in bindings.methods) {
        val batteryEvents = bindings.batteryEvents
        if (batteryEvents.isNotEmpty()) {
            hook(method).intercept { chain ->
                if (!switch.enabled || chain.getArg(SAMSUNG_EVENT_ARG) !in batteryEvents) {
                    return@intercept chain.proceed()
                }
                val text =
                    chain.getArg(SAMSUNG_TEXT_ARG) as? CharSequence
                        ?: return@intercept chain.proceed()
                if (text.isEmpty()) return@intercept chain.proceed()
                val detailed =
                    try {
                        bindings.detailed(chain.thisObject, text)
                    } catch (e: Exception) {
                        Logger.error("details failed tweak=$TWEAK reason=${e.message}", e)
                        null
                    } catch (e: LinkageError) {
                        Logger.error("details failed tweak=$TWEAK reason=${e.message}", e)
                        null
                    } ?: return@intercept chain.proceed()
                val args = chain.args.toTypedArray<Any?>()
                args[SAMSUNG_TEXT_ARG] = detailed
                chain.proceed(args)
            }
            continue
        }
        val message = bindings.message
        if (message == null) {
            hook(method).intercept { chain ->
                val result = chain.proceed()
                if (!switch.enabled || result !is String) return@intercept result
                runCatching { bindings.detailed(chain.thisObject, result)?.toString() }
                    .onFailure {
                        Logger.error(
                            "details failed tweak=$TWEAK reason=${it.message}",
                            it,
                        )
                    }.getOrNull()
                    ?: result
            }
            continue
        }
        hook(method).intercept { chain ->
            val indication = chain.getArg(1)
            if (switch.enabled && chain.getArg(0) == INDICATION_TYPE_BATTERY &&
                indication != null
            ) {
                runCatching {
                    (message.get(indication) as? CharSequence)
                        ?.let { bindings.detailed(chain.thisObject, it) }
                        ?.let { message.set(indication, it) }
                }.onFailure { Logger.error("details failed tweak=$TWEAK reason=${it.message}", it) }
            }
            chain.proceed()
        }
    }
    return true
}
