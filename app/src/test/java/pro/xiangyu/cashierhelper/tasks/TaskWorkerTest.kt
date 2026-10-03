package pro.xiangyu.cashierhelper.tasks

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pro.xiangyu.cashierhelper.api.ConnectionTest
import pro.xiangyu.cashierhelper.api.DocumentApi
import pro.xiangyu.cashierhelper.api.DocumentStatus
import pro.xiangyu.cashierhelper.api.QueryOutcome
import pro.xiangyu.cashierhelper.api.TransientKind
import pro.xiangyu.cashierhelper.api.UploadOutcome
import pro.xiangyu.cashierhelper.config.AppConfig
import pro.xiangyu.cashierhelper.images.ImageFiles

/** The application the worker finds; it hands out an engine wired to fakes. */
class TestApp : Application(), TaskEngineProvider {
    override lateinit var taskEngine: TaskEngine
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestApp::class)
class TaskWorkerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: TaskRepository
    private lateinit var images: ImageFiles
    private var upload: () -> UploadOutcome = { UploadOutcome.Accepted("doc-1") }
    private var query: () -> QueryOutcome = {
        QueryOutcome.Status(DocumentStatus.Cancelled)
    }

    @Before
    fun setUp() {
        repository = TaskRepository(TaskBookStore.create(File(temp.root, "tasks.json"), scope))
        images = ImageFiles(temp.newFolder("images"))
        val api = object : DocumentApi {
            override suspend fun create(config: AppConfig, images: List<File>, entryDate: String, idempotencyKey: String) =
                upload()

            override suspend fun get(config: AppConfig, sourceDocumentId: String) = query()
            override suspend fun testConnection(baseUrl: String, apiKey: String) = ConnectionTest.OK
        }
        (context as TestApp).taskEngine = TaskEngine(
            repository, api, images, config = { AppConfig("https://cashier.example", "key") },
            sleep = {},
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun newTask(): String {
        images.write("t1", 0, byteArrayOf(1))
        repository.add {
            TaskRecord(
                id = "t1", seq = it, source = TaskSource.SCREENSHOT, idempotencyKey = "k",
                entryDate = "2026-10-03", imageCount = 1, createdAt = 0,
            )
        }
        return "t1"
    }

    private fun run(id: String?): ListenableWorker.Result = runBlocking {
        val builder = TestListenableWorkerBuilder<TaskWorker>(context)
        if (id != null) builder.setInputData(workDataOf(TaskWorker.KEY_TASK_ID to id))
        builder.build().doWork()
    }

    @Test
    fun `a task that finishes is a success`() = runBlocking {
        val id = newTask()

        assertEquals(ListenableWorker.Result.success(), run(id))
        assertEquals(TaskState.DONE, repository.get(id)!!.state)
    }

    @Test
    fun `a transient failure asks WorkManager to retry`() = runBlocking {
        val id = newTask()
        upload = { UploadOutcome.Transient(TransientKind.NETWORK) }

        assertEquals(ListenableWorker.Result.retry(), run(id))
        assertEquals(TaskState.QUEUED, repository.get(id)!!.state)
    }

    @Test
    fun `an unexpected error retries instead of stranding the task`() = runBlocking {
        val id = newTask()
        upload = { throw IOException("disk trouble") }

        assertEquals(ListenableWorker.Result.retry(), run(id))
    }

    @Test
    fun `work without a task id fails`() {
        assertEquals(ListenableWorker.Result.failure(), run(null))
    }

    @Test
    fun `a task that needs the user ends the work without retrying`() = runBlocking {
        val id = newTask()
        upload = { UploadOutcome.Unauthorized }

        assertEquals(ListenableWorker.Result.success(), run(id))
        assertEquals(TaskState.NEEDS_ACTION, repository.get(id)!!.state)
    }
}
