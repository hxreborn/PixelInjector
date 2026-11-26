package eu.hxreborn.pixelinjector.ui.log

import eu.hxreborn.pixelinjector.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

        assertEquals(setOf("WarmTiles"), skippedInstalls(failures))
    }

    @Test
    fun targetNotFoundIsNotASkip() {
        val failures = mapOf(SYSTEMUI to mapOf("FingerprintIcon" to NOT_FOUND))

        assertEquals(emptySet<String>(), skippedInstalls(failures))
    }

    @Test
    fun missInAnotherProcessOutranksASafeModeSkip() {
        val failures =
            mapOf(
                SYSTEMUI to mapOf("FingerprintIcon" to SAFE_MODE_SKIP),
                SYSTEM to mapOf("FingerprintIcon" to NOT_FOUND),
            )

        assertEquals(emptySet<String>(), skippedInstalls(failures))
    }

    @Test
    fun externalFingerprintRendererIsSkippedButAnUnknownTargetIsReported() {
        for (vendor in listOf("Samsung", "Transsion")) {
            val external = NOT_FOUND.replace("no-class", "$vendor-draws-fingerprint-icon-outside-SystemUI")
            val failures = mapOf(SYSTEMUI to mapOf("FingerprintIcon" to external))

            assertFalse(isReportableMissingTarget(external))
            assertTrue(deriveErrors(failures).getValue("FingerprintIcon").externalFingerprintIcon)
            assertEquals(setOf("FingerprintIcon"), skippedInstalls(failures))
            assertTrue(isReportableMissingTarget(external.replace("FingerprintIcon ", "WarmTiles ")))
            val unknown = mapOf(SYSTEM to mapOf("FingerprintIcon" to NOT_FOUND))
            for (mixed in listOf(failures + unknown, unknown + failures)) {
                assertEquals(emptySet<String>(), skippedInstalls(mixed))
                assertFalse(deriveErrors(mixed).getValue("FingerprintIcon").externalFingerprintIcon)
            }
        }
        assertTrue(isReportableMissingTarget(NOT_FOUND))
    }

    @Test
    fun externalFingerprintDiagnosticPreservesAnotherRuntimeError() {
        val runtimeError = "apply failed tweak=FingerprintIcon reason=no-drawable"
        val install = log(101, 'I', "install proc=$SYSTEMUI version=${BuildConfig.VERSION_CODE}")
        val external = log(101, 'E', NOT_FOUND.replace("no-class", "Transsion-draws-fingerprint-icon-outside-SystemUI"))

        assertTrue(deriveErrorLines(parseModuleLog(listOf(install, external)), setOf(101)).isEmpty())
        val entries = parseModuleLog(listOf(install, log(101, 'E', runtimeError), external))
        assertEquals(runtimeError, deriveErrorLines(entries, setOf(101))["FingerprintIcon"])
    }

    private fun log(
        pid: Int,
        level: Char,
        message: String,
    ): String = "11-20 12:00:00.000 $pid $pid $level PixelInjector: $message"
}
