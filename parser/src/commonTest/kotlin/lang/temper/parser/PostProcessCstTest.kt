package lang.temper.parser

import lang.temper.common.ListBackedLogSink
import lang.temper.common.assertStringsEqual
import lang.temper.common.testCodeLocation
import lang.temper.cst.ConcreteSyntaxTree
import lang.temper.cst.CstInner
import lang.temper.cst.CstLeaf
import lang.temper.format.toStringViaTokenSink
import lang.temper.lexer.Lexer
import lang.temper.lexer.Operator
import lang.temper.lexer.TemperToken
import lang.temper.lexer.TokenType
import lang.temper.log.LogSink
import lang.temper.log.Position
import kotlin.test.Test

private const val Q3 = "\"\"\""
private const val HOLE = $$"${}"

class PostProcessCstTest {
    private fun assertCstAfterPostProcessing(
        input: ConcreteSyntaxTree,
        want: ConcreteSyntaxTree,
        isInputRaw: Boolean = true,
    ) {
        val logSink = ListBackedLogSink()
        val got = when (input) {
            is CstInner ->
                if (isInputRaw) {
                    postProcessCst(input, logSink)
                } else {
                    input
                }
            is CstLeaf -> input
        }
        assertStringsEqual(
            toStringViaTokenSink(singleLine = false) {
                want.renderTo(it)
            },
            toStringViaTokenSink(singleLine = false) {
                got.renderTo(it)
            },
        )
    }

    /** Allows composing [ConcreteSyntaxTree] via a DSL like syntax. */
    private fun cst(
        makeParts: CstTestHelper.() -> Unit,
    ): ConcreteSyntaxTree {
        val b = CstTestHelper()
        b.makeParts()
        return b.build()
    }

    @Test
    fun stringsJoinedAroundHoles() = assertCstAfterPostProcessing(
        input = cst {
            parse(
                """
                    |$Q3
                    |"foo${HOLE}bar
                """.trimMargin(),
            )
        },
        isInputRaw = false,
        want = cst {
            inner(Operator.ParenGroup) {
                token("(", TokenType.Punctuation, mayBracket = true, synthetic = true)
                inner(Operator.QuotedGroup) {
                    token(Q3, TokenType.LeftDelimiter)
                    inner(Operator.Leaf) {
                        token("foobar", TokenType.QuotedString)
                    }
                    token(Q3, TokenType.RightDelimiter, synthetic = true)
                }
                token(")", TokenType.Punctuation, mayBracket = true, synthetic = true)
            }
        },
    )

