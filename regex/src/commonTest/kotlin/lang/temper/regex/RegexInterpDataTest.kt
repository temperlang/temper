package lang.temper.regex

import lang.temper.common.TestDocumentContext
import lang.temper.common.testCodeLocation
import lang.temper.common.withRandomForTest
import lang.temper.log.Position
import lang.temper.name.ParsedName
import lang.temper.name.Symbol
import lang.temper.type.Abstractness
import lang.temper.type.TypeShapeImpl
import lang.temper.value.Document
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.TBoolean
import lang.temper.value.TClass
import lang.temper.value.TInt
import lang.temper.value.TList
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.Value
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

class RegexInterpDataTest {
    @Test
    fun roundtripTemperObjects() = withRandomForTest { random ->
        repeat(50) {
            val pattern = random.nextRegex()
            val document = Document(TestDocumentContext())
            val temperPattern = document.packRegex(pattern)
            val kotlinAgain = unpackRegex(temperPattern)
            assertEquals(pattern, kotlinAgain)
        }
    }
}

private fun Document.packRegex(regex: RegexNode): Value<*> {
    return when (regex) {
        // Aggregate
        is Capture -> makeCapture(regex)
        is CodePoints -> makeCodePoints(regex)
        is CodeRange -> makeCodeRange(regex)
        is CodeSet -> makeCodeSet(regex)
        is Or -> makeOr(regex)
        is Repeat -> makeRepeat(regex)
        is Seq -> makeSequence(regex)
        // Special
        Begin -> makeEmpty(Begin::class)
        Dot -> makeEmpty(Dot::class)
        End -> makeEmpty(End::class)
        GraphemeCluster -> makeEmpty(GraphemeCluster::class)
        WordBoundary -> makeEmpty(WordBoundary::class)
        // SpecialSet
        Digit -> makeEmpty(Digit::class)
        Space -> makeEmpty(Space::class)
        Word -> makeEmpty(Word::class)
    }
}

// Regex

private fun Document.makeCapture(regex: Capture) = makeValue(
    Capture::class,
    mapOf(
        Capture::name.name to regex.name.toValue(),
        Capture::item.name to packRegex(regex.item),
    ),
)

private fun Document.makeCodePoints(regex: CodePoints) = makeValue(
    CodePoints::class,
    mapOf(
        CodePoints::value.name to regex.value.toValue(),
    ),
)

private fun Document.makeCodeRange(regex: CodeRange) = makeValue(
    CodeRange::class,
    mapOf(
        CodeRange::min.name to regex.min.toValue(),
        CodeRange::max.name to regex.max.toValue(),
    ),
)

private fun Document.makeCodeSet(regex: CodeSet) = makeValue(
    CodeSet::class,
    mapOf(
        CodeSet::items.name to regex.items.map { packRegex(it) }.toValue(),
        CodeSet::negated.name to TBoolean.value(regex.negated),
    ),
)

private fun Document.makeOr(regex: Or) = makeValue(
    Or::class,
    mapOf(
        Or::items.name to regex.items.map { packRegex(it) }.toValue(),
    ),
)

private fun Document.makeRepeat(regex: Repeat) = makeValue(
    Repeat::class,
    mapOf(
        Repeat::item.name to packRegex(regex.item),
        Repeat::min.name to regex.min.toValue(),
        Repeat::max.name to regex.max.toValue(),
        Repeat::reluctant.name to TBoolean.value(regex.reluctant),
    ),
)

private fun Document.makeSequence(regex: Seq) = makeValue(
    "Sequence",
    mapOf(
        Seq::items.name to regex.items.map { packRegex(it) }.toValue(),
    ),
)

// Generic

private fun List<Value<*>>.toValue() = Value(this, TList)

private fun Int.toValue() = Value(this, TInt)

private fun Int?.toValue() = when (this) {
    null -> TNull.value
    else -> toValue()
}

private fun String.toValue() = Value(this, TString)

private fun Document.makeClass(kclass: KClass<*>) = makeClass(kclass.simpleName!!)

private fun Document.makeClass(name: String) = TClass(
    // We could make a type shape with members, but we don't need one.
    TypeShapeImpl(
        Position(testCodeLocation, 0, 0),
        Symbol(name),
        nameMaker.unusedSourceName(ParsedName(name)),
        Abstractness.Concrete,
        context.definitionMutationCounter,
    ),
)

private fun Document.makeEmpty(kclass: KClass<*>) = Value(makeInstance(), makeClass(kclass))

private fun Document.makeInstance(props: Map<String, Value<*>> = emptyMap()) = InstancePropertyRecord(
    // TODO: Shouldn't we be using the name from the instance's member names?
    props.map { (name, value) -> nameMaker.unusedSourceName(ParsedName(name)) to value }
        .toMap()
        .toMutableMap(),
)

private fun Document.makeValue(kclass: KClass<*>, props: Map<String, Value<*>>) =
    makeValue(kclass.simpleName!!, props)

private fun Document.makeValue(className: String, props: Map<String, Value<*>>) =
    Value(makeInstance(props), makeClass(className))
