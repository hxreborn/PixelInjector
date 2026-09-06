package eu.hxreborn.pixelinjector.xposed

import android.content.SharedPreferences
import eu.hxreborn.pixelinjector.xposed.hook.dialer.dialerDechip
import eu.hxreborn.pixelinjector.xposed.hook.files.filesSortByDate
import eu.hxreborn.pixelinjector.xposed.hook.gboard.gboardBlack
import eu.hxreborn.pixelinjector.xposed.hook.launcher.doubleTapToSleep
import eu.hxreborn.pixelinjector.xposed.hook.launcher.hideSearchBar
import eu.hxreborn.pixelinjector.xposed.hook.launcher.taskbarHandle
import eu.hxreborn.pixelinjector.xposed.hook.system.clipboardAllowlist
import eu.hxreborn.pixelinjector.xposed.hook.system.forceNewTask
import eu.hxreborn.pixelinjector.xposed.hook.system.sleepSignal
import eu.hxreborn.pixelinjector.xposed.hook.systemui.biometricBypass
import eu.hxreborn.pixelinjector.xposed.hook.systemui.expandNotifications
import eu.hxreborn.pixelinjector.xposed.hook.systemui.pillShot
import eu.hxreborn.pixelinjector.xposed.hook.systemui.screenshotSound
import eu.hxreborn.pixelinjector.xposed.hook.systemui.statusBarDoubleTap
import eu.hxreborn.pixelinjector.xposed.hook.systemui.warmTiles

internal val tweaks: List<Tweak> =
    listOf(
        warmTiles,
        pillShot,
        screenshotSound,
        expandNotifications,
        statusBarDoubleTap,
        forceNewTask,
        clipboardAllowlist,
        sleepSignal,
        doubleTapToSleep,
        biometricBypass,
        taskbarHandle,
        hideSearchBar,
        gboardBlack,
        dialerDechip,
        filesSortByDate,
    )

internal fun tweaksFor(target: Target): List<Tweak> = tweaks.filter { target in it.targets }

internal fun loadHookPrefs(
    prefs: SharedPreferences,
    target: Target,
    process: String,
    changed: String? = null,
) {
    val values =
        tweaksFor(target).flatMap { it.prefs }.filter { changed == null || it.key == changed }
    for (value in values) {
        runCatching { value.load(prefs) }
            .onFailure {
                Logger.warn(
                    "prefs load failed key=${value.key} reason=${it.message}",
                    it,
                )
            }
    }
    if (values.isNotEmpty()) {
        Logger.info("prefs reloaded proc=$process keys=${values.joinToString(",") { it.key }}")
    }
}
