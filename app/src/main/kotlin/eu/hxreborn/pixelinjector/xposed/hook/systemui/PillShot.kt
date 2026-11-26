package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.UserHandle
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.detail
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.optionalMethod
import eu.hxreborn.pixelinjector.util.orNullIfMissing
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.requiredMethods
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.Value
import eu.hxreborn.pixelinjector.xposed.hook.DexKit
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TWEAK = "PillShot"
private const val CONTROLLER = "com.android.systemui.screenshot.ScreenshotController"
private const val DATA = "com.android.systemui.screenshot.ScreenshotData"
private const val CAPTURE = "com.android.systemui.screenshot.ImageCaptureImpl"
private const val TRANSSION_VIEW = "com.android.systemui.screenshot.TrScreenshotView"
private const val NULL_BITMAP_MESSAGE = "handleScreenshot: Screenshot bitmap was null"
private const val CAPTURE_BUFFER = "android.window.ScreenCapture\$ScreenshotHardwareBuffer"
private const val SAMSUNG_RESULT = "com.android.systemui.screenshot.sep.SemScreenshotResult"
private const val MOTO_SESSION =
    "com.android.systemui.screenshot.MotoGlobalScreenshot\$DisplayScreenshotSession"
private const val MOTO_BITMAP_ARGUMENT = 0
private const val MOTO_PREVIEW_ARGUMENT = 1
private const val MOTO_PACKAGE_ARGUMENT = 3
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
    val captureBitmap: Method?,
    val captureCaller: Method?,
    val captureResultConstructor: Constructor<*>?,
    val context: Field,
    val type: Field,
    val bitmap: Field,
    val topComponent: Field,
    val entryService: Field?,
    val serviceController: Field?,
) {
    companion object {
        fun resolve(cl: ClassLoader): ScreenshotBindings {
            val controller = cl.loadClass(CONTROLLER)
            val data = cl.loadClass(DATA)
            val handleScreenshot =
                resolving { controller.requiredMethods("handleScreenshot") }.getOrElse { failure ->
                    if (Build.FINGERPRINT == HMD_CREST_MAX_FINGERPRINT) {
                        val entry =
                            cl.loadClass(
                                "com.android.systemui.screenshot.TakeScreenshotService\$\$ExternalSyntheticLambda1",
                            )
                        return@getOrElse listOf(entry.requiredMethod("accept", Any::class.java))
                    }
                    if (orNullIfMissing { cl.loadClass(TRANSSION_VIEW) } == null) throw failure
                    listOf(
                        DexKit.method(
                            "$TWEAK.transsion",
                            cl,
                            "accept usingStrings($NULL_BITMAP_MESSAGE)",
                        ) { bridge ->
                            bridge.findMethod {
                                matcher {
                                    name("accept")
                                        .paramTypes("java.lang.Object")
                                        .usingStrings(NULL_BITMAP_MESSAGE)
                                }
                            }
                        },
                    )
                }
            val entryService =
                handleScreenshot
                    .first()
                    .declaringClass
                    .takeUnless { it == controller }
                    ?.requiredField("f\$0")
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
            val captureBitmap =
                if (entryService != null && Build.FINGERPRINT == HMD_CREST_MAX_FINGERPRINT) {
                    cl.loadClass(CAPTURE).requiredMethod("captureDisplay", Rect::class.java)
                } else if (entryService != null) {
                    Bitmap::class.java.requiredMethod(
                        "wrapHardwareBuffer",
                        HardwareBuffer::class.java,
                        ColorSpace::class.java,
                    )
                } else {
                    capture?.optionalMethod("captureDisplay")
                }
            if (captureResultConstructor == null && captureBitmap == null &&
                Build.VERSION.SDK_INT <= LAST_INLINE_CAPTURE_SDK
            ) {
                throw NoSuchMethodException("ImageCaptureImpl.captureDisplay")
            }
            return ScreenshotBindings(
                handleScreenshot = handleScreenshot,
                captureBitmap = captureBitmap,
                captureCaller =
                    if (captureBitmap?.declaringClass == Bitmap::class.java) {
                        orNullIfMissing { cl.loadClass(CAPTURE_BUFFER).requiredMethod("asBitmap") }
                    } else {
                        null
                    },
                captureResultConstructor = captureResultConstructor,
                context = controller.requiredField("context", "mContext"),
                type = data.requiredField("type", "mType"),
                bitmap = data.requiredField("bitmap", "mBitmap"),
                topComponent = data.requiredField("topComponent", "mTopComponent"),
                entryService = entryService,
                serviceController =
                    if (entryService != null) {
                        cl
                            .loadClass("com.android.systemui.screenshot.TakeScreenshotService")
                            .requiredField("mScreenshot")
                    } else {
                        null
                    },
            )
        }
    }

    fun members(): String =
        (
            handleScreenshot.map { it.signature() } +
                listOfNotNull(captureBitmap?.signature(), captureResultConstructor?.toString())
        ).joinToString(",")

    fun contextOf(receiver: Any): Context? =
        try {
            val owner = serviceController?.get(entryService?.get(receiver)) ?: receiver
            context.get(owner) as? Context
        } catch (e: Exception) {
            Logger.error("context failed tweak=$TWEAK reason=${e.detail()}", e)
            null
        } catch (e: LinkageError) {
            Logger.error("context failed tweak=$TWEAK reason=${e.detail()}", e)
            null
        }

    fun awaitingCapture(shot: Any): Boolean =
        try {
            type.getInt(shot) == TAKE_SCREENSHOT_FULLSCREEN && bitmap.get(shot) == null
        } catch (e: Exception) {
            Logger.error("capture state failed tweak=$TWEAK reason=${e.detail()}", e)
            false
        } catch (e: LinkageError) {
            Logger.error("capture state failed tweak=$TWEAK reason=${e.detail()}", e)
            false
        }

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
            Logger.error("stamp failed tweak=$TWEAK path=$path reason=${e.detail()}", e)
            captured
        } catch (e: LinkageError) {
            Logger.error("stamp failed tweak=$TWEAK path=$path reason=${e.detail()}", e)
            captured
        }
}

