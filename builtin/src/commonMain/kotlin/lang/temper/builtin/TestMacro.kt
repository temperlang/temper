package lang.temper.builtin

import lang.temper.common.C_SPACE
import lang.temper.common.decodeUtf16Iter
import lang.temper.env.InterpMode
import lang.temper.lexer.IdParts
import lang.temper.log.MessageTemplate
import lang.temper.name.ParsedName
import lang.temper.name.identifiers.IdentStyle
import lang.temper.stage.Stage
import lang.temper.value.CallTree
import lang.temper.value.DependencyCategory
import lang.temper.value.DependencyCategoryConfigurable
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.InnerTree
import lang.temper.value.MacroEnvironment
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.TString
import lang.temper.value.TVoid
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.complexArgSymbol
import lang.temper.value.curliesBuiltinName
import lang.temper.value.freeTree
import lang.temper.value.outTypeSymbol
import lang.temper.value.symbolContained
import lang.temper.value.throwsBuiltinName
import lang.temper.value.typeSymbol
import lang.temper.value.vComplexArgSymbol
import lang.temper.value.vInitSymbol
import lang.temper.value.vOutTypeSymbol
import lang.temper.value.vSsaSymbol
import lang.temper.value.vTestSymbol
import lang.temper.value.vTypeSymbol
import lang.temper.value.vWordSymbol

/**
 * <!-- snippet: builtin/test -->
 * # `test`
 * Define a test case. Any module that defines tests is considered to be a
 * test module rather than a production module.
 *
 * ```temper inert
 * test("something important") {
 *   // Provide the given message if the assertion value is false.
 *   assert(1 + 1 == 2) { "the sky is falling" }
 * }
 * ```
 */
internal object TestMacro : BuiltinMacro("test", null) {
    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        if (interpMode != InterpMode.Partial) {
            macroEnv.failLog.fail(MessageTemplate.CannotInvokeMacroAsFunction, macroEnv.pos)
            return Fail
        }
        // Handle auto imports and initial setup in import stage, then sneak in the full
        // function definition at define after top-level reorder for nicer string eval.
        val stage = macroEnv.stage
        if (!(stage == Stage.Import || stage == Stage.Define)) {
            return NotYet
        }
        if (macroEnv.args.size != 2) {
            when (val parent = macroEnv.call!!.incoming?.source) {
                is CallTree -> when (val content = parent.children.first().content) {
                    is ParsedName -> if (content.nameText == "@") {
                        // Distinguish `@test` from plain `test` at import stage.
                        return NotYet
                    }
                    else -> {}
                }
                else -> {}
            }
            macroEnv.failLog.fail(MessageTemplate.ArityMismatch, macroEnv.pos, listOf(2))
            return Fail
        }
        // Get function.
        val funTreeMaybe = macroEnv.args.valueTree(1)
        val funTree = (funTreeMaybe as? FunTree) ?: run {
            val foundType: Any = when (funTreeMaybe) {
                is ValueLeaf -> funTreeMaybe.content.typeTag
                else -> funTreeMaybe.treeType
            }
            macroEnv.failLog.fail(MessageTemplate.ExpectedFunctionType, funTreeMaybe.pos, listOf(foundType))
            return@invoke Fail
        }
        // Work in stages.
        if (stage == Stage.Import) {
            // We need to add any imports at import stage.
            configureTesting(macroEnv.call!!)
            elaborateSignature(funTree)
            return NotYet
        }
        // Evaluate name, including any expressions we can work out at this time.
        val namePos = macroEnv.args.pos(0)
        val nameValue = (macroEnv.args.evaluate(0, InterpMode.Full) as? Value<*>) ?: run {
            macroEnv.failLog.fail(MessageTemplate.UnableToEvaluate, namePos, emptyList())
            return@invoke Fail
        }
        val nameMaybe = TString.unpackOrNull(nameValue) ?: run {
            macroEnv.failLog.fail(MessageTemplate.ExpectedValueOfType, namePos, listOf(TString, nameValue.typeTag))
            return@invoke Fail
        }
        val name = nameMaybe.ifEmpty {
            // TODO Check against duplicates somewhere else?
            // This error message isn't quite right, but it's close enough for now.
            macroEnv.failLog.fail(MessageTemplate.IsNotAName, namePos, emptyList())
            return@invoke Fail
        }
        val funName = ParsedName(humanToSafishId(name))
        // Insert additional metadata into the fun.
        val beforeBlock = funTree.size - 1
        funTree.insert(beforeBlock) {
            V(vWordSymbol)
            V(Value(funName.toSymbol()))
        }
        // Declare it.
        macroEnv.call!!.incoming!!.replace {
            Decl {
                Ln { it.unusedSourceName(funName) }
                V(vInitSymbol)
                Replant(freeTree(funTree))
                V(vSsaSymbol)
                V(TVoid.value)
                V(vTestSymbol)
                V(Value(name, TString))
            }
        }
        return NotYet
    }
}

