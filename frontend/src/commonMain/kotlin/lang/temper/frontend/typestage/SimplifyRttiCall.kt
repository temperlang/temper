package lang.temper.frontend.typestage

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.RttiCheckFunction
import lang.temper.builtin.RuntimeTypeOperation
import lang.temper.interp.errorNodeFor
import lang.temper.log.Position
import lang.temper.type.BubbleType
import lang.temper.type.MkType
import lang.temper.type.WellKnownTypes
import lang.temper.type.isVoidLike
import lang.temper.type2.NonNullType
import lang.temper.type2.Nullity.NonNull
import lang.temper.type2.Type2
import lang.temper.type2.TypeContext2
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.type2.withNullity
import lang.temper.type2.withType
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.BubbleFn
import lang.temper.value.CallTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.ControlFlow
import lang.temper.value.IsNullFn
import lang.temper.value.LeafTree
import lang.temper.value.MacroValue
import lang.temper.value.Planting
import lang.temper.value.ReifiedType
import lang.temper.value.RightNameLeaf
import lang.temper.value.StructuredFlow
import lang.temper.value.TNull
import lang.temper.value.TType
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.reifiedTypeContained
import lang.temper.value.ssaSymbol
import lang.temper.value.typeFromSignature
import lang.temper.value.vIsNullFn
import lang.temper.value.void

/**
 * Simplify `expression is TargetType` and `expression as TargetType` calls.
 *
 * The goal of this is to simplify runtime type information (RTTI) checks:
 *
 * - separate `null` checks from RTTI checks because.
 * - allow each RTTI check to operate on a single nominal type
 *   to reduce the burden on translation backends.
 * - make sure compound checks store any complex expression in
 *   a Temporary in case it side-effects.
 *
 * A complex check is a type check whose target type is an AND or OR type,
 * after `null` is separated out as above.
 * E.g. `expr is (Interface1 & Interface2)` is equivalent to
 * `t is Interface1 && t is Interface2` once `expr` has been captured
 * in the temporary *t*.
 *
 * We first deal with complex target types:
 *
 * - A `.is` check for a complex target type turns captures the expression
 *   in a temporary if necessary and then constructs an expression using
 *   `&&` and `||` to nest simpler `.is` expressions.
 * - A `.as` check with a complex target type turns into a tree of
 *   nested `if (...) { ... } else if` statements where the conditions are
 *   `.is` tests and the body statements are `.as` for the corresponding types
 *   and the chain ends with a bubble().  This mixing of `is` and `as` avoids
 *   unnecessary bubbling and interception.
 *
 * Once we have a simple nominal type (or Never), excluding null, we apply
 * the transforms below which covers all 7 combinations of:
 *
 * - rto: AS or IS
 * - target-nullability: yes when TargetType is like (X?)
 * - needsTypeCheck: yes when expression's type (excluding null)
 *   is not a sub-type of TargetType (excluding null)
 *
 * As a special case, if target-nullability='N' && rto=IS, AND the
 * source and target types excluding null are **exactly** the same,
 * we can elide the check:
 * `stringOrNull is String` -> `stringOrNull != null`.
 * That requires knowing the source type which we do not at the time
 * we run this.
 * TODO: do this works when type refinements due to guards are available.
 *
 * ```
 * 0: (AS, N, N)
 *   sameTypeOrNullExpr as SameType
 * ->
 *   if (t == null) { bubble() }  // Complex expression captured in `t`
 *   t as SameType
 *
 * 1: (IS, N, N)
 *   sameTypeOrNullExpr is SameType
 * ->
 *   t != null && t is SameType
 *
 * 2: (AS, Y, N)
 *   sameTypeOrNull as SameType?
 * ->
 *   if (t == null) { null } else { t as SameType }
 *
 * 3: (IS, Y, N)
 *   sameTypeOrNull is SameType?
 * ->
 *   t == null || t is SameType
 *
 * 4: (AS, N, Y)
 *   superTypeOrNull as SubType
 * ->
 *   if (t == null) { bubble() }
 *   t as SubType
 *
 * 5: (IS, N, Y)
 *   superTypeOrNull is SubType
 * ->
 *   t == null || t is SubType
 *
 * 6: (AS, Y, Y)
 *   superTypeOrNull as SubType?
 * ->
 *   if (t == null) { null } else { t as SubType }
 *
 * 7: (IS, Y, Y)
 *   superTypeOrNull is SubType?
 * ->
 *   t == null || t is SubType }
 * ```
 */
