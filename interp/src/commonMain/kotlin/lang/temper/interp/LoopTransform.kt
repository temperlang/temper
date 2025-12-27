package lang.temper.interp

import lang.temper.common.LeftOrRight
import lang.temper.common.Log
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.value.BlockChildReference
import lang.temper.value.CallTree
import lang.temper.value.ControlFlow
import lang.temper.value.FunTree
import lang.temper.value.JumpLabel
import lang.temper.value.NameLeaf
import lang.temper.value.RightNameLeaf
import lang.temper.value.Tree
import lang.temper.value.condSymbol
import lang.temper.value.dotBuiltinName
import lang.temper.value.flowInitSymbol
import lang.temper.value.freeTree
import lang.temper.value.incrSymbol
import lang.temper.value.ofBuiltinName
import lang.temper.value.valueContained

/**
 * Converts loops to complex-flow blocks.
 *
 * This handles both `for` and `while` and for...of loops.
 * `while (condition) { body }` is just treated as `for (;condition;) { body }` so it
 * is convenient to handle both variants here.
 * Since the name `for` is used for two syntactically distinct looping constructs,
 * the disambiguation between them must happen by inspection of arguments here.
 *
 * <!-- snippet: builtin/for -->
 * # `for` loop
 * `for (initializer; condition; increment) { body }` is a [snippet/stmt/simple-for-loop].
 * `for (let one of many) { body }` is a [snippet/stmt/for-of-loop].
 *
 * ⎀ stmt/simple-for-loop
 * ⎀ stmt/for-of-loop
 *
 * <!-- snippet: stmt/simple-for-loop -->
 * ## simple `for` loops
 * First it executes the *initializer*, once only.
 *
 * Then it repeatedly executes the *condition* which must be a [snippet/builtin/Boolean] expression.
 * The first time the condition evaluates to [snippet/builtin/false], the loop exits.
 * After each time the condition evaluates to [snippet/builtin/true], the *increment* is evaluated
 * and control transfers back to the body.
 *
 * An unlabelled [snippet/builtin/break] in the body exits the loop.
 * An unlabelled [snippet/builtin/continue] in the body jumps back to the increment and then to
 * the condition.
 *
 * ```temper
 * for (var i = 0; i < 3; ++i) {
 *   console.log(i.toString());
 * }
 * //!outputs "0"
 * //!outputs "1"
 * //!outputs "2"
 * ```
 *
 * Unlike in some other languages, curly brackets (`{...}`) are **required** around the body.
 *
 * ```temper FAIL
 * for (var i = 0; i < 3; ++i)
 *   console.log(i.toString()) // Curly brackets missing
 * ```
 *
 * So a `for` loop is roughly equivalent to
 *
 * ```temper inert
 * { <-- // Block establishes scope for variables defined in initializer
 *   initializer;
 *   while (condition) {
 *     body;
 *     // <-- `continue` in body goes here
 *     increment;
 *   }
 * } // <-- `break` in body goes here
 * ```
 *
 * except that, as noted, [snippet/builtin/continue]s in body do not skip over the increment.
 *
 * The initializer may declare variables that are scoped to the loop.
 *
 * ```temper
 * let s = "declared-before-loop";
 * for (var s = "declared-in-loop-initializer"; true;) {
 *   console.log(s); //!outputs "declared-in-loop-initializer"
 *   break;
 * }
 * // The use of `s` here refers to the top-level `s`,not the loop's `s`;
 * // the loop variable is only visible within the loop.
 * console.log(s); //!outputs "declared-before-loop"
 * ```
 *
 * <!-- snippet: stmt/for-of-loop -->
 * # for...of loop
 *
 * *For...of* loops allow iterating over each value in some group of values.
 *
 * ```temper
 * for (let x of ["a", "b", "c"]) {
 *   console.log(x);
 * }
 * //!outputs "a"
 * //!outputs "b"
 * //!outputs "c"
 * ```
 *
 * The parts of a for...of loop are:
 *
 * - The *declaration*.  `let x` in the example above.
 * - The *source* of elements.  The list `["a", "b", "c"]` above.
 * - The *body*.  `{ console.log(x) }` above.
 *
 * A for...of loop is syntactic sugar for a call to the `forEach` method.
 * (for...of simply turns one construct into another).
 * The example above is equivalent to the `.forEach` call below.
 *
 * ```temper
 * ["a", "b", "c"].forEach { x =>
 *   console.log(x);
 * }
 * //!outputs "a"
 * //!outputs "b"
 * //!outputs "c"
 * ```
 *
 * In both cases, jumps like [snippet/builtin/break], [snippet/builtin/continue], and
 * [snippet/builtin/return] may appear in the body when the body is a lexical block
 * provided directly to `forEach` and the `forEach` implementation uses
 * [snippet/builtin/@inlineUnrealizedGoal] as [snippet/type/List/method/forEach] does.
 *
 * <!-- TODO(mikesamuel): make for..of snippets below that are inert valid. -->
 *
 * ```temper inert
 * for (let x of ["a", "b", "c", "d", "e"]) {
 *   if (x == "b") { continue }
 *   console.log(x);
 *   if (x == "d") { break }
 * }
 * //!outputs "a"
 * //!outputs "c"
 * //!outputs "d"
 * ```
 *
 * Unlike some other languages, a bare name is not allowed instead of a declaration
 * in a for...of loop.
 *
 * ```temper FAIL
 * var x;
 * for (x of ["a", "b", "c") {
 *   console.log(x)
 * }
 * ```
 *
 * <!-- snippet: builtin/while -->
 * # `while` loop
 * `while (condition) { body }` repeatedly executes the *condition* until it yields
 * [snippet/builtin/false], and after each [snippet/builtin/true] result of the condition,
 * executes the *body*.
 *
 * An unlabelled [snippet/builtin/break] in the body exits the loop.
 * An unlabelled [snippet/builtin/continue] in the body jumps back to the condition.
 *
 * ```temper
 * var i = 0;
 * while (i < 3) {
 *   console.log((i++).toString());
 * }
 * //!outputs "0"
 * //!outputs "1"
 * //!outputs "2"
 * ```
 *
 * Unlike in some other languages, curly brackets (`{...}`) are **required** around the body.
 *
 * ```temper FAIL
 * var i = 0;
 * while (i < 3)
 *   console.log((i++).toString()) // Curly brackets missing
 * ```
 */
