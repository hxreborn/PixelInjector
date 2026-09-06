package eu.hxreborn.pixelinjector.xposed

import android.content.SharedPreferences
import eu.hxreborn.pixelinjector.ModuleConstants.DIALER_PACKAGE
import eu.hxreborn.pixelinjector.ModuleConstants.FILES_PACKAGES
import eu.hxreborn.pixelinjector.ModuleConstants.GBOARD_PACKAGES
import eu.hxreborn.pixelinjector.ModuleConstants.LAUNCHER_PACKAGES
import eu.hxreborn.pixelinjector.ModuleConstants.SYSTEMUI_PACKAGE
import eu.hxreborn.pixelinjector.prefs.BoolPref
import eu.hxreborn.pixelinjector.prefs.PrefSpec
import io.github.libxposed.api.XposedModule

internal enum class Target {
    SYSTEM,
    SYSTEMUI,
    SCREENSHOT,
    LAUNCHER,
    GBOARD,
    DIALER,
    FILES,
    ;

    companion object {
        fun of(
            process: String,
            systemServer: Boolean,
        ): Target? =
            when {
                systemServer -> SYSTEM
                process == SYSTEMUI_PACKAGE -> SYSTEMUI
                process == "$SYSTEMUI_PACKAGE:screenshot" -> SCREENSHOT
                process in LAUNCHER_PACKAGES -> LAUNCHER
                process in GBOARD_PACKAGES -> GBOARD
                process == DIALER_PACKAGE -> DIALER
                process in FILES_PACKAGES -> FILES
                else -> null
            }
    }
}

internal open class Value<T : Any, R>(
    private val pref: PrefSpec<T>,
    private val onLoad: (R) -> Unit = {},
    private val map: (T) -> R,
) {
    @Volatile var value: R = map(pref.default)
        private set

    val key: String get() = pref.key

    fun load(prefs: SharedPreferences) {
        value = map(pref.read(prefs))
        onLoad(value)
    }
}

internal class Switch(
    pref: BoolPref,
    onLoad: (Boolean) -> Unit = {},
) : Value<Boolean, Boolean>(pref, onLoad, { it }) {
    val enabled: Boolean get() = value
}

internal class Tweak(
    val key: String,
    val targets: Set<Target>,
    val prefs: List<Value<*, *>> = emptyList(),
    val saveState: () -> Any? = { null },
    val restoreState: (Any?) -> Unit = {},
    val install: XposedModule.(ClassLoader) -> Boolean,
)
