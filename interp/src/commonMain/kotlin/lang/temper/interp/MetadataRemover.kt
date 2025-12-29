package lang.temper.interp

import lang.temper.common.TriState
import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.name.Symbol
import lang.temper.type2.MacroSignature
import lang.temper.type2.MacroValueFormal
import lang.temper.type2.ValueFormalKind
import lang.temper.value.Fail
import lang.temper.value.InnerTree
import lang.temper.value.InnerTreeType
import lang.temper.value.MacroEnvironment
import lang.temper.value.MacroValue
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Tree
import lang.temper.value.TreeTypeStructureExpectation
import lang.temper.value.freeTree
import lang.temper.value.symbolContained

/**
 * Given a declaration or a function tree, adds metadata symbols.
 *
 * For declarations, the metadata symbols are added where [lang.temper.value.decomposeDecl]
 * expects them and similarly for function trees and [lang.temper.value.decomposeFun].
 */
internal class MetadataRemover(
    override val name: String,
    private val shouldRemoveMetadataPair: (Symbol, Tree) -> TriState,
) : NamedBuiltinFun, MacroValue {
    override val sigs: List<MacroSignature> get() = listOf(
        MacroSignature(
            returnType = null,
            requiredValueFormals = listOf(
                MacroValueFormal(
                    Symbol("decorated"),
                    reifiedType = TreeTypeStructureExpectation(
                        setOf(InnerTreeType.Decl, InnerTreeType.Fun),
                    ),
                    kind = ValueFormalKind.Required,
                ),
            ),
        ),
    )

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        val args = macroEnv.args
        if (args.size != 1) {
            macroEnv.explain(MessageTemplate.ArityMismatch, values = listOf(1))
            return Fail
        }
        val decorated = args.valueTree(0)
        // Keep track of whether we got a clear answer for all pairs.  If so, the macro call
        // considers its job done and removes itself from the tree.
        // There are a couple ways that we may lack a conclusive answer:
        // - A key tree has not yet folded to a ValueLeaf with a symbol value.
        // - The predicate returns TriState.OTHER presumably because it doesn't know whether it
        //   wants a pair with that value tree gone.
        var conclusiveAnswerForAll = true

        val valid = removeMetadata(decorated) { s, _, v ->
            if (s == null || v == null) {
                conclusiveAnswerForAll = false
                false
            } else {
                when (shouldRemoveMetadataPair(s, v)) {
                    TriState.FALSE -> false
                    TriState.TRUE -> true
                    TriState.OTHER -> {
                        conclusiveAnswerForAll = false
                        false
                    }
                }
            }
        }
        if (!valid) {
            return Fail
        }

        return if (conclusiveAnswerForAll) {
            macroEnv.replaceMacroCallWith {
                Replant(freeTree(decorated))
            }
            NotYet
        } else {
            Fail
        }
    }
}

typealias MetadataPairPredicate =
    (keySymbol: Symbol?, keyTree: Tree, valueTree: Tree?) -> Boolean

internal fun removeMetadata(
    t: InnerTree,
    metadataStartIndex: Int,
    metadataEndIndexExclusive: Int,
    shouldRemove: MetadataPairPredicate,
) {
    val allPairsComplete = ((metadataEndIndexExclusive - metadataStartIndex) and 1) == 0
    var scanIndex = metadataEndIndexExclusive + (if (allPairsComplete) 0 else 1) - 2
    while (scanIndex >= metadataStartIndex) { // Stop prior to reaching the name at position 1
        val keyIndex = scanIndex
        val valueIndex = scanIndex + 1
        scanIndex -= 2

        val keyTree = t.child(keyIndex)
        val valueTree = if (valueIndex < metadataEndIndexExclusive) {
            t.child(valueIndex)
        } else {
            null
        }
        val keySymbol = keyTree.symbolContained
        if (shouldRemove(keySymbol, keyTree, valueTree)) {
            val pairEndIndex = if (valueTree != null) valueIndex else keyIndex
            t.removeChildren(keyIndex..pairEndIndex)
        }
    }
}
