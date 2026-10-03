package pro.xiangyu.cashierhelper.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Used by the setup guide's last step: while it is waiting for her to try the
 * side key, a screenshot is taken and thrown away instead of being uploaded,
 * and the guide is told it worked.
 */
class TriggerTrial {
    private val mutableArmed = MutableStateFlow(false)
    private val mutableSucceeded = MutableStateFlow(false)

    val armed: StateFlow<Boolean> = mutableArmed.asStateFlow()
    val succeeded: StateFlow<Boolean> = mutableSucceeded.asStateFlow()

    fun arm() {
        mutableSucceeded.value = false
        mutableArmed.value = true
    }

    fun disarm() {
        mutableArmed.value = false
    }

    fun reportSuccess() {
        if (mutableArmed.value) mutableSucceeded.value = true
    }
}
