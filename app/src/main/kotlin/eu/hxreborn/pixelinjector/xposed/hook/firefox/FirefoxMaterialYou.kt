package eu.hxreborn.pixelinjector.xposed.hook.firefox

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.om.FabricatedOverlay
import android.content.om.OverlayManager
import android.content.om.OverlayManagerTransaction
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import androidx.core.graphics.ColorUtils
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
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
import eu.hxreborn.pixelinjector.xposed.hook.DexKit
import io.github.libxposed.api.XposedModule
import org.luckypray.dexkit.DexKitBridge
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap
import kotlin.math.hypot

private const val TWEAK = "FirefoxMaterialYou"
private const val THEME_PACKAGE = "mozilla.components.compose.base.theme"
private const val ACORN_THEME = "$THEME_PACKAGE.AcornThemeKt"
private const val ACORN_COLORS = "$THEME_PACKAGE.AcornColorsKt"
private const val COLOR_SCHEME = "androidx.compose.material3.ColorScheme"
private const val ACORN_GRADIENT_SCHEME = "$THEME_PACKAGE.AcornGradientScheme"
private const val ACORN_THEME_GROUP = 0xC3118A42.toInt()
private const val COLOR_SCHEME_LABEL = "ColorScheme(primary="
private const val COLOR_STOP = "mozilla.components.compose.base.utils.ColorStop"
private const val TRUST_PANEL = "org.mozilla.fenix.settings.trustpanel.ui"
private const val PROTECTION_BANNER = "$TRUST_PANEL.ProtectionPanelBannerKt"
private const val COMPOSE_COLOR_KT = "androidx.compose.ui.graphics.ColorKt"
private const val OVERLAY_NAME = "pixelinjector_material_you"
private const val OVERLAYABLE_NAME = "PixelInjector"
private const val POLICY_PUBLIC = 0x1
private const val COLOR_TYPE = "color"
private const val NIGHT_CONFIGURATION = "night"
private const val BLACK = 0xFF000000.toInt()
private const val SRGB_SPACE_MASK = 0x3FL
private const val ARGB_MASK = 0xFFFFFFFFL
private const val COLOR_SHIFT = 32
private const val ALPHA_SHIFT = 24
private const val DARK_LUMINANCE = 0.5f
private const val PRIVATE_SURFACE_TINT = 0.2f
private const val BYTE_MASK = 0xFF
private const val RGB_MASK = 0xFFFFFF
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val SAVED_COLORS = "colors"
private const val SAVED_APPLICATION = "application"
private const val SAVED_ACTIVITIES = "activities"
private const val SAVED_LOADER = "loader"

private val fxMobileRoleNames =
    mapOf(
        "inverse_surface" to "surface_inverse",
        "inverse_on_surface" to "on_surface_inverse",
        "inverse_primary" to "primary_inverse",
    )

private val inverseRoleFallbacks =
    mapOf(
        "inverse_primary" to ("primary" to true),
        "inverse_surface" to ("on_surface" to false),
        "inverse_on_surface" to ("surface_container" to false),
    )

private val overlayableLine =
    Regex(
        """resource='[^:]+:(\w+)/([^']+)' overlayable='([^']+)' actor='([^']*)' policy='0x([0-9a-f]+)'""",
    )

private val accentColors =
    listOf(
        android.R.color.system_accent1_0,
        android.R.color.system_accent1_10,
        android.R.color.system_accent1_50,
        android.R.color.system_accent1_100,
        android.R.color.system_accent1_200,
        android.R.color.system_accent1_300,
        android.R.color.system_accent1_400,
        android.R.color.system_accent1_500,
        android.R.color.system_accent1_600,
        android.R.color.system_accent1_700,
        android.R.color.system_accent1_800,
        android.R.color.system_accent1_900,
        android.R.color.system_accent1_1000,
        android.R.color.system_accent2_0,
        android.R.color.system_accent2_10,
        android.R.color.system_accent2_50,
        android.R.color.system_accent2_100,
        android.R.color.system_accent2_200,
        android.R.color.system_accent2_300,
        android.R.color.system_accent2_400,
        android.R.color.system_accent2_500,
        android.R.color.system_accent2_600,
        android.R.color.system_accent2_700,
        android.R.color.system_accent2_800,
        android.R.color.system_accent2_900,
        android.R.color.system_accent2_1000,
    )

private val tertiaryRoleSources =
    mapOf(
        "tertiary" to "primary",
        "on_tertiary" to "on_primary",
        "tertiary_container" to "primary_container",
        "on_tertiary_container" to "on_primary_container",
    )

private val colorResourceRoles =
    mapOf(
        "homepage_edge_to_edge_gradient_start" to "surface_container",
        "homepage_edge_to_edge_gradient_end" to "primary_container",
        "protections_dashboard_gradient_start" to "primary_container",
        "protections_dashboard_gradient_end" to "tertiary_container",
        "protections_dashboard_progress_bar" to "primary",
        "protections_dashboard_chip_background" to "secondary_container",
        "protections_dashboard_chip_text" to "on_secondary_container",
        "mozac_feature_readerview_selected" to "primary",
        "fx_mobile_icon_private" to "error",
    )

