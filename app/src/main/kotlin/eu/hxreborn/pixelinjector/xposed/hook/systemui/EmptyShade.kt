package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import eu.hxreborn.pixelinjector.ModuleConstants.SYSTEMUI_PACKAGE
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.icons.ShadeIcons
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.optionalField
import eu.hxreborn.pixelinjector.util.orNullIfMissing
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethods
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.Value
import eu.hxreborn.pixelinjector.xposed.hook.RecomposeTrigger
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TWEAK = "EmptyShade"
private const val CONTENT =
    "com.android.systemui.notifications.stack.emptyshade.ui.composable.EmptyShadeContentImplKt"

// View shade of stable Android 17 (CD1A.261005.003 SystemUIGoogle), not yet in AOSP. Older binder:
// https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/statusbar/notification/emptyshade/ui/viewbinder/EmptyShadeViewBinder.kt
private const val VIEW_BINDER =
    "com.android.systemui.statusbar.notification.emptyshade.ui.viewbinder.EmptyShadeViewBinder"
private const val VIEW_COLLECTOR_FIELD = "\$view"
private const val MAX_ANONYMOUS_CLASS_INDEX = 4
private const val ICON_MESSAGE_MODEL =
    "com.android.systemui.statusbar.notification.emptyshade.ui.shared.model.IconMessageModel"
private const val ICON_VIEW =
    "com.android.systemui.statusbar.notification.emptyshade.ui.view.EmptyShadeIconView"
private const val RESOURCE_ICON = "com.android.systemui.common.shared.model.Icon\$Resource"
private const val LOADED_ICON = "com.android.systemui.common.shared.model.Icon\$Loaded"
private const val STOCK_ICON = "ic_trophy"
private const val DRAWABLE_TYPE = "drawable"
private const val TEXT_ARG = 0
private const val ICON_ARG = 1
private const val CARRIED_VIEW = "view"
private const val CARRIED_MODEL = "model"

@Volatile private var current: ShadeBindings? = null

@Volatile private var currentView: ViewBindings? = null

private val requestRecompose: (Any?) -> Unit = {
    runCatching { current?.recompose?.bump() }
        .onFailure { Logger.warn("recompose failed tweak=$TWEAK reason=${it.reason()}") }
    currentView?.reapply()
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
        saveState = { current?.recompose?.state ?: currentView?.carry() },
        restoreState = { saved ->
            current?.recompose?.adopt(saved)
            currentView?.restoreLive(saved)
        },
        install = XposedModule::installEmptyShade,
    )

private class StockIcon(
    private val resourceIconClass: Class<*>,
    private val resourceIconId: Field,
    private val currentApplication: Method,
) {
    @Volatile var id = 0
        private set

    fun matches(icon: Any?): Boolean {
        if (!resourceIconClass.isInstance(icon)) return false
        if (id == 0) id = lookUpId()
        return id != 0 && resourceIconId.getInt(icon) == id
    }

    private fun lookUpId(): Int =
        runCatching {
            (currentApplication.invoke(null) as? Context)
                ?.resources
                ?.getIdentifier(STOCK_ICON, DRAWABLE_TYPE, SYSTEMUI_PACKAGE)
        }.getOrNull() ?: 0

    companion object {
        fun resolve(cl: ClassLoader): StockIcon {
            val resourceIconClass = cl.loadClass(RESOURCE_ICON)
            return StockIcon(
                resourceIconClass = resourceIconClass,
                resourceIconId = resourceIconClass.requiredField("resId"),
                currentApplication =
                    Class
                        .forName("android.app.ActivityThread", false, cl)
                        .getMethod("currentApplication"),
            )
        }
    }
}

private class ViewBindings(
    val collectorViews: Map<Method, Field>,
    private val modelType: Class<*>,
    private val modelMessage: Field,
    private val modelIcon: Field,
    private val textView: Field,
    private val iconView: Field,
    private val stockIcon: StockIcon,
) {
    @Volatile private var live: Pair<WeakReference<View>, Any>? = null

    fun onEmit(
        viewField: Field,
        collector: Any,
        emitted: Any?,
    ) {
        val model = emitted?.takeIf(modelType::isInstance) ?: return
        val view = viewField.get(collector) as? View ?: return
        live = WeakReference(view) to model
        if (switch.enabled) apply(view, model)
    }

    fun carry(): HashMap<String, Any>? {
        val (ref, model) = live ?: return null
        val view = ref.get() ?: return null
        return hashMapOf(CARRIED_VIEW to view, CARRIED_MODEL to model)
    }

    fun restoreLive(saved: Any?) {
        val carried = saved as? Map<*, *> ?: return
        val view = carried[CARRIED_VIEW] as? View ?: return
        val model = carried[CARRIED_MODEL] ?: return
        live = WeakReference(view) to model
        reapply()
    }

    fun reapply() {
        val (ref, model) = live ?: return
        val view = ref.get() ?: return
        view.post {
            runCatching { apply(view, model) }
                .onFailure { Logger.warn("reapply failed tweak=$TWEAK reason=${it.reason()}") }
        }
    }

    private fun apply(
        view: View,
        model: Any,
    ) {
        if (!stockIcon.matches(modelIcon.get(model))) return
        val enabled = switch.enabled
        (textView.get(view) as TextView).text =
            customText.value.takeIf { enabled && it.isNotEmpty() }
                ?: modelMessage.get(model) as String
        (iconView.get(view) as ImageView).setImageDrawable(
            customIcon.value.takeIf { enabled } ?: view.context.getDrawable(stockIcon.id),
        )
    }

    companion object {
        fun resolve(cl: ClassLoader): ViewBindings {
            val model = cl.loadClass(ICON_MESSAGE_MODEL)
            val view = cl.loadClass(ICON_VIEW)
            return ViewBindings(
                collectorViews = findCollectorViews(cl),
                modelType = model,
                modelMessage = model.requiredField("message"),
                modelIcon = model.requiredField("icon"),
                textView = view.requiredField("textView"),
                iconView = view.requiredField("iconView"),
                stockIcon = StockIcon.resolve(cl),
            )
        }
    }
}

