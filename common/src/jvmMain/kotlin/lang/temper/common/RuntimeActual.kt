package lang.temper.common

actual fun jvmMajorVersion(): Int? {
    val version = Runtime.version().version()
    return if (version.isEmpty()) {
        null
    } else {
        version[0]
    }
}
