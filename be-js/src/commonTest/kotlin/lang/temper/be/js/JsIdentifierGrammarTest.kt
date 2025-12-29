package lang.temper.be.js

import lang.temper.common.IntRangeSet
import lang.temper.common.assertRangeSetsEqualBestEffort
import lang.temper.common.enumerateCharClassRegexBestEffort
import kotlin.test.Test
import kotlin.test.assertTrue

class JsIdentifierGrammarTest {
    /*
    IdentifierStart::
        UnicodeIDStart
        $
        _
        \UnicodeEscapeSequence
    IdentifierPart::
        UnicodeIDContinue
        $
        \UnicodeEscapeSequence
        <ZWNJ>
        <ZWJ>
    UnicodeIDStart::
        any Unicode code point with the Unicode property “ID_Start”
    UnicodeIDContinue::
        any Unicode code point with the Unicode property “ID_Continue”
     */

    private val underscore = IntRangeSet.new(IntRange('_'.code, '_'.code))
    private val dollar = IntRangeSet.new(IntRange('$'.code, '$'.code))
    private val zwnj = IntRangeSet.new(IntRange('\u200c'.code, '\u200c'.code))
    private val zwj = IntRangeSet.new(IntRange('\u200d'.code, '\u200d'.code))

    @Test
    fun extras() {
        for (cp in listOf('_'.code, '$'.code)) {
            assertTrue(cp in JsIdentifierGrammar.idStart)
            assertTrue(cp in JsIdentifierGrammar.idContinue)
        }
    }

    @Test
    fun extraContinues() {
        for (cp in listOf(0x200C, 0x200D)) {
            assertTrue(cp in JsIdentifierGrammar.idContinue, cp.toString(16))
            assertTrue(cp !in JsIdentifierGrammar.idStart, cp.toString(16))
        }
    }

    @Test
    fun aToZ() {
        // The characters immediately before 'a','A', and immediately after 'z','Z' are not
        // identifier parts.
        for (set in listOf(JsIdentifierGrammar.idStart, JsIdentifierGrammar.idContinue)) {
            for (range in listOf('A'..'Z', 'a'..'z')) {
                for (c in range) {
                    assertTrue(c.code in set)
                }
                assertTrue((range.first.code - 1) !in set)
                assertTrue((range.last.code + 1) !in set)
            }
        }
    }

    @Test
    fun idStart() {
        assertTrue(!underscore.isEmpty())
        assertRangeSetsEqualBestEffortIfAllAvailable(
            want = listOf(
                underscore,
                dollar,
                enumerateCharClassRegexBestEffort("[[:Id_Start:]]"),
            ),
            input = JsIdentifierGrammar.idStart,
        )
    }

    @Test
    fun idContinue() {
        assertRangeSetsEqualBestEffortIfAllAvailable(
            want = listOf(
                underscore,
                dollar,
                zwj,
                zwnj,
                enumerateCharClassRegexBestEffort("[[:Id_Continue:]]"),
            ),
            input = JsIdentifierGrammar.idContinue,
        )
    }

    private fun assertRangeSetsEqualBestEffortIfAllAvailable(
        want: List<IntRangeSet?>,
        input: IntRangeSet,
    ) {
        val wantNotNull = want.filterNotNull()
        if (want.size == wantNotNull.size) {
            assertRangeSetsEqualBestEffort(
                want = IntRangeSet.union(wantNotNull),
                input = input,
            )
        }
    }
}
