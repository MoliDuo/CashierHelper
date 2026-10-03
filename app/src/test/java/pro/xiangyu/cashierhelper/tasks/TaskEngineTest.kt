package pro.xiangyu.cashierhelper.tasks

import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pro.xiangyu.cashierhelper.api.ConnectionTest
import pro.xiangyu.cashierhelper.api.DocumentApi
import pro.xiangyu.cashierhelper.api.DocumentEntry
import pro.xiangyu.cashierhelper.api.DocumentResult
import pro.xiangyu.cashierhelper.api.DocumentStatus
import pro.xiangyu.cashierhelper.api.QueryOutcome
import pro.xiangyu.cashierhelper.api.TransientKind
import pro.xiangyu.cashierhelper.api.UploadOutcome
import pro.xiangyu.cashierhelper.config.AppConfig
import pro.xiangyu.cashierhelper.images.ImageFiles

class TaskEngineTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var clock = 10_000_000L
    private val api = FakeApi()
    private val events = mutableListOf<TaskRecord>()
    private var config: AppConfig? = AppConfig("https://cashier.example", "key-1")
    private lateinit var repository: TaskRepository
    private lateinit var images: ImageFiles
    private lateinit var engine: TaskEngine

    @org.junit.Before
    fun setUp() {
        repository = TaskRepository(TaskBookStore.create(File(temp.root, "tasks.json"), scope)) { clock }
        images = ImageFiles(temp.newFolder("images"))
        engine = TaskEngine(
            repository = repository,
            api = api,
            images = images,
            config = { config },
            listener = { events += it },
            now = { clock },
            sleep = { clock += it },
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun newTask(id: String = "t1", imageCount: Int = 1): TaskRecord {
        repeat(imageCount) { images.write(id, it, byteArrayOf(it.toByte())) }
        return repository.add {
            TaskRecord(
                id = id,
                seq = it,
                source = TaskSource.SCREENSHOT,
                idempotencyKey = "key-$id",
                entryDate = "2026-10-03",
                imageCount = imageCount,
                createdAt = clock,
            )
        }
    }

    private suspend fun task(id: String = "t1") = repository.get(id)!!

    private fun completed(entries: Int = 1) = QueryOutcome.Status(
        DocumentStatus.Completed(
            DocumentResult(
                title = "星巴克",
                total = "35.00",
                totalCurrency = "CNY",
                entries = List(entries) { DocumentEntry("项目$it", null, "1.00", "CNY", "餐饮") },
            ),
        ),
    )

    @Test
    fun `uploads then polls until the result is ready`() = runBlocking {
        newTask()
        api.queries += QueryOutcome.Status(DocumentStatus.Processing, 5_000)
        api.queries += completed()

        val result = engine.drive("t1", budgetMillis = 150_000)

        assertEquals(DriveResult.Finished, result)
        val done = task()
        assertEquals(TaskState.DONE, done.state)
        assertEquals(TaskOutcome.COMPLETED, done.outcome)
        assertEquals("星巴克", done.result!!.title)
        assertEquals("35.00", done.result!!.total)
        assertEquals("doc-1", done.sourceDocumentId)
        assertEquals(1, api.creates.size)
        assertEquals(2, api.gets)
        assertTrue("local images are removed once the server has them", images.list("t1").isEmpty())
    }

    @Test
    fun `the record is stored as processing before the local images are deleted`() = runBlocking {
        newTask()
        api.queries += QueryOutcome.Status(DocumentStatus.Processing, 5_000)
        var imagesWhenProcessing: Int? = null
        engine = TaskEngine(repository, api, images, { config }, listener = {
            if (it.state == TaskState.PROCESSING && imagesWhenProcessing == null) {
                imagesWhenProcessing = images.list("t1").size
            }
        }, now = { clock }, sleep = { clock += it })

        engine.drive("t1", budgetMillis = 1_000)

        assertEquals(1, imagesWhenProcessing)
    }

    @Test
    fun `a failed upload is retried with the same key date and images`() = runBlocking {
        newTask(imageCount = 2)
        api.uploads += UploadOutcome.Transient(TransientKind.NETWORK)
        api.uploads += UploadOutcome.Transient(TransientKind.SERVER)
        api.queries += completed()

        assertEquals(DriveResult.Retry, engine.drive("t1", 150_000))
        assertEquals(TaskState.QUEUED, task().state)
        assertEquals(1, task().uploadAttempts)
        assertEquals("NETWORK", task().lastError)

        assertEquals(DriveResult.Retry, engine.drive("t1", 150_000))
        assertEquals(2, task().uploadAttempts)

        assertEquals(DriveResult.Finished, engine.drive("t1", 150_000))
        assertEquals(TaskState.DONE, task().state)
        assertEquals(3, task().uploadAttempts)
        assertNull(task().lastError)

        assertEquals(3, api.creates.size)
        assertEquals(setOf("key-t1"), api.creates.map { it.key }.toSet())
        assertEquals(setOf("2026-10-03"), api.creates.map { it.date }.toSet())
        assertEquals(setOf(listOf("00.jpg", "01.jpg")), api.creates.map { it.files }.toSet())
    }

    @Test
    fun `permanent upload errors need the user and keep the images`() = runBlocking {
        val cases = listOf(
            UploadOutcome.Unauthorized to (TaskProblem.UNAUTHORIZED to null),
            UploadOutcome.Conflict to (TaskProblem.CONFLICT to null),
            UploadOutcome.Rejected(400, "VALIDATION_FAILED", "图片格式不支持") to (TaskProblem.REJECTED to "图片格式不支持"),
        )
        for ((index, case) in cases.withIndex()) {
            val id = "t$index"
            newTask(id)
            api.uploads += case.first

            assertEquals(DriveResult.Finished, engine.drive(id, 150_000))

            assertEquals(TaskState.NEEDS_ACTION, task(id).state)
            assertEquals(case.second.first, task(id).problem)
            assertEquals(case.second.second, task(id).message)
            assertEquals(1, images.list(id).size)
        }
    }

    @Test
    fun `a task without configuration waits for the user instead of failing`() = runBlocking {
        newTask()
        config = null

        assertEquals(DriveResult.Finished, engine.drive("t1", 150_000))

        assertEquals(TaskState.NEEDS_ACTION, task().state)
        assertEquals(TaskProblem.NOT_CONFIGURED, task().problem)
        assertTrue(api.creates.isEmpty())
    }

    @Test
    fun `a task that never reached the server moves to the new connection`() = runBlocking {
        newTask()
        config = null
        engine.drive("t1", 150_000)
        repository.update("t1") { it.copy(state = TaskState.QUEUED, problem = null, configFingerprint = "old") }
        config = AppConfig("https://other.example", "key-2")
        api.queries += completed()

        engine.drive("t1", 150_000)

        assertEquals(TaskState.DONE, task().state)
        assertEquals("https://other.example", api.creates.single().config.baseUrl)
    }

    @Test
    fun `a task that already tried the old connection is not replayed elsewhere`() = runBlocking {
        newTask()
        api.uploads += UploadOutcome.Transient(TransientKind.NETWORK)
        engine.drive("t1", 150_000)
        config = AppConfig("https://other.example", "key-2")

        assertEquals(DriveResult.Finished, engine.drive("t1", 150_000))

        assertEquals(TaskState.NEEDS_ACTION, task().state)
        assertEquals(TaskProblem.CONFIG_CHANGED, task().problem)
        assertEquals(1, api.creates.size)
    }

    @Test
    fun `missing image files are reported instead of uploading nothing`() = runBlocking {
        newTask(imageCount = 2)
        images.delete("t1")

        engine.drive("t1", 150_000)

        assertEquals(TaskProblem.IMAGES_MISSING, task().problem)
        assertTrue(api.creates.isEmpty())
    }

    @Test
    fun `polling honours Retry-After and hands back when the budget is spent`() = runBlocking {
        newTask()
        repeat(20) { api.queries += QueryOutcome.Status(DocumentStatus.Processing, 5_000) }
        val start = clock

        val result = engine.drive("t1", budgetMillis = 20_000)

        assertEquals(DriveResult.Retry, result)
        assertEquals(TaskState.PROCESSING, task().state)
        assertTrue(clock - start < 20_000)
        // Polls at 0, 5, 10 and 15 seconds; a fifth wait would run past the 20 second budget.
        assertEquals(4, api.gets)
        assertTrue(images.list("t1").isEmpty())
    }

    @Test
    fun `a processing task resumes by polling and never uploads again`() = runBlocking {
        newTask()
        api.queries += QueryOutcome.Status(DocumentStatus.Processing, 5_000)
        engine.drive("t1", budgetMillis = 3_000)
        api.queries.clear()
        api.queries += completed()

        engine.drive("t1", 150_000)

        assertEquals(1, api.creates.size)
        assertEquals(TaskState.DONE, task().state)
    }

    @Test
    fun `terminal server statuses end the task with the matching outcome`() = runBlocking {
        val cases = listOf(
            QueryOutcome.Status(DocumentStatus.Invalid("这是一张退款单据")) to (TaskOutcome.INVALID to "这是一张退款单据"),
            QueryOutcome.Status(DocumentStatus.Failed("processing_timeout", "超时")) to (TaskOutcome.FAILED to "超时"),
            QueryOutcome.Status(DocumentStatus.Cancelled) to (TaskOutcome.CANCELLED to null),
            QueryOutcome.NotFound to (TaskOutcome.DELETED_ON_SERVER to null),
        )
        for ((index, case) in cases.withIndex()) {
            val id = "t$index"
            newTask(id)
            api.queries += case.first

            engine.drive(id, 150_000)

            assertEquals(TaskState.DONE, task(id).state)
            assertEquals(case.second.first, task(id).outcome)
            assertEquals(case.second.second, task(id).message)
        }
        assertEquals("processing_timeout", task("t1").lastError)
    }

    @Test
    fun `a rejected key while polling needs the user and a hiccup just retries`() = runBlocking {
        newTask()
        api.queries += QueryOutcome.Transient(TransientKind.SERVER)
        assertEquals(DriveResult.Retry, engine.drive("t1", 150_000))
        assertEquals(TaskState.PROCESSING, task().state)

        api.queries += QueryOutcome.Unauthorized
        assertEquals(DriveResult.Finished, engine.drive("t1", 150_000))
        assertEquals(TaskState.NEEDS_ACTION, task().state)
        assertEquals(TaskProblem.UNAUTHORIZED, task().problem)
    }

    @Test
    fun `a task is given up on after a day without a result`() = runBlocking {
        newTask()
        api.queries += QueryOutcome.Status(DocumentStatus.Processing, 5_000)
        engine.drive("t1", budgetMillis = 3_000)
        clock += TaskEngine.GIVE_UP_MILLIS + 1

        assertEquals(DriveResult.Finished, engine.drive("t1", 150_000))

        assertEquals(TaskOutcome.NO_RESULT, task().outcome)
        assertEquals(1, api.gets)
    }

    @Test
    fun `only twenty entries are kept but the real count is recorded`() = runBlocking {
        newTask()
        api.queries += completed(entries = 27)

        engine.drive("t1", 150_000)

        assertEquals(20, task().result!!.entries.size)
        assertEquals(27, task().result!!.entryCount)
    }

    @Test
    fun `deleting a task during the upload does not bring it back`() = runBlocking {
        newTask()
        api.onCreate = { runBlocking { repository.delete("t1") } }

        assertEquals(DriveResult.Finished, engine.drive("t1", 150_000))

        assertNull(repository.get("t1"))
        assertEquals(0, api.gets)
    }

    @Test
    fun `cancelling mid upload keeps the task resumable`() = runBlocking {
        newTask()
        api.uploads += CancellationException("worker stopped")
        try {
            engine.drive("t1", 150_000)
            throw AssertionError("expected cancellation to propagate")
        } catch (expected: CancellationException) {
            // expected
        }
        assertEquals(TaskState.UPLOADING, task().state)

        api.queries += completed()
        engine.drive("t1", 150_000)

        assertEquals(TaskState.DONE, task().state)
        assertEquals("key-t1", api.creates.last().key)
    }

    @Test
    fun `finished and unknown tasks are left alone`() = runBlocking {
        newTask()
        api.queries += completed()
        engine.drive("t1", 150_000)
        val before = api.creates.size

        assertEquals(DriveResult.Finished, engine.drive("t1", 150_000))
        assertEquals(DriveResult.Finished, engine.drive("missing", 150_000))
        assertEquals(before, api.creates.size)
    }

    private class Create(val config: AppConfig, val files: List<String>, val date: String, val key: String)

    private class FakeApi : DocumentApi {
        val creates = mutableListOf<Create>()
        val uploads = ArrayDeque<Any>()
        val queries = ArrayDeque<QueryOutcome>()
        var gets = 0
        var onCreate: () -> Unit = {}

        override suspend fun create(
            config: AppConfig,
            images: List<File>,
            entryDate: String,
            idempotencyKey: String,
        ): UploadOutcome {
            creates += Create(config, images.map { it.name }, entryDate, idempotencyKey)
            onCreate()
            return when (val next = uploads.removeFirstOrNull()) {
                null -> UploadOutcome.Accepted("doc-1")
                is CancellationException -> throw next
                else -> next as UploadOutcome
            }
        }

        override suspend fun get(config: AppConfig, sourceDocumentId: String): QueryOutcome {
            gets++
            return queries.removeFirstOrNull() ?: QueryOutcome.Status(DocumentStatus.Processing, 5_000)
        }

        override suspend fun testConnection(baseUrl: String, apiKey: String) = ConnectionTest.OK
    }
}
