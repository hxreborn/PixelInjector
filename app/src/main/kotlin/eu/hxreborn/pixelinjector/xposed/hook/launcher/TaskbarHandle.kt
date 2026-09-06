package eu.hxreborn.pixelinjector.xposed.hook.launcher

import android.graphics.Color
import android.graphics.Insets
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.optionalField
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.Value
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap

private const val TWEAK = "TaskbarHandle"
private const val HANDLE_VIEW = "com.android.launcher3.taskbar.StashedHandleView"
private const val ACTIVITY_CONTEXT = "com.android.launcher3.taskbar.TaskbarActivityContext"
private const val CONTROLLERS = "com.android.launcher3.taskbar.TaskbarControllers"
private const val INSETS_PROVIDER = "android.view.InsetsFrameProvider"
private const val LIGHT_COLOR = "taskbar_stashed_handle_light_color"
private const val DARK_COLOR = "taskbar_stashed_handle_dark_color"
private const val FULL = 100
private const val UNMEASURED = -1

@Volatile private var current: TaskbarBindings? = null

private val hidePill =
    Switch(Prefs.HIDE_PILL) {
        current?.repaint()
        current?.relayout()
    }
private val navSpace =
    Value(Prefs.NAV_SPACE, onLoad = { current?.relayout() }) { it.coerceIn(0, FULL) }

internal val taskbarHandle =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.LAUNCHER),
        prefs = listOf(hidePill, navSpace),
        install = XposedModule::installTaskbarHandle,
    )

private class TaskbarBindings(
    val updateHandleColor: Method,
    val lightColor: Field,
    val darkColor: Field,
    val regionDark: Field,
    val notifyUpdateLayoutParams: Method,
    val windowParams: Field,
    val providedInsets: Field,
    val paramsForRotation: Field?,
    val controllers: Field,
    val insetsController: Field,
    val recomputeInsets: Method,
    val insetsType: Method,
    val getInsetsSize: Method,
    val setInsetsSize: Method,
) {
    @Volatile var live: WeakReference<View>? = null

    @Volatile var liveContext: WeakReference<Any>? = null
    private val stockBottoms = WeakHashMap<Array<*>, IntArray>()

    @Volatile private var stockLight: Int? = null

    @Volatile private var stockDark: Int? = null

    @Volatile private var scaled = false

    fun paint(view: View) {
        val hide = hidePill.enabled
        val light = stockLight ?: stock(view, LIGHT_COLOR).also { stockLight = it }
        val dark = stockDark ?: stock(view, DARK_COLOR).also { stockDark = it }
        lightColor.setInt(view, if (hide) Color.TRANSPARENT else light)
        darkColor.setInt(view, if (hide) Color.TRANSPARENT else dark)
    }

    private fun stock(
        view: View,
        name: String,
    ): Int {
        val context = view.context
        return context.getColor(context.resources.getIdentifier(name, "color", context.packageName))
    }

    fun repaint() {
        val view = live?.get() ?: return
        view.post {
            runCatching {
                paint(view)
                val dark = regionDark.get(view) as Boolean?
                regionDark.set(view, null)
                updateHandleColor.invoke(view, dark ?: false, true)
                Logger.info("applied tweak=$TWEAK hidePill=${hidePill.enabled}")
            }.onFailure { Logger.error("apply failed tweak=$TWEAK reason=${it.message}", it) }
        }
    }

    fun relayout() {
        val context = liveContext?.get() ?: return
        val view = live?.get() ?: return
        view.post {
            runCatching {
                val holder = controllers.get(context) ?: return@runCatching
                recomputeInsets.invoke(insetsController.get(holder))
                Logger.info("applied tweak=$TWEAK navSpace=${navSpace.value}")
            }.onFailure { Logger.error("relayout failed tweak=$TWEAK reason=${it.message}", it) }
        }
    }

    fun scale(context: Any) {
        val hide = hidePill.enabled
        if (!hide && !scaled) return
        scaled = hide
        val params = windowParams.get(context) as WindowManager.LayoutParams? ?: return
        scale(params)
        (paramsForRotation?.get(params) as Array<*>?)?.forEach { rotated ->
            (rotated as WindowManager.LayoutParams?)?.let(::scale)
        }
    }

    private fun scale(params: WindowManager.LayoutParams) {
        val providers = providedInsets.get(params) as Array<*>? ?: return
        val bottoms =
            synchronized(stockBottoms) {
                stockBottoms.getOrPut(providers) { IntArray(providers.size) { UNMEASURED } }
            }
        val percent = if (hidePill.enabled) navSpace.value else FULL
        for ((index, provider) in providers.withIndex()) {
            if (provider == null ||
                insetsType.invoke(provider) != WindowInsets.Type.navigationBars()
            ) {
                continue
            }
            val size = getInsetsSize.invoke(provider) as Insets? ?: continue
            if (bottoms[index] == UNMEASURED) bottoms[index] = size.bottom
            setInsetsSize.invoke(
                provider,
                Insets.of(
                    size.left,
                    size.top,
                    size.right,
                    bottoms[index] * percent / FULL,
                ),
            )
        }
    }

    companion object {
        fun resolve(cl: ClassLoader): TaskbarBindings {
            val handle = cl.loadClass(HANDLE_VIEW)
            val context = cl.loadClass(ACTIVITY_CONTEXT)
            val provider = Class.forName(INSETS_PROVIDER, false, cl)
            val params = WindowManager.LayoutParams::class.java
            val holder = cl.loadClass(CONTROLLERS)
            val insets = holder.requiredField("taskbarInsetsController")
            return TaskbarBindings(
                updateHandleColor =
                    handle.requiredMethod(
                        "updateHandleColor",
                        Boolean::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                    ),
                lightColor = handle.requiredField("mStashedHandleLightColor"),
                darkColor = handle.requiredField("mStashedHandleDarkColor"),
                regionDark = handle.requiredField("mIsRegionDark"),
                notifyUpdateLayoutParams = context.requiredMethod("notifyUpdateLayoutParams"),
                windowParams = context.requiredField("mWindowLayoutParams"),
                providedInsets = params.requiredField("providedInsets"),
                paramsForRotation = params.optionalField("paramsForRotation"),
                controllers = context.requiredField("mControllers"),
                insetsController = insets,
                recomputeInsets =
                    insets.type.requiredMethod("onTaskbarOrBubblebarWindowHeightOrInsetsChanged"),
                insetsType = provider.requiredMethod("getType"),
                getInsetsSize = provider.requiredMethod("getInsetsSize"),
                setInsetsSize = provider.requiredMethod("setInsetsSize", Insets::class.java),
            )
        }
    }
}

private fun XposedModule.installTaskbarHandle(cl: ClassLoader): Boolean {
    val b =
        runCatching { TaskbarBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=${b.updateHandleColor.signature()}," +
            b.notifyUpdateLayoutParams.signature(),
    )
    current = b
    hook(b.updateHandleColor).intercept { chain ->
        runCatching {
            val view = chain.thisObject as View
            if (b.live?.get() !== view) b.live = WeakReference(view)
            b.paint(view)
        }.onFailure { Logger.error("paint failed tweak=$TWEAK reason=${it.message}", it) }
        chain.proceed()
    }
    hook(b.notifyUpdateLayoutParams).intercept { chain ->
        runCatching {
            val context = chain.thisObject
            if (b.liveContext?.get() !== context) b.liveContext = WeakReference(context)
            b.scale(context)
        }.onFailure { Logger.error("scale failed tweak=$TWEAK reason=${it.message}", it) }
        chain.proceed()
    }
    return true
}
