package pro.xiangyu.cashierhelper.capture

import android.accessibilityservice.AccessibilityService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotCaptureTest {
    private fun tooSoon() = Result.failure<String>(
        ScreenshotException(AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT),
    )

    @Test
    fun `a screenshot that follows too closely is retried after a pause`() = runBlocking {
        val results = ArrayDeque(listOf(tooSoon(), Result.success("shot")))
        val pauses = mutableListOf<Long>()

        val result = withIntervalRetry(pause = { pauses += it }) { results.removeFirst() }

        assertEquals("shot", result.getOrNull())
        assertEquals(listOf(450L), pauses)
    }

    @Test
    fun `it gives up after three attempts`() = runBlocking {
        var calls = 0

        val result = withIntervalRetry(pause = {}) { calls++; tooSoon() }

        assertTrue(result.isFailure)
        assertEquals(3, calls)
    }

    @Test
    fun `other failures are not retried`() = runBlocking {
        var calls = 0

        val result = withIntervalRetry<String>(pause = {}) {
            calls++
            Result.failure(ScreenshotException(AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW))
        }

        assertTrue(result.isFailure)
        assertEquals(1, calls)
    }

    @Test
    fun `each failure has a sentence for her`() {
        fun title(code: Int?) = ScreenshotProblem.describe(code?.let { ScreenshotException(it) }).title

        assertEquals("这个页面不允许截图", title(AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW))
        assertEquals("双击得太快了", title(AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT))
        assertEquals("截图服务没有权限", title(AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS))
        assertEquals("没有截到屏幕", title(AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR))
        assertEquals("没有截到屏幕", title(null))
    }
}
