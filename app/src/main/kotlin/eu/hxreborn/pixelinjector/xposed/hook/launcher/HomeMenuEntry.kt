package eu.hxreborn.pixelinjector.xposed.hook.launcher

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.detail
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.module
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import kotlin.math.roundToInt

private const val TWEAK = "HomeMenuEntry"
private const val OPTIONS = "com.android.launcher3.popup.WorkspaceLongPressOptions"
private const val OPTIONS_POPUP = "com.android.launcher3.views.OptionsPopupView"
private const val OPTION_ITEM = "$OPTIONS_POPUP\$OptionItem"
private const val SETTINGS_ICON = "ic_setting"
private const val SAMPLE_PX = 48
private const val POPUP_DATA = "com.android.launcher3.popup.PopupData"
private const val STRING_CONTAINER = "com.android.launcher3.popup.ui.StringContainer"
private const val RESOURCE_STRING = "$STRING_CONTAINER\$ResourceString"
private const val LAUNCHER_EVENT = "com.android.launcher3.logging.StatsLogManager\$LauncherEvent"
private const val SETTINGS_LABEL = "settings_button_text"
private const val ENTRY_ID = 0x7e504901
private const val ENTRY_LABEL = "PixelInjector"
private const val ICON_ID = 0x7e504902
private const val LABEL_ID = 0x7e504903
private const val ICON_DP = 24
private const val GLYPH_SCALE = 0.5336f
private const val GLYPH_FILL = 0.92f

private val switch = Switch(Prefs.HOME_MENU_ENTRY)

internal val homeMenuEntry =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.LAUNCHER),
        prefs = listOf(switch),
        install = XposedModule::installHomeMenuEntry,
    )

private sealed interface MenuBindings {
    val member: Method

    fun withEntry(
        context: Context,
        options: List<*>,
    ): List<*>
}

private class SettingsIds(
    context: Context,
) {
    val label = context.resources.getIdentifier(SETTINGS_LABEL, "string", context.packageName)
    val icon = context.resources.getIdentifier(SETTINGS_ICON, "drawable", context.packageName)
}

@Volatile private var cachedSettingsIds: SettingsIds? = null

private fun settingsIds(context: Context): SettingsIds =
    cachedSettingsIds ?: SettingsIds(context).also { cachedSettingsIds = it }

private fun ignoreEvent(cl: ClassLoader): Any =
    cl.loadClass(LAUNCHER_EVENT).enumConstants.first {
        (it as Enum<*>).name ==
            "IGNORE"
    }

private fun Constructor<*>.listWithEntry(
    options: List<*>,
    settings: Int,
    entry: Any?,
): List<*> {
    @Suppress("UNCHECKED_CAST")
    val list = newInstance(options.size + 1) as MutableList<Any?>
    list.addAll(options)
    list.add(if (settings >= 0) settings + 1 else list.size, entry)
    return list
}

