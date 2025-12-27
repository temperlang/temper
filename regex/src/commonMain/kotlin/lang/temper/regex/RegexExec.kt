package lang.temper.regex

import lang.temper.builtin.StringIndexSupport
import lang.temper.env.InterpMode
import lang.temper.interp.packValue
import lang.temper.name.SourceName
import lang.temper.name.TemperName
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.Fail
import lang.temper.value.InstancePropertyMap
import lang.temper.value.InterpreterCallback
import lang.temper.value.PartialResult
import lang.temper.value.Stayless
import lang.temper.value.TBoolean
import lang.temper.value.TBoolean.value
import lang.temper.value.TClass
import lang.temper.value.TFunction
import lang.temper.value.TList
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.unpackOrFail
import lang.temper.value.void

/** The signature should be provided by the Temper source declaration. */
sealed class PureSigFn(sig: Signature2) : CallableValue, Stayless {
    override val isPure get() = true
    override val sigs = listOf(sig)
}

/** Just a way to store an opaque value as a [Value]. TODO(tjp, regex): Is there a better way? */
private class OpaqueFn<T>(val value: T) : CallableValue, Stayless {
    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode) = void
    override val isPure: Boolean get() = true
    override val sigs get() = opaqueSigs

    companion object {
        private val opaqueSigs = listOf(
            Signature2(WellKnownTypes.voidType2, hasThisFormal = false, listOf()),
        )
    }
}

private class RegexPlus(val regex: Regex, val groupNames: List<String>)

sealed class RegexCompiledBooleanFn(sig: Signature2) : PureSigFn(sig) {
    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        val regex = ((TFunction.unpack(args[1]) as OpaqueFn<*>).value as RegexPlus).regex
        val text = TString.unpack(args[2])
        return value(test(regex, text))
    }

    abstract fun test(regex: Regex, text: String): Boolean
}

class RegexCompiledFindFn(sig: Signature2) : PureSigFn(sig) {
    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        val plus = (TFunction.unpack(args[1]) as OpaqueFn<*>).value as RegexPlus
        val text = TString.unpack(args[2])

        @Suppress("MagicNumber")
        val begin = StringIndexSupport.stringIndexTClass.unpackOrFail(args, 3, cb, interpMode) {
            return@invoke it
        }.let { StringIndexSupport.unpackStringIndex(it) }
        val rawMatch = plus.regex.find(text, begin) ?: return Fail
        val match = rawMatch.convert(groupNames = plus.groupNames)
        @Suppress("MagicNumber")
        return packValue(source = match, ref = args[4].get("match"), cb = cb)
    }
}

private fun Value<*>.get(name: String): Value<*> {
    val klass = typeTag as TClass
    val record = klass.unpack(this)
    val fullName = klass.typeShape.properties
        .first { (it.name as SourceName).baseName.nameText == name }
        .name as SourceName
    return record.properties.getValue(fullName)
}

class RegexCompiledFoundFn(sig: Signature2) : RegexCompiledBooleanFn(sig) {
    override fun test(regex: Regex, text: String) = regex.containsMatchIn(text)
}

class RegexCompiledReplaceFn(sig: Signature2) : PureSigFn(sig) {
    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        val plus = (TFunction.unpack(args[1]) as OpaqueFn<*>).value as RegexPlus
        val text = TString.unpack(args[2])

        @Suppress("MagicNumber")
        val format = args[3]
        var failed = false
        val result = plus.regex.replace(text) { rawMatch ->
            val match = rawMatch.convert(groupNames = plus.groupNames)

            @Suppress("MagicNumber")
            val groupsValue = packValue(source = match, ref = args[4].get("match"), cb = cb)
            when (val result = cb.apply(format, ActualValues.from(groupsValue), interpMode)) {
                is Value<*> -> TString.unpack(result)
                else -> {
                    failed = true
                    ""
                }
            }
        }
        if (failed) {
            return Fail
        }
        return Value(result, TString)
    }
}

class RegexCompiledSplitFn(sig: Signature2) : PureSigFn(sig) {
    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        val plus = (TFunction.unpack(args[1]) as OpaqueFn<*>).value as RegexPlus
        val text = TString.unpack(args[2])
        val parts = plus.regex.split(text).map { Value(it, TString) }
        return Value(parts, TList)
    }
}

class RegexCompileFormattedFn(sig: Signature2) : PureSigFn(sig) {
    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        // Kotlin doesn't expose the capture names, so pull them back out during compile.
        // TODO(tjp, regex): Just use java's apis directly instead of kotlin's?
        val regexData = unpackRegex(args[0])
        val groupNames = buildList {
            regexData.walk { pattern ->
                if (pattern is Capture) {
                    add(pattern.name)
                }
            }
        }
        // Now the easy part.
        val formatted = args[1].stateVector as String
        val compiled = RegexPlus(regex = Regex(formatted), groupNames = groupNames)
        return Value(OpaqueFn(compiled), TFunction)
    }
}

class RegexFormatFn(sig: Signature2) : PureSigFn(sig) {
    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        val data = unpackRegex(args[0])
        val formatted = data.formatToString(KotlinRegexFormatter)
        return Value(formatted, TString)
    }
}

data class Match(val full: Group, val groups: Map<String, Group>)

data class Group(val name: String, val value: String, val begin: StringIndex, val end: StringIndex)

data class StringIndex(val offset: Int)

