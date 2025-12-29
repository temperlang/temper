package lang.temper.lexer

import lang.temper.common.IntRangeSet
import lang.temper.common.assertRangeSetsEqualBestEffort
import lang.temper.common.enumerateCharClassRegexBestEffort
import kotlin.test.Test

@Suppress("UnusedPrivateMember") // github.com/detekt/detekt/issues/2579
private operator fun (IntRangeSet?).minus(x: IntRangeSet?): IntRangeSet? =
    if (this != null && x != null) {
        IntRangeSet.difference(this, x)
    } else {
        null
    }

@Suppress("UnusedPrivateMember") // github.com/detekt/detekt/issues/2579
private operator fun (IntRangeSet?).plus(x: IntRangeSet?): IntRangeSet? =
    if (this != null && x != null) {
        IntRangeSet.union(this, x)
    } else {
        null
    }

class IdPartsTest {
    // Emoji sequence regexs derived from
    // http://www.unicode.org/reports/tr51/
    // Our goal is http://www.unicode.org/reports/tr51/#def_emoji_sequence with modifications as
    // noted below.

    // emoji_character := \p{Emoji}
    private val emojiCharacter = """\p{Emoji}"""

    // emoji_modifier := \p{Emoji_Modifier}
    private val emojiModifierChar = """\p{Emoji_Modifier}"""

    // emoji_modifier_base := \p{Emoji_Modifier_Base}
    private val emojiModifierBaseChar = """\p{Emoji_Modifier_Base}"""

    // emoji_modifier_sequence := emoji_modifier_base emoji_modifier
    private val emojiModifierSequenceChar = """$emojiModifierBaseChar$emojiModifierChar"""

    // emoji_presentation_selector := \x{FE0F}
    private val emojiPresentationSelectorChar = """\x{FE0F}"""

    // emoji_presentation_sequence := emoji_character emoji_presentation_selector
    private val emojiPresentationSequenceChar = """$emojiCharacter$emojiPresentationSelectorChar"""

    // emoji_keycap_sequence := [0-9#*] \x{FE0F 20E3}
    // We cannot support keycaps because they use latin characters that are needed by non-identifier
    // tokens.
    // We include them here and instead later subtract out all latin codepoints from our overly
    // expansive emoji sequence.
    private val emojiKeycapSequenceChar = """0-9#*\uFE0F\u20E3"""

    // emoji_flag_sequence := regional_indicator regional_indicator
    // regional_indicator := \p{Regional_Indicator}
    private val emojiFlagSequenceChar = """\p{Regional_Indicator}"""

    // emoji_zwj_element :=
    //     emoji_character
    //     | emoji_presentation_sequence
    //     | emoji_modifier_sequence
    private val emojiZwjElementChar =
        """$emojiCharacter$emojiPresentationSequenceChar$emojiModifierSequenceChar"""

    // emoji_core_sequence :=
    //     emoji_character
    //     | emoji_presentation_sequence
    //     | emoji_keycap_sequence
    //     | emoji_modifier_sequence
    //     | emoji_flag_sequence
    private val emojiCoreSequenceChar =
        """$emojiCharacter$emojiPresentationSequenceChar$emojiKeycapSequenceChar""" +
            """$emojiModifierSequenceChar$emojiFlagSequenceChar"""

    // emoji_zwj_sequence := emoji_zwj_element ( ZWJ emoji_zwj_element )+
    // ZWJ (U+200D) is already in optionalContinue so we simplify this to
    private val emojiZwjSequenceChar = emojiZwjElementChar

    // emoji_tag_sequence := tag_base tag_spec tag_end
    // tag_base           := emoji_character
    //     | emoji_modifier_sequence
    //     | emoji_presentation_sequence
    private val tagBaseChar = """$emojiCharacter$emojiModifierSequenceChar$emojiPresentationSequenceChar"""

    // tag_spec           := [\x{E0020}-\x{E007E}]+
    private val tagSpecChar = """\x{E0020}-\x{E007E}"""

    // tag_end            := \x{E007F}
    private val tagEndChar = """\x{E007F}"""
    private val emojiTagSequenceChar = """$tagBaseChar$tagSpecChar$tagEndChar"""

    // emoji_sequence := emoji_core_sequence | emoji_zwj_sequence | emoji_tag_sequence
    private val emojiSequenceChar =
        """$emojiCoreSequenceChar$emojiZwjSequenceChar$emojiTagSequenceChar"""

    // Includes underscore from https://unicode.org/reports/tr31/#Table_Optional_Start but not '$'
    private val optionalStart = "_"

    // Includes most of https://unicode.org/reports/tr31/#Table_Optional_Continue
    private val optionalContinue = """$optionalStart\u05F3\u200D"""

    // Includes non-latin chars from https://unicode.org/reports/tr31/#Table_Optional_Medial
    private val optionalMedial = """\u00b7\u058A\u05F4\u0F0B\u200C\u2010\u2019\u2027\u30A0\u30FB"""

    // Character class regexs copied from
    // https://unicode.org/reports/tr31/#Table_Lexical_Classes_for_Identifiers
    // CAVEAT: ICU4J does not recognize \p{Other_ID_Start} or \p{Other_ID_Continue}
    // so I replaced them with the non-Other_ properties below.  I believe ID_Start is defined based
    // on Other_ID_Start so this should be overly inclusive.
    private val starts = """[\p{L}\p{Nl}\p{ID_Start}$optionalStart]"""
    private val continues =
        """[\p{ID_Start}\p{Mn}\p{Mc}\p{Nd}\p{Pc}\p{ID_Continue}$optionalContinue]"""
    private val medials = """[$optionalMedial]"""

    private val startAndContinueExclusions = """[\p{Pattern_Syntax}\p{Pattern_White_Space}]"""

    private val nonLatinEmojis =
        enumerateCharClassRegexBestEffort("[$emojiSequenceChar]") -
            IntRangeSet.new(0..255)

    @Test
    fun testStartChars() {
        val included = enumerateCharClassRegexBestEffort(
            starts,
        )
        val excluded = enumerateCharClassRegexBestEffort(
            startAndContinueExclusions,
        )
        assertRangeSetsEqualBestEffort(
            want = (included + nonLatinEmojis) - excluded,
            input = IdParts.Start,
        )
    }

    @Test
    fun testContinueChars() {
        val included = enumerateCharClassRegexBestEffort(
            continues,
        )
        val excluded = enumerateCharClassRegexBestEffort(
            startAndContinueExclusions,
        )
        assertRangeSetsEqualBestEffort(
            want = (included + nonLatinEmojis) - excluded,
            input = IdParts.Continue,
        )
    }

    @Test
    fun testMedialChars() {
        val included = enumerateCharClassRegexBestEffort(
            medials,
        )
        assertRangeSetsEqualBestEffort(
            want = included,
            input = IdParts.Medial,
        )
    }
}
