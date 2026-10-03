package pro.xiangyu.cashierhelper.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pro.xiangyu.cashierhelper.tasks.TaskEntry
import pro.xiangyu.cashierhelper.tasks.TaskOutcome
import pro.xiangyu.cashierhelper.tasks.TaskProblem
import pro.xiangyu.cashierhelper.tasks.TaskRecord
import pro.xiangyu.cashierhelper.tasks.TaskResult
import pro.xiangyu.cashierhelper.tasks.TaskSource
import pro.xiangyu.cashierhelper.tasks.TaskState

class NotificationTextTest {
    private val now = 5_000_000L

    private fun record(
        state: TaskState,
        source: TaskSource = TaskSource.SCREENSHOT,
        attempts: Int = 0,
        acceptedAt: Long? = null,
        documentId: String? = null,
        outcome: TaskOutcome? = null,
        problem: TaskProblem? = null,
        message: String? = null,
        error: String? = null,
        result: TaskResult? = null,
    ) = TaskRecord(
        id = "t",
        seq = 1,
        source = source,
        state = state,
        idempotencyKey = "k",
        entryDate = "2026-10-03",
        imageCount = 1,
        createdAt = 1_000,
        uploadAttempts = attempts,
        acceptedAt = acceptedAt,
        sourceDocumentId = documentId,
        outcome = outcome,
        problem = problem,
        message = message,
        lastError = error,
        result = result,
    )

    private fun done(outcome: TaskOutcome, result: TaskResult? = null, message: String? = null, error: String? = null) =
        NotificationText.forTask(
            record(TaskState.DONE, documentId = "doc-1", outcome = outcome, result = result, message = message, error = error),
            now,
        )

    @Test
    fun `progress goes from upload to waiting to recognising to slow`() {
        val fresh = NotificationText.forTask(record(TaskState.QUEUED), now)
        assertEquals(NotificationKind.PROGRESS, fresh.kind)
        assertEquals("正在上传截图", fresh.title)

        val waiting = NotificationText.forTask(record(TaskState.QUEUED, attempts = 2), now)
        assertEquals("等待重新上传", waiting.title)
        assertEquals("网络恢复后会自动上传", waiting.text)

        val processing = NotificationText.forTask(record(TaskState.PROCESSING, acceptedAt = now - 10_000), now)
        assertEquals("正在识别账单", processing.title)
        assertEquals("通常需要 10 到 20 秒", processing.text)

        val slow = NotificationText.forTask(record(TaskState.PROCESSING, acceptedAt = now - 91_000), now)
        assertEquals("识别时间比平时长", slow.title)
    }

    @Test
    fun `shared images are called images`() {
        val content = NotificationText.forTask(record(TaskState.UPLOADING, source = TaskSource.SHARE), now)

        assertEquals("正在上传图片", content.title)
    }

    @Test
    fun `a completed bill shows the title and total with its categories`() {
        val content = done(
            TaskOutcome.COMPLETED,
            TaskResult(
                title = "星巴克",
                total = "35.00",
                totalCurrency = "CNY",
                entries = listOf(
                    TaskEntry("拿铁", "20.00", "CNY", "餐饮"),
                    TaskEntry("地铁", "15.00", "CNY", "交通"),
                    TaskEntry("蛋糕", "0.00", "CNY", "餐饮"),
                ),
                entryCount = 3,
            ),
        )

        assertEquals(NotificationKind.RESULT, content.kind)
        assertEquals("星巴克 ¥35.00", content.title)
        assertEquals("餐饮 · 交通 · 共 3 条明细", content.text)
        assertEquals("拿铁  ¥20.00\n地铁  ¥15.00\n蛋糕  ¥0.00", content.details)
        assertEquals(NotificationButton.VIEW_IN_CASHIER, content.button)
        assertTrue(content.opensRecord)
        assertFalse("the amount must not show on the lock screen", content.publicTitle.contains("¥"))
    }

    @Test
    fun `a single entry shows only its category`() {
        val content = done(
            TaskOutcome.COMPLETED,
            TaskResult("便利店", "8.50", "CNY", listOf(TaskEntry("饮料", "8.50", "CNY", "餐饮")), 1),
        )

        assertEquals("便利店 ¥8.50", content.title)
        assertEquals("餐饮", content.text)
        assertNull(content.details)
    }

