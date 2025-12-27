package lang.temper.common

import java.io.OutputStream
import java.util.Base64

actual fun (ByteArray).base64EncodeTo(out: Appendable, off: Int, len: Int) {
    val encoder = Base64.getEncoder() // RFC 4648 version
    if (out is OutputStream) {
        encoder.wrap(out).write(this, off, len)
    } else {
        out.append(encoder.encodeToString(this.sliceArray(off until (off + len))))
    }
}
