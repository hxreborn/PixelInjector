package eu.hxreborn.pixelinjector.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

private const val IDENTITY = "12500|google/bluejay:17/CP4A.261105.040.A1|SystemUIGoogle.apk"
private const val BOOT = "boot-a"
private const val OTHER_BOOT = "boot-b"
private const val START_WALL_MS = 1_795_000_000_000L
private const val START_UPTIME_MS = 100_000L
private const val RESTART_GAP_MS = 10L
private const val REBOOT_DOWNTIME_MS = 30_000L
private const val REBOOT_UPTIME_DROP_MS = 1_000L
private const val REBOOT_UPTIME_RISE_MS = 20_000L
private const val CLOCK_JITTER_MS = 2_000L
private const val CLOCK_STEP_MS = 3_600_000L

class CrashGuardTest {
    @get:Rule val folder = TemporaryFolder()

    private var generations = 0
    private var uptimeMs = START_UPTIME_MS
    private var wallMs = START_WALL_MS
    private var currentBoot = BOOT

    private val file: File get() =
        File(
            folder.root,
            "pixelinjector-guard-com.android.systemui.properties",
        )

    private fun start(
        boot: String = BOOT,
        identity: String = IDENTITY,
        countEarlyDeaths: Boolean = true,
        uptimeAfterRebootMs: Long = uptimeMs - REBOOT_UPTIME_DROP_MS,
    ): CrashGuard {
        if (boot == currentBoot) {
            uptimeMs += RESTART_GAP_MS
            wallMs += RESTART_GAP_MS
        } else {
            currentBoot = boot
            wallMs += REBOOT_DOWNTIME_MS + uptimeAfterRebootMs
            uptimeMs = uptimeAfterRebootMs
        }
        return CrashGuard.open(
            file,
            identity,
            { wallMs - uptimeMs },
            "gen-${generations++}",
            countEarlyDeaths,
        )
    }

    private fun cleanStart(boot: String = BOOT): CrashGuard = start(boot).also { it.installed() }

    @Test
    fun firstStartQuarantinesNothing() {
        val guard = cleanStart()
        assertNull(guard.crashedDuringInstall)
        assertEquals(emptySet<String>(), guard.quarantined)
        assertFalse(guard.safeMode)
        assertEquals(0, guard.earlyDeaths)
    }

    @Test
    fun deathDuringInstallQuarantinesThatTweakOnly() {
        start().installing("DownloadChip")
        val next = start()
        assertEquals("DownloadChip", next.crashedDuringInstall)
        assertEquals(setOf("DownloadChip"), next.quarantined)
        assertFalse(next.safeMode)
    }

    @Test
    fun quarantineSurvivesRebootAndCleanStarts() {
        start().installing("DownloadChip")
        start().also {
            it.installing("WarmTiles")
            it.installed()
            it.stable()
        }
        val afterReboot = start(boot = OTHER_BOOT)
        assertNull(afterReboot.crashedDuringInstall)
        assertEquals(setOf("DownloadChip"), afterReboot.quarantined)
    }

    @Test
    fun rebootDuringInstallDoesNotQuarantine() {
        start().installing("WarmTiles")
        val afterReboot = start(boot = OTHER_BOOT)
        assertNull(afterReboot.crashedDuringInstall)
        assertEquals(emptySet<String>(), afterReboot.quarantined)
    }

    @Test
    fun rebootWithEqualUptimeDuringInstallDoesNotQuarantine() {
        start().installing("WarmTiles")
        val afterReboot = start(boot = OTHER_BOOT, uptimeAfterRebootMs = uptimeMs)
        assertNull(afterReboot.crashedDuringInstall)
        assertEquals(emptySet<String>(), afterReboot.quarantined)
    }

    @Test
    fun rebootWithGreaterUptimeDuringInstallDoesNotQuarantine() {
        start().installing("WarmTiles")
        val afterReboot =
            start(boot = OTHER_BOOT, uptimeAfterRebootMs = uptimeMs + REBOOT_UPTIME_RISE_MS)
        assertNull(afterReboot.crashedDuringInstall)
        assertEquals(emptySet<String>(), afterReboot.quarantined)
    }

