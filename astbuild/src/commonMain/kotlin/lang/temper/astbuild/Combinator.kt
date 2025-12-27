package lang.temper.astbuild

import lang.temper.ast.AstPart
import lang.temper.ast.CstPart
import lang.temper.ast.CstToken
import lang.temper.ast.FinishTree
import lang.temper.ast.KnownProblemEvent
import lang.temper.ast.LeafAstPart
import lang.temper.ast.LeftParenthesis
import lang.temper.ast.ProductionFailedEvent
import lang.temper.ast.RightParenthesis
import lang.temper.ast.StartTree
import lang.temper.ast.TokenLeaf
import lang.temper.ast.ValuePart
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.vAttachCommentDecorator
import lang.temper.common.C_MAX_CODEPOINT
import lang.temper.common.C_MAX_SURROGATE
import lang.temper.common.C_MIN_SURROGATE
import lang.temper.common.LeftOrRight
import lang.temper.common.compatRemoveLast
import lang.temper.common.console
import lang.temper.common.mutSubListToEnd
import lang.temper.common.truncateTo
import lang.temper.cst.CstComment
import lang.temper.lexer.CommentType
import lang.temper.lexer.TokenType
import lang.temper.lexer.closeBrackets
import lang.temper.lexer.commentAssociation
import lang.temper.lexer.decodeHex
import lang.temper.log.CodeLocation
import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.spanningPosition
import lang.temper.value.InnerTreeType
import lang.temper.value.LeafTreeType
import lang.temper.value.TBoolean
import lang.temper.value.TList
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.Value

internal class CombinatorContext<TREE>(
    val loc: CodeLocation,
    /** The list of items to process. */
    val input: List<CstPart>,
    /** Used to lookup productions by name. */
    val productions: Productions<TREE>,
    /** Comments */
    val storedCommentTokens: StoredCommentTokens,
) {
    /** Combinators may append to this if they succeed. */
    val output = mutableListOf<AstPart>()

    /** For debugging, a stack of production names entered but not subsequently exited. */
    val refStack = mutableListOf<Ref>()

    /** Allows counting parentheses in a context. */
    val counterStack = mutableListOf<Pair<Ref, MutIntCell>>()
}

internal interface Combinator {
    /**
     * A combinator takes (context: [CombinatorContext], position: Int)
     * where position is an index into `context.input`.
     *
     * @return -1 to indicate match failure,
     *     otherwise the position after application which must
     *     be >= the input position.
     */
    fun apply(context: CombinatorContext<*>, position: Int): Int

    val children: Iterable<Combinator>

    /**
     * Build JS that may assume that the name `railroad` is bound to
     * https://github.com/tabatkins/railroad-diagrams/blob/gh-pages/README-js.md
     * to build a railroad diagram describing this combinator.
     *
     * @param inlinable true if the reference should be inlined, false if documented separately
     */
    fun toGrammarDocDiagram(g: Productions<*>, inlinable: (Ref) -> Boolean): GrammarDoc.Component
}

internal val epsilon = Cat.empty

/** A reference to a production. */
internal data class Ref(
    val name: String,
) : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val outputLengthAtStart = context.output.size

        val refStack = context.refStack
        refStack.add(this)
        val afterSeed = try {
            // Optimistically assume no LR.  We don't need LR
            // since the operator precedence parser parenthesizes for us.
            val rhs = context.productions.getProduction(name) ?: error(
                "No definition for production $name",
            )
            rhs.apply(context, position)
        } finally {
            refStack.compatRemoveLast()
        }

        if (afterSeed < 0) {
            context.output.mutSubListToEnd(outputLengthAtStart).clear()
            return -1
        }
        return afterSeed
    }

    override val children: List<Combinator> get() = listOf()

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component {
        val p = maybeInline(g, inlinable)
        return if (p == this) {
            GrammarDoc.NonTerminal(name, href = null) // TODO: Link references
        } else {
            p.toGrammarDocDiagram(g, inlinable)
        }
    }
}

internal data class EmitBefore(
    val matcher: Combinator,
    val newPart: (pos: Position) -> AstPart,
) : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val input = context.input
        val n = input.size

        val pos = when {
            position != n -> input[position].pos.leftEdge
            position != 0 -> input[position - 1].pos.rightEdge
            else -> Position(context.loc, 0, 0)
        }

        val output = context.output
        val outputSizeBefore = output.size

        output.add(newPart(pos))

        val positionAfter = matcher.apply(context, position)
        if (positionAfter < 0) {
            output.truncateTo(outputSizeBefore)
        }
        return positionAfter
    }

    override val children: List<Combinator> get() = listOf(matcher)

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component = matcher.toGrammarDocDiagram(g, inlinable)
}

