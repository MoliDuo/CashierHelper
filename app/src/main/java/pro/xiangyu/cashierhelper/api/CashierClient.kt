package pro.xiangyu.cashierhelper.api

import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import pro.xiangyu.cashierhelper.config.ApiKeyValidator
import pro.xiangyu.cashierhelper.config.AppConfig
import pro.xiangyu.cashierhelper.config.BaseUrlValidator

/**
 * The two Cashier v1 calls the app needs. Coroutine cancellation cancels the
 * underlying [Call]. Only [IOException] is turned into an outcome, so a
 * cancelled caller is never mistaken for a network failure.
 */
class CashierClient(
    private val http: OkHttpClient = defaultHttpClient(),
    private val createTimeoutMillis: Long = CREATE_TIMEOUT_MILLIS,
    private val getTimeoutMillis: Long = GET_TIMEOUT_MILLIS,
) : DocumentApi {

    override suspend fun create(
        config: AppConfig,
        images: List<File>,
        entryDate: String,
        idempotencyKey: String,
    ): UploadOutcome {
        require(images.isNotEmpty()) { "At least one image is required" }
        val request = try {
            Request.Builder()
                .url(endpoint(config.baseUrl, DOCUMENTS_PATH))
                .header("Authorization", bearer(config.apiKey))
                .header("Idempotency-Key", idempotencyKey)
                .post(ImagesRequestBody(images, entryDate))
                .build()
        } catch (_: IllegalArgumentException) {
            return UploadOutcome.Rejected(0, "INVALID_CONFIGURATION", null)
        }

        return try {
            execute(request, createTimeoutMillis) { response ->
                val body = response.body?.string().orEmpty()
                val retryAfter = parseRetryAfter(response.header("Retry-After"))
                when (val code = response.code) {
                    201 -> parseAccepted(body)
                    401, 403 -> UploadOutcome.Unauthorized
                    409 -> UploadOutcome.Conflict
                    408, 425, 429 -> UploadOutcome.Transient(TransientKind.SERVER, retryAfter)
                    in 500..599 -> UploadOutcome.Transient(TransientKind.SERVER, retryAfter)
                    else -> {
                        val error = parseError(body)
                        UploadOutcome.Rejected(code, error?.first, error?.second)
                    }
                }
            }
        } catch (error: IOException) {
            UploadOutcome.Transient(error.kind())
        }
    }

    override suspend fun get(config: AppConfig, sourceDocumentId: String): QueryOutcome {
        val request = try {
            Request.Builder()
                .url(endpoint(config.baseUrl, "$DOCUMENTS_PATH/${encodeSegment(sourceDocumentId)}"))
                .header("Authorization", bearer(config.apiKey))
                .get()
                .build()
        } catch (_: IllegalArgumentException) {
            return QueryOutcome.Unauthorized
        }

        return try {
            execute(request, getTimeoutMillis) { response ->
                val body = response.body?.string().orEmpty()
                val retryAfter = parseRetryAfter(response.header("Retry-After"))
                when (response.code) {
                    200 -> parseStatus(body, retryAfter)
                    401, 403 -> QueryOutcome.Unauthorized
                    404 -> QueryOutcome.NotFound
                    else -> QueryOutcome.Transient(TransientKind.SERVER, retryAfter)
                }
            }
        } catch (error: IOException) {
            QueryOutcome.Transient(error.kind())
        }
    }

    /**
     * Asks for a document that cannot exist. Cashier answers 404 with a
     * NOT_FOUND error for a valid key and 401 for a wrong one, so nothing is
     * created and the address, the key and the TLS setup are all exercised.
     */
    override suspend fun testConnection(baseUrl: String, apiKey: String): ConnectionTest {
        val request = try {
            Request.Builder()
                .url(endpoint(baseUrl, "$DOCUMENTS_PATH/${UUID.randomUUID()}"))
                .header("Authorization", bearer(apiKey))
                .get()
                .build()
        } catch (_: IllegalArgumentException) {
            return ConnectionTest.UNREACHABLE
        }

        return try {
            execute(request, TEST_TIMEOUT_MILLIS) { response ->
                val body = response.body?.string().orEmpty()
                when (response.code) {
                    404 ->
                        if (parseError(body)?.first == "NOT_FOUND") ConnectionTest.OK
                        else ConnectionTest.NOT_CASHIER
                    401, 403 -> ConnectionTest.BAD_KEY
                    200, 400, 405 -> ConnectionTest.NOT_CASHIER
                    else -> ConnectionTest.UNREACHABLE
                }
            }
        } catch (error: UnknownHostException) {
            ConnectionTest.UNKNOWN_HOST
        } catch (error: SSLException) {
            ConnectionTest.BAD_CERT
        } catch (error: IOException) {
            ConnectionTest.UNREACHABLE
        }
    }

    private suspend fun <T> execute(
        request: Request,
        timeoutMillis: Long,
        handle: (Response) -> T,
    ): T = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use(handle)
                } catch (error: Throwable) {
                    continuation.resumeWithException(error)
                    return
                }
                continuation.resume(result)
            }
        })
    }

    private fun parseAccepted(body: String): UploadOutcome {
        val id = parseObject(body)?.string("sourceDocumentId")?.takeIf(String::isNotBlank)
        return if (id != null) UploadOutcome.Accepted(id)
        else UploadOutcome.Transient(TransientKind.MALFORMED)
    }

    private fun parseStatus(body: String, retryAfter: Long?): QueryOutcome {
        val root = parseObject(body) ?: return QueryOutcome.Transient(TransientKind.MALFORMED)
        val error = root["error"] as? JsonObject
        val status = when (root.string("status")) {
            "processing" -> DocumentStatus.Processing
            "completed" -> DocumentStatus.Completed(
                parseResult(root["result"] as? JsonObject)
                    ?: return QueryOutcome.Transient(TransientKind.MALFORMED),
            )
            "invalid" -> DocumentStatus.Invalid(error?.string("message")?.takeIf(String::isNotBlank))
            "failed" -> DocumentStatus.Failed(
                error?.string("code")?.takeIf(String::isNotBlank),
                error?.string("message")?.takeIf(String::isNotBlank),
            )
            "cancelled" -> DocumentStatus.Cancelled
            else -> return QueryOutcome.Transient(TransientKind.MALFORMED)
        }
        return QueryOutcome.Status(status, retryAfter)
    }

    private fun parseResult(result: JsonObject?): DocumentResult? {
        result ?: return null
        val entries = (result["entries"] as? JsonArray)?.map { element ->
            val item = element as? JsonObject ?: return null
            DocumentEntry(
                name = item.string("name").orEmpty(),
                description = item.string("description")?.takeIf(String::isNotBlank),
                amount = item.string("amount") ?: return null,
                currency = item.string("currency")?.takeIf(String::isNotBlank),
                category = item.string("category")?.takeIf(String::isNotBlank),
            )
        } ?: return null
        return DocumentResult(
            title = result.string("title")?.takeIf(String::isNotBlank),
            total = result.string("total")?.takeIf(String::isNotBlank),
            totalCurrency = result.string("totalCurrency")?.takeIf(String::isNotBlank),
            entries = entries,
        )
    }

    /** `{"error":{"code":"...","message":"..."}}` as (code, message), or null for any other body. */
    private fun parseError(body: String): Pair<String?, String?>? {
        val error = parseObject(body)?.get("error") as? JsonObject ?: return null
        return error.string("code") to error.string("message")?.takeIf(String::isNotBlank)
    }

    private fun parseObject(body: String): JsonObject? =
        try {
            Json.parseToJsonElement(body).jsonObject
        } catch (_: Exception) {
            null
        }

    private fun JsonObject.string(name: String): String? =
        (get(name) as? JsonPrimitive)?.contentOrNull

    private fun bearer(apiKey: String): String =
        "Bearer " + ApiKeyValidator.normalize(apiKey).getOrElse {
            throw IllegalArgumentException("Invalid API key")
        }

    private fun endpoint(baseUrl: String, path: String): HttpUrl {
        val base = BaseUrlValidator.normalize(baseUrl).getOrElse {
            throw IllegalArgumentException("Invalid base url")
        }
        return (base + path).toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid endpoint")
    }

    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun IOException.kind(): TransientKind =
        if (this is InterruptedIOException) TransientKind.TIMEOUT else TransientKind.NETWORK

    companion object {
        const val DOCUMENTS_PATH = "/api/v1/source-documents"
        const val CREATE_TIMEOUT_MILLIS = 120_000L
        const val GET_TIMEOUT_MILLIS = 20_000L
        const val TEST_TIMEOUT_MILLIS = 15_000L
        private const val MAX_RETRY_AFTER_MILLIS = 60_000L

        /** `Retry-After` in seconds or as an HTTP date, capped so one odd header cannot stall polling. */
        internal fun parseRetryAfter(value: String?, now: Instant = Instant.now()): Long? {
            val text = value?.trim().orEmpty()
            if (text.isEmpty()) return null
            val millis = text.toLongOrNull()?.let { it * 1_000 }
                ?: try {
                    val at = ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
                    at.toEpochMilli() - now.toEpochMilli()
                } catch (_: DateTimeParseException) {
                    return null
                }
            return millis.coerceIn(0, MAX_RETRY_AFTER_MILLIS)
        }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            // Safe with an idempotency key: a resent upload is replayed, never duplicated.
            .retryOnConnectionFailure(true)
            .build()
    }
}
