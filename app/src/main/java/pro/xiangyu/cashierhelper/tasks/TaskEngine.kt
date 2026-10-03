package pro.xiangyu.cashierhelper.tasks

import kotlinx.coroutines.delay
import pro.xiangyu.cashierhelper.api.DocumentApi
import pro.xiangyu.cashierhelper.api.DocumentStatus
import pro.xiangyu.cashierhelper.api.QueryOutcome
import pro.xiangyu.cashierhelper.api.UploadOutcome
import pro.xiangyu.cashierhelper.config.AppConfig
import pro.xiangyu.cashierhelper.config.ConfigFingerprint
import pro.xiangyu.cashierhelper.images.ImageFiles

sealed interface DriveResult {
    /** Nothing more to do now: the task is done, needs the user, or no longer exists. */
    data object Finished : DriveResult

    /** Try again later; the scheduler decides when. */
    data object Retry : DriveResult
}

/** Told whenever a task changes or is polled, so the notification can follow along. */
fun interface TaskListener {
    fun onTask(record: TaskRecord)
}

/**
 * Moves one task forward: upload, then wait for the analysis. It never throws
 * for network trouble and never deletes anything it did not finish; a task only
 * ends in [TaskState.DONE] or [TaskState.NEEDS_ACTION] for a reason it can name.
 */
class TaskEngine(
    private val repository: TaskRepository,
    private val api: DocumentApi,
    private val images: ImageFiles,
    private val config: suspend () -> AppConfig?,
    private val listener: TaskListener = TaskListener {},
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun drive(id: String, budgetMillis: Long): DriveResult {
        val deadline = now() + budgetMillis
        while (true) {
            val record = repository.get(id) ?: return DriveResult.Finished
            val step = when (record.state) {
                TaskState.DONE, TaskState.NEEDS_ACTION -> return DriveResult.Finished
                TaskState.QUEUED, TaskState.UPLOADING -> upload(record)
                TaskState.PROCESSING -> poll(record, deadline)
            }
            when (step) {
                Step.Again -> continue
                Step.Finished -> return DriveResult.Finished
                Step.Retry -> return DriveResult.Retry
            }
        }
    }

    private enum class Step { Again, Finished, Retry }

    private suspend fun upload(record: TaskRecord): Step {
        val current = config() ?: return needsAction(record, TaskProblem.NOT_CONFIGURED)
        val fingerprint = ConfigFingerprint.of(current)

        // A task that never reached the server can move to a new connection; one that
        // did try may have been stored there under the old credentials.
        val bound = record.configFingerprint
        if (bound != null && bound != fingerprint && record.uploadAttempts > 0) {
            return needsAction(record, TaskProblem.CONFIG_CHANGED)
        }
        val files = images.list(record.id)
        if (files.isEmpty() || files.size != record.imageCount) {
            return needsAction(record, TaskProblem.IMAGES_MISSING)
        }

        // Counted and stored before the request, so a crash mid-upload still shows as an attempt.
        val uploading = save(record.id) {
            it.copy(
                state = TaskState.UPLOADING,
                configFingerprint = fingerprint,
                uploadAttempts = it.uploadAttempts + 1,
            )
        } ?: return Step.Finished

        return when (val outcome = api.create(current, files, uploading.entryDate, uploading.idempotencyKey)) {
            is UploadOutcome.Accepted -> {
                // The record is written first; the images are only deleted once the server has them.
                save(record.id) {
                    it.copy(
                        state = TaskState.PROCESSING,
                        sourceDocumentId = outcome.sourceDocumentId,
                        acceptedAt = now(),
                        lastError = null,
                    )
                } ?: return Step.Finished
                images.delete(record.id)
                Step.Again
            }
            is UploadOutcome.Transient -> {
                save(record.id) { it.copy(state = TaskState.QUEUED, lastError = outcome.kind.name) }
                    ?: return Step.Finished
                Step.Retry
            }
            UploadOutcome.Unauthorized -> needsAction(uploading, TaskProblem.UNAUTHORIZED)
            UploadOutcome.Conflict -> needsAction(uploading, TaskProblem.CONFLICT)
            is UploadOutcome.Rejected -> needsAction(uploading, TaskProblem.REJECTED, outcome.message)
        }
    }

    private suspend fun poll(record: TaskRecord, deadline: Long): Step {
        val documentId = record.sourceDocumentId
        val acceptedAt = record.acceptedAt ?: record.createdAt
        if (documentId == null) return finish(record, TaskOutcome.NO_RESULT)
        if (now() - acceptedAt > GIVE_UP_MILLIS) return finish(record, TaskOutcome.NO_RESULT)
        val current = config() ?: return needsAction(record, TaskProblem.NOT_CONFIGURED)

        return when (val outcome = api.get(current, documentId)) {
            is QueryOutcome.Status -> when (val status = outcome.status) {
                DocumentStatus.Processing -> {
                    listener.onTask(record)
                    val wait = (outcome.retryAfterMillis ?: DEFAULT_POLL_MILLIS).coerceIn(MIN_POLL_MILLIS, MAX_POLL_MILLIS)
                    if (now() + wait >= deadline) return Step.Retry
                    sleep(wait)
                    Step.Again
                }
                is DocumentStatus.Completed -> finish(record, TaskOutcome.COMPLETED, result = status.result.toTaskResult())
                is DocumentStatus.Invalid -> finish(record, TaskOutcome.INVALID, message = status.message)
                is DocumentStatus.Failed -> finish(record, TaskOutcome.FAILED, message = status.message, error = status.code)
                DocumentStatus.Cancelled -> finish(record, TaskOutcome.CANCELLED)
            }
            QueryOutcome.NotFound -> finish(record, TaskOutcome.DELETED_ON_SERVER)
            QueryOutcome.Unauthorized -> needsAction(record, TaskProblem.UNAUTHORIZED)
            is QueryOutcome.Transient -> Step.Retry
        }
    }

    private suspend fun needsAction(record: TaskRecord, problem: TaskProblem, message: String? = null): Step {
        save(record.id) {
            it.copy(state = TaskState.NEEDS_ACTION, problem = problem, message = message)
        }
        return Step.Finished
    }

    private suspend fun finish(
        record: TaskRecord,
        outcome: TaskOutcome,
        result: TaskResult? = null,
        message: String? = null,
        error: String? = null,
    ): Step {
        save(record.id) {
            it.copy(
                state = TaskState.DONE,
                outcome = outcome,
                result = result,
                message = message,
                lastError = error,
                problem = null,
            )
        }
        return Step.Finished
    }

    private suspend fun save(id: String, change: (TaskRecord) -> TaskRecord): TaskRecord? {
        val updated = repository.update(id, change)
        if (updated != null) listener.onTask(updated)
        return updated
    }

    companion object {
        const val DEFAULT_POLL_MILLIS = 5_000L
        const val MIN_POLL_MILLIS = 2_000L
        const val MAX_POLL_MILLIS = 30_000L
        const val GIVE_UP_MILLIS = 24L * 60 * 60 * 1000
    }
}

private fun pro.xiangyu.cashierhelper.api.DocumentResult.toTaskResult() = TaskResult(
    title = title,
    total = total,
    totalCurrency = totalCurrency,
    entries = entries.take(TaskResult.MAX_ENTRIES).map {
        TaskEntry(name = it.name, amount = it.amount, currency = it.currency, category = it.category)
    },
    entryCount = entries.size,
)
