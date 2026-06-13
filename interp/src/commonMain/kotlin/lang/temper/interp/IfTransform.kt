package lang.temper.interp

import lang.temper.builtin.BuiltinFuns
import lang.temper.env.InterpMode
import lang.temper.log.spanningPosition
import lang.temper.name.TemperName
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeShape
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.CallTree
import lang.temper.value.ControlFlow
import lang.temper.value.Fail
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.ReifiedType
import lang.temper.value.Result
import lang.temper.value.SpecialFunction
import lang.temper.value.TEdge
import lang.temper.value.Value
import lang.temper.value.elseIfSymbol
import lang.temper.value.elseSymbol
import lang.temper.value.nameContained
import lang.temper.value.valueContained
import lang.temper.value.void

/**
 * <!-- snippet: builtin/if -->
 * # `if`
 * `if` allows branching on a [snippet/builtin/Boolean] predicate.
 *
 * ```temper
 * if (true) {
 *   console.log("Runs");
 * }
 * if (false) {
 *   console.log("Does not run");
 * }
 * //!outputs "Runs"
 * ```
 *
 * An `if` may include an `else` block which is run when the predicate is [snippet/builtin/false].
 *
 * ```temper
 * if (false) {
 *   console.log("Does not run")
 * } else {
 *   console.log("Runs")
 * }
 * if (true) {
 *   console.log("Runs")
 * } else {
 *   console.log("Does not run")
 * }
 * //!outputs ["Runs", "Runs"]
 * ```
 *
 * An `else` can change to another `if` statement.
 *
 * ```temper
 * let twoPlusTwo = 2 + 2;
 * if (twoPlusTwo == 3) {
 *   console.log("Does not run")
 * } else if (twoPlusTwo == 4) {
 *   console.log("Runs")
 * } else {
 *   console.log("Does not run")
 * }
 * //!outputs "Runs"
 * ```
 *
 * Temper is an expression language, so `if` may be used outside an expression context.
 *
 * ```temper
 * let x = (if (true) { 42 } else { 0 });
 * x == 42
 * ```
 *
 * In some syntactically similar languages, you can skip the brackets (`{`...`}`) around
 * `if` and `else` bodies.
 * You can't do that in Temper; always put `{`...`}` around the bodies.
 *
 * ```temper FAIL
 * if (true) console.log("Runs"); else console.log("Does not run");
 * ```
 *
 * !!! note
 *     The reason for this is that in Temper, control-flow operators like `if` are not
 *     special syntactic forms.
 *     They're macros that take blocks as arguments and phrases like `else` and `else if`
 *     are named parameters to that macro which also must be passed blocks.
 *
 * Unlike C, conditions must be [snippet/type/Boolean] or a sub-type.
 *
 * ```temper FAIL
 * if (0) { console.log("truthy") } else { console.log("falsey") }
 * ```
 */
