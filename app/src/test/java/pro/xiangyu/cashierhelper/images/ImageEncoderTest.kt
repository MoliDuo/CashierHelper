package pro.xiangyu.cashierhelper.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageEncoderTest {
    @Test
    fun `a phone sized screenshot keeps its dimensions`() {
        val bytes = ImageEncoder().encode(Bitmap.createBitmap(1440, 3120, Bitmap.Config.ARGB_8888))
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        assertEquals(1440, decoded.width)
        assertEquals(3120, decoded.height)
    }

    @Test
    fun `an oversized image is scaled to the long edge limit keeping the aspect ratio`() {
        val bytes = ImageEncoder(maxLongEdge = 1000).encode(Bitmap.createBitmap(1500, 3000, Bitmap.Config.ARGB_8888))
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        assertEquals(500, decoded.width)
        assertEquals(1000, decoded.height)
    }

    @Test
    fun `transparent areas become white instead of black`() {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
        val bytes = ImageEncoder().encode(source)
        val pixel = BitmapFactory.decodeByteArray(bytes, 0, bytes.size).getPixel(32, 32)

        assertTrue(Color.red(pixel) > 240 && Color.green(pixel) > 240 && Color.blue(pixel) > 240)
    }

    @Test
    fun `noisy images are shrunk until they fit the target size`() {
        val source = noise(1200, 1200)
        val unrestricted = ImageEncoder(targetBytes = Int.MAX_VALUE).encode(source)
        val target = unrestricted.size / 2

        val bytes = ImageEncoder(targetBytes = target).encode(source)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        assertTrue("${bytes.size} should be below $target", bytes.size <= target)
        assertTrue(decoded.width < 1200)
    }

    @Test
    fun `an image that cannot get under the hard cap is reported`() {
        try {
            ImageEncoder(targetBytes = 10, hardCapBytes = 100).encode(noise(400, 400))
            fail("expected ImageTooLargeException")
        } catch (expected: ImageTooLargeException) {
            // expected
        }
    }

    private fun noise(width: Int, height: Int): Bitmap {
        val random = Random(7)
        val pixels = IntArray(width * height) { Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
