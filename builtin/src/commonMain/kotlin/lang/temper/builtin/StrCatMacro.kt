package lang.temper.builtin

import lang.temper.env.InterpMode
import lang.temper.format.OutToks
import lang.temper.log.MessageTemplate
import lang.temper.stage.Stage
import lang.temper.type.DotHelper
import lang.temper.type.ExternalBind
import lang.temper.type.InvalidType
import lang.temper.type.WellKnownTypes
import lang.temper.type.canBeNull
import lang.temper.type2.Signature2
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.CallTree
import lang.temper.value.Fail
import lang.temper.value.IfThenElse
import lang.temper.value.MacroEnvironment
import lang.temper.value.NameLeaf
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Planting
import lang.temper.value.RightNameLeaf
import lang.temper.value.SpecialFunction
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.catBuiltinName
import lang.temper.value.freeTree
import lang.temper.value.isErrorCall
import lang.temper.value.rawBuiltinName
import lang.temper.value.toStringSymbol
import lang.temper.value.typeForValue
import lang.temper.value.vIsNullFn
import lang.temper.value.valueContained

/**
 * Desugars to a simple string concatenation when we have the time.
 */
internal object StrCatMacro : BuiltinMacro(catBuiltinName.builtinKey, null), SpecialFunction {
    override val builtinOperatorId get() = BuiltinOperatorId.StrCat

    override val sigs: List<Signature2> = listOf(
        Signature2(
            returnType2 = WellKnownTypes.stringType2,
            hasThisFormal = false,
            requiredInputTypes = listOf(),
            restInputsType = WellKnownTypes.anyValueOrNullType2,
        ),
    )

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        // Figure out if we already have a simple string value for immediate use.
        val args = macroEnv.args
        val argRange = 0..<args.size

        if (interpMode == InterpMode.Full) {
            val content = buildString {
                for (i in argRange) {
                    val str: String = when (val result = args.evaluate(i, interpMode)) {
                        is NotYet, is Fail -> return result
                        TNull.value -> OutToks.nullWord.text
                        is Value<*> -> {
                            if (result.typeTag == TString) {
                                TString.unpack(result)
                            } else {
                                val toStringCall = macroEnv.treeFarm.grow(args.pos(i)) {
                                    buildToStringCall(result)
                                }
                                when (val toStringResult = macroEnv.evaluateTree(toStringCall, interpMode)) {
                                    is NotYet, is Fail -> return toStringResult
                                    is Value<*> ->
                                        TString.unpackOrNull(toStringResult)
                                            // TODO: explain that toString did not return a string
                                            ?: return Fail
                                }
                            }
                        }
                    }
                    append(str)
                }
            }
            return Value(content, TString)
        }

        val strs = argRange.map {
            args.valueTree(it).valueContained(TString)
        }

        if (strs.none { it == null }) {
            val result = Value(strs.joinToString("") { it!! }, TString)
            macroEnv.replaceMacroCallWith {
                V(macroEnv.pos, result, WellKnownTypes.stringType)
            }
            return result
        }

        // If we have type info, use that to attach .toString() calls or ?.toString() calls as appropriate.
        val argsWithTypes = argRange.map { i ->
            args.valueTree(i).let { tree ->
                tree to (
                    tree.typeInferences?.type
                        ?: (tree as? ValueLeaf)?.content?.let {
                            typeForValue(it)
                        }
                    )
            }
        }

        if (macroEnv.stage < Stage.GenerateCode && argsWithTypes.any { (_, type) -> type == null }) {
            return NotYet
        }