private val privateColorResourceRoles =
    mapOf(
        "fx_mobile_private_icon_private" to "primary",
        "fx_mobile_private_layer_color_2" to "primary_container",
        "homepage_tab_edge_to_edge_private_toolbar_outline" to "outline_variant",
    )

private val privateModeRoles =
    mapOf(
        "primary" to ("error" to true),
        "on_primary" to ("on_error" to true),
        "primary_container" to ("error_container" to true),
        "on_primary_container" to ("on_error_container" to true),
        "inverse_primary" to ("error" to false),
        "secondary_container" to ("error_container" to true),
        "on_secondary_container" to ("on_error_container" to true),
        "tertiary" to ("error" to true),
        "on_tertiary" to ("on_error" to true),
        "tertiary_container" to ("error_container" to true),
        "on_tertiary_container" to ("on_error_container" to true),
        "surface_tint" to ("error" to true),
    )

private val privateTintedRoles =
    setOf(
        "background",
        "surface",
        "surface_dim",
        "surface_bright",
        "surface_variant",
        "surface_container_lowest",
        "surface_container_low",
        "surface_container",
        "surface_container_high",
        "surface_container_highest",
    )

private val bannerStopRoles =
    mapOf(
        0xFFE9DAFBL to (false to "primary_container"),
        0xFFFFE3CEL to (false to "tertiary_container"),
        0xFFAB71FFL to (true to "primary"),
        0xFFFF8A50L to (true to "tertiary"),
    )

private val gradientRoles =
    mapOf(
        "cfr" to ("primary" to "tertiary"),
        "accent" to ("primary" to "tertiary"),
        "accentSubtle" to ("primary_container" to "tertiary_container"),
        "tabOutline" to ("primary" to "tertiary"),
    )

private val switch =
    Switch(Prefs.FIREFOX_MATERIAL_YOU) { enabled ->
        val bindings = current ?: return@Switch
        val application = liveApplication?.get() ?: return@Switch
        Handler(Looper.getMainLooper()).post { bindings.toggleLive(application, enabled) }
    }

@Volatile private var current: MaterialYouBindings? = null

@Volatile private var wallpaperColors: WallpaperColors? = null

@Volatile private var liveApplication: WeakReference<Application>? = null

@Volatile private var overlayLoader: ResourcesLoader? = null

@Volatile private var tracker: ActivityTracker? = null

private val liveActivities: MutableSet<Activity> = Collections.newSetFromMap(WeakHashMap())

@Volatile private var bannerInitThread: Thread? = null

private val recoloredGradients = IdentityHashMap<Any, Any>()

internal val firefoxMaterialYou =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.FIREFOX),
        prefs = listOf(switch),
        saveState = ::saveMaterialYou,
        restoreState = ::restoreMaterialYou,
        onReloadRejected = { liveApplication?.get()?.let(::trackActivities) },
        install = XposedModule::installFirefoxMaterialYou,
    )

private fun saveMaterialYou(): Any {
    val application = liveApplication?.get()
    tracker?.let { application?.unregisterActivityLifecycleCallbacks(it) }
    tracker = null
    val activities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())
    activities.addAll(liveActivitySnapshot())
    return hashMapOf(
        SAVED_COLORS to wallpaperColors?.let { listOf(it.light, it.dark, it.privateMode) },
        SAVED_APPLICATION to application,
        SAVED_ACTIVITIES to activities,
        SAVED_LOADER to overlayLoader,
    )
}

private fun restoreMaterialYou(saved: Any?) {
    val state = saved as? Map<*, *> ?: return
    val arrays = (state[SAVED_COLORS] as? List<*>)?.filterIsInstance<IntArray>()
    if (arrays?.size == 3) {
        wallpaperColors = WallpaperColors(arrays[0], arrays[1], arrays[2])
    }
    overlayLoader = state[SAVED_LOADER] as? ResourcesLoader
    val activities = (state[SAVED_ACTIVITIES] as? Set<*>).orEmpty().filterIsInstance<Activity>()
    synchronized(liveActivities) { liveActivities.addAll(activities) }
    val application = state[SAVED_APPLICATION] as? Application ?: return
    trackActivities(application)
    Logger.info(
        "carried tweak=$TWEAK activities=${activities.size} overlay=${overlayLoader != null}",
    )
}

private fun liveActivitySnapshot(): List<Activity> =
    synchronized(liveActivities) {
        liveActivities.toList()
    }

private fun trackActivities(application: Application) {
    liveApplication = WeakReference(application)
    if (tracker != null) return
    tracker = ActivityTracker().also(application::registerActivityLifecycleCallbacks)
}

