package lang.temper.builtin

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.common.Log
import lang.temper.common.subListToEnd
import lang.temper.env.InterpMode
import lang.temper.format.OutToks
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.spanningPosition
import lang.temper.name.ResolvedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.type.DotHelper
import lang.temper.type.DotMember
import lang.temper.type.ExternalBind
import lang.temper.type.ExternalGet
import lang.temper.type.InvalidType
import lang.temper.type.StaticType
import lang.temper.type.WellKnownTypes
import lang.temper.type.canBeNull
import lang.temper.type2.AnySignature
import lang.temper.type2.Signature2
import lang.temper.value.BasicTypeInferences
import lang.temper.value.BlockTree
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.CallTree
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.FunctionSpecies
import lang.temper.value.IfThenElse
import lang.temper.value.LinearFlow
import lang.temper.value.MacroEnvironment
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.Panic
import lang.temper.value.PartialResult
import lang.temper.value.Planting
import lang.temper.value.RightNameLeaf
import lang.temper.value.SpecialFunction
import lang.temper.value.TBoolean
import lang.temper.value.TFunction
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.TSymbol
import lang.temper.value.TType
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.and
import lang.temper.value.freeTree
import lang.temper.value.funStringSymbol
import lang.temper.value.interpolateSymbol
import lang.temper.value.isErrorCall
import lang.temper.value.newBuiltinName
import lang.temper.value.outTypeSymbol
import lang.temper.value.rawBuiltinName
import lang.temper.value.safeStringPartSymbol
import lang.temper.value.symbolContained
import lang.temper.value.toStringDotName
import lang.temper.value.typeForValue
import lang.temper.value.typeSymbol
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
 * In either case, the macro call is turned into a block expression.
 *
 *     stringExpr(tagOrNull, isTagged, \funString fn() { BODY_STMTS })
 *
 *     ->
 *
 *     do {
 *       let accumulator#0 = ...;  // If the tag is a type, e.g. Tag, then it's just `new Tag()`
 *
 *       BODY_STMTS
 *       // \interpolate  x   -> accumulator#0.append(x)
 *       // \safeString "..." -> accumulator#0.appendSafe("...")
 *
 *       accumulator#0.accumulated;
 *     }
 *
 * If the tag was an accumulator type or was null, then the `.accumulated` content is the result.
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
        val isFunString = args.keyTree(2)?.symbolContained == funStringSymbol
        val funTree = if (isFunString) {
            // The parser turns string expressions with embedded statement fragments into
            // a function body.
            args.valueTree(2) as? FunTree ?: return NotYet
        } else {
            null
        }

        val argRange = 2..args.lastIndex

        if (funTree != null && funTree.parts?.formals?.isEmpty() == true) {
            // If it doesn't have an argument, then we haven't processed it yet to
            // direct interpolations and string literal appends to the buffer.
            pointAppendsAtAccumulator(funTree, isTagged = isTagged)
        }

        // If we don't have a tag, and it's not a function string, then we can just concatenate
        // the parts.
        if (tagExprTree == null) {
            if (funTree != null) {
                // @funString fn (accumulator: StringBuilder): Void { ... }
                // ->
                // do {
                //   let accumulator = new StringBuilder();
                //   ...;
                //   accumulator.toString()
                // }
                inlineFunStringBody(
                    macroEnv,
                    funTree,
                    plantAccumulatorType = {
                        V(Types.vStringBuilder)
                    },
                    plantResult = { bufferName ->
                        Call {
                            Call {
                                V(Value(DotHelper(ExternalBind, toStringDotName)))
                                Rn(bufferName)
                            }
                        }
                    },
                )
            } else {
                tryReplaceWithString(macroEnv, argRange)?.let { return@invoke it }
                // Otherwise, we need to coerce parts to string.
                macroEnv.replaceMacroCallWith {
                    val oldCallArgs = macroEnv.call!!.children.subListToEnd(INDEX_NON_FUN_STRING_ARGS)
                    Call(macroEnv.pos) {
                        V(macroEnv.callee.pos, BuiltinFuns.vStrCatFn)
                        for (arg in oldCallArgs) {
                            freeTree(arg)
                            if (arg.isStringValueLeaf) {
                                Replant(arg)
                            } else {
                                Call(arg.pos, CoerceToString) {
                                    Replant(arg)
                                }
                            }
                        }
                    }
                }
            }
            return NotYet
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
                // Implement collecting accumulator as discussed above.
                // The collecting accumulator builds a list of literal parts, and a list of
                // non-literal parts.
                //
                // TODO: something like
                //
                //     class Collector<INTERPOLATION, OUT>() {
                //      private stringParts: ListBuilder<String> = new ListBuilder<String>();
                //      private interpolations: ListBuilder<INTERPOLATION> = new ListBuilder<INTERPOLATION>();
                //      public append(x: INTERPOLATION): Void { /* insert empties until same length as interpolations */ interpolations.add(x); }
                //      public appendSafe(x: String): Void { stringParts.add(x); }
                //
                //      public applyTo(tagFunction: fn (List<String>, List<INTERPOLATION>): OUT) {
                //        /* insert empties until stringParts one longer than interpolations */
                //        tagFunction(stringParts.toList(), interpolations.toList())
                //      }
                //    }
                //
                //    let adaptTagFunction<INTERPOLATION, OUT>(
                //      tagFunction: fn(List<String>, List<INTERPOLATION>): OUT,
                //      stringExprFn: fn(Collector<INTERPOLATION, OUT>): Void,
                //    ): OUT {
                //      let collector = new Collector<INTERPOLATION, OUT>();
                //      stringExprFn(collector);
                //      collector.applyTo(tagFunction)
                //    }
                TODO("${macroEnv.stage} ${macroEnv.pos}: $tagExprTree $tagCategory isTagged, isFunString")
            } else {
                macroEnv.replaceMacroCallWith {
                    val call = macroEnv.call!!
                    val literalParts = mutableListOf<Tree>()
                    val interpParts = mutableListOf<Tree>()
                    val oldCallArgs = macroEnv.call!!.children.subListToEnd(INDEX_NON_FUN_STRING_ARGS)
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
                        Replant(freeTree(tagExprTree))
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
            TagCategory.AccumulatorStyle -> {
                var funTree = funTree
                if (funTree == null) {
                    // Convert to a funTree
                    val call = macroEnv.call!!
                    val partIndices = INDEX_NON_FUN_STRING_ARGS until call.size
                    val trees = macroEnv.call!!.children.subListToEnd(partIndices.first).toList()
                    val pos = call.pos
                    funTree = call.document.treeFarm.grow(call.pos) {
                        val accumulator = macroEnv.nameMaker.unusedTemporaryName("accumulator")
                        Fn(pos.leftEdge) {
                            Decl(accumulator)
                            V(typeSymbol)
                            V(tagValue as Value<*>)
                            V(outTypeSymbol)
                            V(Types.vVoid)
                            Block {
                                val pending = mutableListOf<Tree>()
                                fun flush() {
                                    if (pending.isEmpty()) { return }
                                    val toFlush = pending.toList()
                                    pending.clear()
                                    val pos = toFlush.spanningPosition(toFlush.first().pos)
                                    Call(pos) {
                                        Call(pos.leftEdge) {
                                            V(Value(DotHelper(ExternalBind, appendSafeDotName)))
                                            Rn(accumulator)
                                        }
                                        if (toFlush.size == 1) {
                                            Replant(freeTree(toFlush.first()))
                                        } else {
                                            Call(pos, BuiltinFuns.vStrCatFn) {
                                                for (t in toFlush) {
                                                    Replant(freeTree(t))
                                                }
                                            }
                                        }
                                    }
                                }
                                var lastWasInterpolation = false
                                for (t in trees) {
                                    if (!lastWasInterpolation && t.symbolContained == interpolateSymbol) {
                                        flush()
                                        lastWasInterpolation = true
                                    } else if (lastWasInterpolation) {
                                        lastWasInterpolation = false
                                        Call(t.pos) {
                                            Call(t.pos.leftEdge) {
                                                V(Value(DotHelper(ExternalBind, appendDotName)))
                                                Rn(accumulator)
                                            }
                                            Replant(freeTree(t))
                                        }
                                    } else {
                                        pending.add(t)
                                    }
                                }
                                flush()
                            }
                        }
                    }
                }

                // tag, fn (acc) { ... }
                // ->
                // do {
                //   let acc = new tag();
                //   ...
                //   acc.accumulated
                // }
                inlineFunStringBody(
                    macroEnv,
                    funTree,
                    plantAccumulatorType = {
                        Replant(freeTree(tagExprTree))
                    },
                    plantResult = { accumulatorName ->
                        Call(funTree.pos.rightEdge) {
                            V(Value(DotHelper(ExternalGet, accumulatedDotName)))
                            Rn(accumulatorName)
                        }
                    },
                )
                return NotYet
            }
        }
    }

    private fun inlineFunStringBody(
        macroEnv: MacroEnvironment,
        funTree: FunTree,
        plantAccumulatorType: Planting.() -> Unit,
        plantResult: Planting.(TemperName) -> Unit,
    ) {
        val fnParts = funTree.parts ?: return
        macroEnv.replaceMacroCallWith {
            Block(macroEnv.pos) {
                val accumulatorDecl = fnParts.formals[0]
                val accumulatorParts = accumulatorDecl.parts!!
                val accumulatorName = accumulatorParts.name.content
                val body = fnParts.body
                for (formal in fnParts.formals) {
                    Replant(freeTree(formal))
                }
                Call(body.pos.leftEdge, BuiltinFuns.setLocalFn) {
                    Ln(accumulatorName)
                    Call(body.pos.leftEdge) {
                        Rn(newBuiltinName)
                        plantAccumulatorType()
                    }
                }
                Replant(freeTree(body))
                plantResult(accumulatorName)
            }
        }
    }

    private const val INDEX_NON_FUN_STRING_ARGS = 3 // callee, tag, isFunString=false, ...args
}

