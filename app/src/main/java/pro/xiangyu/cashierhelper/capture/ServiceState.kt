package pro.xiangyu.cashierhelper.capture

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * "On" in the accessibility list and "actually running" are different things:
 * the system can show the service as enabled while it is not bound, which is
 * what made screenshots silently fail before.
 */
enum class ServiceState {
    OFF,
    ENABLED_NOT_RUNNING,
    RUNNING,
    ;

    companion object {
        fun read(context: Context): ServiceState = when {
            !isEnabledInSettings(context) -> OFF
            CashierAccessibilityService.isConnected() -> RUNNING
            else -> ENABLED_NOT_RUNNING
        }

        fun isEnabledInSettings(context: Context): Boolean {
            if (Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) {
                return false
            }
            val expected = ComponentName(context, CashierAccessibilityService::class.java)
            return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty()
                .split(':')
                .mapNotNull(ComponentName::unflattenFromString)
                .any { it == expected }
        }
    }
}
