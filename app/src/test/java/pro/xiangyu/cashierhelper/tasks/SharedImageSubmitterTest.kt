package pro.xiangyu.cashierhelper.tasks

import android.net.Uri
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pro.xiangyu.cashierhelper.images.ImageFiles
import pro.xiangyu.cashierhelper.images.LoadedImage

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedImageSubmitterTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val now = Instant.parse("2025-10-09T08:00:00Z")
    private val taken = Instant.parse("2025-10-03T10:00:00Z").toEpochMilli()
    private var counter = 0
    private lateinit var repository: TaskRepository
    private lateinit var intake: TaskIntake
    private val unreadable = mutableSetOf<Uri>()

    private val loader = pro.xiangyu.cashierhelper.images.SharedImageLoader { uri ->
        if (uri in unreadable) null else LoadedImage(uri.toString().toByteArray(), taken)
    }

    private fun uri(name: String): Uri = Uri.parse("content://media/$name")

    @Before
    fun setUp() {
        repository = TaskRepository(TaskBookStore.create(File(temp.root, "tasks.json"), scope)) { now.toEpochMilli() }
        intake = TaskIntake(
            repository = repository,
            images = ImageFiles(temp.newFolder("images")),
            queue = object : TaskQueue {
                override fun enqueue(id: String, restart: Boolean) = Unit
                override fun cancel(id: String) = Unit
            },
            listener = { },
            onRemoved = { },
            now = { now.toEpochMilli() },
            zone = { ZoneId.of("UTC") },
            newId = { "id-${++counter}" },
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun submitter() = SharedImageSubmitter(loader, intake, now = { now.toEpochMilli() }, zone = { ZoneId.of("UTC") })

    @Test
    fun `separate images become one task each with their own date`() = runBlocking {
        val result = submitter().submit(TaskSource.SHARE, listOf(uri("1"), uri("2")), together = false)

        assertEquals(Submission(started = 2, unreadable = 0), result)
        val tasks = repository.all()
        assertEquals(2, tasks.size)
        assertTrue(tasks.all { it.imageCount == 1 && it.source == TaskSource.SHARE && it.entryDate == "2025-10-03" })
    }

    @Test
    fun `images of the same bill become a single task`() = runBlocking {
        val result = submitter().submit(TaskSource.PICKER, listOf(uri("1"), uri("2"), uri("3")), together = true)

        assertEquals(Submission(started = 1, unreadable = 0), result)
        assertEquals(3, repository.all().single().imageCount)
    }

    @Test
    fun `grouping is ignored for a single image`() = runBlocking {
        val result = submitter().submit(TaskSource.SHARE, listOf(uri("1")), together = true)

        assertEquals(1, result.started)
        assertEquals(1, repository.all().single().imageCount)
    }

    @Test
    fun `more images than a task can hold are submitted separately`() = runBlocking {
        val result = submitter().submit(TaskSource.SHARE, (1..5).map { uri("$it") }, together = true)

        assertEquals(5, result.started)
        assertTrue(repository.all().all { it.imageCount == 1 })
    }

    @Test
    fun `unreadable images are skipped and counted`() = runBlocking {
        unreadable += uri("2")

        val result = submitter().submit(TaskSource.SHARE, listOf(uri("1"), uri("2")), together = false)

        assertEquals(Submission(started = 1, unreadable = 1), result)
    }

    @Test
    fun `nothing is stored when no image can be read`() = runBlocking {
        unreadable += uri("1")

        val result = submitter().submit(TaskSource.SHARE, listOf(uri("1")), together = false)

        assertEquals(Submission(started = 0, unreadable = 1), result)
        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun `at most nine images are taken`() = runBlocking {
        val result = submitter().submit(TaskSource.SHARE, (1..12).map { uri("$it") }, together = false)

        assertEquals(9, result.started)
    }

    @Test
    fun `the grouping question is only asked for two or three images`() {
        assertFalse(SharedImageSubmitter.asksHowToGroup(1))
        assertTrue(SharedImageSubmitter.asksHowToGroup(2))
        assertTrue(SharedImageSubmitter.asksHowToGroup(3))
        assertFalse(SharedImageSubmitter.asksHowToGroup(4))
    }
}