private enum class TagCategory {
    FnCallStyle,
    AccumulatorStyle,
}

/**
 * We need to rewrite interpolations and string appends in the body of a
 * function created from a string expression with embedded statement fragments.
 * Those interpolations and appends need to point to a particular buffer or
 * accumulator.
 */
private fun pointAppendsAtAccumulator(funTree: FunTree, isTagged: Boolean) {
    val accumulatorName = funTree.document.nameMaker.unusedTemporaryName("accumulator")
    val body = funTree.parts?.body ?: return

    // Inserting the accumulator argument means we will not re-enter this function
    funTree.insert(at = 0) {
        Decl(funTree.pos.leftEdge, accumulatorName) {
            if (!isTagged) {
                // We know it's a StringBuilder
                V(typeSymbol)
                V(Types.vStringBuilder)
            }
            // Otherwise, StringExprMacro will insert its type once it has a tag category
        }
        V(outTypeSymbol)
        V(Types.vVoid)
    }

    val vAppendDotHelper = Value(DotHelper(ExternalBind, appendDotName))
    val vAppendSafeDotHelper = if (isTagged) {
        // Accumulators have separate append and appendSafe methods
        Value(DotHelper(ExternalBind, appendSafeDotName))
    } else {
        // StringBuilders can just use the same one.
        // We also need an implicit `?.toString() ?: "null"`
        // TODO: Maybe separate out that stuff from StringCatMacro into its own thing that is
        // applied to every value interpolation.
        vAppendDotHelper
    }

    // Accumulate a list of edits then play them in reverse order to avoid
    // colliding edits.
    data class Edit(
        val parent: BlockTree,
        val range: IntRange,
        val performEdit: Planting.() -> Unit,
    )
    val edits = buildList {
        TreeVisit.startingAt(body)
            .forEach { t ->
                if (t is FunTree && t.parts?.metadataSymbolMap?.containsKey(funStringSymbol) == true) {
                    // Walk the body but do not descend into other funStrings.
                    // They'll have their own accumulatorName.
                    return@forEach VisitCue.SkipOne
                }

                if (t is BlockTree && t.flow is LinearFlow) {
                    var wroteBlock = false
                    var i = 0
                    val limit = t.size - 1
                    while (i < limit) {
                        val child = t.child(i)
                        var nextI = i + 1
                        val next = t.child(nextI)
                        if (child is ValueLeaf) {
                            val edit = when (TSymbol.unpackOrNull(child.content)) {
                                safeStringPartSymbol -> {
                                    if (!wroteBlock) {
                                        wroteBlock = true
                                    }
                                    val parts = buildList {
                                        // Combine adjacent \safeStringPartSymbols
                                        // which often come from embedded escape sequences
                                        add(next)
                                        while (nextI + 2 <= limit) {
                                            val possibleSymbol = t.child(nextI + 1)
                                            if (isRemCall(possibleSymbol)) {
                                                nextI += 1
                                                continue
                                            }
                                            if (possibleSymbol !is ValueLeaf ||
                                                TSymbol.unpackOrNull(possibleSymbol.content) != safeStringPartSymbol
                                            ) {
                                                break
                                            }
                                            add(t.child(nextI + 2))
                                            nextI += 2
                                        }
                                    }
                                    Edit(t, i..nextI) {
                                        // acc.appendSafe("...")
                                        val pos = parts.spanningPosition(next.pos)
                                        Call(pos) {
                                            Call(pos.leftEdge) {
                                                V(vAppendSafeDotHelper)
                                                Rn(accumulatorName)
                                            }
                                            if (parts.size == 1) {
                                                Replant(parts[0])
                                            } else {
                                                Call(pos, BuiltinFuns.vStrCatFn) {
                                                    parts.forEach { Replant(it) }
                                                }
                                            }
                                        }
                                    }
                                }
                                interpolateSymbol -> Edit(t, i..i + 1) {
                                    // acc.append(expr)
                                    Call(next.pos) {
                                        Call(next.pos.leftEdge) {
                                            V(vAppendDotHelper)
                                            Rn(accumulatorName)
                                        }
                                        if (isTagged) {
                                            Replant(next)
                                        } else {
                                            Call(next.pos, CoerceToString) {
                                                Replant(next)
                                            }
                                        }
                                    }
                                }
                                else -> null
                            }
                            if (edit != null) {
                                add(edit)
                            }
                        }
                        i = nextI
                    }
                }

                VisitCue.Continue
            }
            .visitPreOrder()
    }

    for (edit in edits.asReversed()) {
        val (block, rangeToReplace, performEdit) = edit
        block.replace(rangeToReplace, performEdit)
    }
}

