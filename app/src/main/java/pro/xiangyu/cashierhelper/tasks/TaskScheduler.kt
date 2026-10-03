package pro.xiangyu.cashierhelper.tasks

import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Starts and stops the background work for tasks. */
interface TaskQueue {
    /** Makes sure the task is being worked on; [restart] also drops any waiting for a retry. */
    fun enqueue(id: String, restart: Boolean = false)

    fun cancel(id: String)
}

/**
 * One uniquely named piece of work per task, so a task is never worked on
 * twice at once and survives process death, reboots and offline periods.
 */
class TaskScheduler(private val workManager: WorkManager) : TaskQueue {
    override fun enqueue(id: String, restart: Boolean) {
        val request = OneTimeWorkRequestBuilder<TaskWorker>()
            .setInputData(workDataOf(TaskWorker.KEY_TASK_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .apply {
                // Expedited work before Android 12 needs a foreground notification, which this app avoids.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                }
            }
            .build()
        workManager.enqueueUniqueWork(
            workName(id),
            if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel(id: String) {
        workManager.cancelUniqueWork(workName(id))
    }

    companion object {
        const val BACKOFF_SECONDS = 20L
        fun workName(id: String) = "task-$id"
    }
}
