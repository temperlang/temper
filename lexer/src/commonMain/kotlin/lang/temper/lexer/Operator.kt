package lang.temper.lexer

import lang.temper.common.isAsciiLetter
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.lexer.Associativity.Left
import lang.temper.lexer.Associativity.Right
import lang.temper.lexer.OperatorType.Infix
import lang.temper.lexer.OperatorType.Nullary
import lang.temper.lexer.OperatorType.Postfix
import lang.temper.lexer.OperatorType.Prefix
import lang.temper.lexer.OperatorType.Separator

/*
 If you need to adjust precedence values, you can change one to a fractional value and then run
 `poetry run renumber-operator-precedences` from project root/scripts.
 */

/**
 * Defines operators recognized by the operator precedence parser.
 */
@Suppress("MagicNumber") // Precedence values
enum class Operator(
    val precedence: Precedence,
    val text: String?,
    val operatorType: OperatorType,
    val associativity: Associativity,
    val closer: Boolean,
    val followers: Followers,
    val minArity: Int,
    val maxArity: Int,
    val continuesStatement: Boolean,
    /** Can [text] be treated as a prefix of an operator text? */
    val customizable: Boolean,
) : Structured {
    Root(Int.MIN_VALUE, null, Prefix, minArity = 0, maxArity = Int.MAX_VALUE),

    SemiSemiSemi(-6, ";;;", Separator, Right),

    SemiSemi(-5, ";;", Separator, Right),
    Arrow(-5, "=>", Separator, Right),

    Semi(-4, ";", Separator, Right),

    /**
     * This operator attaches only to interstitials that can
     * appear in runs of statements.
     *
     * ```
     * _ { // In a block lambda
     *   mixin("location-a"):
     *     some_statements;
     *   mixin("location-b"):
     *     more_statements;
     * }
     * ```
     *
     * Those are syntactically like `case _:` and `default:` in other languages
     * in that they are interstitial within runs of statements but instead of
     * identifying jump destinations, they identify locations between statements
     * that might have instructions from a mixin incorporated.
     */
    SelectivePostColon(-3, ":", Postfix, customizable = false),

    // `->` appears between pattern lists and their associated actions.
    // It's higher precedence than `;` since `;` separates pattern/action groups
    ThinArrow(-2, "->", Infix, Right),

    // `given` can connect `,` separated patterns in a pattern match with an additional condition.
    InfixGiven(-1, "given", Infix),

    // Or `given` can precede a predicate without a pattern.
    PrefixGiven(-1, "given", Prefix),

    // Followed by expressions which are often not parenthesized.
    Return(-1, "return", Prefix, minArity = 0),
    Throw(-1, "throw", Prefix),
    Yield(-1, "yield", Prefix, minArity = 0, maxArity = 1),

    /**
     * `of` expects a declaration on the left so binds looser than
     * [HighColon] and [Eq] in `@decoration let name: Type = expr`
     * and the [At] used to decorate.
     *
     * To support multi-declarations on its left, it binds looser than [Comma].
     */
    Of(-1, "of", Infix, Right),

    Comma(0, ",", Separator, Right),

    /**
     * Colon disambiguation is necessary for proper handling of C-like syntactic constructs.
     *
     * | Construct                           | Example Code       |
     * | ----------------------------------- | ------------------ |
     * | labeled statements                  | `label: statement` |
     * | switch body members                 | `case 123:`        |
     * | JSON like object expression         | `key: value`       |
     * | Pascal/TypeScript type specifiers   | `name: Type`       |
     *
     * A colon operator that is slightly higher precedence than comma allows us
     * to handle both of the below:
     *
     *     { a: b }
     *
     * That possibly starts a block containing a labeled statement.
     *
     *     { a: b, c: d }
     *
     * That is probably part of a JSON like object expression.
     */
    LowColon(1, ":", Infix, Left, customizable = false),

    /**
     * Can appear inside left of "->", and inside "," that are inside "->"
     * for use in "match" blocks.
     * See *PostponedCaseMacro*.
     */
    PreCase(1, "case", Prefix),

    At(2, "@", Prefix, maxArity = 2),

    Eq(3, "=", Infix, Right),
    PlusEq(3, "+=", Infix, Right),
    DashEq(3, "-=", Infix, Right),
    StarEq(3, "*=", Infix, Right),
    SlashEq(3, "/=", Infix, Right),
    PctEq(3, "%=", Infix, Right),
    AmpEq(3, "&=", Infix, Right),
    CaretEq(3, "^=", Infix, Right),
    BarEq(3, "|=", Infix, Right),
    AmpAmpEq(3, "&&=", Infix, Right),
    BarBarEq(3, "||=", Infix, Right),
    LtLtEq(3, "<<=", Infix, Right),
    GtGtEq(3, ">>=", Infix, Right),
    GtGtGtEq(3, ">>>=", Infix, Right),

    OrElse(4, "orelse", Infix, Right),

    // A placeholder for words between a call with a terminal block and a call that follows it.
    CallJoin(5, "callJoin:", Infix, associativity = Right, continuesStatement = true),

    // `extends` can capture a comma-separated list of type expressions but must be captured by the
    // {...} containing the class body.
    // But only when it doesn't appear inside angle brackets.
    //
    // `extends` and `implements` group types to the right, so they are slightly lower precedence
    // than operators that appear in type expressions including colon (`:`) which separates
    // function types' output types, and ampersand (`&`) and bar (`|`) which compose types into
    // intersection and union types.
    ExtendsComma(6, "extends", Infix, followers = Commas, maxArity = Int.MAX_VALUE),
    ImplementsComma(
        6,
        "implements",
        Infix,
        followers = Commas,
        maxArity = Int.MAX_VALUE,
    ),
    ForbidsComma(6, "forbids", Infix, followers = Commas, maxArity = Int.MAX_VALUE),
    SupportsComma(6, "supports", Infix, followers = Commas, maxArity = Int.MAX_VALUE),
    ExtendsNoComma(6, "extends", Infix),
    ImplementsNoComma(6, "implements", Infix),
    ForbidsNoComma(6, "forbids", Infix),
    SupportsNoComma(6, "supports", Infix),

    /**
     * A colon operator for associating types with things as in `function f(thing: Type)`.
     * See [LowColon] for notes on how colon is complicated.
     */
    HighColon(7, ":", Infix, Right),

    /**
     * Higher precedence than [HighColon] to allow for
     * `: PassType throws Fail0 | Fail1`
     */
    Throws(8, "throws", Infix, followers = Bars, maxArity = Int.MAX_VALUE),

    Coalesce(9, "??", Infix),

    BarBar(10, "||", Infix, Right),
    AmpAmp(11, "&&", Infix, Right),

    EqEq(12, "==", Infix),
    NotEq(12, "!=", Infix),
    EqEqEq(12, "===", Infix),
    NotEqEq(12, "!==", Infix),
    EqTilde(12, "=~", Infix, Right),
    BangTilde(12, "!~", Infix, Right),

    // Angle is infix-ambiguous with Lt. It sorts earlier when we're finding matches even though it
    // has a higher precedence.  The relationship between the two is governed by the mayBracket
    // bit inferred by the lexer.
    Angle(24, "<", Infix, closer = true, minArity = 1, maxArity = Int.MAX_VALUE),

    Lt(13, "<", Infix),
    Le(13, "<=", Infix),
    Gt(13, ">", Infix),
    Ge(13, ">=", Infix),
    In(13, "in", Infix, minArity = 1),
    Instanceof(13, "instanceof", Infix),

    Is(13, "is", Infix),
    PreIs(13, "is", Prefix),
    As(13, "as", Infix),

    // Following Rust's lead for bitwise operator precedence: https://doc.rust-lang.org/reference/expressions.html
    // Go also places them higher than relational operators, but different: https://go.dev/ref/spec#Operator_precedence
    Bar(14, "|", Infix, Right),
    Caret(15, "^", Infix, Right),
    Amp(16, "&", Infix, Right),

    LtLt(17, "<<", Infix),
    GtGt(17, ">>", Infix),
    GtGtGt(17, ">>>", Infix),

    // I have no plans to use a CONS operator, but a medium precedence right-associative operator
    // will probably come in handy for someone.
    // Positioned here because as in Ocaml: ocaml.org/api/Ocaml_operators.html
    ColonColon(18, "::", Infix, Right),
    Plus(19, "+", Infix),
    Dash(19, "-", Infix),
    Star(20, "*", Infix),
    Slash(20, "/", Infix),
    Pct(20, "%", Infix),
    Tilde(20, "~", Infix),
    StarStar(21, "**", Infix),
    PreIncr(22, "++", Prefix),
    PreDecr(22, "--", Prefix),
    PrePlus(22, "+", Prefix),
    PreDash(22, "-", Prefix),
    Bang(22, "!", Prefix),
    PreTilde(22, "~", Prefix),
    PreAmp(22, "&", Prefix),
    PreStar(22, "*", Prefix, minArity = 0),
    Ellipsis(22, "...", Prefix, minArity = 0),
    Await(22, "await", Prefix),
    PostIncr(23, "++", Postfix),
    PostDecr(23, "--", Postfix),
    PostBang(23, "!", Postfix),
    PostQuest(23, "?", Postfix),

    Dot(24, ".", Infix),
    ChainNull(24, "?.", Infix),

    // Angle is here in precedence order so that it combines left associatively in
    // `foo.bar<C, D>()` with dot and parens, but above Lt for ambiguity resolution purposes.
    DotDot(24, "..", Infix, associativity = Right),
    CurlyGroup(24, "{", Prefix, closer = true, minArity = 0, maxArity = Int.MAX_VALUE),
    ParenGroup(24, "(", Prefix, closer = true, minArity = 0, maxArity = Int.MAX_VALUE),
    SquareGroup(24, "[", Prefix, closer = true, minArity = 0),
    Curly(24, "{", Infix, closer = true, minArity = 1, maxArity = Int.MAX_VALUE),
    Paren(24, "(", Infix, closer = true, minArity = 1, maxArity = Int.MAX_VALUE),
    Square(24, "[", Infix, closer = true),
    Esc(24, "\\", Prefix),
    EscParen(24, "\\(", Prefix, closer = true),
    EscCurly(24, "\\{", Prefix, closer = true),
    DollarCurly(24, $$"${", Prefix, closer = true, minArity = 0, maxArity = 1),
    UnicodeRun(24, "\\u{", Prefix, closer = true, minArity = 0, maxArity = Int.MAX_VALUE),

    New(24, "new", Prefix, minArity = 0),

    /** Groups together parts of a quoted string including character data leaves and embedded expressions. */
    QuotedGroup(25, null, Prefix, closer = true, minArity = 0, maxArity = Int.MAX_VALUE),

    Leaf(Int.MAX_VALUE, null, Nullary),
    ;

    constructor (
        precedenceValue: Int,
        text: String?,
        operatorType: OperatorType,
        associativity: Associativity = Left,
        closer: Boolean = false,
        followers: Followers = Followers.None,
        minArity: Int = operatorType.typicalMinArity,
        maxArity: Int = operatorType.typicalMaxArity,
        continuesStatement: Boolean = false,
        customizable: Boolean = text?.getOrNull(0)?.isAsciiLetter == false,
    ) : this(
        precedence = Precedence(precedenceValue),
        text = text,
        operatorType = operatorType,
        associativity = associativity,
        closer = closer,
        followers = followers,
        minArity = minArity,
        maxArity = maxArity,
        continuesStatement = continuesStatement,
        customizable = customizable,
    )

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("name", Hints.su) {
            value(name)
        }
        if (text != null) {
            key("text", Hints.s) {
                value(text)
            }
        }
        key("type", Hints.u) {
            value(operatorType)
        }
        key("precedence", Hints.u) {
            value(precedence.intValue)
        }
    }

    val isBracket get() = closer && text in openBrackets

    companion object {
        private val lowestPrecedences: Map<String, Precedence>

        init {
            val lpMap = mutableMapOf<String, Precedence>()
            for (operator in entries) {
                val precedence = operator.precedence
                val text = operator.text ?: continue
                lpMap[text] = lpMap[text] ?: precedence
            }
            lowestPrecedences = lpMap.toMap()
        }

        private val operatorsByType: Map<Pair<String, OperatorType>, List<Operator>> =
            entries.mapNotNull {
                if (it.text != null) {
                    (it.text to it.operatorType) to it
                } else {
                    null
                }
            }.groupBy({ it.first }, { it.second })

        private val operatorTexts = entries.map { it.text }.toSet()

        private fun possibleTokenTexts(
            tokenText: String,
            tokenType: TokenType,
            type: OperatorType,
        ) = sequence {
            yield(tokenText)
            if (type == Infix && tokenText.endsWith("=")) {
                // Treat punctuation tokens that end with "=" as compound assignment operator.
                yield("=")
            }
            var prefixLength = tokenText.length
            var last = tokenText
            while (prefixLength > 1) {
                if (
                    tokenType != TokenType.Punctuation || // Do not consider prefixes of words.
                    // Prevent `++` as an infix operator from being treated as an extension of `+`.
                    last in operatorTexts
                ) {
                    break
                }
                prefixLength -= 1
                val prefix = tokenText.take(prefixLength)
                yield(prefix)
                last = prefix
            }
        }

        fun matching(tokenText: String, tokenType: TokenType, type: OperatorType): List<Operator> {
            if (!tokenType.grammatical) {
                // Only punctuation and word tokens correspond to operators.
                // Specifically, QuotedString tokens like those lexed from
                //     '-'
                // do not.
                return emptyList()
            }
            for (possibleTokenText in possibleTokenTexts(tokenText, tokenType, type)) {
                val operators = operatorsByType[possibleTokenText to type]
                if (operators != null) {
                    val filtered = if (tokenText == possibleTokenText) {
                        operators
                    } else {
                        operators.filter { it.customizable }
                    }
                    if (filtered.isNotEmpty()) {
                        return filtered
                    }
                }
            }
            return emptyList()
        }

        /**
         * The set of token texts that can follow a second operand to continue an infix operator
         * with a third or subsequent operand.
         */
        val followsAny: Set<String> =
            entries.flatMap { it.followers.followingTexts.toList() }.toSet()

        fun isProbablyAssignmentOperator(tokenText: String, tokenType: TokenType): Boolean =
            tokenType == TokenType.Punctuation && tokenText == "--" || tokenText == "++" ||
                (
                    tokenText.endsWith("=") &&
                        matching(tokenText, tokenType, Infix).any {
                            when (it) {
                                Le, Ge, EqEq, EqEqEq, NotEq, NotEqEq -> false
                                else -> true
                            }
                        }
                    )

        fun lowestPrecedenceMatching(tokenText: String, tokenType: TokenType): Precedence? {
            for (possibleTokenText in possibleTokenTexts(tokenText, tokenType, Infix)) {
                val precedence = lowestPrecedences[possibleTokenText]
                if (precedence != null) {
                    return precedence
                }
            }
            return null
        }
    }
}

data class Precedence(val intValue: Int) : Comparable<Precedence> {
    override fun compareTo(other: Precedence): Int = this.intValue.compareTo(other.intValue)
}

private abstract class AbstractPunctuationFollowers(
    override val followingTexts: Set<String>,
) : Followers {
    override fun mayFollowAtOperandIndex(i: Int, tokenText: String, tokenType: TokenType): Boolean {
        return tokenType == TokenType.Punctuation && tokenText in followingTexts
    }
}

private object Commas : AbstractPunctuationFollowers(setOf(","))

private object Bars : AbstractPunctuationFollowers(setOf("|"))
