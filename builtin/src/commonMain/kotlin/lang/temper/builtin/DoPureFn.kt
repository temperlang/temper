package lang.temper.builtin

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.stage.Stage
import lang.temper.type.MkType
import lang.temper.type2.Signature2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.ControlFlow
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.LinearFlow
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.PartialResult
import lang.temper.value.SpecialFunction
import lang.temper.value.StructuredFlow
import lang.temper.value.TFunction
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.freeTree
import lang.temper.value.isAssignment
import lang.temper.value.unpackOrFail
import lang.temper.value.valueContained
import lang.temper.value.void

/**
 * <!-- snippet: builtin/doPure -->
 * # doPure
 *
 * `doPure { x }` is the same as `x` except that the result will be available to
 * macro code early.  `x` must be pure code: it can't mutate any environment bindings
 * or objects it does not create and may not print or cause other side effects.
 */
object DoPureFn : SpecialFunction, NamedBuiltinFun {
    override val sigs = run {
        val (tf, t) = makeTypeFormal("doPure", "T")
        val noneToT = hackMapOldStyleToNew(
            MkType.fn(listOf(), listOf(), null, MkType.nominal(tf)),
        )
        listOf(
            Signature2(
                returnType2 = t,
                hasThisFormal = false,
                requiredInputTypes = listOf(noneToT),
                typeFormals = listOf(tf),
            ),
        )
    }

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        val args = macroEnv.args
        if (args.size != 1) {
            val error = LogEntry(
                Log.Error,
                MessageTemplate.ArityMismatch,
                macroEnv.pos,
                listOf(1),
            )
            macroEnv.replaceMacroCallWithErrorNode(error)
            return Fail(error)
        }
        if (interpMode == InterpMode.Partial) {
            // Always visit and expand macros
            macroEnv.evaluateTree(args.valueTree(0), interpMode)
        }
        val fn =
            TFunction.unpackOrFail(args, 0, macroEnv, interpMode = InterpMode.Full) {
                return@invoke it
            }
        val result = macroEnv.dispatchCallTo(
            macroEnv.document.treeFarm.grow(macroEnv.pos) {
                Call(fn) {}
            },
            Value(fn),
            listOf(),
            InterpMode.Full,
        )

        if (macroEnv.stage === Stage.GenerateCode) {
            // Erase this call so we don't have to translate it.
            val argTree = args.valueTree(0)
            macroEnv.replaceMacroCallWith {
                val parts = (argTree as? FunTree)?.parts
                if (parts != null && parts.formals.isEmpty()) {
                    val returnDecl = parts.returnDecl
                    val returnName = returnDecl?.parts?.name?.content
                    if (returnName != null) {
                        val body = parts.body
                        val singleStmt: Tree? = singleStmtIgnoringVoids(body)
                        if (singleStmt != null && singleStmt is CallTree && isAssignment(singleStmt) &&
                            (singleStmt.child(1) as? LeftNameLeaf)?.content == returnName
                        ) {
                            val expr = singleStmt.child(2)
                            Replant(freeTree(expr))
                            return@replaceMacroCallWith
                        }

                        Block(argTree.pos) {
                            Replant(freeTree(returnDecl))
                            Replant(freeTree(body))
                            Rn(argTree.pos.rightEdge, returnName)
                        }
                        return@replaceMacroCallWith
                    }
                }

                Call(macroEnv.pos) {
                    Replant(freeTree(argTree))
                }
            }
        }

        return result
    }

    override val name: String = "doPure"
}

private fun singleStmtIgnoringVoids(t: Tree): Tree? {
    return if (t is BlockTree) {
        when (val flow = t.flow) {
            LinearFlow -> if (t.size == t.parts.startIndex + 1) {
                var found: Tree? = null
                for (i in t.parts.startIndex until t.size) {
                    val child = t.child(i)
                    if (child.valueContained == void) { continue }
                    if (found != null) { return null }
                    found = child
                }
                return found?.let { singleStmtIgnoringVoids(it) }
            }
            is StructuredFlow -> {
                fun walk(cf: ControlFlow): BlockChildReference? {
                    when (cf) {
                        is ControlFlow.If,
                        is ControlFlow.Loop,
                        is ControlFlow.Jump,
                        is ControlFlow.OrElse,
                        -> return null
                        is ControlFlow.Stmt -> return cf.ref
                        is ControlFlow.StmtBlock -> {
                            var found: ControlFlow? = null
                            for (s in cf.stmts) {
                                if (s is ControlFlow.Stmt && t.dereference(s.ref)?.target?.valueContained == void) {
                                    continue
                                }
                                if (found != null) { return null }
                                found = s
                            }
                            return found?.let { walk(it) }
                        }
                        is ControlFlow.Labeled -> return walk(cf.stmts)
                    }
                }
                val ref = walk(flow.controlFlow)
                if (ref != null) {
                    return t.dereference(ref)?.target?.let { singleStmtIgnoringVoids(it) }
                }
            }
        }
        null
    } else {
        t
    }
}
