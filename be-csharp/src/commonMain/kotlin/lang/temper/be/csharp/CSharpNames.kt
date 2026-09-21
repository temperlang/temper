package lang.temper.be.csharp

import lang.temper.be.names.LookupNameVisitor
import lang.temper.be.names.NameLookup
import lang.temper.be.tmpl.TmpL
import lang.temper.interp.importExport.STANDARD_LIBRARY_NAME
import lang.temper.library.LibraryConfiguration
import lang.temper.log.FilePath
import lang.temper.name.ModuleName
import lang.temper.name.QName
import lang.temper.name.ResolvedName
import lang.temper.value.TString

class CSharpNames(
    val nameLookup: NameLookup,
    val rootNamespace: String,
    val rootNamespaces: List<Pair<LibraryConfiguration, String>>,
    val rootNamespacesByRoot: Map<FilePath, String>,
) {
    val nameSelection = mutableMapOf<QName, String>()
    private val nameSource = mutableMapOf<Pair<ModuleName, String>, ResolvedName>()
    private val qNameMappings: Map<Pair<ModuleName, ResolvedName>, QName?> =
        nameLookup.qNameMappings().filter { (_, qName) -> qName != null }

    fun putSelection(codeLocation: ModuleName, name: ResolvedName, nameText: String): Boolean {
        return qNameMappings[codeLocation to name]?.let { nameSelection.putIfAbsent(it, nameText) } != null
    }

    fun reserveName(module: ModuleName, sourceName: ResolvedName, wantedName: String): Boolean {
        return when (nameSource.putIfAbsent(module to wantedName, sourceName)) {
            null, sourceName -> true
            else -> false
        }
    }
}

private fun chooseRootNamespace(config: LibraryConfiguration) =
    config.configExports[csharpRootNamespaceKey]?.let { value ->
        TString.unpackOrNull(value)
    } ?: when (config.libraryName.text) {
        // Hardcode std because we don't yet get config exports in funtests.
        STANDARD_LIBRARY_NAME -> STD_ROOT_NAMESPACE
        else -> config.libraryName.text.dashToPascal()
    }

internal fun makeCSharpNames(
    backend: CSharpBackend,
    moduleSet: TmpL.ModuleSet,
): CSharpNames {
    val libraryConfig = moduleSet.libraryConfiguration
    val rootNamespace = chooseRootNamespace(libraryConfig)
    val rootNamespaces = backend.libraryConfigurations.byLibraryRoot.values.map { it to chooseRootNamespace(it) }
    return CSharpNames(
        nameLookup = LookupNameVisitor().visit(moduleSet).toLookup(),
        rootNamespace = rootNamespace,
        rootNamespaces = rootNamespaces,
        rootNamespacesByRoot = rootNamespaces.associate { it.first.libraryRoot to it.second },
    )
}
