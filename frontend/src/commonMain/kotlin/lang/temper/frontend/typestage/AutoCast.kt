package lang.temper.frontend.typestage

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.BuiltinFuns
import lang.temper.name.TemperName
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.ControlFlow
import lang.temper.value.DeclTree
import lang.temper.value.IsNullFn
import lang.temper.value.RightNameLeaf
import lang.temper.value.StructuredFlow
import lang.temper.value.TNull
import lang.temper.value.TType
import lang.temper.value.TVoid
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.functionContained
import lang.temper.value.vSsaSymbol
import lang.temper.value.valueContained
import lang.temper.value.varSymbol
import lang.temper.value.void

internal class AutoCast(val root: BlockTree) {
    fun apply() {
        TreeVisit.startingAt(root).forEachContinuing trees@{ tree ->
            processTree(tree)
        }.visitPreOrder()
    }

    /** Collect var decls only if we need them. */
    private val varDecls by lazy {
        buildSet {
            TreeVisit.startingAt(root).forEach trees@{ tree ->
                when (tree) {
                    is DeclTree -> {
                        if (tree.parts?.metadataSymbolMultimap?.containsKey(varSymbol) == true) {
                            add(tree.parts!!.name.content)
                        }
                        VisitCue.SkipOne
                    }

                    else -> VisitCue.Continue
                }
            }.visitPreOrder()
        }
    }

    private fun processTree(tree: Tree) {
        val block = (tree as? BlockTree) ?: return
        val blockFlow = (block.flow as? StructuredFlow) ?: return
        // Gather all cast groups.
        val castGroups = buildList {
            fun scanIfs(flow: ControlFlow) {
                if (flow is ControlFlow.If) {
                    block.dereference(flow.condition)?.target?.let { castsForCondition(flow, it) }?.let { add(it) }
                }
                for (clause in flow.clauses) {
                    scanIfs(clause)
                }
            }
            scanIfs(blockFlow.controlFlow)
        }
        // Apply casts, which can append to the block but shouldn't affect existing ref indexing.
        for (castGroup in castGroups) {
            for (cast in castGroup.thenCasts) {
                applyCast(cast, block, castGroup.ifStmt.thenClause)
            }
            for (cast in castGroup.elseCasts) {
                applyCast(cast, block, castGroup.ifStmt.elseClause)
            }
        }
    }

    private fun castsForCondition(ifStmt: ControlFlow.If, cond: Tree): CastGroup? {
        // Address only single cases for now. TODO Are cases like `&&` always converted to flow by this point?
        // Is there a non-var name?
        // If so, look for casting needs.
        var cond = cond
        var negated = false
        if (isNotCall(cond)) {
            negated = true
            cond = cond.child(1)
        }
        cond is CallTree || return null
        val name = cond.children.firstNotNullOfOrNull { it as? RightNameLeaf }?.content ?: return null
        name in varDecls && return null

        val thenCasts = mutableListOf<Cast>()
        val elseCasts = mutableListOf<Cast>()
        if (negated) {
            castsForNull(cond, name, thenCasts = elseCasts, elseCasts = thenCasts)
            castsForType(cond, name, thenCasts = elseCasts)
        } else {
            castsForNull(cond, name, thenCasts = thenCasts, elseCasts = elseCasts)
            castsForType(cond, name, thenCasts = thenCasts)
        }
        return CastGroup(ifStmt = ifStmt, thenCasts = thenCasts, elseCasts = elseCasts)
    }

