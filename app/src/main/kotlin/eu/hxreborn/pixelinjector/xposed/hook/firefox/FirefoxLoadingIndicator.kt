package eu.hxreborn.pixelinjector.xposed.hook.firefox

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.detail
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.optionalField
import eu.hxreborn.pixelinjector.util.orNullIfMissing
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.hook.DexKit
import io.github.libxposed.api.XposedModule
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.roundToInt

private const val TWEAK = "FirefoxLoadingIndicator"
private const val SWIPE_REFRESH_PACKAGE = "androidx.swiperefreshlayout.widget"
private const val SWIPE_REFRESH_LAYOUT = "$SWIPE_REFRESH_PACKAGE.SwipeRefreshLayout"
private const val PROGRESS_DRAWABLE = "$SWIPE_REFRESH_PACKAGE.CircularProgressDrawable"
private const val PROGRESS_RING = "$PROGRESS_DRAWABLE\$Ring"
private const val CIRCLE_IMAGE_VIEW = "$SWIPE_REFRESH_PACKAGE.CircleImageView"
private const val MAX_PROGRESS_TRIM = 0.8f
private const val CONTAINER_SIZE_DP = 48f

private val trackedLayouts = WeakHashMap<ViewGroup, StockCircle?>()

@Volatile private var current: RefreshBindings? = null

private val switch = Switch(Prefs.FIREFOX_LOADING_INDICATOR) { current?.toggleTracked(it) }

internal val firefoxLoadingIndicator =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.FIREFOX),
        prefs = listOf(switch),
        saveState = {
            synchronized(
                trackedLayouts,
            ) { WeakHashMap(trackedLayouts.mapValues { it.value?.toSaved() }) }
        },
        restoreState = ::restoreLayouts,
        install = XposedModule::installFirefoxLoadingIndicator,
    )

private class StockCircle(
    val circleDiameter: Int,
    val originalOffsetTop: Int,
    val currentTargetOffsetTop: Int,
    val background: Drawable?,
    val outline: ViewOutlineProvider?,
    val image: Drawable?,
) {
    fun toSaved(): Array<Any?> =
        arrayOf(
            circleDiameter,
            originalOffsetTop,
            currentTargetOffsetTop,
            background,
            outline,
            image,
        )

    companion object {
        fun fromSaved(saved: Array<*>) =
            StockCircle(
                circleDiameter = saved[0] as Int,
                originalOffsetTop = saved[1] as Int,
                currentTargetOffsetTop = saved[2] as Int,
                background = saved[3] as Drawable?,
                outline = saved[4] as ViewOutlineProvider?,
                image = saved[5] as Drawable?,
            )
    }
}

private fun restoreLayouts(saved: Any?) {
    val carried = saved as? Map<*, *> ?: return
    synchronized(trackedLayouts) {
        carried.forEach { (layout, stock) ->
            if (layout is ViewGroup) {
                trackedLayouts[layout] =
                    (stock as? Array<*>)?.let(StockCircle::fromSaved)
            }
        }
    }
    Logger.info("carried tweak=$TWEAK layouts=${carried.size}")
}

private object OvalOutline : ViewOutlineProvider() {
    override fun getOutline(
        view: View,
        outline: Outline,
    ) = outline.setOval(0, 0, view.width, view.height)
}

