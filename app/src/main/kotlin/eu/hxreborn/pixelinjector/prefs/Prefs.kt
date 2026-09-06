package eu.hxreborn.pixelinjector.prefs

import eu.hxreborn.pixelinjector.ModuleConstants

private const val NAV_SPACE_FULL = 100

object Prefs {
    const val GROUP: String = ModuleConstants.PREFS_GROUP

    val all: List<PrefSpec<*>>
        field = mutableListOf<PrefSpec<*>>()

    private fun <P : PrefSpec<*>> P.register(): P = also { all += it }

    val WARM_TILES = BoolPref("systemui.warmTiles", true).register()
    val PILL_SHOT = BoolPref("systemui.pillShot", true).register()
    val SCREENSHOT_SOUND = BoolPref("systemui.screenshotSound", false).register()
    val EXPAND_NOTIFICATIONS = BoolPref("systemui.expandNotifications", false).register()
    val STATUS_BAR_DOUBLE_TAP = BoolPref("systemui.statusBarDoubleTap", false).register()
    val NEW_TASK = BoolPref("system.newTask", false).register()
    val NEW_TASK_RULES = StringPref("system.newTask.rules", "").register()
    val CLIPBOARD = BoolPref("system.clipboard", false).register()
    val CLIPBOARD_APPS = StringSetPref("system.clipboard.apps", emptySet()).register()
    val DOUBLE_TAP_TO_SLEEP = BoolPref("launcher.doubleTapToSleep", false).register()
    val BIOMETRIC_BYPASS = BoolPref("systemui.biometricBypass", false).register()
    val HIDE_PILL = BoolPref("launcher.hidePill", false).register()
    val NAV_SPACE = IntPref("launcher.navSpace", NAV_SPACE_FULL).register()
    val HIDE_SEARCH_BAR = BoolPref("launcher.hideSearchBar", false).register()
    val GBOARD_BLACK = BoolPref("gboard.black", false).register()
    val DIALER_DECHIP = BoolPref("dialer.dechip", false).register()
    val FILES_SORT_BY_DATE = BoolPref("files.sortByDate", false).register()
}
