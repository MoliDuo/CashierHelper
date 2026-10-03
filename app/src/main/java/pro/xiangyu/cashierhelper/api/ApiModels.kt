package pro.xiangyu.cashierhelper.api

import java.io.File
import pro.xiangyu.cashierhelper.config.AppConfig

/** One line of a recognised bill. Amounts stay decimal strings: they must never go through a float. */
data class DocumentEntry(
    val name: String,
    val description: String?,
    val amount: String,
    val currency: String?,
    val category: String?,
)

/**
 * What Cashier recognised. [total] is already converted to the ledger's main
 * currency and can be null until an exchange rate is available.
 */
data class DocumentResult(
    val title: String?,
    val total: String?,
    val totalCurrency: String?,
    val entries: List<DocumentEntry>,
)

sealed interface DocumentStatus {
    data object Processing : DocumentStatus
    data class Completed(val result: DocumentResult) : DocumentStatus

    /** The image is not something Cashier can book. [message] is a sentence meant for the user. */
    data class Invalid(val message: String?) : DocumentStatus
    data class Failed(val code: String?, val message: String?) : DocumentStatus
    data object Cancelled : DocumentStatus
}

/** Why a request is worth repeating. Only used to choose the wording shown to the user. */
enum class TransientKind { NETWORK, TIMEOUT, SERVER, MALFORMED }

sealed interface UploadOutcome {
    /** Cashier stored the images and queued the analysis; also what an idempotent replay returns. */
    data class Accepted(val sourceDocumentId: String) : UploadOutcome

    /** Nothing is known to be wrong with the request itself; try again with the same key. */
    data class Transient(val kind: TransientKind, val retryAfterMillis: Long? = null) : UploadOutcome
    data object Unauthorized : UploadOutcome

    /** The idempotency key was already used with different content. */
    data object Conflict : UploadOutcome
    data class Rejected(val httpCode: Int, val code: String?, val message: String?) : UploadOutcome
}

sealed interface QueryOutcome {
    data class Status(val status: DocumentStatus, val retryAfterMillis: Long? = null) : QueryOutcome
    data object NotFound : QueryOutcome
    data object Unauthorized : QueryOutcome
    data class Transient(val kind: TransientKind, val retryAfterMillis: Long? = null) : QueryOutcome
}

enum class ConnectionTest { OK, BAD_KEY, NOT_CASHIER, UNREACHABLE, BAD_CERT, UNKNOWN_HOST }

interface DocumentApi {
    /** Uploads 1 to 3 JPEG files. Retrying with the same [idempotencyKey], files and [entryDate] never duplicates. */
    suspend fun create(
        config: AppConfig,
        images: List<File>,
        entryDate: String,
        idempotencyKey: String,
    ): UploadOutcome

    suspend fun get(config: AppConfig, sourceDocumentId: String): QueryOutcome

    suspend fun testConnection(baseUrl: String, apiKey: String): ConnectionTest
}