private fun MaterialYouBindings.toggleLive(
    application: Application,
    enabled: Boolean,
) {
    if (enabled == (overlayLoader != null)) return
    runCatching {
        if (enabled) {
            val colors = readWallpaperColors(application)
            wallpaperColors = colors
            applyOverlay(application, colors)
        } else {
            removeOverlayLoader(application)
            removeOverlay(application)
        }
        val activities = liveActivitySnapshot()
        activities.forEach(Activity::recreate)
        Logger.info(
            "${if (enabled) "applied" else "reverted"} tweak=$TWEAK activities=${activities.size}",
        )
    }.onFailure {
        val action = if (enabled) "apply" else "revert"
        val detail = Logger.field("detail", it.message)
        Logger.error("$action failed tweak=$TWEAK reason=${it.reason()}$detail", it)
    }
}

private fun removeOverlayLoader(application: Application) {
    val loader = overlayLoader ?: return
    application.resources.removeLoaders(loader)
    liveActivitySnapshot().forEach { it.resources.removeLoaders(loader) }
    overlayLoader = null
}

private class SchemeColor(
    val field: Field,
    val role: String,
)

private class WallpaperColors(
    val light: IntArray,
    val dark: IntArray,
    val privateMode: IntArray,
) {
    fun forMode(dark: Boolean) = if (dark) this.dark else light
}

private class Gradients(
    val schemeClass: Class<*>,
    val schemeConstructor: Constructor<*>,
    val schemeFields: List<Field>,
    val gradientConstructor: Constructor<*>,
    val type: Field,
    val colorStops: Field,
    val stopConstructor: Constructor<*>,
    val colorStop: Field,
    val pairFields: List<Field>? = null,
    val packedColor: Field? = null,
) {
    companion object {
        fun resolve(
            cl: ClassLoader,
            schemeClass: Class<*>,
        ): Gradients {
            val gradientClass = schemeClass.requiredField("accent").type
            val stopClass = cl.loadClass(COLOR_STOP)
            val type = gradientClass.requiredField("type")
            val fieldNames = gradientRoles.keys + "privacyMask"
            val schemeConstructor =
                schemeClass.declaredConstructors.firstOrNull { it.parameterCount == 5 }
                    ?: throw NoSuchMethodException("$ACORN_GRADIENT_SCHEME.<init>")
            return Gradients(
                schemeClass = schemeClass,
                schemeConstructor = schemeConstructor,
                schemeFields = fieldNames.map { schemeClass.requiredField(it) },
                gradientConstructor =
                    gradientClass.getDeclaredConstructor(
                        type.type,
                        List::class.java,
                    ),
                type = type,
                colorStops = gradientClass.requiredField("colorStops"),
                stopConstructor =
                    stopClass.declaredConstructors.firstOrNull { it.parameterCount == 1 }
                        ?: throw NoSuchMethodException("$COLOR_STOP.<init>"),
                colorStop = stopClass.requiredField("colorStop"),
            )
        }

        fun resolveFromDex(
            cl: ClassLoader,
            bridge: DexKitBridge,
            sample: Any,
        ): Gradients {
            val schemeClass = sample.javaClass
            val schemeConstructor =
                schemeClass.declaredConstructors.singleOrNull {
                    it.parameterCount == gradientRoles.size + 1 &&
                        it.parameterTypes.distinct().size == 1
                } ?: throw NoSuchMethodException("AcornGradientScheme constructor")
            val gradientClass = schemeConstructor.parameterTypes.first()
            val schemeFields =
                bridge
                    .getMethodData(schemeConstructor)
                    ?.usingFields
                    .orEmpty()
                    .filter {
                        it.usingType.isWrite() && it.field.declaredClassName == schemeClass.name
                    }.map { it.field.getFieldInstance(cl).apply { isAccessible = true } }
                    .distinct()
            if (schemeFields.size != schemeConstructor.parameterCount ||
                schemeFields.any { it.type != gradientClass }
            ) {
                throw NoSuchFieldException("AcornGradientScheme constructor fields")
            }
            val gradientConstructor =
                gradientClass.declaredConstructors.singleOrNull {
                    it.parameterCount == 2 && it.parameterTypes[1] == List::class.java
                } ?: throw NoSuchMethodException("AcornGradient constructor")
            val type = gradientClass.onlyFieldOfType(gradientConstructor.parameterTypes[0])
            val stops = gradientClass.onlyFieldOfType(List::class.java)
            val gradient = schemeFields.first().get(sample)
            val stop =
                (stops.get(gradient) as List<*>).firstOrNull()
                    ?: throw NoSuchFieldException("AcornGradient color stop sample")
            val stopConstructor =
                stop.javaClass.declaredConstructors.singleOrNull { it.parameterCount == 1 }
                    ?: throw NoSuchMethodException("ColorStop constructor")
            val pair = stop.javaClass.onlyFieldOfType(stopConstructor.parameterTypes[0])
            val pairSample = pair.get(stop)
            val pairConstructor =
                pairSample.javaClass.getDeclaredConstructor(
                    Any::class.java,
                    Any::class.java,
                )
            val pairFields =
                bridge
                    .getMethodData(pairConstructor)
                    ?.usingFields
                    .orEmpty()
                    .filter {
                        it.usingType.isWrite() &&
                            it.field.declaredClassName == pairSample.javaClass.name
                    }.map { it.field.getFieldInstance(cl).apply { isAccessible = true } }
                    .distinct()
            val boxedColor = pairFields.getOrNull(1)?.get(pairSample)
            if (pairFields.size != 2 ||
                boxedColor == null
            ) {
                throw NoSuchFieldException("ColorStop pair")
            }
            return Gradients(
                schemeClass,
                schemeConstructor,
                schemeFields,
                gradientConstructor,
                type,
                stops,
                stopConstructor,
                pair,
                pairFields,
                boxedColor.javaClass.onlyFieldOfType(Long::class.javaPrimitiveType!!),
            )
        }

        private fun Class<*>.onlyFieldOfType(type: Class<*>): Field =
            declaredFields
                .singleOrNull { it.type == type && !Modifier.isStatic(it.modifiers) }
                ?.apply { isAccessible = true }
                ?: throw NoSuchFieldException("$simpleName field of type ${type.simpleName}")
    }
}

