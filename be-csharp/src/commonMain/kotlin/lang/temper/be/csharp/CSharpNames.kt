package lang.temper.be.csharp

import lang.temper.be.names.LookupNameVisitor
import lang.temper.be.names.NameLookup
import lang.temper.be.tmpl.TmpL
import lang.temper.library.LibraryConfiguration
import lang.temper.log.FilePath
import lang.temper.name.ModuleName
import lang.temper.name.QName
import lang.temper.name.ResolvedName

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

internal fun makeCSharpNames(
    backend: CSharpBackend,
    moduleSet: TmpL.ModuleSet,
): CSharpNames {
    val libraryConfig = moduleSet.libraryConfiguration
    val rootNamespace = CSharpLibraryConfig(libraryConfig).rootNamespace()
    val rootNamespaces = backend.libraryConfigurations.byLibraryRoot.values.map { libraryConfig ->
        libraryConfig to CSharpLibraryConfig(libraryConfig).rootNamespace()
    }
    return CSharpNames(
        nameLookup = LookupNameVisitor().visit(moduleSet).toLookup(),
        rootNamespace = rootNamespace,
        rootNamespaces = rootNamespaces,
        rootNamespacesByRoot = rootNamespaces.associate { it.first.libraryRoot to it.second },
    )
}
