@file:Suppress("MatchingDeclarationName")
package lang.temper.fs

actual abstract class KCharset

actual fun KCharset.encodeToBytes(str: String): ByteArray = TODO()
actual fun KCharset.decodeToString(bytes: ByteArray): String = TODO()

private object Utf8Instance : KCharset()

actual val KCharsets.utf8: KCharset get() = Utf8Instance
