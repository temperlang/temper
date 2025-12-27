package lang.temper.common

import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toCValues
import platform.posix.EOF
import platform.posix.fflush
import platform.posix.fputc
import platform.posix.fwrite
import platform.posix.stderr

@ExperimentalUnsignedTypes
private fun writeToErr(s: String) {
    val byteArray = s.encodeToByteArray()
    val nBytes = byteArray.size.toULong()
    memScoped {
        fwrite(byteArray.toCValues(), 1, nBytes, stderr) == nBytes ||
            error("printErr output truncated")
    }
}

actual fun printErr(s: String) {
    writeToErr(s)
    fputc(CVAL_NEWLINE, stderr) != EOF || error("printErr output truncated")
}

actual fun printErrNoEol(s: String) {
    writeToErr(s)
    fflush(1)
}

private const val CVAL_NEWLINE = 0xA
