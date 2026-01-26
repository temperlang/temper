package lang.temper.builtin

import lang.temper.common.subListToEnd
import lang.temper.env.InterpMode
import lang.temper.format.OutToks
import lang.temper.log.MessageTemplate
import lang.temper.log.spanningPosition
import lang.temper.name.ResolvedName
import lang.temper.stage.Stage
import lang.temper.type.DotHelper
import lang.temper.type.ExternalBind
import lang.temper.type.InvalidType
import lang.temper.type.WellKnownTypes
import lang.temper.type.canBeNull
import lang.temper.type2.AnySignature
import lang.temper.type2.Signature2
import lang.temper.value.BasicTypeInferences
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.CallTree
import lang.temper.value.Fail
import lang.temper.value.IfThenElse
import lang.temper.value.MacroEnvironment
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Planting
import lang.temper.value.RightNameLeaf
import lang.temper.value.SpecialFunction
import lang.temper.value.TBoolean
import lang.temper.value.TFunction
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.TType
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.freeTree
import lang.temper.value.funStringSymbol
import lang.temper.value.interpolateSymbol
import lang.temper.value.isErrorCall
import lang.temper.value.rawBuiltinName
import lang.temper.value.symbolContained
import lang.temper.value.toStringSymbol
import lang.temper.value.typeForValue
import lang.temper.value.vIsNullFn
import lang.temper.value.valueContained

/**
 * Tagged strings come in a few forms, and this macro parlays between them.
 *
 * Arguments to the stringExpr are:
 *
 * - First, a tag value.
 * - Second, a boolean flag, isTagged: false if there was no tag.
 *   If this flag is false then escape sequences have been decoded, otherwise they have not.
 * - Rest, either a function tree with \accumulateBlock metadata or any number of string parameters.
 *
 * For simple string expressions, those without interpolations or embedded statement fragments,
 * it just converts them to a string value, as if they were tagged with [StringExprMacro].
 *
 * A tag may be any of the following:
 *
 * - `null` indicating simple concatenation as by [StringExprMacro]
 * - a function of minimum arity one that accepts an unescaped string as input.
 * - a type that extends *Accumulator*, like *StringBuilder*, which can be used to accumulate safe
 *   and unsafe parts.
 *
 * Here are the calling conventions this macro switches between and the strategies it uses to
 * syncretize the IRs.
 *
 * ## `null, false, ...exprs`
 *
 * Needs to produce a string by concatenating values.
 *
 * Converts any expressions to have a `.toString()` method call:
 * `interpolation  ->  interpolation?.toString() ?? "null"`.
 *
 * Once the expression is type-safe, replaces itself with a call to `cat` to do the actual concatenation.
 * Special case: if all the values are simple, builtin values, just replaces with the actual string content.
 *
 * ## `tag, true, ...exprs`
 *
 * If the tag is a type, we convert ...exprs to \funString form and proceed as below.
 *
 * Otherwise, we collect *exprs* into a list of literal parts and a list of interpolated expression
 * so that every i-th interpolation follows the prefix of literal parts of length i.
 *
 * ## `tagOrNull, isTagged, \funString, fn () { ... }`
 *
 * If !isTagged, rewrites to allocate a StringBuilder and accumulate there.
 *
 * If the tag is a type, assume it's a subtype of Accumulator and use that to allocate the accumulator.
 *
 * If the tag is a function or does not resolve to a type or null, use a collecting
 * accumulator that builds lists of string parts and interpolations, and then passes those two lists to the
 * tag function.
 *
 * Rewrites any statement level constructs that use [lang.temper.value.interpolateSymbol] or
 * [lang.temper.value.safeStringPartSymbol] to emit to the accumulator.
 *
 * In either case, the macro call is turned into a function call.
 *
 *     stringExpr(tagOrNull, isTagged, \funString fn() { BODY_STMTS })
 *
 *     ->
 *
 *     @stringExprDesugared fn {
 *       let accumulator#0 = ...;
 *
 *       BODY_STMTS
 *       // \interpolate  x   -> accumulator#0.accumulate(x)
 *       // \safeString "..." -> accumulator#0.accumulateKnownSafe("...")
 *
 *       let produced#0 = accumulator#0.produce();
 *
 *       // return statement depending on tag convention
 *
 *     }() // Invoke the accumulation
 *
 * If the tag was an accumulator type or was null, then produced#0 is returned.
 * If the accumulator is a collecting accumulator because the tag is not otherwise
 * known, then the return statement calls the tag with the collected lists and
 * returns the call result.
 *
 * Later processing stages may inline function calls to @stringExprDesugared operations.
 */
