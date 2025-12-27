package lang.temper.be.names

import lang.temper.be.tmpl.source
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ModuleName
import lang.temper.name.QName
import lang.temper.name.ResolvedName
import lang.temper.name.SourceName
import lang.temper.name.Temporary
import kotlin.Comparator

internal data class Info(val decl: DescriptorChain?, val qName: QName?, val use: MutableList<DescriptorChain>)

/** Update an Info object, possibly mutating the original. */
internal fun Info?.updateMutating(decl: DescriptorChain?, qName: QName?, use: Iterable<DescriptorChain>): Info {
    if (this == null) {
        return Info(decl, qName, use.toMutableList())
    }
    this.use.addAll(use)
    return if (decl != null || qName != null) {
        Info(decl ?: this.decl, qName ?: this.qName, this.use)
    } else {
        this
    }
}

open class NameLookup internal constructor(
    val allModules: Set<ModuleName>,
    private val nameMap: Map<Pair<ModuleName, ResolvedName>, Info>,
    private val supportCodeMap: Map<ResolvedName, Info>,
    /** external to { module to local } */
    private val imports: Map<Pair<ModuleName, ResolvedName>, ResolvedName>,
) {
    /** module to { local to external } */
    private val inverseImports: Map<Pair<ModuleName, ResolvedName>, ResolvedName> =
        buildMap(imports.size) {
            for ((keyPair, value) in imports.entries) {
                val (mod, key) = keyPair
                this@buildMap[mod to value] = key
            }
        }

    fun qNameMappings() =
        nameMap.mapValues { (_, info) -> info.qName }

    fun lookupDeclDescriptor(moduleName: ModuleName, name: ResolvedName): DescriptorChain? =
        nameMap[moduleName to name]?.decl
            ?: supportCodeMap[name]?.decl

    fun lookupUseDescriptors(moduleName: ModuleName, name: ResolvedName): List<DescriptorChain> =
        nameMap[moduleName to name]?.use
            ?: supportCodeMap[name]?.use
            ?: emptyList()

    /** Look up an external (imported) name and find the local name. */
    fun lookupLocalName(
        moduleName: ModuleName,
        externalName: ResolvedName,
    ): ResolvedName? = imports[moduleName to externalName]

    /** Look up a local name and find the external name. */
    fun lookupExternalName(
        moduleName: ModuleName,
        localName: ResolvedName,
    ): ResolvedName? = inverseImports[moduleName to localName]

    companion object {
        val empty = NameLookup(emptySet(), emptyMap(), emptyMap(), emptyMap())
    }
}

internal val resolvedNameComparator: Comparator<ResolvedName> =
    compareBy(
        {
            when (it) {
                is ExportedName -> it.origin
                is SourceName -> it.origin
                is Temporary -> it.origin
                is BuiltinName -> null
            }?.loc?.source()?.dirName()?.toString()
        },
        {
            when (it) {
                is ExportedName -> it.baseName.nameText
                is SourceName -> it.baseName.nameText
                is Temporary -> it.nameHint
                is BuiltinName -> it.builtinKey
            }
        },
        {
            when (it) {
                is ExportedName -> 0
                is SourceName -> it.uid
                is Temporary -> it.uid
                is BuiltinName -> 0
            }
        },
    )
