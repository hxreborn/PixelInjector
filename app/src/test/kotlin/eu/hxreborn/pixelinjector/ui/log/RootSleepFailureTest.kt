package eu.hxreborn.pixelinjector.ui.log

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootSleepFailureTest {
    @Test
    fun suMissingFromTheHomeAppIsARootFailure() {
        assertTrue(
            isRootSleepFailure(
                "sleep failed path=root tweak=DoubleTapToSleep reason=error=2, No such file or directory",
            ),
        )
    }

    @Test
    fun suDeniedIsARootFailure() {
        assertTrue(isRootSleepFailure("sleep path=root tweak=DoubleTapToSleep exit=1 output=Permission denied"))
    }

    @Test
    fun unansweredSuPromptIsARootFailure() {
        assertTrue(isRootSleepFailure("sleep failed path=root tweak=DoubleTapToSleep reason=su timeout"))
    }

    @Test
    fun lockNowFailureIsNotARootFailure() {
        assertFalse(isRootSleepFailure("sleep failed path=lockNow tweak=StatusBarDoubleTap reason=SecurityException"))
    }
}
