package lang.temper.fs

import lang.temper.common.console
import lang.temper.log.FilePath
import lang.temper.log.asUnixPath
import lang.temper.log.asUnixPathAbsolute
import java.net.URI
import kotlin.reflect.KClass

actual typealias Url = URI
actual typealias ResourceLoader = ClassLoader
actual fun KClass<*>.loader(): ResourceLoader = this.java.classLoader!!

actual fun ResourceLoader.url(path: FilePath): Url? = this.getResource(path.asUnixPathAbsolute())?.toURI()

actual fun ResourceLoader.load(path: FilePath, charset: KCharset): String? =
    loadBinary(path)?.let { charset.decodeToString(it) }

actual fun ResourceLoader.loadBinary(path: FilePath): ByteArray? {
    val rsrcPath = path.asUnixPath()
    val result = getResourceAsStream(rsrcPath)?.readAllBytes()
    if (result == null) {
        console.warn { "Resource at '$rsrcPath' not found" }
    }
    return result
}
