package lang.temper.interp

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.type2.Signature2
import lang.temper.value.BlockTree
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.MacroEnvironment
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.StaylessMacroValue
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.fnParsedName
import lang.temper.value.freeTarget
import lang.temper.value.labelSymbol
import lang.temper.value.outTypeSymbol
import lang.temper.value.returnParsedName
import lang.temper.value.symbolContained
import lang.temper.value.typeSymbol
import lang.temper.value.vLabelSymbol
import lang.temper.value.vReturnDeclSymbol
import lang.temper.value.void

/**
 * Converts `return` statements to a `break` to the end of the function body, optionally preceded
 * by an assignment to the return-value-holding variable.
 *
 *      return foo();
 *
 * becomes
 *
 *      return_123 = foo();
 *      break fn_124
 *
 * Bare `return` without a value means `return void`.
 *
 * <!-- snippet: builtin/return -->
 * # `return`
 *
 * The `return` keyword ends execution of the innermost enclosing function.
 *
 * ```temper
 * let f(returnEarly: Boolean): Void {
 *   if (returnEarly) { return } // Exit f without performing the next statement
 *   console.log("Did not return early");
 * }
 * console.log("Return early");        //!outputs "Return early"
 * f(true);                      // outputs nothing
 * console.log("Do not return early"); //!outputs "Do not return early"
 * f(false);                     //!outputs "Did not return early"
 * ```
 *
 * `return` may be followed by an expression to specify the result value.
 *
 * ```temper
 * let answer(): Int { return 42; }
 * answer() == 42
 * ```
 *
 * ## Implied `return`s
 *
 * You can leave out a `return` if the type is `Void` or the
 * terminal statements in a function body are not in a loop and
 * should be the output.
 *
 * ```temper
 * let yesOrNo(b: Boolean): String {
 *   if (b) {
 *     "yes" // Implicitly returned
 *   } else {
 *     "no"  // me too
 *   }
 * }
 *
 * console.log(yesOrNo(true));  //!outputs "yes"
 * console.log(yesOrNo(false)); //!outputs "no"
 * ```
 *
 * *Terminal statements* are those statements that may be executed
 * just before control leaves the function body.
 */
internal object ReturnDesugarMacro : StaylessMacroValue, NamedBuiltinFun {
    override val name: String = returnParsedName.nameText
    override val sigs: List<Signature2>? get() = null

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        if (macroEnv.stage != Stage.SyntaxMacro) {
            return NotYet
        }
        val args = macroEnv.args
        val nArgs = args.size
        if (nArgs >= 2) {
            val fail = macroEnv.fail(MessageTemplate.ArityMismatch, values = listOf(1))
            macroEnv.replaceMacroCallWithErrorNode(fail.info!!)
            return fail
        }
        val results = if (nArgs == 1 && args.key(0) == null) {
            args.valueTree(0).incoming!!
        } else {
            null
        }
        // Find a function declared via `fn() { ... }` or `let f() { ... }`
        // so that the return goes to a predictable place, not to the end of
        // a function that's part of a desugared control flow statement.
        val explicitFunction: FunTree? = run {
            var candidate = macroEnv.callee
            while (true) {
                val containingScope = macroEnv.innermostContainingScope(candidate)
                if (containingScope is FunTree) {
                    val parts = containingScope.parts
                    if (parts?.returnedFrom == true) {
                        return@run containingScope
                    }
                }
                if (containingScope == candidate) {
                    break
                }
                candidate = containingScope
            }
            null
        }

