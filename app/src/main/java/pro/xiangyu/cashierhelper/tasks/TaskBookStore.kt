package pro.xiangyu.cashierhelper.tasks

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object TaskBookSerializer : Serializer<TaskBook> {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    override val defaultValue: TaskBook = TaskBook()

    override suspend fun readFrom(input: InputStream): TaskBook = try {
        json.decodeFromString(TaskBook.serializer(), input.readBytes().decodeToString())
    } catch (error: SerializationException) {
        throw CorruptionException("Unreadable task list", error)
    } catch (error: IllegalArgumentException) {
        throw CorruptionException("Unreadable task list", error)
    }

    override suspend fun writeTo(t: TaskBook, output: OutputStream) {
        output.write(json.encodeToString(TaskBook.serializer(), t).encodeToByteArray())
    }
}

object TaskBookStore {
    /**
     * A task list that cannot be read is set aside under a new name rather than
     * deleted, so it can still be looked at; the app carries on with an empty list.
     * The images of those tasks stay on disk.
     */
    fun create(file: File, scope: CoroutineScope): DataStore<TaskBook> = DataStoreFactory.create(
        serializer = TaskBookSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler {
            file.renameTo(File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}"))
            TaskBook()
        },
        scope = scope,
        produceFile = { file },
    )
}
