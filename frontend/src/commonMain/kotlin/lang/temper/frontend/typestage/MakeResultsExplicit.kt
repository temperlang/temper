package lang.temper.frontend.typestage

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.MultilineOutput
import lang.temper.common.TextTable
import lang.temper.common.benchmarkIf
import lang.temper.frontend.Module
import lang.temper.frontend.allRootsOfAsBlocks
import lang.temper.frontend.implicits.ImplicitsModule
import lang.temper.frontend.prefixBlockWith
import lang.temper.frontend.prefixWith
import lang.temper.frontend.structureBlock
import lang.temper.name.ExportedName
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.Temporary
import lang.temper.type.WellKnownTypes
import lang.temper.type.isVoidLike
import lang.temper.type2.SuperTypeTree2
import lang.temper.type2.Type2
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.FnParts
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.NameLeaf
import lang.temper.value.Planting
import lang.temper.value.ReifiedType
import lang.temper.value.RightNameLeaf
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.freeTarget
import lang.temper.value.getTerminalExpressions
import lang.temper.value.isImplicits
import lang.temper.value.isPureVirtualBody
import lang.temper.value.reifiedTypeContained
import lang.temper.value.returnDeclSymbol
import lang.temper.value.toPseudoCode
import lang.temper.value.typeSymbol
import lang.temper.value.vReturnDeclSymbol
import lang.temper.value.vTypeSymbol
import lang.temper.value.void

private const val BENCHMARK = true
private const val DEBUG = false

/**
 * For each module/function body, if there is a terminal expression with no
 * semicolon, treat it as the result of the block by inserting an assignment
 * to the `return__123` name for that module/function.
 *
 * A terminal expression is one that appears just before an exit from the
 * control-flow graph.
 *
 * If there is no such thing, then we assign `void` unless the body is the
 * body of a *GeneratorFn* which `yield`s *ValueResult*s and implicitly returns
 * *DoneResult*s.
 */
