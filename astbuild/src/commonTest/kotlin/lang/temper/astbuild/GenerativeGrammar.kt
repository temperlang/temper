package lang.temper.astbuild

import lang.temper.ast.CstToken
import lang.temper.common.binarySearch
import lang.temper.common.testCodeLocation
import lang.temper.common.truncateTo
import lang.temper.lexer.Lexer
import lang.temper.lexer.TemperToken
import lang.temper.lexer.TokenType
import lang.temper.lexer.nextNotSyntheticOrNull
import lang.temper.lexer.reservedWords
import lang.temper.log.LogSink
import lang.temper.log.Position
import kotlin.random.Random

/**
 * A generative grammar of a **subset** of the Temper language.
 *
 * This allows us to fuzz-test the actual language grammar which is spread between the
 * - [operator-precedence][lang.temper.lexer.Operator] [parser][lang.temper.parser.parse], and
 * - a [combinator-based tree builder][grammar]
 */
internal object GenerativeGrammar {
    private val productions = mutableMapOf<String, GrammarElement>()

    fun generate(prng: Random, quota: Int): List<CstToken>? {
        val c = GeneratorContext(
            prng = prng,
            quota = quota,
            tokensOut = mutableListOf(),
            productions = productions,
        )
        return if (productions["stmt"]!!.generate(c, 1.0)) {
            c.tokensOut.toList()
        } else {
            null
        }
    }

