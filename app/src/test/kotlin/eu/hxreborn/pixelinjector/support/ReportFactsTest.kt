package eu.hxreborn.pixelinjector.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportFactsTest {
    private val services = RomCode(RomCode.SERVICES, "/system/framework/services.jar", "hash", 1L)
    private val tweak = "CrtScreenOff"

    @Test
    fun selectedTweakIncludesCodeWithoutDetectedFailure() {
        val facts = reportFacts(emptyList(), mapOf(tweak to services))

        assertEquals(services, facts.romCodeFor(tweak))
        assertNull(facts.romCodeFor(null))
    }

    @Test
    fun reportWithoutSelectedTweakIncludesCodeForDetectedFailure() {
        val missing = listOf(MissingTarget(tweak, "ColorFade#draw", "no-method"))
        val facts = reportFacts(missing, mapOf(tweak to services))

        assertEquals(services, facts.romCodeFor(null))
    }

    @Test
    fun unreadableCodeBlocksOnlyTheSelectedOrDetectedTweak() {
        val facts = reportFacts(emptyList(), emptyMap(), mapOf(tweak to RomCode.SERVICES))

        assertEquals(RomCode.SERVICES, facts.unreadableRomCodeFor(tweak))
        assertNull(facts.unreadableRomCodeFor(null))
    }

    private fun reportFacts(
        missing: List<MissingTarget>,
        romCodes: Map<String, RomCode>,
        unreadable: Map<String, String> = emptyMap(),
    ): ReportFacts =
        ReportFacts(
            framework = null,
            rootDenied = false,
            logLines = null,
            tweaks = emptyMap(),
            missing = missing,
            packages = emptyList(),
            romCodes = romCodes,
            unreadableRomCodes = unreadable,
        )
}
