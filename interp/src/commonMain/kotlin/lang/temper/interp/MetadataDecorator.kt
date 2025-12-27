package lang.temper.interp

import lang.temper.builtin.Types
import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.name.Symbol
import lang.temper.type2.MacroSignature
import lang.temper.type2.MacroValueFormal
import lang.temper.type2.ValueFormalKind
import lang.temper.value.BaseReifiedType
import lang.temper.value.DeclTree
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.InnerTree
import lang.temper.value.InnerTreeType
import lang.temper.value.MacroActuals
import lang.temper.value.MacroEnvironment
import lang.temper.value.MacroValue
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.TProblem
import lang.temper.value.Tree
import lang.temper.value.TreeTypeStructureExpectation
import lang.temper.value.Value
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.void
import kotlin.math.max

/**
 * Given a declaration or a function tree, adds metadata symbols.
 *
 * For declarations, the metadata symbols are added where [lang.temper.value.decomposeDecl]
 * expects them and similarly for function trees and [lang.temper.value.decomposeFun].
 */
class MetadataDecorator(
    private val symbolKey: Symbol,
    override val name: String = "@${symbolKey.text}",
    argumentTypes: List<BaseReifiedType> = emptyList(),
    private val findDecoratorInsertions: (MacroActuals, Symbol) -> List<Pair<Tree, Int>> =
        ::findDefaultDecoratorInsertions,
    private val valuer: (MacroActuals) -> PartialResult,
) : NamedBuiltinFun, MacroValue {
    override val sigs: List<MacroSignature> = listOf(
        MacroSignature(
            returnType = Types.void,
            requiredValueFormals = run {
                val valueFormalList = mutableListOf(
                    MacroValueFormal(
                        symbol = Symbol("decorated"),
                        reifiedType = TreeTypeStructureExpectation(
                            setOf(InnerTreeType.Decl, InnerTreeType.Fun),
                        ),
                        ValueFormalKind.Required,
                    ),
                )
                argumentTypes.mapTo(valueFormalList) {
                    MacroValueFormal(
                        symbol = null,
                        reifiedType = it,
                        ValueFormalKind.Required,
                    )
                }
                valueFormalList.toList()
            },
        ),
    )
    private val keyValue = Value(symbolKey)

    private val arity = argumentTypes.size + 1 // Preceding arguments plus the decorated

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        val args = macroEnv.args
        if (args.size != arity) {
            val fail = macroEnv.fail(MessageTemplate.ArityMismatch, values = listOf(arity))
            macroEnv.replaceMacroCallWithErrorNode(fail.info!!)
            return fail
        }
        val insertions = findDecoratorInsertions(args, symbolKey)
        for (insertion in insertions) {
            val (decorated, insertionPoint) = insertion
            if (insertionPoint in 0..decorated.size) {
                val calleePos = macroEnv.callee.pos
                when (val valuerResult = valuer(args)) {
                    NotYet -> return NotYet
                    is Fail -> {
                        val logEntry = valuerResult.info
                        if (logEntry != null) {
                            macroEnv.replaceMacroCallWithErrorNode(logEntry)
                        } else {
                            macroEnv.replaceMacroCallWithErrorNode()
                        }
                        return valuerResult
                    }
                    is Value<*> -> {
                        (decorated as InnerTree).replace(insertionPoint until insertionPoint) {
                            V(calleePos, keyValue)
                            when (val error = TProblem.unpackOrNull(valuerResult)) {
                                null -> V(calleePos, valuerResult)
                                else -> {
                                    val call = macroEnv.call
                                    // Store an error node in the metadata
                                    Replant(
                                        errorNodeFor(
                                            if (call != null) {
                                                freeTree(call)
                                            } else {
                                                macroEnv.document.treeFarm.grow {
                                                    V(macroEnv.pos, void)
                                                }
                                            },
                                            error,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                return Fail // Maybe in a later stage
            }
        }
        // Insertions finished successfully.
        macroEnv.replaceMacroCallWith {
            Replant(freeTarget(args.valueTree(0).incoming!!))
        }
        return NotYet
    }
}

fun findDefaultDecoratorInsertions(
    args: MacroActuals,

    @Suppress("UNUSED_PARAMETER") // see findDecoratorInsertions contract above
    symbolKey: Symbol,
): List<Pair<Tree, Int>> {
    val decorated = args.valueTree(0)
    return listOf(Pair(decorated, findDefaultDecoratorInsertionPoint(decorated)))
}

fun findDefaultDecoratorInsertionPoint(decorated: Tree) = when (decorated) {
    is DeclTree -> max(1, decorated.size) // After the name
    is FunTree -> decorated.size - 1 // Before the body
    else -> -1
}

fun noDupeInsertions(args: MacroActuals, symbolKey: Symbol): List<Pair<Tree, Int>> {
    val decorated = args.valueTree(0)
    val metadata = when (decorated) {
        is DeclTree -> decorated.parts?.metadataSymbolMap
        is FunTree -> decorated.parts?.metadataSymbolMap
        else -> null
    }
    val extant = metadata?.get(symbolKey)
    return if (extant != null) {
        listOf(decorated to extant.edgeIndex)
    } else {
        findDefaultDecoratorInsertions(args, symbolKey)
    }
}
