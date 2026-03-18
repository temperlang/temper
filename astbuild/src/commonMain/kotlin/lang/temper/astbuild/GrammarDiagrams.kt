package lang.temper.astbuild

import lang.temper.lexer.reservedWords

private val quotedStringGrammarDoc = GrammarDoc.Choice(
    index = 0,
    listOf(
        // "..."
        GrammarDoc.Sequence(
            listOf(
                GrammarDoc.Terminal("\""),
                stringContentGrammar(sourceCharacterText = "SourceCharacter - ('\\n', '\\r', '\\', '\"')"),
                GrammarDoc.Terminal("\""),
            ),
        ),
        // `...`
        GrammarDoc.Sequence(
            listOf(
                GrammarDoc.Terminal("`"),
                stringContentGrammar(sourceCharacterText = "SourceCharacter - ('\\n', '\\r', '\\', '`')"),
                GrammarDoc.Terminal("`"),
            ),
        ),
        // '...'
        GrammarDoc.Sequence(
            listOf(
                GrammarDoc.Terminal("'"),
                stringContentGrammar(sourceCharacterText = "SourceCharacter - ('\\n', '\\r', '\\', '\\'')"),
                GrammarDoc.Terminal("'"),
            ),
        ),
        // """...
        GrammarDoc.Sequence(
            listOf(
                GrammarDoc.Terminal("\"\"\""),
                GrammarDoc.ZeroOrMore(
                    GrammarDoc.Group(
                        GrammarDoc.Sequence(
                            listOf(
                                GrammarDoc.Terminal("LineBreak"),
                                GrammarDoc.Comment("indentation"),
                                GrammarDoc.Choice(
                                    0,
                                    listOf(
                                        GrammarDoc.Sequence(
                                            listOf(
                                                GrammarDoc.Group(
                                                    GrammarDoc.Choice(
                                                        0,
                                                        listOf(
                                                            GrammarDoc.Terminal("\""),
                                                            GrammarDoc.Terminal("~"),
                                                        ),
                                                    ),
                                                    GrammarDoc.Comment("Ignored margin character"),
                                                ),
                                                stringContentGrammar(sourceCharacterText = "SourceCharacter - ('\\')"),
                                            ),
                                        ),
                                        GrammarDoc.Sequence(
                                            listOf(
                                                GrammarDoc.Terminal(":"),
                                                GrammarDoc.NonTerminal("StatementFragment"),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        GrammarDoc.Comment("Content line starting with `\"`"),
                    ),
                    null,
                ),
                GrammarDoc.Terminal("LineBreak"),
            ),
        ),
    ),
)

/**
 * Used by our documentation system.  See build-user-docs/.../GrammarProductionExtractor.
 */
object GrammarDiagrams {
    /**
     * Some productions are thin veneers over lexical productions.
     * Present them in a character-by-character way.
     * Others may be used via familiar syntax like booleans, but are implemented in terms of
     * constants, not via grammar.
     */
    val overriddenProductionNames get() = overrides.keys.toList()

    private val overrides = mutableMapOf(
        "ReservedWord" to GrammarDoc.Choice(
            index = 0,
            reservedWords.map { GrammarDoc.Terminal(it) },
        ),
        "Garbage" to GrammarDoc.Comment("Consumes problem tokens and emits an error syntax-tree node"),
        "BooleanLiteral" to GrammarDoc.Choice(
            index = 0,
            listOf(
                GrammarDoc.Terminal("false"),
                GrammarDoc.Terminal("true"),
            ),
        ),
        "StringExpr" to quotedStringGrammarDoc,
        "StringGroupSynthetic" to GrammarDoc.Choice.doNotShow,
        "StringGroup" to quotedStringGrammarDoc,
        "StringGroupTagged" to GrammarDoc.Group(
            quotedStringGrammarDoc,
            GrammarDoc.Comment(
                "Whether escape sequences are expanded is up to the tag",
            ),
        ),
        "EscapeSequence" to GrammarDoc.HorizontalChoice(
            listOf(
                // Keep this up-to-date with the table in unpacked string
                GrammarDoc.Terminal("""\0""", title = "Encodes NUL"),
                GrammarDoc.Terminal("""\\""", title = "Encodes a single backslash"),
                GrammarDoc.Terminal("""\/""", title = "Encodes a forward-slash"),
                GrammarDoc.Terminal("""\"""", title = "Encodes a double-quote"),
                GrammarDoc.Terminal("""\'""", title = "Encodes a single-quote"),
                GrammarDoc.Terminal("""\`""", title = "Encodes a back-quote"),
                GrammarDoc.Terminal("""\{""", title = "Encodes a left curly-bracket"),
                GrammarDoc.Terminal("""\}""", title = "Encodes a right curly-bracket"),
                GrammarDoc.Terminal("""\$""", title = "Encodes a dollar sign"),
                GrammarDoc.Terminal("""\b""", title = "Encodes a backspace"),
                GrammarDoc.Terminal("""\t""", title = "Encodes a tab"),
                GrammarDoc.Terminal("""\n""", title = "Encodes a line-feed a.k.a. new-line"),
                GrammarDoc.Terminal("""\f""", title = "Encodes a form-feed"),
                GrammarDoc.Terminal("""\r""", title = "Encodes a carriage-return"),
                GrammarDoc.Terminal("\\", title = "Broken escape sequence encodes nothing"),
            ),
        ),
        "Float64Literal" to run {
            val optionalDigits = GrammarDoc.ZeroOrMore(
                GrammarDoc.OneOrMore(
                    GrammarDoc.NonTerminal("[0-9]"),
                    repeat = null,
                ),
                repeat = GrammarDoc.Terminal("_"),
            )
            val optionalWholeNumber = GrammarDoc.Group(
                optionalDigits,
                label = GrammarDoc.Comment("whole number"),
            )
            val requiredWholeNumber = GrammarDoc.Group(
                optionalDigits,
                label = GrammarDoc.Comment("whole number"),
            )
            val decimalPoint = GrammarDoc.Terminal(".")
            val requiredDigits = GrammarDoc.OneOrMore(
                GrammarDoc.OneOrMore(
                    GrammarDoc.NonTerminal("[0-9]"),
                    repeat = null,
                ),
                repeat = GrammarDoc.Terminal("_"),
            )
            val fraction = GrammarDoc.Group(
                requiredDigits,
                label = GrammarDoc.Comment("fraction"),
            )
            val exponent = GrammarDoc.Group(
                GrammarDoc.Sequence(
                    listOf(
                        GrammarDoc.Choice(
                            0,
                            listOf(GrammarDoc.Terminal("E"), GrammarDoc.Terminal("e")),
                        ),
                        GrammarDoc.Choice(
                            0,
                            listOf(
                                GrammarDoc.Skip,
                                GrammarDoc.Terminal("+"),
                                GrammarDoc.Terminal("-"),
                            ),
                        ),
                        requiredDigits,
                    ),
                ),
                label = GrammarDoc.Comment("exponent"),
            )
            val suffix = GrammarDoc.Group(
                GrammarDoc.OneOrMore(
                    GrammarDoc.Choice(
                        0,
                        listOf(
                            GrammarDoc.Terminal("D"),
                            GrammarDoc.Terminal("d"),
                        ),
                    ),
                    repeat = null,
                ),
                label = GrammarDoc.Comment("suffix"),
            )

            GrammarDoc.Choice(
                0,
                listOf(
                    GrammarDoc.Sequence(
                        listOf(
                            GrammarDoc.Choice(
                                0,
                                listOf(
                                    // An optional integer, a required fraction, and an optional
                                    // exponent
                                    GrammarDoc.Sequence(
                                        listOf(
                                            optionalWholeNumber,
                                            decimalPoint,
                                            fraction,
                                            GrammarDoc.Optional(exponent),
                                        ),
                                    ),
                                    // Or a required integer, and a required exponent
                                    GrammarDoc.Sequence(listOf(requiredWholeNumber, exponent)),
                                ),
                            ),
                            GrammarDoc.Optional(suffix),
                        ),
                    ),
                    GrammarDoc.Sequence(listOf(requiredWholeNumber, suffix)),
                ),
            )
        },
        // Figure out what to do with quasis before inflicting them on all expression grammar readers.
        "Quasis" to GrammarDoc.Choice.doNotShow,
        "QuasiAst" to GrammarDoc.Choice.doNotShow,
        "QuasiHole" to GrammarDoc.Choice.doNotShow,
        "QuasiInner" to GrammarDoc.Choice.doNotShow,
        "QuasiLeaf" to GrammarDoc.Choice.doNotShow,
        "QuasiTree" to GrammarDoc.Choice.doNotShow,
    )

    private val diagramContext = lazy {
        val doNotShows = overrides.mapNotNull { (name, component) ->
            if (component == GrammarDoc.Choice.doNotShow) {
                name
            } else {
                null
            }
        }
        GrammarDoc.Context(
            inlineable = { false },
            elide = { it in doNotShows },
        )
    }

    fun forProductionNamed(productionName: String): GrammarDoc.Component {
        val lexicalOverride = overrides[productionName]
        if (lexicalOverride != null) { return lexicalOverride }
        val body = grammar.getProduction(productionName)
            ?: throw NoSuchElementException("No production named $productionName")
        return body.toGrammarDocDiagram(grammar, diagramContext.value)
    }

    const val GRAMMAR_DIAGRAM_BASENAME = "snippet"
    const val GRAMMAR_DIAGRAM_EXTENSION = ".svg"
    const val GRAMMAR_DIAGRAM_FILENAME = "$GRAMMAR_DIAGRAM_BASENAME$GRAMMAR_DIAGRAM_EXTENSION"
}

private fun stringContentGrammar(sourceCharacterText: String) = GrammarDoc.ZeroOrMore(
    GrammarDoc.Choice(
        index = 0,
        listOf(
            GrammarDoc.NonTerminal(sourceCharacterText),
            GrammarDoc.NonTerminal(
                "EscapeSequence",
                href = "../EscapeSequence/${GrammarDiagrams.GRAMMAR_DIAGRAM_FILENAME}",
            ),
            GrammarDoc.Group(
                GrammarDoc.Sequence(
                    listOf(
                        GrammarDoc.Terminal("\\u"),
                        GrammarDoc.NonTerminal("Hex"),
                        GrammarDoc.NonTerminal("Hex"),
                        GrammarDoc.NonTerminal("Hex"),
                        GrammarDoc.NonTerminal("Hex"),
                    ),
                ),
                GrammarDoc.Comment(
                    "UTF-16 Code Unit",
                    href = "https://unicode.org/glossary/#code_unit",
                ),
            ),
            GrammarDoc.Group(
                GrammarDoc.Sequence(
                    listOf(
                        GrammarDoc.Terminal("\\u{"),
                        GrammarDoc.OneOrMore(
                            GrammarDoc.NonTerminal("HexDigits"),
                            repeat = GrammarDoc.Terminal(","),
                        ),
                        GrammarDoc.Terminal("}"),
                    ),
                ),
                GrammarDoc.Comment(
                    "Unicode Scalar Values",
                    href = "https://unicode.org/glossary/#unicode_scalar_value",
                ),
            ),
            GrammarDoc.Group(
                GrammarDoc.Sequence(
                    listOf(
                        GrammarDoc.Terminal("\${"),
                        GrammarDoc.NonTerminal(
                            "Expr",
                            href = "../Expr/${GrammarDiagrams.GRAMMAR_DIAGRAM_FILENAME}",
                        ),
                        GrammarDoc.Terminal("}"),
                    ),
                ),
                GrammarDoc.Comment(
                    "Interpolation",
                ),
            ),
        ),
    ),
    repeat = null,
)
