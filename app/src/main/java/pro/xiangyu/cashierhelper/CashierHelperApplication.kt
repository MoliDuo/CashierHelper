package pro.xiangyu.cashierhelper

import android.app.Application
import kotlinx.coroutines.launch
import pro.xiangyu.cashierhelper.notify.Channels
import pro.xiangyu.cashierhelper.tasks.TaskEngine
import pro.xiangyu.cashierhelper.tasks.TaskEngineProvider

class CashierHelperApplication : Application(), TaskEngineProvider {
    val graph by lazy { AppGraph(this) }

    override val taskEngine: TaskEngine get() = graph.engine

    override fun onCreate() {
        super.onCreate()
        Channels.ensure(this)
        graph.appScope.launch {
            Migration.run(this@CashierHelperApplication, graph.prefs, graph.config)
            // Anything unfinished from before the app was last stopped goes on from here.
            graph.intake.reconcile()
        }
    }
}
