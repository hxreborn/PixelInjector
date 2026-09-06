package eu.hxreborn.pixelinjector.ui.dashboard

import eu.hxreborn.pixelinjector.ModuleConstants.GBOARD_PACKAGES
import eu.hxreborn.pixelinjector.ModuleConstants.LAUNCHER_PACKAGES
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.prefs.AppPrefs
import eu.hxreborn.pixelinjector.prefs.BoolPref
import eu.hxreborn.pixelinjector.prefs.IntPref
import eu.hxreborn.pixelinjector.prefs.PrefSpec
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.ui.navigation.Destination

enum class TweakGroup {
    SYSTEM_UI,
    SYSTEM,
    LAUNCHER,
    GBOARD,
    DIALER,
    FILES,
}

class TweakEditor(
    val title: Int,
    val description: Int?,
    val destination: Destination,
    val count: (AppPrefs) -> Int,
)

class TweakSlider(
    val title: Int,
    val description: Int,
    val pref: IntPref,
)

class TweakUi(
    val key: String,
    val pref: BoolPref,
    val title: Int,
    val description: Int,
    val group: TweakGroup,
    val editor: TweakEditor? = null,
    val slider: TweakSlider? = null,
    val errorKeys: Set<String> = setOf(key),
)

val tweakUis: List<TweakUi> =
    listOf(
        TweakUi("WarmTiles", Prefs.WARM_TILES, R.string.tweak_warm_tiles, R.string.tweak_warm_tiles_desc, TweakGroup.SYSTEM_UI),
        TweakUi("PillShot", Prefs.PILL_SHOT, R.string.tweak_pill_shot, R.string.tweak_pill_shot_desc, TweakGroup.SYSTEM_UI),
        TweakUi(
            "ScreenshotSound",
            Prefs.SCREENSHOT_SOUND,
            R.string.tweak_screenshot_sound,
            R.string.tweak_screenshot_sound_desc,
            TweakGroup.SYSTEM_UI,
        ),
        TweakUi(
            "ExpandNotifications",
            Prefs.EXPAND_NOTIFICATIONS,
            R.string.tweak_expand_notifications,
            R.string.tweak_expand_notifications_desc,
            TweakGroup.SYSTEM_UI,
        ),
        TweakUi(
            "StatusBarDoubleTap",
            Prefs.STATUS_BAR_DOUBLE_TAP,
            R.string.tweak_status_bar_double_tap,
            R.string.tweak_status_bar_double_tap_desc,
            TweakGroup.SYSTEM_UI,
            errorKeys = setOf("StatusBarDoubleTap", "SleepSignal"),
        ),
        TweakUi(
            "BiometricBypass",
            Prefs.BIOMETRIC_BYPASS,
            R.string.tweak_biometric_bypass,
            R.string.tweak_biometric_bypass_desc,
            TweakGroup.SYSTEM_UI,
        ),
        TweakUi(
            "ForceNewTask",
            Prefs.NEW_TASK,
            R.string.tweak_new_task,
            R.string.tweak_new_task_desc,
            TweakGroup.SYSTEM,
            editor =
                TweakEditor(R.string.tweak_new_task_rules, R.string.tweak_new_task_rules_desc, Destination.RulesEditor) { prefs ->
                    prefs[Prefs.NEW_TASK_RULES].lines().count { it.isNotBlank() }
                },
        ),
        TweakUi(
            "ClipboardAllowlist",
            Prefs.CLIPBOARD,
            R.string.tweak_clipboard,
            R.string.tweak_clipboard_desc,
            TweakGroup.SYSTEM,
            editor = TweakEditor(R.string.tweak_clipboard_apps, null, Destination.AppsEditor) { it[Prefs.CLIPBOARD_APPS].size },
        ),
        TweakUi(
            "DoubleTapToSleep",
            Prefs.DOUBLE_TAP_TO_SLEEP,
            R.string.tweak_double_tap_sleep,
            R.string.tweak_double_tap_sleep_desc,
            TweakGroup.LAUNCHER,
            errorKeys = setOf("DoubleTapToSleep", "SleepSignal"),
        ),
        TweakUi(
            "TaskbarHandle",
            Prefs.HIDE_PILL,
            R.string.tweak_hide_pill,
            R.string.tweak_hide_pill_desc,
            TweakGroup.LAUNCHER,
            slider = TweakSlider(R.string.tweak_nav_space, R.string.tweak_nav_space_desc, Prefs.NAV_SPACE),
        ),
        TweakUi(
            "HideSearchBar",
            Prefs.HIDE_SEARCH_BAR,
            R.string.tweak_hide_search_bar,
            R.string.tweak_hide_search_bar_desc,
            TweakGroup.LAUNCHER,
        ),
        TweakUi("GboardBlack", Prefs.GBOARD_BLACK, R.string.tweak_gboard_black, R.string.tweak_gboard_black_desc, TweakGroup.GBOARD),
        TweakUi("DialerDechip", Prefs.DIALER_DECHIP, R.string.tweak_dialer_dechip, R.string.tweak_dialer_dechip_desc, TweakGroup.DIALER),
        TweakUi(
            "FilesSortByDate",
            Prefs.FILES_SORT_BY_DATE,
            R.string.tweak_files_sort_by_date,
            R.string.tweak_files_sort_by_date_desc,
            TweakGroup.FILES,
        ),
    )

val restartOnChange: Map<PrefSpec<*>, Set<String>> =
    mapOf(
        Prefs.HIDE_SEARCH_BAR to LAUNCHER_PACKAGES,
        Prefs.NAV_SPACE to LAUNCHER_PACKAGES,
        Prefs.GBOARD_BLACK to GBOARD_PACKAGES,
    )