private class ComposeMenu(
    override val member: Method,
    val listConstructor: Constructor<*>,
    val popupData: Constructor<*>,
    val args: Array<Any?>,
    val categoryIndex: Int,
    val category: Field,
    val iconResId: Field,
) : MenuBindings {
    override fun withEntry(
        context: Context,
        options: List<*>,
    ): List<*> {
        if (!EntryIcon.servedAsResource(context)) return options
        val settingsIcon = settingsIds(context).icon
        val settings = options.indexOfFirst { it != null && iconResId.getInt(it) == settingsIcon }
        val anchor = options.getOrNull(settings) ?: options.lastOrNull() ?: return options
        val entryArgs = args.copyOf().also { it[categoryIndex] = category.get(anchor) }
        val entry = popupData.newInstance(*entryArgs)

        return listConstructor.listWithEntry(options, settings, entry)
    }

    companion object {
        fun resolve(cl: ClassLoader): ComposeMenu {
            val options = cl.loadClass(OPTIONS)
            val getAll =
                resolving { options.requiredMethod("getAll", Context::class.java) }.getOrElse {
                    // Pixel Launcher 17 CP41.260831.019
                    options.requiredMethod(
                        "getAll",
                        Context::class.java,
                        Boolean::class.javaPrimitiveType,
                    )
                }
            val popupDataClass = cl.loadClass(POPUP_DATA)
            val category = popupDataClass.requiredField("category")
            val eventType = popupDataClass.requiredField("eventId").type
            val actionType = popupDataClass.requiredField("popupAction").type
            val int = Int::class.javaPrimitiveType
            val constructor =
                popupDataClass.declaredConstructors
                    .filter {
                        !it.isSynthetic && actionType in it.parameterTypes &&
                            it.parameterTypes.last() != int
                    }.maxByOrNull { it.parameterCount }
                    ?: throw NoSuchMethodException("PopupData.<init>(...,${actionType.name})")
            val types = constructor.parameterTypes
            val stringContainer = types.firstOrNull { it.name == STRING_CONTAINER }
            val ids = ArrayDeque(listOf(ICON_ID, LABEL_ID))
            if (stringContainer != null) ids.addFirst(ENTRY_ID)
            val args =
                types
                    .map { type ->
                        when (type) {
                            int -> {
                                ids.removeFirst()
                            }

                            stringContainer -> {
                                resourceString(cl, LABEL_ID)
                            }

                            category.type -> {
                                null
                            }

                            eventType -> {
                                ignoreEvent(cl)
                            }

                            String::class.java -> {
                                ""
                            }

                            actionType -> {
                                Proxy.newProxyInstance(
                                    actionType.classLoader,
                                    arrayOf(actionType),
                                    ::onAction,
                                )
                            }

                            // AOSP Launcher3QuickStep GSI DP11.260918.006
                            else -> {
                                if (type.isPrimitive) {
                                    throw NoSuchMethodException(
                                        "PopupData.<init>(...,${type.name})",
                                    )
                                }
                                null
                            }
                        }
                    }.toTypedArray()
            return ComposeMenu(
                member = getAll,
                // LineageOS 24 Launcher3QuickStep CP2A.260605.016
                listConstructor =
                    getAll.returnType
                        .takeUnless { Modifier.isAbstract(it.modifiers) }
                        ?.getDeclaredConstructor(int)
                        ?: ArrayList::class.java.getConstructor(int),
                popupData = constructor.apply { isAccessible = true },
                args = args,
                categoryIndex =
                    types.indexOf(category.type).takeIf { it >= 0 }
                        ?: throw NoSuchMethodException(
                            "PopupData.<init>(...,${category.type.name})",
                        ),
                category = category,
                iconResId = popupDataClass.requiredField("iconResId"),
            )
        }

        private fun resourceString(
            cl: ClassLoader,
            id: Int,
        ): Any =
            cl
                .loadClass(RESOURCE_STRING)
                .getDeclaredConstructor(Int::class.javaPrimitiveType)
                .newInstance(id)
    }
}

private class ViewMenu(
    override val member: Method,
    val optionItem: Constructor<*>,
    val labelRes: Field,
    val label: Field,
    val icon: Field,
    val ignoreEvent: Any,
) : MenuBindings {
    override fun withEntry(
        context: Context,
        options: List<*>,
    ): List<*> {
        val ids = settingsIds(context)
        val settings =
            options.indexOfFirst { item ->
                item != null &&
                    labelRes.getInt(item) == ids.label
            }
        val anchor = options.getOrNull(settings) ?: options.lastOrNull() ?: return options
        val entry =
            optionItem.newInstance(
                context,
                labelRes.getInt(anchor),
                ids.icon,
                ignoreEvent,
                View.OnLongClickListener { openCompanion(it.context) == null },
            )
        label.set(entry, ENTRY_LABEL)
        EntryIcon.bitmap(context)?.let { glyph ->
            val tint = sampleColor(icon.get(anchor) as Drawable?)
            icon.set(entry, BitmapDrawable(context.resources, glyph).apply { tint?.let(::setTint) })
        }
        val list = ArrayList<Any?>(options)
        list.add(if (settings >= 0) settings + 1 else list.size, entry)
        return list
    }

    private fun sampleColor(drawable: Drawable?): Int? {
        drawable ?: return null
        val sample = Bitmap.createBitmap(SAMPLE_PX, SAMPLE_PX, Bitmap.Config.ARGB_8888)
        val copy = drawable.constantState?.newDrawable()?.mutate() ?: drawable
        copy.setBounds(0, 0, SAMPLE_PX, SAMPLE_PX)
        copy.draw(Canvas(sample))
        val pixels =
            IntArray(SAMPLE_PX * SAMPLE_PX).also {
                sample.getPixels(it, 0, SAMPLE_PX, 0, 0, SAMPLE_PX, SAMPLE_PX)
            }
        return pixels.maxByOrNull { it ushr 24 }?.takeIf { it ushr 24 != 0 }?.or(0xFF shl 24)
    }

    companion object {
        fun resolve(cl: ClassLoader): ViewMenu {
            val popup = cl.loadClass(OPTIONS_POPUP)
            val getOptions =
                popup.declaredMethods.firstOrNull {
                    it.name == "getOptions" &&
                        it.parameterCount == 1
                }
                    ?: throw NoSuchMethodException("OptionsPopupView.getOptions(Launcher)")
            val item = cl.loadClass(OPTION_ITEM)
            val constructor =
                item.declaredConstructors.firstOrNull {
                    it.parameterCount == 5 &&
                        it.parameterTypes[0] == Context::class.java
                }
                    ?: throw NoSuchMethodException(
                        "OptionItem.<init>(Context,int,int,EventEnum,OnLongClickListener)",
                    )
            return ViewMenu(
                member = getOptions.apply { isAccessible = true },
                optionItem = constructor.apply { isAccessible = true },
                labelRes = item.requiredField("labelRes"),
                label = item.requiredField("label"),
                icon = item.requiredField("icon"),
                ignoreEvent = ignoreEvent(cl),
            )
        }
    }
}

