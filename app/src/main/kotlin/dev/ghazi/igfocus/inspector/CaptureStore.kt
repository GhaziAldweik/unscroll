package dev.ghazi.igfocus.inspector

import android.content.Context
import android.util.Log
import dev.ghazi.igfocus.LOG_TAG
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Captures saved in the app's private storage as one small JSON file.
 * The file leaves the phone only when the user taps Share or Copy.
 */
class CaptureStore(private val file: File) {
    private val _captures = MutableStateFlow(load())
    val captures: StateFlow<List<Capture>> = _captures.asStateFlow()

    @Synchronized
    fun add(capture: Capture) = save(_captures.value + capture)

    @Synchronized
    fun removeAt(index: Int) {
        val current = _captures.value
        if (index in current.indices) save(current.filterIndexed { i, _ -> i != index })
    }

    @Synchronized
    fun clear() = save(emptyList())

    fun exportJson(): String = CaptureJson.encode(_captures.value)

    private fun load(): List<Capture> {
        if (!file.exists()) return emptyList()
        return try {
            CaptureJson.decode(file.readText())
        } catch (e: Exception) {
            // Keep the unreadable file for inspection instead of overwriting it on the next save.
            Log.w(LOG_TAG, "Capture file unreadable; set aside as ${file.name}.corrupt", e)
            file.renameTo(File(file.parentFile, "${file.name}.corrupt"))
            emptyList()
        }
    }

    private fun save(captures: List<Capture>) {
        try {
            // Write beside the real file, then swap, so a crash cannot leave half a file.
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(CaptureJson.encode(captures))
            if (!temp.renameTo(file)) {
                file.delete()
                check(temp.renameTo(file)) { "could not replace ${file.name}" }
            }
            _captures.value = captures
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Could not save captures", e)
        }
    }

    companion object {
        @Volatile
        private var instance: CaptureStore? = null

        /** One store for the whole process, shared by the service and the screens. */
        fun get(context: Context): CaptureStore =
            instance ?: synchronized(this) {
                instance ?: CaptureStore(File(context.applicationContext.filesDir, "captures.json"))
                    .also { instance = it }
            }
    }
}