internal object LoopTransform : ControlFlowTransform("for") {
    override val desugarsEarly: Boolean = true

    override fun complicate(macroCursor: MacroCursor): ControlFlowReplacement? {
        val stage = macroCursor.macroEnvironment.stage
        val first = macroCursor.peek()?.target
        return if (
            first?.isOfCall == true && isForCall(macroCursor) &&
            // Is it time to desugar?
            // We want to desugar when `.` is still waiting to be turned
            // into dot helpers.
            // (We could synthesize a method call helper here but that
            // would be complicated by a need to separately handle private
            // forEach methods).
            (stage >= Stage.Define || ofCallDeclMigrated(first as CallTree))
        ) {
            complicateForOfLoop(macroCursor)
        } else if (isEarly(macroCursor.macroEnvironment)) {
            null
        } else {
            complicateSimpleLoop(macroCursor)
        }
    }

    private fun complicateSimpleLoop(macroCursor: MacroCursor): ControlFlowReplacement? {
        var initializer: BlockChildReference? = null
        var condition: BlockChildReference? = null
        var increment: BlockChildReference? = null
        // First try extracting `for`-loop style
        while (true) {
            if (initializer == null && macroCursor.consumeSymbol(flowInitSymbol)) {
                initializer = macroCursor.referenceTo(macroCursor.nextTEdge() ?: return null)
            } else if (condition == null && macroCursor.consumeSymbol(condSymbol)) {
                condition = macroCursor.referenceTo(macroCursor.nextTEdge() ?: return null)
            } else if (increment == null && macroCursor.consumeSymbol(incrSymbol)) {
                increment = macroCursor.referenceTo(macroCursor.nextTEdge() ?: return null)
            } else {
                break
            }
        }
        // If that doesn't work, try `while`-loop style.
        if (
            initializer == null && condition == null && increment == null &&
            macroCursor.nRemaining >= 2
        ) {
            condition = macroCursor.referenceTo(macroCursor.nextTEdge()!!)
        }
        val body = macroCursor.rawBody() ?: return null
        if (!macroCursor.isEmpty()) {
            return null
        }
        val env = macroCursor.macroEnvironment
        val label = env.label
        if (label != null) {
            env.consumeLabel()
        }
        val loopPos = env.pos
        if (condition == null) {
            condition = macroCursor.referenceToBoolean(loopPos.leftEdge, true)
        }
        val incrementAsBlock = if (increment != null) {
            ControlFlow.StmtBlock.wrap(ControlFlow.Stmt(increment))
        } else {
            ControlFlow.StmtBlock(condition.pos.rightEdge, emptyList())
        }
        return ControlFlowSubflow(
            ControlFlow.StmtBlock(
                pos = loopPos,
                stmts = listOfNotNull(
                    initializer?.let { ControlFlow.Stmt(it) },
                    ControlFlow.Loop(
                        pos = loopPos,
                        label = label as JumpLabel?,
                        checkPosition = LeftOrRight.Left,
                        condition = condition,
                        increment = incrementAsBlock,
                        body = ControlFlow.StmtBlock.wrap(
                            ControlFlow.Stmt(macroCursor.referenceTo(body)),
                        ),
                    ),
                    // If a loop appears as the last statement in a module or function body,
                    // the MakeResultsExplicit pass will find the `void` as the result.
                    ControlFlow.Stmt(macroCursor.referenceToVoid(loopPos.rightEdge)),
                ),
            ),
        )
    }

