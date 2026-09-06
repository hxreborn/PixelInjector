package eu.hxreborn.pixelinjector

object ModuleConstants {
    const val PREFS_GROUP: String = "pixelinjector"
    const val LOG_TAG: String = "PixelInjector"
    const val SYSTEMUI_PACKAGE: String = "com.android.systemui"
    const val DIALER_PACKAGE: String = "com.google.android.dialer"

    val GBOARD_PACKAGES: Set<String> =
        setOf(
            "com.google.android.inputmethod.latin",
            "dev.jason.com.google.android.inputmethod.latin",
        )

    val LAUNCHER_PACKAGES: Set<String> =
        setOf("com.android.launcher3", "com.google.android.apps.nexuslauncher")

    val FILES_PACKAGES: Set<String> =
        setOf("com.google.android.documentsui", "com.android.documentsui")

    val SLEEP_CALLERS: Set<String> = LAUNCHER_PACKAGES + SYSTEMUI_PACKAGE
}
