package pro.xiangyu.cashierhelper.capture

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short buzzes that confirm the double press even when the screen shows nothing. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = context.applicationContext.let { app ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** One short tick: the screenshot was taken. */
    fun confirm() = vibrate(longArrayOf(0, 35))

    /** Two longer buzzes: nothing was captured. */
    fun error() = vibrate(longArrayOf(0, 90, 60, 90))

    @Suppress("DEPRECATION")
    private fun vibrate(pattern: LongArray) {
        val vibrator = vibrator ?: return
        val effect = VibrationEffect.createWaveform(pattern, -1)
        // Tagged as touch feedback so it is not swallowed like a notification buzz.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
    }
}
