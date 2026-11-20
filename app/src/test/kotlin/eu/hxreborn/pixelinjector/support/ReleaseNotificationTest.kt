package eu.hxreborn.pixelinjector.support

import eu.hxreborn.pixelinjector.BuildConfig
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ReleaseNotificationTest {
    private val release =
        Release(
            versionName = "test",
            versionCode = BuildConfig.VERSION_CODE + 2,
            publishedAt = null,
            url = "",
            notesNew = emptyList(),
            notesFixed = emptyList(),
            notesOther = emptyList(),
        )

    @Test
    fun postponedVersionDoesNotNotify() {
        assertNull(
            release.releaseToNotify(
                postponedVersionCode = release.versionCode,
                notifiedVersionCode = 0,
            ),
        )
    }

    @Test
    fun alreadyNotifiedVersionDoesNotNotify() {
        assertNull(
            release.releaseToNotify(
                postponedVersionCode = 0,
                notifiedVersionCode = release.versionCode,
            ),
        )
    }

    @Test
    fun releaseNewerThanBothVersionsCanNotify() {
        val previousVersionCode = release.versionCode - 1

        assertSame(release, release.releaseToNotify(previousVersionCode, previousVersionCode))
    }

    @Test
    fun installedVersionDoesNotNotify() {
        val installed = release.copy(versionCode = BuildConfig.VERSION_CODE)

        assertNull(installed.releaseToNotify(postponedVersionCode = 0, notifiedVersionCode = 0))
    }
}
