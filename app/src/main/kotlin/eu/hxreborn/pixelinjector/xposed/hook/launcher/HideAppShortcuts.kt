package eu.hxreborn.pixelinjector.xposed.hook.launcher

import android.os.Build
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.orNullIfMissing
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

private const val TWEAK = "HideAppShortcuts"
private const val POPUP = "com.android.launcher3.popup.PopupContainerWithArrow"
private const val ITEM_INFO = "com.android.launcher3.model.data.ItemInfo"
private const val BUBBLE_TEXT_VIEW = "com.android.launcher3.BubbleTextView"

private val switch = Switch(Prefs.HIDE_APP_SHORTCUTS)

internal val hideAppShortcuts =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.LAUNCHER),
        prefs = listOf(switch),
        install = XposedModule::installHideAppShortcuts,
    )

private class PopupBindings(
    val showRows: List<Method>,
    val countIndex: Int,
    val loadAppShortcuts: Method?,
) {
    companion object {
        fun resolve(cl: ClassLoader): PopupBindings {
            val popup = cl.loadClass(POPUP)
            val itemInfo = cl.loadClass(ITEM_INFO)
            val iconRows =
                resolving {
                    popup.requiredMethod(
                        "populateAndShowRows",
                        cl.loadClass(BUBBLE_TEXT_VIEW),
                        Int::class.javaPrimitiveType,
                        List::class.java,
                    )
                }.getOrNull()
            if (iconRows != null) return PopupBindings(listOf(iconRows), 1, null)
            val showRows =
                resolving {
                    popup.requiredMethod(
                        "showComposePopup",
                        Int::class.javaPrimitiveType,
                        List::class.java,
                    )
                }.recoverCatching {
                    popup.requiredMethod(
                        "populateAndShowRows",
                        itemInfo,
                        Int::class.javaPrimitiveType,
                        List::class.java,
                    )
                }.getOrNull()
            if (showRows == null) {
                // Pixel Launcher 17 CP2A.260605.012
                val countRows =
                    orNullIfMissing {
                        popup.requiredMethod(
                            "populateAndShowRows",
                            Int::class.javaPrimitiveType,
                            List::class.java,
                        )
                    }
                if (countRows != null) return PopupBindings(listOf(countRows), 0, null)
                val icon = cl.loadClass(BUBBLE_TEXT_VIEW)
                val count = Int::class.javaPrimitiveType
                val list = List::class.java
                val legacyRows =
                    listOfNotNull(
                        orNullIfMissing {
                            popup.requiredMethod(
                                "populateAndShowRowsMaterialU",
                                icon,
                                count,
                                list,
                            )
                        },
                        orNullIfMissing {
                            popup.requiredMethod(
                                "populateAndShow",
                                icon,
                                count,
                                list,
                                list,
                            )
                        },
                    )
                if (legacyRows.isEmpty()) {
                    throw NoSuchMethodException(
                        "${popup.simpleName} popup rows",
                    )
                }
                return PopupBindings(legacyRows, 1, null)
            }
            return PopupBindings(
                showRows = listOf(showRows),
                countIndex = showRows.parameterTypes.indexOf(Int::class.javaPrimitiveType),
                loadAppShortcuts =
                    popup.requiredMethod(
                        "loadAppShortcuts",
                        itemInfo,
                    ),
            )
        }
    }
}

private fun XposedModule.installHideAppShortcuts(cl: ClassLoader): Boolean {
    val bindings =
        resolving { PopupBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    val members = bindings.showRows + listOfNotNull(bindings.loadAppShortcuts)
    Logger.info("resolved tweak=$TWEAK members=${members.joinToString(",") { it.signature() }}")
    for (showRows in bindings.showRows) {
        hook(showRows).intercept { chain ->
            if (!switch.enabled) return@intercept chain.proceed()
            chain.proceed(chain.args.toTypedArray<Any?>().also { it[bindings.countIndex] = 0 })
        }
    }
    bindings.loadAppShortcuts?.let { loadAppShortcuts ->
        hook(loadAppShortcuts).intercept { chain ->
            if (switch.enabled) null else chain.proceed()
        }
    }
    return true
}
