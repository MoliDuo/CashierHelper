package pro.xiangyu.cashierhelper.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import pro.xiangyu.cashierhelper.R

/**
 * Three channels so each kind of message can be tuned separately: quiet
 * progress, a result that pops up, and problems that need her attention.
 */
object Channels {
    const val PROGRESS = "progress"
    const val RESULTS = "results"
    const val ALERTS = "alerts"

    /** The first channel this app ever created; replaced by the three above. */
    const val LEGACY_RESULTS = "capture_results"

    private val resultsVibration = longArrayOf(0, 45, 45, 65)
    private val alertsVibration = longArrayOf(0, 90, 60, 90)

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                PROGRESS,
                context.getString(R.string.channel_progress_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_progress_description)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                RESULTS,
                context.getString(R.string.channel_results_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_results_description)
                enableVibration(true)
                vibrationPattern = resultsVibration
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ALERTS,
                context.getString(R.string.channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_alerts_description)
                enableVibration(true)
                vibrationPattern = alertsVibration
            },
        )
    }

    fun deleteLegacy(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.deleteNotificationChannel(LEGACY_RESULTS)
    }
}
