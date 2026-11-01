package eu.hxreborn.pixelinjector.xposed.hook.firefox

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.ScaleAnimation
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.orNullIfMissing
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.requiredMethods
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.hook.proceedReplacing
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

private const val TWEAK = "FirefoxClassicMenu"
private const val MENU_PACKAGE = "org.mozilla.fenix.components.menu"
private const val MENU_COMPOSE = "$MENU_PACKAGE.compose"
private const val BOTTOM_SHEET_BEHAVIOR =
    "com.google.android.material.bottomsheet.BottomSheetBehavior"
private const val COORDINATOR_PARAMS =
    "androidx.coordinatorlayout.widget.CoordinatorLayout\$LayoutParams"
private const val FENIX_CONTEXT = "org.mozilla.fenix.ext.ContextKt"
private const val FENIX_COMPONENTS = "org.mozilla.fenix.components.Components"
private const val FENIX_SETTINGS = "org.mozilla.fenix.utils.Settings"
private const val COMPOSER = "androidx.compose.runtime.Composer"
private const val GAP_COMPOSER = "androidx.compose.runtime.GapComposer"
private const val SLOT_READER = "androidx.compose.runtime.composer.gapbuffer.SlotReader"
private const val SLOT_WRITER = "androidx.compose.runtime.composer.gapbuffer.SlotWriter"
private const val RECOMPOSE_SCOPE = "androidx.compose.runtime.RecomposeScopeImpl"
private const val COLOR_SCHEME = "androidx.compose.material3.ColorScheme"
private const val COLOR_SCHEME_KT = "androidx.compose.material3.ColorSchemeKt"
private const val SPACED_ALIGNED = "androidx.compose.foundation.layout.Arrangement\$SpacedAligned"
private const val BACKGROUND_KT = "androidx.compose.foundation.BackgroundKt"
private const val SURFACE_KT = "androidx.compose.material3.SurfaceKt"
private const val DIVIDER_KT = "androidx.compose.material3.DividerKt"
private const val LIST_ITEM_KT = "org.mozilla.fenix.compose.list.ListItemKt"
private const val MODIFIER = "androidx.compose.ui.Modifier"
private const val SIZE_KT = "androidx.compose.foundation.layout.SizeKt"
private const val PADDING_KT = "androidx.compose.foundation.layout.PaddingKt"
private const val CLIP_KT = "androidx.compose.ui.draw.ClipKt"
private const val SHAPE = "androidx.compose.ui.graphics.Shape"
private const val ROUNDED_CORNER_SHAPE_KT = "androidx.compose.foundation.shape.RoundedCornerShapeKt"
private const val RECTANGLE_SHAPE_KT = "androidx.compose.ui.graphics.RectangleShapeKt"
private const val TEXT_KT = "androidx.compose.material3.TextKt"
private const val TEXT_STYLE = "androidx.compose.ui.text.TextStyle"
private const val ACORN_TYPOGRAPHY_KT = "mozilla.components.compose.base.theme.AcornTypographyKt"
private const val ICON_KT = "androidx.compose.material3.IconKt"
private const val CHECKBOX_KT = "androidx.compose.material3.CheckboxKt"
private const val CHECKBOX_COLORS = "androidx.compose.material3.CheckboxColors"
private const val COMPOSE_COLOR = "androidx.compose.ui.graphics.Color"
private const val PAINTER = "androidx.compose.ui.graphics.painter.Painter"
private const val PAINTER_RESOURCES = "androidx.compose.ui.res.PainterResources_androidKt"
private const val STRING_RESOURCES = "androidx.compose.ui.res.StringResources_androidKt"
private const val COMPOSITION_LOCALS =
    "androidx.compose.ui.platform.AndroidCompositionLocals_androidKt"
private const val FUNCTION0 = "kotlin.jvm.functions.Function0"
private const val FUNCTION2 = "kotlin.jvm.functions.Function2"
private const val KOTLIN_UNIT = "kotlin.Unit"
private const val MENU_WIDTH_DP = 270f
private const val CORNER_DP = 24f
private const val MARGIN_DP = 6f
private const val BOTTOM_GAP_DP = 13f
private const val ROW_HEIGHT_DP = 48f
private const val STOCK_ACCOUNT_ROW_HEIGHT_DP = 56f
private const val NAV_BUTTON_DP = 40f
private const val NAV_BUTTON_ICON_INSET_DP = 8f
private const val STOCK_NAV_ROW_PADDING_DP = 4f
private const val ICON_ROW_PADDING_DP = 8f
private const val NAV_SPACING_DP = 4f
private const val STOCK_ICON_LABEL_GAP_DP = 16f
private const val ICON_LABEL_GAP_DP = 12f
private const val SINGLE_LINE = 1
private const val DIVIDER_INSET_DP = 16f
private const val DIVIDER_PADDING_DP = 5f
private const val DIVIDER_THICKNESS_DEFAULT_MASK = 2
private const val CHECKBOX_MODIFIER_ENABLED_DEFAULT_MASK = 0xC
private const val PARAMS_PER_CHANGED_INT = 10
private const val PARAMS_PER_DEFAULT_INT = 31
private const val MENU_ITEM_FIRST_DEFAULT_BIT = 2
private const val ACTIVE_STATE = "ACTIVE"
private const val ENABLED_STATE = "ENABLED"
private const val DISABLED_STATE = "DISABLED"
private const val LIBRARY_ROWS_GROUP_KEY = 0x4C494252
private const val MENU_FRAME_CONTENT_MODIFIER = 1
private const val WRAP_CONTENT_DEFAULTS_MASK = 3
private const val DISABLED_CONTAINER_ALPHA = 0x1F
private const val COLOR_SHIFT = 32
private const val ALPHA_SHIFT = 24
private const val RGB_MASK = 0xFFFFFFL
private const val GROUP_SPACING_DP = 2f
private const val FRAME_SPACING_DP = 12f
private const val ENTER_MS = 200L
private const val RESTART_CALL_BIT = 1
private const val BACKGROUND_COLOR = 1
private const val BACKGROUND_SHAPE = 2
private const val MIN_HEIGHT = 2
private const val SURFACE_COLOR = 2
private const val LIST_ITEM_DESCRIPTION = 5
private const val LIST_ITEM_HEIGHT = 8
private const val LIST_ITEM_CONTENT_MAX_LINES = 5
private val stockRowHeights = setOf(56f, 52f)

private val menuFragments = listOf("MenuDialogFragment", "MenuFragment")

private val optionalMenuRows =
    listOf(
        "MenuItemKt" to "MenuTextItem",
        "LibraryMenuItemKt" to "LibraryMenuItem",
        "IPProtectionMenuItemKt" to "IPProtectionMenuItem",
        "ExtensionsMenuItemKt" to "NumberedChevronBadge",
    )

private class LibraryRow(
    val icon: Int,
    val label: Int,
    val onClick: Any,
)

