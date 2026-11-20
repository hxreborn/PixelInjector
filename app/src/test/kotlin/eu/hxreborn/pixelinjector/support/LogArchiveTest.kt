package eu.hxreborn.pixelinjector.support

import eu.hxreborn.pixelinjector.BuildConfig
import eu.hxreborn.pixelinjector.ui.log.currentInstallFailures
import eu.hxreborn.pixelinjector.ui.log.parseModuleLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogArchiveTest {
    @get:Rule val folder = TemporaryFolder()

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
        val failures = currentInstallFailures(parseModuleLog(reopened))
        assertEquals(systemMiss, failures.getValue("system").getValue("CrtScreenOff").raw)
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
        assertTrue(currentInstallFailures(parseModuleLog(merged)).getValue("system").isEmpty())
    }

    @Test
    fun olderModuleInstallDoesNotLeaveCurrentVersionFailure() {
        val archive = LogArchive(folder.root)
        val olderStart = install("system", 103, version = BuildConfig.VERSION_CODE - 1)
        val merged =
            archive.merge(listOf(systemStart, systemMiss, olderStart, failure(103)) + playback)

        assertTrue(olderStart in merged)
        assertFalse("system" in currentInstallFailures(parseModuleLog(merged)))
    }

    private fun install(
        process: String,
        pid: Int,
        origin: String = "boot",
        version: Int = BuildConfig.VERSION_CODE,
    ): String = line(pid, "install proc=$process pid=$pid version=$version origin=$origin")

    private fun failure(pid: Int): String =
        "[ 2026-11-20T11:00:01.000 1000: $pid: $pid E/LSPosedFramework ] " +
            "(system)[eu.hxreborn.pixelinjector,PixelInjector,0,1] " +
            "target not found tweak=CrtScreenOff member=ColorFade#draw build=TEST reason=no-method"

    private fun line(
        pid: Int,
        body: String,
    ): String = "11-20 12:00:00.000 $pid $pid I PixelInjector: eu.hxreborn.pixelinjector: $body"
}
