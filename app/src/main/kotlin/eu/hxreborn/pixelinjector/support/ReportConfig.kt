package eu.hxreborn.pixelinjector.support

import android.util.Base64
import eu.hxreborn.pixelinjector.BuildConfig

object ReportConfig {
    val baseUrl: String = BuildConfig.REPORT_BASE_URL
    val app: String = BuildConfig.REPORT_APP
    val repo: String = BuildConfig.REPORT_REPO
    val keyId: String = BuildConfig.REPORT_KEY_ID
    val assetPattern = Regex(BuildConfig.REPORT_ASSET_PATTERN)
    val publicKey: ByteArray = Base64.decode(BuildConfig.REPORT_PUBLIC_KEY, Base64.DEFAULT)
    val host: String get() = baseUrl.substringAfter("://")
}
