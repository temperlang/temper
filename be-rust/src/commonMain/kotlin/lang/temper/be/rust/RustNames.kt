package lang.temper.be.rust

import lang.temper.be.TargetLanguageTypeName
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSink
import lang.temper.log.FilePath
import lang.temper.name.SourceName

class RustNames(
    val packageNaming: PackageNaming,
    val packageNamingsByRoot: Map<FilePath, PackageNaming>,
) {
    /** Not keyed on module because we only reserve source names for the current module. */
    private val reservedNames = mutableMapOf<String, SourceName>()

    /** Returns the owner of [text], which might be [sourceName] if not a previous owner. */
    fun reserveName(sourceName: SourceName, text: String): SourceName {
        return reservedNames.getOrPut(text) { sourceName }
    }
}

data class PackageNaming(
    val packageName: String,
    /**
     * The name cargo gives the package's library crate: the package name with each `-` made `_`.
     * Not [dashToSnake], which re-splits words around digits: `f64str` to `f64_str`, `radix-36` to `radix36`.
     */
    val crateName: String = packageName.replace('-', '_'),
)

enum class ConnectedType : TargetLanguageTypeName {
    StringBuilder,
    ;

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutputToken(toString(), OutputTokenType.Name))
    }
}

internal fun makeRustNames(backend: RustBackend): RustNames {
    val libraryConfig = backend.libraryConfigurations.currentLibraryConfiguration
    val rootPackageName = RustLibraryConfig(libraryConfig).packageNaming()
    val rootPackageNames = backend.libraryConfigurations.byLibraryRoot.values.map { config ->
        config to RustLibraryConfig(config).packageNaming()
    }
    return RustNames(
        packageNaming = rootPackageName,
        packageNamingsByRoot = rootPackageNames.associate { it.first.libraryRoot to it.second },
    )
}
