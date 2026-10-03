package pro.xiangyu.cashierhelper.tasks

import kotlinx.serialization.Serializable

@Serializable
enum class TaskSource { SCREENSHOT, SHARE, PICKER }

@Serializable
enum class TaskState {
    /** Waiting to be uploaded, possibly after a failed attempt. */
    QUEUED,
    UPLOADING,

    /** Cashier has the images and is analysing them. The local images are already gone. */
    PROCESSING,
    DONE,

    /** Nothing more happens until the user fixes something or retries. */
    NEEDS_ACTION,
}

@Serializable
enum class TaskProblem {
    NOT_CONFIGURED,
    UNAUTHORIZED,
    CONFIG_CHANGED,
    CONFLICT,
    REJECTED,
    IMAGES_MISSING,
}

@Serializable
enum class TaskOutcome { COMPLETED, INVALID, FAILED, CANCELLED, DELETED_ON_SERVER, NO_RESULT }

@Serializable
data class TaskEntry(
    val name: String,
    val amount: String,
    val currency: String? = null,
    val category: String? = null,
)

@Serializable
data class TaskResult(
    val title: String? = null,
    val total: String? = null,
    val totalCurrency: String? = null,
    /** At most [MAX_ENTRIES] lines; [entryCount] is the real number. */
    val entries: List<TaskEntry> = emptyList(),
    val entryCount: Int = 0,
) {
    companion object {
        const val MAX_ENTRIES = 20
    }
}

@Serializable
data class TaskRecord(
    val id: String,
    /** Small number that is unique among live tasks, used to derive notification ids. */
    val seq: Int,
    val source: TaskSource,
    val state: TaskState = TaskState.QUEUED,
    val idempotencyKey: String,
    /** Fixed when the task is created so that every retry sends exactly the same request. */
    val entryDate: String,
    val imageCount: Int,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    /** Identity of the connection the upload was (or is about to be) made with. */
    val configFingerprint: String? = null,
    val uploadAttempts: Int = 0,
    val lastError: String? = null,
    val sourceDocumentId: String? = null,
    val acceptedAt: Long? = null,
    val outcome: TaskOutcome? = null,
    val result: TaskResult? = null,
    val problem: TaskProblem? = null,
    /** A sentence from the server or a short note about what went wrong, shown to the user. */
    val message: String? = null,
) {
    val isFinished: Boolean get() = state == TaskState.DONE
}

@Serializable
data class TaskBook(
    val version: Int = 1,
    val nextSeq: Int = 1,
    val tasks: List<TaskRecord> = emptyList(),
)
