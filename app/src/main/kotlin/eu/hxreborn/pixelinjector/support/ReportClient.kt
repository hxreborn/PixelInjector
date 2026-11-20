package eu.hxreborn.pixelinjector.support

import android.net.Network
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
        val etag: String? = null,
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

sealed interface AttachmentState {
    data object Present : AttachmentState

    data object Absent : AttachmentState

    data class Failed(
        val reason: String,
    ) : AttachmentState
}

object ReportClient {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val HTTP_TIMEOUT = 408
    private const val HTTP_TOO_MANY = 429
    private const val HTTP_SERVER_ERRORS = 500
    private const val MILLIS = 1000

    private fun url(path: String) =
        if (path.startsWith("https://")) {
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
        network: Network? = null,
    ): ApiResponse {
        val url = url(path)
        var opened: HttpURLConnection? = null
        return try {
            val connection =
                (
                    network?.openConnection(
                        url,
                    ) ?: url.openConnection()
                ) as HttpURLConnection
            opened = connection
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
            val stream =
                if (code < HttpURLConnection.HTTP_BAD_REQUEST) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
            val text = stream?.use { it.bufferedReader().readText() }.orEmpty()
            when {
                code in HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE ||
                    code == HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    ApiResponse.Ok(code, text, connection.getHeaderField("ETag"))
                }

                code == HttpURLConnection.HTTP_FORBIDDEN &&
                    connection.getHeaderField("X-RateLimit-Remaining") == "0" -> {
                    ApiResponse.Retryable(
                        "rate limited",
                        connection.getHeaderField("X-RateLimit-Reset")?.toLongOrNull()?.let {
                            (it - System.currentTimeMillis() / MILLIS).coerceAtLeast(0)
                        },
                    )
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
                            JSONObject(
                                text,
                            ).run { if (isNull("message")) "" else optString("message") }
                        }.getOrNull().orEmpty().ifBlank { "http $code" },
                    )
                }
            }
        } catch (e: IOException) {
            ApiResponse.Retryable(e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""), null)
        } finally {
            opened?.disconnect()
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

    fun hasAttachment(sha256: String): AttachmentState =
        when (val reply = request("HEAD", "/attachments/$sha256")) {
            is ApiResponse.Ok -> {
                AttachmentState.Present
            }

            is ApiResponse.Refused -> {
                if (reply.code == HttpURLConnection.HTTP_NOT_FOUND) {
                    AttachmentState.Absent
                } else {
                    AttachmentState.Failed(reply.reason)
                }
            }

            is ApiResponse.Retryable -> {
                AttachmentState.Failed(reply.reason)
            }
        }

    fun putAttachment(
        sha256: String,
        reportId: String,
        deleteToken: String,
        envelope: ByteArray,
    ): ApiResponse =
        request(
            "PUT",
            "/attachments/$sha256",
            mapOf(
                "X-Report-Id" to reportId,
                "X-Delete-Token" to deleteToken,
                "X-Key-Id" to ReportConfig.keyId,
            ),
            envelope,
        )

    fun fetchReportStatuses(reports: List<Pair<String, String>>): ApiResponse =
        request(
            "GET",
            "/reports/status?ids=${reports.joinToString(",") { it.first }}",
            mapOf("X-Read-Tokens" to reports.joinToString(",") { it.second }),
        )

    fun deleteReport(
        id: String,
        deleteToken: String,
    ): ApiResponse = request("DELETE", "/reports/$id", mapOf("X-Delete-Token" to deleteToken))

    fun fetchRelease(network: Network? = null): ApiResponse =
        request("GET", "/release", network = network)

    fun fetchGitHubRelease(
        etag: String?,
        network: Network? = null,
    ): ApiResponse =
        request(
            "GET",
            "https://api.github.com/repos/${ReportConfig.repo}/releases/latest",
            buildMap {
                put("Accept", "application/vnd.github+json")
                etag?.let { put("If-None-Match", it) }
            },
            network = network,
        )

    fun parseStatuses(body: String): List<JSONObject> {
        val array: JSONArray = JSONObject(body).optJSONArray("reports") ?: return emptyList()
        return List(array.length()) { array.getJSONObject(it) }
    }
}
