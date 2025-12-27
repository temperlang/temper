package lang.temper.docgen.transformations

import lang.temper.docgen.SimpleCodeFragment
import kotlin.test.Test
import kotlin.test.assertEquals

// This doesn't test the code transformer itself since writing a good fake compiler seems really hard and other
// strategies would be quite brittle and this seems like it will be high churn
class MatchCurliesTest {
    @Test
    fun matchCurliesNoMatchesLeft() {
        val fragments = listOf(SimplestCodeFragment("{"), SimplestCodeFragment(("{")))

        val result = matchCurlies(fragments)

        result.assertMatchedCurlies()
        assertEquals(emptyList(), result)
    }

    @Test
    fun matchCurliesSingle() {
        val fragments = listOf(SimplestCodeFragment("{}"), SimplestCodeFragment(("")))

        val result = matchCurlies(fragments)

        result.assertMatchedCurlies()
        assertEquals(listOf(fragments.first()), result)
    }

    @Test
    fun matchCurliesPair() {
        val fragments = listOf(SimplestCodeFragment("{"), SimplestCodeFragment(("}")))

        val result = matchCurlies(fragments)

        result.assertMatchedCurlies()
        assertEquals(fragments, result)
    }

    @Test
    fun matchCurliesTriple() {
        val fragments = listOf(SimplestCodeFragment("{"), SimplestCodeFragment("foo"), SimplestCodeFragment(("}")))

        val result = matchCurlies(fragments)

        result.assertMatchedCurlies()
        assertEquals(fragments, result)
    }

    @Test
    fun matchCurliesSkipOne() {
        val fragments = listOf(SimplestCodeFragment("foo"), SimplestCodeFragment(("{}")))

        val result = matchCurlies(fragments)

        result.assertMatchedCurlies()
        assertEquals(listOf(fragments.last()), result)
    }

    @Test
    fun matchCurliesNegative() {
        val fragments = listOf(SimplestCodeFragment(("{}}")))

        val result = matchCurlies(fragments)

        result.assertMatchedCurlies()
        assertEquals(emptyList(), result)
    }
}

fun List<SimpleCodeFragment>.assertMatchedCurlies() {
    val opens = this.countOf('{')
    val closes = this.countOf('}')

    assert(opens == closes)
}

fun List<SimpleCodeFragment>.countOf(char: Char): Int {
    return this.sumOf { it.countOf(char) }
}

data class SimplestCodeFragment(
    override val sourceText: CharSequence,
    override val isTemperCode: Boolean = true,
) : SimpleCodeFragment
