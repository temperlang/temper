package lang.temper.frontend.typestage

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.Console
import lang.temper.common.MultilineOutput
import lang.temper.common.TextTable
import lang.temper.common.benchmarkIf
import lang.temper.frontend.allRootsOfAsBlocks
import lang.temper.frontend.core.CoreModule
import lang.temper.frontend.prefixBlockWith
import lang.temper.frontend.prefixWith
import lang.temper.name.ExportedName
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.type.WellKnownTypes
import lang.temper.type.isVoidLike
import lang.temper.type2.SuperTypeTree2
import lang.temper.type2.Type2
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.ControlFlow
import lang.temper.value.DeclTree
import lang.temper.value.FnParts
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.NameLeaf
import lang.temper.value.Planting
import lang.temper.value.ReifiedType
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.isCore
import lang.temper.value.isPureVirtualBody
import lang.temper.value.outTypeSymbol
import lang.temper.value.reifiedTypeContained
import lang.temper.value.returnDeclSymbol
import lang.temper.value.toPseudoCode
import lang.temper.value.typeSymbol
import lang.temper.value.vReturnDeclSymbol
import lang.temper.value.vTypeSymbol
import lang.temper.value.vVarSymbol
import lang.temper.value.valueContained
import lang.temper.value.varSymbol
import lang.temper.value.void

private const val BENCHMARK = true
private const val DEBUG = false

/**
 * For each module/function body, if there is a terminal expression with no
 * semicolon, treat it as the result of the block by inserting an assignment
 * to the `return__123` name for that module/function.
 *
 * This is useful for REPL contexts and testing so that a passage of Temper code
 * can be evaluated for a result.
 *
 * A terminal expression is classified based on analysis of [ControlFlow].
 * - `if`s can have a terminal expression in both their *then* and *else* branches.
 * - `orelse`s can too.
 * - Branch conditions are never terminal expressions: those boolean expressions in loops and `if`s.
 * - A statement block can only have a terminal expression as its last member.
 * - Loops, including `while`, `do...while` and `for` loops never contain a terminal
 *   expression.  Even though `do...while` loops always execute their body,
 *   their expression result is effectively `void`.
 *
 * The `return` macro inserts assignments to the `return` variable
 * (allocating one if necessary), so we never consider an expression terminal if
 * there is a pre-existing `return` variable and an assignment on all branches leading
 * to the expression.
 *
 * If there is no such thing, then we assign `void` unless the body is the
 * body of a *GeneratorFn* which `yield`s *ValueResult*s and implicitly returns
 * *DoneResult*s.
 */
