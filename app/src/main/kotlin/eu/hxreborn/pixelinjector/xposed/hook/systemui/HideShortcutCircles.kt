package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import eu.hxreborn.pixelinjector.ModuleConstants.SYSTEMUI_PACKAGE
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.orNullIfMissing
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.hook.RecomposeTrigger
import eu.hxreborn.pixelinjector.xposed.hook.proceedReplacing
import eu.hxreborn.pixelinjector.xposed.module
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val TWEAK = "HideShortcutCircles"
private const val BINDER = "com.android.systemui.keyguard.ui.binder"
private const val VIEW_BINDER = "$BINDER.KeyguardQuickAffordanceViewBinder"
private const val LEGACY_BINDER = "$BINDER.KeyguardBottomAreaViewBinder"
private const val SHORTCUT_PROVIDER =
    "com.android.systemui.keyguard.ui.composable.elements.ShortcutElementProvider"
private const val SHORTCUT_LAMBDA = "$SHORTCUT_PROVIDER\$\$ExternalSyntheticLambda"
private const val MAX_LAMBDA_INDEX = 15
private const val VIEW_MODEL =
    "com.android.systemui.keyguard.ui.viewmodel.KeyguardQuickAffordanceViewModel"
private const val FUNCTION3 = "kotlin.jvm.functions.Function3"
private const val PAINTER = "androidx.compose.ui.graphics.painter.Painter"
private const val BACKGROUND_KT = "androidx.compose.foundation.BackgroundKt"
private const val ICON_KT = "androidx.compose.material3.IconKt"
private const val STATE = "androidx.compose.runtime.State"
private const val BIND = "bind"
private val BUTTON_IDS = listOf("start_button", "end_button")
private const val ICON_COLOR_ATTR = "wallpaperTextColorAccent"
private const val BACKGROUND_PARAMS = 3
private const val BACKGROUND_COLOR_ARG = 1
private const val ICON_TINT_ARG = 3
private const val TRANSPARENT = 0L
private const val COMPOSE_ARGB_SHIFT = 32
private const val CARRIED_BACKGROUNDS = "backgrounds"
private const val CARRIED_RECOMPOSE = "recompose"

@Volatile private var views: ViewShortcuts? = null

@Volatile private var compose: ComposeShortcuts? = null

private val composingIconColor = ThreadLocal<Long>()

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

private val switch =
    Switch(Prefs.HIDE_SHORTCUT_CIRCLES) { enabled ->
        views?.updateAll()
        compose?.let { shortcuts ->
            if (enabled) shortcuts.hookOnce()
            try {
                shortcuts.recompose.bump()
            } catch (e: Exception) {
                Logger.warn("recompose failed tweak=$TWEAK reason=${e.reason()}")
            } catch (e: LinkageError) {
                Logger.warn("recompose failed tweak=$TWEAK reason=${e.reason()}")
            }
            Logger.info("applied tweak=$TWEAK on=$enabled path=compose")
        }
    }

internal val hideShortcutCircles =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(switch),
        saveState = ::carry,
        restoreState = ::restore,
        install = XposedModule::installHideShortcutCircles,
    )

private object IconColor {
    @Volatile private var attr: Int? = null

    @Volatile private var reported = false

    fun of(context: Context): Int? {
        val id =
            attr ?: context.resources
                .getIdentifier(ICON_COLOR_ATTR, "attr", SYSTEMUI_PACKAGE)
                .also { attr = it }
        val color =
            id.takeIf { it != 0 }?.let { resolved ->
                context.obtainStyledAttributes(intArrayOf(resolved)).use {
                    if (it.hasValue(0)) it.getColor(0, 0) else null
                }
            }
        if (color == null && !reported) {
            reported = true
            Logger.warn("icon color missing tweak=$TWEAK attr=$ICON_COLOR_ATTR id=$id")
        }
        return color
    }
}