private fun findCollectorViews(cl: ClassLoader): Map<Method, Field> {
    val indices = 1..MAX_ANONYMOUS_CLASS_INDEX
    return indices
        .flatMap { a -> indices.flatMap { b -> indices.map { c -> "\$bind\$$a\$$b\$$c" } } }
        .mapNotNull { orNullIfMissing { cl.loadClass(VIEW_BINDER + it) } }
        .mapNotNull { type ->
            val viewField = type.optionalField(VIEW_COLLECTOR_FIELD) ?: return@mapNotNull null
            type.declaredMethods
                .singleOrNull { it.name == "emit" }
                ?.apply { isAccessible = true }
                ?.let { it to viewField }
        }.toMap()
        .ifEmpty { throw NoSuchMethodException("$VIEW_BINDER\$bind.emit") }
}

private class ShadeBindings(
    val content: Method,
    private val stockIcon: StockIcon,
    private val newLoadedIcon: Constructor<*>,
    val recompose: RecomposeTrigger,
) {
    @Volatile private var lastLoaded: Pair<Drawable, Any>? = null

    fun rewriteCaughtUp(args: Array<Any?>): Boolean {
        if (!stockIcon.matches(args[ICON_ARG])) return false
        customText.value.takeIf(String::isNotEmpty)?.let { args[TEXT_ARG] = it }
        customIcon.value?.let { args[ICON_ARG] = loadedIcon(it) }
        return true
    }

    private fun loadedIcon(drawable: Drawable): Any {
        lastLoaded?.takeIf { it.first === drawable }?.let { return it.second }
        val icon = newLoadedIcon.newInstance(drawable)
        Logger.info("applied tweak=$TWEAK icon=custom")
        lastLoaded = drawable to icon
        return icon
    }

    companion object {
        fun resolve(cl: ClassLoader): ShadeBindings =
            ShadeBindings(
                content =
                    cl
                        .loadClass(CONTENT)
                        .requiredMethods("EmptyShadeContent")
                        .single { it.parameterTypes.firstOrNull() == String::class.java },
                stockIcon = StockIcon.resolve(cl),
                newLoadedIcon = cl.loadClass(LOADED_ICON).getConstructor(Drawable::class.java),
                recompose = RecomposeTrigger.resolve(cl),
            )
    }
}

private fun XposedModule.installEmptyShade(cl: ClassLoader): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) {
        Logger.debug { "install skipped tweak=$TWEAK reason=sdk sdk=${Build.VERSION.SDK_INT}" }
        return false
    }
    val bindings =
        resolving { ShadeBindings.resolve(cl) }.getOrElse { composeMiss ->
            return installViewPath(cl, composeMiss)
        }
    Logger.info("resolved tweak=$TWEAK members=${bindings.content.signature()}")
    current = bindings
    hook(bindings.content).intercept { chain ->
        runCatching { bindings.recompose.read() }
        if (!switch.enabled) return@intercept chain.proceed()
        val args = chain.args.toTypedArray<Any?>()
        val rewritten =
            runCatching { bindings.rewriteCaughtUp(args) }.getOrElse {
                Logger.warn("rewrite failed tweak=$TWEAK reason=${it.reason()}")
                false
            }
        if (rewritten) chain.proceed(args) else chain.proceed()
    }
    return true
}

private fun XposedModule.installViewPath(
    cl: ClassLoader,
    composeMiss: Throwable,
): Boolean {
    val bindings =
        resolving { ViewBindings.resolve(cl) }.getOrElse { viewMiss ->
            listOf(composeMiss, viewMiss).forEach {
                Logger.error(
                    "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
                )
            }
            return false
        }
    val members = bindings.collectorViews.keys.joinToString(",") { it.signature() }
    Logger.info("resolved tweak=$TWEAK members=$members")
    currentView = bindings
    for ((emit, viewField) in bindings.collectorViews) {
        hook(emit).intercept { chain ->
            val result = chain.proceed()
            runCatching { bindings.onEmit(viewField, chain.thisObject, chain.args[0]) }
                .onFailure { Logger.warn("rewrite failed tweak=$TWEAK reason=${it.reason()}") }
            result
        }
    }
    return true
}