object StringExprMacro : BuiltinStatelessMacroValue, NamedBuiltinFun {
    override val name: String = "stringExpr"

    override val sigs: List<AnySignature>? = null

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        val args = macroEnv.args

        val isTagged = TBoolean.unpack(args.valueTree(1).valueContained!!)
        val tagExprTree = if (isTagged) { args.valueTree(0) } else { null }
        val isFunString = args.valueTree(2).symbolContained == funStringSymbol

        val argRange = 2..args.lastIndex

        // If we don't have a tag, and it's not a function string, then we can just concatenate
        // the parts.
        if (tagExprTree == null) {
            if (isFunString) {
                TODO("${macroEnv.stage} ${macroEnv.pos}: untagged isFunString")
            } else {
                tryReplaceWithString(macroEnv, argRange)?.let { return@invoke it }
                // Otherwise, we need to coerce parts to string.
                macroEnv.replaceMacroCallWith {
                    val oldCallArgs = macroEnv.call!!.children.subListToEnd(3)
                    Call(macroEnv.pos) {
                        V(macroEnv.callee.pos, vStringCatMacro)
                        for (arg in oldCallArgs) {
                            Replant(freeTree(arg))
                        }
                    }
                }
                return NotYet
            }
        }

        val tagValue = tagExprTree.valueContained
            ?: if (macroEnv.stage > Stage.SyntaxMacro ||
                tagExprTree is RightNameLeaf && tagExprTree.content is ResolvedName
            ) {
                macroEnv.evaluateTree(tagExprTree, interpMode) as? Value<*>
            } else { null }
        val tagCategory = when {
            tagValue?.typeTag == TType -> TagCategory.AccumulatorStyle
            tagValue?.typeTag == TFunction -> TagCategory.FnCallStyle
            macroEnv.stage > Stage.Define -> TagCategory.FnCallStyle
            else -> return NotYet
        }

