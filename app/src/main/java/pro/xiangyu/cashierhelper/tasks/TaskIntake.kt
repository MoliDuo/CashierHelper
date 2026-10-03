package pro.xiangyu.cashierhelper.tasks

import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import pro.xiangyu.cashierhelper.images.EntryDates
import pro.xiangyu.cashierhelper.images.ImageFiles

/**
 * Everything that creates, changes or removes a task from outside the engine:
 * a new screenshot, the user's retry or delete, and the connection being fixed.
 */
class TaskIntake(
    private val repository: TaskRepository,
    private val images: ImageFiles,
    private val queue: TaskQueue,
    private val listener: TaskListener,
    private val onRemoved: (TaskRecord) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** Stores the encoded images first, then the record, so a record never points at missing files. */
    suspend fun create(source: TaskSource, jpegs: List<ByteArray>, entryDate: String? = null): TaskRecord {
        require(jpegs.size in 1..MAX_IMAGES) { "A task holds 1 to $MAX_IMAGES images" }
        val id = newId()
        try {
            jpegs.forEachIndexed { index, bytes -> images.write(id, index, bytes) }
            val record = repository.add { seq ->
                TaskRecord(
                    id = id,
                    seq = seq,
                    source = source,
                    idempotencyKey = newId(),
                    entryDate = entryDate ?: EntryDates.today(Instant.ofEpochMilli(now()), zone()),
                    imageCount = jpegs.size,
                    createdAt = now(),
                )
            }
            listener.onTask(record)
            queue.enqueue(id)
            return record
        } catch (error: Throwable) {
            images.delete(id)
            throw error
        }
    }

    suspend fun createFromScreenshot(jpeg: ByteArray): TaskRecord = create(TaskSource.SCREENSHOT, listOf(jpeg))

    /** The user pressed "retry" or "retry now". */
    suspend fun retry(id: String) {
        val record = repository.get(id) ?: return
        val updated = when (record.state) {
            TaskState.NEEDS_ACTION -> repository.update(id) { requeue(it, newKey = it.problem == TaskProblem.CONFLICT) }
            TaskState.QUEUED, TaskState.UPLOADING, TaskState.PROCESSING -> record
            TaskState.DONE -> return
        } ?: return
        listener.onTask(updated)
        queue.enqueue(id, restart = true)
    }

    /** A new connection was saved: whatever waited for it can go on. */
    suspend fun connectionFixed() {
        for (record in repository.all()) {
            if (record.state != TaskState.NEEDS_ACTION) continue
            if (record.problem !in WAITING_FOR_CONNECTION) continue
            val updated = repository.update(record.id) { requeue(it, newKey = false) } ?: continue
            listener.onTask(updated)
            queue.enqueue(record.id, restart = true)
        }
    }

    /** Removes the task: stop the work first so a late write cannot bring it back. */
    suspend fun delete(id: String) {
        val record = repository.get(id)
        queue.cancel(id)
        repository.delete(id)
        images.delete(id)
        if (record != null) onRemoved(record)
    }

    /** At app start: re-queue everything that is unfinished and clear leftovers. */
    suspend fun reconcile() {
        for (record in repository.all()) {
            if (record.state == TaskState.QUEUED || record.state == TaskState.UPLOADING ||
                record.state == TaskState.PROCESSING
            ) {
                queue.enqueue(record.id)
            }
        }
        for (id in repository.pruneFinished()) images.delete(id)
        val known = repository.all().map { it.id }.toSet()
        val cutoff = now() - ORPHAN_GRACE_MILLIS
        for (id in images.taskIds() - known) {
            // A folder this young may belong to a task that is being created right now.
            if (images.dir(id).lastModified() < cutoff) images.delete(id)
        }
    }

    private fun requeue(record: TaskRecord, newKey: Boolean): TaskRecord = record.copy(
        state = if (record.sourceDocumentId != null) TaskState.PROCESSING else TaskState.QUEUED,
        problem = null,
        message = null,
        idempotencyKey = if (newKey) newId() else record.idempotencyKey,
        configFingerprint = if (record.problem == TaskProblem.CONFIG_CHANGED) null else record.configFingerprint,
    )

    companion object {
        const val MAX_IMAGES = 3
        private const val ORPHAN_GRACE_MILLIS = 60 * 60 * 1000L
        private val WAITING_FOR_CONNECTION =
            setOf(TaskProblem.UNAUTHORIZED, TaskProblem.NOT_CONFIGURED, TaskProblem.CONFIG_CHANGED)
    }
}
