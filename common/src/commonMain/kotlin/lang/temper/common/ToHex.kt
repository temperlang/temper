package lang.temper.common

fun (Any?).toHex(): String = when (this) {
    null -> "null"
    is Byte -> this.toHex()
    is Short -> this.toHex()
    is Int -> this.toHex()
    is Long -> this.toHex()
    else -> this.toString()
}

fun Byte.toHex() = (this.toInt() and UNSIGNED_BYTE_MASK).toString(HEX_RADIX)
fun Short.toHex() = this.toString(HEX_RADIX)
fun Int.toHex() = this.toString(HEX_RADIX)
fun Long.toHex() = this.toString(HEX_RADIX)
fun ByteArray.toHex(): String = buildString {
    val array = this@toHex
    append("size=").append(array.size).append("; [")
    for (i in array.indices) {
        append(array[i].toHex().padStart(2, '0'))
    }
    append("]")
}

fun (Any?).toHexPadded(pad: Int) = when (this) {
    is Byte, is Short, is Int, is Long -> this.toHex().padStart(pad, '0')
    else -> this.toHex().padStart(pad, ' ')
}

private const val UNSIGNED_BYTE_MASK = 0xFF