        macroEnv.replaceMacroCallWith {
            Call(macroEnv.pos) {
                V(macroEnv.callee.pos, BuiltinFuns.vStrCatFn)
                for ((arg, argType) in argsWithTypes) {
                    if (argType == WellKnownTypes.stringType) {
                        Replant(freeTree(arg))
                    } else if (isErrorCall(arg)) {
                        // Don't try to call toString on error nodes.
                        Replant(freeTree(arg))
                    } else if (argType is InvalidType? || canBeNull(argType)) {
                        // if (isNull(arg)) { "null" } else { arg.toString() }
                        fun Planting.plantNullSafeCall(toCheck: Tree, subject: Tree) = IfThenElse(
                            {
                                Call(vIsNullFn) {
                                    Replant(toCheck)
                                }
                            },
                            {
                                V(arg.pos, Value(OutToks.nullWord.text, TString), WellKnownTypes.stringType)
                            },
                            {
                                buildToStringCall(
                                    macroEnv.treeFarm.grow(subject.pos) {
                                        Call(BuiltinFuns.vNotNullFn) {
                                            Replant(subject)
                                        }
                                    },
                                )
                            },
                        )
                        when (arg) {
                            is ValueLeaf, is NameLeaf -> plantNullSafeCall(freeTree(arg), arg.copy())
                            else -> Block(macroEnv.pos) {
                                val doc = macroEnv.document
                                val name = doc.nameMaker.unusedTemporaryName("subject")
                                val nameLeaf = RightNameLeaf(doc, arg.pos, name)
                                // let subject#0;
                                // subject#0 = arg;
                                // if (isNull(subject#0)) { "null" } else { subject#0.toString() }
                                Decl(name) {}
                                Call(BuiltinFuns.vSetLocalFn) {
                                    Ln(name)
                                    Replant(freeTree(arg))
                                }
                                plantNullSafeCall(nameLeaf, nameLeaf.copy())
                            }
                        }
                    } else {
                        // Just call .toString()
                        buildToStringCall(freeTree(arg))
                    }
                }
            }
        }
        return NotYet
    }

    override val callMayFailPerSe: Boolean get() = false
}

internal object StrRawMacro : BuiltinMacro(rawBuiltinName.builtinKey, null) {
    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        // Convert calls to `raw` into calls to `cat` by interleaving the lists.
        val args = macroEnv.args
        if (macroEnv.stage == Stage.Import) {
            // Let any interpolate work out first.
            return NotYet
        }
        // We support only calls to `raw` with a pair of list literals, which `raw"..."` syntax provides.
        // If we insist on evaluating things, we'd also need to support a runtime function for `raw`.
        args.size == 2 || return macroEnv.failer(MessageTemplate.ArityMismatch, values = listOf(2))
        val templateStrings = args.valueTree(0)
        val interpolatedValues = args.valueTree(1)
        for (listTree in listOf(templateStrings, interpolatedValues)) {
            if (!(listTree is CallTree && listTree.childOrNull(0)?.valueContained == BuiltinFuns.vListifyFn)) {
                return macroEnv.failer(MessageTemplate.UnrecognizedToken)
            }
        }
        // And we need at list one string template value and matching numbers of each.
        if (!(templateStrings.size > 1 && interpolatedValues.size == templateStrings.size - 1)) {
            return macroEnv.failer(MessageTemplate.UnrecognizedToken)
        }
        // That validated, convert the call.
        macroEnv.call?.incoming?.replace {
            Call {
                // Everything is a string value, so skip the cat macro straight to the function.
                V(BuiltinFuns.vStrCatFn)
                fun replantUnlessEmpty(tree: Tree) {
                    // When building these things, we often supply plain empty string values. Be kind and clear them.
                    if (tree.valueContained?.let { TString.unpackOrNull(it) } != "") {
                        Replant(freeTree(tree))
                    }
                }
                replantUnlessEmpty(templateStrings.child(1))
                for (valueIndex in 1 until interpolatedValues.size) {
                    replantUnlessEmpty(interpolatedValues.child(valueIndex))
                    replantUnlessEmpty(templateStrings.child(valueIndex + 1))
                }
            }
        }
        return NotYet
    }
}

private fun Planting.buildToStringCall(subject: Value<*>) =
    Call {
        Call {
            V(Value(DotHelper(memberAccessor = ExternalBind, symbol = toStringSymbol)))
            V(subject)
        }
    }

private fun Planting.buildToStringCall(subject: Tree) =
    Call {
        Call {
            V(Value(DotHelper(memberAccessor = ExternalBind, symbol = toStringSymbol)))
            Replant(subject)
        }
    }
