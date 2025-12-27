package lang.temper.builtin

import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.name.TemperName
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.BuiltinStatelessCallableValue
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.ClosureRecord
import lang.temper.value.Fail
import lang.temper.value.InterpreterCallback
import lang.temper.value.LeafTreeType
import lang.temper.value.LeftNameLeaf
import lang.temper.value.LocalAccessor
import lang.temper.value.MacroEnvironment
import lang.temper.value.MacroValue
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Result
import lang.temper.value.TClosureRecord
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.Value
import lang.temper.value.cherryPicker
import lang.temper.value.getterSymbol
import lang.temper.value.setterSymbol
import lang.temper.value.wordSymbol

/**
 * Constructor for [ClosureRecord] values.
 */
internal object MakeClosRec : BuiltinStatelessMacroValue, NamedBuiltinFun {
    override val sigs: List<Signature2>? get() = null
    override val name: String = "makeCR"

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        if (interpMode == InterpMode.Partial) {
            return NotYet
        }

        val localAccessors = mutableListOf<LocalAccessor>()

        var name: TemperName? = null
        var getter: Value<MacroValue>? = null
        var setter: Value<MacroValue>? = null

        fun completeLocalAccessor() {
            val nm = name
            if (nm != null) {
                localAccessors.add(LocalAccessor(nm, getter, setter))
                name = null
            }
            getter = null
            setter = null
        }

        val args = macroEnv.args

        // Expect symbol, expr pairs of arguments.
        for (i in args.indices) {
            when (val symbol = args.key(i)) {
                wordSymbol -> {
                    completeLocalAccessor()
                    val argTree = args.valueTree(i)
                    val nameTree = argTree as? LeftNameLeaf
                        ?: run {
                            macroEnv.explain(
                                MessageTemplate.ExpectedValueOfType,
                                argTree.pos,
                                listOf(LeafTreeType.LeftName, argTree.treeType),
                            )
                            return Fail
                        }
                    name = nameTree.content
                }
                getterSymbol, setterSymbol -> {
                    val arg = args.evaluate(i, interpMode)
                    if (arg !is Value<*>) {
                        return Fail
                    }
                    val fn = TFunction.unpackOrNull(arg)
                        ?: run {
                            macroEnv.explain(
                                MessageTemplate.ExpectedValueOfType,
                                args.pos(i),
                                listOf(TFunction, arg),
                            )
                            return Fail
                        }
                    if (symbol == getterSymbol) {
                        getter = Value(fn)
                    } else {
                        setter = Value(fn)
                    }
                }
                else -> {
                    macroEnv.explain(
                        MessageTemplate.NoSignatureMatches,
                        args.pos(i),
                    )
                    return Fail
                }
            }
        }
        completeLocalAccessor()

        return Value(ClosureRecord(localAccessors.toList()), TClosureRecord)
    }
}

/**
 * Reads via a closure record.
 *
 * `getCR(t#1, 4)` applies the getter for accessor at index 4 in the [ClosureRecord] at `t#1`.
 */
internal object GetCR : BuiltinStatelessCallableValue, NamedBuiltinFun {
    override val name: String = "getCR"

    override val sigs: List<Signature2>? get() = null

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result = applyCR(args, cb, interpMode, true)
}

/**
 * Reads via a closure record.
 *
 * `setCR(t#1, 4, 42)` applies the setter for accessor at index 4 in the [ClosureRecord] at `t#1`
 * to the new value argument `42`.
 */
internal object SetCR : BuiltinStatelessCallableValue, NamedBuiltinFun {
    override val name: String = "setCR"

    override val sigs: List<Signature2>? get() = null

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result = applyCR(args, cb, interpMode, false)
}

private fun applyCR(
    args: ActualValues,
    cb: InterpreterCallback,
    interpMode: InterpMode,
    isGet: Boolean,
): Result {
    @Suppress("MagicNumber")
    val arity = if (isGet) 2 else 3
    val argsPositioned = args.unpackPositioned(arity, cb) ?: return Fail

    val (crArg, nameIndexArg) = argsPositioned
    val cr = TClosureRecord.unpackOrNull(crArg)
        ?: run {
            cb.explain(
                MessageTemplate.ExpectedValueOfType,
                values = listOf(TClosureRecord, crArg),
            )
            return Fail
        }
    val nameIndex = TInt.unpackOrNull(nameIndexArg)
        ?: run {
            cb.explain(
                MessageTemplate.ExpectedValueOfType,
                values = listOf(TInt, nameIndexArg),
            )
            return Fail
        }

    val localAccessor = if (nameIndex in intValues) {
        cr.localAccessors.getOrNull(nameIndex)
    } else {
        null
    }

    if (localAccessor == null) {
        cb.explain(
            MessageTemplate.OutOfBounds,
            values = listOf(nameIndex, cr.localAccessors.indices),
        )
        return Fail
    }

    val accessor = if (isGet) { localAccessor.getter } else { localAccessor.setter }
    if (accessor == null) {
        cb.explain(
            if (isGet) {
                MessageTemplate.NoAccessibleGetter
            } else {
                MessageTemplate.NoAccessibleSetter
            },
            values = listOf("ClosureRecord for ${localAccessor.name}"),
        )
        return Fail
    }
    val accessorArgs = args.cherryPicker.add(2 until args.size).build()
    return cb.apply(accessor, accessorArgs, interpMode) as? Result ?: Fail
}

private val intValues = Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
