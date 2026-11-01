package eu.hxreborn.pixelinjector.prefs

import eu.hxreborn.pixelinjector.ModuleConstants

private const val NAV_SPACE_FULL = 100
private const val HEADS_UP_SECONDS_DEFAULT = 5

object Prefs {
    const val GROUP: String = ModuleConstants.PREFS_GROUP

    val all: List<PrefSpec<*>>
        field = mutableListOf<PrefSpec<*>>()

    val perBrowser: List<PerPackage<*>>
        field = mutableListOf<PerPackage<*>>()

    private fun <P : PrefSpec<*>> P.register(): P = also { all += it }

    private fun <P : PrefSpec<*>> browser(
        key: String,
        make: (String) -> P,
    ): PerPackage<P> =
        PerPackage(key, ModuleConstants.FIREFOX_PACKAGES, make).also {
            perBrowser += it
            all += it.byPackage.values
        }

    val WARM_TILES = BoolPref("systemui.warmTiles", true).register()
    val PILL_SHOT = BoolPref("systemui.pillShot", true).register()
    val PILL_SHOT_ONLY_SELECTED = BoolPref("systemui.pillShot.onlySelected", false).register()
    val PILL_SHOT_APPS = StringSetPref("systemui.pillShot.apps", emptySet()).register()
    val SCREENSHOT_SOUND = BoolPref("systemui.screenshotSound", false).register()
    val EMPTY_SHADE = BoolPref("systemui.emptyShade", false).register()
    val EMPTY_SHADE_TEXT = StringPref("systemui.emptyShade.text", "No notifications").register()
    val EMPTY_SHADE_ICON_PATH = StringPref("systemui.emptyShade.iconPath", "").register()
    val EXPAND_NOTIFICATIONS = BoolPref("systemui.expandNotifications", false).register()
    val ONGOING_NO_DISMISS = BoolPref("systemui.ongoingNoDismiss", false).register()
    val CHARGING_DETAILS = BoolPref("systemui.chargingDetails", false).register()
    val DOWNLOAD_CHIP = BoolPref("systemui.downloadChip", false).register()
    const val DOWNLOAD_CHIP_LEFT = "left"
    const val DOWNLOAD_CHIP_RIGHT = "right"
    const val DOWNLOAD_CHIP_NOTIFICATION = "notification"
    const val DOWNLOAD_CHIP_APP = "app"
    val DOWNLOAD_CHIP_SIDE = StringPref("systemui.downloadChip.side", DOWNLOAD_CHIP_LEFT).register()
    val DOWNLOAD_CHIP_ICON =
        StringPref(
            "systemui.downloadChip.icon",
            DOWNLOAD_CHIP_NOTIFICATION,
        ).register()
    val DOWNLOAD_CHIP_HIDE_LOCKED = BoolPref("systemui.downloadChip.hideLocked", false).register()
    val DOWNLOAD_CHIP_HIDE_HEADS_UP =
        BoolPref(
            "systemui.downloadChip.hideHeadsUp",
            false,
        ).register()
    val DOWNLOAD_CHIP_HIDE_STATUS_ICON =
        BoolPref(
            "systemui.downloadChip.hideStatusIcon",
            false,
        ).register()
    val HEADS_UP_DURATION = BoolPref("systemui.headsUpDuration", false).register()
    const val HEADS_UP_SECONDS_MIN = 1
    const val HEADS_UP_SECONDS_MAX = 15
    val HEADS_UP_SECONDS =
        IntPref(
            "systemui.headsUpDuration.seconds",
            HEADS_UP_SECONDS_DEFAULT,
        ).register()
    val FINGERPRINT_ICON = BoolPref("systemui.fingerprintIcon", false).register()
    val FINGERPRINT_ICON_STYLE = StringPref("systemui.fingerprintIcon.style", "").register()
    val FINGERPRINT_HIDE_CIRCLE = BoolPref("systemui.fingerprintIcon.hideCircle", false).register()
    val HIDE_SHORTCUT_CIRCLES = BoolPref("systemui.hideShortcutCircles", false).register()
    val STATUS_BAR_DOUBLE_TAP = BoolPref("systemui.statusBarDoubleTap", false).register()
    val NEW_TASK = BoolPref("system.newTask", false).register()
    val NEW_TASK_RULES = StringPref("system.newTask.rules", "").register()
    val CLIPBOARD = BoolPref("system.clipboard", false).register()
    val CLIPBOARD_APPS = StringSetPref("system.clipboard.apps", emptySet()).register()
    val DOUBLE_TAP_TO_SLEEP = BoolPref("launcher.doubleTapToSleep", false).register()
    val BIOMETRIC_BYPASS = BoolPref("systemui.biometricBypass", false).register()
    val BIOMETRIC_SOUND = StringPref("systemui.biometricBypass.sound", "").register()
    val HIDE_PILL = BoolPref("launcher.hidePill", false).register()
    val NAV_SPACE = IntPref("launcher.navSpace", NAV_SPACE_FULL).register()
    val HIDE_SEARCH_BAR = BoolPref("launcher.hideSearchBar", false).register()
    val HIDE_APP_SHORTCUTS = BoolPref("launcher.hideAppShortcuts", false).register()
    val HOME_MENU_ENTRY = BoolPref("launcher.homeMenuEntry", false).register()
    val GBOARD_BLACK = BoolPref("gboard.black", false).register()
    val DIALER_DECHIP = BoolPref("dialer.dechip", false).register()
    val FILES_SORT_BY_DATE = BoolPref("files.sortByDate", false).register()
    val KDE_CONNECT_QUIET = BoolPref("kdeconnect.quiet", false).register()
    val GH_FAST_PASS = BoolPref("github.fastPass", false).register()
    val FIREFOX_MATERIAL_YOU = browser("firefox.materialYou") { BoolPref(it, false) }
    val FIREFOX_LOADING_INDICATOR = browser("firefox.loadingIndicator") { BoolPref(it, false) }
    val FIREFOX_CLASSIC_MENU = browser("firefox.classicMenu") { BoolPref(it, false) }
    val FIREFOX_BETTERFOX = browser("firefox.betterfox") { BoolPref(it, false) }
    val FIREFOX_BETTERFOX_OFF_GROUPS =
        browser("firefox.betterfox.offGroups") { StringSetPref(it, emptySet()) }
    val FIREFOX_BETTERFOX_OVERRIDES = browser("firefox.betterfox.overrides") { StringPref(it, "") }
    val FIREFOX_BETTERFOX_LIST = StringPref("firefox.betterfox.list", "").register()
}
