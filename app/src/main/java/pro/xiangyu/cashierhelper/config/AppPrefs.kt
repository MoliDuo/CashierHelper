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

    private companion object {
        const val ONBOARDING_DONE = "onboarding_done"
        const val MIGRATED = "migrated_to_tasks"
    }
}
