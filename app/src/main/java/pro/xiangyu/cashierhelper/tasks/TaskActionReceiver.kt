package pro.xiangyu.cashierhelper.tasks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import pro.xiangyu.cashierhelper.CashierHelperApplication
import pro.xiangyu.cashierhelper.notify.TaskActions

/** Handles the "重试" button on a notification. */
class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskActions.RETRY) return
        val id = intent.getStringExtra(TaskActions.EXTRA_TASK_ID) ?: return
        val graph = (context.applicationContext as CashierHelperApplication).graph
        val pending = goAsync()
        graph.appScope.launch {
            try {
                graph.intake.retry(id)
            } finally {
                pending.finish()
            }
        }
    }
}