    @Test
    fun `a bill without a total waits for the exchange rate`() {
        val content = done(
            TaskOutcome.COMPLETED,
            TaskResult("Hotel", null, "CNY", listOf(TaskEntry("Room", "100.00", "USD", "住宿")), 1),
        )

        assertEquals("Hotel", content.title)
        assertEquals("金额要等汇率更新后显示", content.text)
    }

    @Test
    fun `long entry lists are cut with a count of the rest`() {
        val entries = List(12) { TaskEntry("项目$it", "1.00", "CNY", null) }
        val content = done(TaskOutcome.COMPLETED, TaskResult("超市", "12.00", "CNY", entries.take(12), 15))

        val lines = content.details!!.lines()
        assertEquals(9, lines.size)
        assertEquals("还有 7 条明细", lines.last())
        assertEquals("共 15 条明细", content.text)
    }

    @Test
    fun `an invalid image shows the server's own sentence`() {
        val content = done(TaskOutcome.INVALID, message = "这是一张退款单据，本系统只处理支出。")

        assertEquals("这张图片无法记账", content.title)
        assertEquals("这是一张退款单据，本系统只处理支出。", content.text)
        assertFalse(content.publicTitle.contains("退款"))
    }

    @Test
    fun `failure codes become plain sentences`() {
        assertEquals("识别服务暂时不可用", done(TaskOutcome.FAILED, error = "ai_provider_unavailable").text)
        assertEquals("识别超时", done(TaskOutcome.FAILED, error = "processing_timeout").text)
        assertEquals("暂时取不到汇率", done(TaskOutcome.FAILED, error = "exchange_rate_failure").text)
        assertEquals("服务器说的话", done(TaskOutcome.FAILED, error = "something_new", message = "服务器说的话").text)
        assertEquals("Cashier 没能完成识别", done(TaskOutcome.FAILED).text)
    }

    @Test
    fun `other endings are explained without a button when there is nothing to open`() {
        assertEquals("识别已取消", done(TaskOutcome.CANCELLED).title)
        assertEquals("记录已被删除", done(TaskOutcome.DELETED_ON_SERVER).title)
        assertNull(done(TaskOutcome.DELETED_ON_SERVER).button)
        assertEquals("没有等到识别结果", done(TaskOutcome.NO_RESULT).title)
    }

    @Test
    fun `problems that need her say what is kept and what happens next`() {
        fun problem(problem: TaskProblem, documentId: String? = null, message: String? = null) =
            NotificationText.forTask(
                record(TaskState.NEEDS_ACTION, problem = problem, documentId = documentId, message = message),
                now,
            )

        val key = problem(TaskProblem.UNAUTHORIZED)
        assertEquals(NotificationKind.ALERT, key.kind)
        assertEquals("API 密钥无效", key.title)
        assertEquals("截图已保存。更新密钥后会自动上传", key.text)
        assertEquals(NotificationButton.FIX, key.button)

        assertEquals("更新密钥后会继续查询结果", problem(TaskProblem.UNAUTHORIZED, documentId = "doc-1").text)

        assertEquals("还没有连接 Cashier", problem(TaskProblem.NOT_CONFIGURED).title)
        assertEquals(NotificationButton.RETRY, problem(TaskProblem.CONFIG_CHANGED).button)
        assertEquals(NotificationButton.RETRY, problem(TaskProblem.CONFLICT).button)

        val rejected = problem(TaskProblem.REJECTED, message = "图片格式不支持")
        assertEquals("图片格式不支持", rejected.text)
        assertEquals("截图已保存。", rejected.details)
        assertEquals("服务器拒绝了这次上传", problem(TaskProblem.REJECTED).text)

        assertEquals("找不到要上传的截图", problem(TaskProblem.IMAGES_MISSING).title)
    }

    @Test
    fun `every state produces words and a lock screen title`() {
        for (state in TaskState.entries) {
            val content = NotificationText.forTask(record(state, outcome = TaskOutcome.COMPLETED), now)
            assertTrue(content.title.isNotBlank())
            assertTrue(content.text.isNotBlank())
            assertTrue(content.publicTitle.isNotBlank())
        }
    }
}
