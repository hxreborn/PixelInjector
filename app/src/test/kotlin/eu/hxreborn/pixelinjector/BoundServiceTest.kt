package eu.hxreborn.pixelinjector

import io.github.libxposed.service.XposedService
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

private const val BINDER_INTERFACE = "io.github.libxposed.service.IXposedService"

class BoundServiceTest {
    private fun newService(): XposedService =
        XposedService::class.java
            .getDeclaredConstructor(Class.forName(BINDER_INTERFACE))
            .apply { isAccessible = true }
            .newInstance(null)

    @Test
    fun releaseOfCurrentServiceClearsIt() {
        val boundService = BoundService()
        val service = newService()
        boundService.bind(service)

        boundService.release(service)

        assertNull(boundService.current.value)
    }

    @Test
    fun releaseOfReplacedServiceKeepsCurrent() {
        val boundService = BoundService()
        val replaced = newService()
        val current = newService()
        boundService.bind(replaced)
        boundService.bind(current)

        boundService.release(replaced)

        assertSame(current, boundService.current.value)
    }
}
