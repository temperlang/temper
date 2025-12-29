package lang.temper.be

import lang.temper.be.tmpl.TmpL
import lang.temper.name.BackendId
import lang.temper.name.ResolvedName
import lang.temper.type2.Descriptor

class DescriptorsForDeclarations(
    val nameToDescriptor: Map<ResolvedName, Descriptor>,
) {
    data class Key<BACKEND : Backend<BACKEND>>(
        override val backendId: BackendId,
    ) : MetadataKey<BACKEND, DescriptorsForDeclarations>() {
        constructor(f: Backend.Factory<BACKEND>) : this(f.backendId)
    }
}

/**
 * Store frontend types for declarations.
 * This should be obviated as we move towards having declaration structure records.
 */
fun <BACKEND : Backend<BACKEND>> BACKEND.storeDescriptorsForDeclarations(
    tmpl: TmpL.Tree,
    factory: Backend.Factory<BACKEND>,
) {
    val descriptorsForDeclarations = DescriptorsForDeclarations(
        buildMap {
            fun walk(t: TmpL.Tree) {
                if (t is TmpL.NameDeclaration) {
                    val descriptor = t.descriptor
                    if (descriptor != null) {
                        val name = t.name.name
                        this[name] = descriptor
                    }
                }
                for (c in t.children) {
                    walk(c)
                }
            }
            walk(tmpl)
        },
    )
    this.dependenciesBuilder.addMetadata(
        libraryName,
        DescriptorsForDeclarations.Key(factory),
        descriptorsForDeclarations,
    )
}
