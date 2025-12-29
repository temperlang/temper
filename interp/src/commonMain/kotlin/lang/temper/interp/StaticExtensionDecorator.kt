package lang.temper.interp

import lang.temper.builtin.Types
import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.type2.MacroSignature
import lang.temper.type2.MacroValueFormal
import lang.temper.type2.ValueFormalKind
import lang.temper.value.DeclTree
import lang.temper.value.InnerTreeType
import lang.temper.value.MacroEnvironment
import lang.temper.value.MacroValue
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.ReifiedType
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.TType
import lang.temper.value.TreeTypeStructureExpectation
import lang.temper.value.Value
import lang.temper.value.freeTree
import lang.temper.value.makePairValue
import lang.temper.value.staticExtensionSymbol
import lang.temper.value.unpackPairValue
import lang.temper.value.valueContained

/**
 * A custom decorator that works in two phases to attach [staticExtensionSymbol].
 *
 * 1. During the syntax stage, marks its target declaration as a static extension function,
 *    by attaching metadata like
 *    *\\staticExtension, new Pair(null, "dotName")*
 * 2. After type expressions are inlined into ReifiedType values,
 *    adjusts that metadata to include the receiver type like
 *    *\\staticExtension, new Pair(ReceiverType, "dotName")*
 *
 * This eventually feeds into *DotHelper*s having complete *StaticExtensionResolution*s.
 */
internal object StaticExtensionDecorator : NamedBuiltinFun, MacroValue {
    override val name: String = "@staticExtension"

    override val sigs: List<MacroSignature> = listOf(
        MacroSignature(
            returnType = Types.void,
            requiredValueFormals = listOf(
                MacroValueFormal(
                    symbol = Symbol("decorated"),
                    reifiedType = TreeTypeStructureExpectation(
                        setOf(InnerTreeType.Decl, InnerTreeType.Fun),
                    ),
                    kind = ValueFormalKind.Required,
                ),
                MacroValueFormal(
                    symbol = Symbol("receiverType"),
                    reifiedType = Types.type,
                    kind = ValueFormalKind.Required,
                ),
                MacroValueFormal(
                    symbol = Symbol("dotName"),
                    reifiedType = Types.string,
                    kind = ValueFormalKind.Required,
                ),
            ),
        ),
    )

    private const val ARITY = 3

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        val args = macroEnv.args
        if (args.size != ARITY) {
            val fail = macroEnv.fail(MessageTemplate.ArityMismatch, values = listOf(ARITY))
            macroEnv.replaceMacroCallWithErrorNode(fail.info!!)
            return fail
        }
        if (macroEnv.stage < Stage.SyntaxMacro) { return NotYet }
        val declTree = args.valueTree(0)
        val parts = (declTree as? DeclTree)?.parts
        if (parts == null) {
            val fail = macroEnv.fail(MessageTemplate.MalformedDeclaration, declTree.pos)
            macroEnv.replaceMacroCallWithErrorNode(fail.info!!)
            return fail
        }

        val dotNamePos = args.pos(2)
        val dotName = args.evaluate(2, interpMode)
        val dotNameText = TString.unpackOrNull(dotName as? Value<*>)

        var indexForPairMetadata: Int? = null
        if (interpMode == InterpMode.Partial && macroEnv.stage == Stage.SyntaxMacro) {
            if (dotNameText == null) {
                val fail = macroEnv.fail(
                    MessageTemplate.ExpectedValueOfType,
                    pos = dotNamePos,
                    values = listOf(TString, dotName),
                )
                macroEnv.replaceMacroCallWithErrorNode(fail.info!!)
                return fail
            }
            declTree.insert(declTree.size) {
                V(dotNamePos.leftEdge, staticExtensionSymbol)
                V(dotNamePos, makePairValue(TNull.value, dotName as Value<*>))
            }
            indexForPairMetadata = declTree.size - 1
        }

        // If we have a resolved type, then we can get rid of this decorator.
        val receiverTypePos = args.pos(1)
        val receiverTypeResult = args.evaluate(1, interpMode)
        if (dotNameText != null && receiverTypeResult is Value<*>) {
            val receiverType = TType.unpackOrNull(receiverTypeResult)
            if (receiverType !is ReifiedType) {
                val fail = macroEnv.fail(
                    MessageTemplate.ExpectedValueOfType,
                    pos = receiverTypePos,
                    values = listOf(TType, receiverTypeResult),
                )
                macroEnv.replaceMacroCallWithErrorNode(fail.info!!)
                return fail
            }
            if (indexForPairMetadata == null) {
                val edges = declTree.parts!!.metadataSymbolMultimap[staticExtensionSymbol]
                    ?: emptyList()
                for (e in edges) {
                    val v = e.target.valueContained ?: continue
                    val pair = unpackPairValue(v)
                    if (TString.unpackOrNull(pair?.second) == dotNameText && TNull.value == pair?.first) {
                        indexForPairMetadata = e.edgeIndex
                        break
                    }
                }
            }
            if (indexForPairMetadata != null) {
                declTree.edge(indexForPairMetadata).replace { pos ->
                    V(pos, makePairValue(receiverTypeResult, dotName as Value<*>))
                }
            }
            macroEnv.replaceMacroCallWith {
                Replant(freeTree(declTree))
            }
        }
        return NotYet
    }
}
