package eu.hxreborn.pixelinjector.support

import android.util.Log
import eu.hxreborn.pixelinjector.ModuleConstants
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

private const val TIMEOUT_MS = 10_000
private const val ASSET_HOST_SUFFIX = ".githubusercontent.com"

fun assetDownloadUrl(apkUrl: String): String {
    val location =
        try {
            redirectLocation(apkUrl)
        } catch (e: IOException) {
            Log.w(ModuleConstants.LOG_TAG, "update asset redirect failed url=$apkUrl", e)
            return apkUrl
        }
    val target =
        location?.let(::parseUri)?.takeIf {
            it.scheme == "https" && it.host?.endsWith(ASSET_HOST_SUFFIX) == true
        }
    if (target == null) {
        Log.w(ModuleConstants.LOG_TAG, "update asset redirect unusable location=$location")
        return apkUrl
    }
    return target.toString()
}

private fun redirectLocation(url: String): String? {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.instanceFollowRedirects = false
        connection.requestMethod = "HEAD"
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        val redirected =
            connection.responseCode in
                HttpURLConnection.HTTP_MULT_CHOICE until HttpURLConnection.HTTP_BAD_REQUEST
        return if (redirected) connection.getHeaderField("Location") else null
    } finally {
        connection.disconnect()
    }
}

private fun parseUri(value: String): URI? =
    try {
        URI(value)
    } catch (_: URISyntaxException) {
        null
    }