val appendSafeDotName = DotMember(Symbol("appendSafe"))
val appendDotName = DotMember(Symbol("append"))
val accumulatedDotName = DotMember(Symbol("accumulated"))

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
                    buildStringifyCall(arg, argType)
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

internal object CoerceToString : SpecialFunction, BuiltinMacro("str", null) {
    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        val args = macroEnv.args

        if (args.size != 1) {
            val problem = LogEntry(
                Log.Error,
                MessageTemplate.ArityMismatch,
                macroEnv.pos,
                listOf(1),
            )
            if (interpMode == InterpMode.Partial) {
                macroEnv.replaceMacroCallWithErrorNode(problem)
            }
            return Fail(problem)
        }

        return when (interpMode) {
            InterpMode.Full -> {
                args.evaluate(0, interpMode).and {
                    stringify(it, macroEnv, interpMode, args.pos(0))
                }
            }
            InterpMode.Partial -> {
                val arg = args.valueTree(0)
                val type = arg.typeInferences?.type
                if (type != null) {
                    macroEnv.replaceMacroCallWith {
                        buildStringifyCall(freeTree(arg), type)
                    }
                } else if (macroEnv.stage == Stage.GenerateCode) {
                    val problem = LogEntry(
                        Log.Error,
                        MessageTemplate.InternalErrorMacroNotErased,
                        macroEnv.pos,
                        listOf(this.name),
                    )
                    problem.logTo(macroEnv.logSink)
                    macroEnv.replaceMacroCallWithErrorNode(problem)
                }
                arg.valueContained?.let {
                    try {
                        stringify(it, macroEnv, interpMode, arg.pos) as? Value<*>
                    } catch (_: Panic) {
                        null
                    }
                } ?: NotYet
            }
        }
    }

    override val functionSpecies: FunctionSpecies get() = FunctionSpecies.Special

    override val sigs: List<Signature2> = listOf(
        Signature2(
            returnType2 = WellKnownTypes.stringType2,
            hasThisFormal = false,
            requiredInputTypes = listOf(WellKnownTypes.anyValueOrNullType2),
        ),
    )
}