private val switch = Switch(Prefs.FIREFOX_CLASSIC_MENU)

internal val firefoxClassicMenu =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.FIREFOX),
        prefs = listOf(switch),
        install = XposedModule::installFirefoxClassicMenu,
    )

private enum class MenuScope { FRAME, HEADER, GROUP, ROW, NAV, ACCOUNT }

private val menuScope = ThreadLocal<MenuScope?>()

private val fragmentCreatingDialog = ThreadLocal<Any?>()

private class SheetSurface(
    val stock: Long,
    val bright: Long,
    val chevronContainer: Long,
)

private val sheetSurface = ThreadLocal<SheetSurface?>()

private val awaitingFirstGroup = ThreadLocal<Boolean>()

private val badgeAsCheckbox = ThreadLocal<Boolean?>()

private val libraryCapture = ThreadLocal<MutableList<LibraryRow>?>()

private val libraryRowsByComposer = WeakHashMap<Any, List<LibraryRow>>()

private const val LIBRARY_ROWS = "LibraryRows"
private const val GROUP_DIVIDER = "GroupDivider"
private const val CHECKBOX = "Checkbox"
private const val MAX_REWOUND_GROUPS = 4096

private val failedEmissions = ConcurrentHashMap.newKeySet<String>()

private class RoundOutline(
    private val radius: Float,
) : ViewOutlineProvider() {
    override fun getOutline(
        view: View,
        outline: Outline,
    ) = outline.setRoundRect(0, 0, view.width, view.height, radius)
}

private class MenuFragment(
    val onCreateDialog: Method,
    val sheetWidth: Method,
    val behavior: Field,
)

private fun Class<*>.firstDeclared(
    description: String,
    predicate: (Method) -> Boolean,
): Method = declaredMethods.firstOrNull(predicate) ?: throw NoSuchMethodException(description)

private fun Class<*>.resourceComposable(
    name: String,
    returns: Class<*>,
    composer: Class<*>,
): Method =
    requiredMethods(name).singleOrNull { method ->
        val types = method.parameterTypes
        method.returnType == returns &&
            types.firstOrNull() == Int::class.javaPrimitiveType &&
            types.count { it == composer } == 1 &&
            types.all { it == composer || it == Int::class.javaPrimitiveType }
    } ?: throw NoSuchMethodException("$simpleName.$name(int,Composer)")

private fun invokeResource(
    method: Method,
    id: Int,
    composer: Any?,
): Any? {
    val args = method.parameterTypes.map(::zeroOf).toTypedArray()
    args[0] = id
    args[method.parameterTypes.indexOfFirst { !it.isPrimitive }] = composer
    return method.invoke(null, *args)
}

private fun Method.takes(
    index: Int,
    type: Class<*>?,
) = parameterTypes.getOrNull(index) == type

private class WindowBindings(
    val fragments: List<MenuFragment>,
    val setOnShowListener: Method,
    val viewRef: Field,
    val maxHeight: Field,
    val draggable: Field,
    val setHideable: Method,
    val gravity: Field,
    val components: Method,
    val settings: Method,
    val shouldUseBottomToolbar: Method,
) {
    fun isBottomToolbar(context: Context): Boolean =
        shouldUseBottomToolbar.invoke(settings.invoke(components.invoke(null, context))) as Boolean

    companion object {
        fun resolve(cl: ClassLoader): WindowBindings {
            val behavior = cl.loadClass(BOTTOM_SHEET_BEHAVIOR)
            val fragments =
                menuFragments.mapNotNull { name ->
                    orNullIfMissing { menuFragment(cl.loadClass("$MENU_PACKAGE.$name")) }
                }
            if (fragments.isEmpty()) {
                throw ClassNotFoundException("$MENU_PACKAGE.MenuDialogFragment")
            }
            val showListener = DialogInterface.OnShowListener::class.java
            val settings = cl.loadClass(FENIX_SETTINGS)
            return WindowBindings(
                fragments = fragments,
                setOnShowListener =
                    Dialog::class.java.requiredMethod("setOnShowListener", showListener),
                viewRef = behavior.requiredField("viewRef"),
                maxHeight = behavior.requiredField("maxHeight"),
                draggable = behavior.requiredField("draggable"),
                setHideable =
                    behavior.requiredMethod("setHideable", Boolean::class.javaPrimitiveType),
                gravity = cl.loadClass(COORDINATOR_PARAMS).getField("gravity"),
                components =
                    cl
                        .loadClass(FENIX_CONTEXT)
                        .requiredMethod("getComponents", Context::class.java),
                settings = cl.loadClass(FENIX_COMPONENTS).requiredMethod("getSettings"),
                shouldUseBottomToolbar = settings.requiredMethod("getShouldUseBottomToolbar"),
            )
        }

        private fun menuFragment(fragment: Class<*>) =
            MenuFragment(
                onCreateDialog = fragment.requiredMethod("onCreateDialog", Bundle::class.java),
                sheetWidth =
                    fragment.firstDeclared("${fragment.simpleName}.calculateMenuSheetWidth") {
                        it.name.startsWith("calculateMenuSheetWidth") &&
                            it.parameterCount == 0 &&
                            it.returnType == Int::class.javaPrimitiveType
                    },
                behavior = fragment.requiredField("bottomSheetBehavior"),
            )
    }
}

private data class ComposerMarker(
    val inserting: Boolean,
    val parent: Int,
    val openScopes: Int,
)

