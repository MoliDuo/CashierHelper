package pro.xiangyu.cashierhelper.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap
import pro.xiangyu.cashierhelper.R
import pro.xiangyu.cashierhelper.tasks.TaskListener
import pro.xiangyu.cashierhelper.tasks.TaskRecord
import pro.xiangyu.cashierhelper.update.UpdateActions
import pro.xiangyu.cashierhelper.update.UpdateFeed
import pro.xiangyu.cashierhelper.update.UpdateNotifier

/** Intent actions that notification buttons send back to the app. */
object TaskActions {
    const val RETRY = "pro.xiangyu.cashierhelper.action.RETRY_TASK"
    const val EXTRA_TASK_ID = "taskId"
}

/**
 * One notification per task. It starts as quiet progress and is replaced by a
 * result (or a problem) that pops up; posting the result as a fresh
 * notification, instead of updating the progress one, is what makes the
 * banner appear reliably. When notifications are switched off the same words
 * go to a Toast, so she still learns what happened.
 */
class Notifier(
    context: Context,
    private val baseUrl: () -> String?,
    private val appIntent: () -> Intent,
    private val now: () -> Long = System::currentTimeMillis,
) : TaskListener, UpdateNotifier {
    private val appContext = context.applicationContext
    private val shown = ConcurrentHashMap<String, NotificationContent>()

    override fun onTask(record: TaskRecord) {
        val content = NotificationText.forTask(record, now())
        // Polling reports the same state again and again; only a real change is shown.
        if (shown.put(record.id, content) == content) return

        val manager = NotificationManagerCompat.from(appContext)
        val (current, other) = when (content.kind) {
            NotificationKind.PROGRESS -> progressId(record.seq) to resultId(record.seq)
            else -> resultId(record.seq) to progressId(record.seq)
        }
        manager.cancel(other)

        if (!NotificationAvailability.check(appContext).isVisible) {
            if (content.kind != NotificationKind.PROGRESS) toast("${content.title}：${content.text}")
            return
        }
        post(manager, current, build(record, content))
    }

    /** Removes everything shown for a task that was deleted. */
    fun clear(taskId: String, seq: Int) {
        shown.remove(taskId)
        val manager = NotificationManagerCompat.from(appContext)
        manager.cancel(progressId(seq))
        manager.cancel(resultId(seq))
    }

    /** A message that does not belong to a task, such as a screenshot that could not be taken. */
    fun notice(title: String, text: String) {
        if (!NotificationAvailability.check(appContext).isVisible) {
            toast("$title：$text")
            return
        }
        val notification = NotificationCompat.Builder(appContext, Channels.ALERTS)
            .setSmallIcon(R.drawable.ic_stat_cashier)
            .setColor(ContextCompat.getColor(appContext, R.color.brand_primary))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(openApp(NOTICE_ID))
            .setPublicVersion(publicVersion(Channels.ALERTS, "需要处理"))
            .build()
        post(NotificationManagerCompat.from(appContext), NOTICE_ID, notification)
    }

    /** A downloaded, verified update is waiting; the buttons are the same as in the app. */
    override fun updateReady(feed: UpdateFeed) {
        if (!NotificationAvailability.check(appContext).isVisible) return
        val title = appContext.getString(R.string.update_available_title, feed.versionName)
        val text = feed.notes?.trim()?.takeIf { it.isNotEmpty() } ?: appContext.getString(R.string.update_ready_body)
        val install = PendingIntent.getActivity(
            appContext,
            UPDATE_REQUEST_BASE,
            appIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(UpdateActions.EXTRA_INSTALL, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val later = PendingIntent.getBroadcast(
            appContext,
            UPDATE_REQUEST_BASE + 1,
            Intent(UpdateActions.LATER).setPackage(appContext.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, Channels.UPDATES)
            .setSmallIcon(R.drawable.ic_stat_cashier)
            .setColor(ContextCompat.getColor(appContext, R.color.brand_primary))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(install)
            .addAction(0, appContext.getString(R.string.update_action_install), install)
            .addAction(0, appContext.getString(R.string.update_action_later), later)
            .build()
        post(NotificationManagerCompat.from(appContext), UPDATE_ID, notification)
    }

    override fun clearUpdate() {
        NotificationManagerCompat.from(appContext).cancel(UPDATE_ID)
    }

    private fun build(record: TaskRecord, content: NotificationContent): android.app.Notification {
        val channel = when (content.kind) {
            NotificationKind.PROGRESS -> Channels.PROGRESS
            NotificationKind.RESULT -> Channels.RESULTS
            NotificationKind.ALERT -> Channels.ALERTS
        }
        val builder = NotificationCompat.Builder(appContext, channel)
            .setSmallIcon(R.drawable.ic_stat_cashier)
            .setColor(ContextCompat.getColor(appContext, R.color.brand_primary))
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.details ?: content.text))
            .setWhen(record.createdAt)
            .setPublicVersion(publicVersion(channel, content.publicTitle))

        val recordIntent = if (content.opensRecord) viewRecord(record) else null
        builder.setContentIntent(recordIntent ?: openApp(record.seq))

        if (content.kind == NotificationKind.PROGRESS) {
            builder.setProgress(0, 0, true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        } else {
            builder.setAutoCancel(true)
                .setCategory(
                    if (content.kind == NotificationKind.ALERT) NotificationCompat.CATEGORY_ERROR
                    else NotificationCompat.CATEGORY_STATUS,
                )
        }

        when (content.button) {
            NotificationButton.VIEW_IN_CASHIER -> viewRecord(record)?.let {
                builder.addAction(0, appContext.getString(R.string.notification_action_view), it)
            }
            NotificationButton.RETRY ->
                builder.addAction(0, appContext.getString(R.string.notification_action_retry), retry(record))
            NotificationButton.FIX ->
                builder.addAction(0, appContext.getString(R.string.notification_action_fix), openApp(record.seq))
            null -> Unit
        }
        return builder.build()
    }

    /** The lock screen version: tells her something happened without showing the amount or the server's words. */
    private fun publicVersion(channel: String, title: String) =
        NotificationCompat.Builder(appContext, channel)
            .setSmallIcon(R.drawable.ic_stat_cashier)
            .setColor(ContextCompat.getColor(appContext, R.color.brand_primary))
            .setContentTitle(title)
            .build()

    private fun openApp(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        appContext,
        requestCode,
        appIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun viewRecord(record: TaskRecord): PendingIntent? {
        val base = baseUrl()?.trimEnd('/') ?: return null
        val id = record.sourceDocumentId ?: return null
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$base/records?detail=${Uri.encode(id)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            appContext,
            VIEW_REQUEST_BASE + record.seq,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun retry(record: TaskRecord): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        RETRY_REQUEST_BASE + record.seq,
        Intent(TaskActions.RETRY)
            .setPackage(appContext.packageName)
            .putExtra(TaskActions.EXTRA_TASK_ID, record.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @SuppressLint("MissingPermission")
    private fun post(manager: NotificationManagerCompat, id: Int, notification: android.app.Notification) {
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // The permission was withdrawn between the check and the call.
        }
    }

    private fun toast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        fun progressId(seq: Int) = 10_000 + seq
        fun resultId(seq: Int) = 20_000 + seq
        const val NOTICE_ID = 30_001
        const val UPDATE_ID = 30_002
        private const val UPDATE_REQUEST_BASE = 300_000
        private const val VIEW_REQUEST_BASE = 100_000
        private const val RETRY_REQUEST_BASE = 200_000
    }
}
