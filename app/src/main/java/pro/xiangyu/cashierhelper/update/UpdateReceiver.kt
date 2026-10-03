package pro.xiangyu.cashierhelper.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import pro.xiangyu.cashierhelper.CashierHelperApplication

/** The installer's answer, and the "稍后" button of the update notification. */
class UpdateReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as CashierHelperApplication).graph
        when (intent.action) {
            UpdateActions.LATER -> graph.updates.defer()
            UpdateActions.INSTALL_RESULT -> {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        } else {
                            intent.getParcelableExtra(Intent.EXTRA_INTENT)
                        }
                        confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    PackageInstaller.STATUS_SUCCESS -> Unit
                    else -> graph.notifier.notice("更新没有安装成功", InstallFailure.describe(status))
                }
            }
        }
    }
}
