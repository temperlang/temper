package lang.temper.common

/**
 * Best effort to write a file.
 * Useful for debugging where the JS backend is actually running in Node.
 *
 * @return true on success
 */
actual fun writeAFileBestEffort(content: String, fileName: String): Boolean {
    return try {
        val writeFileSync = js("require('fs').writeFileSync")
        writeFileSync(fileName, content)
        true
    } catch (_: Exception) {
        false
    }
}
