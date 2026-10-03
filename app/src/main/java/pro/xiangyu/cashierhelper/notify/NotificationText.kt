package pro.xiangyu.cashierhelper.notify

import pro.xiangyu.cashierhelper.tasks.TaskOutcome
import pro.xiangyu.cashierhelper.tasks.TaskProblem
import pro.xiangyu.cashierhelper.tasks.TaskRecord
import pro.xiangyu.cashierhelper.tasks.TaskSource
import pro.xiangyu.cashierhelper.tasks.TaskState

enum class NotificationKind { PROGRESS, RESULT, ALERT }

/** What the single button on a notification does. */
enum class NotificationButton { VIEW_IN_CASHIER, RETRY, FIX }

data class NotificationContent(
    val kind: NotificationKind,
    val title: String,
    val text: String,
    /** Longer text for the expanded view. */
    val details: String? = null,
    /** Shown on the lock screen instead of the amount or the server's message. */
    val publicTitle: String,
    val button: NotificationButton? = null,
    /** Tapping the notification opens the record in Cashier instead of this app. */
    val opensRecord: Boolean = false,
)

/**
 * The words shown for a task, as a pure function of the task so they can be
 * tested without a phone. Plain, calm sentences: say what happened and what
 * (if anything) to do.
 */
object NotificationText {
    private const val SLOW_AFTER_MILLIS = 90_000L
    private const val MAX_DETAIL_LINES = 8

    fun forTask(record: TaskRecord, now: Long): NotificationContent {
        val noun = if (record.source == TaskSource.SCREENSHOT) "截图" else "图片"
        return when (record.state) {
            TaskState.QUEUED ->
                if (record.uploadAttempts == 0) uploading(noun) else waitingToUpload()
            TaskState.UPLOADING -> uploading(noun)
            TaskState.PROCESSING -> processing(record, now)
            TaskState.DONE -> done(record)
            TaskState.NEEDS_ACTION -> needsAction(record, noun)
        }
    }

    private fun uploading(noun: String) = NotificationContent(
        kind = NotificationKind.PROGRESS,
        title = "正在上传$noun",
        text = "上传完成后会开始识别",
        publicTitle = "正在记账",
    )

    private fun waitingToUpload() = NotificationContent(
        kind = NotificationKind.PROGRESS,
        title = "等待重新上传",
        text = "网络恢复后会自动上传",
        publicTitle = "正在记账",
    )

    private fun processing(record: TaskRecord, now: Long): NotificationContent {
        val slow = record.acceptedAt?.let { now - it > SLOW_AFTER_MILLIS } == true
        return if (slow) {
            NotificationContent(
                kind = NotificationKind.PROGRESS,
                title = "识别时间比平时长",
                text = "还在处理中，完成后会通知你",
                publicTitle = "正在记账",
            )
        } else {
            NotificationContent(
                kind = NotificationKind.PROGRESS,
                title = "正在识别账单",
                text = "通常需要 10 到 20 秒",
                publicTitle = "正在记账",
            )
        }
    }

    private fun done(record: TaskRecord): NotificationContent {
        val canOpen = record.sourceDocumentId != null
        val view = if (canOpen) NotificationButton.VIEW_IN_CASHIER else null
        return when (record.outcome) {
            TaskOutcome.COMPLETED -> completed(record, view)
            TaskOutcome.INVALID -> NotificationContent(
                kind = NotificationKind.RESULT,
                title = "这张图片无法记账",
                text = record.message?.takeIf { it.isNotBlank() } ?: "Cashier 没能从图片里识别出一笔支出",
                publicTitle = "识别完成",
                button = view,
                opensRecord = canOpen,
            )
            TaskOutcome.FAILED -> NotificationContent(
                kind = NotificationKind.RESULT,
                title = "识别失败",
                text = failureReason(record.lastError, record.message),
                details = "可以在 Cashier 里重新提交，或者手动记一笔",
                publicTitle = "识别失败",
                button = view,
                opensRecord = canOpen,
            )
            TaskOutcome.CANCELLED -> NotificationContent(
                kind = NotificationKind.RESULT,
                title = "识别已取消",
                text = "这条记录在 Cashier 里被取消了",
                publicTitle = "识别已取消",
                button = view,
                opensRecord = canOpen,
            )
            TaskOutcome.DELETED_ON_SERVER -> NotificationContent(
                kind = NotificationKind.RESULT,
                title = "记录已被删除",
                text = "Cashier 里已经找不到这条记录",
                publicTitle = "记录已被删除",
            )
            TaskOutcome.NO_RESULT, null -> NotificationContent(
                kind = NotificationKind.RESULT,
                title = "没有等到识别结果",
                text = "请到 Cashier 里看看这张账单有没有记上",
                publicTitle = "没有等到识别结果",
                button = view,
                opensRecord = canOpen,
            )
        }
    }

