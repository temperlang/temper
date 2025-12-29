package lang.temper.frontend

import lang.temper.common.Either
import lang.temper.env.InterpMode
import lang.temper.frontend.disambiguate.formalizeArgs
import lang.temper.frontend.syntax.rewriteFun
import lang.temper.log.MessageTemplate
import lang.temper.stage.Stage
import lang.temper.type2.Signature2
import lang.temper.value.Fail
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.StaylessMacroValue
import lang.temper.value.letBuiltinName

/**
 * Handles `let` of functions.
 *
 * Unlike `let x`, function declarations like `let f() { ... }` parse as calls to this macro.
 *
 * <!-- snippet: builtin/let -->
 * # `let`
 * The word `let` is used to define names for both variables and functions.
 *
 * ## Defining variables
 *
 * ⎀ scoping/examples
 *
 * ## Defining functions
 * To define a function, use `let`, but with an argument list and body.
 *
 * ```temper
 * // Declare a function named `f`
 * let f(): String { "foo" }
 * // and call it
 * console.log(f()); //!outputs "foo"
 * ```
 *
 * The syntax for function values is similar, but uses [snippet/builtin/fn] instead of `let`.
 * A function value may have a name, so that it may recursively call itself, but that name is
 * not visible outside the function expression.
 *
 * ```temper
 * // A function "declaration"
 * let f(): String { "function-declaration" }
 * // A function expression's name does not affect uses of that name outside itself
 * fn f(): String { "function-expression" }
 * console.log(f()); //!outputs "function-declaration"
 * ```
 */
internal object MultiStageLetMacro : StaylessMacroValue, NamedBuiltinFun {
    override val name = letBuiltinName.builtinKey
    override val sigs: List<Signature2>? = null
    override val nameIsKeyword: Boolean = true

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult =
        if (interpMode == InterpMode.Partial) {
            rewriteFunctionLikeSyntacticElement(macroEnv, isDeclaration = true)
        } else {
            Fail
        }
}

internal fun rewriteFunctionLikeSyntacticElement(
    macroEnv: MacroEnvironment,
    isDeclaration: Boolean,
) = when (macroEnv.stage) {
    Stage.Lex,
    Stage.Parse,
    Stage.Import,
    -> NotYet
    Stage.DisAmbiguate -> formalizeArgs(macroEnv, isDeclaration = isDeclaration)
    Stage.SyntaxMacro -> {
        val call = macroEnv.call
        if (call != null) {
            when (
                val rewrite = rewriteFun(call, isDeclaration = isDeclaration)
            ) {
                is Either.Left -> {
                    macroEnv.replaceMacroCallWith(rewrite.item)
                    NotYet
                }
                is Either.Right -> {
                    macroEnv.replaceMacroCallWithErrorNode(rewrite.item)
                    Fail(rewrite.item)
                }
            }
        } else {
            Fail
        }
    }
    Stage.Define,
    Stage.Type,
    Stage.FunctionMacro,
    Stage.Export,
    Stage.Query,
    Stage.GenerateCode,
    Stage.Run,
    -> {
        macroEnv.replaceMacroCallWithErrorNode()
        macroEnv.fail(MessageTemplate.MalformedFunction)
    }
}
