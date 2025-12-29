package lang.temper.astbuild

import lang.temper.ast.AstPart
import lang.temper.ast.CstPart
import lang.temper.ast.CstToken
import lang.temper.ast.ErrorEvent
import lang.temper.ast.FinishTree
import lang.temper.ast.FinishedTreeType
import lang.temper.ast.FinishedType
import lang.temper.ast.KnownProblemEvent
import lang.temper.ast.LeafAstPart
import lang.temper.ast.LeftParenthesis
import lang.temper.ast.NamePart
import lang.temper.ast.ProductionFailedEvent
import lang.temper.ast.RightParenthesis
import lang.temper.ast.ShiftLeft
import lang.temper.ast.SoftBlock
import lang.temper.ast.SoftComma
import lang.temper.ast.StartTree
import lang.temper.ast.TokenLeaf
import lang.temper.ast.ValuePart
import lang.temper.builtin.BuiltinFuns
import lang.temper.common.Log
import lang.temper.log.LogSink
import lang.temper.log.Position
import lang.temper.name.TemperName
import lang.temper.name.decodeName
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.Document
import lang.temper.value.EscTree
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.InnerTreeType
import lang.temper.value.LeafTreeType
import lang.temper.value.LeftNameLeaf
import lang.temper.value.LinearFlow
import lang.temper.value.Result
import lang.temper.value.RightNameLeaf
import lang.temper.value.TString
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.errorFn
import lang.temper.value.unpackValue

/** "Lifting" is turning a series of AST pseudo-tokens into an AST. */
internal fun lift(
    astParts: List<AstPart>,
    logSink: LogSink,
    document: Document,
): List<Tree> {
    val forest = mutableListOf<Tree>()
    val lifter = Lift(document, logSink)
    val after = lifter.liftSome(astParts, 0, forest)
    if (after < astParts.size) {
        val part = astParts[after]
        val leftPos = part.pos
        val last = astParts.last()
        val pos = Position(leftPos.loc, leftPos.left, last.pos.right)
        forest.add(
            document.treeFarm.grow(pos) {
                Call(errorFn) {
                    Replant(
                        lifter.astPartsToErrorParts(pos, astParts.subList(after, astParts.size)),
                    )
                }
            },
        )
    }
    return forest.toList()
}

