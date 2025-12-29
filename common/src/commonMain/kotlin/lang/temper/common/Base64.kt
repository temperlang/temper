package lang.temper.common

// TODO: Writing a b64 encoder is not that hard.  Just implement this as a Kotlin common function.
expect fun (ByteArray).base64EncodeTo(out: Appendable, off: Int, len: Int)

fun (ByteArray).base64EncodeTo(out: Appendable) = base64EncodeTo(out, 0, this.size)
fun (ByteArray).base64Encode(off: Int = 0, len: Int = this.size - off) = toStringViaBuilder {
    base64EncodeTo(it, off, len)
}