    @Test
    fun rebootWithEqualUptimeClearsSafeMode() {
        cleanStart()
        cleanStart()
        assertTrue(start().safeMode)
        assertFalse(start(boot = OTHER_BOOT, uptimeAfterRebootMs = uptimeMs).safeMode)
    }

    @Test
    fun rebootWithGreaterUptimeClearsSafeMode() {
        cleanStart()
        cleanStart()
        assertTrue(start().safeMode)
        val afterReboot =
            start(boot = OTHER_BOOT, uptimeAfterRebootMs = uptimeMs + REBOOT_UPTIME_RISE_MS)
        assertFalse(afterReboot.safeMode)
    }

    @Test
    fun rebootWithGreaterUptimeIsNotAnEarlyDeath() {
        cleanStart()
        val afterReboot =
            start(boot = OTHER_BOOT, uptimeAfterRebootMs = uptimeMs + REBOOT_UPTIME_RISE_MS)
        assertEquals(0, afterReboot.earlyDeaths)
    }

    @Test
    fun clockJitterWithinTheBootStaysTheSameBoot() {
        start().installing("DownloadChip")
        wallMs += CLOCK_JITTER_MS
        assertEquals("DownloadChip", start().crashedDuringInstall)
    }

    @Test
    fun clockStepWithinTheBootCountsAsANewBootAndKeepsQuarantine() {
        start().installing("DownloadChip")
        start().also {
            it.installing("WarmTiles")
            it.installed()
        }
        wallMs += CLOCK_STEP_MS
        val next = start()
        assertNull(next.crashedDuringInstall)
        assertEquals(setOf("DownloadChip"), next.quarantined)
    }

    @Test
    fun eachTweakThatKillsInstallIsQuarantinedInTurn() {
        start().installing("DownloadChip")
        start().installing("ChargingDetails")
        val third = start()
        assertEquals(setOf("DownloadChip", "ChargingDetails"), third.quarantined)
        assertFalse(third.safeMode)
    }

    @Test
    fun twoEarlyDeathsAfterInstallEnterSafeMode() {
        cleanStart()
        val second = cleanStart()
        assertEquals(1, second.earlyDeaths)
        assertFalse(second.safeMode)
        val third = start()
        assertEquals(2, third.earlyDeaths)
        assertTrue(third.safeMode)
    }

    @Test
    fun safeModeHoldsForTheRestOfTheBootAndClearsOnReboot() {
        cleanStart()
        cleanStart()
        start().stable()
        assertTrue(start().safeMode)
        assertFalse(start(boot = OTHER_BOOT).safeMode)
    }

    @Test
    fun fourInstallDeathsEnterSafeMode() {
        listOf("A", "B", "C").forEach { start().installing(it) }
        assertFalse(start().also { it.installing("D") }.safeMode)
        assertTrue(start().safeMode)
    }

    @Test
    fun appRestartsWithoutDeathCountingNeverEnterSafeMode() {
        repeat(5) { start(countEarlyDeaths = false).installed() }
        val guard = start(countEarlyDeaths = false)
        assertEquals(0, guard.earlyDeaths)
        assertFalse(guard.safeMode)
    }

    @Test
    fun appInstallDeathStillQuarantines() {
        start(countEarlyDeaths = false).installing("GboardBlack")
        assertEquals(setOf("GboardBlack"), start(countEarlyDeaths = false).quarantined)
    }

    @Test
    fun stableGenerationResetsEarlyDeaths() {
        cleanStart()
        cleanStart().stable()
        val next = start()
        assertEquals(0, next.earlyDeaths)
        assertFalse(next.safeMode)
    }

    @Test
    fun rebootIsNotAnEarlyDeath() {
        cleanStart()
        val afterReboot = cleanStart(boot = OTHER_BOOT)
        assertEquals(0, afterReboot.earlyDeaths)
        assertEquals(0, start(boot = "boot-c").earlyDeaths)
    }

