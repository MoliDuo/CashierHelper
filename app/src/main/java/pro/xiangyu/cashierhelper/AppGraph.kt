package pro.xiangyu.cashierhelper

import android.app.Application
import android.content.Intent
import androidx.work.WorkManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import pro.xiangyu.cashierhelper.api.CashierClient
import pro.xiangyu.cashierhelper.api.DocumentApi
import pro.xiangyu.cashierhelper.capture.Haptics
import pro.xiangyu.cashierhelper.capture.TriggerTrial
import pro.xiangyu.cashierhelper.config.AppPrefs
import pro.xiangyu.cashierhelper.config.ConfigRepository
import pro.xiangyu.cashierhelper.images.ImageEncoder
import pro.xiangyu.cashierhelper.images.ImageFiles
import pro.xiangyu.cashierhelper.notify.Notifier
import pro.xiangyu.cashierhelper.tasks.TaskBookStore
import pro.xiangyu.cashierhelper.tasks.TaskEngine
import pro.xiangyu.cashierhelper.tasks.TaskIntake
import pro.xiangyu.cashierhelper.tasks.TaskRepository
import pro.xiangyu.cashierhelper.tasks.TaskScheduler
import pro.xiangyu.cashierhelper.ui.MainActivity

/** Hand-wired singletons, created on first use. */
class AppGraph(private val application: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val prefs by lazy { AppPrefs(application) }
    val config by lazy { ConfigRepository(application) }
    val api: DocumentApi by lazy { CashierClient() }
    val haptics by lazy { Haptics(application) }
    val trial by lazy { TriggerTrial() }
    val encoder by lazy { ImageEncoder() }

    val images by lazy { ImageFiles(File(application.noBackupFilesDir, "task-images")) }
    val tasks by lazy {
        TaskRepository(TaskBookStore.create(File(application.noBackupFilesDir, "tasks.json"), appScope))
    }

    val notifier by lazy {
        Notifier(
            application,
            baseUrl = { config.baseUrl() },
            appIntent = { Intent(application, MainActivity::class.java) },
        )
    }

    val engine by lazy {
        TaskEngine(tasks, api, images, config = { config.config() }, listener = notifier)
    }

    val scheduler by lazy { TaskScheduler(WorkManager.getInstance(application)) }

    val intake by lazy {
        TaskIntake(
            repository = tasks,
            images = images,
            queue = scheduler,
            listener = notifier,
            onRemoved = { notifier.clear(it.id, it.seq) },
        )
    }
}
