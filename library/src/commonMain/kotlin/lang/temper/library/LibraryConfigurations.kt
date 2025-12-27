package lang.temper.library

import lang.temper.common.commonPrefixLength
import lang.temper.log.FilePath
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModularName
import lang.temper.name.ModuleName
import lang.temper.name.TemperName

internal class LibraryMaps(libraryConfigurations: Iterable<LibraryConfiguration>) {
    val byLibraryName: Map<DashedIdentifier, LibraryConfiguration> =
        libraryConfigurations.associateBy { it.libraryName }
    val byLibraryRoot: Map<FilePath, LibraryConfiguration> =
        libraryConfigurations.associateBy { it.libraryRoot }

    init {
        check(byLibraryName.size == byLibraryRoot.size)
    }
}

/**
 * A group of configurations for libraries including ones that are translated together,
 * and ones that the former may depend upon.
 */
sealed class AbstractLibraryConfigurations(
    private val libraryMaps: LibraryMaps,
) {
    val byLibraryName: Map<DashedIdentifier, LibraryConfiguration> get() = libraryMaps.byLibraryName
    val byLibraryRoot: Map<FilePath, LibraryConfiguration> get() = libraryMaps.byLibraryRoot

    fun toBundle() = LibraryConfigurationsBundle(libraryMaps)
    fun withCurrentLibrary(libraryName: DashedIdentifier) =
        LibraryConfigurations(libraryMaps, libraryMaps.byLibraryName.getValue(libraryName))
    fun withCurrentLibrary(libraryConfiguration: LibraryConfiguration) =
        LibraryConfigurations(libraryMaps, libraryConfiguration)

    fun forFilePath(filePath: FilePath): LibraryConfiguration? {
        val dir = if (filePath.isDir) {
            filePath
        } else {
            filePath.dirName()
        }
        var maxDepth = -1
        var best: LibraryConfiguration? = null
        for ((path, configuration) in byLibraryRoot) {
            val prefixCount = commonPrefixLength(path.segments, dir.segments)
            if (prefixCount == path.segments.size && prefixCount > maxDepth) {
                maxDepth = prefixCount
                best = configuration
            }
        }
        return best
    }
}

class LibraryConfigurationsBundle internal constructor(
    libraryMaps: LibraryMaps,
) : AbstractLibraryConfigurations(libraryMaps) {
    companion object {
        val empty = LibraryConfigurationsBundle(LibraryMaps(emptyList()))

        fun from(configurations: Iterable<LibraryConfiguration>) =
            LibraryConfigurationsBundle(LibraryMaps(configurations))
    }
}

/**
 * Bundles the *current* library configuration with a way to get configurations for other
 * libraries by root or name.
 */
class LibraryConfigurations internal constructor(
    libraryMaps: LibraryMaps,
    val currentLibraryConfiguration: LibraryConfiguration,
) : AbstractLibraryConfigurations(libraryMaps) {
    init {
        check(currentLibraryConfiguration == libraryMaps.byLibraryName[currentLibraryConfiguration.libraryName])
    }
}

fun AbstractLibraryConfigurations.definingName(name: TemperName): LibraryConfiguration? =
    ((name as? ModularName)?.origin?.loc as? ModuleName)?.libraryRoot()?.let { libraryRoot ->
        this.byLibraryRoot[libraryRoot]
    }
