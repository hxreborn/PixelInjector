package eu.hxreborn.pixelinjector.xposed

import android.content.SharedPreferences
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.xposed.hook.dialer.dialerDechip
import eu.hxreborn.pixelinjector.xposed.hook.files.filesSortByDate
import eu.hxreborn.pixelinjector.xposed.hook.firefox.firefoxBetterfox
import eu.hxreborn.pixelinjector.xposed.hook.firefox.firefoxClassicMenu
import eu.hxreborn.pixelinjector.xposed.hook.firefox.firefoxLoadingIndicator
import eu.hxreborn.pixelinjector.xposed.hook.firefox.firefoxMaterialYou
import eu.hxreborn.pixelinjector.xposed.hook.gboard.gboardBlack
import eu.hxreborn.pixelinjector.xposed.hook.github.ghFastPass
import eu.hxreborn.pixelinjector.xposed.hook.kdeconnect.kdeConnectQuiet
import eu.hxreborn.pixelinjector.xposed.hook.launcher.doubleTapToSleep
import eu.hxreborn.pixelinjector.xposed.hook.launcher.hideAppShortcuts
import eu.hxreborn.pixelinjector.xposed.hook.launcher.hideSearchBar
import eu.hxreborn.pixelinjector.xposed.hook.launcher.homeMenuEntry
import eu.hxreborn.pixelinjector.xposed.hook.launcher.taskbarHandle
import eu.hxreborn.pixelinjector.xposed.hook.system.clipboardAllowlist
import eu.hxreborn.pixelinjector.xposed.hook.system.forceNewTask
import eu.hxreborn.pixelinjector.xposed.hook.systemui.biometricBypass
import eu.hxreborn.pixelinjector.xposed.hook.systemui.chargingDetails
import eu.hxreborn.pixelinjector.xposed.hook.systemui.downloadChip
import eu.hxreborn.pixelinjector.xposed.hook.systemui.emptyShade
import eu.hxreborn.pixelinjector.xposed.hook.systemui.expandNotifications
import eu.hxreborn.pixelinjector.xposed.hook.systemui.fingerprintIcon
import eu.hxreborn.pixelinjector.xposed.hook.systemui.headsUpDuration
import eu.hxreborn.pixelinjector.xposed.hook.systemui.hideShortcutCircles
import eu.hxreborn.pixelinjector.xposed.hook.systemui.ongoingNoDismiss
import eu.hxreborn.pixelinjector.xposed.hook.systemui.pillShot
import eu.hxreborn.pixelinjector.xposed.hook.systemui.screenshotSound
import eu.hxreborn.pixelinjector.xposed.hook.systemui.statusBarDoubleTap
import eu.hxreborn.pixelinjector.xposed.hook.systemui.warmTiles

internal val tweaks: List<Tweak> =
    listOf(
        warmTiles,
        pillShot,
        screenshotSound,
        emptyShade,
        expandNotifications,
        ongoingNoDismiss,
        headsUpDuration,
        chargingDetails,
        downloadChip,
        fingerprintIcon,
        hideShortcutCircles,
        statusBarDoubleTap,
        forceNewTask,
        clipboardAllowlist,
        doubleTapToSleep,
        biometricBypass,
        taskbarHandle,
        hideSearchBar,
        hideAppShortcuts,
        homeMenuEntry,
        gboardBlack,
        dialerDechip,
        filesSortByDate,
        kdeConnectQuiet,
        ghFastPass,
        firefoxMaterialYou,
        firefoxLoadingIndicator,
        firefoxClassicMenu,
        firefoxBetterfox,
    )

internal fun tweaksFor(target: Target): List<Tweak> = tweaks.filter { target in it.targets }

internal fun loadHookPrefs(
    prefs: SharedPreferences,
    target: Target,
    process: String,
    changed: String? = null,
) {
    val values =
        tweaksFor(target).flatMap { it.prefs }.filter {
            changed == null ||
                it.key(process) == changed
        }
    for (value in values) {
        try {
            value.load(prefs, process)
        } catch (e: Exception) {
            Logger.warn("prefs load failed key=${value.key(process)} reason=${e.message}", e)
        } catch (e: LinkageError) {
            Logger.warn("prefs load failed key=${value.key(process)} reason=${e.message}", e)
        }
    }
    if (values.isNotEmpty()) {
        Logger.info(
            "prefs reloaded proc=$process keys=${values.joinToString(",") { it.key(process) }}",
        )
    }
}
