package pro.xiangyu.cashierhelper.images

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageFilesTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `stores images in order and leaves no temporary file behind`() {
        val files = ImageFiles(temp.newFolder("images"))
        files.write("task", 1, byteArrayOf(2))
        files.write("task", 0, byteArrayOf(1))

        val stored = files.list("task")
        assertEquals(listOf("00.jpg", "01.jpg"), stored.map { it.name })
        assertArrayEquals(byteArrayOf(1), stored[0].readBytes())
        assertEquals(listOf("00.jpg", "01.jpg"), files.dir("task").list()!!.sorted())
    }

    @Test
    fun `a half written image is not listed`() {
        val files = ImageFiles(temp.newFolder("images"))
        files.write("task", 0, byteArrayOf(1))
        files.dir("task").resolve("01.tmp").writeBytes(byteArrayOf(9))

        assertEquals(listOf("00.jpg"), files.list("task").map { it.name })
    }

    @Test
    fun `delete removes the whole folder and unknown tasks list as empty`() {
        val files = ImageFiles(temp.newFolder("images"))
        files.write("task", 0, byteArrayOf(1))
        assertEquals(setOf("task"), files.taskIds())

        files.delete("task")

        assertTrue(files.list("task").isEmpty())
        assertTrue(files.taskIds().isEmpty())
        assertTrue(files.list("never-existed").isEmpty())
    }
}
