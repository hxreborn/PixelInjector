package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.content.Context
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.requiredMethods
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TWEAK = "PillShot"
private const val CONTROLLER = "com.android.systemui.screenshot.ScreenshotController"
private const val DATA = "com.android.systemui.screenshot.ScreenshotData"

private val switch = Switch(Prefs.PILL_SHOT)

internal val pillShot =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI, Target.SCREENSHOT),
        prefs = listOf(switch),
        install = XposedModule::installPillShot,
    )

private class ScreenshotBindings(
    val handleScreenshot: List<Method>,
    val context: Field,
    val bitmap: Field,
    val packageName: Method,
) {
    companion object {
        fun resolve(cl: ClassLoader): ScreenshotBindings {
            val controller = cl.loadClass(CONTROLLER)
            val data = cl.loadClass(DATA)
            return ScreenshotBindings(
                handleScreenshot = controller.requiredMethods("handleScreenshot"),
                context = controller.requiredField("context", "mContext"),
                bitmap = data.requiredField("bitmap", "mBitmap"),
                packageName = data.requiredMethod("getPackageNameString"),
            )
        }
    }

    fun stamp(
        controller: Any,
        shot: Any,
    ) {
        val src = bitmap.get(shot) as Bitmap? ?: return
        val pkg = packageName.invoke(shot) as String
        if (pkg.isEmpty()) return
        val ctx = context.get(controller) as Context
        bitmap.set(shot, drawPill(ctx, label(ctx, pkg), src))
    }
}

private fun XposedModule.installPillShot(cl: ClassLoader): Boolean {
    val b =
        runCatching { ScreenshotBindings.resolve(cl) }.getOrElse {
            if (it is ClassNotFoundException) {
                Logger.debug { "skipped tweak=$TWEAK reason=no-class" }
            } else {
                Logger.error(
                    "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
                )
            }
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=${b.handleScreenshot.joinToString(",") { it.signature() }}",
    )
    for (method in b.handleScreenshot) {
        hook(method).intercept { chain ->
            if (!switch.enabled) return@intercept chain.proceed()
            runCatching { b.stamp(chain.thisObject, chain.getArg(0)) }
                .onFailure { Logger.error("stamp failed tweak=$TWEAK reason=${it.message}", it) }
            chain.proceed()
        }
    }
    return true
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
    val top = (if (portrait) 12 else 2) * density
    val hPad = 12 * density
    val vPad = (if (portrait) 8 else 6) * density
    val radius = 18 * density
    val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(28, 28, 30) }
    val text =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 16 * density
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
    val bounds = Rect().also { text.getTextBounds(label, 0, label.length, it) }
    val width = maxOf(bounds.width() + 2 * hPad, 80 * density)
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