        when (tagCategory) {
            TagCategory.FnCallStyle -> if (isFunString) {
                TODO("${macroEnv.stage} ${macroEnv.pos}: $tagExprTree $tagCategory isTagged, isFunString")
            } else {
                macroEnv.replaceMacroCallWith {
                    val call = macroEnv.call!!
                    val literalParts = mutableListOf<Tree>()
                    val interpParts = mutableListOf<Tree>()
                    val oldCallArgs = macroEnv.call!!.children.subListToEnd(3)
                    var sawInterpolateSymbol = false
                    for (arg in oldCallArgs) {
                        if (arg.symbolContained == interpolateSymbol) {
                            sawInterpolateSymbol = true
                        } else if (sawInterpolateSymbol) {
                            sawInterpolateSymbol = false
                            interpParts.add(freeTree(arg))
                            // If we have adjacent interpolations, insert empty strings.
                            // For a string expression like "${a}${b}${c}x"
                            // we should end up with interpolations [a, b, c]
                            // and literal parts ["", "", "", "x"].
                            // That way the tag can go over and zip the strings to deal with
                            // preceding literal and interpolation pairs and then unambiguously
                            // know that the last literal part follows the last interpolation.
                            while (interpParts.size > literalParts.size) {
                                literalParts.add(
                                    ValueLeaf(arg.document, arg.pos.leftEdge, vEmptyString).also {
                                        it.typeInferences = stringTypeInf
                                    },
                                )
                            }
                        } else {
                            literalParts.add(freeTree(arg))
                            // If we have multiple literal parts between two interpolations,
                            // then collapse them.  This can happen when escapes are broken out.
                            if (literalParts.size > interpParts.size + 1) {
                                val last = literalParts.removeLast() as ValueLeaf
                                val penultimate = literalParts.removeLast() as ValueLeaf
                                val two = listOf(penultimate, last)
                                literalParts.add(
                                    ValueLeaf(
                                        last.document,
                                        two.spanningPosition(last.pos),
                                        Value(
                                            two.joinToString("") { TString.unpack(it.content) },
                                            TString,
                                        ),
                                    ),
                                )
                            }
                        }
                    }
                    // See loop above
                    if (interpParts.size >= literalParts.size) {
                        literalParts.add(
                            ValueLeaf(call.document, call.pos.rightEdge, vEmptyString).also {
                                it.typeInferences = stringTypeInf
                            },
                        )
                    }

                    val callPos = call.pos
                    val interpPositions = interpParts.firstOrNull()?.let { interpParts.spanningPosition(it.pos) }
                        ?: callPos.rightEdge
                    val literalPositions = oldCallArgs.firstOrNull()?.let { oldCallArgs.spanningPosition(it.pos) }
                        ?: callPos.rightEdge

                    // Collect string parts and interpolations in separate lists.
                    Call(callPos) {
                        Replant(tagExprTree)
                        Call(literalPositions, BuiltinFuns.listifyFn) {
                            for (p in literalParts) {
                                Replant(p)
                            }
                        }
                        Call(interpPositions, BuiltinFuns.listifyFn) {
                            for (p in interpParts) {
                                Replant(p)
                            }
                        }
                    }
                }
                return NotYet
            }
            TagCategory.AccumulatorStyle -> if (isFunString) {
                TODO("${macroEnv.stage} ${macroEnv.pos}: $tagExprTree $tagCategory isTagged, isFunString")
            } else {
                TODO("${macroEnv.stage} ${macroEnv.pos}: $tagExprTree $tagCategory isTagged")
            }
        }
    }
}

private enum class TagCategory {
    FnCallStyle,
    AccumulatorStyle,
}

/**
 * Desugars to a simple string concatenation when we have the time.
 */
internal object StringCatMacro : BuiltinMacro("cat", null), SpecialFunction {
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
            return collapseToString(macroEnv, interpMode)
        }

        tryReplaceWithString(macroEnv, argRange)?.let { return@invoke it }

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

private fun collapseToString(
    macroEnv: MacroEnvironment,
    interpMode: InterpMode,
): PartialResult {
    val args = macroEnv.args
    val content = buildString {
        for (i in 0..<args.size) {
            val str: String = when (val result = args.evaluate(i, interpMode)) {
                is NotYet, is Fail -> return@collapseToString result
                TNull.value -> OutToks.nullWord.text
                is Value<*> -> {
                    if (result.typeTag == TString) {
                        TString.unpack(result)
                    } else {
                        val toStringCall = macroEnv.treeFarm.grow(args.pos(i)) {
                            buildToStringCall(result)
                        }
                        when (val toStringResult = macroEnv.evaluateTree(toStringCall, interpMode)) {
                            is NotYet, is Fail -> return@collapseToString toStringResult
                            is Value<*> ->
                                TString.unpackOrNull(toStringResult)
                                    // TODO: explain that toString did not return a string
                                    ?: return@collapseToString Fail
                        }
                    }
                }
            }
            append(str)
        }
    }
    return Value(content, TString)
}

private fun tryReplaceWithString(macroEnv: MacroEnvironment, argRange: IntRange): PartialResult? {
    val args = macroEnv.args
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

    return null
}

val vStringExprMacro = Value(StringExprMacro)
val vStringCatMacro = Value(StringCatMacro)
private val vEmptyString = Value("", TString)
private val stringTypeInf = BasicTypeInferences(WellKnownTypes.stringType, listOf())
