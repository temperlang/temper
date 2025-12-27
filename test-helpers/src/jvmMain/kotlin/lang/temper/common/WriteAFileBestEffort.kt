package lang.temper.common

import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Best effort to write a file.
 * Useful for debugging where the JS backend is actually running in Node.
 *
 * @return true on success
 */
actual fun writeAFileBestEffort(content: String, fileName: String): Boolean {
    return try {
        val path = File(fileName).toPath()
        Files.writeString(path, content, Charsets.UTF_8)
        true
    } catch (_: IOException) {
        false
    }
}
