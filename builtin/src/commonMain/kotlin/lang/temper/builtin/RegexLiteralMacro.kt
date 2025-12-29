package lang.temper.builtin

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.value.CallTree
import lang.temper.value.Fail
import lang.temper.value.MacroEnvironment
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Planting
import lang.temper.value.TBoolean
import lang.temper.value.TEdge
import lang.temper.value.TInt
import lang.temper.value.TList
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.Tree
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.dotBuiltinName
import lang.temper.value.functionContained
import lang.temper.value.newBuiltinName
import lang.temper.value.regexLiteralBuiltinName
import lang.temper.value.valueContained
import temper.regex_parser.RegexParserGlobal
import temper.std.regex.Capture
import temper.std.regex.CodePoints
import temper.std.regex.CodeRange
import temper.std.regex.CodeSet
import temper.std.regex.Or
import temper.std.regex.RegexNode
import temper.std.regex.Repeat
import temper.std.regex.Sequence
import temper.std.regex.Special

/**
 * <!-- snippet: builtin/rgx -->
 * # `rgx`
 * Constructs a compiled regex object from a regex literal.
 *
 * ```temper inert
 * // These all do the same thing.
 * let regex1 = rgx"[ab]c";
 * let regex2 = /[ab]c/;
 * let regex3 = new Sequence([
 *   new CodeSet([new CodePoints("ab")]),
 *   new CodePoints("c"),
 * ]).compiled();
 * ```
 */
internal object RegexLiteralMacro : BuiltinMacro(regexLiteralBuiltinName.builtinKey, null) {
    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        if (interpMode != InterpMode.Partial) { return NotYet }
        // Find and validate interpolation lists.
        val args = macroEnv.args
        var argTrees: List<Tree>? = null
        if (args.size == 1) { // Call to interpolate()
            val call = macroEnv.call
            if (call != null) {
                // Force expanding of any interpolate and cat calls
                evaluateEagerly(macroEnv, args.valueTree(0).incoming!!)
                if (call.size == 1 + 2) { // callee and two arguments
                    argTrees = listOf(call.child(1), call.child(2))
                }
            }
        } else if (args.size == 2) {
            argTrees = (0 until args.size).map { args.valueTree(it) }
        }
        if (argTrees == null) {
            // Expected an expanded interpolate call
            return reportRegexError(macroEnv, MessageTemplate.ArityMismatch, values = listOf(2))
        }
        val (templateStrings, interpolatedValues) = argTrees.map { tree ->
            // Without full interpreter mode, we don't get always our lists, even when available.
            // TODO When supporting runtime eval, check at FunctionMacroStage for Strings, and wrap in CodePoints.
            val valueList: List<Value<*>>? =
                if (tree is CallTree && isListifyCall(tree)) {
                    val n = tree.size - 1 // non callee trees
                    val values = buildList {
                        for (i in 0 until n) {
                            val value = evaluateEagerly(macroEnv, tree.edge(i + 1))
                                as? Value<*>
                            add(value ?: break)
                        }
                    }
                    if (n == values.size) {
                        values
                    } else {
                        null
                    }
                } else {
                    tree.valueContained(TList)
                }
            valueList
                // For now, give up on anything we can't evaluate at compile time.
                ?: return@invoke reportRegexError(macroEnv)
        }
        // And we need at list one string template value and matching numbers of each.
        if (!(templateStrings.isNotEmpty() && interpolatedValues.size == templateStrings.size - 1)) {
            return reportRegexError(macroEnv)
        }
        // Loop through args to build the pattern, including any interpolation slots.
        val builder = StringBuilder()
        val slots = mutableMapOf<String, RegexNode>()
        fun tryString(values: List<Value<*>>, index: Int, build: (String) -> String): PartialResult? {
            val value = TString.unpackOrNull(values[index])
                ?: return reportRegexError(
                    macroEnv,
                    template = MessageTemplate.ExpectedValueOfType,
                    pos = macroEnv.args.valueTree(index).pos,
                    values = listOf(TString, values[index].typeTag),
                )
            builder.append(build(value))
            return null
        }
        // Start with template text.
        tryString(templateStrings, 0) { it }?.let { return@invoke it }
        for (index in interpolatedValues.indices) {
            // Then interleave interpolation values and template text.
            // TODO Also support regex objects for interpolated values.
            tryString(interpolatedValues, index) { text ->
                // This doesn't check for collision with existing slot names, but we don't
                // officially support slot syntax in our dialect anyway. It's for internal usage.
                // TODO Some more clever way to interpolate regex values?
                val slotKey = "arg$index"
                slots[slotKey] = CodePoints(text)
                "(?\$$slotKey)"
            }?.let { return@invoke it }
            tryString(templateStrings, index + 1) { it }?.let { return@invoke it }
        }
        // Try parsing it.
        val text = builder.toString()
        val regex = runCatching {
            RegexParserGlobal.parseWith(text, slots)!!
        }.getOrElse {
            return@invoke reportRegexError(macroEnv)
        }
        // Replace the macro call with regex constructor calls.
        macroEnv.replaceMacroCallWith {
            Call {
                Call {
                    Rn(dotBuiltinName)
                    buildRegex(regex, macroEnv)
                    // Also auto compile regex literals to backend regexes.
                    V(Symbol("compiled"))
                }
            }
        }
        return NotYet
    }
}

