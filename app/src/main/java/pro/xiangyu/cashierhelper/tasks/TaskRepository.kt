package pro.xiangyu.cashierhelper.tasks

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * All task records. Writes only ever change a record that still exists, so a
 * background worker finishing late can never bring back a task the user deleted.
 */
class TaskRepository(
    private val store: DataStore<TaskBook>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val tasks: Flow<List<TaskRecord>> = store.data.map { it.tasks }

    suspend fun all(): List<TaskRecord> = tasks.first()

    suspend fun get(id: String): TaskRecord? = all().firstOrNull { it.id == id }

    /** Adds a task; [build] receives the sequence number reserved for it. */
    suspend fun add(build: (seq: Int) -> TaskRecord): TaskRecord {
        var created: TaskRecord? = null
        store.updateData { book ->
            val record = build(book.nextSeq)
            created = record
            book.copy(
                nextSeq = if (book.nextSeq >= MAX_SEQ) 1 else book.nextSeq + 1,
                tasks = book.tasks + record,
            )
        }
        return checkNotNull(created)
    }

    /** Applies [change] to the task and returns the stored result, or null if the task no longer exists. */
    suspend fun update(id: String, change: (TaskRecord) -> TaskRecord): TaskRecord? {
        var updated: TaskRecord? = null
        store.updateData { book ->
            val index = book.tasks.indexOfFirst { it.id == id }
            if (index < 0) {
                book
            } else {
                val record = change(book.tasks[index]).copy(updatedAt = clock())
                updated = record
                book.copy(tasks = book.tasks.toMutableList().also { it[index] = record })
            }
        }
        return updated
    }

    suspend fun delete(id: String): Boolean {
        var removed = false
        store.updateData { book ->
            removed = book.tasks.any { it.id == id }
            if (removed) book.copy(tasks = book.tasks.filterNot { it.id == id }) else book
        }
        return removed
    }

    /**
     * Drops finished tasks that are old or beyond the newest [MAX_FINISHED].
     * Unfinished tasks are never touched. Returns the ids that were removed.
     */
    suspend fun pruneFinished(): List<String> {
        val removed = mutableListOf<String>()
        val cutoff = clock() - FINISHED_RETENTION_MILLIS
        store.updateData { book ->
            val finished = book.tasks.filter { it.isFinished }.sortedByDescending { it.updatedAt }
            val drop = finished.filterIndexed { index, task -> index >= MAX_FINISHED || task.updatedAt < cutoff }
                .map { it.id }
                .toSet()
            removed.clear()
            removed += drop
            if (drop.isEmpty()) book else book.copy(tasks = book.tasks.filterNot { it.id in drop })
        }
        return removed
    }

    companion object {
        const val MAX_SEQ = 9_999
        const val MAX_FINISHED = 50
        const val FINISHED_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