private class ComposerRewind(
    private val composerClass: Class<*>,
    private val inserting: Field,
    private val reader: Field,
    private val writer: Field,
    private val invalidateStack: Field,
    private val end: Method,
    private val invalidateScope: Method,
    private val readerParent: Field,
    private val readerIsNode: Method,
    private val writerParent: Field,
    private val writerIsNode: Method,
) {
    fun markerOf(composer: Any?): ComposerMarker? {
        if (!composerClass.isInstance(composer)) return null
        val inserting = inserting.getBoolean(composer)
        val parent =
            if (inserting) {
                writerParent.getInt(writer.get(composer))
            } else {
                readerParent.getInt(reader.get(composer))
            }
        return ComposerMarker(inserting, parent, scopes(composer).size)
    }

    fun rewind(
        composer: Any,
        marker: ComposerMarker,
    ) {
        var closed = 0

        fun closeWhile(
            slots: Field,
            parent: Field,
            isNode: Method,
            open: (Int) -> Boolean,
        ) {
            while (true) {
                val table = slots.get(composer)
                val group = parent.getInt(table)
                if (!open(group)) return
                check(++closed <= MAX_REWOUND_GROUPS) { "rewind did not reach $marker" }
                end.invoke(composer, isNode.invoke(table, group))
            }
        }
        if (marker.inserting) {
            closeWhile(writer, writerParent, writerIsNode) { it > marker.parent }
        } else {
            closeWhile(writer, writerParent, writerIsNode) { inserting.getBoolean(composer) }
            closeWhile(reader, readerParent, readerIsNode) { it > marker.parent }
        }
        val scopes = scopes(composer)
        while (scopes.size > marker.openScopes) scopes.removeAt(scopes.lastIndex)
        scopes.lastOrNull()?.let { invalidateScope.invoke(it) }
    }

    private fun scopes(composer: Any?) = invalidateStack.get(composer) as ArrayList<*>

    companion object {
        fun resolve(cl: ClassLoader): ComposerRewind {
            val composer = cl.loadClass(GAP_COMPOSER)
            val reader = cl.loadClass(SLOT_READER)
            val writer = cl.loadClass(SLOT_WRITER)
            val int = Int::class.javaPrimitiveType
            return ComposerRewind(
                composerClass = composer,
                inserting = composer.requiredField("inserting"),
                reader = composer.requiredField("reader"),
                writer = composer.requiredField("writer"),
                invalidateStack = composer.requiredField("invalidateStack"),
                end = composer.requiredMethod("end", Boolean::class.javaPrimitiveType),
                invalidateScope = cl.loadClass(RECOMPOSE_SCOPE).requiredMethod("invalidate"),
                readerParent = reader.requiredField("parent"),
                readerIsNode = reader.requiredMethod("isNode", int),
                writerParent = writer.requiredField("parent"),
                writerIsNode = writer.requiredMethod("isNode", int),
            )
        }
    }
}

private fun ceilDiv(
    value: Int,
    divisor: Int,
) = (value + divisor - 1) / divisor

private class MenuItemCall(
    val method: Method,
    val labelAt: Int,
    val iconAt: Int,
    val onClickAt: Int,
    val stateAt: Int,
    val afterContentAt: Int,
    val composerAt: Int,
) {
    @Volatile
    var plainDefaults: Int? = null
        private set

    fun learnDefaults(defaults: Int) {
        if (plainDefaults != null || defaults <= 0) return
        val highest = Int.SIZE_BITS - 1 - Integer.numberOfLeadingZeros(defaults)
        val explicit = (MENU_ITEM_FIRST_DEFAULT_BIT..highest).count { defaults and (1 shl it) == 0 }
        if (explicit != 1) return
        plainDefaults = defaults
        Logger.info("learned tweak=$TWEAK member=MenuItem defaults=0x${defaults.toString(16)}")
    }

    fun emit(
        defaults: Int,
        label: String,
        icon: Any?,
        onClick: Any,
        composer: Any?,
    ) {
        val args = method.parameterTypes.map(::zeroOf).toTypedArray()
        args[labelAt] = label
        args[iconAt] = icon
        args[onClickAt] = onClick
        args[composerAt] = composer
        args[args.lastIndex] = defaults
        method.invoke(null, *args)
    }

    companion object {
        fun resolve(
            method: Method,
            composer: Class<*>,
            painter: Class<*>,
            function0: Class<*>,
            function2: Class<*>,
            state: Class<*>,
        ): MenuItemCall {
            val types = method.parameterTypes
            val composerAt = types.indexOf(composer)
            val trailing = types.drop(composerAt + 1)
            val expectedTrailing = ceilDiv(composerAt, PARAMS_PER_CHANGED_INT) + 1
            if (composerAt < 0 || composerAt > PARAMS_PER_DEFAULT_INT ||
                trailing.size != expectedTrailing ||
                trailing.any { it != Int::class.javaPrimitiveType }
            ) {
                throw NoSuchMethodException("MenuItemKt.MenuItem(params=${types.size})")
            }
            val params = types.take(composerAt)

            fun indexOf(type: Class<*>) =
                params.indexOf(type).takeIf { it >= 0 }
                    ?: throw NoSuchMethodException("MenuItemKt.MenuItem(${type.simpleName})")
            return MenuItemCall(
                method = method,
                labelAt = indexOf(String::class.java),
                iconAt = indexOf(painter),
                onClickAt = indexOf(function0),
                stateAt = indexOf(state),
                afterContentAt =
                    params.lastIndexOf(function2).takeIf { it >= 0 }
                        ?: throw NoSuchMethodException("MenuItemKt.MenuItem(Function2)"),
                composerAt = composerAt,
            )
        }
    }
}

