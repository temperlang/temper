package lang.temper.fs

/** Wrapper or alias around Charset. */
expect class KCharset

expect fun KCharset.encodeToBytes(str: String): ByteArray
expect fun KCharset.decodeToString(bytes: ByteArray): String
expect val KCharset.name: String

/** Corresponds to [kotlin.text.Charsets] */
object KCharsets

expect val KCharsets.utf8: KCharset
