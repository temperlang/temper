package lang.temper.fs

import lang.temper.common.urlDecode
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import java.io.File
import java.io.FileNotFoundException
import java.net.URI
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.absolute
import kotlin.io.path.name
import kotlin.io.path.toPath
import kotlin.streams.toList

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual typealias NativePath = Path

actual val NativePath.filePathSegment: FilePathSegment
    get() = FilePathSegment(this.fileName.toString())

actual fun NativePath.read(): String = Files.readString(this)
actual fun NativePath.list(): List<NativePath> =
    Files.list(this).map { it.absolute() }.toList()

actual fun temperSubRoot(obj: Any): NativePath {
    var uri: URI = obj::class.java.getResource("/marker.txt")?.toURI()
        ?: throw FileNotFoundException("/marker.txt")
    if (uri.scheme == "jar") {
        // jar:file:foobar -> file:foobar
        uri = URI(uri.schemeSpecificPart)
    }
    return uri.toPath().parent.parent.parent.parent
}

actual val temperRoot: NativePath by lazy {
    temperSubRoot(object {}).parent
}

actual fun NativePath.resolve(relative: FilePath): NativePath {
    var node = this
    for (seg in relative.segments) {
        node = node.resolve(seg.fullName)
    }
    return node
}

actual fun NativePath.resolveEntry(entry: String): NativePath = this.resolve(entry)

actual fun NativePath.rmrf() {
    removeDirRecursive(this)
}

actual fun NativePath.mkdir() {
    Files.createDirectories(this)
}

actual fun NativePath.walk(block: (NativePath, FilePath) -> WalkSignal) {
    val base = this
    Files.walkFileTree(
        this,
        object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val rel = base.relativize(file).asFilePath(isDir = false)
                val sig = block(file, rel)
                return sig.asVisitResult()
            }

            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val rel = base.relativize(dir).asFilePath(isDir = true)
                val sig = block(dir, rel)
                return sig.asVisitResult()
            }
        },
    )
}

actual fun NativePath.stat(): NativeStat {
    val attrs = Files.readAttributes(this, BasicFileAttributes::class.java)
    return JvmStat(attrs.isDirectory, attrs.isRegularFile)
}

actual val nativeConvention get() =
    if (File.separator == "\\") NativeConvention.Windows else NativeConvention.Posix

data class JvmStat(override val isDir: Boolean, override val isFile: Boolean) : NativeStat

fun WalkSignal.asVisitResult() = when (this) {
    WalkSignal.Continue -> FileVisitResult.CONTINUE
    WalkSignal.SkipSubtree -> FileVisitResult.SKIP_SUBTREE
    WalkSignal.Stop -> FileVisitResult.TERMINATE
}

/** Only use for a relative path. */
fun Path.asFilePath(isDir: Boolean): FilePath {
    val parts = map { it.name }

    if (isDir && parts == listOf("")) {
        return FilePath.emptyPath
    }
    return FilePath(parts.map(::FilePathSegment), isDir = isDir)
}

fun FilePath.asPath(): Path =
    if (this.segments.isNotEmpty()) {
        val decodedSegments = this.segments.map { urlDecode(it.fullName)!! }
        @Suppress("SpreadOperator") // No other API
        Path.of(decodedSegments.first(), *decodedSegments.drop(1).toTypedArray())
    } else {
        Path.of(".")
    }