    private fun castsForNull(
        cond: CallTree,
        name: TemperName,
        thenCasts: MutableList<Cast>,
        elseCasts: MutableList<Cast>,
    ) {
        // TODO At later stages could also check if the type is null.
        if (cond.size == CALLEE_AND_TWO_ARGS && cond.children.any { it.valueContained == TNull.value }) {
            // Is it an equality check with a relevant branch?
            // TODO Check for (... is T) calls also. And maybe isNull if we care about later stages here sometime.
            when (cond.childOrNull(0)?.functionContained) {
                // == null so go to else body
                BuiltinFuns.equalsFn -> elseCasts.add(Cast(name, null))
                // != null so go to if body
                BuiltinFuns.notEqualsFn -> thenCasts.add(Cast(name, null))
                else -> Unit
            }
        } else if (isIsNullCall(cond)) {
            elseCasts.add(Cast(name, null))
        }
    }

    private fun castsForType(cond: Tree, name: TemperName, thenCasts: MutableList<Cast>) {
        cond.size == CALLEE_AND_TWO_ARGS && cond.childOrNull(0)?.functionContained == BuiltinFuns.isFn || return
        val typeValue = cond.childOrNull(2)?.valueContained ?: return
        typeValue.typeTag == TType || return
        thenCasts.add(Cast(name, typeValue))
    }

    private fun applyCast(cast: Cast, block: BlockTree, branch: ControlFlow.StmtBlock) {
        // Bail if this is an empty block as represented by a single void value.
        if (branch.stmts.size == 1) {
            val stmt = block.dereference(branch.stmts.first().ref ?: return) ?: return
            stmt.valueContained == TVoid.value && return
        }
        val name = cast.name
        // Reference the type-narrowed non-var.
        val notNullName = block.document.nameMaker.unusedTemporaryName(name.displayName)
        val oldSize = block.size
        var anyChanges = false
        scanClauses(block, branch) { sub ->
            // TODO Reject any immediately inside a notNull call.
            if (sub is RightNameLeaf && sub.content == name) {
                anyChanges = true
                sub.incoming!!.replace { Rn(notNullName) }
            }
        }
        anyChanges || return
        // Insert notNull assignment now that we finished the rename, so this won't be affected.
        block.insert {
            // By this stage, we expect separate decl from init.
            Decl {
                Ln(notNullName)
                V(vSsaSymbol)
                V(void)
            }
            Call {
                V(BuiltinFuns.vSetLocalFn)
                Ln(notNullName)
                Call {
                    when (val type = cast.type) {
                        null -> {
                            V(BuiltinFuns.vNotNullFn)
                            Rn(name)
                        }
                        else -> {
                            V(BuiltinFuns.vAssertAsFn)
                            Rn(name)
                            V(type)
                        }
                    }
                }
            }
        }
        branch.withMutableStmtList { branchStmts ->
            // Build then insert refs to the new autocast statement nodes.
            // Alternatively, could insert one at a time in reverse order, which likely has pros and cons.
            val refs = (oldSize until block.size).map { ControlFlow.Stmt(BlockChildReference(it, block.pos)) }
            branchStmts.addAll(0, refs)
        }
    }
}

private class Cast(
    val name: TemperName,
    /** Where `null` type means a cast to not-null. */
    val type: Value<*>?,
)

private class CastGroup(
    val ifStmt: ControlFlow.If,
    val thenCasts: List<Cast>,
    val elseCasts: List<Cast>,
    // TODO postCasts: List<Cast>, // if one of the branches always jumps out
)

private fun scanClauses(block: BlockTree, flow: ControlFlow, action: (Tree) -> Unit) {
    fun scan(sub: ControlFlow) {
        sub.ref?.let { block.dereference(it) }?.let { edge ->
            TreeVisit.startingAt(edge.target).forEachContinuing { tree ->
                action(tree)
            }.visitPreOrder()
        }
        for (clause in sub.clauses) {
            scan(clause)
        }
    }
    scan(flow)
}

fun isNotCall(tree: Tree) =
    tree is CallTree && tree.size == 2 &&
        tree.child(0).functionContained == BuiltinFuns.notFn

fun isIsNullCall(tree: CallTree) =
    tree.size == 2 && tree.child(0).functionContained == IsNullFn

private const val CALLEE_AND_TWO_ARGS = 3