internal fun simplifyRttiCall(rttiCall: CallTree, typeContext: TypeContext2) {
    val document = rttiCall.document

    val (fnTree, expr, targetTree) = rttiCall.children
    val fn = fnTree.functionContained as RttiCheckFunction

    if (targetTree.typeInferences != null) {
        // If type inferences are null, then we haven't expanded yet.
        // See IsCheck and AsCheck which add type inferences to avoid
        // recursive expansion, and to avoid repeatedly expanding as
        // when GenerateCodeStage invokes MagicSecurityDust.
        return
    }

    val (temporaryName, simpleExpr: LeafTree) = when (expr) {
        is ValueLeaf -> null to expr
        is RightNameLeaf -> null to expr
        else -> {
            val name = rttiCall.document.nameMaker.unusedTemporaryName("t")
            name to RightNameLeaf(document, expr.pos, name)
        }
    }
    if (temporaryName != null) {
        expr.incoming?.replace {
            Rn(expr.pos, temporaryName)
        }
    }

    val target = targetTree.reifiedTypeContained ?: return
    val targetType = target.type2
    val targetCanBeNull = typeContext.admitsNull(targetType)
    val targetTypeNotNull = targetType.withNullity(NonNull)
    if (targetCanBeNull && targetType == targetTypeNotNull) {
        // If we have a type parameter reference to a necessarily nullable
        // type parameter like <T extends AnyValue?>, we can cast away nullability
        // but user code must instead cast to an upper bound but not null like AnyValue.
        TODO("issue error and replace with error node")
    }

    val edge = rttiCall.incoming!!
    val pos = rttiCall.pos
    val targetPos = targetTree.pos
    val rto = fn.runtimeTypeOperation

    val replacementAssumingNotNull = simplify(
        pos = pos,
        targetPos = targetPos,
        rto = rto,
        expr = simpleExpr,
        targetType = targetTypeNotNull,
    )

    var replacement = when (rto) {
        RuntimeTypeOperation.As, RuntimeTypeOperation.AssertAs -> {
            // 0: (AS, N, N)
            // 4: (AS, N, Y)

            // 2: (AS, Y, N)
            // 6: (AS, Y, Y)

            // 0, 4
            //   sameOrSuperTypeOrNullExpr as SameOrSubType
            // ->
            //   if (t == null) { bubble() } else { t as SameOrSubType }

            // 2, 6
            //   sameOrSuperTypeOrNullExpr as SameOrSubType?
            // ->
            //   if (t == null) { null     } else { t as SameOrSubType }
            val block = document.treeFarm.grow(pos) {
                Block(pos) {
                    Call {
                        V(expr.pos.leftEdge, vIsNullFn)
                        Replant(simpleExpr.copy())
                    }
                    if (!targetCanBeNull) { // 2, 10
                        Call {
                            val bubbler = when (rto) {
                                RuntimeTypeOperation.As -> BuiltinFuns.vBubble
                                RuntimeTypeOperation.AssertAs -> BuiltinFuns.vPanic
                                else -> error("unexpected")
                            }
                            V(expr.pos.rightEdge, bubbler)
                        }
                    } else { // 6, 14
                        V(expr.pos.rightEdge, TNull.value)
                    }
                    Replant(replacementAssumingNotNull)
                }
            }
            val lastIndex = block.size - 1
            val pre = (0 until lastIndex - 2).map {
                ControlFlow.Stmt(refTo(block, it))
            }
            val condition = refTo(block, lastIndex - 2)
            val thenClause = refTo(block, lastIndex - 1)
            val asCall = refTo(block, lastIndex)
            // Build flow control
            block.replaceFlow(
                StructuredFlow(
                    ControlFlow.StmtBlock(
                        block.pos,
                        pre + listOf(
                            ControlFlow.If(
                                thenClause.pos,
                                condition,
                                ControlFlow.StmtBlock.wrap(ControlFlow.Stmt(thenClause)),
                                ControlFlow.StmtBlock.wrap(ControlFlow.Stmt(asCall)),
                            ),
                        ),
                    ),
                ),
            )
            block
        }
        RuntimeTypeOperation.Is -> {
            // 1: (IS, N, N)
            // 3: (IS, N, Y)
            // 5: (IS, Y, N)
            // 7: (IS, Y, Y)

            // 1, 5
            //   sameTypeOrNullExpr is SameOrSubType
            // ->
            //   t != null && t is SameOrSubType

            // 3, 7
            //   sameOrSuperTypeOrNull is SameOrSubType?
            // ->
            //   t == null || t is SameOrSubType

            document.treeFarm.grow(pos) {
                Call(pos) {
                    V(
                        pos.leftEdge,
                        if (targetCanBeNull) {
                            BuiltinFuns.vDesugarLogicalOrFn // 3, 7
                        } else {
                            BuiltinFuns.vDesugarLogicalAndFn // 1, 5
                        },
                    )
                    val exprLeft = expr.pos.leftEdge
                    if (targetCanBeNull) {
                        Call(exprLeft, IsNullFn) {
                            Replant(simpleExpr.copy())
                        }
                    } else {
                        Call(exprLeft, BuiltinFuns.notFn) {
                            Call(exprLeft, IsNullFn) {
                                Replant(simpleExpr.copy())
                            }
                        }
                    }
                    Replant(replacementAssumingNotNull)
                }
            }
        }
    }

    if (temporaryName != null) {
        replacement = document.treeFarm.grow(pos) {
            Block {
                Decl(pos.leftEdge, temporaryName) {
                    V(ssaSymbol)
                    V(void)
                }
                Call(expr.pos) {
                    V(expr.pos.leftEdge, BuiltinFuns.vSetLocalFn)
                    Ln(expr.pos.leftEdge, temporaryName)
                    Replant(freeTree(expr))
                }
                Replant(replacement)
            }
        }
    }

    edge.replace(replacement)
}