internal data class EmitAfter(
    val matcher: Combinator,
    val newParts: (context: CombinatorContext<*>, pos: Position) -> Iterable<AstPart>,
) : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val positionAfter = matcher.apply(context, position)
        if (positionAfter < 0) {
            return -1
        }

        val input = context.input
        val n = input.size

        val p = when {
            positionAfter != 0 -> input[positionAfter - 1].pos.rightEdge
            positionAfter < n -> input[positionAfter].pos.leftEdge
            else -> Position(context.loc, 0, 0)
        }

        context.output.addAll(newParts(context, p))

        return positionAfter
    }

    override val children: List<Combinator> get() = listOf(matcher)

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component = matcher.toGrammarDocDiagram(g, inlinable)
}

/** Emits an implied ast part, getting position metadata from the next token. */
internal data class Implied(
    val bias: LeftOrRight = LeftOrRight.Right,
    val emitAt: (pos: Position, out: MutableList<AstPart>) -> Unit,
) : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val input = context.input

        // Use context to find position metadata for the implied symbol.
        val lookRight = when {
            // Assume there's at least one part if there's an implicit tree
            position == 0 -> true
            position >= input.size -> false
            else -> bias == LeftOrRight.Right
        }

        val p =
            if (lookRight) {
                input[position].pos.leftEdge
            } else {
                require(position != 0)
                input[position - 1].pos.rightEdge
            }

        emitAt(p, context.output)
        return position
    }

    override val children: List<Combinator> get() = listOf()

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component = GrammarDoc.Skip
}

/** Concatenation. */
@Suppress("DataClassPrivateConstructor")
@ConsistentCopyVisibility
internal data class Cat private constructor(val elements: List<Combinator>) : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val outputLengthAtStart = context.output.size
        var after = position
        for (part in elements) {
            after = part.apply(context, after)
            if (after < 0) {
                context.output.mutSubListToEnd(outputLengthAtStart).clear()
                return -1
            }
        }
        return after
    }

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component {
        val components = mutableListOf<GrammarDoc.Component>()
        for (element in elements) {
            when (val component = element.toGrammarDocDiagram(g, inlinable)) {
                is GrammarDoc.Sequence -> components.addAll(component.children)
                is GrammarDoc.Skip -> Unit
                else -> components.add(component)
            }
        }
        return when (components.size) {
            0 -> GrammarDoc.Skip
            1 -> components[0]
            else -> GrammarDoc.Sequence(components.toList())
        }
    }

    companion object {
        val empty = Cat(emptyList())

        fun new(elements: List<Combinator>): Combinator {
            val flatList = elements.flatMap {
                if (it is Cat) {
                    it.elements
                } else {
                    listOf(it)
                }
            }
            return when (flatList.size) {
                0 -> empty
                1 -> flatList[0]
                else -> Cat(flatList.toList())
            }
        }
    }

    override val children: List<Combinator> get() = elements
}

/** Alternation. */
@Suppress("DataClassPrivateConstructor")
@ConsistentCopyVisibility
internal data class Or private constructor(val options: List<Combinator>) : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        for (option in options) {
            val after = option.apply(context, position)
            if (after >= 0) {
                return after
            }
        }
        return -1
    }

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component {
        val choices = mutableSetOf<GrammarDoc.Component>()
        for (option in options) {
            when (val component = option.toGrammarDocDiagram(g, inlinable)) {
                is GrammarDoc.Choice -> choices.addAll(component.children)
                else -> choices.add(component)
            }
        }
        val components = choices.toList()
        // Specialize to Regex * and ?
        if (components.size == 2 && components[1] is GrammarDoc.Skip) {
            val child = components[0]
            return if (child is GrammarDoc.OneOrMore) {
                GrammarDoc.ZeroOrMore(child.child, repeat = child.repeat)
            } else {
                GrammarDoc.Optional(child)
            }
        }
        return when (components.size) {
            0 -> GrammarDoc.Skip
            1 -> components[0]
            else -> GrammarDoc.Choice(components.size / 2, components.toList())
        }
    }

    companion object {
        private val empty = Or(emptyList())
        fun new(options: List<Combinator>): Combinator {
            val flatList = options.flatMap {
                if (it is Or) {
                    it.options
                } else {
                    listOf(it)
                }
            }
            return when (flatList.size) {
                0 -> empty
                1 -> options[0]
                else -> Or(options)
            }
        }
    }

    override val children: List<Combinator> get() = options
}

