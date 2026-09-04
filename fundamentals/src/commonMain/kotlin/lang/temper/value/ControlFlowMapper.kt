package lang.temper.value

fun mapControlFlowPlanting(
    sourceBlock: BlockTree,
    cf: ControlFlow,
    target: BlockPlanting,
    mapLabel: (JumpLabel) -> JumpLabel,
    mapBlockChild: Planting.(BlockChildReference, TEdge?) -> UnpositionedTreeTemplate<*>,
) {
    when (cf) {
        is ControlFlow.If -> target.If(
            pos = cf.pos,
            cond = { applyBlockChildFn(sourceBlock, mapBlockChild, this, cf.condition) },
            thn = {
                mapControlFlowPlanting(sourceBlock, cf.thenClause, this, mapLabel, mapBlockChild)
            },
            els = {
                mapControlFlowPlanting(sourceBlock, cf.elseClause, this, mapLabel, mapBlockChild)
            },
        )
        is ControlFlow.Loop -> target.While(
            pos = cf.pos,
            cond = { applyBlockChildFn(sourceBlock, mapBlockChild, this, cf.condition) },
            label = cf.label?.let { mapLabel(it) },
            body = { mapControlFlowPlanting(sourceBlock, cf.body, this, mapLabel, mapBlockChild) },
            testAt = cf.checkPosition,
            increment = { mapControlFlowPlanting(sourceBlock, cf.increment, this, mapLabel, mapBlockChild) },
        )
        is ControlFlow.Break -> target.Break(
            pos = cf.pos,
            label = when (val specifier = cf.target) {
                DefaultJumpSpecifier -> null
                is NamedJumpSpecifier -> mapLabel(specifier.label)
                is UnresolvedJumpSpecifier -> TODO()
            },
        )
        is ControlFlow.Continue -> target.Continue(
            pos = cf.pos,
            label = when (val specifier = cf.target) {
                DefaultJumpSpecifier -> null
                is NamedJumpSpecifier -> mapLabel(specifier.label)
                is UnresolvedJumpSpecifier -> TODO()
            },
        )
        is ControlFlow.Labeled -> target.Do(
            pos = cf.pos,
            label = mapLabel(cf.breakLabel),
            continueLabel = cf.continueLabel?.let { mapLabel(it) },
        ) {
            mapControlFlowPlanting(sourceBlock, cf.stmts, this, mapLabel, mapBlockChild)
        }
        is ControlFlow.OrElse -> target.OrElse(
            pos = cf.pos,
            label = mapLabel(cf.orClause.breakLabel),
            or = {
                mapControlFlowPlanting(sourceBlock, cf.orClause, this, mapLabel, mapBlockChild)
            },
            els = {
                mapControlFlowPlanting(sourceBlock, cf.elseClause, this, mapLabel, mapBlockChild)
            },
        )
        is ControlFlow.Stmt -> applyBlockChildFn(sourceBlock, mapBlockChild, target, cf.ref)
        is ControlFlow.StmtBlock -> {
            for (stmt in cf.stmts) {
                mapControlFlowPlanting(sourceBlock, stmt, target, mapLabel, mapBlockChild)
            }
        }
    }
}

private fun applyBlockChildFn(
    block: BlockTree,
    fn: Planting.(BlockChildReference, TEdge?) -> UnpositionedTreeTemplate<*>,
    planting: Planting,
    ref: BlockChildReference,
): UnpositionedTreeTemplate<*> =
    planting.fn(ref, block.dereference(ref))

fun mapControlFlow(
    cf: ControlFlow,
    mapLabel: (JumpLabel) -> JumpLabel,
    mapBlockChild: (BlockChildReference) -> BlockChildReference,
): ControlFlow = when (cf) {
    is ControlFlow.If -> ControlFlow.If(
        pos = cf.pos,
        condition = mapBlockChild(cf.condition),
        thenClause = ControlFlow.StmtBlock.wrap(mapControlFlow(cf.thenClause, mapLabel, mapBlockChild)),
        elseClause = ControlFlow.StmtBlock.wrap(mapControlFlow(cf.elseClause, mapLabel, mapBlockChild)),
    )
    is ControlFlow.Loop -> ControlFlow.Loop(
        pos = cf.pos,
        label = cf.label?.let { mapLabel(it) },
        checkPosition = cf.checkPosition,
        condition = mapBlockChild(cf.condition),
        body = ControlFlow.StmtBlock.wrap(mapControlFlow(cf.body, mapLabel, mapBlockChild)),
        increment = ControlFlow.StmtBlock.wrap(mapControlFlow(cf.increment, mapLabel, mapBlockChild)),
    )
    is ControlFlow.Break -> ControlFlow.Break(
        pos = cf.pos,
        target = when (val specifier = cf.target) {
            DefaultJumpSpecifier -> specifier
            is NamedJumpSpecifier -> NamedJumpSpecifier(mapLabel(specifier.label))
            is UnresolvedJumpSpecifier -> specifier
        },
    )
    is ControlFlow.Continue -> ControlFlow.Continue(
        pos = cf.pos,
        target = when (val specifier = cf.target) {
            DefaultJumpSpecifier -> specifier
            is NamedJumpSpecifier -> NamedJumpSpecifier(mapLabel(specifier.label))
            is UnresolvedJumpSpecifier -> specifier
        },
    )
    is ControlFlow.Labeled -> ControlFlow.Labeled(
        pos = cf.pos,
        breakLabel = mapLabel(cf.breakLabel),
        continueLabel = cf.continueLabel?.let { mapLabel(it) },
        stmts = ControlFlow.StmtBlock.wrap(mapControlFlow(cf.stmts, mapLabel, mapBlockChild)),
    )
    is ControlFlow.OrElse -> ControlFlow.OrElse(
        pos = cf.pos,
        orClause = mapControlFlow(cf.orClause, mapLabel, mapBlockChild).let {
            (it as? ControlFlow.Labeled) ?: run {
                ControlFlow.Labeled(
                    pos = it.pos,
                    breakLabel = mapLabel(cf.orClause.breakLabel),
                    continueLabel = null,
                    stmts = ControlFlow.StmtBlock.wrap(it),
                )
            }
        },
        elseClause = ControlFlow.StmtBlock.wrap(mapControlFlow(cf.elseClause, mapLabel, mapBlockChild)),
    )
    is ControlFlow.Stmt -> ControlFlow.Stmt(mapBlockChild(cf.ref))
    is ControlFlow.StmtBlock -> ControlFlow.StmtBlock(
        cf.pos,
        cf.stmts.map {
            mapControlFlow(it, mapLabel, mapBlockChild)
        },
    )
}
