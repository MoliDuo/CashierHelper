package pro.xiangyu.cashierhelper.tasks

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlin.coroutines.cancellation.CancellationException

/** Implemented by the application so the worker can reach the engine. */
interface TaskEngineProvider {
    val taskEngine: TaskEngine
}

/**
 * Runs one task for up to [BUDGET_MILLIS], then asks to be run again. WorkManager
 * supplies the waiting, the backoff, the network condition and the restart after
 * the process was killed.
 */
class TaskWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val engine = (applicationContext as TaskEngineProvider).taskEngine
        return try {
            when (engine.drive(id, BUDGET_MILLIS)) {
                DriveResult.Finished -> Result.success()
                DriveResult.Retry -> Result.retry()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Whatever went wrong, the task is still on disk; try again later rather than strand it.
            Log.w(TAG, "Task $id failed unexpectedly", error)
            Result.retry()
        }
    }

    companion object {
        const val KEY_TASK_ID = "taskId"
        const val BUDGET_MILLIS = 150_000L
        private const val TAG = "TaskWorker"
    }
}