private class RefreshBindings(
    val constructor: Constructor<*>,
    val circleView: Field,
    val progress: Field,
    val circleDiameter: Field,
    val originalOffsetTop: Field,
    val currentTargetOffsetTop: Field,
    val ring: Field,
    val endTrim: Field,
    val circleSetBackgroundColor: Method?,
    val circleBackgroundColor: Field?,
) {
    fun track(layout: ViewGroup) {
        synchronized(trackedLayouts) { trackedLayouts[layout] = null }
    }

    fun applyTracked(layout: ViewGroup): Boolean {
        if (synchronized(trackedLayouts) { trackedLayouts[layout] } != null) return false
        val stock = apply(layout)
        synchronized(trackedLayouts) { trackedLayouts[layout] = stock }
        return true
    }

    fun revertTracked(layout: ViewGroup): Boolean {
        val stock = synchronized(trackedLayouts) { trackedLayouts[layout] } ?: return false
        revert(layout, stock)
        synchronized(trackedLayouts) { trackedLayouts[layout] = null }
        return true
    }

    fun toggleTracked(enabled: Boolean) {
        val tracked = synchronized(trackedLayouts) { trackedLayouts.keys.toList() }
        if (tracked.isEmpty()) return
        val action = if (enabled) "apply" else "revert"
        Handler(Looper.getMainLooper()).post {
            val changed =
                tracked.count { layout ->
                    runCatching { if (enabled) applyTracked(layout) else revertTracked(layout) }
                        .onFailure {
                            Logger.error("$action failed tweak=$TWEAK reason=${it.detail()}", it)
                        }.getOrDefault(false)
                }
            Logger.info("${if (enabled) "applied" else "reverted"} tweak=$TWEAK layouts=$changed")
        }
    }

    fun keepStockCircleColor(
        circle: View,
        color: Int,
    ) {
        val layout = circle.parent as? ViewGroup ?: return
        val stock = synchronized(trackedLayouts) { trackedLayouts[layout] } ?: return
        (stock.background as? ShapeDrawable)?.paint?.color = color
        circleBackgroundColor?.setInt(circle, color)
    }

    private fun apply(layout: ViewGroup): StockCircle {
        val view = circleView.get(layout) as ImageView
        val captured =
            StockCircle(
                circleDiameter = circleDiameter.getInt(layout),
                originalOffsetTop = originalOffsetTop.getInt(layout),
                currentTargetOffsetTop = currentTargetOffsetTop.getInt(layout),
                background = view.background,
                outline = view.outlineProvider,
                image = view.drawable,
            )
        val diameter = (CONTAINER_SIZE_DP * layout.resources.displayMetrics.density).roundToInt()
        circleDiameter.setInt(layout, diameter)
        originalOffsetTop.setInt(layout, -diameter)
        currentTargetOffsetTop.setInt(layout, -diameter)
        val stock = progress.get(layout) as Drawable
        val stockRing = ring.get(stock)
        val pullFraction = { endTrim.getFloat(stockRing) / MAX_PROGRESS_TRIM }
        val indicator = LoadingIndicatorDrawable(stock, layout.context, pullFraction)
        view.background = ColorDrawable(Color.TRANSPARENT)
        view.outlineProvider = OvalOutline
        view.setImageDrawable(indicator)
        stock.callback = indicator
        layout.requestLayout()
        return captured
    }

    private fun revert(
        layout: ViewGroup,
        stock: StockCircle,
    ) {
        circleDiameter.setInt(layout, stock.circleDiameter)
        originalOffsetTop.setInt(layout, stock.originalOffsetTop)
        currentTargetOffsetTop.setInt(layout, stock.currentTargetOffsetTop)
        val view = circleView.get(layout) as ImageView
        view.background = stock.background
        view.outlineProvider = stock.outline
        view.setImageDrawable(stock.image)
        layout.requestLayout()
    }

    companion object {
        fun resolve(cl: ClassLoader): RefreshBindings {
            val layout = cl.loadClass(SWIPE_REFRESH_LAYOUT)
            return orNullIfMissing { resolveNamed(cl, layout) } ?: resolveFromDex(cl, layout)
        }

        private fun resolveNamed(
            cl: ClassLoader,
            layout: Class<*>,
        ): RefreshBindings {
            val circle = orNullIfMissing { cl.loadClass(CIRCLE_IMAGE_VIEW) }
            return RefreshBindings(
                constructor =
                    layout.getDeclaredConstructor(
                        Context::class.java,
                        AttributeSet::class.java,
                    ),
                circleView = layout.requiredField("mCircleView"),
                progress = layout.requiredField("mProgress"),
                circleDiameter = layout.requiredField("mCircleDiameter"),
                originalOffsetTop = layout.requiredField("mOriginalOffsetTop"),
                currentTargetOffsetTop = layout.requiredField("mCurrentTargetOffsetTop"),
                ring = cl.loadClass(PROGRESS_DRAWABLE).requiredField("mRing"),
                endTrim = cl.loadClass(PROGRESS_RING).requiredField("mEndTrim"),
                circleSetBackgroundColor =
                    circle?.let {
                        orNullIfMissing {
                            it.requiredMethod("setBackgroundColor", Int::class.javaPrimitiveType)
                        }
                    },
                circleBackgroundColor = circle?.optionalField("mBackgroundColor"),
            )
        }

        private fun resolveFromDex(
            cl: ClassLoader,
            layout: Class<*>,
        ): RefreshBindings =
            DexKit.query(cl) { bridge ->
                val constructor =
                    layout.getDeclaredConstructor(
                        Context::class.java,
                        AttributeSet::class.java,
                    )
                val circleView = layout.fieldOfType(ImageView::class.java)
                val progress = layout.fieldOfType(Drawable::class.java)
                val progressClass = progress.type
                val animation =
                    bridge
                        .findMethod {
                            matcher {
                                declaredClass(progressClass)
                                returnType("void")
                                paramCount(3)
                                usingNumbers(MAX_PROGRESS_TRIM)
                            }
                        }.singleOrNull()
                        ?: throw NoSuchMethodException(
                            "CircularProgressDrawable applyTransformation",
                        )
                val ringClass = animation.paramTypes[1].getInstance(cl)
                val ring = progressClass.fieldOfType(ringClass)
                val draw =
                    bridge.getMethodData(
                        progressClass.requiredMethod("draw", android.graphics.Canvas::class.java),
                    )
                        ?: throw NoSuchMethodException("CircularProgressDrawable.draw")
                val drawnFields = draw.fields(ringClass, "float", reads = true)
                val trimAndRotation =
                    animation.fields(ringClass, "float", reads = false).filter { it in drawnFields }
                if (trimAndRotation.size != 3) {
                    throw NoSuchFieldException(
                        "CircularProgressDrawable ring start trim, end trim and rotation",
                    )
                }
                val (_, endTrim) = trimAndRotation

                fun layoutInt(
                    method: String,
                    reads: Boolean,
                    vararg parameters: Class<*>,
                ) = bridge
                    .getMethodData(layout.requiredMethod(method, *parameters))
                    ?.fields(layout, "int", reads)
                    ?.singleOrNull()
                    ?.getFieldInstance(cl)
                    ?.apply { isAccessible = true }
                    ?: throw NoSuchFieldException("SwipeRefreshLayout.$method int field")

                val colorSetter =
                    orNullIfMissing {
                        circleView.type.requiredMethod(
                            "setBackgroundColor",
                            Int::class.javaPrimitiveType,
                        )
                    }
                val backgroundColor =
                    colorSetter
                        ?.let { bridge.getMethodData(it) }
                        ?.fields(circleView.type, "int", reads = false)
                        ?.singleOrNull()
                        ?.getFieldInstance(cl)
                        ?.apply { isAccessible = true }
                return@query RefreshBindings(
                    constructor = constructor,
                    circleView = circleView,
                    progress = progress,
                    circleDiameter = layoutInt("getProgressCircleDiameter", true),
                    originalOffsetTop = layoutInt("getProgressViewStartOffset", true),
                    currentTargetOffsetTop =
                        layoutInt(
                            "setTargetOffsetTopAndBottom",
                            false,
                            Int::class.javaPrimitiveType!!,
                        ),
                    ring = ring,
                    endTrim = endTrim.getFieldInstance(cl).apply { isAccessible = true },
                    circleSetBackgroundColor = colorSetter,
                    circleBackgroundColor = backgroundColor,
                )
            }

        private fun Class<*>.fieldOfType(type: Class<*>): Field =
            declaredFields
                .singleOrNull { type.isAssignableFrom(it.type) }
                ?.apply { isAccessible = true }
                ?: throw NoSuchFieldException("$simpleName field of type ${type.simpleName}")

        private fun MethodData.fields(
            owner: Class<*>,
            type: String,
            reads: Boolean,
        ) = usingFields
            .filter {
                it.field.declaredClassName == owner.name && it.field.typeName == type &&
                    it.usingType.isRead() == reads
            }.map { it.field }
            .distinct()
    }
}

private fun XposedModule.installFirefoxLoadingIndicator(cl: ClassLoader): Boolean {
    val bindings =
        resolving { RefreshBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=SwipeRefreshLayout#<init>,${bindings.endTrim.signature()}",
    )
    hook(bindings.constructor).intercept { chain ->
        val result = chain.proceed()
        val layout = chain.thisObject as ViewGroup
        bindings.track(layout)
        if (switch.enabled) {
            runCatching { bindings.applyTracked(layout) }
                .onSuccess { Logger.info("applied tweak=$TWEAK") }
                .onFailure { Logger.error("apply failed tweak=$TWEAK reason=${it.detail()}", it) }
        }
        result
    }
    bindings.circleSetBackgroundColor?.let { setter ->
        hook(setter).intercept { chain ->
            val result = chain.proceed()
            bindings.keepStockCircleColor(chain.thisObject as View, chain.getArg(0) as Int)
            result
        }
    }
    current = bindings
    return true
}
