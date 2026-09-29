package lang.temper.be.rust

import lang.temper.be.TargetLanguageTypeName
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSink
import lang.temper.log.FilePath

class RustNames(
    val packageNaming: PackageNaming,
    val packageNamingsByRoot: Map<FilePath, PackageNaming>,
)

data class PackageNaming(
    val packageName: String,
    val crateName: String = packageName.dashToSnake(),
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