internal class MakeResultsExplicit private constructor(
    private val console: Console,
) {
    private fun explicate(root: BlockTree): ResolvedName? {
        val doc = root.document

        val incoming = root.incoming

        val parent = incoming?.source
        val newDeclarations = mutableListOf<DeclTree>()
        val rootIsFunctionBody = parent is FunTree && root == parent.parts?.body
        var returnTypeTree: Tree? = null
        val returnDeclTree: DeclTree?
        if (rootIsFunctionBody) {
            val fnParts = parent.parts!!
            returnTypeTree = fnParts.metadataSymbolMap[outTypeSymbol]?.let { outTypeEdge ->
                val edgeIndex = outTypeEdge.edgeIndex
                outTypeEdge.target.also {
                    // Splice it out
                    outTypeEdge.source!!.replace(edgeIndex - 1..edgeIndex) {}
                }
            }
            returnDeclTree = fnParts.returnDecl
        } else {
            returnDeclTree = null
        }

        var needToDeclareOutputName = false
        val outputName = console.benchmarkIf(BENCHMARK, "findOutputName") {
            var outputName: NameLeaf? = null
            if (returnDeclTree != null) {
                val returnDeclParts = returnDeclTree.parts
                returnTypeTree = returnDeclParts?.type?.target
                outputName = returnDeclParts?.name
            }

            if (outputName == null) {
                // Modules and functions with no output parameters use the special name `return`.
                // TODO: This may change once we nail down module prologues.
                needToDeclareOutputName = true
                outputName = LeftNameLeaf(
                    doc,
                    root.pos.leftEdge,
                    // Using a SourceName means that CleanupTemporaryPass does not eliminate these.
                    doc.nameMaker.unusedSourceName(ParsedName("return")),
                )
            }

            outputName
        }

        val isGeneratorFn = rootIsFunctionBody && parent.parts?.mayYield == true
        // In a generator function, the implicit end result is core.doneResult,
        // but we don't want to call the function that produces that while processing
        // the core module, so we might have to be a bit more careful about
        // terminal expressions there.
        val endWithDoneResult = isGeneratorFn && !doc.isCore
        val returnType = returnTypeTree?.reifiedTypeContained?.type2
        // In some cases, we know exactly what the result is; there can be only one.
        val knownResultBasedOnType = when {
            endWithDoneResult -> KnownResult.Done
            returnType?.isVoidLike == true -> KnownResult.Void
            else -> null
        }

        // Next, walk forwards from the entry looking for terminal expressions and
        // keeping track of which names are set before reaching it.
        val terminalExpressions =
            console.benchmarkIf(BENCHMARK, "findingTerminals") {
                findUnsetTerminalExpressions(
                    root,
                    outputName.content as ResolvedName?,
                )
            }

        val needToInitializeOutputNameToSingleton = when {
            // Just assign the return value at the front of the function's body
            // and simplify any assignments found.
            knownResultBasedOnType?.isSingleton == true -> {
                terminalExpressions.existingAssignments.forEach { assignment ->
                    val edge = assignment.incoming!!
                    edge.replace {
                        Replant(freeTree(assignment.child(2)))
                    }
                }
                true
            }
            // Otherwise, if we don't have terminal expressions, and we don't have assignments,
            // just initialize it up front.
            knownResultBasedOnType != null && terminalExpressions.existingAssignments.isEmpty() &&
                terminalExpressions.unsetTerminalExpressionEdges.isEmpty() -> true
            // Normalize Void returns: if there are void assignments and all the assignments are void-like,
            // just preassign void.
            (
                terminalExpressions.existingAssignments.isNotEmpty() ||
                    terminalExpressions.unsetTerminalExpressionEdges.isNotEmpty()
                ) &&
                knownResultBasedOnType == null &&
                terminalExpressions.unsetTerminalExpressionEdges.all { it.target.valueContained == void } &&
                terminalExpressions.existingAssignments.all { it.child(2).valueContained == void }
            -> true
            // In the REPL, it can be the case that we want a result, don't know the desired type, and have
            // a loop at the end.  In that case, just assume that void is the result.
            knownResultBasedOnType == null &&
                !rootIsFunctionBody && returnTypeTree == null &&
                terminalExpressions.existingAssignments.isEmpty() &&
                terminalExpressions.unsetTerminalExpressionEdges.isEmpty() &&
                terminalExpressions.blocksMissingTerminators.isNotEmpty() &&
                terminalExpressions.blocksMissingTerminators.all { (_, cf) -> endsWithLoop(cf) } -> true
            else -> false
        }

        if (DEBUG) {
            fun <T> table(title: String, ls: List<T>, xform: (T) -> MultilineOutput) {
                if (ls.isEmpty()) { return }
                console.logMulti(
                    TextTable(
                        listOf(listOf(MultilineOutput.of(title))) +
                            ls.map { listOf(xform(it)) },
                    ),
                )
            }
            table("terminal", terminalExpressions.unsetTerminalExpressionEdges) {
                MultilineOutput.of(it.target.toPseudoCode())
            }
            table("blocksMissingTerminators", terminalExpressions.blocksMissingTerminators) { (t, cf) ->
                MultilineOutput.of(
                    if (cf != null) {
                        "$cf"
                    } else {
                        t.toPseudoCode()
                    },
                )
            }
            table("existing assignments", terminalExpressions.existingAssignments) {
                MultilineOutput.of(it.toPseudoCode())
            }
            console.log("knownResultBasedOnType=${knownResultBasedOnType}")
            console.log("needToInitializeOutputNameToSingleton=$needToInitializeOutputNameToSingleton")
            console.log("returnType=${returnTypeTree?.toPseudoCode()}")
        }

        if (!needToInitializeOutputNameToSingleton) {
            console.benchmarkIf(BENCHMARK, "addImplicitAssignments") {
                for (terminal in terminalExpressions.unsetTerminalExpressionEdges) {
                    val target = terminal.target
                    if (endWithDoneResult && target is ValueLeaf && target.content == void) {
                        terminal.replace {
                            makeDoneResult(parent.parts!!)
                        }
                    }
                    addImplicitAssignment(terminal, outputName)
                }
                if (terminalExpressions.terminalsNeedVar && returnDeclTree != null &&
                    returnDeclTree.parts?.metadataSymbolMap?.containsKey(varSymbol) == false
                ) {
                    returnDeclTree.insert(returnDeclTree.size) {
                        val pos = returnDeclTree.pos.leftEdge
                        V(pos, varSymbol)
                        V(pos, void)
                    }
                }
            }
        } else {
            prefixBlockWith(
                listOf(
                    doc.treeFarm.grow {
                        Call(root.pos.rightEdge) {
                            V(BuiltinFuns.vSetLocalFn)
                            Ln(outputName.content)
                            when (knownResultBasedOnType) {
                                null, KnownResult.Void -> V(void)
                                KnownResult.Done -> {
                                    check(parent is FunTree)
                                    makeDoneResult(parent.parts!!)
                                }
                            }
                        }
                    },
                ),
                root,
            )
        }

        if (needToDeclareOutputName) {
            val pos = outputName.pos
            val resultDecl = DeclTree(
                doc,
                pos,
                buildList {
                    add(outputName)
                    if (returnTypeTree != null) {
                        // Mark as an output parameter.
                        add(ValueLeaf(doc, pos, vTypeSymbol))
                        add(returnTypeTree)
                    }
                    if (terminalExpressions.terminalsNeedVar) {
                        add(ValueLeaf(doc, pos, vVarSymbol))
                        add(ValueLeaf(doc, pos, void))
                    }
                },
            )
            if (rootIsFunctionBody) {
                @Suppress("USELESS_IS_CHECK")
                require(parent is FunTree)
                val beforeBodyIndex = parent.size - 1
                parent.replace(beforeBodyIndex until beforeBodyIndex) {
                    V(pos, vReturnDeclSymbol)
                    Replant(resultDecl)
                }
            } else {
                prefixWith(listOf(resultDecl), root)
            }
        }

        prefixBlockWith(newDeclarations, root)

        return outputName.content as ResolvedName?
    }

    private fun addImplicitAssignment(
        edge: TEdge,
        outputName: NameLeaf,
    ) {
        val tree = edge.target
        if (tree is DeclTree || tree.isPureVirtualBody()) {
            // Just don't.
            // TODO: explain
        } else {
            edge.replace { p ->
                Call(p) {
                    V(p.leftEdge, setLocalValue)
                    Replant(outputName.copyLeft())
                    Replant(freeTarget(edge))
                }
            }
        }
    }

    companion object {
        fun makeAllResultsExplicit(
            console: Console,
            moduleRoot: BlockTree,
            needResultForModuleRoot: Boolean,
        ): ResolvedName? {
            val resultNamesByRoot = mutableMapOf<Tree, ResolvedName?>()
            for (root in allRootsOfAsBlocks(moduleRoot)) {
                if (needResultForModuleRoot || root != moduleRoot) {
                    resultNamesByRoot[root] = MakeResultsExplicit(console)
                        .explicate(root) // I do not think that word means what you think it means.
                }
            }
            return resultNamesByRoot[moduleRoot]
        }
    }
}

