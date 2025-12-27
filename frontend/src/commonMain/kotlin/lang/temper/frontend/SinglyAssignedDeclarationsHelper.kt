package lang.temper.frontend

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.interp.removeDeclMetadata
import lang.temper.lexer.Operator
import lang.temper.lexer.TokenType
import lang.temper.name.ResolvedName
import lang.temper.name.TemperName
import lang.temper.value.BINARY_OP_CALL_ARG_COUNT
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.EscTree
import lang.temper.value.FunTree
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.RightNameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.functionContained
import lang.temper.value.initSymbol
import lang.temper.value.ssaSymbol
import lang.temper.value.vSsaSymbol
import lang.temper.value.valueContained
import lang.temper.value.void

/**
 * Sprinkle [ssaSymbol] around where there is a single assignment/initializer for a given
 * declaration and remove that where it is not the case.
 */
fun adjustDeclarationMetadataWithSinglyAssignedHints(root: BlockTree) {
    val helper = SinglyAssignedDeclarationsHelper()
    helper.scanForMentions(root)
    helper.adjustDeclarationMetadata()
}

private class SinglyAssignedDeclarationsHelper {
    private val mentionsByName = mutableMapOf<TemperName, Mentions>()
    private val malformedDecls = mutableListOf<DeclTree>()
    private val formalDecls = mutableSetOf<DeclTree>()

    fun scanForMentions(tree: Tree) {
        TreeVisit.startingAt(tree)
            .forEach { t ->
                when (t) {
                    is CallTree -> {
                        val nameAssigned = nameForAssignmentOrNull(t)
                        if (nameAssigned != null) {
                            mentionsByName.getOrPut(nameAssigned) { Mentions() }
                                .sets.add(t)
                        }
                    }
                    is DeclTree -> {
                        val dp = t.parts
                        if (dp != null) {
                            val mentions =
                                mentionsByName.getOrPut(dp.name.content) { Mentions() }
                            mentions.decls.add(t)
                            if (initSymbol in dp.metadataSymbolMap) {
                                mentions.sets.add(t)
                            }
                        } else {
                            malformedDecls.add(t)
                        }
                    }
                    is FunTree -> {
                        val fp = t.parts
                        if (fp != null) {
                            formalDecls.addAll(fp.formals)
                        } else {
                            // Conservatively assume that all decls in a malformed FunTree are
                            // formals and so are not singly-assigned since we've not done the work
                            // to identify singly called functions.
                            t.children.forEach {
                                if (it is DeclTree) {
                                    formalDecls.add(it)
                                }
                            }
                        }
                    }
                    is BlockTree, is EscTree,
                    is NameLeaf, is StayLeaf, is ValueLeaf,
                    -> Unit
                }
                VisitCue.Continue
            }
            .visitPreOrder()
    }

    fun adjustDeclarationMetadata() {
        for ((name, mentions) in mentionsByName) {
            if (mentions.decls.size == 1 && mentions.sets.size <= 1) {
                val decl = mentions.decls[0]
                if (decl in formalDecls || name !is ResolvedName) {
                    removeSsaMetadataFrom(decl)
                } else {
                    addSsaMetadataTo(decl)
                }
            } else {
                mentions.decls.forEach { removeSsaMetadataFrom(it) }
            }
        }
        malformedDecls.forEach { removeSsaMetadataFrom(it) }
    }
}

private class Mentions {
    val decls = mutableListOf<DeclTree>()
    val sets = mutableListOf<Tree>()
}

private fun nameForAssignmentOrNull(tree: CallTree): TemperName? {
    if (tree.size != BINARY_OP_CALL_ARG_COUNT) {
        return null
    }
    val leftOperand = tree.child(1)
    if (leftOperand !is NameLeaf) {
        return null
    }
    val nameText = when (val callee = tree.child(0)) {
        is RightNameLeaf -> callee.content.builtinKey
        is ValueLeaf, is NameLeaf, is StayLeaf, is BlockTree, is CallTree, is DeclTree,
        is EscTree, is FunTree,
        -> (callee.functionContained as? NamedBuiltinFun)?.name
    }
    if (
        nameText == null ||
        !Operator.isProbablyAssignmentOperator(nameText, TokenType.Punctuation)
    ) {
        return null
    }
    return leftOperand.content
}

private fun removeSsaMetadataFrom(t: DeclTree) =
    removeDeclMetadata(t) { keySymbol, _, _ -> keySymbol == ssaSymbol }

private fun addSsaMetadataTo(t: DeclTree) {
    var needsSsaMetadata = true
    removeDeclMetadata(t) { keySymbol, _, valueTree ->
        if (keySymbol == ssaSymbol) {
            if (valueTree?.valueContained != void) {
                true
            } else {
                needsSsaMetadata = false
                false
            }
        } else {
            false
        }
    }
    if (needsSsaMetadata) {
        t.replace(t.size until t.size) {
            V(t.pos.rightEdge, vSsaSymbol)
            V(t.pos.rightEdge, void)
        }
    }
}