    @Test
    fun ignorableSpaceAtEndOfLineStripped() = assertCstAfterPostProcessing(
        input = cst {
            inner(Operator.ParenGroup) {
                token("(", TokenType.Punctuation, mayBracket = true, synthetic = true)
                inner(Operator.QuotedGroup) {
                    // Strings with four spaces of prefix to strip and incidental
                    // space at the end of some lines.
                    token(Q3, TokenType.LeftDelimiter)
                    inner(Operator.Leaf) {
                        token("\nLine1\nLine2 \r\n", TokenType.QuotedString)
                    }
                    inner(Operator.DollarCurly) {
                        token($$"${", TokenType.Punctuation, mayBracket = true)
                        inner(Operator.Leaf) {
                            token("x", TokenType.Word)
                        }
                        token("}", TokenType.Punctuation, mayBracket = true)
                    }
                    inner(Operator.Leaf) {
                        // There are 5 spaces before Line4, of which 4 are stripped.
                        token(" \n Line4 ", TokenType.QuotedString)
                    }
                    inner(Operator.DollarCurly) {
                        token($$"${", TokenType.Punctuation, mayBracket = true)
                        inner(Operator.Leaf) {
                            token("y", TokenType.Word)
                        }
                        token("}", TokenType.Punctuation, mayBracket = true)
                    }
                    inner(Operator.Leaf) {
                        token("  \nLine5  ", TokenType.QuotedString)
                    }
                    // Space before a hole not stripped.
                    inner(Operator.DollarCurly) {
                        token($$"${", TokenType.Punctuation, mayBracket = true)
                        token("}", TokenType.Punctuation, mayBracket = true)
                    }
                    inner(Operator.Leaf) {
                        token("  \n", TokenType.QuotedString)
                    }
                    token(Q3, TokenType.RightDelimiter, synthetic = true)
                }
                token(")", TokenType.Punctuation, mayBracket = true, synthetic = true)
            }
        },
        want = cst {
            inner(Operator.ParenGroup) {
                token("(", TokenType.Punctuation, mayBracket = true, synthetic = true)
                inner(Operator.QuotedGroup) {
                    // Strings with four spaces of prefix to strip and incidental
                    // space at the end of some lines.
                    token(Q3, TokenType.LeftDelimiter)
                    inner(Operator.Leaf) {
                        token("Line1\nLine2\n", TokenType.QuotedString)
                    }
                    inner(Operator.DollarCurly) {
                        token($$"${", TokenType.Punctuation, mayBracket = true)
                        inner(Operator.Leaf) {
                            token("x", TokenType.Word)
                        }
                        token("}", TokenType.Punctuation, mayBracket = true)
                    }
                    inner(Operator.Leaf) {
                        token("\n Line4 ", TokenType.QuotedString)
                    }
                    inner(Operator.DollarCurly) {
                        token($$"${", TokenType.Punctuation, mayBracket = true)
                        inner(Operator.Leaf) {
                            token("y", TokenType.Word)
                        }
                        token("}", TokenType.Punctuation, mayBracket = true)
                    }
                    inner(Operator.Leaf) {
                        token("\nLine5  ", TokenType.QuotedString)
                    }
                    token(Q3, TokenType.RightDelimiter, synthetic = true)
                }
                token(")", TokenType.Punctuation, mayBracket = true, synthetic = true)
            }
        },
    )
}

/** A DSL for making ConcreteSyntaxTrees */
private class CstTestHelper {
    private var pos = defaultPosition
    private var operator = Operator.Root
    private var isInner = true
    private var operands: List<ConcreteSyntaxTree> = emptyList()
    private var token: TemperToken? = null

    fun build() = if (isInner) {
        CstInner(pos, operator, operands)
    } else {
        CstLeaf(token!!)
    }

    fun inner(operator: Operator, makeOperands: OperandHelper.() -> Unit) {
        this.isInner = true
        this.operator = operator
        val operandList = mutableListOf<ConcreteSyntaxTree>()
        val operandHelper = OperandHelper(operandList)
        operandHelper.makeOperands()
        this.operands = operandList.toList()
    }

    fun token(text: String, type: TokenType, mayBracket: Boolean = false, synthetic: Boolean = false) {
        this.isInner = false
        this.token = TemperToken(pos, text, type, mayBracket = mayBracket, synthetic = synthetic)
    }

    fun parse(source: String) {
        val loc = pos.loc
        val logSink = LogSink.devNull
        val tokens = Lexer(loc, logSink, sourceText = source)
        val cst = parse(tokens, logSink)
        this.isInner = true
        this.pos = cst.pos
        this.operator = cst.operator
        this.operands = cst.operands
    }

    class OperandHelper(private val operands: MutableList<ConcreteSyntaxTree>) {
        fun inner(operator: Operator, makeOperands: OperandHelper.() -> Unit) {
            val h = CstTestHelper()
            h.inner(operator, makeOperands)
            operands.add(h.build())
        }

        fun token(text: String, type: TokenType, mayBracket: Boolean = false, synthetic: Boolean = false) {
            val h = CstTestHelper()
            h.token(text, type, mayBracket = mayBracket, synthetic = synthetic)
            operands.add(h.build())
        }
    }

    companion object {
        private val defaultPosition = Position(testCodeLocation, 0, 0)
    }
}