private val setLocalValue = BuiltinFuns.vSetLocalFn

private fun Planting.makeDoneResult(generatorFnParts: FnParts): UnpositionedTreeTemplate<CallTree> {
    var yielded: Type2? = null
    val returnDecl = generatorFnParts.metadataSymbolMap[returnDeclSymbol]?.target as? DeclTree
    if (returnDecl != null) { // Given `: GeneratorResult<T>`, pull out `T` as the yielded type
        val returnType = returnDecl.parts?.metadataSymbolMap[typeSymbol]?.target?.reifiedTypeContained?.type2
        if (returnType != null) {
            val superTypeTree = SuperTypeTree2.of(returnType)
            val generatorType = superTypeTree[WellKnownTypes.generatorResultTypeDefinition].firstOrNull()
            if (generatorType != null && generatorType.bindings.size == 1) {
                yielded = generatorType.bindings[0]
            }
        }
    }
    val doneResultName = ExportedName(CoreModule.module.namingContext, ParsedName("doneResult"))
    return Call {
        // doneResult<Yielded>()
        if (yielded != null) {
            Call(BuiltinFuns.angleFn) {
                Rn(doneResultName)
                V(Value(ReifiedType(yielded)))
            }
        } else {
            Rn(doneResultName)
        }
    }
}

private enum class KnownResult(val isSingleton: Boolean) {
    Void(true),
    Done(false), // A yielded result is also valid
}

private fun endsWithLoop(cf: ControlFlow.StmtBlock?): Boolean =
    cf?.stmts?.lastOrNull() is ControlFlow.Loop
