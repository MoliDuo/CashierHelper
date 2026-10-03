package pro.xiangyu.cashierhelper.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import pro.xiangyu.cashierhelper.capture.CashierAccessibilityService
import pro.xiangyu.cashierhelper.notify.Channels

/**
 * Shortcuts into system settings. Phones differ, so each destination is a list
 * of intents tried in order; the last one is always a screen every phone has.
 */
object SystemIntents {
    fun openAccessibility(context: Context) = startFirst(context, accessibility(context))

    fun openNotifications(context: Context) = startFirst(context, notifications(context))

    fun openBattery(context: Context) = startFirst(context, battery(context))

    fun openAppDetails(context: Context) = startFirst(context, appDetails(context))

    fun openInstallSources(context: Context) = startFirst(context, installSources(context))

    fun openUrl(context: Context, url: String) =
        startFirst(context, listOf(Intent(Intent.ACTION_VIEW, Uri.parse(url))))

    /** This service's own page first, so she does not have to find it in the list. */
    internal fun accessibility(context: Context): List<Intent> {
        val component = ComponentName(context, CashierAccessibilityService::class.java).flattenToString()
        return listOf(
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, component),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        )
    }

    internal fun notifications(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, Channels.RESULTS),
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        appDetails(context).first(),
    )

    internal fun battery(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appDetails(context).first(),
    )

    internal fun appDetails(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )

    internal fun installSources(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
        appDetails(context).first(),
    )

    private fun startFirst(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
                continue
            } catch (_: SecurityException) {
                continue
            }
        }
        return false
    }
}
