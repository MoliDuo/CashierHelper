package pro.xiangyu.cashierhelper.ui.onboarding

enum class Step { WELCOME, CONNECT, SERVICE, NOTIFICATIONS, BATTERY, SIDE_KEY }

/** What the phone currently says about each thing the guide asks her to set up. */
data class Signals(
    val connected: Boolean,
    val serviceRunning: Boolean,
    val notificationsVisible: Boolean,
    val batteryUnrestricted: Boolean,
    val sideKeyWorked: Boolean,
)

object OnboardingFlow {
    /** Steps are numbered for her without counting the welcome page. */
    val numbered = Step.entries.filter { it != Step.WELCOME }

    fun isComplete(step: Step, signals: Signals): Boolean = when (step) {
        Step.WELCOME -> false
        Step.CONNECT -> signals.connected
        Step.SERVICE -> signals.serviceRunning
        Step.NOTIFICATIONS -> signals.notificationsVisible
        Step.BATTERY -> signals.batteryUnrestricted
        Step.SIDE_KEY -> signals.sideKeyWorked
    }

    fun next(step: Step): Step? = Step.entries.getOrNull(step.ordinal + 1)

    /**
     * Called whenever she comes back to the app: a step she has just finished in
     * system settings moves the guide on by itself. The welcome page and the last
     * step wait for her to press a button.
     */
    fun settle(current: Step, signals: Signals): Step {
        var step = current
        while (step != Step.WELCOME && step != Step.SIDE_KEY && isComplete(step, signals)) {
            step = next(step) ?: break
        }
        return step
    }

    fun position(step: Step): Int? = numbered.indexOf(step).takeIf { it >= 0 }?.plus(1)
}

object RestrictedSettings {
    private val stores = setOf(
        "com.android.vending",
        "com.sec.android.app.samsungapps",
        "com.huawei.appmarket",
        "com.xiaomi.market",
        "com.heytap.market",
        "com.bbk.appstore",
    )

    /**
     * From Android 13, an app installed from a file cannot have its accessibility
     * service switched on until the user allows "restricted settings". When the app
     * did not come from a store, the explanation is shown up front instead of
     * waiting for her to find a greyed-out switch.
     */
    fun shouldExplain(installer: String?, sdk: Int): Boolean = sdk >= 33 && installer !in stores
}
