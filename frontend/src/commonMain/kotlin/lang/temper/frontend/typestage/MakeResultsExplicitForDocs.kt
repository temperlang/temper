package lang.temper.frontend.typestage

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.common.TriState
import lang.temper.common.ignore
import lang.temper.frontend.Module
import lang.temper.frontend.allRootsOfAsBlocks
import lang.temper.interp.docgenalts.AltReturnFn
import lang.temper.interp.docgenalts.DocGenAltFn
import lang.temper.interp.docgenalts.DocGenAltIfFn
import lang.temper.interp.docgenalts.DocGenAltImpliedResultFn
import lang.temper.interp.docgenalts.DocGenAltReturnFn
import lang.temper.interp.docgenalts.DocGenAltWhileFn
import lang.temper.interp.docgenalts.isPreserveCall
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.FunTree
import lang.temper.value.LinearFlow
import lang.temper.value.Tree
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.valueContained

internal class MakeResultsExplicitForDocs(module: Module) {
    val console = module.console

    private fun explicate(root: BlockTree) {
        ignore(console) // Comes in handy for debugging

        val incoming = root.incoming
        val parent = incoming?.source
        val returnType: Tree? = run {
            require(parent is FunTree)
            // This is the root of a function.  Look at its output parameters.
            val fnParts = parent.parts
            val returnDecl = fnParts?.returnDecl
            returnDecl?.parts?.type?.target
        }

        val isVoid = returnType?.valueContained == Types.vVoid
        if (isVoid) {
            return
        }

        val terminals = mutableListOf<Tree>()

        data class BlockWalkResult(val goesToNextInOrder: TriState)

        fun walk(block: BlockTree, hasFollower: Boolean): BlockWalkResult {
            check(block.flow is LinearFlow)
            val lastIndex = block.size - 1
            if (lastIndex < 0) {
                return BlockWalkResult(TriState.TRUE)
            }
            var goesToNextInOrder = TriState.TRUE
            for (i in 0..lastIndex) {
                val child = block.child(i)
                val childHasFollower = hasFollower || i < lastIndex

                var childContent = child
                while (isPreserveCall(childContent)) {
                    childContent = childContent.child(1)
                }
                if (childContent is BlockTree) {
                    when (walk(childContent, hasFollower = childHasFollower).goesToNextInOrder) {
                        TriState.TRUE -> Unit
                        TriState.FALSE -> return BlockWalkResult(goesToNextInOrder = TriState.FALSE)
                        TriState.OTHER -> goesToNextInOrder = TriState.OTHER
                    }
                }
                when (val fn = (childContent as? CallTree)?.childOrNull(0)?.functionContained) {
                    is DocGenAltFn -> when (fn) {
                        is DocGenAltIfFn -> {
                            // Look separately along every branch.
                            var branchIndex = 2 // Skip callee and condition
                            var hasElse = false
                            val lastIfChildIndex = childContent.size - 1
                            val branchResults = mutableSetOf<BlockWalkResult>()

                            while (true) {
                                var branch = childContent.child(branchIndex)
                                while (isPreserveCall(branch)) {
                                    branch = branch.child(1)
                                }
                                check(branch is BlockTree)
                                branchResults.add(walk(branch, hasFollower = childHasFollower))

                                if (branchIndex == lastIfChildIndex) {
                                    break
                                } else if (branchIndex + 2 <= lastIfChildIndex) {
                                    branchIndex += 2 // Skip over `else if(condition)`
                                } else {
                                    branchIndex += 1
                                    hasElse = true
                                }
                            }

                            if (!hasElse) {
                                branchResults.add(BlockWalkResult(goesToNextInOrder = TriState.TRUE))
                            }

                            val ifGoesToNextInOrder = if (branchResults.size != 1) {
                                TriState.OTHER
                            } else {
                                branchResults.first().goesToNextInOrder
                            }
                            when (ifGoesToNextInOrder) {
                                TriState.FALSE -> return BlockWalkResult(goesToNextInOrder = TriState.FALSE)
                                TriState.OTHER -> goesToNextInOrder = TriState.OTHER
                                TriState.TRUE -> Unit
                            }
                        }
                        is DocGenAltWhileFn -> {
                            // The result of a while loop is void, so do not proceed into its body.
                            // Optimistically assume the loop exits.
                            // We could check for a `true` predicate and an absence of reachable
                            // `break`s / `return`s or fail-outs.
                            return BlockWalkResult(goesToNextInOrder = TriState.TRUE)
                        }
                        is DocGenAltReturnFn -> {
                            return BlockWalkResult(goesToNextInOrder = TriState.FALSE)
                        }
                        is DocGenAltImpliedResultFn -> Unit
                    }
                    BuiltinFuns.pureVirtualFn -> {
                        // pureVirtual is a placeholder for function bodies that need to be
                        // overridden.
                        // Treat it as a panic.
                        // Do not wrap it in `return` so that the TmpL translator doesn't
                        // need to do work to identify body-less abstract method definitions.
                        return BlockWalkResult(goesToNextInOrder = TriState.FALSE)
                    }
                    else -> {
                        if (!childHasFollower) {
                            terminals.add(block.child(i))
                        }
                    }
                }
            }
            return BlockWalkResult(goesToNextInOrder = goesToNextInOrder)
        }

        walk(root, hasFollower = false)

        terminals.forEach { terminal ->
            val terminalEdge = terminal.incoming!!
            terminalEdge.replace {
                Call(terminal.pos, AltReturnFn) {
                    Replant(freeTree(terminal))
                }
            }
        }
    }

    companion object {
        operator fun invoke(
            module: Module,
            moduleRoot: BlockTree,
        ) {
            for (root in allRootsOfAsBlocks(moduleRoot)) {
                if (root !== moduleRoot) {
                    MakeResultsExplicitForDocs(module).explicate(root)
                }
            }
        }
    }
}