    init {
        // Epsilon is shorthand for the empty string.
        @Suppress("LocalVariableName", "NonAsciiCharacters")
        val ε = GenerateAll(emptyList())

        // "<foo>" is a reference to the production named "foo"
        // "foo" matches the token with that text after stripping quotes.
        // "foo/*synthetic*/" matches token with text "foo" but only as a synthetic token.
        fun convert(s: String): GrammarElement = when {
            s.isNotEmpty() && s[0] == '<' && s.last() == '>' -> GrammarElementReference(s.substring(1, s.length - 1))
            else -> {
                val (tokenText, synthetic) = if (s.endsWith("/*synthetic*/")) {
                    s.dropLast("/*synthetic*/".length) to true
                } else {
                    s to false
                }
                val lexer = Lexer(testCodeLocation, LogSink.devNull, tokenText)
                val token = lexer.nextNotSyntheticOrNull
                require(token != null && token.tokenText == tokenText) {
                    tokenText
                }
                ExactToken(CstToken(token.copy(synthetic = synthetic)))
            }
        }

        // y is shorthand for "and-then"
        infix fun (GrammarElement).y(b: GrammarElement) = GenerateAll.flatten(this, b)
        infix fun (GrammarElement).y(b: String): GrammarElement = GenerateAll.flatten(this, convert(b))
        infix fun (String).y(b: GrammarElement): GrammarElement = GenerateAll.flatten(convert(this), b)
        infix fun (String).y(b: String): GrammarElement = GenerateAll.flatten(convert(this), convert(b))

        fun or(vararg els: GrammarElement) = GenerateOneOf(els.toList())
        fun or(vararg els: Pair<GrammarElement, Int>): GenerateOneOf {
            val n = els.size
            val intervals = IntArray(size = n)
            var sumWeights = 0
            for (i in els.indices) {
                sumWeights += els[i].second
                intervals[i] = sumWeights
            }
            return GenerateOneOf(els.map { it.first }.toList(), intervals)
        }
        fun or(vararg els: String) = GenerateOneOf(els.map { convert(it) })
        fun or(vararg els: Pair<String, Int>): GenerateOneOf =
            or(*els.map { convert(it.first) to it.second }.toTypedArray())
        infix fun (String).matches(body: GrammarElement) {
            val declaredName = this
            require(declaredName !in productions)
            productions[declaredName] = body
        }
        fun opt(body: GrammarElement) = or(body, ε)
        fun opt(body: String) = or(ε y body, ε)

        // Kleene plus
        fun some(body: GrammarElement) = GenerateRepeatedly(body)
        fun some(body: String) = GenerateRepeatedly(ε y body)

        // Kleene star
        fun any(body: GrammarElement) = opt(some(body))
        fun any(body: String) = opt(some(ε y body))

        // We need a way to prevent {stmt;blocks} where {key:value} bundles are allowed and
        // vice-versa.
        fun lastTokenIn(toks: Set<String>) = Lookback { it in toks }
        fun lastTokenNotIn(toks: Set<String>) = Lookback { it !in toks }
        val objPreceders = setOf("(", "=", "new", "[", ",")

        val reservedKeywords: Set<String> = setOf(
            "new", "in", "instanceof", "is", "while", "do", "return", "throw", "yield", "else",
            "finally", "catch", "default", "out", "as", "get", "set", "of",
        ) + reservedWords

        // Lexical grammar
        "number" matches GenerateComplexToken(
            or(
                or("0x", "0X") y "<hex-digits>",
                // Integer and decimal parts
                or(
                    "." y "<decimal-digits>",
                    "<decimal-digits>" y or(
                        ("." y opt("<decimal-digits>")) to 1,
                        ε to 3, // Weight decimal integers higher since there are two float forms
                    ),
                ) y
                    // Exponent
                    or(
                        (
                            or("e", "E") y
                                or(ε y "+", ε y "-", ε) y
                                "<decimal-digits>"
                            ) to 1,
                        ε to 5, // Weight exponents low.
                    ),
            ),
            16,
            TokenType.Number,
        )

        "nl" matches GenerateComplexToken(OneOf("\n"), 1, TokenType.Space)
        "hex-digits" matches (some("<hex-digit>") y some(ε y "_" y some("<hex-digit>")))
        "decimal-digits" matches (some("<decimal-digit>") y some(ε y "_" y some("<decimal-digit>")))
        "hex-digit" matches OneOf("0123456789ABCDEF0123456789abcdef")
        "decimal-digit" matches OneOf("0123456789")
        "char-value" matches GenerateComplexToken(
            "'" y or(
                "\"" to 1,
                "`" to 1,
                "\$" to 1,
                "<quoted-char>" to 17,
            ) y "'",
            100,
            TokenType.QuotedString,
        )
        "quoted-char" matches or(
            "\\" y or(
                "u" y "<hex-digit>" y "<hex-digit>" y "<hex-digit>" y "<hex-digit>",
                ε y "n",
                ε y "t",
                ε y "\\",
            ) to 1,
            OneOf(
                // Quotes excluded here but reincluded above.
                // TODO: just define a CodePointExcept generator
                " abcdefghijklmnopqrstuvwxyz" +
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                    "0123456789-+=*&^!@#$/:;<>,.",
            ) to 10,
        )
        "dq-char" matches or("'" to 1, "`" to 1, "<quoted-char>" to 18)
        "bq-char" matches or("'" to 1, "\"" to 1, "<quoted-char>" to 18)

        "string-qq" matches
            GenerateComplexToken("\"" y any("<dq-char>") y "\"", 25, TokenType.QuotedString)
        "string-qb" matches
            GenerateComplexToken("\"" y any("<dq-char>") y "\${", 25, TokenType.QuotedString)
        "string-bb" matches
            GenerateComplexToken("}" y any("<dq-char>") y "\${", 25, TokenType.QuotedString)
        "string-bq" matches
            GenerateComplexToken("}" y any("<dq-char>") y "\"", 25, TokenType.QuotedString)

        "string-value" matches or(
            "(/*synthetic*/" y "<string-qq>" y ")/*synthetic*/",
            "(/*synthetic*/" y "<string-qb>" y "(/*synthetic*/" y
                "<comma-expr>" y
                any(")/*synthetic*/" y "<string-bb>" y "(/*synthetic*/" y "<comma-expr>") y
                ")/*synthetic*/" y "<string-bq>" y ")/*synthetic*/",
        )

        "id" matches or(
            GenerateComplexToken(
                "<id-start>" y any("<id-continue>"),
                15,
                TokenType.Word,
            ) {
                it !in reservedKeywords
            } to 10,
            GenerateComplexToken(
                "nym" y "`" y some("<bq-char>") y "`",
                10,
                TokenType.QuotedString,
            ) to 1,
        )
        // TODO: negative lookahead for reserved words
        "id-start" matches OneOf("_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
        "id-continue" matches or("<id-start>", "<decimal-digit>")

        "expr" matches (ε y "<assign-expr>")
        "expr-no-rel-ops" matches (ε y "<assign-expr-no-rel-ops>")

        "assign-expr" matches or(
            "<assign-expr>" y or("=", "+=", "-=", "*=", "/=") y
                or("<hook-expr>" to 10, "<property-bag>" to 1) to 1,
            ε y "<hook-expr>" to 3,
            // multi-assignment
        )
        "assign-expr-no-rel-ops" matches or(
            "<assign-expr-no-rel-ops>" y or("=", "+=", "-=", "*=", "/=") y
                or("<hook-expr-no-rel-ops>" to 10, "<property-bag>" to 1) to 1,
            ε y "<hook-expr-no-rel-ops>" to 3,
            // multi-assignment
        )

        "hook-expr" matches or(
            "<hook-expr>" y "?" y "<hook-expr>" y ":" y "<logic-expr>" to 1,
            ε y "<logic-expr>" to 3,
        )
        "hook-expr-no-rel-ops" matches or(
            "<hook-expr-no-rel-ops>" y "?" y "<hook-expr-no-rel-ops>" y ":" y "<plus-expr>" to 1,
            ε y "<plus-expr>" to 3,
        )

        "logic-expr" matches or(
            "<bit-expr>" y or("||", "&&") y "<logic-expr>" to 1,
            ε y "<bit-expr>" to 3,
        )

        "bit-expr" matches or(
            "<comparison-expr>" y or("|", "&", "^") y "<bit-expr>" to 1,
            ε y "<comparison-expr>" to 3,
        )

        "comparison-expr" matches or(
            "<shift-expr>" y
                or("==", "!=", ">", "<=", ">=", "===", "!==") y
                "<comparison-expr>"
                to 7,
            // We place a lot of restrictions on how < expressions can appear since
            // uses of `>`, `>>`, `>=`, or `>>=` later affect angle bracket disambiguation.
            ε y "(" y "<shift-expr>" y "<" y "<plus-expr>" y ")" to 1,
            ε y "<shift-expr>" to 48,
        )

        "shift-expr" matches or(
            "<plus-expr>" y or("<<", ">>", ">>>") y "<shift-expr>" to 1,
            ε y "<plus-expr>" to 3,
        )

        "plus-expr" matches or(
            "<mult-expr>" y or("+", "-", ">>>") y "<plus-expr>" to 1,
            ε y "<mult-expr>" to 3,
        )

        "mult-expr" matches or(
            "<mult-expr>" y or("*", "/", "%") y "<new-expr>" to 1,
            ε y "<new-expr>" to 3,
        )

        "new-expr" matches or(
            "new" y "<type>" y or("<actuals>", "<property-bag>") to 1,
            ε y "<prefix-expr>" to 3,
        )

        "prefix-expr" matches or(
            or("-", "+", "!", "~") y "<pre-assign-expr>" to 1,
            ε y "<pre-assign-expr>" to 3,
        )

        "pre-assign-expr" matches or(
            or("++", "--") y "<dot-expr>" to 1,
            ε y "<dot-expr>" to 3,
        )

        "dot-expr" matches or(
            "<bracket-expr>" y "." y "<id>" to 1,
            ε y "<bracket-expr>" to 3,
        )

        "bracket-expr" matches or(
            "<atom>" y "<actuals>" to 1,
            "<atom>" y "[" y "<comma-expr>" y "]" to 1,
//          "<atom>" y "<" y "<comma-expr-no-rel-ops>" y ">" to 1,  // TODO
            "(" y or("<expr>" to 10, "<property-bag>" to 1) y ")" to 1,
            "[" y "<comma-expr>" y "]" to 1,
            ε y "<atom>" to 5,
        )

        "atom" matches or(
            (ε y "<number>") to 4,
            (ε y "<string-value>") to 4,
            (ε y "<char-value>") to 4,
            (ε y "<id>") to 4,
            ("\\" y "<id>") to 1,
        )

        "actuals" matches ("(" y opt("<expr>" y any("," y "<expr>")) y ")")

        "property-bag" matches (lastTokenIn(objPreceders) y "{" y opt("<properties>") y "}")

        "properties" matches ("<property>" y any("," y "<property>") y opt(","))

        "property" matches ("<id>" y ":" y "<hook-expr>")

        "comma-expr" matches ("<expr>" y any(ε y "," y "<expr>"))
        "comma-expr-no-rel-ops" matches ("<expr-no-rel-ops>" y any(ε y "," y "<expr-no-rel-ops>"))
        "stmt" matches or("<stmt-semi>", "<stmt-no-semi>")
        "stmt-semi" matches or(
            "<maybe-label>" y "<expr>" to 4,
            ε y "<jump>" to 1,
            "<maybe-label>" y "<do-while-loop>" to 1,
            ε y "<no-op>" to 1,
            ε y "<let>" to 1,
            // TODO: multi-let
        )
        "stmt-no-semi" matches or(
            "<maybe-label>" y "<block>",
            "<maybe-label>" y "<for-loop>",
            "<maybe-label>" y "<while-loop>",
            "<maybe-label>" y "<if>",
        )
        "stmts" matches or(
            ε y "<stmt>",
            "<stmt-semi>" y ";" y "<stmts>",
            "<stmt-no-semi>" y "<nl>" y "<stmts>",
        )
        "maybe-label" matches or(
            ("<id>" y ":") to 1,
            ε to 4,
        )

        "no-op" matches ε
        "block" matches (
            lastTokenNotIn(objPreceders) y
                "{" y or(ε y "<stmts>" to 3, ε to 1) y "}"
            )
        "for-loop" matches (
            "for" y
                "(" y "<for-init>" y
                ";" y opt("<expr>") y
                ";" y opt("<comma-expr>") y
                ")" y
                "<block>"
            )
        "while-loop" matches ("while" y "(" y "<comma-expr>" y ")" y "<block>")
        "do-while-loop" matches ("do" y "<block>" y "while" y "(" y "<expr>" y ")")

        "for-init" matches or("<let>", "<comma-expr>")

        "if" matches ("if" y "(" y "<comma-expr>" y ")" y "<block>" y opt("else" y "<if>"))

        "let" matches (or("let", "const", "var") y "<decl>" y any("," y "<decl>"))
        "decl" matches (
            "<id>" y
                opt(":" y "<type>") y
                opt("=" y "<hook-expr>")
            )

        "type" matches (
            "<id>" y or(
                "<" y "<type>" y any("," y "<type>") y ">" to 1,
                ε to 4,
            )
            )

        "jump" matches or(
            "break" y opt("<id>"),
            "continue" y opt("<id>"),
            "return" y opt("<expr>"),
            "throw" y "<expr>",
            "yield" y "<expr>",
        )
    }
}

private data class GeneratorContext(
    /** a source of pseudo-randomness for decision making. */
    val prng: Random,
    /** A target maximum length for [tokensOut] */
    val quota: Int,
    /** a list to which [GrammarElement]s may only append. */
    val tokensOut: MutableList<CstToken>,
    /** maps names to referents. */
    val productions: Map<String, GrammarElement>,
)

private sealed class GrammarElement {
    /**
     * Attempts to append a sequence of tokens matching this grammar element to
     * [GeneratorContext.tokensOut] without causing its size to exceed
     * [GeneratorContext.quota].
     *
     * Must be deterministic given a deterministic [GeneratorContext.prng] so
     * that failing tests may be repeated by reusing a random seed.
     *
     * @param decay A double that reduces when dereferencing to bound recursion depth.
     * @return true if the tokens appended to [GeneratorContext.tokensOut] by
     *    this call match the language embodied by the grammar element.
     */
    abstract fun generate(c: GeneratorContext, decay: Double): Boolean
}

/** Outputs one token if there's space. */
private class ExactToken(val t: CstToken) : GrammarElement() {
    override fun generate(c: GeneratorContext, decay: Double): Boolean {
        if (c.quota <= c.tokensOut.size) {
            return false
        }
        c.tokensOut.add(t)
        return true
    }
}

/** Concatenation */
private class GenerateAll(val els: List<GrammarElement>) : GrammarElement() {
    override fun generate(c: GeneratorContext, decay: Double): Boolean {
        val sizeBefore = c.tokensOut.size
        for (el in els) {
            if (!el.generate(c, decay)) {
                c.tokensOut.truncateTo(sizeBefore)
                return false
            }
        }
        return true
    }