    private fun complicateForOfLoop(macroCursor: MacroCursor): ControlFlowReplacement {
        val env = macroCursor.macroEnvironment

        val ofCall = macroCursor.nextTEdge()!!.target as CallTree
        val body = macroCursor.nextTEdge()?.target
        val unused = macroCursor.nextTEdge()
        val group = ofCall.child(2)

        val problem = when {
            body !is FunTree -> LogEntry(
                Log.Error,
                MessageTemplate.ExpectedBlock,
                (body?.pos ?: env.pos.rightEdge),
                emptyList(),
            )
            // Trailing content
            unused != null -> LogEntry(
                Log.Error,
                MessageTemplate.MalformedStatement,
                unused.target.pos,
                emptyList(),
            )
            !ofCallDeclMigrated(ofCall) -> LogEntry(
                Log.Error,
                MessageTemplate.MalformedStatement,
                (ofCall.childOrNull(1)?.pos ?: ofCall.pos.leftEdge),
                emptyList(),
            )
            else -> null
        }

        return if (problem == null) {
            check(body is FunTree)
            Desugaring {
                Call {
                    Call {
                        Rn(dotBuiltinName)
                        Replant(freeTree(group))
                        V(forEachSymbol)
                    }
                    Replant(freeTree(body))
                }
            }
        } else {
            TransformFailed(problem)
        }
    }

    private fun isForCall(macroCursor: MacroCursor) =
        (macroCursor.macroEnvironment.callee as? NameLeaf)?.content?.builtinKey == "for"
}

val Tree.isOfCall: Boolean get() {
    if (this is CallTree && size == INFIX_OF_OPERATOR_CALL_SIZE) {
        val callee = childOrNull(0)
        return callee is RightNameLeaf && ofBuiltinName == callee.content
    }
    return false
}

/** Whether ExtractFlowInitDeclarations has moved the declaration successfully onto body. */
private fun ofCallDeclMigrated(ofCall: CallTree) =
    emptyValue == ofCall.child(1).valueContained

private const val INFIX_OF_OPERATOR_CALL_SIZE = 3 // Callee, left, right

private val forEachSymbol = Symbol("forEach")
