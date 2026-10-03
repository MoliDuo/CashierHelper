package pro.xiangyu.cashierhelper.tasks

import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pro.xiangyu.cashierhelper.images.ImageFiles

class TaskIntakeTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var clock = 1_760_000_000_000L // 2025-10-09
    private var counter = 0
    private val queued = mutableListOf<Pair<String, Boolean>>()
    private val cancelled = mutableListOf<String>()
    private val seen = mutableListOf<TaskRecord>()
    private val removed = mutableListOf<TaskRecord>()
    private lateinit var repository: TaskRepository
    private lateinit var images: ImageFiles
    private lateinit var intake: TaskIntake

    @Before
    fun setUp() {
        repository = TaskRepository(TaskBookStore.create(File(temp.root, "tasks.json"), scope)) { clock }
        images = ImageFiles(temp.newFolder("images"))
        intake = TaskIntake(
            repository = repository,
            images = images,
            queue = object : TaskQueue {
                override fun enqueue(id: String, restart: Boolean) {
                    queued += id to restart
                }

                override fun cancel(id: String) {
                    cancelled += id
                }
            },
            listener = { seen += it },
            onRemoved = { removed += it },
            now = { clock },
            zone = { ZoneId.of("UTC") },
            newId = { "id-${++counter}" },
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun problem(id: String, problem: TaskProblem, documentId: String? = null) {
        repository.update(id) {
            it.copy(
                state = TaskState.NEEDS_ACTION,
                problem = problem,
                message = "x",
                sourceDocumentId = documentId,
                configFingerprint = "old",
                uploadAttempts = 2,
            )
        }
    }

    @Test
    fun `a screenshot becomes a queued task with its images on disk`() = runBlocking {
        val record = intake.createFromScreenshot(byteArrayOf(1, 2, 3))

        assertEquals(TaskState.QUEUED, record.state)
        assertEquals(TaskSource.SCREENSHOT, record.source)
        assertEquals("2025-10-09", record.entryDate)
        assertEquals(1, record.imageCount)
        assertNotEquals(record.id, record.idempotencyKey)
        assertEquals(listOf("00.jpg"), images.list(record.id).map { it.name })
        assertEquals(listOf(record.id to false), queued)
        assertEquals(listOf(record), seen)
        assertEquals(record, repository.get(record.id))
    }

    @Test
    fun `an explicit date and several images are kept`() = runBlocking {
        val record = intake.create(TaskSource.SHARE, listOf(byteArrayOf(1), byteArrayOf(2)), entryDate = "2025-09-30")

        assertEquals("2025-09-30", record.entryDate)
        assertEquals(2, record.imageCount)
        assertEquals(2, images.list(record.id).size)
    }

    @Test
    fun `a failure while storing leaves no images behind`() = runBlocking {
        val broken = TaskIntake(
            repository = repository,
            images = images,
            queue = object : TaskQueue {
                override fun enqueue(id: String, restart: Boolean) = error("queue unavailable")
                override fun cancel(id: String) = Unit
            },
            listener = {},
            onRemoved = {},
            newId = { "id-x" },
        )

        try {
            broken.createFromScreenshot(byteArrayOf(1))
            throw AssertionError("expected failure")
        } catch (expected: IllegalStateException) {
            // expected
        }

        assertTrue(images.list("id-x").isEmpty())
    }

    @Test
    fun `retry puts a task that needs her back in the queue with the same key`() = runBlocking {
        val record = intake.createFromScreenshot(byteArrayOf(1))
        problem(record.id, TaskProblem.REJECTED)
        queued.clear()

        intake.retry(record.id)

        val after = repository.get(record.id)!!
        assertEquals(TaskState.QUEUED, after.state)
        assertNull(after.problem)
        assertNull(after.message)
        assertEquals(record.idempotencyKey, after.idempotencyKey)
        assertEquals(listOf(record.id to true), queued)
    }

    @Test
    fun `retry of a conflict uses a new idempotency key`() = runBlocking {
        val record = intake.createFromScreenshot(byteArrayOf(1))
        problem(record.id, TaskProblem.CONFLICT)

        intake.retry(record.id)

        assertNotEquals(record.idempotencyKey, repository.get(record.id)!!.idempotencyKey)
    }

    @Test
    fun `retry after a changed connection forgets the old binding`() = runBlocking {
        val record = intake.createFromScreenshot(byteArrayOf(1))
        problem(record.id, TaskProblem.CONFIG_CHANGED)

        intake.retry(record.id)

        assertNull(repository.get(record.id)!!.configFingerprint)
    }

    @Test
    fun `a task that already reached the server resumes by polling`() = runBlocking {
        val record = intake.createFromScreenshot(byteArrayOf(1))
        problem(record.id, TaskProblem.UNAUTHORIZED, documentId = "doc-1")

        intake.retry(record.id)

        assertEquals(TaskState.PROCESSING, repository.get(record.id)!!.state)
    }

    @Test
    fun `retry now restarts a task that is waiting for its next attempt`() = runBlocking {
        val record = intake.createFromScreenshot(byteArrayOf(1))
        queued.clear()

        intake.retry(record.id)

        assertEquals(listOf(record.id to true), queued)
        assertEquals(TaskState.QUEUED, repository.get(record.id)!!.state)
    }

    @Test
    fun `a finished or unknown task is not retried`() = runBlocking {
        val record = intake.createFromScreenshot(byteArrayOf(1))
        repository.update(record.id) { it.copy(state = TaskState.DONE) }
        queued.clear()

        intake.retry(record.id)
        intake.retry("missing")

        assertTrue(queued.isEmpty())
    }

    @Test
    fun `saving a working connection restarts what waited for it`() = runBlocking {
        val key = intake.createFromScreenshot(byteArrayOf(1))
        val config = intake.createFromScreenshot(byteArrayOf(2))
        val changed = intake.createFromScreenshot(byteArrayOf(3))
        val rejected = intake.createFromScreenshot(byteArrayOf(4))
        problem(key.id, TaskProblem.UNAUTHORIZED)
        problem(config.id, TaskProblem.NOT_CONFIGURED)
        problem(changed.id, TaskProblem.CONFIG_CHANGED)
        problem(rejected.id, TaskProblem.REJECTED)
        queued.clear()

        intake.connectionFixed()

        assertEquals(TaskState.QUEUED, repository.get(key.id)!!.state)
        assertEquals(TaskState.QUEUED, repository.get(config.id)!!.state)
        assertEquals(TaskState.QUEUED, repository.get(changed.id)!!.state)
        assertNull(repository.get(changed.id)!!.configFingerprint)
        assertEquals(TaskState.NEEDS_ACTION, repository.get(rejected.id)!!.state)
        assertEquals(setOf(key.id, config.id, changed.id), queued.map { it.first }.toSet())
    }

    @Test
    fun `delete stops the work first and removes the record and images`() = runBlocking {
        val record = intake.createFromScreenshot(byteArrayOf(1))

        intake.delete(record.id)

        assertEquals(listOf(record.id), cancelled)
        assertNull(repository.get(record.id))
        assertTrue(images.list(record.id).isEmpty())
        assertEquals(listOf(record), removed)
    }

    @Test
    fun `reconcile re-queues unfinished tasks only and clears old orphan folders`() = runBlocking {
        val waiting = intake.createFromScreenshot(byteArrayOf(1))
        val needs = intake.createFromScreenshot(byteArrayOf(2))
        val done = intake.createFromScreenshot(byteArrayOf(3))
        problem(needs.id, TaskProblem.REJECTED)
        repository.update(done.id) { it.copy(state = TaskState.DONE) }
        images.write("orphan-old", 0, byteArrayOf(9))
        images.write("orphan-new", 0, byteArrayOf(9))
        images.dir("orphan-old").setLastModified(clock - 2 * 60 * 60 * 1000)
        images.dir("orphan-new").setLastModified(clock)
        queued.clear()

        intake.reconcile()

        assertEquals(listOf(waiting.id to false), queued)
        assertTrue(images.list("orphan-old").isEmpty())
        assertEquals(1, images.list("orphan-new").size)
        assertEquals(1, images.list(needs.id).size)
    }
}