internal class MakeResultsExplicit private constructor(
    val module: Module,
) {
    private val console = module.console

    private fun explicate(root: BlockTree): ResolvedName? {
        val doc = root.document

        val incoming = root.incoming

        val parent = incoming?.source
        val newDeclarations = mutableListOf<DeclTree>()
        val rootIsFunctionBody = parent is FunTree && root == parent.parts?.body
        var returnTypeTree: Tree? = null
        var needToDeclareOutputName = false

        val outputName = console.benchmarkIf(BENCHMARK, "findOutputName") {
            var outputName: NameLeaf? = null
            if (rootIsFunctionBody) {
                @Suppress("USELESS_IS_CHECK")
                require(parent is FunTree)
                // This is the root of a function.  Look at its output parameters.
                val fnParts = parent.parts
                val returnDecl = fnParts?.returnDecl
                if (returnDecl != null) {
                    val returnDeclParts = returnDecl.parts
                    returnTypeTree = returnDeclParts?.type?.target
                    outputName = returnDeclParts?.name
                }
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

        // Next, walk forwards from the entry, looking for terminal expressions, and
        // keeping track of which names are set before reaching it.
        var needToInitializeOutputNameToSingleton = false
        val isGeneratorFn = rootIsFunctionBody && parent.parts?.mayYield == true
        // In a generator function, the implicit end result is implicits.doneResult,
        // but we don't want to call the function that produces that while processing
        // the implicits module, so we might have to be a bit more careful about
        // terminal expressions there.
        val endWithDoneResult = isGeneratorFn && !doc.isImplicits
        val returnType = returnTypeTree?.reifiedTypeContained?.type2
        val isValidResultKnown = returnType?.isVoidLike == true || endWithDoneResult

        val unsetTerminalExpressions =
            console.benchmarkIf(BENCHMARK, "findingTerminals") {
                findUnsetTerminalExpressions(
                    root,
                    outputName.content as ResolvedName?,
                )
            }

        if (DEBUG) {
            console.logMulti(
                TextTable(
                    listOf(
                        listOf(
                            MultilineOutput.of("terminal"),
                        ),
                    ) +
                        unsetTerminalExpressions.unsetTerminalExpressionEdges.map { terminal ->
                            listOf(
                                MultilineOutput.of(terminal.target.toPseudoCode()),
                            )
                        },
                ),
            )
            console.log("setsOutputName=${unsetTerminalExpressions.setsName}")
            console.log("reachesExit=${unsetTerminalExpressions.reachesExit}")
            console.log("returnType=${returnTypeTree?.toPseudoCode()}")
        }

        if (
            !unsetTerminalExpressions.setsName &&
            unsetTerminalExpressions.unsetTerminalExpressionEdges.isEmpty() &&
            // If it always bubbles, don't throw a (possibly type-unsafe) assignment
            // to void in there.
            (unsetTerminalExpressions.reachesExit || isValidResultKnown)
        ) {
            needToInitializeOutputNameToSingleton = true
        } else {
            console.benchmarkIf(BENCHMARK, "addImplicitAssignments") {
                for (terminal in unsetTerminalExpressions.unsetTerminalExpressionEdges) {
                    val target = terminal.target
                    if (endWithDoneResult && target is ValueLeaf && target.content == void) {
                        terminal.replace {
                            makeDoneResult(parent.parts!!)
                        }
                    }
                    addImplicitAssignment(terminal, outputName)
                }
            }
        }

        if (needToInitializeOutputNameToSingleton) {
            prefixBlockWith(
                listOf(
                    doc.treeFarm.grow {
                        Call(root.pos.rightEdge) {
                            V(BuiltinFuns.vSetLocalFn)
                            Ln(outputName.content)
                            if (endWithDoneResult) {
                                makeDoneResult(parent.parts!!)
                            } else {
                                V(void)
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
                listOf(outputName) +
                    if (returnTypeTree != null) {
                        val nameForReturnType = shareReferenceTo(
                            returnTypeTree.incoming!!, // Safe bc returnType must be part of a FunTree
                            "type",
                            newDeclarations,
                        )

                        // Mark as an output parameter.
                        listOf(
                            ValueLeaf(doc, pos, vTypeSymbol),
                            RightNameLeaf(doc, returnTypeTree.pos, nameForReturnType),
                        )
                    } else {
                        emptyList()
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
        if (tree is BlockTree) {
            structureBlock(tree)
            val (terminalExpressions) = tree.getTerminalExpressions()
            for (terminalExpression in terminalExpressions) {
                val ref = terminalExpression.ref
                tree.dereference(ref)?.let {
                    if (!it.target.isPureVirtualBody()) {
                        addImplicitAssignment(it, outputName)
                    }
                }
            }
        } else {
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
    }

    companion object {
        operator fun invoke(
            module: Module,
            moduleRoot: BlockTree,
            needResultForModuleRoot: Boolean,
        ): ResolvedName? {
            val resultNamesByRoot = mutableMapOf<Tree, ResolvedName?>()
            for (root in allRootsOfAsBlocks(moduleRoot)) {
                if (needResultForModuleRoot || root != moduleRoot) {
                    resultNamesByRoot[root] = MakeResultsExplicit(module)
                        .explicate(root) // I do not think that word means what you think it means.
                }
            }
            return resultNamesByRoot[moduleRoot]
        }
    }
}

private val setLocalValue = BuiltinFuns.vSetLocalFn

private fun shareReferenceTo(
    edge: TEdge,
    nameHint: String,
    declarations: MutableList<DeclTree>,
): Temporary {
    val tree = edge.target
    val existingName = (tree as? NameLeaf)?.content
    if (existingName is Temporary) {
        return existingName
    }
    val doc = tree.document
    val pos = tree.pos
    val name = doc.nameMaker.unusedTemporaryName(nameHint = nameHint)
    // TODO: weave this block into the function body?
    val assignThenReference = doc.treeFarm.grow {
        Block(pos) { // { name = ...; name }
            Call(pos, setLocalValue) {
                Ln(pos, name)
                Replant(freeTarget(edge))
            }
            Rn(pos, name)
        }
    }
    declarations.add(DeclTree(doc, pos, listOf(LeftNameLeaf(doc, pos, name))))
    edge.replace(assignThenReference)
    return name
}

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
    val doneResultName = ExportedName(ImplicitsModule.module.namingContext, ParsedName("doneResult"))
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
