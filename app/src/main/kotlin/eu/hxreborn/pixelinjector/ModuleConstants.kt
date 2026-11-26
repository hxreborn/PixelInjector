package eu.hxreborn.pixelinjector

object ModuleConstants {
    const val PREFS_GROUP: String = "pixelinjector"
    const val LOG_TAG: String = "PixelInjector"
    const val SYSTEMUI_PACKAGE: String = "com.android.systemui"
    const val DIALER_PACKAGE: String = "com.google.android.dialer"
    const val KDE_CONNECT_PACKAGE: String = "org.kde.kdeconnect_tp"
    const val GITHUB_PACKAGE: String = "com.github.android"
    const val SAFE_MODE_REASON: String = "safe-mode"

    const val SAMSUNG_FINGERPRINT_REASON = "Samsung-draws-fingerprint-icon-outside-SystemUI"
    const val TRANSSION_FINGERPRINT_REASON = "Transsion-draws-fingerprint-icon-outside-SystemUI"

    val GBOARD_PACKAGES: Set<String> =
        setOf(
            "com.google.android.inputmethod.latin",
            "dev.jason.com.google.android.inputmethod.latin",
        )

    val LAUNCHER_PACKAGES: Set<String> =
        setOf("com.android.launcher3", "com.google.android.apps.nexuslauncher")

    val FILES_PACKAGES: Set<String> =
        setOf("com.google.android.documentsui", "com.android.documentsui")

    val FIREFOX_PACKAGES: Set<String> =
        setOf(
            "io.github.forkmaintainers.iceraven",
            "org.ironfoxoss.ironfox",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fenix",
        )
}
