package lang.temper.lexer

import lang.temper.common.assertStringsEqual
import kotlin.test.Test

class BlockCommentContentTest {
    private fun assertCommentContent(
        want: String,
        input: String,
    ) = assertStringsEqual(want, blockCommentContent(input))

    @Test
    fun singleLine0() = assertCommentContent(
        """Single line""".trimMargin(),
        """/** Single line **/""".trimMargin(),
    )

    @Test
    fun singleLine1() = assertCommentContent(
        """Single line""".trimMargin(),
        """/* Single line */""".trimMargin(),
    )

    @Test
    fun singleLine2() = assertCommentContent(
        """Single line asterisked to occupy width""".trimMargin(),
        """/********* Single line asterisked to occupy width *********/""".trimMargin(),
    )

    @Test
    fun asterisksAtEdge() = assertCommentContent(
        """
            |Asterisks lined up at edge.
            |Second line
            |
            |    Indented
            |    Indented
            |
            |Lorem ipsum
        """.trimMargin(),
        """
            |/**
            | * Asterisks lined up at edge.
            | * Second line
            | *
            | *     Indented  ${
            // trailing white space on this line
            ""
        }
            | *     Indented
            | *
            | * Lorem ipsum
            | */
        """.trimMargin(),
    )

    @Test
    fun heavyLeftEdge1() = assertCommentContent(
        """
            |Like the last but
            |with heavier star borders
        """.trimMargin(),
        """
            |/** Like the last but
            | ** with heavier star borders
            | **/
        """.trimMargin(),
    )

    @Test
    fun heavyLeftEdge2() = assertCommentContent(
        """
            |Like the last but
            |with heavier star borders
        """.trimMargin(),
        """
            |/**
            | ** Like the last but
            | ** with heavier star borders
            | **/
        """.trimMargin(),
    )

    @Test
    fun heavyLeftEdge3() = assertCommentContent(
        """
            |Like the last but
            |with heavier star borders
        """.trimMargin(),
        """
            |/**
            | ** Like the last but
            | ** with heavier star borders **/
        """.trimMargin(),
    )

    @Test
    fun boxOfStarts() = assertCommentContent(
        """
            |Some people like to
            |put content in boxes
            |made of stars.
            |OMG Reverse 2001.
        """.trimMargin(),
        """
            |/**************************
            | ** Some people like to  **
            | ** put content in boxes **
            | ** made of stars.       **
            | ** OMG Reverse 2001.    **
            | **************************/
        """.trimMargin(),
    )

    @Test
    fun leadingIndentation() = assertCommentContent(
        """
            |Lines other than first indented
            |by minimal common space prefix.
            |
            |  Indented
            |
            |Unindented
        """.trimMargin(),
        """
            |/*
            |   Lines other than first indented
            |   by minimal common space prefix.
            |
            |     Indented
            |   ${""}
            |   Unindented
            | */
        """.trimMargin(),
    )

    @Test
    fun someStarsAreImportant() = assertCommentContent(
        // '*' is a meta-character for un-ordered lists in Markdown
        """
            |Kinds of fruit include:
            |
            |* Apples
            |* Bananas
            |* Cantaloupes
            |* Durians
        """.trimMargin(),
        """
            |/* Kinds of fruit include:
            | *
            | * * Apples
            | * * Bananas
            | * * Cantaloupes
            | * * Durians              */
        """.trimMargin(),
    )

    @Test
    fun horizontalRule() = assertCommentContent(
        input = "/************************/",
        want = "",
    )
}
