package lang.temper.be

import lang.temper.ast.TreeVisit
import lang.temper.common.RSuccess
import lang.temper.frontend.Module
import lang.temper.library.LibraryConfiguration
import lang.temper.name.BackendId
import lang.temper.name.QName
import lang.temper.name.ResolvedName
import lang.temper.value.DeclTree
import lang.temper.value.TString
import lang.temper.value.qNameSymbol
import lang.temper.value.valueContained

internal fun <BE : Backend<BE>> storeQNameMapping(
    libraryConfiguration: LibraryConfiguration,
    modules: List<Module>,
    dependenciesBuilder: Dependencies.Builder<BE>,
    backendId: BackendId,
) {
    val mapping = mutableMapOf<ResolvedName, QName>()
    for (module in modules) {
        val ast = module.generatedCode ?: continue
        TreeVisit.startingAt(ast)
            .forEachContinuing { t ->
                if (t is DeclTree) {
                    val name = t.parts?.name?.content as ResolvedName?
                    val qName = t.parts?.metadataSymbolMap?.get(qNameSymbol)?.target?.valueContained(TString)
                        ?.let {
                            QName.fromString(it)
                        }
                    if (name != null && qName is RSuccess) {
                        mapping[name] = qName.result
                    }
                    if (qName !is RSuccess?) {
                        error(qName)
                    }
                }
            }
            .visitPreOrder()
    }
    dependenciesBuilder.addMetadata(
        libraryName = libraryConfiguration.libraryName,
        key = QNameMapping.key<BE>(backendId),
        value = mapping.toMap(),
    )
}

class QNameMapping<BE : Backend<BE>> private constructor(
    override val backendId: BackendId,
) : MetadataKey<BE, Map<ResolvedName, QName>>() {
    companion object {
        fun <BE : Backend<BE>> key(backendId: BackendId) =
            QNameMapping<BE>(backendId)
    }

    override fun equals(other: Any?): Boolean =
        other is QNameMapping<*> && this.backendId == other.backendId

    override fun hashCode(): Int = 0x3c47337e xor backendId.hashCode()

    override fun toString(): String = "QNameMapping($backendId)"
}