private class MaterialYouBindings(
    val acornTheme: Method,
    val applicationOnCreate: Method,
    val privatePalette: Any,
    val oledPalette: Any?,
    val paletteIndex: Int,
    val schemeIndex: Int,
    val gradientIndex: Int,
    val schemeColors: List<SchemeColor>,
    val surface: Field,
    val gradients: Gradients?,
    val overlayablesToString: Method?,
) {
    fun roleIndex(role: String) = schemeColors.indexOfFirst { it.role == role }

    val sourceIndices =
        IntArray(schemeColors.size) { index ->
            tertiaryRoleSources[schemeColors[index].role]?.let(::roleIndex)?.takeIf { it >= 0 }
                ?: index
        }

    companion object {
        fun resolve(cl: ClassLoader): MaterialYouBindings =
            orNullIfMissing { resolveNamed(cl) } ?: resolveFromDex(cl)

        private fun resolveNamed(cl: ClassLoader): MaterialYouBindings {
            val acornColors = cl.loadClass(ACORN_COLORS)
            val privatePalette =
                acornColors.requiredField("privateColorPalette").get(null)
                    ?: throw NoSuchFieldException("$ACORN_COLORS#privateColorPalette")
            val oledPalette =
                orNullIfMissing { acornColors.requiredField("oledColorPalette").get(null) }
            val colorScheme = cl.loadClass(COLOR_SCHEME)
            val acornTheme =
                cl
                    .loadClass(ACORN_THEME)
                    .requiredMethods("AcornTheme")
                    .firstOrNull {
                        colorScheme in it.parameterTypes &&
                            privatePalette.javaClass in it.parameterTypes
                    } ?: throw NoSuchMethodException("$ACORN_THEME#AcornTheme")
            val gradients =
                orNullIfMissing { Gradients.resolve(cl, cl.loadClass(ACORN_GRADIENT_SCHEME)) }
            val schemeColors =
                colorScheme.declaredFields
                    .filter {
                        it.type == Long::class.javaPrimitiveType && !Modifier.isStatic(it.modifiers)
                    }.mapNotNull { it.asSchemeColor(it.name) }
            return bindings(
                acornTheme,
                colorScheme,
                privatePalette,
                oledPalette,
                schemeColors,
                gradients,
            )
        }

        private fun resolveFromDex(cl: ClassLoader): MaterialYouBindings =
            DexKit.query(cl) { bridge ->
                val schemeData =
                    bridge
                        .findMethod {
                            matcher {
                                name("toString")
                                usingStrings(COLOR_SCHEME_LABEL)
                            }
                        }.singleOrNull() ?: throw NoSuchMethodException("ColorScheme.toString")
                val colorScheme = schemeData.getClassInstance(cl)
                val schemeColors = identifySchemeColors(colorScheme)
                val themeData =
                    bridge
                        .findMethod {
                            matcher {
                                paramCount(7)
                                returnType("void")
                                usingNumbers(ACORN_THEME_GROUP)
                            }
                        }.singleOrNull { colorScheme.name in it.paramTypeNames }
                        ?: throw NoSuchMethodException("AcornTheme ColorScheme group")
                val acornTheme = themeData.getMethodInstance(cl).apply { isAccessible = true }
                val paletteClass = acornTheme.parameterTypes.first()
                val firefoxTheme =
                    bridge
                        .findMethod {
                            matcher {
                                name("FirefoxTheme")
                                paramCount(5)
                                returnType("void")
                            }
                        }.singleOrNull { method ->
                            method.usingFields.any {
                                it.field.typeName ==
                                    paletteClass.name
                            }
                        }
                        ?: throw NoSuchMethodException("FirefoxTheme Acorn palettes")

                fun paletteFields(method: org.luckypray.dexkit.result.MethodData) =
                    method.usingFields
                        .filter { it.usingType.isRead() && it.field.typeName == paletteClass.name }
                        .map { it.field }
                        .distinct()
                val palettes = paletteFields(firefoxTheme)
                val defaults = paletteFields(themeData)
                if (palettes.size != 4 || palettes.drop(2) != defaults) {
                    throw NoSuchFieldException(
                        "FirefoxTheme private, OLED, dark and light palettes",
                    )
                }
                val (privatePalette, oledPalette) =
                    palettes.take(2).map { field ->
                        field.getFieldInstance(cl).apply { isAccessible = true }.get(null)
                            ?: throw NoSuchFieldException("FirefoxTheme.${field.name}")
                    }
                val gradientClass =
                    acornTheme.parameterTypes[
                        acornTheme.parameterTypes.indexOf(colorScheme) +
                            1,
                    ]
                val gradients =
                    orNullIfMissing {
                        val sample =
                            firefoxTheme.usingFields
                                .firstOrNull {
                                    it.usingType.isRead() && it.field.typeName == gradientClass.name
                                }?.field
                                ?.getFieldInstance(cl)
                                ?.apply { isAccessible = true }
                                ?.get(null)
                                ?: throw NoSuchFieldException("FirefoxTheme Acorn gradient scheme")
                        Gradients.resolveFromDex(cl, bridge, sample)
                    }
                bindings(
                    acornTheme,
                    colorScheme,
                    privatePalette,
                    oledPalette,
                    schemeColors,
                    gradients,
                )
            }

        private fun identifySchemeColors(colorScheme: Class<*>): List<SchemeColor> {
            val constructor =
                colorScheme.declaredConstructors.singleOrNull {
                    it.parameterCount > 0 &&
                        it.parameterTypes.all { type -> type == Long::class.javaPrimitiveType }
                } ?: throw NoSuchMethodException("ColorScheme color constructor")
            constructor.isAccessible = true
            val sample =
                constructor.newInstance(
                    *Array<Any>(constructor.parameterCount) { packColor(BLACK) },
                )
            val stock = schemeLabels(sample)
            return colorScheme.declaredFields.mapNotNull { field ->
                if (field.type != Long::class.javaPrimitiveType ||
                    Modifier.isStatic(field.modifiers)
                ) {
                    return@mapNotNull null
                }
                field.isAccessible = true
                val original = field.getLong(sample)
                val probe = if (isBlack(original)) packColor(-1) else packColor(BLACK)
                field.setLong(sample, probe)
                val labels = schemeLabels(sample)
                field.setLong(sample, original)
                labels.keys.singleOrNull { labels[it] != stock[it] }?.let {
                    field.asSchemeColor(
                        it,
                    )
                }
            }
        }

        private fun schemeLabels(scheme: Any): Map<String, String> =
            Regex("""(\w+)=(Color\([^)]*\))""")
                .findAll(scheme.toString())
                .associate { it.groupValues[1] to it.groupValues[2] }

        private fun Field.asSchemeColor(name: String): SchemeColor? {
            val role = name.replace(Regex("([A-Z])")) { "_" + it.value.lowercase() }
            if (systemColorId(role, dark = true) == 0) return null
            isAccessible = true
            return SchemeColor(this, role)
        }

        private fun bindings(
            acornTheme: Method,
            colorScheme: Class<*>,
            privatePalette: Any,
            oledPalette: Any?,
            schemeColors: List<SchemeColor>,
            gradients: Gradients?,
        ): MaterialYouBindings =
            MaterialYouBindings(
                acornTheme = acornTheme,
                applicationOnCreate = Application::class.java.requiredMethod("onCreate"),
                privatePalette = privatePalette,
                oledPalette = oledPalette,
                paletteIndex = acornTheme.parameterTypes.indexOf(privatePalette.javaClass),
                schemeIndex = acornTheme.parameterTypes.indexOf(colorScheme),
                gradientIndex =
                    gradients?.let { acornTheme.parameterTypes.indexOf(it.schemeClass) } ?: -1,
                surface =
                    schemeColors.singleOrNull { it.role == "surface" }?.field
                        ?: throw NoSuchFieldException("ColorScheme.surface"),
                gradients = gradients,
                overlayablesToString =
                    orNullIfMissing {
                        AssetManager::class.java.getMethod(
                            "getOverlayablesToString",
                            String::class.java,
                        )
                    },
                schemeColors = schemeColors,
            )
    }
}

