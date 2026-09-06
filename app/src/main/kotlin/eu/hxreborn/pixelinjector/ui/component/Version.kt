package eu.hxreborn.pixelinjector.ui.component

import eu.hxreborn.pixelinjector.BuildConfig

fun versionLabel(code: Long): String = "${code / 100}.${code / 10 % 10}.${code % 10} ($code)"

val installedVersionLabel: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

fun processNameForDisplay(name: String): String = name.replace(":", ":​").replace(".", ".​")
