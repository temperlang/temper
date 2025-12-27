package lang.temper.common

private val Buffer = js("(typeof Buffer !== 'undefined' ? Buffer : null)")

actual fun (ByteArray).base64EncodeTo(out: Appendable, off: Int, len: Int) {
    val bytes = if (off == 0 && len == this.size) {
        this
    } else {
        this.sliceArray(off until (off + len))
    }
    out.append(Buffer.from(bytes).toString("base64") as String)
}