private fun Gradients.recolor(
    gradient: Any,
    startArgb: Int,
    endArgb: Int,
): Any {
    val stops =
        (colorStops.get(gradient) as List<*>).mapIndexed { index, stop ->
            val pair = colorStop.get(stop)!!
            val position = (pairFields?.get(0) ?: pair.javaClass.requiredField("first")).get(pair)
            val box = (pairFields?.get(1) ?: pair.javaClass.requiredField("second")).get(pair)!!
            val stockColor = (packedColor ?: box.javaClass.requiredField("value")).getLong(box)
            val alpha = (stockColor ushr (COLOR_SHIFT + ALPHA_SHIFT)).toInt() and BYTE_MASK
            val rgb = (if (index == 0) startArgb else endArgb) and RGB_MASK
            val color =
                box.javaClass
                    .getDeclaredConstructor(Long::class.javaPrimitiveType)
                    .apply { isAccessible = true }
                    .newInstance(packColor(alpha shl ALPHA_SHIFT or rgb))
            val pairConstructor = pair.javaClass.getConstructor(Any::class.java, Any::class.java)
            stopConstructor.newInstance(pairConstructor.newInstance(position, color))
        }
    return gradientConstructor.newInstance(type.get(gradient), stops)
}

private fun MaterialYouBindings.recolorGradients(
    scheme: Any,
    argb: IntArray,
): Any {
    val gradients = gradients ?: return scheme
    return recoloredGradients.getOrPut(scheme) {
        runCatching {
            fun color(role: String) = argb[roleIndex(role)]
            val stock = gradients.schemeFields.map { it.get(scheme)!! }
            val copy = gradients.schemeConstructor.newInstance(*stock.toTypedArray())
            gradients.schemeFields.forEachIndexed { index, field ->
                val roles = gradientRoles.values.elementAtOrNull(index)
                field.isAccessible = true
                val gradient =
                    roles?.let { (start, end) ->
                        gradients.recolor(stock[index], color(start), color(end))
                    }
                field.set(copy, gradient ?: stock[index])
            }
            copy
        }.getOrElse {
            Logger.error(
                "apply failed tweak=$TWEAK reason=${it.reason()}" +
                    Logger.field("detail", it.message),
                it,
            )
            scheme
        }
    }
}

