package eu.hxreborn.pixelinjector.support

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface ApiResponse {
    data class Ok(
        val code: Int,
        val body: String,
    ) : ApiResponse

    data class Retryable(
        val reason: String,
        val afterSeconds: Long?,
    ) : ApiResponse

    data class Refused(
        val code: Int,
        val reason: String,
    ) : ApiResponse
}

object ReportClient {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val HTTP_TIMEOUT = 408
    private const val HTTP_TOO_MANY = 429
    private const val HTTP_SERVER_ERRORS = 500

    private fun url(path: String) =
        if (path.startsWith(
                "https://",
            )
        ) {
            URL(path)
        } else {
            URL("${ReportConfig.baseUrl}/v1/${ReportConfig.app}$path")
        }

    private fun request(
        method: String,
        path: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        contentType: String = "application/octet-stream",
    ): ApiResponse {
        val connection = url(path).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val text =
                (
                    if (code <
                        HttpURLConnection.HTTP_BAD_REQUEST
                    ) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }
                )?.use { stream ->
                    stream.bufferedReader().readText()
                }.orEmpty()
            when {
                code in HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE -> {
                    ApiResponse.Ok(code, text)
                }

                code == HTTP_TIMEOUT || code == HTTP_TOO_MANY || code >= HTTP_SERVER_ERRORS -> {
                    ApiResponse.Retryable(
                        "http $code",
                        connection.getHeaderField("Retry-After")?.toLongOrNull(),
                    )
                }

                else -> {
                    ApiResponse.Refused(
                        code,
                        runCatching {
                            JSONObject(text).optString("message")
                        }.getOrNull().orEmpty().ifEmpty { "http $code" },
                    )
                }
            }
        } catch (e: IOException) {
            ApiResponse.Retryable(e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""), null)
        } finally {
            connection.disconnect()
        }
    }

    fun putReport(
        id: String,
        meta: ByteArray,
        envelope: ByteArray,
        readHash: String,
        deleteHash: String,
        replyTo: String?,
    ): ApiResponse =
        request(
            "PUT",
            "/reports/$id",
            buildMap {
                put(
                    "X-Report-Meta",
                    Base64.encodeToString(
                        meta,
                        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
                    ),
                )
                put("X-Read-Hash", readHash)
                put("X-Delete-Hash", deleteHash)
                replyTo?.let { put("X-Reply-To", it) }
            },
            envelope,
        )

    fun statuses(reports: List<Pair<String, String>>): ApiResponse =
        request(
            "GET",
            "/reports/status?ids=${reports.joinToString(",") { it.first }}",
            mapOf("X-Read-Tokens" to reports.joinToString(",") { it.second }),
        )

    fun delete(
        id: String,
        deleteToken: String,
    ): ApiResponse = request("DELETE", "/reports/$id", mapOf("X-Delete-Token" to deleteToken))

    fun postStatus(meta: ByteArray): ApiResponse =
        request("POST", "/status", body = meta, contentType = "application/json")

    fun release(): ApiResponse = request("GET", "/release")

    fun gitHubRelease(): ApiResponse =
        request(
            "GET",
            "https://api.github.com/repos/${ReportConfig.repo}/releases/latest",
            mapOf("Accept" to "application/vnd.github+json"),
        )

    fun parseStatuses(body: String): List<JSONObject> {
        val array: JSONArray = JSONObject(body).optJSONArray("reports") ?: return emptyList()
        return List(array.length()) { array.getJSONObject(it) }
    }
}
