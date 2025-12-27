@file:Suppress("MatchingDeclarationName")
package lang.temper.fs

import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.FilePathSegmentOrPseudoSegment

actual class NativePath

actual val (NativePath).filePathSegment: FilePathSegment
    get() = TODO()

actual val temperRoot: NativePath
    get() = TODO()

actual fun NativePath.resolve(relative: FilePath): NativePath = TODO()
actual fun NativePath.resolveEntry(entry: String): NativePath = TODO()
actual fun NativePath.resolveEntry(entry: FilePathSegmentOrPseudoSegment): NativePath = TODO()

actual fun NativePath.read(): String = TODO()
actual fun NativePath.list(): List<NativePath> = TODO()

actual fun NativePath.rmrf(): Unit = TODO()
actual fun NativePath.walk(block: (NativePath, FilePath) -> WalkSignal): Unit = TODO()
actual fun NativePath.stat(): NativeStat = TODO()