    @Test
    fun newIdentityClearsQuarantineAndSafeMode() {
        start().installing("DownloadChip")
        cleanStart()
        cleanStart()
        val updated = start(identity = "12501|$IDENTITY")
        assertEquals(emptySet<String>(), updated.quarantined)
        assertFalse(updated.safeMode)
        assertNull(updated.crashedDuringInstall)
    }

    @Test
    fun updatedHostApkClearsQuarantine() {
        start(countEarlyDeaths = false).installing("HideSearchBar")
        assertEquals(
            emptySet<String>(),
            start(identity = "$IDENTITY.updated", countEarlyDeaths = false).quarantined,
        )
    }

    @Test
    fun hotReloadNeitherCountsNorResetsDeathsButOpensANewWindow() {
        cleanStart()
        val reloaded = start(countEarlyDeaths = false)
        assertEquals(0, reloaded.earlyDeaths)
        reloaded.installed()
        assertEquals(1, start().earlyDeaths)
    }

    @Test
    fun hotReloadOfANewVersionCountsTheDeathItCauses() {
        cleanStart().stable()
        start(identity = "12501|$IDENTITY", countEarlyDeaths = false).installed()
        assertEquals(1, start(identity = "12501|$IDENTITY").earlyDeaths)
    }

    @Test
    fun hotReloadThatDiesDuringInstallQuarantines() {
        cleanStart().stable()
        start(countEarlyDeaths = false).installing("HeadsUpDuration")
        assertEquals(setOf("HeadsUpDuration"), start().quarantined)
    }

    @Test
    fun stableClearsInstallMarkerAfterCompletionWriteFails() {
        val guard = start()
        guard.installing("DownloadChip")
        val staging = File(folder.root, "${file.name}.gen-0.tmp")
        assertTrue(staging.mkdir())
        try {
            guard.installed()
            throw AssertionError("completion write succeeded with a staging directory")
        } catch (_: IOException) {
            assertTrue(staging.delete())
        }
        guard.stable()
        val next = start()
        assertNull(next.crashedDuringInstall)
        assertEquals(emptySet<String>(), next.quarantined)
        assertEquals(0, next.earlyDeaths)
    }

    @Test
    fun staleStableFromAnOlderGenerationIsIgnored() {
        val old = cleanStart()
        cleanStart()
        old.stable()
        assertEquals(2, start().earlyDeaths)
    }

    @Test
    fun staleStableAfterAHotReloadIsIgnored() {
        val old = cleanStart()
        start(identity = "12501|$IDENTITY", countEarlyDeaths = false).installed()
        old.stable()
        assertEquals(1, start(identity = "12501|$IDENTITY").earlyDeaths)
    }

    @Test
    fun corruptFileStartsFresh() {
        file.writeText("installing=\\uZZZZ\n")
        val guard = start()
        assertNull(guard.crashedDuringInstall)
        assertFalse(guard.safeMode)
    }

    @Test
    fun openDeletesStagingFilesOfOtherGenerations() {
        val leftover = File(folder.root, "${file.name}.gen-dead.tmp")
        val otherProcess = File(folder.root, "pixelinjector-guard-other.properties.gen-dead.tmp")
        val unrelated = File(folder.root, "${file.name}.bak")
        listOf(leftover, otherProcess, unrelated).forEach { it.writeText("x") }
        start()
        assertFalse(leftover.exists())
        assertTrue(otherProcess.exists())
        assertTrue(unrelated.exists())
        assertEquals(
            setOf(file.name, otherProcess.name, unrelated.name),
            folder.root.list()!!.toSet(),
        )
    }

    @Test(expected = IOException::class)
    fun unreadableExistingFileThrows() {
        start().installing("DownloadChip")
        file.setReadable(false, false)
        assumeFalse(file.canRead())
        start()
    }

    @Test(expected = IOException::class)
    fun missingDirectoryThrows() {
        CrashGuard.open(
            File(folder.root, "missing/guard.properties"),
            IDENTITY,
            { 0L },
            "gen",
            true,
        )
    }
}