/**
 * Matches an input token.
 * If emit is truthy, matched content is copied to the output.
 */
@Suppress("DataClassPrivateConstructor")
@ConsistentCopyVisibility
internal data class Match private constructor(
    val description: GrammarDoc.Component,
    val filter: (t: CstPart) -> Boolean,
    private val emit: Boolean,
) : Combinator {
    constructor(
        description: GrammarDoc.Component,
        filter: (t: CstPart) -> Boolean,
    ) : this(description, filter, false)
    constructor(
        description: GrammarDoc.Component,
        emit: Boolean,
        filterToken: (T: CstToken) -> Boolean,
    ) : this(description, { it is CstToken && filterToken(it) }, emit)

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component = description

    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val input = context.input
        if (position < input.size) {
            val target = input[position]
            if (filter(target)) {
                if (emit) {
                    require(target is CstToken)
                    context.output.add(TokenLeaf(target))
                }
                return position + 1
            }
        }
        return -1
    }

    override val children: List<Combinator> get() = listOf()
}

/**
 * Matches a quoted string content token and converts it directly to a [TString] value.
 */
internal object TokenToRawString : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val tok = context.input[position] as? CstToken ?: return -1
        if (tok.tokenType != TokenType.QuotedString) { return -1 }
        context.output.add(ValuePart(Value(tok.tokenText, TString), tok.pos))
        return position + 1
    }

    override val children: Iterable<Combinator> get() = emptyList()

    override fun toGrammarDocDiagram(g: Productions<*>, inlinable: (Ref) -> Boolean): GrammarDoc.Component =
        GrammarDoc.Terminal("raw string")
}

/**
 * Matches a quoted string content token and decodes it from hex to a [TString] code point value.
 */
internal object TokenToCodePoint : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val tok = context.input[position] as? CstToken ?: return -1
        if (tok.tokenType != TokenType.QuotedString) { return -1 }
        val result = decodeHex(tok.tokenText, 0, tok.tokenText.length)?.let { code ->
            when {
                code in C_MIN_SURROGATE..C_MAX_SURROGATE || code > C_MAX_CODEPOINT -> null
                else -> runCatching { Character.toChars(code) }.getOrNull()?.concatToString()?.let { decoded ->
                    // Good decode, so keep it.
                    ValuePart(Value(decoded, TString), tok.pos)
                }
            }
            // Reencode error cases for later handling. Wasteful, but should be rare.
        } ?: TokenLeaf(CstToken(tok.token.copy(tokenText = "\\u{${tok.tokenText}}")))
        context.output.add(result)
        return position + 1
    }

    override val children: Iterable<Combinator> get() = emptyList()

    override fun toGrammarDocDiagram(g: Productions<*>, inlinable: (Ref) -> Boolean): GrammarDoc.Component =
        GrammarDoc.Terminal("code point escape")
}

/** Combine possibly multiple QuotedString tokens into one. */
internal object JoinStrings : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        var index = position
        fun nextToken(): CstToken? {
            val token = context.input.getOrNull(index) as? CstToken ?: return null
            token.tokenType == TokenType.QuotedString || return null
            index += 1
            return token
        }
        // Fail or return one if that's all we have.
        val first = nextToken() ?: return -1
        val second = nextToken() ?: run {
            context.output.add(TokenLeaf(first))
            return@apply index
        }
        // If more, concatenate into one pseudotoken.
        var last = second
        val fullText = buildString content@{
            append(first.tokenText)
            append(second.tokenText)
            while (true) {
                last = nextToken() ?: return@content
                append(last.tokenText)
            }
        }
        val pos = first.pos.copy(right = last.pos.right)
        val result = TokenLeaf(CstToken(first.token.copy(pos = pos, tokenText = fullText)))
        context.output.add(result)
        return index
    }

    override val children: Iterable<Combinator> = listOf()

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component = GrammarDoc.NonTerminal("ContentChars")
}

/** Kleene plus style repetition. */
internal data class Rep(val body: Combinator) : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        var before = position
        var after = body.apply(context, before)
        if (after < 0) {
            return -1
        }
        while (after > before) {
            before = after
            after = body.apply(context, before)
        }
        return before
    }

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component =
        GrammarDoc.OneOrMore(body.toGrammarDocDiagram(g, inlinable), null)

    override val children: List<Combinator> get() = listOf(body)
}

