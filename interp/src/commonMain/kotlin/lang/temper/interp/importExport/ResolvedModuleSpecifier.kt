package lang.temper.interp.importExport

import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.FileRelatedCodeLocation
import lang.temper.log.SharedLocationContext
import lang.temper.name.DashedIdentifier
import lang.temper.name.LibraryNameLocationKey
import lang.temper.name.ModuleLocation
import lang.temper.name.ModuleName

/**
 * A prefix for resolved specifiers that refer to local [lang.temper.log.FilePath]s.
 */
const val LOCAL_FILE_SPECIFIER_PREFIX = "file:"

/** Enable custom handling of standard library resolution. */
const val STANDARD_LIBRARY_NAME = "std"
const val STANDARD_LIBRARY_SPECIFIER_PREFIX = "$STANDARD_LIBRARY_NAME/"

val STANDARD_LIBRARY_FILEPATH = FilePath(listOf(FilePathSegment(STANDARD_LIBRARY_NAME)), isDir = true)

data class ResolvedModuleSpecifier(
    val text: String,
)

fun ModuleLocation.isEffectivelyStd(sharedLocationContext: SharedLocationContext?): Boolean {
    if (this is ModuleName) {
        if (this.isPreface) { return false }
        val libraryName = sharedLocationContext?.get(this, LibraryNameLocationKey)
        if (libraryName == DashedIdentifier.temperStandardLibraryIdentifier) {
            return true
        }
    }
    if (this is FileRelatedCodeLocation) {
        // Recognize fake std, such as when editing them as ordinary files.
        val sourceFile = this.sourceFile
        if (sourceFile.segments.firstOrNull()?.fullName == STANDARD_LIBRARY_NAME) {
            return true
        }
    }
    return false
}
