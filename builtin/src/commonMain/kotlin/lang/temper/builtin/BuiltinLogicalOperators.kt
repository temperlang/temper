package lang.temper.builtin

import lang.temper.log.Position
import lang.temper.value.Document
import lang.temper.value.LeafTree
import lang.temper.value.LogicalOperators
import lang.temper.value.ValueLeaf

object BuiltinLogicalOperators : LogicalOperators {
    override fun notFn(doc: Document, pos: Position) =
        ValueLeaf(doc, pos, BuiltinFuns.vNotFn)

    override fun andFn(doc: Document, pos: Position) =
        ValueLeaf(doc, pos, BuiltinFuns.vDesugarLogicalAndFn)

    override fun orFn(doc: Document, pos: Position): LeafTree =
        ValueLeaf(doc, pos, BuiltinFuns.vDesugarLogicalOrFn)
}
