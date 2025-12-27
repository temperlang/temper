package lang.temper.ast

import lang.temper.cst.ConcreteSyntaxTree
import lang.temper.cst.CstInner
import lang.temper.cst.CstLeaf

fun flatten(cst: ConcreteSyntaxTree): List<CstPart> {
    val cstParts = mutableListOf<CstPart>()
    flattenOnto(cst, cstParts)
    return cstParts.toList()
}

private fun flattenOnto(cst: ConcreteSyntaxTree, cstParts: MutableList<CstPart>) {
    when (cst) {
        is CstInner -> {
            cstParts.add(LeftParenthesis(cst.operator, cst.pos.leftEdge))
            cst.operands.forEach { flattenOnto(it, cstParts) }
            cstParts.add(RightParenthesis(cst.operator, cst.pos.rightEdge))
        }
        is CstLeaf -> cstParts.add(CstToken(cst.temperToken))
    }
}
