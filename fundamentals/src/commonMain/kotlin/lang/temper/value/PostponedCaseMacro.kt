package lang.temper.value

import lang.temper.env.InterpMode
import lang.temper.log.Position
import lang.temper.name.ParsedName
import lang.temper.name.Symbol
import lang.temper.stage.Stage

// TODO: This reserves syntactic space for `match` cases, but it is mostly
// not yet implemented.
// The goal here is to make sure we have enough information to do
// meta-programming later, and to let us write test cases that test that
// information makes it through existing passes without interference.

/**
 * A compiler fiction that preserves the token structure of a case until we
 * know the type to potentially use to translate cases to patterns.
 *
 * There are three kinds of match cases.  This macro preserves the second
 * kind for later processing by carving out a syntactic space that will
 * be implemented later when we've got better in-language processing
 * facilities.
 *
 * 1. `is` *Type* `->` cases which involve testing
 *    the type of the matched expression result.
 * 2. `case` *token soup* `->` cases which allow for custom, type-driven,
 *    compile-time pattern decomposition.
 * 3. cases that start with neither the keyword `is` nor the keyword
 *    `case` which specify expressions whose result is compared directly
 *    to the matched expression result.
 *
 * A postponed case pattern goes through the following steps:
 *
 * Source text:
 *
 *     when (x) {
 *       case word(1), case word(2) -> handler();
 *     }
 *
 * After Disambiguate expands `match`:
 *
 *     let t#0 = x;
 *     if (postponedCase(["word", "(", "1", ")"], t#0) ||
 *         postponedCase(["word", "(", "2", ")"], t#0)) {
 *       handler()
 *     }
 *
 * As can be seen, the actual tokens are preserved, and associated with the
 * temporary that holds the expression match result.
 *
 * After SyntaxMacro name resolution stores resolutions for word tokens, so that
 * pattern decomposing macros can convert word tokens to names.
 *
 *     let t#0 = x;
 *     if (postponedCase(["word", "(", "1", ")"], t#0, \word, word__0) ||
 *         postponedCase(["word", "(", "2", ")"], t#0, \word, word__0)) {
 *       handler__0()
 *     }
 *
 * After Typer figures out the type for t#0, a type-related macro
 * decomposes the cases based on custom syntax processing, erasing the
 * postponedCase call.
 *
 * The type-related macro consumes all the `case`s to produce:
 *
 * 1. a set of property name chains like [`a.b.c`, `a[1]`, `a.length`] across all cases
 * 2. a set of value lines for those properties that arrange simple values
 *    that do not need further decomposition like the value line of
 *    ints, floats, booleans, strings, or merged value lines of
 *    (singleton null and one of the others), or a value line of type tags
 * 3. for each case, a set of range tests for those properties in
 *    disjunction of conjunction form, like:
 *    (a.b.c >= 0 || a.length >= 2 && 1 <= a[1] && a[1] <= 1)
 * 4. dependencies between these representing facts like:
 *    `a[1]` is only safe to test after knowing `a.length >= 2`.
 *
 * That information is sufficient to use value-of-info to turn the combined bundle of
 * value relationships into a decision tree.
 *
 * TENTATIVE IMPLEMENTATION PLAN:
 *
 * - Implement std/compile-time/decision-trees to provide return types for
 *   pattern decomposing macros
 * - Add code to PostposedCaseMacro to, during FunctionMacrosStage, to
 *   use the static type of subject to look up a type-attached static
 *   pattern resolver.
 * - Implement a pass after those resolve to collect them and build the
 *   decision tree.
 *   This may be easier if Matches are represented in ControlFlow instead
 *   of always decomposing to nested if.
 */
object PostponedCaseMacro : NamedBuiltinFun {
    override val name: String = "postponedCase"

    override val sigs = null

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        val call = macroEnv.call
        if (macroEnv.stage == Stage.SyntaxMacro && call != null) {
            val positionsBySymbol = mutableMapOf<Symbol, Position>()
            val tokenList = macroEnv.args.valueTree(0)
            val tokenListEdge = tokenList.incoming
            if (tokenList is CallTree && tokenListEdge != null) {
                val callee = tokenList.childOrNull(0)
                if ((callee?.functionContained as? NamedBuiltinFun)?.name == "list") {
                    val tokens = (1 until tokenList.size).map {
                        val listElement = tokenList.child(it)
                        val value = listElement.valueContained
                        if (value != null && value.typeTag == TSymbol) {
                            val symbol = TSymbol.unpack(value)
                            if (symbol !in positionsBySymbol) {
                                positionsBySymbol[symbol] = listElement.pos
                            }
                        }
                        value
                    }
                    if (null !in tokens) {
                        tokenListEdge.replace {
                            V(tokenList.pos, Value(tokens.map { it!! }, TList))
                        }
                    }
                }
            }
            // Generate left names for symbol tokens so that the normal name resolution
            // stores resolution information.
            val endOfCall = call.pos.rightEdge
            val doc = call.document
            TList.unpackOrNull(tokenListEdge?.target?.valueContained)?.let { tokenListValue ->
                val symbolized = mutableSetOf<Symbol>()
                for (tokenValue in tokenListValue) {
                    val symbol = TSymbol.unpackOrNull(tokenValue)
                    if (symbol != null && symbol !in symbolized) {
                        symbolized.add(symbol)
                        val pos = positionsBySymbol[symbol] ?: endOfCall

                        call.add(ValueLeaf(doc, pos.leftEdge, tokenValue))
                        call.add(LeftNameLeaf(doc, pos, ParsedName(symbol.text)))
                    }
                }
            }
        }
        return NotYet
    }
}

val vPostponedCaseMacro = Value(PostponedCaseMacro)