        // Figure out the name of the variable used to store the output
        val outputName = if (explicitFunction != null) {
            val fnParts = explicitFunction.parts
            when (val retDecl = fnParts?.returnDecl) {
                null -> null
                else -> retDecl.parts?.name?.content
            }
        } else {
            null
        } ?: run {
            // Allocate an output variable if there is none defined
            val newName = macroEnv.nameMaker.unusedSourceName(returnParsedName)
            // Add a declaration to the tree as metadata
            val declarationEdge = if (explicitFunction != null) {
                val parts = explicitFunction.parts
                val outTypeEdge = parts?.metadataSymbolMap?.get(outTypeSymbol)
                val beforeBodyIndex = explicitFunction.size - 1
                val replaceRange = outTypeEdge?.edgeIndex
                    // Replace the return type with the return decl
                    ?.let {
                        (it - 1)..it
                    }
                    ?: (beforeBodyIndex until beforeBodyIndex)
                val calleePos = macroEnv.callee.pos.rightEdge
                explicitFunction.replace(replaceRange) {
                    V(calleePos, vReturnDeclSymbol)
                    Decl(calleePos, newName) {
                        if (outTypeEdge != null) {
                            val outType = freeTarget(outTypeEdge)
                            V(outType.pos.leftEdge, typeSymbol)
                            Replant(outType)
                        }
                    }
                }
                explicitFunction.edge(replaceRange.first + 1) // past \returnDecl
            } else {
                null
            }
            // Declare it so that interpretation following this macro call
            // have a coherent environment.
            if (declarationEdge != null) {
                macroEnv.evaluateEdge(declarationEdge, interpMode)
            }
            newName
        }

        val pos = macroEnv.pos
        if (explicitFunction == null) {
            val problem = LogEntry(
                Log.Error,
                MessageTemplate.ReturnOutsideFn,
                pos,
                listOf(),
            )
            if (macroEnv.stage > Stage.SyntaxMacro) {
                macroEnv.replaceMacroCallWithErrorNode(problem)
            }
            return Fail(problem)
        }

        // We need a label to break to.
        val rootLabelName = rootLabelFor(macroEnv, explicitFunction)
            ?: defineRootLabelFor(explicitFunction)
            ?: run {
                macroEnv.logSink.log(
                    Log.Error,
                    MessageTemplate.MalformedFunction,
                    pos,
                    listOf(),
                )
                return Fail
            }

        val breakCall = macroEnv.treeFarm.grow {
            Call(pos, vBreakTransform) {
                V(vLabelSymbol)
                Rn(pos, rootLabelName)
            }
        }
        val replacement = macroEnv.treeFarm.grow(pos) {
            Block {
                Call(pos, BuiltinFuns.vSetLocalFn) {
                    Ln(pos, outputName)
                    when (results) {
                        null -> V(pos, void)
                        else -> Replant(freeTarget(results))
                    }
                }
                Replant(breakCall)
            }
        }

        macroEnv.replaceMacroCallWith(replacement)
        return NotYet
    }
}

private fun rootLabelFor(
    env: MacroEnvironment,
    fn: Tree?,
): TemperName? {
    val root = if (fn != null && fn.size != 0) {
        fn.child(fn.size - 1)
    } else {
        var node = env.callee
        while (true) {
            val outer = env.innermostContainingScope(node)
            if (outer == node) {
                break
            }
            node = outer
        }
        node
    }
    if (root is BlockTree && root.size >= 2) {
        val c0 = root.child(0)
        val c1 = root.child(1)
        if (c0.symbolContained == labelSymbol && c1 is NameLeaf) {
            return c1.content
        }
    }
    return null
}

private fun defineRootLabelFor(
    fnTree: FunTree,
): TemperName? {
    val parts = fnTree.parts ?: return null
    val doc = fnTree.document
    val bodyEdge = parts.body.incoming!!
    val rootLabel = doc.nameMaker.unusedTemporaryName(fnParsedName.nameText)
    val body = bodyEdge.target
    bodyEdge.replace {
        Block(body.pos) {
            V(labelSymbol)
            Ln(rootLabel)
        }
    }
    val newBody = bodyEdge.target as BlockTree
    newBody.add(body)
    return rootLabel
}

private val vBreakTransform = Value(BreakTransform)
