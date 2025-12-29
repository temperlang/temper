package lang.temper.frontend

import lang.temper.library.LibraryConfiguration
import lang.temper.library.LibraryConfigurationLocationKey
import lang.temper.log.CodeLocation
import lang.temper.log.CodeLocationKey
import lang.temper.log.FilePath
import lang.temper.log.FilePositions
import lang.temper.log.FileRelatedCodeLocation
import lang.temper.log.SharedLocationContext
import lang.temper.name.LibraryNameLocationKey
import lang.temper.name.ModuleName

/**
 * A [SharedLocationContext] that simplifies providing support for:
 *
 * - [CodeLocationKey.SourceCodeKey] via overriding [getSource]
 * - [CodeLocationKey.FilePositionsKey] via overriding [getFilePositions]
 *   and/or [getFilePositionsFromCache] and [getSource]
 * - [LibraryNameLocationKey] via overriding [getLibraryConfiguration]
 * - [LibraryConfigurationLocationKey] via overriding [getLibraryConfiguration]
 */
abstract class AbstractSharedLocationContext : SharedLocationContext {
    /** May be overridden to return the source for a file */
    open fun getSource(sourceFilePath: FilePath): String? { return null }

    /** May be overridden to return the file positions for a file */
    open fun getFilePositions(sourceFilePath: FilePath): FilePositions? = null

    /** May be overridden to construct file positions given known source text and to cache it. */
    open fun getFilePositionsFromCache(sourceFilePath: FilePath, source: String): FilePositions? = null

    /** May be overridden to look up a library configuration for a code location. */
    open fun getLibraryConfiguration(libraryRoot: FilePath): LibraryConfiguration? = null

    override fun <T : Any> get(loc: CodeLocation, v: CodeLocationKey<T>): T? {
        val sourceFile = (loc as? FileRelatedCodeLocation)?.sourceFile ?: return null
        return when (v) {
            CodeLocationKey.SourceCodeKey -> getSource(sourceFile)?.let { v.cast(it) }
            CodeLocationKey.FilePositionsKey -> {
                val positions = getFilePositions(sourceFile)
                if (positions != null) {
                    v.cast(positions)
                } else {
                    val sourceOrNull = getSource(sourceFile)
                    sourceOrNull?.let { source ->
                        getFilePositionsFromCache(sourceFile, source)?.let {
                            v.cast(it)
                        }
                    }
                }
            }
            LibraryNameLocationKey -> {
                get(loc, LibraryConfigurationLocationKey)?.let {
                    v.cast(it.libraryName)
                }
            }
            LibraryConfigurationLocationKey -> {
                val libraryRootOrNull = when {
                    loc is FilePath && loc.isDir -> loc
                    loc is ModuleName -> loc.libraryRoot()
                    else -> null
                }
                libraryRootOrNull?.let { libraryRoot ->
                    getLibraryConfiguration(libraryRoot)?.let { v.cast(it) }
                }
            }
            else -> null
        }
    }
}