internal object IfTransform : ControlFlowTransform("if") {
    override fun complicate(macroCursor: MacroCursor): ControlFlowSubflow? {
        val env = macroCursor.macroEnvironment
        val doc = env.document
        val condition = macroCursor.nextTEdge() ?: return null
        val thenClause = macroCursor.rawBody() ?: return null
        val branches = mutableListOf<Pair<TEdge?, TEdge>>(condition to thenClause)
        var followChain: ((cursor: MacroCursor) -> PartialResult)? = null

        // `if ... else if ... else ...`
        // desugar to nested calls.
        // Create some macros so that we can recursively invoke the interpreter to extract content
        // from those recursive calls.
        val elseIfHandler = object : SpecialFunction, NamedBuiltinFun {
            override val name: String = "elseIfHandler"
            override val sigs: List<Signature2>? get() = null
            override fun invoke(
                macroEnv: MacroEnvironment,
                interpMode: InterpMode,
            ): PartialResult {
                require(macroEnv.document == doc)
                val subCursor = MacroCursor(macroEnv)
                val subCondition = subCursor.nextTEdge() ?: return Fail
                val subClause = subCursor.rawBody() ?: return Fail
                // Adopt the edges so that they do not become inoperable when the sub-macro
                // invocation completes.
                branches.add(subCondition to subClause)
                return followChain!!(subCursor)
            }
        }

        val elseHandler = object : SpecialFunction, NamedBuiltinFun {
            override val name: String = "elseHandler"
            override val sigs: List<Signature2>? get() = null
            override fun invoke(
                macroEnv: MacroEnvironment,
                interpMode: InterpMode,
            ): Result {
                val subCursor = MacroCursor(macroEnv)

                val subClause = subCursor.rawBody()
                    ?: return Fail
                branches.add(null to subClause)
                return if (macroCursor.isEmpty()) {
                    void // Signals success.  Not the final result for the `if` macro.
                } else {
                    Fail
                }
            }
        }

        val elseIfHandlerValue = Value(elseIfHandler)
        val elseHandlerValue = Value(elseHandler)

        @Suppress("AssignedValueIsNeverRead") // It's read below.
        followChain = { subCursor ->
            val handler = when {
                subCursor.consumeSymbol(elseIfSymbol) -> elseIfHandlerValue
                subCursor.consumeSymbol(elseSymbol) -> elseHandlerValue
                else -> null
            }
            val result = when (handler) {
                null -> void
                else -> {
                    // Use goingOutOfStyle to get the interpreter to construct a function value.
                    val fn: PartialResult = subCursor.macroEnvironment.goingOutOfStyle {
                        subCursor.evaluate(InterpMode.Full)
                    }
                    when (fn) {
                        is Value<*> ->
                            env.apply(fn, ActualValues.from(handler), InterpMode.Full)
                        else -> Fail
                    }
                }
            }
            if (subCursor.isEmpty()) {
                result
            } else {
                Fail
            }
        }

        return when (followChain(macroCursor)) {
            is Fail, NotYet -> null
            is Value<*> -> {
                val wholePos = macroCursor.macroEnvironment.pos
                // Atomize in lexical order so that child order in control flow mirrors lexical.
                val branchesAtomized = branches.map { (cond, body) ->
                    cond?.let { macroCursor.referenceTo(it) } to
                        ControlFlow.Stmt(macroCursor.referenceTo(body))
                }
                // Build branching subsystems from `else` backwards to `if`.
                val hasFinalElse = branches.last().first == null
                var branchIndex = branches.size
                var controlFlow: ControlFlow? =
                    if (hasFinalElse) {
                        null
                    } else {
                        when {
                            // If someone has checked all subtypes for some supertype, panic instead of void.
                            anySealedTypesExhaustive(branches) -> {
                                val pos = macroCursor.macroEnvironment.pos
                                val panicCall = macroCursor.macroEnvironment.document.treeFarm.grow(pos) {
                                    Block {
                                        Call { V(BuiltinFuns.vPanic) }
                                    }
                                }.edge(0)
                                macroCursor.referenceTo(panicCall)
                            }
                            // Otherwise, having a `void` here makes sure that static checks like UseBeforeInit
                            // get a diagnostic position for error logging.
                            else -> macroCursor.referenceToVoid(macroCursor.macroEnvironment.pos.rightEdge)
                        }.let { ControlFlow.Stmt(it) }
                    }
                while (branchIndex != 0) {
                    branchIndex -= 1
                    val (cond, body) = branchesAtomized[branchIndex]
                    val pos = if (branchIndex == 0) {
                        wholePos
                    } else {
                        listOfNotNull(cond, body).spanningPosition(wholePos)
                    }
                    controlFlow = if (cond != null) {
                        ControlFlow.If(
                            pos = pos,
                            condition = cond,
                            thenClause = ControlFlow.StmtBlock.wrap(body),
                            elseClause = if (controlFlow != null) {
                                ControlFlow.StmtBlock.wrap(controlFlow)
                            } else {
                                ControlFlow.StmtBlock(body.pos.rightEdge, emptyList())
                            },
                        )
                    } else {
                        body
                    }
                }
                controlFlow?.let { ControlFlowSubflow(it) }
            }
        }
    }
}

/**
 * Check for simple sealed exhaustiveness. This requires that a single name be
 * checked against each sealed subtype of some sealed supertype. If so, default
 * to panic instead of void, which allows for type inference without an else.
 *
 * Rely on checks elsewhere for valid downcasts. And if the above holds and we
 * could downcast, the check would be exhaustive in any case.
 *
 * TODO Fancy flowtyping check for exhaustiveness.
 */
private fun anySealedTypesExhaustive(branches: MutableList<Pair<TEdge?, TEdge>>): Boolean {
    val allFoundSealedSubs = mutableMapOf<TypeShape, MutableSet<TypeDefinition>>()
    var checkedName: TemperName? = null
    branches@ for (branch in branches) {
        val condition = branch.first?.target as? CallTree ?: continue@branches
        condition.childOrNull(0)?.valueContained?.stateVector == BuiltinFuns.isFn || continue@branches
        val nextCheckedName = condition.childOrNull(1)?.nameContained ?: continue@branches
        when (checkedName) {
            null -> checkedName = nextCheckedName
            else if checkedName != nextCheckedName -> break@branches
            else -> {}
        }
        val subtype = condition.childOrNull(2)?.valueContained?.stateVector as? ReifiedType ?: continue@branches
        val subdef = subtype.type2.definition
        supertypes@ for (supertype in subdef.superTypes) {
            val supershape = supertype.definition as? TypeShape
            val sealedSubs = supershape?.sealedSubTypes ?: continue@supertypes
            if (subdef in sealedSubs) {
                val foundSealedSubs = allFoundSealedSubs.getOrPut(supershape) { mutableSetOf() }
                foundSealedSubs.add(subdef)
                if (foundSealedSubs.size == sealedSubs.size) {
                    return true
                }
            }
        }
    }
    return false
}
