package lang.temper.value

fun Tree.isEmptyBlock(): Boolean =
    when (val flow = (this as? BlockTree)?.flow) {
        null -> false
        is LinearFlow -> this.size == 0
        is StructuredFlow -> flow.controlFlow.isEmptyBlock()
    }

fun ControlFlow.isEmptyBlock(): Boolean = when (this) {
    is ControlFlow.StmtBlock -> this.stmts.isEmpty()
    is ControlFlow.If,
    is ControlFlow.Jump,
    is ControlFlow.Labeled,
    is ControlFlow.Loop,
    is ControlFlow.OrElse,
    is ControlFlow.Stmt,
    -> false
}