internal data class NegLA(val body: Combinator) : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val outputLengthAtStart = context.output.size
        return if (body.apply(context, position) >= 0) {
            context.output.mutSubListToEnd(outputLengthAtStart).clear()
            -1
        } else {
            position
        }
    }

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component = GrammarDoc.Group(
        body.toGrammarDocDiagram(g, inlinable),
        label = GrammarDoc.Comment("Disallowed"),
    )

    override val children: List<Combinator> get() = listOf(body)
}

/** Lookahead to fail fast based on a predicate over the current CST part. */
internal data class Lookahead(
    val description: GrammarDoc.Component,
    val p: (suffix: Suffix) -> Boolean,
) : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int =
        if (p(Suffix(context, position))) {
            position
        } else {
            -1
        }

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component =
        if (description is GrammarDoc.Skip) {
            description
        } else {
            GrammarDoc.Group(
                description,
                label = GrammarDoc.Comment("Lookahead"),
            )
        }

    override val children: List<Combinator> get() = listOf()
}

internal class Suffix(
    private val context: CombinatorContext<*>,
    private val position: Int,
) {
    val next: CstPart? get() = context.input.getOrNull(position)
    val nextToken: CstToken? get() = nextToken(0)
    fun nextToken(n: Int): CstToken? {
        val input = context.input
        var seen = 0
        for (i in position until input.size) {
            val el = input[i]
            if (el is CstToken) {
                if (seen < n) {
                    seen += 1
                } else {
                    return el
                }
            }
        }
        return null
    }
    operator fun component1() = next
}

internal data class Lookbehind(
    val description: GrammarDoc.Component,
    val p: (prefix: Prefix) -> Boolean,
) : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int =
        if (p(Prefix(context, position))) {
            position
        } else {
            -1
        }

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component = GrammarDoc.Group(
        description,
        label = GrammarDoc.Comment("Lookbehind"),
    )

    override val children: List<Combinator> get() = listOf()
}

internal class Prefix(
    private val context: CombinatorContext<*>,
    private val position: Int,
) {
    val prev: CstPart? get() = context.input.getOrNull(position - 1)
    val prevToken: CstToken?
        get() {
            val input = context.input
            for (i in (0 until position).reversed()) {
                val el = input[i]
                if (el is CstToken) { return el }
            }
            return null
        }
    operator fun component1() = prev
}

/** Substitutes a builtin reference for a token. */
internal class Where(
    private val describe: (Productions<*>, (Ref) -> Boolean) -> GrammarDoc.Component,
    /** Must match one CstToken.  Used to determine success though [makeLeaf]'s output is substituted. */
    private val filter: Combinator,
    private val makeLeaf: (tokenText: String, pos: Position) -> LeafAstPart,
) : Combinator {
    constructor(
        tokenText: String,
        makeLeaf: (tokenText: String, pos: Position) -> LeafAstPart,
    ) : this(
        { _, _ -> GrammarDoc.Terminal(tokenText) },
        Match(
            GrammarDoc.Terminal(tokenText),
        ) {
            it is CstToken && it.tokenType != TokenType.Error && it.tokenText == tokenText
        },
        makeLeaf,
    )

    override val children: List<Combinator> get() = listOf(filter)

    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val input = context.input
        val el = input.getOrNull(position) ?: return -1
        if (el !is CstToken || el.tokenType == TokenType.Error) {
            return -1
        }
        val output = context.output
        val outputBefore = output.size
        val filterAfter = filter.apply(context, position)
        output.subList(outputBefore, output.size).clear()
        return if (filterAfter == position + 1) {
            output.add(makeLeaf(el.tokenText, el.pos))
            position + 1
        } else {
            -1
        }
    }

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component = describe(g, inlinable)
}

