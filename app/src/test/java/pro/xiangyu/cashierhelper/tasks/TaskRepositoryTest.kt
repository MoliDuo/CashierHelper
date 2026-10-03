package pro.xiangyu.cashierhelper.tasks

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TaskRepositoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var now = 1_000_000L

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun repository(file: File = File(temp.root, "tasks.json")) =
        TaskRepository(TaskBookStore.create(file, scope)) { now }

    private fun record(id: String, seq: Int, state: TaskState = TaskState.QUEUED) = TaskRecord(
        id = id,
        seq = seq,
        source = TaskSource.SCREENSHOT,
        state = state,
        idempotencyKey = "key-$id",
        entryDate = "2026-10-03",
        imageCount = 1,
        createdAt = now,
    )

    @Test
    fun `add hands out increasing sequence numbers and survives a restart`() = runBlocking {
        val file = File(temp.root, "tasks.json")
        val first = repository(file)
        val a = first.add { record("a", it) }
        val b = first.add { record("b", it) }
        assertEquals(listOf(1, 2), listOf(a.seq, b.seq))

        // A second store over the same file sees what the first one wrote.
        // DataStore only lets go of the file once its scope has finished, so wait for that.
        scope.coroutineContext[Job]!!.cancelAndJoin()
        val reopened = TaskRepository(
            TaskBookStore.create(file, CoroutineScope(SupervisorJob() + Dispatchers.IO)),
        ) { now }
        assertEquals(listOf("a", "b"), reopened.all().map { it.id })
        assertEquals(3, reopened.add { record("c", it) }.seq)
    }

    @Test
    fun `update changes an existing task and stamps the time`() = runBlocking {
        val repository = repository()
        repository.add { record("a", it) }
        now += 500

        val updated = repository.update("a") { it.copy(uploadAttempts = 3) }

        assertEquals(3, updated!!.uploadAttempts)
        assertEquals(now, updated.updatedAt)
        assertEquals(updated, repository.get("a"))
    }

    @Test
    fun `update never creates a task that was deleted`() = runBlocking {
        val repository = repository()
        repository.add { record("a", it) }
        assertTrue(repository.delete("a"))

        assertNull(repository.update("a") { it.copy(state = TaskState.DONE) })
        assertNull(repository.get("a"))
        assertFalse(repository.delete("a"))
    }

    @Test
    fun `an unreadable file is set aside and the app starts empty`() = runBlocking {
        val file = File(temp.root, "tasks.json").apply { writeText("{ this is not json") }
        val repository = repository(file)

        assertTrue(repository.all().isEmpty())
        repository.add { record("a", it) }

        val setAside = temp.root.listFiles { f -> f.name.startsWith("tasks.json.corrupt-") }!!
        assertEquals(1, setAside.size)
        assertEquals("{ this is not json", setAside.single().readText())
    }

    @Test
    fun `unknown fields in the file are ignored`() = runBlocking {
        val file = File(temp.root, "tasks.json").apply {
            writeText("""{"version":1,"nextSeq":5,"future":true,"tasks":[]}""")
        }
        val repository = repository(file)

        assertEquals(5, repository.add { record("a", it) }.seq)
    }

    @Test
    fun `prune drops old finished tasks and keeps everything unfinished`() = runBlocking {
        val repository = repository()
        repository.add { record("queued", it) }
        repository.add { record("processing", it, TaskState.PROCESSING) }
        repository.add { record("needs", it, TaskState.NEEDS_ACTION) }
        repository.add { record("old", it, TaskState.DONE) }
        now += TaskRepository.FINISHED_RETENTION_MILLIS + 1
        repository.add { record("fresh", it, TaskState.DONE) }

        val removed = repository.pruneFinished()

        assertEquals(listOf("old"), removed)
        assertEquals(setOf("queued", "processing", "needs", "fresh"), repository.all().map { it.id }.toSet())
    }

    @Test
    fun `prune keeps only the newest finished tasks`() = runBlocking {
        val repository = repository()
        repeat(TaskRepository.MAX_FINISHED + 5) { index ->
            now += 1
            repository.add { record("t$index", it, TaskState.DONE) }
        }

        val removed = repository.pruneFinished()

        assertEquals(5, removed.size)
        assertEquals((0 until 5).map { "t$it" }.toSet(), removed.toSet())
        assertEquals(TaskRepository.MAX_FINISHED, repository.all().size)
    }

    @Test
    fun `sequence numbers wrap around`() = runBlocking {
        val file = File(temp.root, "tasks.json").apply {
            writeText("""{"nextSeq":${TaskRepository.MAX_SEQ},"tasks":[]}""")
        }
        val repository = repository(file)

        assertEquals(TaskRepository.MAX_SEQ, repository.add { record("a", it) }.seq)
        assertEquals(1, repository.add { record("b", it) }.seq)
    }
}
