package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.os.Build
import eu.hxreborn.pixelinjector.ModuleConstants.SYSTEMUI_PACKAGE
import eu.hxreborn.pixelinjector.icons.ShadeIcons
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethods
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.Value
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TWEAK = "EmptyShade"
private const val CONTENT =
    "com.android.systemui.notifications.stack.emptyshade.ui.composable.EmptyShadeContentImplKt"
private const val RESOURCE_ICON = "com.android.systemui.common.shared.model.Icon\$Resource"
private const val LOADED_ICON = "com.android.systemui.common.shared.model.Icon\$Loaded"
private const val STOCK_ICON = "ic_trophy"
private const val DRAWABLE_TYPE = "drawable"
private const val TEXT_ARG = 0
private const val ICON_ARG = 1
private const val SNAPSHOT_STATE = "androidx.compose.runtime.SnapshotStateKt"
private const val STATE = "androidx.compose.runtime.State"
private const val MUTABLE_STATE = "androidx.compose.runtime.MutableState"
private const val DEFAULT_POLICY_ARG_MASK = 2

@Volatile private var bindings: ShadeBindings? = null

private val requestRecompose: (Any?) -> Unit = {
    runCatching { bindings?.requestRecompose() }
        .onFailure { Logger.warn("recompose failed tweak=$TWEAK reason=${it.reason()}") }
}

private val switch = Switch(Prefs.EMPTY_SHADE, requestRecompose)
private val customText = Value(Prefs.EMPTY_SHADE_TEXT, requestRecompose) { it.trim() }
private val customIcon =
    Value(Prefs.EMPTY_SHADE_ICON_PATH, requestRecompose) {
        it.takeIf(String::isNotEmpty)?.let(::renderableIcon)
    }

private fun renderableIcon(pathData: String): Drawable? =
    runCatching {
        val icon = ShadeIcons.fromPathData(pathData)
        check(icon.intrinsicWidth > 0 && icon.intrinsicWidth == icon.intrinsicHeight) {
            "intrinsic=${icon.intrinsicWidth}x${icon.intrinsicHeight}"
        }
        FixedSizeNoThrowIcon(icon).also { it.recordTestDraw() }
    }.onFailure { Logger.warn("icon rejected tweak=$TWEAK reason=${it.reason()}", it) }.getOrNull()

private class FixedSizeNoThrowIcon(
    icon: Drawable,
) : DrawableWrapper(icon) {
    private val intrinsicSize = icon.intrinsicWidth

    @Volatile private var drawFailed = false

    override fun getIntrinsicWidth(): Int = intrinsicSize

    override fun getIntrinsicHeight(): Int = intrinsicSize

    fun recordTestDraw() {
        val picture = Picture()
        setBounds(0, 0, intrinsicSize, intrinsicSize)
        super.draw(picture.beginRecording(intrinsicSize, intrinsicSize))
        picture.endRecording()
    }

    override fun draw(canvas: Canvas) {
        if (drawFailed) return
        runCatching { super.draw(canvas) }.onFailure {
            drawFailed = true
            Logger.error("draw failed tweak=$TWEAK reason=${it.reason()}", it)
        }
    }
}

internal val emptyShade =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(switch, customText, customIcon),
        saveState = { bindings?.recomposeState },
        restoreState = { saved -> bindings?.restoreRecomposeState(saved) },
        install = XposedModule::installEmptyShade,
    )

private class ShadeBindings(
    val content: Method,
    private val resourceIconClass: Class<*>,
    private val resourceIconId: Field,
    private val newLoadedIcon: Constructor<*>,
    private val currentApplication: Method,
    private val getStateValue: Method,
    private val setStateValue: Method,
    @Volatile var recomposeState: Any,
) {
    @Volatile private var stockIconId = 0

    @Volatile private var lastLoaded: Pair<Drawable, Any>? = null

    fun rewriteCaughtUp(args: Array<Any?>): Boolean {
        if (!isStockIcon(args[ICON_ARG])) return false
        customText.value.takeIf(String::isNotEmpty)?.let { args[TEXT_ARG] = it }
        customIcon.value?.let { args[ICON_ARG] = loadedIcon(it) }
        return true
    }

    private fun isStockIcon(arg: Any?): Boolean {
        if (!resourceIconClass.isInstance(arg)) return false
        if (stockIconId == 0) stockIconId = lookUpStockIconId()
        return stockIconId != 0 && resourceIconId.getInt(arg) == stockIconId
    }

    private fun lookUpStockIconId(): Int =
        runCatching {
            (currentApplication.invoke(null) as? Context)
                ?.resources
                ?.getIdentifier(STOCK_ICON, DRAWABLE_TYPE, SYSTEMUI_PACKAGE)
        }.getOrNull() ?: 0

    private fun loadedIcon(drawable: Drawable): Any {
        lastLoaded?.takeIf { it.first === drawable }?.let { return it.second }
        val icon = newLoadedIcon.newInstance(drawable)
        Logger.info("applied tweak=$TWEAK icon=custom")
        lastLoaded = drawable to icon
        return icon
    }

    fun readRecomposeState() {
        getStateValue.invoke(recomposeState)
    }

    fun requestRecompose() {
        setStateValue.invoke(recomposeState, getStateValue.invoke(recomposeState) as Int + 1)
    }

    fun restoreRecomposeState(saved: Any?) {
        if (saved == null || !getStateValue.declaringClass.isInstance(saved)) return
        recomposeState = saved
        requestRecompose()
    }

    companion object {
        fun resolve(cl: ClassLoader): ShadeBindings {
            val resourceIconClass = cl.loadClass(RESOURCE_ICON)
            val newState =
                cl.loadClass(SNAPSHOT_STATE).requiredMethods("mutableStateOf\$default").single()
            return ShadeBindings(
                content =
                    cl
                        .loadClass(CONTENT)
                        .requiredMethods("EmptyShadeContent")
                        .single { it.parameterTypes.firstOrNull() == String::class.java },
                resourceIconClass = resourceIconClass,
                resourceIconId = resourceIconClass.requiredField("resId"),
                newLoadedIcon = cl.loadClass(LOADED_ICON).getConstructor(Drawable::class.java),
                currentApplication =
                    Class
                        .forName("android.app.ActivityThread", false, cl)
                        .getMethod("currentApplication"),
                getStateValue = cl.loadClass(STATE).getMethod("getValue"),
                setStateValue = cl.loadClass(MUTABLE_STATE).getMethod("setValue", Any::class.java),
                recomposeState =
                    checkNotNull(
                        newState.invoke(null, 0, null, DEFAULT_POLICY_ARG_MASK, null),
                    ),
            )
        }
    }
}

private fun XposedModule.installEmptyShade(cl: ClassLoader): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) {
        Logger.debug { "install skipped tweak=$TWEAK reason=sdk sdk=${Build.VERSION.SDK_INT}" }
        return false
    }
    val b =
        runCatching { ShadeBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${b.content.signature()}")
    bindings = b
    hook(b.content).intercept { chain ->
        runCatching { b.readRecomposeState() }
        if (!switch.enabled) return@intercept chain.proceed()
        val args = chain.args.toTypedArray<Any?>()
        val rewritten =
            runCatching { b.rewriteCaughtUp(args) }.getOrElse {
                Logger.warn("rewrite failed tweak=$TWEAK reason=${it.reason()}")
                false
            }
        if (rewritten) chain.proceed(args) else chain.proceed()
    }
    return true
}