private class ComposeBindings(
    val rewind: ComposerRewind?,
    val menuItem: MenuItemCall,
    val menuItemEnabled: Any,
    val rows: List<Method>,
    val toolsGroup: Method,
    val libraryGroup: Method,
    val libraryItem: Method,
    val libraryItemOnClickAt: Int,
    val localContext: Any,
    val badge: Method,
    val badgeStateAt: Int,
    val expandable: Method,
    val account: Method?,
    val text: Method,
    val captionStyle: Any,
    val width: Method,
    val defaultMinSize: Method,
    val rectangleShape: Any,
    val menuGroup: Method,
    val menuFrame: Method,
    val sheetHandle: Method,
    val sheet: Method,
    val navigation: Method,
    val navItem: Method,
    val navItemStateAt: Int,
    val frameHeaderAt: Int,
    val frameFooterAt: Int,
    val function2: Class<*>,
    val invokeComposable: Method,
    val unit: Any,
    val iconListItem: Method,
    val listItemContent: Method,
    val icon: Method,
    val checkbox: Method,
    val checkboxColors: Constructor<*>,
    val transparent: Long,
    val painterResource: Method,
    val stringResource: Method,
    val startReplaceGroup: Method,
    val endReplaceGroup: Method,
    val background: Method,
    val surfaceComposable: Method,
    val spacedAligned: Constructor<*>,
    val divider: Method,
    val consume: Method,
    val localColorScheme: Any,
    val surfaceColor: Field,
    val surfaceBrightColor: Field,
    val surfaceContainerColor: Field,
    val surfaceContainerHighColor: Field,
    val outlineVariantColor: Field,
    val onSurfaceColor: Field,
    val onSurfaceVariantColor: Field,
    val primaryColor: Field,
    val modifierCompanion: Any,
    val wrapContentWidth: Method,
    val size: Method,
    val paddingSymmetric: Method,
    val clip: Method,
    val circleShape: Any,
) {
    fun composerIndex(method: Method) = method.parameterTypes.indexOf(consume.declaringClass)

    companion object {
        fun resolve(cl: ClassLoader): ComposeBindings {
            val composer = cl.loadClass(COMPOSER)
            val colorScheme = cl.loadClass(COLOR_SCHEME)

            fun menuComposable(
                owner: String,
                name: String,
            ) = cl.loadClass("$MENU_COMPOSE.$owner").requiredMethods(name).single()

            fun Method.indexOfParam(type: Class<*>) =
                parameterTypes.indexOf(type).takeIf { it >= 0 }
                    ?: throw NoSuchMethodException(
                        "${declaringClass.simpleName}.$name(${type.simpleName})",
                    )
            val painter = cl.loadClass(PAINTER)
            val function0 = cl.loadClass(FUNCTION0)
            val function2 = cl.loadClass(FUNCTION2)
            val menuItemState = cl.loadClass("$MENU_COMPOSE.MenuItemState")
            val menuItemMethod = menuComposable("MenuItemKt", "MenuItem")
            val menuItem =
                MenuItemCall.resolve(
                    menuItemMethod,
                    composer,
                    painter,
                    function0,
                    function2,
                    menuItemState,
                )
            val libraryItem = menuComposable("LibraryMenuItemKt", "LibraryMenuItem")
            val badge = menuComposable("MenuItemKt", "Badge")
            val navItem = menuComposable("MenuNavigationKt", "MenuNavItem")
            val menuFrame = menuComposable("MenuScaffoldKt", "MenuFrame")
            val frameSlots =
                menuFrame.parameterTypes.indices
                    .filter { menuFrame.parameterTypes[it] == function2 }
                    .takeIf { it.size == 2 }
                    ?: throw NoSuchMethodException("MenuScaffoldKt.MenuFrame(Function2)")
            val textStyle = cl.loadClass(TEXT_STYLE)
            val composeColor = cl.loadClass(COMPOSE_COLOR)
            return ComposeBindings(
                rewind = orNullIfMissing { ComposerRewind.resolve(cl) },
                menuItem = menuItem,
                menuItemEnabled =
                    menuItemState.enumConstants?.singleOrNull {
                        (it as Enum<*>).name == ENABLED_STATE
                    } ?: throw NoSuchFieldException("MenuItemState.$ENABLED_STATE"),
                rows =
                    listOf(menuItemMethod) +
                        optionalMenuRows.mapNotNull { (owner, name) ->
                            orNullIfMissing { menuComposable(owner, name) }
                        },
                toolsGroup = menuComposable("MainMenuKt", "ToolsAndActionsMenuGroup"),
                libraryGroup = menuComposable("MainMenuKt", "LibraryMenuGroup"),
                libraryItem = libraryItem,
                libraryItemOnClickAt =
                    libraryItem.parameterTypes.indexOf(function0).takeIf {
                        it >= 0 && it == libraryItem.parameterTypes.lastIndexOf(function0)
                    } ?: throw NoSuchMethodException(
                        "LibraryMenuItemKt.LibraryMenuItem(Function0)",
                    ),
                localContext =
                    cl.loadClass(COMPOSITION_LOCALS).requiredField("LocalContext").get(null)
                        ?: throw NoSuchFieldException("AndroidCompositionLocals.LocalContext"),
                badge = badge,
                badgeStateAt = badge.indexOfParam(menuItemState),
                expandable = menuComposable("MenuItemKt", "ExpandableMenuItemAnimation"),
                account =
                    orNullIfMissing {
                        menuComposable("header.MozillaAccountMenuItemKt", "MozillaAccountMenuItem")
                    },
                text =
                    cl.loadClass(TEXT_KT).firstDeclared("TextKt.Text") {
                        it.name.startsWith("Text-") && it.takes(0, String::class.java) &&
                            textStyle in it.parameterTypes
                    },
                captionStyle =
                    cl
                        .loadClass(ACORN_TYPOGRAPHY_KT)
                        .requiredField("defaultTypography")
                        .get(null)
                        .let { it?.javaClass?.requiredField("caption")?.get(it) }
                        ?: throw NoSuchFieldException("AcornTypography.caption"),
                width =
                    cl.loadClass(SIZE_KT).firstDeclared("SizeKt.width") {
                        it.name.startsWith("width-") && it.parameterCount == 2
                    },
                defaultMinSize =
                    cl.loadClass(SIZE_KT).firstDeclared("SizeKt.defaultMinSize") {
                        it.name.startsWith("defaultMinSize-") && it.parameterCount == 3
                    },
                rectangleShape =
                    cl.loadClass(RECTANGLE_SHAPE_KT).requiredField("RectangleShape").get(null)
                        ?: throw NoSuchFieldException("RectangleShapeKt.RectangleShape"),
                menuGroup = menuComposable("MenuGroupKt", "MenuGroup"),
                menuFrame = menuFrame,
                sheetHandle = menuComposable("MenuDialogBottomSheetKt", "MenuBottomSheetHandle"),
                sheet = menuComposable("MenuDialogBottomSheetKt", "MenuDialogBottomSheet"),
                navigation = menuComposable("MenuNavigationKt", "MenuNavigation"),
                navItem = navItem,
                navItemStateAt = navItem.indexOfParam(menuItemState),
                frameHeaderAt = frameSlots[0],
                frameFooterAt = frameSlots[1],
                function2 = function2,
                invokeComposable =
                    function2.requiredMethod("invoke", Any::class.java, Any::class.java),
                unit =
                    cl.loadClass(KOTLIN_UNIT).requiredField("INSTANCE").get(null)
                        ?: throw NoSuchFieldException("Unit.INSTANCE"),
                iconListItem =
                    cl.loadClass(LIST_ITEM_KT).firstDeclared("ListItemKt.IconListItem") {
                        it.name.startsWith("IconListItem-") &&
                            it.takes(LIST_ITEM_DESCRIPTION, String::class.java) &&
                            it.takes(LIST_ITEM_HEIGHT, Float::class.javaPrimitiveType)
                    },
                listItemContent =
                    cl.loadClass(LIST_ITEM_KT).firstDeclared("ListItemKt.ListItemContent") {
                        it.name.startsWith("ListItemContent-") &&
                            it.takes(LIST_ITEM_CONTENT_MAX_LINES, Int::class.javaPrimitiveType)
                    },
                icon =
                    cl.loadClass(ICON_KT).firstDeclared("IconKt.Icon") {
                        it.name.startsWith("Icon-") && painter in it.parameterTypes &&
                            Long::class.javaPrimitiveType in it.parameterTypes
                    },
                checkbox = cl.loadClass(CHECKBOX_KT).requiredMethods("Checkbox").single(),
                checkboxColors =
                    cl.loadClass(CHECKBOX_COLORS).declaredConstructors.singleOrNull {
                        it.parameterTypes.all { type -> type == Long::class.javaPrimitiveType }
                    } ?: throw NoSuchMethodException("CheckboxColors.<init>"),
                transparent = composeColor.requiredField("Transparent").getLong(null),
                painterResource =
                    cl
                        .loadClass(
                            PAINTER_RESOURCES,
                        ).resourceComposable("painterResource", painter, composer),
                stringResource =
                    cl
                        .loadClass(STRING_RESOURCES)
                        .resourceComposable("stringResource", String::class.java, composer),
                startReplaceGroup =
                    composer.requiredMethod("startReplaceGroup", Int::class.javaPrimitiveType),
                endReplaceGroup = composer.requiredMethod("endReplaceGroup"),
                background =
                    cl.loadClass(BACKGROUND_KT).firstDeclared("BackgroundKt.background") {
                        it.name.startsWith("background-") &&
                            it.takes(BACKGROUND_COLOR, Long::class.javaPrimitiveType)
                    },
                surfaceComposable =
                    cl.loadClass(SURFACE_KT).firstDeclared("SurfaceKt.Surface") {
                        it.name.startsWith("Surface-") &&
                            it.takes(SURFACE_COLOR, Long::class.javaPrimitiveType)
                    },
                spacedAligned =
                    cl.loadClass(SPACED_ALIGNED).declaredConstructors.firstOrNull {
                        it.parameterCount == 3 &&
                            it.parameterTypes[0] == Float::class.javaPrimitiveType
                    } ?: throw NoSuchMethodException("Arrangement.SpacedAligned.<init>"),
                divider =
                    cl.loadClass(DIVIDER_KT).firstDeclared("DividerKt.HorizontalDivider") {
                        it.name.startsWith("HorizontalDivider-") && composer in it.parameterTypes
                    },
                consume =
                    composer.firstDeclared("Composer.consume") {
                        it.name == "consume" && it.parameterCount == 1
                    },
                localColorScheme =
                    cl.loadClass(COLOR_SCHEME_KT).requiredField("LocalColorScheme").get(null)
                        ?: throw NoSuchFieldException("ColorSchemeKt.LocalColorScheme"),
                surfaceColor = colorScheme.requiredField("surface"),
                surfaceBrightColor = colorScheme.requiredField("surfaceBright"),
                surfaceContainerColor = colorScheme.requiredField("surfaceContainer"),
                surfaceContainerHighColor = colorScheme.requiredField("surfaceContainerHigh"),
                outlineVariantColor = colorScheme.requiredField("outlineVariant"),
                onSurfaceColor = colorScheme.requiredField("onSurface"),
                onSurfaceVariantColor = colorScheme.requiredField("onSurfaceVariant"),
                primaryColor = colorScheme.requiredField("primary"),
                modifierCompanion =
                    cl.loadClass("$MODIFIER\$Companion").requiredField("\$\$INSTANCE").get(null)
                        ?: throw NoSuchFieldException("Modifier.Companion"),
                wrapContentWidth =
                    cl.loadClass(SIZE_KT).firstDeclared("SizeKt.wrapContentWidth") {
                        it.name.startsWith("wrapContentWidth") && it.parameterCount == 2 &&
                            it.takes(1, Int::class.javaPrimitiveType)
                    },
                size =
                    cl.loadClass(SIZE_KT).firstDeclared("SizeKt.size") {
                        it.name.startsWith("size-") && it.parameterCount == 2 &&
                            it.takes(1, Float::class.javaPrimitiveType)
                    },
                paddingSymmetric =
                    cl.loadClass(PADDING_KT).firstDeclared("PaddingKt.padding") {
                        it.name.startsWith("padding-") && it.parameterCount == 3 &&
                            it.takes(2, Float::class.javaPrimitiveType)
                    },
                clip =
                    cl.loadClass(CLIP_KT).requiredMethod(
                        "clip",
                        cl.loadClass(MODIFIER),
                        cl.loadClass(SHAPE),
                    ),
                circleShape =
                    cl.loadClass(ROUNDED_CORNER_SHAPE_KT).requiredField("CircleShape").get(null)
                        ?: throw NoSuchFieldException("RoundedCornerShapeKt.CircleShape"),
            )
        }
    }
}

