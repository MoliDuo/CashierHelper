package pro.xiangyu.cashierhelper.config

import android.content.Context
import android.content.SharedPreferences

/** Small non-secret flags. */
class AppPrefs(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("app_state", Context.MODE_PRIVATE))

    var onboardingDone: Boolean
        get() = preferences.getBoolean(ONBOARDING_DONE, false)
        set(value) = preferences.edit().putBoolean(ONBOARDING_DONE, value).apply()

    var migratedToTasks: Boolean
        get() = preferences.getBoolean(MIGRATED, false)
        set(value) = preferences.edit().putBoolean(MIGRATED, value).apply()

    /** The version whose "later" button was pressed; it is not offered again until a newer one appears. */
    var deferredUpdateCode: Int
        get() = preferences.getInt(DEFERRED_UPDATE, 0)
        set(value) = preferences.edit().putInt(DEFERRED_UPDATE, value).apply()

    /** The version a notification was already shown for. */
    var notifiedUpdateCode: Int
        get() = preferences.getInt(NOTIFIED_UPDATE, 0)
        set(value) = preferences.edit().putInt(NOTIFIED_UPDATE, value).apply()

    var lastUpdateCheck: Long
        get() = preferences.getLong(LAST_UPDATE_CHECK, 0L)
        set(value) = preferences.edit().putLong(LAST_UPDATE_CHECK, value).apply()

    /** The feed entry of a downloaded update, kept so it can be offered again after a restart. */
    var pendingUpdate: String?
        get() = preferences.getString(PENDING_UPDATE, null)
        set(value) = preferences.edit().putString(PENDING_UPDATE, value).apply()

    private companion object {
        const val DEFERRED_UPDATE = "deferred_update_code"
        const val NOTIFIED_UPDATE = "notified_update_code"
        const val LAST_UPDATE_CHECK = "last_update_check"
        const val PENDING_UPDATE = "pending_update"
        const val ONBOARDING_DONE = "onboarding_done"
        const val MIGRATED = "migrated_to_tasks"
    }
}