private fun Planting.buildRegex(
    regex: RegexNode,
    macroEnv: MacroEnvironment,
): UnpositionedTreeTemplate<*> {
    // Simple Java names match Temper class names here by design.
    fun Planting.makeName() = makeImportMeCall(
        macroEnv.pos,
        Symbol(regex.javaClass.simpleName),
        "std/regex",
        macroEnv,
    )

    return when (regex) {
        is Special -> makeName()
        else -> Call {
            Rn(newBuiltinName)
            makeName()
            when (regex) {
                is Capture -> {
                    V(Value(regex.name!!, TString))
                    buildRegex(regex.item, macroEnv)
                }
                is CodePoints -> V(Value(regex.value!!, TString))
                is CodeRange -> {
                    V(Value(regex.min, TInt))
                    V(Value(regex.max, TInt))
                }
                is Repeat -> {
                    buildRegex(regex.item, macroEnv)
                    V(Value(regex.min, TInt))
                    V(regex.max?.let { Value(regex.max, TInt) } ?: TNull.value)
                    V(TBoolean.value(regex.reluctant))
                }
                is CodeSet -> {
                    Call {
                        V(BuiltinFuns.vListifyFn)
                        for (item in regex.items) {
                            buildRegex(item, macroEnv)
                        }
                    }
                    V(TBoolean.value(regex.negated))
                }
                is Or -> Call {
                    V(BuiltinFuns.vListifyFn)
                    for (item in regex.items) {
                        buildRegex(item, macroEnv)
                    }
                }
                is Sequence -> Call {
                    V(BuiltinFuns.vListifyFn)
                    for (item in regex.items) {
                        buildRegex(item, macroEnv)
                    }
                }
                else -> error("unexpected regex type: ${regex::class}")
            }
        }
    }
}

private fun isListifyCall(callTree: CallTree) =
    callTree.childOrNull(0)?.functionContained == BuiltinFuns.listifyFn

private fun evaluateEagerly(macroEnv: MacroEnvironment, edge: TEdge): PartialResult =
    macroEnv.evaluateEdge(edge, InterpMode.Partial)

private fun reportRegexError(
    macroEnv: MacroEnvironment,
    template: MessageTemplateI? = null,
    pos: Position? = null,
    values: List<Any>? = null,
): PartialResult =
    if (macroEnv.stage >= Stage.Define) {
        val problem = LogEntry(
            Log.Error,
            template ?: MessageTemplate.UnrecognizedToken,
            pos ?: macroEnv.pos,
            values ?: emptyList(),
        )
        problem.logTo(macroEnv.logSink)
        macroEnv.replaceMacroCallWithErrorNode(problem)
        Fail(problem)
    } else {
        NotYet
    }