private class Lift(
    private val document: Document,
    /** Receives details of error trees. */
    private val logSink: LogSink,
) {

    fun makeName(content: LeafAstPart): TemperName? = when (content) {
        is TokenLeaf -> decodeName(content.cstToken.tokenText)
        is ValuePart -> error("$content in name")
        is NamePart -> content.name
    }

    private fun makeValue(content: LeafAstPart): Result = when (content) {
        is TokenLeaf -> unpackValue(content.cstToken.tokenText, content.cstToken.tokenType)
        is ValuePart -> content.value
        is NamePart -> error("$content in value")
    }

    private fun makeLeaf(t: LeafTreeType, part: LeafAstPart): Tree = when (t) {
        LeafTreeType.LeftName -> when (val name = makeName(part)) {
            null -> astPartToErrorTree(part)
            else -> LeftNameLeaf(document, pos = part.pos, content = name)
        }
        LeafTreeType.RightName -> when (val name = makeName(part)) {
            null -> astPartToErrorTree(part)
            else -> RightNameLeaf(document, pos = part.pos, content = name)
        }
        LeafTreeType.Stay -> throw IllegalArgumentException("stay's are not a result of parsing")
        LeafTreeType.Value -> when (val result = makeValue(part)) {
            is Value<*> -> ValueLeaf(document, pos = part.pos, content = result)
            is Fail -> astPartToErrorTree(part)
        }
    }

    private fun makeInner(
        t: InnerTreeType,
        pos: Position,
        children: List<Tree>,
    ): Tree = when (t) {
        InnerTreeType.Block -> BlockTree(document, pos, children, LinearFlow)
        InnerTreeType.Call -> CallTree(document, pos, children)
        InnerTreeType.Decl -> DeclTree(document, pos, children)
        InnerTreeType.Esc -> EscTree(document, pos, children)
        InnerTreeType.Fun -> FunTree(document, pos, children)
    }

    /** Returns a value >= [partIndex] which is the start of the unprocessed suffix. */
    private fun liftOne(
        astParts: List<AstPart>,
        /** Index into [astParts] of start of suffix to lift */
        partIndex: Int,
        /** Receives built trees. */
        items: MutableList<Tree>,
    ): Int {
        var i = partIndex
        val n = astParts.size
        return when (val part = astParts[i]) {
            is FinishTree -> -1 // Signal liftSome to return so caller can consume.
            is StartTree -> {
                val make: (t: FinishedType, p: Position) -> Tree?
                if (i + 1 < n && astParts[i + 1] is LeafAstPart) {
                    i += 1
                    val leaf = astParts[i] as LeafAstPart
                    make = { t, _ ->
                        val tt = t.treeType
                        if (tt is LeafTreeType) {
                            makeLeaf(tt, leaf)
                        } else {
                            null
                        }
                    }
                    i += 1
                } else {
                    val childList = mutableListOf<Tree>()
                    i = liftSome(astParts, i + 1, childList)
                    make = { t, p ->
                        when (t) {
                            is FinishedTreeType -> {
                                val tt = t.treeType
                                if (tt is InnerTreeType) {
                                    makeInner(tt, p, childList.toList())
                                } else {
                                    null
                                }
                            }
                            is SoftBlock -> if (childList.size == 1) {
                                childList[0]
                            } else {
                                makeInner(t.treeType, p, childList.toList())
                            }
                            is SoftComma -> if (childList.size == 1) {
                                childList[0]
                            } else {
                                makeInner(
                                    InnerTreeType.Call,
                                    p,
                                    listOf(ValueLeaf(document, p.leftEdge, BuiltinFuns.vCommaFn)) +
                                        childList,
                                )
                            }
                        }
                    }
                }
                val next = if (i < n) astParts[i] else null
                if (next is FinishTree) {
                    items.add(
                        make(next.type, part.pos.copy(right = next.pos.right)) ?: malformed(
                            astParts,
                            i,
                            "unexpected tree type ${next.type}",
                        ),
                    )
                } else {
                    malformed(astParts, i, "unbalanced")
                }
                i + 1
            }
            is ShiftLeft -> {
                // This just works by swapping the last 2.
                val lastIndex = items.size - 1
                val secondToLastIndex = lastIndex - 1
                if (secondToLastIndex < 0) {
                    malformed(astParts, i, "too few preceding ShiftLeft")
                } else {
                    val oldLast = items[lastIndex]
                    items[lastIndex] = items[secondToLastIndex]
                    items[secondToLastIndex] = oldLast
                    i + 1
                }
            }
            is ValuePart -> {
                items.add(
                    ValueLeaf(document, pos = part.pos, content = part.value),
                )
                i + 1
            }
            is LeafAstPart -> malformed(astParts, i, "orphan $part")
            is ErrorEvent -> {
                val pos = part.pos
                items.add(
                    document.treeFarm.grow(pos) {
                        Call(errorFn) {
                            Call(BuiltinFuns.vListifyFn) {
                                cstPartsToErrorParts(pos, part.parts).forEach {
                                    Replant(it)
                                }
                            }
                        }
                    },
                )
                when (val errorPart: ErrorEvent = part) {
                    is ProductionFailedEvent ->
                        logSink.log(
                            level = Log.Error,
                            template = errorPart.messageTemplate,
                            pos = pos,
                            values = errorPart.messageValues,
                        )
                    is KnownProblemEvent ->
                        logSink.log(
                            level = Log.Error,
                            template = errorPart.messageTemplate,
                            pos = pos,
                            values = emptyList(),
                        )
                }
                i + 1
            }
        }
    }

    fun liftSome(
        astParts: List<AstPart>,
        pos: Int,
        items: MutableList<Tree>,
    ): Int {
        var i = pos
        val n = astParts.size
        while (i < n) {
            val nextI = liftOne(astParts, i, items)
            if (nextI < 0) {
                return i // Let caller handle
            }
            i = nextI
        }
        return n
    }

    fun cstPartsToErrorParts(
        pos: Position,
        parts: List<CstPart>,
    ): List<Tree> = parts.map<CstPart, Tree> { part ->
        when (part) {
            is CstToken -> ValueLeaf(document, pos, toPseudoValue(part.tokenText))
            is LeftParenthesis ->
                ValueLeaf(document, pos, toPseudoValue("`(${part.operator.name}`"))
            is RightParenthesis ->
                ValueLeaf(document, pos, toPseudoValue("`${part.operator.name})`"))
        }
    }

    private fun astPartToErrorTree(part: AstPart): Tree {
        val pos = part.pos
        return CallTree(
            document,
            pos = pos,
            children = listOf(
                ValueLeaf(document, pos.leftEdge, errorFn),
            ) + astPartsToErrorParts(pos, listOf(part)),
        )
    }

    fun astPartsToErrorParts(pos: Position, parts: List<AstPart>): Tree =
        CallTree(
            document,
            pos,
            listOf(
                ValueLeaf(document, pos.leftEdge, BuiltinFuns.vListifyFn),
            ) + parts.map {
                astPartToErrorPart(it)
            },
        )

    private fun astPartToErrorPart(p: AstPart): Tree {
        val pos = p.pos
        return when (p) {
            is StartTree -> ValueLeaf(document, pos, Value("`<`", TString))
            is FinishTree -> ValueLeaf(
                document,
                pos,
                Value("`${ p.type.abbrev }>`", TString),
            )
            is TokenLeaf -> ValueLeaf(document, pos, toPseudoValue(p.cstToken.tokenText))
            is ShiftLeft -> ValueLeaf(document, pos, toPseudoValue("`ShiftLeft`"))
            is ValuePart -> ValueLeaf(document, pos, p.value)
            is NamePart -> ValueLeaf(document, pos, toPseudoValue(p.name.rawDiagnostic))
            is ErrorEvent -> document.treeFarm.grow(pos) {
                Call(errorFn) {
                    Call(BuiltinFuns.vListifyFn) {
                        cstPartsToErrorParts(pos, p.parts).forEach {
                            Replant(it)
                        }
                    }
                }
            }
        }
    }
}

private fun toPseudoValue(tokenText: String) = Value(tokenText, TString)

private fun malformed(astParts: List<AstPart>, i: Int, description: String): Nothing = error(
    "$description at $i: ${
        astParts.subList(0, i).joinToString(", ")
    }, \u2191${
        astParts.subList(i, astParts.size).joinToString(", ")
    }",
)
