package eu.hxreborn.pixelinjector.ui.component

import eu.hxreborn.pixelinjector.BuildConfig

fun versionLabel(versionCode: Long): String = "${versionCode / 10000}.${versionCode / 100 % 100}.${versionCode % 100} ($versionCode)"

val installedVersionLabel: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

fun processNameForDisplay(name: String): String = name.replace(":", ":​").replace(".", ".​")