internal data class Garbage(
    val messageTemplate: MessageTemplateI,
    val messageValues: List<Any>,
    val stopAfter: Set<String> = emptySet(),
    val stopBefore: Set<String> = closeBrackets,
    val requireSome: Boolean = false,
) : Combinator {

    constructor(
        productionName: String,
        stopAfter: Set<String> = emptySet(),
        stopBefore: Set<String> = closeBrackets,
        requireSome: Boolean = false,
    ) : this(
        messageTemplate = MessageTemplate.Unparsable,
        messageValues = listOf(productionName),
        stopBefore = stopBefore,
        stopAfter = stopAfter,
        requireSome = requireSome,
    )

    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val input = context.input
        val output = context.output

        var nIndent = 0
        var after = position
        while (after < input.size) {
            val inp = input[after]
            if (inp is LeftParenthesis) {
                ++nIndent
            } else if (inp is RightParenthesis) {
                if (nIndent == 0) {
                    break
                }
                --nIndent
            } else if (inp is CstToken && nIndent == 0) {
                if (inp.tokenText in stopAfter) {
                    ++after
                    break
                }
                if (inp.tokenText in stopBefore) {
                    break
                }
            }
            after += 1
        }
        if (after > position) {
            output.add(ProductionFailedEvent(messageTemplate, messageValues, input.subList(position, after)))
            return after
        }
        return if (requireSome) -1 else after
    }

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component = GrammarDoc.NonTerminal("Garbage")

    override val children: List<Combinator> get() = listOf()
}

internal data class Problem(
    val matcher: Combinator,
    val messageTemplate: MessageTemplate,
) : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val input = context.input
        val output = context.output

        val after = matcher.apply(context, position)
        if (after > position) {
            output.add(KnownProblemEvent(messageTemplate, input.subList(position, after)))
            return after
        }
        return -1
    }

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component = GrammarDoc.Group(
        matcher.toGrammarDocDiagram(g, inlinable),
        GrammarDoc.Comment(messageTemplate.formatString),
    )

    override val children: List<Combinator> get() = listOf()
}

/** Dumps state before entering a combinator and eventual success/failure to console. */
internal data class Debug(val description: String, val body: Combinator) : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val input = context.input
        return console.group(
            "$description @ $position : ${(
                if (position < input.size) "${input[position]}" else "none"
                )}",
        ) {
            val result = body.apply(context, position)
            if (result >= 0) {
                console.log("Application succeeded -> $result")
            } else {
                console.log("Application failed")
            }
            result
        }
    }

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component = body.toGrammarDocDiagram(g, inlinable)

    override val children: List<Combinator> get() = listOf(body)
}

internal object Eof : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        return if (context.input.size == position) position else -1
    }

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component = GrammarDoc.Comment("end-of-file")

    override val children: List<Combinator> get() = listOf()
}

/**
 * Runs [c] in the scope of a [CombinatorContext.counterStack] element with the owner matching
 * [owner].  This allows counting how many split start [CstPart]s are pushed so that the same
 * number may be closed.
 */
internal class Counter(
    private val owner: Ref,
    private val c: Combinator,
) : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val count = MutIntCell()
        val entry = owner to count
        context.counterStack.add(entry)
        val result = c.apply(context, position)
        val removed = context.counterStack.compatRemoveLast()
        check(removed === entry)
        return result
    }

    // owner is not a child as it's never applied as part of this.apply.
    // owner is solely used to avoid interference between counter users.
    override val children get() = listOf(c)

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component = c.toGrammarDocDiagram(g, inlinable)
}

/**
 * Performs [repeated] for each count in the last counter on the
 * [counter stack][CombinatorContext.counterStack] matching [counterOwner].
 */
internal class CountForEach(
    private val counterOwner: Ref,
    private val repeated: Combinator,
) : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val (_, count) = context.counterStack.last { it.first == counterOwner }
        var result = position
        var remaining = count.i
        while (remaining > 0) {
            result = repeated.apply(context, result)
            if (result < 0) { break }
            remaining -= 1
        }
        return result
    }

    override val children get() = listOf(repeated)

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ): GrammarDoc.Component = repeated.toGrammarDocDiagram(g, inlinable)
}

/** Increments the last counter on the stack owned by [counterOwner]. */
internal class CountUp(
    private val counterOwner: Ref,
) : Combinator {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        context.counterStack.last { it.first == counterOwner }
            .second.i += 1
        return position
    }

    override val children get() = emptyList<Combinator>()

    override fun toGrammarDocDiagram(
        g: Productions<*>,
        inlinable: (Ref) -> Boolean,
    ) = GrammarDoc.Skip
}

internal abstract class ExtractCommentCombinator : Combinator {
    fun commentsAt(context: CombinatorContext<*>, position: Int): List<CstComment> {
        val input = context.input
        return if (position in input.indices) {
            val after = input[position].pos.leftEdge
            val before = run positionBefore@{
                var i = position
                while (--i >= 0) {
                    val part = input[i]
                    if (part is CstToken) {
                        return@positionBefore part.pos.rightEdge
                    }
                }
                Position(after.loc, 0, 0)
            }
            context.storedCommentTokens.commentsBetween(before, after)
        } else {
            emptyList()
        }
    }
}