private fun systemColorId(
    role: String,
    dark: Boolean,
): Int {
    fun id(name: String) =
        Resources.getSystem().getIdentifier(name, "color", "android").takeIf { it != 0 }
    val suffix = if (dark) "_dark" else "_light"
    return id("system_$role$suffix")
        ?: id("system_$role")
        ?: inverseRoleFallbacks[role]?.let { (fallback, opposite) ->
            systemColorId(fallback, dark != opposite)
        }
        ?: 0
}

private fun MaterialYouBindings.readWallpaperColors(context: Context): WallpaperColors {
    fun colors(dark: Boolean) =
        IntArray(schemeColors.size) { context.getColor(systemColorId(schemeColors[it].role, dark)) }
    val errorContainer = context.getColor(systemColorId("error_container", dark = true))
    val privateMode =
        IntArray(schemeColors.size) {
            val stockRole = schemeColors[it].role
            val (role, dark) = privateModeRoles[stockRole] ?: (stockRole to true)
            val color = context.getColor(systemColorId(role, dark))
            if (stockRole in privateTintedRoles) {
                blend(color, errorContainer, PRIVATE_SURFACE_TINT)
            } else {
                color
            }
        }
    return WallpaperColors(
        light = colors(dark = false),
        dark = colors(dark = true),
        privateMode = privateMode,
    )
}

private fun OverlayManager.register(overlay: FabricatedOverlay) =
    commit(OverlayManagerTransaction.newInstance().apply { registerFabricatedOverlay(overlay) })

private class DeclaredOverlayable(
    val name: String,
    val colors: List<String>,
)

private fun MaterialYouBindings.declaredOverlayable(context: Context): DeclaredOverlayable? {
    val dump =
        overlayablesToString?.invoke(context.assets, context.packageName) as? String ?: return null
    return overlayableLine
        .findAll(dump)
        .map { it.destructured }
        .filter { (type, _, _, actor, policy) ->
            type == COLOR_TYPE && actor.isEmpty() && policy.toInt(16) and POLICY_PUBLIC != 0
        }.groupBy({ (_, _, overlayable) -> overlayable }, { (_, name) -> name })
        .maxByOrNull { it.value.size }
        ?.let { (name, colors) -> DeclaredOverlayable(name, colors) }
}

private fun lightnessAndChroma(color: Int): Pair<Double, Double> {
    val lab = DoubleArray(3)
    ColorUtils.colorToLAB(color or BLACK, lab)
    return lab[0] to hypot(lab[1], lab[2])
}

