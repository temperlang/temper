package lang.temper.builtin

import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.type.DotHelper
import lang.temper.type.StaticType
import lang.temper.type.WellKnownTypes
import lang.temper.type.canBeNull
import lang.temper.type.excludeNull
import lang.temper.type2.Signature2
import lang.temper.value.BlockPlanting
import lang.temper.value.CallTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.ComparableTypeTag
import lang.temper.value.Fail
import lang.temper.value.LeafTree
import lang.temper.value.MacroEnvironment
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Planting
import lang.temper.value.TBoolean
import lang.temper.value.TNull
import lang.temper.value.Tree
import lang.temper.value.TreeTemplate
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.and
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.valueContained

/**
 * `==` can perform equality checks, but its primary purpose is to
 * handle null safety checks and replace itself with a call to an
 * equivalence operator specific to its arguments types.
 *
 * This special takes three arguments:
 *
 * - `a`, the first to compare
 * - `b`, compared to `a`
 * - DotHelper, a DotHelper that conveys the extension resolutions
 *   available in scope for `_==_`
 */
sealed class EquivalenceDesugarMacro(
    val negated: Boolean,
) : BuiltinMacro(
    if (negated) {
        "!="
    } else {
        "=="
    },
    run {
        // Having a type formal allows propagating information from one
        // argument to the other.  For example: `returnsListOfStrings() == []`.
        val (f, t) = makeTypeFormal(
            if (negated) { "eq" } else { "ne" },
            "T",
            listOf(WellKnownTypes.anyValueOrNullType2),
        )
        Signature2(
            WellKnownTypes.booleanType2,
            false,
            listOf(t, t, WellKnownTypes.anyValueType2),
            typeFormals = listOf(f),
        )
    },
) {
    companion object {
        private const val N_ARGS = 3
    }

    val sig = sigs!!.first() as Signature2
    val value = Value(this)

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        val args = macroEnv.args
        val calleePos = macroEnv.callee.pos
        if (args.size != N_ARGS) {
            val problem = LogEntry(
                MessageTemplate.ArityMismatch,
                macroEnv.pos,
                listOf(N_ARGS),
            )
            when (interpMode) {
                InterpMode.Partial ->
                    macroEnv.replaceMacroCallWithErrorNode(problem)
                InterpMode.Full -> {}
            }
            return Fail(problem)
        }

        if (interpMode == InterpMode.Partial) {
            val aTree = args.valueTree(0)
            val bTree = args.valueTree(1)
            val dotHelper = args.valueTree(2).functionContained
                as? DotHelper
                ?: run {
                    macroEnv.replaceMacroCallWithErrorNode()
                    return@invoke Fail
                }

            // If either operand is `null`, convert to an isNull call.
            val aIsNull = aTree.valueContained == TNull.value
            val bIsNull = bTree.valueContained == TNull.value
            if (aIsNull || bIsNull) {
                if (aIsNull && bIsNull) {
                    val result = TBoolean.value(!negated)
                    macroEnv.replaceMacroCallWith {
                        V(macroEnv.pos, result, WellKnownTypes.booleanType)
                    }
                    return result
                }
                val operand = if (aIsNull) bTree else aTree
                fun Planting.plantIsNull(): TreeTemplate<CallTree> {
                    return IsNullCall(
                        pos = macroEnv.pos,
                        calleePos = calleePos,
                        argType = operand.typeInferences?.type,
                    ) {
                        Replant(freeTree(operand))
                    }
                }
                macroEnv.replaceMacroCallWith {
                    MaybeNot(macroEnv.pos, negated = negated) {
                        plantIsNull()
                    }
                }
                return NotYet
            }

            // If we have type info, desugar based on that.
            val aType = aTree.typeInferences?.type
            val bType = bTree.typeInferences?.type

            val aCanBeNull = aType?.let(::canBeNull)
            val bCanBeNull = bType?.let(::canBeNull)
            if (aCanBeNull == true || bCanBeNull == true) {
                // Do the nullable expansion
                macroEnv.replaceMacroCallWith {
                    Block {
                        val aLeaf: LeafTree = maybeCaptureInBlock(aTree)
                        val bLeaf: LeafTree = maybeCaptureInBlock(bTree)

                        fun BlockPlanting.plantANotNullBranch() {
                            if (bCanBeNull == false) {
                                plantDotHelper(
                                    macroEnv.pos, calleePos, dotHelper,
                                    a = aLeaf,
                                    aHasNonNullType = !(aCanBeNull ?: true),
                                    b = bLeaf,
                                    bHasNonNullType = true,
                                )
                            } else {
                                If(
                                    cond = {
                                        IsNullCall(bTree.pos.leftEdge, argType = bType) {
                                            Replant(bLeaf.copy(copyInferences = true))
                                        }
                                    },
                                    thn = {
                                        // a != null && b == null
                                        V(bTree.pos.leftEdge, TBoolean.value(negated), WellKnownTypes.booleanType)
                                    },
                                    els = {
                                        plantDotHelper(
                                            macroEnv.pos, calleePos, dotHelper,
                                            a = aLeaf,
                                            aHasNonNullType = !(aCanBeNull ?: true),
                                            b = bLeaf,
                                            bHasNonNullType = !(bCanBeNull ?: true),
                                        )
                                    },
                                )
                            }
                        }

                        if (aCanBeNull == false) {
                            plantANotNullBranch()
                        } else {
                            If(
                                cond = {
                                    IsNullCall(aTree.pos.leftEdge, argType = aType) {
                                        Replant(aLeaf.copy(copyInferences = true))
                                    }
                                },
                                thn = {
                                    if (bCanBeNull == false) {
                                        // a == null && b != null -> a != b
                                        V(bTree.pos.leftEdge, TBoolean.value(negated), WellKnownTypes.booleanType)
                                    } else {
                                        MaybeNot(
                                            bTree.pos.leftEdge,
                                            negated = negated,
                                        ) {
                                            IsNullCall(bTree.pos.leftEdge, argType = bType) {
                                                Replant(bLeaf.copy(copyInferences = true))
                                            }
                                        }
                                    }
                                },
                                els = {
                                    plantANotNullBranch()
                                },
                            )
                        }
                    }
                }
                return NotYet
            } else if (aType != null && bType != null) {
                // We can do the regular non-nullable expansion.
                macroEnv.replaceMacroCallWith {
                    plantDotHelper(
                        macroEnv.pos,
                        calleePos,
                        dotHelper,
                        a = aTree,
                        aHasNonNullType = true,
                        bTree,
                        bHasNonNullType = true,
                    )
                }
                return NotYet
            }
        }

        // Try and evaluate truthiness.
        val result = args.evaluate(0, interpMode).and { aValue ->
            args.evaluate(1, interpMode).and { bValue ->
                val aTypeTag = aValue.typeTag
                if (aTypeTag == bValue.typeTag) {
                    if (aTypeTag is ComparableTypeTag<*>) {
                        TBoolean.value(
                            0 == applyComparator(aTypeTag, aValue, bValue),
                        )
                    } else {
                        NotYet
                    }
                } else if (aValue == TNull.value || bValue == TNull.value) {
                    // If both were null, top branch would've been taken
                    TBoolean.valueFalse
                } else {
                    NotYet
                }
            }
        }

        return when (interpMode) {
            InterpMode.Partial -> {
                if (result is Value<*> && result.typeTag == TBoolean) {
                    macroEnv.replaceMacroCallWith {
                        V(macroEnv.pos, result, WellKnownTypes.booleanType)
                    }
                }
                NotYet
            }
            InterpMode.Full -> result
        }
    }

    private fun Planting.plantDotHelper(
        pos: Position,
        calleePos: Position,
        dotHelper: DotHelper,
        a: Tree,
        aHasNonNullType: Boolean,
        b: Tree,
        bHasNonNullType: Boolean,
    ): UnpositionedTreeTemplate<*> {
        return MaybeNot(pos, negated = negated) {
            Call(pos) {
                V(
                    calleePos,
                    Value(dotHelper),
                )
                if (aHasNonNullType) {
                    Replant(freeTree(a))
                } else {
                    NotNullCall(a.pos, a.typeInferences?.type) {
                        Replant(freeTree(a))
                    }
                }
                if (bHasNonNullType) {
                    Replant(freeTree(b))
                } else {
                    NotNullCall(b.pos, b.typeInferences?.type) {
                        Replant(freeTree(b))
                    }
                }
            }
        }
    }
}

/** Desugars `==` to null-safe, type specific checks. */
object EqMacro : EquivalenceDesugarMacro(negated = false)

/** Desugars `!=` to null-safe, type specific checks. */
object NeMacro : EquivalenceDesugarMacro(negated = true)

private fun <T : Any> applyComparator(
    typeTag: ComparableTypeTag<T>,
    a: Value<*>,
    b: Value<*>,
) = typeTag.comparator.compare(
    typeTag.unpack(a),
    typeTag.unpack(b),
)

@Suppress("FunctionName")
fun Planting.NotNullCall(
    pos: Position,
    argType: StaticType?,
    plantArg: Planting.() -> UnpositionedTreeTemplate<*>,
): TreeTemplate<CallTree> {
    val callType = argType?.let {
        val argTypeNotNull = excludeNull(argType)
        val sig = NotNullFn.sig
        CallTypeInferences(
            argTypeNotNull,
            sig,
            mapOf(sig.typeFormals[0] to argTypeNotNull),
            listOf(),
        )
    }
    return Call(pos, type = callType) {
        V(pos.leftEdge, BuiltinFuns.vNotNullFn, callType?.variant)
        plantArg()
    }
}
