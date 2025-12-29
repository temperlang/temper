package lang.temper.common

actual fun isatty(fd: Int): Boolean {
    return platform.posix.isatty(fd) != 0
}