private fun accentOverlay(
    context: Context,
    declared: DeclaredOverlayable,
): Pair<FabricatedOverlay, Int> {
    val resources = context.resources
    val packageName = context.packageName
    val accents = accentColors.map(context::getColor).map { it to lightnessAndChroma(it) }
    val overlay =
        FabricatedOverlay(OVERLAY_NAME, packageName).apply {
            setTargetOverlayable(declared.name)
        }
    var count = 0
    for (name in declared.colors) {
        val id =
            resources.getIdentifier(name, COLOR_TYPE, packageName).takeIf { it != 0 } ?: continue
        val stock = context.getColor(id)
        val (lightness, chroma) = lightnessAndChroma(stock)
        val (nearest) = accents.minBy { (_, lc) -> hypot(lc.first - lightness, lc.second - chroma) }
        overlay.setResourceValue(
            resources.getResourceName(id),
            TypedValue.TYPE_INT_COLOR_ARGB8,
            (stock and BLACK) or (nearest and RGB_MASK),
            null,
        )
        count++
    }
    return overlay to count
}

private fun OverlayManager.findOverlay(packageName: String) =
    getOverlayInfosForTarget(packageName).firstOrNull { it.overlayName == OVERLAY_NAME }

private fun resourcesFor(
    context: Context,
    nightMode: Int,
): Resources {
    val configuration = Configuration(context.resources.configuration)
    configuration.uiMode =
        nightMode or (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
    return context.createConfigurationContext(configuration).resources
}

private fun MaterialYouBindings.applyOverlay(
    application: Application,
    colors: WallpaperColors,
) {
    val overlayManager = application.getSystemService(OverlayManager::class.java) ?: return
    val packageName = application.packageName
    val resources = application.resources
    val day = resourcesFor(application, Configuration.UI_MODE_NIGHT_NO)
    val night = resourcesFor(application, Configuration.UI_MODE_NIGHT_YES)
    val overlay =
        FabricatedOverlay(OVERLAY_NAME, packageName).apply {
            setTargetOverlayable(OVERLAYABLE_NAME)
        }
    var count = 0

    fun put(
        id: Int,
        stock: Int,
        color: Int,
        configuration: String?,
        keepBlack: Boolean = true,
    ) {
        overlay.setResourceValue(
            resources.getResourceName(id),
            TypedValue.TYPE_INT_COLOR_ARGB8,
            if (keepBlack && stock == BLACK) BLACK else color,
            configuration,
        )
        count++
    }

    fun putDayNight(
        id: Int,
        index: Int,
    ) {
        put(id, day.getColor(id, null), colors.light[index], null, keepBlack = false)
        put(
            id,
            night.getColor(id, null),
            colors.dark[index],
            NIGHT_CONFIGURATION,
            keepBlack = false,
        )
    }

    fun colorId(name: String) =
        resources.getIdentifier(name, "color", packageName).takeIf { it != 0 }
    schemeColors.forEachIndexed { index, schemeColor ->
        val role = fxMobileRoleNames[schemeColor.role] ?: schemeColor.role
        val source = sourceIndices[index]
        colorId("fx_mobile_$role")?.let { putDayNight(it, source) }
        colorId("fx_mobile_oled_$role")?.let { id ->
            put(id, night.getColor(id, null), colors.dark[source], null)
        }
        colorId("fx_mobile_private_$role")?.let { id ->
            put(id, night.getColor(id, null), colors.privateMode[source], null)
        }
        colorId("m3_sys_color_light_${schemeColor.role}")?.let { id ->
            put(id, day.getColor(id, null), colors.light[source], null)
        }
        colorId("m3_sys_color_dark_${schemeColor.role}")?.let { id ->
            put(id, night.getColor(id, null), colors.dark[source], null)
        }
    }
    colorResourceRoles.forEach { (name, role) ->
        val index = roleIndex(role)
        val id = colorId(name)
        if (index >= 0 && id != null) putDayNight(id, index)
    }
    privateColorResourceRoles.forEach { (name, role) ->
        val index = roleIndex(role)
        val id = colorId(name)
        if (index >= 0 &&
            id != null
        ) {
            put(id, night.getColor(id, null), colors.privateMode[index], null)
        }
    }
    var overlayable = OVERLAYABLE_NAME
    val declared = declaredOverlayable(application)
    if (declared == null) {
        overlayManager.register(overlay)
    } else {
        val (accent, accentCount) = accentOverlay(application, declared)
        overlayManager.register(accent)
        count = accentCount
        overlayable = declared.name
    }
    val info = overlayManager.findOverlay(packageName) ?: return
    val loader = ResourcesLoader().apply { addProvider(ResourcesProvider.loadOverlay(info)) }
    resources.addLoaders(loader)
    overlayLoader = loader
    Logger.info("applied tweak=$TWEAK colors=$count overlayable=$overlayable")
}

private fun removeOverlay(application: Application) {
    val overlayManager = application.getSystemService(OverlayManager::class.java) ?: return
    val info = overlayManager.findOverlay(application.packageName) ?: return
    overlayManager.commit(
        OverlayManagerTransaction.newInstance().apply {
            unregisterFabricatedOverlay(info.overlayIdentifier)
        },
    )
    Logger.info("overlay removed tweak=$TWEAK")
}

private class ActivityTracker : Application.ActivityLifecycleCallbacks {
    override fun onActivityPreCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        synchronized(liveActivities) { liveActivities.add(activity) }
        overlayLoader?.let { activity.resources.addLoaders(it) }
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        synchronized(liveActivities) { liveActivities.remove(activity) }
    }
}

