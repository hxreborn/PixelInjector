package eu.hxreborn.pixelinjector.xposed.hook.files

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
import java.lang.reflect.Method

private const val TWEAK = "FilesSortByDate"
private const val SORT_MODEL = "com.android.documentsui.sorting.SortModel"
private const val M3_CONFIG = "com.android.documentsui.util.Material3Config"

private val switch = Switch(Prefs.FILES_SORT_BY_DATE)

internal val filesSortByDate =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.FILES),
        prefs = listOf(switch),
        install = XposedModule::installFilesSortByDate,
    )

private class SortBindings(
    val setDefaultDimension: Method,
    val dateId: Int,
) {
    companion object {
        fun resolve(cl: ClassLoader): SortBindings {
            val model = cl.loadClass(SORT_MODEL)
            val rawId = model.requiredField("SORT_DIMENSION_ID_DATE").getInt(null)
            val dateId =
                runCatching {
                    cl
                        .loadClass(M3_CONFIG)
                        .getMethod("getRes", Int::class.javaPrimitiveType)
                        .invoke(null, rawId) as Int
                }.getOrDefault(rawId)
            return SortBindings(
                setDefaultDimension =
                    model.requiredMethod("setDefaultDimension", Int::class.javaPrimitiveType),
                dateId = dateId,
            )
        }
    }
}

private fun XposedModule.installFilesSortByDate(cl: ClassLoader): Boolean {
    val b =
        runCatching { SortBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=${b.setDefaultDimension.signature()} dateId=${b.dateId}",
    )
    hook(b.setDefaultDimension).intercept { chain ->
        if (!switch.enabled) return@intercept chain.proceed()
        chain.proceed(arrayOf<Any?>(b.dateId))
    }
    return true
}