private fun MatchResult.convert(groupNames: List<String>): Match {
    val groups = groups as MatchNamedGroupCollection
    val full = Group(name = "full", value = value, begin = StringIndex(range.first), end = StringIndex(range.last + 1))
    val resultGroups = buildMap {
        for (name in groupNames) {
            val group = groups[name] ?: continue
            this[name] = Group(
                name = name,
                value = group.value,
                begin = StringIndex(group.range.first),
                end = StringIndex(group.range.last + 1),
            )
        }
    }
    return Match(full = full, groups = resultGroups)
}

fun unpackRegex(value: Value<*>): RegexNode {
    return when (val type = value.typeTag) {
        is TClass -> {
            // If type-checking elsewhere validates these, then the names should be good enough.
            // TODO(tjp, regex): Can we presume type validation elsewhere?
            // TODO(tjp, regex): Any speed up? Lazily build a TypeShape identity to function map for processing?
            val state = type.unpack(value)
            when (val typeName = type.typeShape.word?.text) {
                // Aggregate
                Capture::class.simpleName -> unpackCapture(state.properties)
                CodeRange::class.simpleName -> unpackCodeRange(state.properties)
                CodePoints::class.simpleName -> unpackCodePoints(state.properties)
                CodeSet::class.simpleName -> unpackCodeSet(state.properties)
                Or::class.simpleName -> unpackOr(state.properties)
                Repeat::class.simpleName -> unpackRepeat(state.properties)
                "Sequence" -> unpackSeq(state.properties) // non-matched name for this one
                // Special
                Begin::class.simpleName -> Begin
                Dot::class.simpleName -> Dot
                End::class.simpleName -> End
                GraphemeCluster::class.simpleName -> GraphemeCluster
                WordBoundary::class.simpleName -> WordBoundary
                // Special Set
                Digit::class.simpleName -> Digit
                Space::class.simpleName -> Space
                Word::class.simpleName -> Word
                else -> TODO("Unpack pattern type: $typeName")
            }
        }
        else -> error("Unexpected value type: $type")
    }
}

private fun base(name: TemperName) = when (name) {
    is SourceName -> name.baseName.nameText
    else -> unexpectedName(name)
}

private fun unpackCapture(properties: InstancePropertyMap): Capture {
    var nameValue = ""
    var item: RegexNode = bogusRegex
    for ((name, value) in properties) {
        when (base(name)) {
            Capture::name.name -> nameValue = TString.unpack(value)
            Capture::item.name -> item = unpackRegex(value)
            else -> unexpectedName(name)
        }
    }
    return Capture(nameValue, item)
}

private fun unpackCodePoints(properties: InstancePropertyMap): CodePoints {
    // Loop on declarations seems at least as efficient as going through the TypeShape.
    // We need one or the other to get at the base name text, though.
    var text = ""
    for ((name, value) in properties) {
        when (base(name)) {
            CodePoints::value.name -> text = value.stateVector as String
            else -> unexpectedName(name)
        }
    }
    return CodePoints(text)
}

private fun unpackCodeRange(properties: InstancePropertyMap): CodeRange {
    var min = 0
    var max = 0
    for ((name, value) in properties) {
        when (base(name)) {
            CodeRange::min.name -> min = (value.stateVector as Number).toInt()
            CodeRange::max.name -> max = (value.stateVector as Number).toInt()
            else -> unexpectedName(name)
        }
    }
    return CodeRange(min = min, max = max)
}

private fun unpackCodeSet(properties: InstancePropertyMap): CodeSet {
    var items = emptyList<CodePart>()
    var negated = false
    for ((name, value) in properties) {
        when (base(name)) {
            CodeSet::items.name ->
                items = (value.stateVector as List<*>).map { unpackRegex(it as Value<*>) as CodePart }
            CodeSet::negated.name -> negated = TBoolean.unpack(value)
            else -> unexpectedName(name)
        }
    }
    return CodeSet(items = items, negated = negated)
}

private fun unpackOr(properties: InstancePropertyMap): Or {
    var items = emptyList<RegexNode>()
    for ((name, value) in properties) {
        when (base(name)) {
            Or::items.name -> items = (value.stateVector as List<*>).map { unpackRegex(it as Value<*>) }
            else -> unexpectedName(name)
        }
    }
    return Or(items)
}

private fun unpackRepeat(properties: InstancePropertyMap): Repeat {
    var item: RegexNode = bogusRegex
    var min = 0
    var max: Int? = null
    var reluctant = false
    for ((name, value) in properties) {
        when (base(name)) {
            Repeat::item.name -> item = unpackRegex(value)
            Repeat::min.name -> min = (value.stateVector as Number).toInt()
            Repeat::max.name -> max = (value.stateVector as? Number)?.toInt()
            Repeat::reluctant.name -> reluctant = value.stateVector as Boolean
            else -> unexpectedName(name)
        }
    }
    return Repeat(item = item, min = min, max = max, reluctant = reluctant)
}

private fun unpackSeq(properties: InstancePropertyMap): Seq {
    var patterns = emptyList<RegexNode>()
    for ((name, value) in properties) {
        when (base(name)) {
            Seq::items.name -> patterns = (value.stateVector as List<*>).map { unpackRegex(it as Value<*>) }
            else -> unexpectedName(name)
        }
    }
    return Seq(patterns)
}

private fun unexpectedName(name: TemperName): Nothing = error("Unexpected name: $name")

private val bogusRegex = CodePoints("")
