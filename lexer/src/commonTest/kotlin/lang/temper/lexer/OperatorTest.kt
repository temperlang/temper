package lang.temper.lexer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperatorTest {
    @Test
    fun partitionEqualsSuffixBasedOnProbablyAssignmentCheck() {
        val partition = Operator.values()
            .mapNotNull { if (it.text?.endsWith("=") == true) it.text else null }
            .toSet()
            .toList()
            .sorted()
            .partition {
                Operator.isProbablyAssignmentOperator(it, TokenType.Punctuation)
            }
        assertEquals(
            Pair(
                listOf(
                    "%=", "&&=", "&=", "*=", "+=", "-=", "/=",
                    "<<=", "=", ">>=", ">>>=", "^=", "|=", "||=",
                ),
                listOf("!=", "!==", "<=", "==", "===", ">="),
            ),
            partition,
        )

        // For symmetry between =~ and !~ in Perl.
        assertFalse(Operator.isProbablyAssignmentOperator("=~", TokenType.Punctuation))
    }

    @Test
    fun customEqualsSuffix() {
        assertTrue(Operator.isProbablyAssignmentOperator(":=", TokenType.Punctuation))
        assertTrue(Operator.isProbablyAssignmentOperator("::==", TokenType.Punctuation))
        // Usually means comparison.
        assertFalse(Operator.isProbablyAssignmentOperator("<=>", TokenType.Punctuation))
    }

    @Test
    fun unarySuccessorOperators() {
        assertTrue(Operator.isProbablyAssignmentOperator("++", TokenType.Punctuation))
        assertTrue(Operator.isProbablyAssignmentOperator("--", TokenType.Punctuation))
    }

    @Test
    fun matcherDoesNotMatchWordPrefixes() {
        assertEquals(
            emptyList(), // `input` is not an extension of `in`; that's just not how words work.
            Operator.matching(
                "${Operator.In.text}put",
                TokenType.Word,
                Operator.In.operatorType,
            ),
        )
    }

    @Test
    fun matcherDoesNotCrossOperatorTypes() {
        assertEquals(
            // Should not consider the prefix "+" since "++" is defined for other operator types.
            emptyList(),
            Operator.matching(
                "++",
                TokenType.Punctuation,
                OperatorType.Infix,
            ),
        )
    }

    @Test
    fun customCompoundOperatorMatching() {
        assertEquals(
            listOf(Operator.Eq),
            Operator.matching(
                ":=",
                TokenType.Punctuation,
                OperatorType.Infix,
            ),
        )
    }

    // We have LowColon and PostColon specifically for backwards compatibility.  These are not
    // good bases for custom operator semantics.
    @Test
    fun customColonOperatorsBindTightly() {
        assertEquals(
            listOf(Operator.HighColon), // Not LowColon
            Operator.matching(
                ":-",
                TokenType.Punctuation,
                OperatorType.Infix,
            ),
        )
    }

    @Test
    fun postfixColonDoesNotCustomize() {
        assertEquals(
            emptyList(),
            Operator.matching(
                ":-",
                TokenType.Punctuation,
                OperatorType.Postfix,
            ),
        )
    }
}
