package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.optionalMethod
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
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TWEAK = "PillShot"
private const val CONTROLLER = "com.android.systemui.screenshot.ScreenshotController"
private const val DATA = "com.android.systemui.screenshot.ScreenshotData"
private const val CAPTURE = "com.android.systemui.screenshot.ImageCaptureImpl"
private const val SAMSUNG_RESULT = "com.android.systemui.screenshot.sep.SemScreenshotResult"
private const val TAKE_SCREENSHOT_FULLSCREEN = 1
private const val LAST_INLINE_CAPTURE_SDK = 35
private const val PORTRAIT_TOP_DP = 12
private const val LANDSCAPE_TOP_DP = 2
private const val HORIZONTAL_PADDING_DP = 12
private const val PORTRAIT_VERTICAL_PADDING_DP = 8
private const val LANDSCAPE_VERTICAL_PADDING_DP = 6
private const val CORNER_RADIUS_DP = 18
private const val TEXT_SIZE_DP = 16
private const val MIN_WIDTH_DP = 80
private val PILL_BACKGROUND_COLOR = 0xFF1C1C1E.toInt()

private val switch = Switch(Prefs.PILL_SHOT)
private val onlySelected = Switch(Prefs.PILL_SHOT_ONLY_SELECTED)
private val selectedApps = Value(Prefs.PILL_SHOT_APPS) { HashSet(it) }

private val pendingScreenshot = ThreadLocal<Pair<Context, Any>>()

internal val pillShot =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI, Target.SCREENSHOT),
        prefs = listOf(switch, onlySelected, selectedApps),
        install = XposedModule::installPillShot,
    )

private class ScreenshotBindings(
    val handleScreenshot: List<Method>,
    val captureDisplay: Method?,
    val captureResultConstructor: Constructor<*>?,
    val context: Field,
    val type: Field,
    val bitmap: Field,
    val topComponent: Field,
) {
    companion object {
        fun resolve(cl: ClassLoader): ScreenshotBindings {
            val controller = cl.loadClass(CONTROLLER)
            val data = cl.loadClass(DATA)
            val handleScreenshot = controller.requiredMethods("handleScreenshot")
            val captureResultConstructor =
                orNullIfMissing { cl.loadClass(SAMSUNG_RESULT) }
                    ?.getDeclaredConstructor(
                        Bitmap::class.java,
                        Int::class.javaPrimitiveType,
                        String::class.java,
                        String::class.java,
                    )?.apply { isAccessible = true }
            val capture =
                if (captureResultConstructor == null) {
                    resolving { cl.loadClass(CAPTURE) }.getOrNull()
                } else {
                    null
                }
            val captureDisplay = capture?.optionalMethod("captureDisplay")
            if (captureResultConstructor == null && captureDisplay == null &&
                Build.VERSION.SDK_INT <= LAST_INLINE_CAPTURE_SDK
            ) {
                throw NoSuchMethodException("ImageCaptureImpl.captureDisplay")
            }
            return ScreenshotBindings(
                handleScreenshot = handleScreenshot,
                captureDisplay = captureDisplay,
                captureResultConstructor = captureResultConstructor,
                context = controller.requiredField("context", "mContext"),
                type = data.requiredField("type", "mType"),
                bitmap = data.requiredField("bitmap", "mBitmap"),
                topComponent = data.requiredField("topComponent", "mTopComponent"),
            )
        }
    }

    fun members(): String =
        (
            handleScreenshot.map { it.signature() } +
                listOfNotNull(captureDisplay?.signature(), captureResultConstructor?.toString())
        ).joinToString(",")

    fun contextOf(controller: Any): Context? = context.get(controller) as? Context

    fun awaitingCapture(shot: Any): Boolean =
        runCatching {
            type.getInt(shot) == TAKE_SCREENSHOT_FULLSCREEN && bitmap.get(shot) == null
        }.getOrDefault(false)

    fun stamp(
        context: Context,
        shot: Any,
    ) {
        val src = bitmap.get(shot) as Bitmap? ?: return
        bitmap.set(shot, stampBitmap(context, shot, src) ?: return)
    }

    fun stampBitmap(
        context: Context,
        shot: Any,
        src: Bitmap,
    ): Bitmap? {
        val pkg = (topComponent.get(shot) as ComponentName?)?.packageName ?: return null
        if (!shouldStamp(pkg)) {
            Logger.info("stamp skipped tweak=$TWEAK pkg=$pkg onlySelected=${onlySelected.enabled}")
            return null
        }
        return drawPill(context, label(context, pkg), src)
    }

    fun stampCapturedBitmap(
        context: Context,
        shot: Any,
        captured: Bitmap,
        path: String,
    ): Bitmap =
        try {
            stampBitmap(context, shot, captured) ?: captured
        } catch (e: Exception) {
            Logger.error("stamp failed tweak=$TWEAK path=$path reason=${e.message}", e)
            captured
        } catch (e: LinkageError) {
            Logger.error("stamp failed tweak=$TWEAK path=$path reason=${e.message}", e)
            captured
        }
}

