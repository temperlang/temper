package lang.temper.lexer

import kotlin.test.Test
import kotlin.test.assertEquals

class AngleBracketInfoTest {
    @Test
    fun angleBracketInfoSplitting() {
        for (
        (tokenText, want) in listOf(
            "<" to AngleBracketInfo(1, 1, 0),
            "</" to AngleBracketInfo(1, 2, 0),
            "<=" to AngleBracketInfo.zeros,
            "<<" to AngleBracketInfo.zeros,
            "<*" to AngleBracketInfo(1, 2, 0),
            "<*>" to AngleBracketInfo(1, 2, 1),
            "<*>>" to AngleBracketInfo(1, 2, 2),
            "*>" to AngleBracketInfo(0, 1, 1),
            ">" to AngleBracketInfo(0, 0, 1),
            ">>>" to AngleBracketInfo(0, 0, 3),
            ">>=" to AngleBracketInfo(0, 0, 2),
            "<*<" to AngleBracketInfo.zeros,
        )
        ) {
            assertEquals(
                want,
                AngleBracketInfo.of(tokenText, TokenType.Punctuation),
                "Want $want for `$tokenText`",
            )
        }
    }
}
