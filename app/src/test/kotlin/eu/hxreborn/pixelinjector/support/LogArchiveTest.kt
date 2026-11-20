package eu.hxreborn.pixelinjector.support

import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.InstallDiagnostics
import eu.hxreborn.pixelinjector.ui.log.installFailureBodies
import eu.hxreborn.pixelinjector.ui.log.parseModuleLog
import eu.hxreborn.pixelinjector.xposed.CrashGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogArchiveTest {
    @get:Rule val folder = TemporaryFolder()

    private val missBody =
        "target not found tweak=CrtScreenOff member=ColorFade#draw build=TEST reason=no-method"
    private val systemStart = install("system", 101)
    private val systemMiss = failure(101)
    private val systemUiStart = install("com.android.systemui", 102)
    private val playback =
        List(3100) { tick ->
            line(102, "updated tweak=NowPlayingChip playing=true package=player tick=$tick")
        }

    @Test
    fun retainsInstallAndFailureAfterLogRotation() {
        val archive = LogArchive(folder.root)
        val merged = archive.merge(listOf(systemStart, systemMiss, systemUiStart) + playback)

        assertEquals(3000, merged.size)
        assertEquals(listOf(systemStart, systemMiss, systemUiStart), merged.take(3))
        assertEquals(playback.last(), merged.last())
        val reopened = LogArchive(folder.root).merge(playback.takeLast(100))
        assertEquals(merged, reopened)
        val failures = installFailureBodies(parseModuleLog(reopened), emptyMap())
        assertEquals(missBody, failures.getValue("system").getValue("CrtScreenOff"))
        assertTrue(failures.getValue("com.android.systemui").isEmpty())
    }

    @Test
    fun newerInstallClearsFailureEvenWhenOldLogIsReadAgain() {
        val archive = LogArchive(folder.root)
        val original = listOf(systemStart, systemMiss, systemUiStart)
        archive.merge(original + playback)
        val reload = install("system", 101, origin = "reload")
        val fresh = original + reload + playback
        val merged = archive.merge(fresh)

        assertTrue(reload in merged)
        assertFalse(systemMiss in merged)
        assertEquals(merged, archive.merge(fresh))
        assertTrue(
            installFailureBodies(parseModuleLog(merged), emptyMap()).getValue("system").isEmpty(),
        )
    }

    @Test
    fun olderModuleInstallDoesNotLeaveCurrentVersionFailure() {
        val archive = LogArchive(folder.root)
        val olderStart = install("system", 103, version = BuildConfig.VERSION_CODE - 1)
        val merged =
            archive.merge(listOf(systemStart, systemMiss, olderStart, failure(103)) + playback)

        assertTrue(olderStart in merged)
        assertFalse("system" in installFailureBodies(parseModuleLog(merged), emptyMap()))
    }

    @Test
    fun firstReportAfterSourceRotationUsesStoredFailure() {
        val file = InstallDiagnostics.file(folder.root, "system")
        val generation = "101-2000"
        val bootInstant = 50_000L
        val guard = CrashGuard.open(file, "test", { bootInstant }, generation, false)
        guard.installed(BuildConfig.VERSION_CODE, mapOf("CrtScreenOff" to missBody))
        guard.stable()

        val sourceAfterRotation = LogArchive(folder.root).merge(playback)
        val contents = file.readText()
        val stored = checkNotNull(readDiagnostics(contents))
        val rotated = parseModuleLog(sourceAfterRotation)
        val failures = installFailureBodies(rotated, mapOf("system" to stored))
        assertEquals(missBody, failures.getValue("system").getValue("CrtScreenOff"))
        assertNull(readDiagnostics(contents, pid = 102))
        assertNull(readDiagnostics(contents, version = BuildConfig.VERSION_CODE - 1))
        assertNull(readDiagnostics(contents, bootInstant = bootInstant + 20_000))
    }

    @Test
    fun deferredFailuresSurviveInstallCompletionAndTheStableWrite() {
        val file = InstallDiagnostics.file(folder.root, "system")
        val guard = CrashGuard.open(file, "test", { 50_000L }, "101-2000", false)
        val version = BuildConfig.VERSION_CODE
        val fingerprintMiss =
            "target not found tweak=FingerprintIcon member=DeviceEntryIconView build=TEST reason=no-view"
        guard.installing("CrtScreenOff")
        guard.recordDeferredFailure(version, "FingerprintIcon", fingerprintMiss)
        assertNull(readDiagnostics(file.readText()))
        val outcomes = mapOf("FingerprintIcon" to "installed", "CrtScreenOff" to "installed")
        guard.installed(version, outcomes)
        assertEquals(
            mapOf("FingerprintIcon" to fingerprintMiss),
            readDiagnostics(file.readText())?.failures,
        )

        guard.stable()
        guard.recordDeferredFailure(version, "CrtScreenOff", missBody)
        assertEquals(
            mapOf("FingerprintIcon" to fingerprintMiss, "CrtScreenOff" to missBody),
            readDiagnostics(file.readText())?.failures,
        )
    }

    @Test
    fun successfulReloadSupersedesEarlierArchivedFailure() {
        val file = InstallDiagnostics.file(folder.root, "system")
        val bootInstant = 50_000L
        val earlier = CrashGuard.open(file, "test", { bootInstant }, "101-1000", false)
        earlier.installed(BuildConfig.VERSION_CODE, mapOf("CrtScreenOff" to missBody))
        val reload = CrashGuard.open(file, "test", { bootInstant }, "101-2000", false)
        assertNull(readDiagnostics(file.readText()))
        reload.installed(BuildConfig.VERSION_CODE, mapOf("CrtScreenOff" to "installed"))

        val stored = checkNotNull(readDiagnostics(file.readText()))
        val archived = parseModuleLog(listOf("$systemStart generation=101-1000", systemMiss))
        val failures = installFailureBodies(archived, mapOf("system" to stored))
        assertTrue(failures.getValue("system").isEmpty())
    }

    private fun readDiagnostics(
        contents: String,
        pid: Int = 101,
        version: Int = BuildConfig.VERSION_CODE,
        bootInstant: Long = 50_000L,
    ): InstallDiagnostics? = InstallDiagnostics.read(contents, pid, version, bootInstant)

    private fun install(
        process: String,
        pid: Int,
        origin: String = "boot",
        version: Int = BuildConfig.VERSION_CODE,
    ): String = line(pid, "install proc=$process pid=$pid version=$version origin=$origin")

    private fun failure(pid: Int): String =
        "[ 2026-11-20T11:00:01.000 1000: $pid: $pid E/LSPosedFramework ] " +
            "(system)[eu.hxreborn.pixelinjector,PixelInjector,0,1] " +
            missBody

    private fun line(
        pid: Int,
        body: String,
    ): String = "11-20 12:00:00.000 $pid $pid I PixelInjector: eu.hxreborn.pixelinjector: $body"
}