private fun blend(
    from: Int,
    to: Int,
    ratio: Float,
): Int {
    fun channel(shift: Int): Int {
        val start = from ushr shift and BYTE_MASK
        val end = to ushr shift and BYTE_MASK
        return (start + (end - start) * ratio).toInt() shl shift
    }
    return BLACK or channel(RED_SHIFT) or channel(GREEN_SHIFT) or channel(0)
}

private fun packColor(argb: Int): Long = (argb.toLong() and ARGB_MASK) shl COLOR_SHIFT

private fun isBlack(color: Long): Boolean =
    color and SRGB_SPACE_MASK == 0L && (color ushr COLOR_SHIFT).toInt() == BLACK

private fun XposedModule.installFirefoxMaterialYou(cl: ClassLoader): Boolean {
    val bindings =
        resolving { MaterialYouBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    val banner =
        orNullIfMissing {
            val colorKt = cl.loadClass(COMPOSE_COLOR_KT)
            cl.loadClass(PROTECTION_BANNER) to
                colorKt.getDeclaredMethod("Color", Long::class.javaPrimitiveType)
        }
    Logger.info(
        "resolved tweak=$TWEAK members=${bindings.acornTheme.signature()}," +
            "${bindings.applicationOnCreate.signature()} roles=${bindings.schemeColors.size}" +
            " gradients=${bindings.gradients != null} banner=${banner != null}",
    )
    banner?.let { (bannerClass, composeColor) ->
        hookProtectionBanner(bindings, bannerClass, composeColor)
    }
    hook(bindings.applicationOnCreate).intercept { chain ->
        val application = chain.thisObject as? Application ?: return@intercept chain.proceed()
        trackActivities(application)
        runCatching {
            if (switch.enabled) {
                val colors = bindings.readWallpaperColors(application)
                wallpaperColors = colors
                bindings.applyOverlay(application, colors)
            } else {
                removeOverlay(application)
            }
        }.onFailure {
            Logger.error(
                "apply failed tweak=$TWEAK reason=${it.reason()}" +
                    Logger.field("detail", it.message),
                it,
            )
        }
        chain.proceed()
    }
    current = bindings
    hook(bindings.acornTheme).intercept { chain ->
        if (!switch.enabled) return@intercept chain.proceed()
        val colors = wallpaperColors ?: return@intercept chain.proceed()
        val scheme = chain.getArg(bindings.schemeIndex) ?: return@intercept chain.proceed()
        val palette = chain.getArg(bindings.paletteIndex)
        val keepBlack = palette === bindings.oledPalette
        val argb =
            if (palette === bindings.privatePalette) {
                colors.privateMode
            } else {
                val surfaceArgb = (bindings.surface.getLong(scheme) ushr COLOR_SHIFT).toInt()
                colors.forMode(Color.luminance(surfaceArgb) < DARK_LUMINANCE)
            }
        bindings.schemeColors.forEachIndexed { index, schemeColor ->
            if (!keepBlack || !isBlack(schemeColor.field.getLong(scheme))) {
                schemeColor.field.setLong(scheme, packColor(argb[bindings.sourceIndices[index]]))
            }
        }
        val gradients =
            bindings.gradientIndex.takeIf { it >= 0 }?.let { chain.getArg(it) }
                ?: return@intercept chain.proceed()
        chain.proceed(
            chain.args.toTypedArray<Any?>().also {
                it[bindings.gradientIndex] = bindings.recolorGradients(gradients, argb)
            },
        )
    }
    return true
}

private fun XposedModule.hookProtectionBanner(
    bindings: MaterialYouBindings,
    bannerClass: Class<*>,
    composeColor: Method,
) {
    hookClassInitializer(bannerClass).intercept { chain ->
        bannerInitThread = Thread.currentThread()
        try {
            chain.proceed()
        } finally {
            bannerInitThread = null
        }
    }
    hook(composeColor).intercept { chain ->
        if (bannerInitThread !== Thread.currentThread() || !switch.enabled) {
            return@intercept chain.proceed()
        }
        val colors = wallpaperColors ?: return@intercept chain.proceed()
        val stop = bannerStopRoles[chain.getArg(0) as Long] ?: return@intercept chain.proceed()
        val (dark, role) = stop
        val index = bindings.roleIndex(role)
        if (index < 0) return@intercept chain.proceed()
        chain.proceed(arrayOf<Any?>(colors.forMode(dark)[index].toLong() and ARGB_MASK))
    }
}