    companion object {
        fun flatten(a: GrammarElement, b: GrammarElement): GenerateAll {
            val els = mutableListOf<GrammarElement>()
            if (a is GenerateAll) {
                els.addAll(a.els)
            } else {
                els.add(a)
            }
            if (b is GenerateAll) {
                els.addAll(b.els)
            } else {
                els.add(b)
            }
            return GenerateAll(els.toList())
        }
    }
}

/** Alternation */
private class GenerateOneOf(
    val els: List<GrammarElement>,
    /**
     * The weight given to option els\[i] as the first tried is
     *     (intervals[i] - intervals[i - 1]) / intervals.last()
     * where division is floaty and intervals[-1] is zero.
     */
    val intervals: IntArray = (1..els.size).toList().toIntArray(),
) : GrammarElement() {
    init {
        require(intervals.size == els.size)
        require(
            (1 until intervals.size).all { i ->
                intervals[i - 1] < intervals[i]
            },
        )
    }

    override fun generate(c: GeneratorContext, decay: Double): Boolean {
        val n = els.size
        if (n == 0) {
            return false
        }
        var choice = binarySearch(intervals, c.prng.nextInt(intervals.last()))
        if (choice < 0) {
            choice = choice.inv()
        }
        val sizeBefore = c.tokensOut.size
        for (i in choice until n) {
            if (els[i].generate(c, decay)) {
                return true
            }
            c.tokensOut.truncateTo(sizeBefore)
        }
        return false
    }
}

/** Repeatedly generate content. */
private class GenerateRepeatedly(
    val body: GrammarElement,
    val repeatWeight: Double = 0.75,
) : GrammarElement() {
    override fun generate(c: GeneratorContext, decay: Double): Boolean {
        val scaledRepeatWeight = repeatWeight * decay
        var passed = false
        var sizeBefore: Int
        while (true) {
            sizeBefore = c.tokensOut.size
            val p = body.generate(c, decay)
            if (p) {
                passed = true
                val sizeAfter = c.tokensOut.size
                if (
                    sizeAfter < c.quota && sizeAfter > sizeBefore &&
                    c.prng.nextDouble() < scaledRepeatWeight
                ) {
                    continue
                }
            } else {
                c.tokensOut.truncateTo(sizeBefore)
            }
            break
        }

        if (!passed) {
            c.tokensOut.truncateTo(sizeBefore)
        }
        return passed
    }
}

/**
 * Uses a generator for characters instead of tokens, produces a single token.
 */
private class GenerateComplexToken(
    val chars: GrammarElement,
    val lengthQuota: Int,
    val tt: TokenType,
    val postcondition: (tokenText: String) -> Boolean = { true },
) : GrammarElement() {
    override fun generate(c: GeneratorContext, decay: Double): Boolean {
        if (c.tokensOut.size < c.quota) {
            val charList = mutableListOf<CstToken>()
            val charsContext = GeneratorContext(c.prng, lengthQuota, charList, c.productions)
            if (chars.generate(charsContext, decay)) {
                val tokenText = charList.joinToString("") { it.tokenText }
                if (postcondition(tokenText)) {
                    c.tokensOut.add(
                        CstToken(
                            TemperToken(
                                pos = pseudoPos,
                                tokenText = tokenText,
                                tokenType = tt,
                                mayBracket = false,
                                synthetic = false,
                            ),
                        ),
                    )
                    return true
                }
            }
        }
        return false
    }
}

/** A reference by name */
private class GrammarElementReference(val name: String) : GrammarElement() {
    override fun generate(c: GeneratorContext, decay: Double): Boolean =
        c.prng.nextDouble() < decay &&
            (c.productions[name] ?: error(name)).generate(c, decay * 0.99)
}

/** Generates one character in the string.  Useful with [GenerateComplexToken]s. */
private class OneOf(val chars: String) : GrammarElement() {
    override fun generate(c: GeneratorContext, decay: Double): Boolean {
        val i = c.prng.nextInt(chars.length)
        c.tokensOut.add(
            CstToken(
                TemperToken(
                    pos = pseudoPos,
                    tokenText = chars.substring(i, i + 1),
                    tokenType = TokenType.Error, // Ok if used to create a ComplexToken
                    mayBracket = false,
                    synthetic = false,
                ),
            ),
        )
        return true
    }
}

/** Emits nothing but fails if the last token emitted doesn't pass its predicate. */
private class Lookback(val lastTokenPredicate: (last: String?) -> Boolean) : GrammarElement() {
    override fun generate(c: GeneratorContext, decay: Double): Boolean {
        return lastTokenPredicate(c.tokensOut.lastOrNull()?.tokenText)
    }
}

private val pseudoPos = Position(testCodeLocation, -1, -1)