private fun XposedModule.installPillShot(cl: ClassLoader): Boolean {
    val bindings =
        resolving { ScreenshotBindings.resolve(cl) }.getOrElse {
            if (it is ClassNotFoundException) {
                Logger.debug { "skipped tweak=$TWEAK reason=no-class" }
            } else {
                Logger.error(
                    "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
                )
            }
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${bindings.members()}")
    for (method in bindings.handleScreenshot) {
        hook(method).intercept { chain ->
            if (!switch.enabled) return@intercept chain.proceed()
            val shot = chain.getArg(0)
            val context = bindings.contextOf(chain.thisObject) ?: return@intercept chain.proceed()
            runCatching { bindings.stamp(context, shot) }
                .onFailure {
                    Logger.error("stamp failed tweak=$TWEAK path=entry reason=${it.message}", it)
                }
            val previousScreenshot = pendingScreenshot.get()
            pendingScreenshot.set(context to shot)
            try {
                chain.proceed()
            } finally {
                if (previousScreenshot == null) {
                    pendingScreenshot.remove()
                } else {
                    pendingScreenshot.set(previousScreenshot)
                }
            }
        }
    }
    bindings.captureDisplay?.let { method ->
        hook(method).intercept { chain ->
            val (context, shot) = pendingScreenshot.get() ?: return@intercept chain.proceed()
            if (!switch.enabled || !bindings.awaitingCapture(shot)) return@intercept chain.proceed()
            val captured = chain.proceed() as Bitmap? ?: return@intercept null
            bindings.stampCapturedBitmap(context, shot, captured, "capture")
        }
    }
    bindings.captureResultConstructor?.let { constructor ->
        hook(constructor).intercept { chain ->
            if (!switch.enabled) return@intercept chain.proceed()
            val (context, shot) = pendingScreenshot.get() ?: return@intercept chain.proceed()
            val captured = chain.getArg(0) as? Bitmap ?: return@intercept chain.proceed()
            if (!bindings.awaitingCapture(shot)) return@intercept chain.proceed()
            val args = chain.args.toTypedArray<Any?>()
            args[0] = bindings.stampCapturedBitmap(context, shot, captured, "result")
            chain.proceed(args)
        }
    }
    return true
}

private fun shouldStamp(pkg: String): Boolean {
    val selected = selectedApps.value
    return selected.isEmpty() || (pkg in selected) == onlySelected.enabled
}

private fun label(
    context: Context,
    pkg: String,
): String {
    val pm = context.packageManager
    return runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, ApplicationInfoFlags.of(0))).toString()
    }.getOrDefault(pkg)
}

private fun drawPill(
    context: Context,
    label: String,
    src: Bitmap,
): Bitmap {
    val bitmap = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true)
    val density = context.resources.displayMetrics.density
    val portrait = bitmap.height > bitmap.width
    val top = (if (portrait) PORTRAIT_TOP_DP else LANDSCAPE_TOP_DP) * density
    val hPad = HORIZONTAL_PADDING_DP * density
    val vPad =
        (if (portrait) PORTRAIT_VERTICAL_PADDING_DP else LANDSCAPE_VERTICAL_PADDING_DP) * density
    val radius = CORNER_RADIUS_DP * density
    val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PILL_BACKGROUND_COLOR }
    val text =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = TEXT_SIZE_DP * density
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
    val bounds = Rect().also { text.getTextBounds(label, 0, label.length, it) }
    val width = maxOf(bounds.width() + 2 * hPad, MIN_WIDTH_DP * density)
    val height = bounds.height() + 2 * vPad
    val left = (bitmap.width - width) / 2
    val canvas = Canvas(bitmap)
    canvas.drawRoundRect(left, top, left + width, top + height, radius, radius, pill)
    canvas.drawText(
        label,
        bitmap.width / 2f,
        top + height / 2 + bounds.height() / 2f - bounds.bottom,
        text,
    )
    return bitmap
}