private fun configureTesting(callTree: CallTree) {
    val config = callTree.document.context as? DependencyCategoryConfigurable
    if (config != null && config.dependencyCategory == DependencyCategory.Production) {
        // Flip this from prod to test.
        config.dependencyCategory = DependencyCategory.Test
        // And append an import of Test.
        // Find the root, which is expected to be the immediate parent, but eh.
        var parent: InnerTree = callTree
        while (true) {
            parent = parent.incoming?.source ?: break
        }
        // And put it at the bottom to avoid early resolution vs the assert macro.
        // Also at the bottom so it still gets evaluated in this interpreter pass.
        parent.replace(parent.size until parent.size) {
            Decl {
                Call {
                    Rn(curliesBuiltinName)
                    Ln(ParsedName("Test"))
                }
                V(vInitSymbol)
                Call {
                    Rn(ParsedName("import"))
                    V(Value("std/testing", TString))
                }
            }
        }
    }
}

private fun elaborateSignature(funTree: FunTree) {
    // Add signature `(test: Test): Void throws Bubble` or adjust missing info toward that.
    // This allows things like just `test =>` to provide a locally usable name.
    when (val arg = funTree.children.firstOrNull { it.childOrNull(0)?.symbolContained == complexArgSymbol }) {
        null -> funTree.replace(IntRange(0, -1)) {
            // No param at all, so insert one.
            Block {
                V(vComplexArgSymbol)
                Rn { it.unusedTemporaryName("test") }
                V(vTypeSymbol)
                Rn(testTypeName)
            }
        }
        else -> if (arg.children.none { it.symbolContained == typeSymbol }) {
            // We have a param already but no type, so add it.
            (arg as InnerTree).replace(arg.size until arg.size) {
                V(vTypeSymbol)
                Rn(testTypeName)
            }
        }
    }
    if (funTree.children.none { it.symbolContained == outTypeSymbol }) {
        // No out type, so add it, and we're guaranteed to have at least one param by now.
        val index = funTree.children.indexOfLast { it.childOrNull(0)?.symbolContained == complexArgSymbol } + 1
        funTree.replace(index until index) {
            V(vOutTypeSymbol)
            Call {
                // Return type `Void throws Bubble`
                Rn(throwsBuiltinName)
                V(Types.vVoid)
                V(Types.vBubble)
            }
        }
    }
}

// TODO Where should this utility be?

/** Convert arbitrary strings to camelCase ids that work in Temper and maybe other languages. */
fun humanToSafishId(name: String): String {
    val filtered = buildString {
        for (code in decodeUtf16Iter(name)) {
            val mapped = when (code) {
                in IdParts.Continue -> code
                else -> C_SPACE
            }
            appendCodePoint(mapped)
        }
    }.trim()
    val prefixed = when {
        filtered.isEmpty() -> "id"
        filtered[0].code in IdParts.Start -> filtered
        else -> "id $filtered"
    }
    return IdentStyle.Human.convertTo(IdentStyle.Camel, prefixed)
}

val testTypeName = ParsedName("Test")
