package lang.temper.common

/**
 * Best effort to write a file.
 * Useful for debugging where the JS backend is actually running in Node.
 *
 * @return true on success
 */
expect fun writeAFileBestEffort(content: String, fileName: String): Boolean
