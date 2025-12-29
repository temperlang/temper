package lang.temper.interp.importExport

import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment

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
