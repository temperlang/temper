package lang.temper.be.tmpl

import lang.temper.builtin.BuiltinFun
import lang.temper.env.InterpMode
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.type.StaticType
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.type2.mapType
import lang.temper.value.ActualValues
import lang.temper.value.BasicTypeInferences
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.CallTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.CallableValue
import lang.temper.value.Document
import lang.temper.value.InterpreterCallback
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.typeFromSignature

object ResultHelperFnPlaceholders {
    private const val IS_OK_RESULT_NAME = "isOkResult"

    /** Checks whether its `Result` argument is of the *Ok* variant, not the *Err* variant. */
    object IsOkResult : BuiltinFun(
        BuiltinName(IS_OK_RESULT_NAME),
        signature = run {
            val (passF, passT) = makeTypeFormalHelper(IS_OK_RESULT_NAME, "PASS")
            val (failF, failT) = makeTypeFormalHelper(IS_OK_RESULT_NAME, "FAIL")
            Signature2(
                returnType2 = WellKnownTypes.booleanType2,
                hasThisFormal = false,
                requiredInputTypes = listOf(
                    MkType2(WellKnownTypes.resultTypeDefinition)
                        .actuals(listOf(passT, failT))
                        .get(),
                ),
                typeFormals = listOf(passF, failF),
            )
        },
        builtinOperatorId = BuiltinOperatorId.IsOkResult,
    ) {
        override fun invoke(
            args: ActualValues,
            cb: InterpreterCallback,
            interpMode: InterpMode,
        ): PartialResult {
            // Just an abstraction.
            return NotYet
        }
    }

    private const val UNPACK_OK_RESULT_NAME = "unpackOkResult"

    /** Converts a `Result<Foo>` to a `Foo`. Unsafe unless its argument has passed [IsOkResult]. */
    object UnpackOkResult : BuiltinFun(
        BuiltinName(UNPACK_OK_RESULT_NAME),
        signature = run {
            val (passF, passT) = makeTypeFormalHelper(UNPACK_OK_RESULT_NAME, "PASS")
            val (failF, failT) = makeTypeFormalHelper(UNPACK_OK_RESULT_NAME, "FAIL")
            Signature2(
                returnType2 = passT,
                hasThisFormal = false,
                requiredInputTypes = listOf(
                    MkType2(WellKnownTypes.resultTypeDefinition)
                        .actuals(listOf(passT, failT))
                        .get(),
                ),
                typeFormals = listOf(passF, failF),
            )
        },
        builtinOperatorId = BuiltinOperatorId.UnpackOkResult,
    ) {
        override fun invoke(
            args: ActualValues,
            cb: InterpreterCallback,
            interpMode: InterpMode,
        ): PartialResult {
            // Just an abstraction.
            return NotYet
        }
    }

    private const val PACK_OK_RESULT_NAME = "packOkResult"

    /** Turns a `Foo` into a `Result<Foo, FAIL>`. */
    object PackOkResult : BuiltinFun(
        BuiltinName(PACK_OK_RESULT_NAME),
        signature = run {
            val (passF, passT) = makeTypeFormalHelper(PACK_OK_RESULT_NAME, "PASS")
            val (failF, failT) = makeTypeFormalHelper(PACK_OK_RESULT_NAME, "FAIL")
            Signature2(
                returnType2 = MkType2(WellKnownTypes.resultTypeDefinition)
                    .actuals(listOf(passT, failT))
                    .get(),
                hasThisFormal = false,
                requiredInputTypes = listOf(passT),
                typeFormals = listOf(passF, failF),
            )
        },
        builtinOperatorId = BuiltinOperatorId.PackOkResult,
    ) {
        override fun invoke(
            args: ActualValues,
            cb: InterpreterCallback,
            interpMode: InterpMode,
        ): PartialResult {
            // Just an abstraction.
            return NotYet
        }
    }
}

internal fun synthesizeCall(
    doc: Document,
    pos: Position,
    callee: CallableValue,
    args: List<Tree>,
    returnType: StaticType? = null,
    typeActuals: List<Type2> = listOf(),
): CallTree {
    val sig = callee.sigs!![0]

    val calleeTree = ValueLeaf(doc, pos, Value(callee))
    calleeTree.typeInferences = BasicTypeInferences(typeFromSignature(sig), listOf())
    val call = CallTree(doc, pos, listOf(calleeTree) + args)
    val bindings2 = buildMap {
        for ((f, a) in sig.typeFormals zip typeActuals) {
            this[f] = a
        }
    }
    val variant = sig.mapType(bindings2)
    val bindings = bindings2.mapValues { hackMapNewStyleToOld(it.value) }
    call.typeInferences = CallTypeInferences(
        returnType ?: hackMapNewStyleToOld(variant.returnType2),
        variant,
        bindings,
        listOf(),
    )
    return call
}
