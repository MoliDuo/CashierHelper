package pro.xiangyu.cashierhelper.tasks

import android.net.Uri
import java.time.Instant
import java.time.ZoneId
import pro.xiangyu.cashierhelper.images.EntryDates
import pro.xiangyu.cashierhelper.images.LoadedImage
import pro.xiangyu.cashierhelper.images.SharedImageLoader

/** How many tasks were started and how many images could not be read. */
data class Submission(val started: Int, val unreadable: Int)

/** Turns shared or picked images into tasks, either one bill per image or one bill from several. */
class SharedImageSubmitter(
    private val loader: SharedImageLoader,
    private val intake: TaskIntake,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    /**
     * Reads every image before anything is stored, because the permission to read
     * them can end as soon as the receiving screen closes. [together] puts all
     * images into a single task and is only honoured for 2 to [TaskIntake.MAX_IMAGES] images.
     */
    suspend fun submit(source: TaskSource, uris: List<Uri>, together: Boolean): Submission {
        val loaded = uris.take(MAX_SELECTION).map { loader.load(it) }
        val images = loaded.filterNotNull()
        val unreadable = loaded.size - images.size
        if (images.isEmpty()) return Submission(0, unreadable)

        if (together && images.size in 2..TaskIntake.MAX_IMAGES) {
            intake.create(source, images.map { it.jpeg }, entryDate(images.first()))
            return Submission(1, unreadable)
        }
        images.forEach { intake.create(source, listOf(it.jpeg), entryDate(it)) }
        return Submission(images.size, unreadable)
    }

    private fun entryDate(image: LoadedImage): String =
        EntryDates.forShared(image.takenAtMillis, Instant.ofEpochMilli(now()), zone())

    companion object {
        const val MAX_SELECTION = 9

        /** The "same bill or separate bills" question only makes sense for a few images. */
        fun asksHowToGroup(count: Int): Boolean = count in 2..TaskIntake.MAX_IMAGES
    }
}
