package lang.temper.library

import lang.temper.fs.FileSystem
import lang.temper.lexer.LanguageConfig
import lang.temper.log.CodeLocationKey
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.plus
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier
import lang.temper.name.ParsedName
import lang.temper.name.Symbol
import lang.temper.value.TString
import lang.temper.value.Value

/**
 * A library configuration may export important metadata bits, which are bundled together here.
 */
class LibraryConfiguration(
    val libraryName: DashedIdentifier,
    val libraryRoot: FilePath,
    val supportedBackendList: List<BackendId>,
    val classifyTemperSource: (FilePathSegment) -> LanguageConfig?,
    val configExports: Map<Symbol, Value<*>> = emptyMap(),
) {
    companion object {
        val libraryNameParsedName = ParsedName("name")
        val fileName = FilePathSegment("config.temper.md")
    }

    override fun toString(): String {
        val klass = this::class.simpleName ?: "LibraryConfiguration"
        return "$klass(libraryName=$libraryName, libraryRoot=$libraryRoot, supportedBackendList=$supportedBackendList)"
    }

    fun copy(
        supportedBackendList: List<BackendId> = this.supportedBackendList,
    ) = LibraryConfiguration(
        libraryName = libraryName,
        libraryRoot = libraryRoot,
        supportedBackendList = supportedBackendList,
        classifyTemperSource = classifyTemperSource,
        configExports = configExports,
    )
}

/**
 * Configuration export name text for the authors of the library. Format is unspecified for now, but a single
 * "name &lt;email>" (which could be a group) is probably safest for now.
 * TODO Make this a list of Author instances once we can represent that as a constant value.
 */
val authorsSymbol = Symbol("authors")

/** Configuration export name text for library description. Format is unspecified. */
val descriptionSymbol = Symbol("description")

/** Configuration export name text for the homepage URL of the library. */
val homepageSymbol = Symbol("homepage")

/** Configuration export name text for a [SPDX license code](https://spdx.org/licenses/) */
val licenseSymbol = Symbol("license")

/**
 * Configuration export name text for the source control repository URL.
 * For now, treat this as a link for humans, though repo specifics might be possible to determine in some cases.
 * Example: "https://github.com/temperlang/temper"
 * TODO Make this some Repository instance once we can represent that as a constant value.
 */
val repositorySymbol = Symbol("repository")

/** Configuration export name text for a version identifier.  Please use semver. */
val versionSymbol = Symbol("version")

fun LibraryConfiguration.backendLibraryName(key: Symbol): String {
    val backendSpecific = TString.unpackOrNull(configExports[key])
    val generic = libraryName.text
    return if (backendSpecific.isNullOrBlank()) {
        generic
    } else {
        backendSpecific
    }
}

fun LibraryConfiguration.authors() = TString.unpackOrNull(configExports[authorsSymbol])
fun LibraryConfiguration.description() = TString.unpackOrNull(configExports[descriptionSymbol])
fun LibraryConfiguration.homepage() = TString.unpackOrNull(configExports[homepageSymbol])
fun LibraryConfiguration.license() = TString.unpackOrNull(configExports[licenseSymbol])
fun LibraryConfiguration.repository() = TString.unpackOrNull(configExports[repositorySymbol])
fun LibraryConfiguration.version() = TString.unpackOrNull(configExports[versionSymbol])
fun LibraryConfiguration.versionOrDefault() = version() ?: "0.0.1"

// TODO: is this necessary if we have a compiler session?
fun FilePath.findRoot(fileSystem: FileSystem): FilePath? {
    var dir = if (this.isDir) { this } else { this.dirName() }
    if (!dir.isDir) {
        return null
    }
    while (true) {
        val candidate = dir + LibraryConfiguration.fileName
        if (fileSystem.isFile(candidate)) {
            return candidate
        }
        val parent = dir.dirName()
        if (parent != dir) {
            dir = parent
        } else {
            return null
        }
    }
}

/**
 * Allows looking up library configurations by library root directory.
 */
object LibraryConfigurationLocationKey : CodeLocationKey<LibraryConfiguration> {
    override fun cast(x: Any) = x as LibraryConfiguration
}

fun LibraryConfiguration.outputDirectory(backendId: BackendId) =
    relativeOutputDirectoryForLibrary(backendId, libraryName)

/**
 * Translations are written to *temper.out/backend-id/library-name*.
 * This returns the last two path segments: *backend-id/library-name*.
 */
fun relativeOutputDirectoryForLibrary(
    backendId: BackendId,
    libraryName: DashedIdentifier,
): FilePath = FilePath(
    listOf(
        FilePathSegment(backendId.uniqueId),
        // We know that a library name is a valid path segment because of restrictions on
        // sensitive ascii characters in how they're derived, and also they can't be empty.
        FilePathSegment(libraryName.text),
    ),
    isDir = true,
)