private fun simplify(
    pos: Position,
    targetPos: Position,
    rto: RuntimeTypeOperation,
    expr: LeafTree,
    targetType: Type2,
): Tree {
    val document = expr.document

    // Delegate to complex handling
    val replacement = withType(
        targetType,
        invalid = { errorNodeFor(expr) },
        // fn = { _, _, _ ->
        //    simplify(pos, targetPos, rto, expr, WellKnownTypes.anyValueType2)
        // },
        result = { _, _, _ -> errorNodeFor(expr) },
        never = { _, _, neverType ->
            expr.document.treeFarm.grow(expr.pos) {
                Call(BubbleFn, bubbleFnCallTypeInferences(neverType)) {}
            }
        },
        fallback = { null },
    )
    if (replacement != null) {
        return replacement
    }

    // We got a nominal type initially, or a complex type handler called
    // back in here with a nominal type.
    @Suppress("USELESS_IS_CHECK")
    check(targetType is NonNullType)

    return document.treeFarm.grow(pos) {
        Check(pos, rto, targetPos, expr, targetType)
    }
}

@Suppress("FunctionName")
private fun Planting.Check(
    pos: Position,
    rto: RuntimeTypeOperation,
    targetPos: Position,
    expr: LeafTree,
    targetType: Type2,
) = when (rto) {
    RuntimeTypeOperation.As -> BuiltinFuns.asFn
    RuntimeTypeOperation.AssertAs -> BuiltinFuns.assertAsFn
    RuntimeTypeOperation.Is -> BuiltinFuns.isFn
}.let { plantCheck(pos, it, targetPos, expr, targetType) }

private fun Planting.plantCheck(
    pos: Position,
    fn: MacroValue,
    targetPos: Position,
    expr: LeafTree,
    targetType: Type2,
) = Call(pos, fn, callTypeForCheck(targetType, expr)) {
    Replant(expr.copy())
    V(
        targetPos,
        Value(ReifiedType(targetType), TType),
        WellKnownTypes.typeType,
    )
}

private fun callTypeForCheck(targetType: Type2, expr: Tree): CallTypeInferences? {
    val exprType = expr.typeInferences?.type ?: return null
    val targetTypeOrBubble = MkType.or(hackMapNewStyleToOld(targetType), BubbleType)
    val variant = MkType.fn(
        emptyList(),
        listOf(exprType),
        null,
        targetTypeOrBubble,
    )
    return CallTypeInferences(targetTypeOrBubble, variant, mapOf(), listOf())
}

private fun refTo(b: BlockTree, childIndex: Int) = BlockChildReference(
    childIndex,
    b.child(childIndex).pos,
)

private fun bubbleFnCallTypeInferences(neverType: Type2): CallTypeInferences {
    val neverTypeOld = hackMapNewStyleToOld(neverType)
    val (variant, bindings) = if (neverType.isVoidLike) {
        BubbleFn.sigs[0] to mapOf()
    } else {
        BubbleFn.sigs[1].let {
            it to mapOf(it.typeFormals[0] to neverTypeOld)
        }
    }
    return CallTypeInferences(
        neverTypeOld,
        typeFromSignature(variant),
        bindings,
        listOf(),
    )
}
