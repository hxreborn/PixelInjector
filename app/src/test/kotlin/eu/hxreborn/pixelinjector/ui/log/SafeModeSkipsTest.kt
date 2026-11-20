package eu.hxreborn.pixelinjector.ui.log

import eu.hxreborn.pixelinjector.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

private const val SYSTEMUI = "com.android.systemui"
private const val SYSTEM = "system"
private const val SAFE_MODE_SKIP = "install failed tweak=WarmTiles build=BP2A.250605.031.A3 reason=safe-mode"
private const val NOT_FOUND = "target not found tweak=FingerprintIcon member=DeviceEntryIconView build=BP2A reason=no-class"

class SafeModeSkipsTest {
    @Test
    fun safeModeLogLeavesOnlyTheOtherProcessError() {
        val runtimeError = "apply failed tweak=WarmTiles reason=no-view"
        val entries =
            parseModuleLog(
                listOf(
                    log(101, 'I', "install proc=$SYSTEM version=${BuildConfig.VERSION_CODE}"),
                    log(101, 'E', runtimeError),
                    log(102, 'I', "install proc=$SYSTEMUI version=${BuildConfig.VERSION_CODE}"),
                    log(102, 'E', SAFE_MODE_SKIP),
                ),
            )

        assertEquals(
            mapOf("WarmTiles" to runtimeError, processKey("WarmTiles", SYSTEM) to runtimeError),
            deriveErrorLines(entries, setOf(101, 102)),
        )
        assertEquals(emptyMap<String, String>(), deriveErrorLines(entries, setOf(102)))
    }

    @Test
    fun tweakSkippedOnlyBySafeModeIsASkip() {
        val failures = mapOf(SYSTEMUI to mapOf("WarmTiles" to SAFE_MODE_SKIP))

        assertEquals(setOf("WarmTiles"), safeModeSkips(failures))
    }

    @Test
    fun targetNotFoundIsNotASkip() {
        val failures = mapOf(SYSTEMUI to mapOf("FingerprintIcon" to NOT_FOUND))

        assertEquals(emptySet<String>(), safeModeSkips(failures))
    }

    @Test
    fun missInAnotherProcessOutranksASafeModeSkip() {
        val failures =
            mapOf(
                SYSTEMUI to mapOf("FingerprintIcon" to SAFE_MODE_SKIP),
                SYSTEM to mapOf("FingerprintIcon" to NOT_FOUND),
            )

        assertEquals(emptySet<String>(), safeModeSkips(failures))
    }

    private fun log(
        pid: Int,
        level: Char,
        message: String,
    ): String = "11-20 12:00:00.000 $pid $pid $level PixelInjector: $message"
}