private fun XposedModule.installPillShot(cl: ClassLoader): Boolean {
    orNullIfMissing { cl.loadClass(MOTO_SESSION) }?.let { return installMotoPillShot(it) }
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
            if (bindings.entryService != null &&
                !bindings.type.declaringClass.isInstance(shot)
            ) {
                return@intercept chain.proceed()
            }
            val context = bindings.contextOf(chain.thisObject) ?: return@intercept chain.proceed()
            try {
                bindings.stamp(context, shot)
            } catch (e: Exception) {
                Logger.error("stamp failed tweak=$TWEAK path=entry reason=${e.detail()}", e)
            } catch (e: LinkageError) {
                Logger.error("stamp failed tweak=$TWEAK path=entry reason=${e.detail()}", e)
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
        if (bindings.entryService != null) deoptimize(method)
    }
    bindings.captureCaller?.let(::deoptimize)
    bindings.captureBitmap?.let { method ->
        hook(method).intercept { chain ->
            if (!switch.enabled) return@intercept chain.proceed()
            val (context, shot) = pendingScreenshot.get() ?: return@intercept chain.proceed()
            if (!bindings.awaitingCapture(shot)) return@intercept chain.proceed()
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

private class MotoScreenshotBindings(
    val takeScreenshot: Method,
    val context: Field,
) {
    companion object {
        fun resolve(session: Class<*>): MotoScreenshotBindings =
            MotoScreenshotBindings(
                takeScreenshot =
                    session.requiredMethod(
                        "takeScreenshot",
                        Bitmap::class.java,
                        Bitmap::class.java,
                        Int::class.javaPrimitiveType,
                        String::class.java,
                        UserHandle::class.java,
                    ),
                context = session.requiredField("mContext"),
            )
    }
}

private fun XposedModule.installMotoPillShot(session: Class<*>): Boolean {
    val bindings =
        resolving { MotoScreenshotBindings.resolve(session) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info("resolved tweak=$TWEAK members=${bindings.takeScreenshot.signature()}")
    hook(bindings.takeScreenshot).intercept { chain ->
        if (!switch.enabled) return@intercept chain.proceed()
        val captured =
            chain.getArg(MOTO_BITMAP_ARGUMENT) as? Bitmap ?: return@intercept chain.proceed()
        val pkg = chain.getArg(MOTO_PACKAGE_ARGUMENT) as? String ?: return@intercept chain.proceed()
        if (!shouldStamp(pkg)) {
            Logger.info("stamp skipped tweak=$TWEAK pkg=$pkg onlySelected=${onlySelected.enabled}")
            return@intercept chain.proceed()
        }
        val args =
            try {
                val context = bindings.context.get(chain.thisObject) as Context
                val label = label(context, pkg)
                val preview = chain.getArg(MOTO_PREVIEW_ARGUMENT) as? Bitmap
                val stamped = drawPill(context, label, captured)
                chain.args.toTypedArray<Any?>().apply {
                    this[MOTO_BITMAP_ARGUMENT] = stamped
                    this[MOTO_PREVIEW_ARGUMENT] =
                        when (preview) {
                            null -> null
                            captured -> stamped
                            else -> drawPill(context, label, preview)
                        }
                }
            } catch (e: Exception) {
                Logger.error("stamp failed tweak=$TWEAK path=motorola reason=${e.detail()}", e)
                null
            } catch (e: LinkageError) {
                Logger.error("stamp failed tweak=$TWEAK path=motorola reason=${e.detail()}", e)
                null
            }
        if (args == null) chain.proceed() else chain.proceed(args)
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
    return try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, ApplicationInfoFlags.of(0))).toString()
    } catch (_: Exception) {
        pkg
    } catch (_: LinkageError) {
        pkg
    }
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