private fun Planting.buildToStringCall(subject: Value<*>) =
    Call {
        Call {
            V(Value(DotHelper(memberAccessor = ExternalBind, member = toStringDotName)))
            V(subject)
        }
    }

private fun Planting.buildToStringCall(subject: Tree) =
    Call {
        Call {
            V(Value(DotHelper(memberAccessor = ExternalBind, member = toStringDotName)))
            Replant(subject)
        }
    }

private fun Planting.buildStringifyCall(arg: Tree, argType: StaticType?) {
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
                    arg.document.treeFarm.grow(subject.pos) {
                        Call(BuiltinFuns.vNotNullFn) {
                            Replant(subject)
                        }
                    },
                )
            },
        )
        when (arg) {
            is ValueLeaf, is NameLeaf -> plantNullSafeCall(freeTree(arg), arg.copy())
            else -> Block(arg.pos) {
                val doc = arg.document
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

private fun collapseToString(
    macroEnv: MacroEnvironment,
    interpMode: InterpMode,
): PartialResult {
    val args = macroEnv.args
    val content = buildString {
        for (i in 0..<args.size) {
            val str: String = when (val result = args.evaluate(i, interpMode)) {
                is NotYet, is Fail -> return@collapseToString result
                is Value<*> -> when (
                    val stringed = stringify(result, macroEnv, interpMode, args.pos(i))
                ) {
                    is Value<*> -> TString.unpack(stringed)
                    else -> return@collapseToString stringed
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

private fun stringify(
    subject: Value<*>,
    macroEnv: MacroEnvironment,
    interpMode: InterpMode,
    pos: Position,
): PartialResult =
    if (subject.typeTag == TString) {
        subject
    } else if (subject == TNull.value) {
        Value(OutToks.nullWord.text, TString)
    } else {
        val toStringCall = macroEnv.treeFarm.grow(pos) {
            buildToStringCall(subject)
        }
        when (val toStringResult = macroEnv.evaluateTree(toStringCall, interpMode)) {
            is NotYet, is Fail -> toStringResult
            is Value<*> -> if (toStringResult.typeTag == TString) {
                toStringResult
            } else {
                // TODO: explain that toString did not return a string
                Fail
            }
        }
    }

val vStringExprMacro = Value(StringExprMacro)
val vStringCatMacro = Value(StringCatMacro)
private val vEmptyString = Value("", TString)
private val stringTypeInf = BasicTypeInferences(WellKnownTypes.stringType, listOf())

val Tree.isStringValueLeaf get() = this is ValueLeaf && this.content.typeTag == TString