internal class DecorateWithDocCommentCombinator(
    val decorated: Combinator,
) : ExtractCommentCombinator() {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val output = context.output
        val before = commentsAt(context, position)
            .filter {
                it.commentContent != null &&
                    commentAssociation(it.text, it.type) == LeftOrRight.Right
            }
        val outputOffsetBefore = output.size
        val positionAfter = decorated.apply(context, position)
        if (positionAfter > position) {
            val after = commentsAt(context, positionAfter).filter {
                it.commentContent != null &&
                    commentAssociation(it.text, it.type) == LeftOrRight.Left
            }
            var allComments = before + after
            val useSemilit = allComments.all { it.type == CommentType.SemilitParagraph }
            if (!useSemilit) {
                allComments = allComments.filter { it.type != CommentType.SemilitParagraph }
            }
            if (allComments.isNotEmpty()) {
                // `@docComment(decorated, ["comment content"])`
                val left = allComments.first().pos.leftEdge
                val right = allComments.last().pos.rightEdge
                val insertBefore = buildList {
                    add(StartTree(left)) // Start call.  Finished in insertAfter
                    add(StartTree(left)) // Start reference to docComment
                    add(ValuePart(vAttachCommentDecorator, left))
                    add(FinishTree(left, LeafTreeType.Value))
                }
                val insertAfter = buildList {
                    add(StartTree(left))
                    add(
                        ValuePart(
                            Value(
                                allComments.map { Value(it.commentContent!!, TString) },
                                TList,
                            ),
                            allComments.spanningPosition(allComments.first().pos),
                        ),
                    )
                    add(FinishTree(left, LeafTreeType.Value))
                    add(StartTree(left))
                    add(ValuePart(TBoolean.value(useSemilit), left))
                    add(FinishTree(left, LeafTreeType.Value))
                    add(FinishTree(right, InnerTreeType.Call))
                }
                output.addAll(outputOffsetBefore, insertBefore)
                output.addAll(insertAfter)
            }
        }
        return positionAfter
    }

    override val children: Iterable<Combinator> get() = listOf(decorated)

    override fun toGrammarDocDiagram(g: Productions<*>, inlinable: (Ref) -> Boolean): GrammarDoc.Component =
        decorated.toGrammarDocDiagram(g, inlinable)
}

internal object ExtractCommentsToCalls : ExtractCommentCombinator() {
    override fun apply(context: CombinatorContext<*>, position: Int): Int {
        val input = context.input
        if (position in input.indices) {
            val comments = commentsAt(context, position)
            val output = context.output
            comments.forEach {
                val commentContent = it.commentContent
                if (commentContent != null) {
                    val commentAssociation = when (commentAssociation(it.text, it.type)) {
                        LeftOrRight.Left -> TBoolean.valueFalse
                        LeftOrRight.Right -> TBoolean.valueTrue
                        null -> TNull.value
                    }
                    val isSemilitPara = TBoolean.value(it.type == CommentType.SemilitParagraph)
                    val left = it.pos.leftEdge
                    val right = it.pos.rightEdge
                    output.add(StartTree(left))
                    output.add(StartTree(left))
                    output.add(ValuePart(BuiltinFuns.vEmbeddedCommentFn, left))
                    output.add(FinishTree(left, LeafTreeType.Value))
                    output.add(StartTree(left))
                    output.add(ValuePart(Value(commentContent, TString), it.pos))
                    output.add(FinishTree(right, LeafTreeType.Value))
                    output.add(StartTree(left))
                    output.add(ValuePart(commentAssociation, right))
                    output.add(FinishTree(right, LeafTreeType.Value))
                    output.add(StartTree(left))
                    output.add(ValuePart(isSemilitPara, right))
                    output.add(FinishTree(right, LeafTreeType.Value))
                    output.add(FinishTree(right, InnerTreeType.Call))
                }
            }
        }
        return position
    }

    override val children: Iterable<Combinator> = emptyList()

    override fun toGrammarDocDiagram(g: Productions<*>, inlinable: (Ref) -> Boolean): GrammarDoc.Component =
        GrammarDoc.Skip
}

private fun Combinator.maybeInline(g: Productions<*>, inlinable: (Ref) -> Boolean) =
    if (this is Ref && inlinable(this)) {
        g.getProduction(this.name) ?: this
    } else {
        this
    }