private class HiddenCircle(
    stock: Drawable,
    private val button: ImageView,
) : DrawableWrapper(stock) {
    private var iconColor: Int? = null

    val hidden: Boolean get() = iconColor != null

    fun update() {
        val color =
            if (switch.enabled && !button.isActivated && !button.isSelected) {
                IconColor.of(button.context)
            } else {
                null
            }
        if (color == iconColor) return
        iconColor = color
        button.colorFilter = color?.let { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
        invalidateSelf()
    }

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean =
        super.onStateChange(state).also { update() }

    override fun setTintList(tint: ColorStateList?) {
        super.setTintList(tint)
        update()
    }

    override fun draw(canvas: Canvas) {
        if (!hidden) super.draw(canvas)
    }
}

private class ViewShortcuts(
    val binds: List<Method>,
    val legacyBinds: List<Method>,
) {
    private val buttons: MutableSet<ImageView> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    fun register(button: ImageView) {
        buttons += button
        button.post { attach(button) }
    }

    fun registerIn(root: View) {
        val ids = BUTTON_IDS.map { root.resources.getIdentifier(it, "id", SYSTEMUI_PACKAGE) }
        buttonsIn(root, ids).forEach(::register)
    }

    private fun buttonsIn(
        view: View,
        ids: List<Int>,
    ): List<ImageView> {
        if (view is ImageView && view.id in ids) return listOf(view)
        val group = view as? ViewGroup ?: return emptyList()
        return (0 until group.childCount).flatMap { buttonsIn(group.getChildAt(it), ids) }
    }

    fun updateAll() {
        mainHandler.post {
            val tracked = snapshot().onEach(::attach)
            val hidden = tracked.count { (it.background as? HiddenCircle)?.hidden == true }
            Logger.info("applied tweak=$TWEAK on=${switch.enabled} path=view hidden=$hidden")
        }
    }

    private fun snapshot(): List<ImageView> = synchronized(buttons) { buttons.toList() }

    private fun attach(button: ImageView) {
        val background = button.background ?: return
        val circle =
            background as? HiddenCircle
                ?: if (switch.enabled) {
                    HiddenCircle(background, button).also { button.background = it }
                } else {
                    return
                }
        circle.update()
    }

    fun carry(): HashMap<View, Drawable> =
        snapshot()
            .mapNotNull { button ->
                (button.background as? HiddenCircle)?.drawable?.let { button to it }
            }.toMap(HashMap())

    fun restore(carried: Map<*, *>) {
        for ((button, stock) in carried) {
            if (button !is ImageView || stock !is Drawable) continue
            button.post {
                if ((button.background as? DrawableWrapper)?.drawable === stock) {
                    button.background = stock
                }
                button.colorFilter = null
                register(button)
            }
        }
    }

    fun registerAttached() {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val roots =
            global.requiredField("mViews").get(global.getMethod("getInstance").invoke(null))
                as List<*>
        roots.filterIsInstance<View>().forEach(::registerIn)
        Logger.info("attached tweak=$TWEAK buttons=${buttons.size}")
    }

    companion object {
        fun resolve(cl: ClassLoader): ViewShortcuts {
            val binds = bindMethods(cl, VIEW_BINDER, ImageView::class.java)
            // LineageOS 21 and 22 SystemUI with keyguard_bottom_area_refactor off
            val legacyBinds = bindMethods(cl, LEGACY_BINDER, ViewGroup::class.java)
            if (binds.isEmpty() && legacyBinds.isEmpty()) {
                throw NoSuchMethodException("KeyguardQuickAffordanceViewBinder.$BIND")
            }
            return ViewShortcuts(binds, legacyBinds)
        }

        private fun bindMethods(
            cl: ClassLoader,
            binder: String,
            firstParam: Class<*>,
        ): List<Method> =
            orNullIfMissing { cl.loadClass(binder) }
                ?.declaredMethods
                ?.filter {
                    it.name == BIND &&
                        it.parameterTypes.firstOrNull()?.let(firstParam::isAssignableFrom) == true
                }.orEmpty()
    }
}

private class ShortcutModel(
    private val stateFields: List<Field>,
    private val getStateValue: Method,
    private val type: Class<*>,
    private val isActivated: Field,
    private val isSelected: Field,
) {
    @Volatile private var modelField: Field? = null

    fun isIdle(lambda: Any): Boolean {
        val model = modelOf(lambda) ?: return false
        return !isActivated.getBoolean(model) && !isSelected.getBoolean(model)
    }

    private fun modelOf(lambda: Any): Any? {
        modelField?.let { return getStateValue.invoke(it.get(lambda)) }
        for (field in stateFields) {
            val value = field.get(lambda)?.let { getStateValue.invoke(it) }
            if (type.isInstance(value)) {
                modelField = field
                return value
            }
        }
        return null
    }

    companion object {
        fun resolve(
            cl: ClassLoader,
            lambdaFields: List<Field>,
        ): ShortcutModel {
            val state = cl.loadClass(STATE)
            val type = cl.loadClass(VIEW_MODEL)
            return ShortcutModel(
                stateFields = lambdaFields.filter { state.isAssignableFrom(it.type) },
                getStateValue = state.getMethod("getValue"),
                type = type,
                isActivated = type.requiredField("isActivated"),
                isSelected = type.requiredField("isSelected"),
            )
        }
    }
}

private class ComposeShortcuts(
    val content: Method,
    private val model: ShortcutModel,
    private val provider: Field,
    private val providerContext: Field,
    private val background: Method,
    private val icon: Method,
    val recompose: RecomposeTrigger,
) {
    private val hooked = AtomicBoolean(false)

    fun iconColorFor(lambda: Any): Long? {
        recompose.read()
        if (!switch.enabled || !model.isIdle(lambda)) return null
        val context = providerContext.get(provider.get(lambda)) as Context
        return IconColor.of(context)?.toLong()?.shl(COMPOSE_ARGB_SHIFT)
    }

    fun hookOnce() {
        if (!hooked.compareAndSet(false, true)) return
        module.hook(background).intercept { chain ->
            if (composingIconColor.get() == null) return@intercept chain.proceed()
            chain.proceedReplacing(BACKGROUND_COLOR_ARG, TRANSPARENT)
        }
        module.hook(icon).intercept { chain ->
            val color = composingIconColor.get() ?: return@intercept chain.proceed()
            chain.proceedReplacing(ICON_TINT_ARG, color)
        }
    }

    companion object {
        fun resolve(cl: ClassLoader): ComposeShortcuts {
            val providerClass = cl.loadClass(SHORTCUT_PROVIDER)
            val function3 = cl.loadClass(FUNCTION3)
            val lambda =
                (0..MAX_LAMBDA_INDEX)
                    .mapNotNull { orNullIfMissing { cl.loadClass("$SHORTCUT_LAMBDA$it") } }
                    .singleOrNull { type ->
                        function3.isAssignableFrom(type) &&
                            type.declaredFields.any { it.type.name == PAINTER }
                    } ?: throw NoSuchMethodException("ShortcutElementProvider.KeyguardShortcut")
            val lambdaFields = lambda.declaredFields.map { it.accessible() }
            val longType = Long::class.javaPrimitiveType
            return ComposeShortcuts(
                content =
                    lambda.requiredMethod(
                        "invoke",
                        Any::class.java,
                        Any::class.java,
                        Any::class.java,
                    ),
                model = ShortcutModel.resolve(cl, lambdaFields),
                provider = lambdaFields.single { it.type == providerClass },
                providerContext =
                    providerClass.declaredFields
                        .single { Context::class.java.isAssignableFrom(it.type) }
                        .accessible(),
                background =
                    cl.loadClass(BACKGROUND_KT).declaredMethods.single {
                        it.name.startsWith("background") &&
                            it.parameterTypes.size == BACKGROUND_PARAMS &&
                            it.parameterTypes[BACKGROUND_COLOR_ARG] == longType
                    },
                icon =
                    cl.loadClass(ICON_KT).declaredMethods.single {
                        it.name.startsWith("Icon") &&
                            it.parameterTypes.firstOrNull()?.name == PAINTER &&
                            it.parameterTypes.getOrNull(ICON_TINT_ARG) == longType
                    },
                recompose = RecomposeTrigger.resolve(cl),
            )
        }
    }
}

private fun Field.accessible(): Field = apply { isAccessible = true }

private fun carry(): HashMap<String, Any> {
    val carried = HashMap<String, Any>()
    views?.carry()?.takeIf { it.isNotEmpty() }?.let { carried[CARRIED_BACKGROUNDS] = it }
    compose?.recompose?.state?.let { carried[CARRIED_RECOMPOSE] = it }
    return carried
}

private fun restore(saved: Any?) {
    val carried = saved as? Map<*, *>
    views?.let { shortcuts ->
        (carried?.get(CARRIED_BACKGROUNDS) as? Map<*, *>)?.let(shortcuts::restore)
        mainHandler.post {
            try {
                shortcuts.registerAttached()
            } catch (e: Exception) {
                Logger.error("attached failed tweak=$TWEAK reason=${e.reason()}", e)
            } catch (e: LinkageError) {
                Logger.error("attached failed tweak=$TWEAK reason=${e.reason()}", e)
            }
        }
    }
    compose?.recompose?.adopt(carried?.get(CARRIED_RECOMPOSE))
}

private fun XposedModule.installHideShortcutCircles(cl: ClassLoader): Boolean {
    val viewPath = resolving { ViewShortcuts.resolve(cl) }
    // SystemUIGoogle CP3A.261005.005 and CP41.260831.019
    val composePath = resolving { ComposeShortcuts.resolve(cl) }
    val viewBindings = viewPath.getOrNull()
    val composeBindings = composePath.getOrNull()
    if (viewBindings == null && composeBindings == null) {
        listOfNotNull(viewPath.exceptionOrNull(), composePath.exceptionOrNull()).forEach {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
        }
        return false
    }
    val members =
        (
            viewBindings?.binds.orEmpty() + viewBindings?.legacyBinds.orEmpty() +
                listOfNotNull(composeBindings?.content)
        ).joinToString(",") { it.signature() }
    Logger.info("resolved tweak=$TWEAK members=$members")
    viewBindings?.let { installViewPath(it) }
    composeBindings?.let { installComposePath(it) }
    return true
}

private fun XposedModule.installViewPath(bindings: ViewShortcuts) {
    views = bindings
    for (bind in bindings.binds) {
        hook(bind).intercept { chain ->
            val result = chain.proceed()
            (chain.args.firstOrNull() as? ImageView)?.let(bindings::register)
            result
        }
    }
    for (bind in bindings.legacyBinds) {
        hook(bind).intercept { chain ->
            val result = chain.proceed()
            (chain.args.firstOrNull() as? View)?.let { root ->
                root.post { bindings.registerIn(root) }
            }
            result
        }
    }
}

private fun XposedModule.installComposePath(bindings: ComposeShortcuts) {
    compose = bindings
    hook(bindings.content).intercept { chain ->
        val color =
            try {
                bindings.iconColorFor(chain.thisObject)
            } catch (e: Exception) {
                Logger.warn("compose failed tweak=$TWEAK reason=${e.reason()}")
                null
            } catch (e: LinkageError) {
                Logger.warn("compose failed tweak=$TWEAK reason=${e.reason()}")
                null
            } ?: return@intercept chain.proceed()
        composingIconColor.set(color)
        try {
            chain.proceed()
        } finally {
            composingIconColor.remove()
        }
    }
    if (switch.enabled) bindings.hookOnce()
}
