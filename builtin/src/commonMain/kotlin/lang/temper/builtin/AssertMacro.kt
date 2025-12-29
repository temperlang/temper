package lang.temper.builtin

import lang.temper.common.mapFirst
import lang.temper.env.InterpMode
import lang.temper.format.SimplifyingTokenSink
import lang.temper.format.toStringViaTokenSink
import lang.temper.log.MessageTemplate
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.SourceName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.stage.Stage
import lang.temper.type.NominalType
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.MacroEnvironment
import lang.temper.value.NameLeaf
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Planting
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.RightNameLeaf
import lang.temper.value.TString
import lang.temper.value.TVoid
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.dotBuiltinName
import lang.temper.value.eqBuiltinName
import lang.temper.value.freeTree
import lang.temper.value.staticTypeContained
import lang.temper.value.toPseudoCode
import lang.temper.value.typeSymbol
import lang.temper.value.vInitSymbol
import lang.temper.value.vSsaSymbol

/**
 * <!-- snippet: builtin/assert -->
 * # `assert`
 * A macro for convenience in test cases. Calls assert on the current test
 * instance.
 *
 * ```temper inert
 * test("something important") {
 *   // Provide the given message if the assertion value is false.
 *   assert(1 + 1 == 2) { "the sky is falling" }
 * }
 * ```
 */
internal object AssertMacro : BuiltinMacro("assert", null) {
    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        if (interpMode != InterpMode.Partial) {
            macroEnv.failLog.fail(MessageTemplate.CannotInvokeMacroAsFunction, macroEnv.pos)
            return Fail
        }
        if (macroEnv.stage != Stage.Define) {
            return NotYet
        }
        if (macroEnv.args.size !in 1..2) {
            macroEnv.failLog.fail(MessageTemplate.ArityMismatch, macroEnv.pos, listOf(2))
            return Fail
        }
        val call = macroEnv.call!!
        val testName = findTestName(call) ?: run {
            // No matching function, so the assert macro isn't placed right.
            macroEnv.failLog.fail(MessageTemplate.InvalidBlockContent, call.pos, emptyList())
            return@invoke Fail
        }
        // Replace the callee with a dot call.
        call.replace(0..0) {
            Call {
                Rn(dotBuiltinName)
                Rn(testName)
                V(Symbol(AssertMacro.name))
            }
        }
        if (macroEnv.args.size == 1) {
            if (buildValueMessageMaybe(call)) {
                // Went extra fancy already.
                return NotYet
            }
            // Just put in a simple string for the expected condition.
            call.replace(call.size until call.size) {
                Fn {
                    V(Value("expected ${call.child(1).toSimplePseudoCode()}", TString))
                }
            }
        }
        return NotYet
    }
}

private fun buildValueMessageMaybe(call: Tree): Boolean {
    val condition = (call.child(1) as? CallTree) ?: return false
    val conditionCallee = (condition.child(0) as? RightNameLeaf) ?: return false
    val opName = (conditionCallee.content as? BuiltinName) ?: return false
    if (!(opName == eqBuiltinName && condition.size == EQ_SIZE)) {
        return false
    }
    // Capture actual text expression while it's still in original form.
    val originalActual = condition.child(1)
    val actualText = originalActual.toSimplePseudoCode()
    // For now, expect that the left side has the interesting name or expression, and the right side is a boring value.
    // We can use fancier heuristics in the future, if we want.
    call.incoming?.replace {
        var actualName: TemperName
        var expectedName: TemperName
        Block {
            // TODO If these are already names, we don't need to extract temporaries.
            actualName = extractTemporary("actual", originalActual)
            expectedName = extractTemporary("expected", condition.child(2))
            Call {
                Replant(freeTree(call.child(0)))
                Call {
                    Replant(freeTree(conditionCallee))
                    Rn(actualName)
                    Rn(expectedName)
                }
                Fn {
                    Call {
                        // Requires toString on compared values.
                        V(BuiltinFuns.vStrCatMacro)
                        V(Value("expected $actualText ${opName.builtinKey} (", TString))
                        Rn(expectedName)
                        V(Value(") not (", TString))
                        Rn(actualName)
                        V(Value(")", TString))
                    }
                }
            }
        }
    } ?: return false
    return true
}

private fun Planting.extractTemporary(nameHint: String, tree: Tree): TemperName {
    var result: TemperName? = null
    Decl {
        Ln { it.unusedTemporaryName(nameHint).also { name -> result = name } }
        V(vInitSymbol)
        Replant(freeTree(tree))
        V(vSsaSymbol)
        V(TVoid.value)
    }
    return result!!
}

val simplePseudoCodeDetail = PseudoCodeDetail.default.copy(resugarDotHelpers = true)
private fun Tree.toSimplePseudoCode() = toStringViaTokenSink { tokenSink ->
    // Pseudocode is an easy grab for now.
    // Meanwhile, seems we don't inline expression values until after current expansion.
    // TODO Would direct access to the source code be stabler than our rendering?
    toPseudoCode(SimplifyingTokenSink(tokenSink), detail = simplePseudoCodeDetail)
}

private const val EQ_SIZE = 3

/** Find some test variable, looking for now only at parameters of functions containing the call. */
private fun findTestName(call: Tree): TemperName? {
    var parent = call
    while (true) {
        parent = parent.incoming?.source ?: break
        if (parent is FunTree) {
            // Return a matching test parameter if we find one.
            val formals = parent.parts?.formals ?: continue
            return formals.mapFirst { nameIfTest(it) } ?: continue
        }
    }
    return null
}

/** Return the decl name if it's effectively named "test" or typed "Test". */
private fun nameIfTest(declTree: DeclTree): TemperName? {
    val name = declTree.parts?.name?.content ?: return null
    // We get either a SoureName or Temporary, depending on how explicit.
    val baseName = when (name) {
        is SourceName -> name.baseName.nameText
        is Temporary -> name.nameHint
        else -> null
    }
    // TODO Note that this won't resolve for inferred param type, because pre TypeStage.
    val good = when (baseName) {
        null -> false
        "test" -> true
        else -> {
            // Only handling cases seen so far.
            val baseTypeName = when (val type = declTree.parts?.metadataSymbolMap?.get(typeSymbol)?.target) {
                is NameLeaf -> when (val typeName = type.content) {
                    is SourceName -> typeName.baseName.nameText
                    else -> null
                }
                is ValueLeaf -> when (val typeValue = type.staticTypeContained) {
                    is NominalType -> when (val typeName = typeValue.definition.name) {
                        is ExportedName -> typeName.baseName.nameText
                        else -> null
                    }
                    else -> null
                }
                else -> null
            }
            baseTypeName == testTypeName.nameText
        }
    }
    return when (good) {
        true -> name
        else -> null
    }
}
