package pro.xiangyu.cashierhelper.notify

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import pro.xiangyu.cashierhelper.tasks.TaskOutcome
import pro.xiangyu.cashierhelper.tasks.TaskProblem
import pro.xiangyu.cashierhelper.tasks.TaskRecord
import pro.xiangyu.cashierhelper.tasks.TaskResult
import pro.xiangyu.cashierhelper.tasks.TaskSource
import pro.xiangyu.cashierhelper.tasks.TaskState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotifierTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val manager: NotificationManager = application.getSystemService(NotificationManager::class.java)
    private var baseUrl: String? = "https://cashier.example/"
    private lateinit var notifier: Notifier

    @Before
    fun setUp() {
        Channels.ensure(application)
        shadowOf(manager).setNotificationsEnabled(true)
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier = Notifier(
            application,
            baseUrl = { baseUrl },
            appIntent = { Intent(Intent.ACTION_MAIN).setClassName(application, "pro.xiangyu.cashierhelper.ui.MainActivity") },
            now = { 5_000L },
        )
    }

    private fun record(
        state: TaskState,
        seq: Int = 3,
        documentId: String? = null,
        outcome: TaskOutcome? = null,
        problem: TaskProblem? = null,
        result: TaskResult? = null,
    ) = TaskRecord(
        id = "task-$seq",
        seq = seq,
        source = TaskSource.SCREENSHOT,
        state = state,
        idempotencyKey = "k",
        entryDate = "2026-10-03",
        imageCount = 1,
        createdAt = 1_000,
        sourceDocumentId = documentId,
        outcome = outcome,
        problem = problem,
        result = result,
    )

    private fun active(id: Int): Notification? = shadowOf(manager).getNotification(id)

    private fun Notification.title(): String = extras.getString(Notification.EXTRA_TITLE)!!
    private fun Notification.text(): String = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    @Test
    fun `creates the three channels with the right importance`() {
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(Channels.PROGRESS).importance)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(Channels.RESULTS).importance)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(Channels.ALERTS).importance)
    }

    @Test
    fun `progress is a quiet ongoing notification and the result replaces it`() {
        notifier.onTask(record(TaskState.UPLOADING))
        val progress = active(Notifier.progressId(3))!!
        assertEquals(Channels.PROGRESS, progress.channelId)
        assertEquals("正在上传截图", progress.title())
        assertTrue(progress.flags and Notification.FLAG_ONGOING_EVENT != 0)

        notifier.onTask(
            record(
                TaskState.DONE,
                documentId = "doc 1",
                outcome = TaskOutcome.COMPLETED,
                result = TaskResult("星巴克", "35.00", "CNY", emptyList(), 0),
            ),
        )

        assertNull(active(Notifier.progressId(3)))
        val result = active(Notifier.resultId(3))!!
        assertEquals(Channels.RESULTS, result.channelId)
        assertEquals("星巴克 ¥35.00", result.title())
        assertEquals("识别完成", result.publicVersion.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `the result button opens the record in Cashier`() {
        notifier.onTask(record(TaskState.DONE, documentId = "doc 1", outcome = TaskOutcome.COMPLETED))

        val result = active(Notifier.resultId(3))!!
        assertEquals("在 Cashier 查看", result.actions.single().title.toString())
        val intent = shadowOf(result.actions.single().actionIntent).savedIntent
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://cashier.example/records?detail=doc%201", intent.data.toString())
        assertEquals(intent.data, shadowOf(result.contentIntent).savedIntent.data)
    }

    @Test
    fun `without a server address there is no view button and a tap opens the app`() {
        baseUrl = null
        notifier.onTask(record(TaskState.DONE, documentId = "doc-1", outcome = TaskOutcome.COMPLETED))

        val result = active(Notifier.resultId(3))!!
        assertTrue(result.actions.isNullOrEmpty())
        assertEquals("pro.xiangyu.cashierhelper.ui.MainActivity", shadowOf(result.contentIntent).savedIntent.component!!.className)
    }

    @Test
    fun `a problem uses the alerts channel and the retry button reaches the receiver`() {
        notifier.onTask(record(TaskState.NEEDS_ACTION, problem = TaskProblem.REJECTED))

        val alert = active(Notifier.resultId(3))!!
        assertEquals(Channels.ALERTS, alert.channelId)
        val intent = shadowOf(alert.actions.single().actionIntent).savedIntent
        assertEquals(TaskActions.RETRY, intent.action)
        assertEquals(application.packageName, intent.`package`)
        assertEquals("task-3", intent.getStringExtra(TaskActions.EXTRA_TASK_ID))
    }

    @Test
    fun `retrying a problem swaps the alert for progress again`() {
        notifier.onTask(record(TaskState.NEEDS_ACTION, problem = TaskProblem.UNAUTHORIZED))
        assertNotNull(active(Notifier.resultId(3)))

        notifier.onTask(record(TaskState.QUEUED))

        assertNull(active(Notifier.resultId(3)))
        assertNotNull(active(Notifier.progressId(3)))
    }

    @Test
    fun `repeating the same state does not post again`() {
        notifier.onTask(record(TaskState.UPLOADING))
        manager.cancel(Notifier.progressId(3))

        notifier.onTask(record(TaskState.UPLOADING))

        assertNull(active(Notifier.progressId(3)))
    }

    @Test
    fun `tasks use separate notification ids and clearing removes both`() {
        notifier.onTask(record(TaskState.UPLOADING, seq = 1))
        notifier.onTask(record(TaskState.DONE, seq = 2, outcome = TaskOutcome.CANCELLED))
        assertNotNull(active(Notifier.progressId(1)))
        assertNotNull(active(Notifier.resultId(2)))

        notifier.clear("task-1", 1)
        notifier.clear("task-2", 2)

        assertNull(active(Notifier.progressId(1)))
        assertNull(active(Notifier.resultId(2)))
    }

    @Test
    fun `a notice goes to the alerts channel`() {
        notifier.notice("截图失败", "请再双击一次侧键")

        val notice = active(Notifier.NOTICE_ID)!!
        assertEquals(Channels.ALERTS, notice.channelId)
        assertEquals("请再双击一次侧键", notice.text())
    }

    @Test
    fun `when notifications are off the result is shown as a toast and progress stays silent`() {
        shadowOf(manager).setNotificationsEnabled(false)

        notifier.onTask(record(TaskState.UPLOADING))
        assertNull(ShadowToast.getLatestToast())

        notifier.onTask(record(TaskState.DONE, outcome = TaskOutcome.CANCELLED))
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("识别已取消：这条记录在 Cashier 里被取消了", ShadowToast.getTextOfLatestToast())
        assertFalse(shadowOf(manager).allNotifications.any())
    }

    @Test
    fun `the legacy channel is removed`() {
        manager.createNotificationChannel(
            android.app.NotificationChannel(Channels.LEGACY_RESULTS, "old", NotificationManager.IMPORTANCE_DEFAULT),
        )

        Channels.deleteLegacy(application)

        assertNull(manager.getNotificationChannel(Channels.LEGACY_RESULTS))
    }
}
