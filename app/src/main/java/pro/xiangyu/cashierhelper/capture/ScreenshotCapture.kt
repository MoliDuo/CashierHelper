package pro.xiangyu.cashierhelper.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.Display
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

class ScreenshotException(val errorCode: Int) : Exception("Screenshot failed with error code $errorCode")

/** Takes one screenshot of the default display as a software bitmap. */
suspend fun AccessibilityService.captureScreen(): Result<Bitmap> = withIntervalRetry {
    suspendCancellableCoroutine { continuation ->
        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                val result = copyToSoftwareBitmap(screenshot)
                if (continuation.isActive) continuation.resume(result) else result.getOrNull()?.recycle()
            }

            override fun onFailure(errorCode: Int) {
                if (continuation.isActive) continuation.resume(Result.failure(ScreenshotException(errorCode)))
            }
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resume(Result.failure(error))
        }
    }
}

private fun copyToSoftwareBitmap(screenshot: AccessibilityService.ScreenshotResult): Result<Bitmap> = runCatching {
    val buffer = screenshot.hardwareBuffer
    try {
        val wrapped = checkNotNull(Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)) {
            "Unable to read the screenshot buffer"
        }
        try {
            checkNotNull(wrapped.copy(Bitmap.Config.ARGB_8888, false)) { "Unable to copy the screenshot buffer" }
        } finally {
            wrapped.recycle()
        }
    } finally {
        buffer.close()
    }
}

/**
 * Android refuses a screenshot that follows the previous one too closely. A
 * quick second double press is common, so wait a moment and try again instead
 * of reporting a failure.
 */
internal suspend fun <T> withIntervalRetry(
    attempts: Int = 3,
    pauseMillis: Long = 450,
    pause: suspend (Long) -> Unit = { delay(it) },
    takeOnce: suspend () -> Result<T>,
): Result<T> {
    var result = takeOnce()
    var remaining = attempts - 1
    while (remaining > 0 && result.isTooSoon()) {
        pause(pauseMillis)
        result = takeOnce()
        remaining--
    }
    return result
}

private fun <T> Result<T>.isTooSoon(): Boolean =
    (exceptionOrNull() as? ScreenshotException)?.errorCode ==
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT

/** What to tell her when a screenshot could not be taken. */
object ScreenshotProblem {
    data class Text(val title: String, val message: String)

    fun describe(error: Throwable?): Text = when ((error as? ScreenshotException)?.errorCode) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ->
            Text("这个页面不允许截图", "付款页面通常不能截图，可以在付款完成页再试")
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
            Text("双击得太快了", "请稍等一下，再双击一次侧键")
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
            Text("截图服务没有权限", "请在辅助功能里把「Cashier 记账截图服务」关闭再打开")
        else -> Text("没有截到屏幕", "请再双击一次侧键")
    }
}
