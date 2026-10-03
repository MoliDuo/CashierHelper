package pro.xiangyu.cashierhelper

import android.content.Context
import java.io.File
import pro.xiangyu.cashierhelper.config.AppPrefs
import pro.xiangyu.cashierhelper.config.ConfigRepository
import pro.xiangyu.cashierhelper.config.ConfigState
import pro.xiangyu.cashierhelper.notify.Channels

/** One-time clean-up when the app starts for the first time on the task-based design. */
object Migration {
    suspend fun run(context: Context, prefs: AppPrefs, config: ConfigRepository) {
        if (prefs.migratedToTasks) return
        // The old design kept encrypted pending uploads here; they cannot be read any more.
        File(context.noBackupFilesDir, "pending_tasks").deleteRecursively()
        Channels.deleteLegacy(context)
        // Someone who already set everything up does not need the first-run guide.
        if (config.current() !is ConfigState.Missing) prefs.onboardingDone = true
        prefs.migratedToTasks = true
    }
}