    private fun completed(record: TaskRecord, button: NotificationButton?): NotificationContent {
        val result = record.result
        val name = result?.title?.takeIf { it.isNotBlank() } ?: "账单"
        val total = result?.total
        val title = if (total != null) "$name ${AmountFormat.format(total, result.totalCurrency)}" else name

        val categories = result?.entries.orEmpty()
            .mapNotNull { it.category?.takeIf(String::isNotBlank) }
            .distinct()
            .take(3)
        val count = result?.entryCount ?: 0
        val text = when {
            total == null -> "金额要等汇率更新后显示"
            else -> {
                val parts = categories.toMutableList()
                if (count > 1) parts += "共 $count 条明细"
                parts.joinToString(" · ").ifEmpty { "已记入 Cashier" }
            }
        }

        val lines = result?.entries.orEmpty().take(MAX_DETAIL_LINES).map { entry ->
            "${entry.name}  ${AmountFormat.format(entry.amount, entry.currency)}"
        }
        val more = count - lines.size
        val details = if (lines.size > 1 || more > 0) {
            (lines + if (more > 0) listOf("还有 $more 条明细") else emptyList()).joinToString("\n")
        } else {
            null
        }

        return NotificationContent(
            kind = NotificationKind.RESULT,
            title = title,
            text = text,
            details = details,
            publicTitle = "识别完成",
            button = button,
            opensRecord = record.sourceDocumentId != null,
        )
    }

    private fun failureReason(code: String?, message: String?): String = when (code) {
        "ai_provider_unavailable", "processing_unavailable" -> "识别服务暂时不可用"
        "ai_schema_invalid" -> "识别结果不完整"
        "exchange_rate_failure" -> "暂时取不到汇率"
        "storage_failure" -> "Cashier 保存图片时出了问题"
        "request_bound_retry_exhausted" -> "多次识别都没有成功"
        "processing_timeout" -> "识别超时"
        else -> message?.takeIf { it.isNotBlank() } ?: "Cashier 没能完成识别"
    }

    private fun needsAction(record: TaskRecord, noun: String): NotificationContent {
        val kept = if (record.sourceDocumentId == null) "${noun}已保存。" else ""
        return when (record.problem) {
            TaskProblem.UNAUTHORIZED -> NotificationContent(
                kind = NotificationKind.ALERT,
                title = "API 密钥无效",
                text = if (record.sourceDocumentId == null) {
                    "${kept}更新密钥后会自动上传"
                } else {
                    "更新密钥后会继续查询结果"
                },
                publicTitle = "需要处理",
                button = NotificationButton.FIX,
            )
            TaskProblem.NOT_CONFIGURED -> NotificationContent(
                kind = NotificationKind.ALERT,
                title = "还没有连接 Cashier",
                text = "${kept}填好服务器地址和密钥后会自动上传",
                publicTitle = "需要处理",
                button = NotificationButton.FIX,
            )
            TaskProblem.CONFIG_CHANGED -> NotificationContent(
                kind = NotificationKind.ALERT,
                title = "连接设置已更改",
                text = "这张${noun}是用之前的设置上传的，要重新上传到现在连接的 Cashier 吗",
                publicTitle = "需要处理",
                button = NotificationButton.RETRY,
            )
            TaskProblem.CONFLICT -> NotificationContent(
                kind = NotificationKind.ALERT,
                title = "上传出现冲突",
                text = "${kept}可以重试一次",
                publicTitle = "需要处理",
                button = NotificationButton.RETRY,
            )
            TaskProblem.REJECTED -> NotificationContent(
                kind = NotificationKind.ALERT,
                title = "Cashier 没有收下这张$noun",
                text = record.message?.takeIf { it.isNotBlank() } ?: "服务器拒绝了这次上传",
                details = kept.ifEmpty { null },
                publicTitle = "需要处理",
                button = NotificationButton.RETRY,
            )
            TaskProblem.IMAGES_MISSING, null -> NotificationContent(
                kind = NotificationKind.ALERT,
                title = "找不到要上传的$noun",
                text = "文件已经丢失，请删除这条记录后重新截图",
                publicTitle = "需要处理",
                button = NotificationButton.FIX,
            )
        }
    }
}
