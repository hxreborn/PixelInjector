package eu.hxreborn.pixelinjector.xposed.hook.system

import android.view.Display
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrtDozeTargetTest {
    @Test
    fun acceptsAwakeAndAmbientTargets() {
        for (target in listOf(Display.STATE_ON, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND)) {
            assertTrue(isCrtDozeTarget(target, Display.STATE_DOZE, true, false, false, false))
        }
    }

    @Test
    fun rejectsScreenOffAndUnsupportedTargets() {
        for (target in listOf(Display.STATE_OFF, Display.STATE_UNKNOWN, Display.STATE_ON_SUSPEND)) {
            assertFalse(isCrtDozeTarget(target, Display.STATE_DOZE, true, false, false, false))
        }
        assertFalse(isCrtDozeTarget(Display.STATE_ON, Display.STATE_OFF, true, false, false, false))
    }

    @Test
    fun rejectsForcedDisplayBlanks() {
        val target = Display.STATE_DOZE
        val doze = Display.STATE_DOZE
        assertFalse(isCrtDozeTarget(target, doze, false, false, false, false))
        assertFalse(isCrtDozeTarget(target, doze, true, true, false, false))
        assertFalse(isCrtDozeTarget(target, doze, true, false, true, false))
        assertFalse(isCrtDozeTarget(target, doze, true, false, false, true))
    }
}