private fun View.dp(value: Float) = value * resources.displayMetrics.density

private fun WindowBindings.showAsPopup(
    fragment: Any,
    dialog: Dialog,
) {
    val window = dialog.window ?: return skipPopup("no-window")
    val behaviorField =
        fragments.firstOrNull { it.behavior.declaringClass.isInstance(fragment) }?.behavior
            ?: return skipPopup("no-fragment")
    val behavior = behaviorField.get(fragment) ?: return skipPopup("no-behavior")
    val sheet =
        (viewRef.get(behavior) as? WeakReference<*>)?.get() as? View
            ?: return skipPopup("no-sheet")
    val bottomToolbar = isBottomToolbar(dialog.context)
    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    if ((window.decorView.background as? ColorDrawable)?.color != Color.TRANSPARENT) {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
    setHideable.invoke(behavior, false)
    draggable.setBoolean(behavior, false)
    sheet.animate().cancel()
    sheet.outlineProvider = RoundOutline(sheet.dp(CORNER_DP))
    sheet.clipToOutline = true
    sheet.elevation = 0f
    sheet.setOnApplyWindowInsetsListener { view, insets ->
        view.setPadding(0, 0, 0, 0)
        insets
    }
    (sheet.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
        if (gravity.declaringClass.isInstance(params)) {
            gravity.setInt(params, Gravity.TOP or Gravity.END)
        }
        params.marginEnd = sheet.dp(MARGIN_DP).toInt()
        sheet.layoutParams = params
    }
    sheet.viewTreeObserver.addOnPreDrawListener {
        positionSheet(sheet, behavior, bottomToolbar)
        true
    }
    sheet.startAnimation(enterAnimation(bottomToolbar))
    Logger.info("menu shown tweak=$TWEAK bottomToolbar=$bottomToolbar")
}

private fun skipPopup(reason: String) = Logger.warn("popup skipped tweak=$TWEAK reason=$reason")

private fun WindowBindings.positionSheet(
    sheet: View,
    behavior: Any,
    bottomToolbar: Boolean,
) {
    val parentHeight = (sheet.parent as? View)?.height?.takeIf { it > 0 } ?: return
    val insets = sheet.rootWindowInsets
    val statusBar = insets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0
    val navigationBar = insets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
    val margin = sheet.dp(MARGIN_DP)
    val bottomGap = if (bottomToolbar) sheet.dp(BOTTOM_GAP_DP) else navigationBar + margin
    val height = parentHeight - statusBar - (margin + bottomGap).toInt()
    if (maxHeight.getInt(behavior) != height) {
        maxHeight.setInt(behavior, height)
        sheet.requestLayout()
    }
    val translation = if (bottomToolbar) -bottomGap else statusBar + margin - sheet.top
    if (sheet.translationY != translation) sheet.translationY = translation
}

private fun enterAnimation(bottomToolbar: Boolean) =
    AnimationSet(true).apply {
        val pivotY = if (bottomToolbar) 1f else 0f
        val relative = ScaleAnimation.RELATIVE_TO_SELF
        addAnimation(ScaleAnimation(0f, 1f, 0f, 1f, relative, 1f, relative, pivotY))
        addAnimation(AlphaAnimation(0f, 1f))
        interpolator = DecelerateInterpolator()
        duration = ENTER_MS
    }

private fun XposedModule.installFirefoxClassicMenu(cl: ClassLoader): Boolean {
    val bindings =
        resolving { WindowBindings.resolve(cl) to ComposeBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    val (window, compose) = bindings
    val fragments =
        window.fragments.joinToString(",") { it.onCreateDialog.declaringClass.simpleName }
    Logger.info(
        "resolved tweak=$TWEAK members=$fragments,${compose.iconListItem.name} rewind=${compose.rewind != null}",
    )
    hookMenuWindow(window)
    hookMenuRows(compose)
    return true
}

private fun XposedModule.hookMenuWindow(window: WindowBindings) {
    window.fragments.forEach { fragment ->
        hook(fragment.onCreateDialog).intercept { chain ->
            if (!switch.enabled) return@intercept chain.proceed()
            fragmentCreatingDialog.set(chain.thisObject)
            val dialog =
                try {
                    chain.proceed()
                } finally {
                    fragmentCreatingDialog.remove()
                }
            (dialog as? Dialog)?.window?.setWindowAnimations(0)
            dialog
        }
        hook(fragment.sheetWidth).intercept { chain ->
            val stock = chain.proceed() as Int
            if (!switch.enabled) return@intercept stock
            val density = Resources.getSystem().displayMetrics.density
            minOf((MENU_WIDTH_DP * density).toInt(), stock)
        }
    }
    hook(window.setOnShowListener).intercept { chain ->
        val fragment = fragmentCreatingDialog.get() ?: return@intercept chain.proceed()
        val stock = chain.getArg(0) as? DialogInterface.OnShowListener
        val popup =
            DialogInterface.OnShowListener { shown ->
                stock?.onShow(shown)
                try {
                    window.showAsPopup(fragment, shown as Dialog)
                } catch (e: Exception) {
                    Logger.error("apply failed tweak=$TWEAK reason=${e.unwrapped().summary()}", e)
                } catch (e: LinkageError) {
                    Logger.error("apply failed tweak=$TWEAK reason=${e.unwrapped().summary()}", e)
                }
            }
        chain.proceed(arrayOf<Any?>(popup))
    }
}

private fun <T> within(
    scope: MenuScope,
    block: () -> T,
): T {
    val outer = menuScope.get()
    menuScope.set(scope)
    try {
        return block()
    } finally {
        menuScope.set(outer)
    }
}

private fun brightSurfaceFor(color: Any?): Long? {
    if (!switch.enabled) return null
    val surface = sheetSurface.get() ?: return null
    return surface.bright.takeIf { color == surface.stock }
}

private fun ComposeBindings.withSheetSurface(
    composer: Any?,
    block: () -> Any?,
): Any? {
    val scheme = consume.invoke(composer, localColorScheme)
    val outer = sheetSurface.get()
    sheetSurface.set(
        SheetSurface(
            surfaceColor.getLong(scheme),
            surfaceBrightColor.getLong(scheme),
            surfaceContainerHighColor.getLong(scheme),
        ),
    )
    try {
        return block()
    } finally {
        sheetSurface.set(outer)
    }
}

private fun <T> withCheckboxBadges(
    enabled: Boolean,
    block: () -> T,
): T {
    val outer = badgeAsCheckbox.get()
    badgeAsCheckbox.set(enabled)
    try {
        return block()
    } finally {
        badgeAsCheckbox.set(outer)
    }
}

private fun ComposeBindings.emitGroupDivider(
    composer: Any?,
    changed: Int,
) {
    val first = awaitingFirstGroup.get() == true
    awaitingFirstGroup.set(false)
    if (first || changed and RESTART_CALL_BIT != 0) return
    emitBalanced(composer, GROUP_DIVIDER) {
        val color = outlineVariantColor.getLong(consume.invoke(composer, localColorScheme))
        val modifier =
            paddingSymmetric.invoke(
                null,
                modifierCompanion,
                DIVIDER_INSET_DP,
                DIVIDER_PADDING_DP,
            )
        divider.invoke(null, modifier, 0f, color, composer, 0, DIVIDER_THICKNESS_DEFAULT_MASK)
    }
}

private fun ComposeBindings.emitBalanced(
    composer: Any?,
    emission: String,
    emit: () -> Unit,
): Boolean {
    if (emission in failedEmissions) return false
    val marker = rewind?.markerOf(composer) ?: return false
    val failure =
        try {
            emit()
            null
        } catch (e: Exception) {
            e
        } catch (e: LinkageError) {
            e
        }
    if (failure == null && rewind.markerOf(composer) == marker) return true
    failedEmissions += emission
    reportFailure(emission, failure ?: IllegalStateException("groups left open at marker=$marker"))
    rewind.rewind(checkNotNull(composer), marker)
    return false
}

private fun ComposeBindings.navButtonModifier(
    stock: Any?,
    container: Long,
): Any? {
    val outer = sheetSurface.get()
    sheetSurface.remove()
    try {
        var modifier = stock ?: modifierCompanion
        modifier = wrapContentWidth.invoke(null, modifier, WRAP_CONTENT_DEFAULTS_MASK)
        modifier = size.invoke(null, modifier, NAV_BUTTON_DP)
        modifier = clip.invoke(null, modifier, circleShape)
        modifier = background.invoke(null, modifier, container, circleShape)
        return paddingSymmetric.invoke(null, modifier, 0f, NAV_BUTTON_ICON_INSET_DP)
    } finally {
        sheetSurface.set(outer)
    }
}

private fun disabledContainer(onSurface: Long): Long {
    val rgb = (onSurface ushr COLOR_SHIFT) and RGB_MASK
    return ((DISABLED_CONTAINER_ALPHA.toLong() shl ALPHA_SHIFT) or rgb) shl COLOR_SHIFT
}

private fun ComposeBindings.emitCheckbox(
    checked: Boolean,
    composer: Any?,
) {
    val scheme = consume.invoke(composer, localColorScheme)
    val box = primaryColor.getLong(scheme)
    val outline = onSurfaceVariantColor.getLong(scheme)
    val colors =
        checkboxColors.newInstance(
            surfaceBrightColor.getLong(scheme),
            transparent,
            box,
            transparent,
            box,
            transparent,
            box,
            box,
            outline,
            box,
            outline,
            box,
        )
    checkbox.invoke(
        null,
        checked,
        null,
        null,
        false,
        colors,
        composer,
        0,
        CHECKBOX_MODIFIER_ENABLED_DEFAULT_MASK,
    )
}

private fun zeroOf(type: Class<*>): Any? =
    when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        else -> null
    }

private fun ComposeBindings.libraryRowOf(chain: XposedInterface.Chain): LibraryRow? {
    val composerAt = composerIndex(libraryItem)
    val context = consume.invoke(chain.getArg(composerAt), localContext) as? Context ?: return null
    val ids =
        (0 until composerAt)
            .filter { libraryItem.parameterTypes[it] == Int::class.javaPrimitiveType }
            .map { chain.getArg(it) as Int }

    fun idOfType(type: String) =
        ids.singleOrNull {
            try {
                context.resources.getResourceTypeName(it) == type
            } catch (_: Resources.NotFoundException) {
                false
            }
        }
    return LibraryRow(
        icon = idOfType("drawable") ?: return null,
        label = idOfType("string") ?: return null,
        onClick = chain.getArg(libraryItemOnClickAt) ?: return null,
    )
}

private fun ComposeBindings.emitLibraryRows(
    defaults: Int,
    rows: List<LibraryRow>,
    composer: Any?,
) {
    startReplaceGroup.invoke(composer, LIBRARY_ROWS_GROUP_KEY)
    rows.forEach { row ->
        menuItem.emit(
            defaults = defaults,
            label = invokeResource(stringResource, row.label, composer) as String,
            icon = invokeResource(painterResource, row.icon, composer),
            onClick = row.onClick,
            composer = composer,
        )
    }
    endReplaceGroup.invoke(composer)
}

private fun ComposeBindings.composableSequence(parts: List<Any>): Any =
    Proxy.newProxyInstance(function2.classLoader, arrayOf(function2)) { proxy, method, args ->
        when (method.name) {
            "invoke" -> {
                within(MenuScope.HEADER) {
                    parts.forEach { invokeComposable.invoke(it, args[0], args[1]) }
                }
                unit
            }

            "equals" -> {
                proxy === args?.firstOrNull()
            }

            "hashCode" -> {
                System.identityHashCode(proxy)
            }

            else -> {
                "ComposableSequence(${parts.size})"
            }
        }
    }

private val reportedFailures = ConcurrentHashMap.newKeySet<String>()

private fun XposedModule.menuHook(
    member: Executable,
    interceptor: (XposedInterface.Chain) -> Any?,
) {
    val name = "${member.declaringClass.simpleName}#${member.name}"
    hook(member).intercept { chain ->
        try {
            interceptor(chain)
        } catch (e: Exception) {
            reportFailure(name, e)
            throw e
        } catch (e: LinkageError) {
            reportFailure(name, e)
            throw e
        }
    }
}

private fun reportFailure(
    member: String,
    failure: Throwable,
) {
    if (!reportedFailures.add(member)) return
    val cause = failure.unwrapped()
    Logger.error("hook failed tweak=$TWEAK member=$member reason=${cause.summary()}", cause)
}

private fun Throwable.unwrapped(): Throwable =
    (this as? InvocationTargetException)?.targetException ?: this

private fun Throwable.summary() = "${javaClass.simpleName}: $message"

private fun XposedModule.hookMenuRows(compose: ComposeBindings) {
    menuHook(compose.sheetHandle) { chain ->
        if (switch.enabled) null else chain.proceed()
    }
    val sheetComposerAt = compose.composerIndex(compose.sheet)
    menuHook(compose.sheet) { chain ->
        if (!switch.enabled) return@menuHook chain.proceed()
        compose.withSheetSurface(chain.getArg(sheetComposerAt)) { chain.proceed() }
    }
    val navigationComposerAt = compose.composerIndex(compose.navigation)
    menuHook(compose.navigation) { chain ->
        if (!switch.enabled) return@menuHook chain.proceed()
        compose.withSheetSurface(chain.getArg(navigationComposerAt)) {
            within(MenuScope.NAV) { chain.proceed() }
        }
    }
    val navItemComposerAt = compose.composerIndex(compose.navItem)
    menuHook(compose.navItem) { chain ->
        if (!switch.enabled) return@menuHook chain.proceed()
        val scheme =
            compose.consume.invoke(
                chain.getArg(navItemComposerAt),
                compose.localColorScheme,
            )
        val disabled = (chain.getArg(compose.navItemStateAt) as? Enum<*>)?.name == DISABLED_STATE
        val container =
            if (disabled) {
                disabledContainer(compose.onSurfaceColor.getLong(scheme))
            } else {
                compose.surfaceContainerColor.getLong(scheme)
            }
        chain.proceedReplacing(0, compose.navButtonModifier(chain.getArg(0), container))
    }
    val iconTintAt = compose.icon.parameterTypes.indexOf(Long::class.javaPrimitiveType)
    val iconComposerAt = compose.composerIndex(compose.icon)
    menuHook(compose.icon) { chain ->
        if (!switch.enabled || menuScope.get() != MenuScope.NAV) return@menuHook chain.proceed()
        val scheme = compose.consume.invoke(chain.getArg(iconComposerAt), compose.localColorScheme)
        if (chain.getArg(iconTintAt) != compose.onSurfaceVariantColor.getLong(scheme)) {
            return@menuHook chain.proceed()
        }
        chain.proceedReplacing(iconTintAt, compose.onSurfaceColor.getLong(scheme))
    }
    menuHook(compose.paddingSymmetric) { chain ->
        val navRow =
            switch.enabled && menuScope.get() == MenuScope.NAV &&
                chain.getArg(1) == STOCK_NAV_ROW_PADDING_DP
        if (!navRow) return@menuHook chain.proceed()
        chain.proceedReplacing(1, ICON_ROW_PADDING_DP)
    }
    compose.account?.let { account ->
        menuHook(account) { chain ->
            if (switch.enabled) within(MenuScope.ACCOUNT) { chain.proceed() } else chain.proceed()
        }
    }
    menuHook(compose.width) { chain ->
        val accountGap =
            switch.enabled && menuScope.get() == MenuScope.ACCOUNT &&
                chain.getArg(1) == STOCK_ICON_LABEL_GAP_DP
        if (!accountGap) return@menuHook chain.proceed()
        chain.proceedReplacing(1, ICON_LABEL_GAP_DP)
    }
    menuHook(compose.defaultMinSize) { chain ->
        val accountHeight =
            switch.enabled && menuScope.get() == MenuScope.ACCOUNT &&
                chain.getArg(MIN_HEIGHT) == STOCK_ACCOUNT_ROW_HEIGHT_DP
        if (!accountHeight) return@menuHook chain.proceed()
        chain.proceedReplacing(MIN_HEIGHT, ROW_HEIGHT_DP)
    }
    val textStyleAt = compose.text.parameterTypes.indexOf(compose.captionStyle.javaClass)
    menuHook(compose.text) { chain ->
        val accountDescription =
            switch.enabled && menuScope.get() == MenuScope.ACCOUNT &&
                chain.getArg(textStyleAt) === compose.captionStyle
        if (accountDescription) null else chain.proceed()
    }
    menuHook(compose.background) { chain ->
        val chevronContainer =
            switch.enabled && menuScope.get() == MenuScope.ROW &&
                chain.getArg(BACKGROUND_SHAPE) !== compose.rectangleShape &&
                chain.getArg(BACKGROUND_COLOR) == sheetSurface.get()?.chevronContainer
        if (!chevronContainer) return@menuHook chain.proceed()
        chain.proceedReplacing(BACKGROUND_COLOR, compose.transparent)
    }
    compose.rows.forEach { row ->
        val rowComposerAt = compose.composerIndex(row)
        menuHook(row) { chain ->
            if (!switch.enabled) return@menuHook chain.proceed()
            compose.withSheetSurface(chain.getArg(rowComposerAt)) {
                within(MenuScope.ROW) { chain.proceed() }
            }
        }
    }
    menuHook(compose.menuFrame) { chain ->
        if (!switch.enabled) return@menuHook chain.proceed()
        val args = chain.args.toTypedArray<Any?>()
        args[MENU_FRAME_CONTENT_MODIFIER] = compose.modifierCompanion
        args[compose.frameHeaderAt] =
            compose.composableSequence(
                listOfNotNull(args[compose.frameHeaderAt], args[compose.frameFooterAt]),
            )
        args[compose.frameFooterAt] = compose.composableSequence(emptyList())
        awaitingFirstGroup.set(true)
        try {
            within(MenuScope.FRAME) { chain.proceed(args) }
        } finally {
            awaitingFirstGroup.remove()
        }
    }
    menuHook(compose.divider) { chain ->
        if (switch.enabled && menuScope.get() == MenuScope.HEADER) null else chain.proceed()
    }
    val groupComposerAt = compose.composerIndex(compose.menuGroup)
    menuHook(compose.menuGroup) { chain ->
        if (!switch.enabled) return@menuHook chain.proceed()
        compose.emitGroupDivider(chain.getArg(groupComposerAt), chain.args.last() as Int)
        within(MenuScope.GROUP) { chain.proceed() }
    }
    val libraryComposerAt = compose.composerIndex(compose.libraryGroup)
    menuHook(compose.libraryGroup) { chain ->
        if (!switch.enabled) return@menuHook chain.proceed()
        val defaults = compose.menuItem.plainDefaults
        val composer = chain.getArg(libraryComposerAt)
        val changed = chain.args.last() as Int
        compose.emitGroupDivider(composer, changed)
        val rewindable = compose.rewind?.markerOf(composer) != null
        if (defaults == null || !rewindable || LIBRARY_ROWS in failedEmissions) {
            return@menuHook chain.proceed()
        }
        val captured = mutableListOf<LibraryRow>()
        val outer = libraryCapture.get()
        libraryCapture.set(captured)
        try {
            chain.proceed()
        } finally {
            libraryCapture.set(outer)
        }
        if (changed and RESTART_CALL_BIT != 0) return@menuHook null
        val rows = captured.ifEmpty { libraryRowsByComposer[composer].orEmpty() }
        libraryRowsByComposer[composer] = rows
        val emitted =
            compose.emitBalanced(composer, LIBRARY_ROWS) {
                compose.emitLibraryRows(defaults, rows, composer)
            }
        if (!emitted) chain.proceed()
        null
    }
    menuHook(compose.libraryItem) { chain ->
        val captured = libraryCapture.get() ?: return@menuHook chain.proceed()
        val row = compose.libraryRowOf(chain)
        if (row == null) {
            Logger.debug { "library row kept tweak=$TWEAK reason=unknown-resources" }
            return@menuHook chain.proceed()
        }
        captured += row
        null
    }
    menuHook(compose.toolsGroup) { chain ->
        if (!switch.enabled) return@menuHook chain.proceed()
        try {
            withCheckboxBadges(true) { chain.proceed() }
        } finally {
            awaitingFirstGroup.set(false)
        }
    }
    menuHook(compose.expandable) { chain ->
        if (switch.enabled) withCheckboxBadges(false) { chain.proceed() } else chain.proceed()
    }
    menuHook(compose.menuItem.method) { chain ->
        compose.menuItem.learnDefaults(chain.args.last() as Int)
        val checkboxRow =
            switch.enabled &&
                badgeAsCheckbox.get() == true &&
                CHECKBOX !in failedEmissions &&
                chain.getArg(compose.menuItem.afterContentAt) != null &&
                (chain.getArg(compose.menuItem.stateAt) as? Enum<*>)?.name == ACTIVE_STATE
        if (!checkboxRow) return@menuHook chain.proceed()
        chain.proceedReplacing(compose.menuItem.stateAt, compose.menuItemEnabled)
    }
    val badgeComposerAt = compose.composerIndex(compose.badge)
    menuHook(compose.badge) { chain ->
        if (!switch.enabled || badgeAsCheckbox.get() != true) return@menuHook chain.proceed()
        val checked = (chain.getArg(compose.badgeStateAt) as? Enum<*>)?.name == ACTIVE_STATE
        val composer = chain.getArg(badgeComposerAt)
        val emitted =
            compose.emitBalanced(
                composer,
                CHECKBOX,
            ) { compose.emitCheckbox(checked, composer) }
        if (emitted) null else chain.proceed()
    }
    menuHook(compose.spacedAligned) { chain ->
        val spacing = chain.getArg(0) as Float
        val scope = if (switch.enabled) menuScope.get() else null
        val replacement =
            when (scope) {
                MenuScope.FRAME -> 0f.takeIf { spacing == FRAME_SPACING_DP }
                MenuScope.GROUP -> 0f.takeIf { spacing == GROUP_SPACING_DP }
                MenuScope.NAV -> 0f.takeIf { spacing == NAV_SPACING_DP }
                MenuScope.ROW -> ICON_LABEL_GAP_DP.takeIf { spacing == STOCK_ICON_LABEL_GAP_DP }
                MenuScope.HEADER, MenuScope.ACCOUNT, null -> null
            } ?: return@menuHook chain.proceed()
        chain.proceedReplacing(0, replacement)
    }
    menuHook(compose.iconListItem) { chain ->
        if (!switch.enabled || menuScope.get() != MenuScope.ROW) return@menuHook chain.proceed()
        val args = chain.args.toTypedArray<Any?>()
        args[LIST_ITEM_DESCRIPTION] = null
        if (args[LIST_ITEM_HEIGHT] in stockRowHeights) args[LIST_ITEM_HEIGHT] = ROW_HEIGHT_DP
        chain.proceed(args)
    }
    menuHook(compose.listItemContent) { chain ->
        if (!switch.enabled || menuScope.get() != MenuScope.ROW) return@menuHook chain.proceed()
        chain.proceedReplacing(LIST_ITEM_CONTENT_MAX_LINES, SINGLE_LINE)
    }
    listOf(compose.background to BACKGROUND_COLOR, compose.surfaceComposable to SURFACE_COLOR)
        .forEach { (method, colorAt) ->
            menuHook(method) { chain ->
                val surface =
                    brightSurfaceFor(chain.getArg(colorAt)) ?: return@menuHook chain.proceed()
                chain.proceedReplacing(colorAt, surface)
            }
        }
}
