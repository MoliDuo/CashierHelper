package pro.xiangyu.cashierhelper.update

import android.content.pm.PackageInstaller

/** Intent names shared by the update notification, the receiver and the main screen. */
object UpdateActions {
    const val INSTALL_RESULT = "pro.xiangyu.cashierhelper.action.UPDATE_INSTALL_RESULT"
    const val LATER = "pro.xiangyu.cashierhelper.action.UPDATE_LATER"
    const val EXTRA_INSTALL = "installUpdate"
}

/** Plain words for why the system did not install an update. The running version is never affected. */
object InstallFailure {
    fun describe(status: Int): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "你取消了安装。需要时可以在 Cashier 记账里再点「立即更新」。"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "手机阻止了这次安装。请检查「安装未知应用」的设置后再试。"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "手机存储空间不足。清理一些空间后再试。"
        PackageInstaller.STATUS_FAILURE_CONFLICT,
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
        PackageInstaller.STATUS_FAILURE_INVALID,
        -> "这个安装包与手机上的版本不兼容。当前版本不受影响。"
        else -> "安装没有完成，当前版本不受影响。可以稍后再试。"
    }
}