private object EntryIcon {
    @Volatile private var glyph: Bitmap? = null
    private var hooked = false

    fun bitmap(context: Context): Bitmap? {
        if (glyph == null) {
            glyph =
                runCatching { render(context) }
                    .onFailure {
                        Logger.error(
                            "icon failed tweak=$TWEAK reason=${it.detail()}",
                            it,
                        )
                    }.getOrNull()
        }
        return glyph
    }

    @Synchronized
    fun servedAsResource(context: Context): Boolean {
        if (bitmap(context) == null) return false
        if (!hooked) hookResources()
        return true
    }

    private fun render(context: Context): Bitmap {
        val glyph =
            context.packageManager
                .getResourcesForApplication(BuildConfig.APPLICATION_ID)
                .getDrawable(R.drawable.ic_launcher_foreground, null)
        val size = (ICON_DP * context.resources.displayMetrics.density).roundToInt()
        val canvasSize = (size * GLYPH_FILL / GLYPH_SCALE).roundToInt()
        val offset = (size - canvasSize) / 2
        glyph.setBounds(offset, offset, offset + canvasSize, offset + canvasSize)
        return Bitmap
            .createBitmap(
                size,
                size,
                Bitmap.Config.ARGB_8888,
            ).also { glyph.draw(Canvas(it)) }
    }

    private fun hookResources() {
        val resources = Resources::class.java
        val getValue =
            resources.requiredMethod(
                "getValue",
                Int::class.javaPrimitiveType,
                TypedValue::class.java,
                Boolean::class.javaPrimitiveType,
            )
        val getDrawable =
            resources.requiredMethod(
                "getDrawable",
                Int::class.javaPrimitiveType,
                Resources.Theme::class.java,
            )
        val getText = resources.requiredMethod("getText", Int::class.javaPrimitiveType)
        hooked = true
        module
            .hook(getValue)
            .intercept { chain ->
                if (chain.getArg(0) != ICON_ID) return@intercept chain.proceed()
                (chain.getArg(1) as TypedValue).apply {
                    type = TypedValue.TYPE_STRING
                    string = "res/drawable/pixelinjector.png"
                    resourceId = ICON_ID
                }
                null
            }
        module
            .hook(getDrawable)
            .intercept { chain ->
                val icon = glyph
                if (chain.getArg(0) != ICON_ID || icon == null) return@intercept chain.proceed()
                BitmapDrawable(chain.thisObject as Resources, icon)
            }
        module
            .hook(getText)
            .intercept { chain ->
                if (chain.getArg(0) != LABEL_ID) return@intercept chain.proceed()
                ENTRY_LABEL
            }
        Logger.info(
            "hooked tweak=$TWEAK members=Resources#getValue,Resources#getDrawable,Resources#getText",
        )
    }
}

private fun onAction(
    proxy: Any,
    method: Method,
    args: Array<out Any?>?,
): Any? =
    when (method.name) {
        "equals" -> {
            proxy === args?.firstOrNull()
        }

        "hashCode" -> {
            System.identityHashCode(proxy)
        }

        "toString" -> {
            TWEAK
        }

        else -> {
            openCompanion(
                args?.firstNotNullOfOrNull { (it as? View)?.context ?: it as? Context },
            )
        }
    }

private fun openCompanion(context: Context?): Any? {
    context ?: return null.also { Logger.error("open failed tweak=$TWEAK reason=no-context") }
    runCatching {
        val intent =
            context.packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)
                ?: error("no launch intent")
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Logger.info("opened tweak=$TWEAK")
    }.onFailure { Logger.error("open failed tweak=$TWEAK reason=${it.detail()}", it) }
    return null
}

private fun XposedModule.installHomeMenuEntry(cl: ClassLoader): Boolean {
    val bindings =
        resolving<MenuBindings> { ComposeMenu.resolve(cl) }
            .recoverCatching {
                if (it is ClassNotFoundException) {
                    ViewMenu.resolve(
                        cl,
                    )
                } else {
                    throw it
                }
            }.getOrElse {
                Logger.error(
                    "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
                )
                return false
            }
    Logger.info("resolved tweak=$TWEAK members=${bindings.member.signature()}")
    hook(bindings.member).intercept { chain ->
        val options = chain.proceed()
        if (!switch.enabled || options !is List<*>) return@intercept options
        val context = chain.getArg(0) as? Context ?: return@intercept options

        try {
            bindings.withEntry(context, options)
        } catch (e: Exception) {
            Logger.error("entry failed tweak=$TWEAK reason=${e.detail()}", e)
            options
        }
    }
    return true
}
