package pro.xiangyu.cashierhelper.images

import java.io.File
import java.io.FileOutputStream

/**
 * Where the images of unfinished tasks live: `<root>/<taskId>/<index>.jpg`.
 * Plain files in app-private storage, so a corrupted task list can never take
 * the screenshots with it.
 */
class ImageFiles(private val root: File) {
    fun dir(taskId: String): File = File(root, taskId)

    /** Writes through a temporary name so a crash never leaves a half-written image that looks complete. */
    fun write(taskId: String, index: Int, bytes: ByteArray): File {
        val dir = dir(taskId).also { it.mkdirs() }
        val target = File(dir, "%02d.jpg".format(index))
        val temp = File(dir, "%02d.tmp".format(index))
        FileOutputStream(temp).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        check(temp.renameTo(target)) { "Unable to store image $index of task $taskId" }
        return target
    }

    fun list(taskId: String): List<File> =
        dir(taskId).listFiles { file -> file.isFile && file.extension == "jpg" }
            ?.sortedBy { it.name }
            .orEmpty()

    fun delete(taskId: String) {
        dir(taskId).deleteRecursively()
    }

    /** Task ids that have a folder on disk, used to clean up folders whose task is gone. */
    fun taskIds(): Set<String> =
        root.listFiles { file -> file.isDirectory }?.map { it.name }?.toSet().orEmpty()
}
