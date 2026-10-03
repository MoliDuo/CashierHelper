package pro.xiangyu.cashierhelper.capture

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Exists only to take screenshots. The class name is part of how Android and
 * the saved accessibility setting find this service, so it must not change.
 */
class CashierAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = WeakReference(this)
        mutableRunning.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        detach()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        detach()
        super.onDestroy()
    }

    private fun detach() {
        if (activeService?.get() === this) {
            activeService = null
            mutableRunning.value = false
        }
    }

    companion object {
        @Volatile
        private var activeService: WeakReference<CashierAccessibilityService>? = null
        private val mutableRunning = MutableStateFlow(false)

        /** True while the system has the service bound. */
        val running: StateFlow<Boolean> = mutableRunning.asStateFlow()

        fun isConnected(): Boolean = activeService?.get() != null

        fun active(): CashierAccessibilityService? = activeService?.get()
    }
}
