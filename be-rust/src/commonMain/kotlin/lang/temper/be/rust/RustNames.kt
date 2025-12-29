package lang.temper.be.rust

import lang.temper.be.TargetLanguageTypeName
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSink
import lang.temper.interp.importExport.STANDARD_LIBRARY_NAME
import lang.temper.library.LibraryConfiguration
import lang.temper.log.FilePath
import lang.temper.name.Symbol
import lang.temper.value.TString

class RustNames(
    val packageNaming: PackageNaming,
    val packageNamingsByRoot: Map<FilePath, PackageNaming>,
)

data class PackageNaming(
    val packageName: String,
    val crateName: String,
)

enum class ConnectedType : TargetLanguageTypeName {
    StringBuilder,
    ;

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutputToken(toString(), OutputTokenType.Name))
    }
}

private fun chooseRootPackageName(config: LibraryConfiguration): PackageNaming {
    val packageName = config.configExports[rustPackageNameKey]?.let { value ->
        TString.unpackOrNull(value)
    } ?: when (config.libraryName.text) {
        // Hardcode std because we don't yet get config exports in funtests.
        STANDARD_LIBRARY_NAME -> STD_ROOT_PACKAGE_NAME
        else -> config.libraryName.text
    }
    // TODO Also allow configured crate name completely different from package name.
    val crateName = packageName.dashToSnake()
    return PackageNaming(packageName = packageName, crateName = crateName)
}

internal fun makeRustNames(backend: RustBackend): RustNames {
    val libraryConfig = backend.libraryConfigurations.currentLibraryConfiguration
    val rootPackageName = chooseRootPackageName(libraryConfig)
    val rootPackageNames = backend.libraryConfigurations.byLibraryRoot.values.map { it to chooseRootPackageName(it) }
    return RustNames(
        packageNaming = rootPackageName,
        packageNamingsByRoot = rootPackageNames.associate { it.first.libraryRoot to it.second },
    )
}

val rustPackageNameKey = Symbol("rustName")
const val STD_ROOT_PACKAGE_NAME = "temper-std"
