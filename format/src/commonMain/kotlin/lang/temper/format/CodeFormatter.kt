package lang.temper.format

import lang.temper.common.LeftOrRight
import lang.temper.common.asciiUnTitleCase
import lang.temper.common.ignore
import lang.temper.common.jsonEscaper
import lang.temper.log.Positioned

/**
 * Part of a [FormattableTree]'s child list.  Either a child tree or a tree list.
 *
 * This allows easy indexing of groups of children as by
 * [CodeFormattingTemplate.GroupSubstitution].
 * The format string parsed by [CodeFormattingTemplate.fromFormatString] uses the `*` to
 * distinguish substitutions that might expect a group.
 *
 *     "fn {{0}} ( {{1*:,}} ) {\n{{2}}\n}"
 *
 * In that format string, the function parameters are grouped under index 1 even though
 * a function may have zero or more parameters.
 * The first element binds to `{{0}}`, the last to `{{2}}` and the ones in between to `{{1*:,}}`.
 */
sealed interface IndexableFormattableTreeElement {
    val formatElementCount: Int
    fun formatElement(index: Int): IndexableFormattableTreeElement
    fun isCurlyBracketBlock(): Boolean

    companion object {
        fun wrap(x: TokenSerializable): FormattableTree =
            object : FormattableTree {
                override val codeFormattingTemplate: CodeFormattingTemplate?
                    get() = null
                override val formatElementCount: Int get() = 0
                override fun formatElement(index: Int): IndexableFormattableTreeElement {
                    throw NoSuchElementException()
                }
                override val operatorDefinition: OperatorDefinition? get() = null
                override fun renderTo(tokenSink: TokenSink) {
                    x.renderTo(tokenSink)
                }
            }
        fun <T : Any> wrap(x: List<T>): IndexableFormattableTreeElement =
            object : FormattableTreeGroup {
                override fun formatElement(index: Int): FormattableTree = when (val e = x[index]) {
                    is FormattableTree -> e
                    is TokenSerializable -> wrap(e)
                    is CharSequence -> wrap(e)
                    is Number -> wrap(e)
                    is Boolean -> wrap(e)
                    is Enum<*> -> wrap(e)
                    else -> error("$e")
                }

                override val formatElementCount: Int get() = x.size
            }
        fun wrap(x: CharSequence): FormattableTree = wrap(
            object : TokenSerializable {
                override fun renderTo(tokenSink: TokenSink) {
                    tokenSink.quoted(jsonEscaper.escape(x))
                }
            },
        )
        fun wrap(x: Number): FormattableTree = wrap(
            object : TokenSerializable {
                override fun renderTo(tokenSink: TokenSink) {
                    tokenSink.number("$x")
                }
            },
        )
        fun wrap(x: Boolean): FormattableTree = wrap(
            object : TokenSerializable {
                override fun renderTo(tokenSink: TokenSink) {
                    tokenSink.word("$x")
                }
            },
        )
        fun wrap(x: Enum<*>): FormattableTree = wrap(
            object : TokenSerializable {
                override fun renderTo(tokenSink: TokenSink) {
                    tokenSink.word(x.name.asciiUnTitleCase())
                }
            },
        )
    }
}

/**
 * A tree that can be formatted to a [TokenSink] via a [CodeFormatter].
 */
interface FormattableTree : IndexableFormattableTreeElement, TokenSerializable {
    /** If `null`, then this must override renderTo to not rely on this being here. */
    val codeFormattingTemplate: CodeFormattingTemplate?
    override val formatElementCount: Int
    override fun formatElement(index: Int): IndexableFormattableTreeElement
    override fun isCurlyBracketBlock(): Boolean {
        val codeFormattingTemplate = this.codeFormattingTemplate
        val ftElements =
            (codeFormattingTemplate as? CodeFormattingTemplate.Concatenation)?.elements
                ?: emptyList()
        val firstElement = ftElements.firstOrNull()
        val lastElement = ftElements.lastOrNull()
        return firstElement.matchesToken(OutToks.leftCurly) &&
            lastElement.matchesToken(OutToks.rightCurly)
    }

    override fun renderTo(tokenSink: TokenSink) {
        val ft = codeFormattingTemplate
        if (ft == null) {
            error("${this::class} has a null CodeFormattingTemplate and does not customize renderTo")
        }
        val codeFormatter = CodeFormatter(tokenSink)
        codeFormatter.format(this)
    }

    /**
     * Used to determine whether the tree needs parentheses to embed in its parent.
     */
    val operatorDefinition: OperatorDefinition?

    fun canNest(
        child: FormattableTree,
        /**
         * The count of children preceding this.
         * The count of children may differ from the format element index, as
         * [FormattableTreeGroup]s may contain zero or more child trees.
         */
        childIndex: Int,
    ): Boolean {
        val parentOpDef = this.operatorDefinition ?: return true
        val childOpDef = child.operatorDefinition ?: return true
        return parentOpDef.canNest(childOpDef, childIndex)
    }
}

/** A group of formattable trees that can match a wildcard format placeholder like `{{1*,}}`. */
interface FormattableTreeGroup : IndexableFormattableTreeElement {
    override fun formatElement(index: Int): FormattableTree

    // Allowing groups to nest would make computing child indices complicated.
    override fun isCurlyBracketBlock(): Boolean = false

    companion object {
        operator fun invoke(trees: Iterable<FormattableTree>): FormattableTreeGroup =
            ListBackedFormattableTreeGroup(trees.toList())
        val empty: FormattableTreeGroup = ListBackedFormattableTreeGroup(emptyList())
    }
}

private data class ListBackedFormattableTreeGroup(
    val trees: List<FormattableTree>,
) : FormattableTreeGroup {
    override val formatElementCount: Int get() = trees.size
    override fun formatElement(index: Int) = trees[index]
}

/**
 * Lets ASTs  tell how to format themselves to a token stream via format strings.
 */
class CodeFormatter(
    private val tokenSink: TokenSink,
) {

    /** Formats children and slots them into placeholders in [FormattableTree.codeFormattingTemplate]. */
    fun format(tree: FormattableTree, skipOuterCurlies: Boolean = false) {
        val pos = if (tree is Positioned) { tree.pos } else { null }
        if (pos != null) {
            tokenSink.position(pos, LeftOrRight.Left)
        }
        var ft = tree.codeFormattingTemplate
        if (ft == null) {
            tree.renderTo(tokenSink)
            if (pos != null) {
                tokenSink.position(pos, LeftOrRight.Right)
            }
            return
        }

        if (skipOuterCurlies) {
            val concat = ft as? CodeFormattingTemplate.Concatenation
            if (concat != null) {
                val elements = concat.elements
                var i = 0
                var j = elements.size
                if (elements.getOrNull(i).matchesToken(OutToks.leftCurly)) {
                    i += 1
                    if (elements.getOrNull(i) == CodeFormattingTemplate.NewLine) {
                        i += 1
                    }
                }
                if (elements.getOrNull(j - 1).matchesToken(OutToks.rightCurly)) {
                    j -= 1
                    if (
                        j > i &&
                        // {\n} is an empty block
                        (elements.getOrNull(j - 1) == CodeFormattingTemplate.NewLine)
                    ) {
                        j -= 1
                    }
                }
                if (i != 0 && j != elements.size && i <= j) {
                    ft = concat.copy(elements = elements.subList(i, j))
                }
            }
        }

        val n = tree.formatElementCount
        var ftIndex = 0 // Index at start of unconsumed portion of ftElements
        val ftElements = ft.asConcatListElements()

        // Find the placeholders and their positions in ftElements
        val placeholders = ftElements.mapIndexedNotNull { index, t ->
            (t as? CodeFormattingTemplate.Substitution)?.let { t to index }
        }

        val childIndicesForFormatElementIndices = run {
            var childIndex = 0
            (0 until n).map { i ->
                val childIndexBefore = childIndex
                childIndex += when (val el = tree.formatElement(i)) {
                    is FormattableTree -> 1
                    is FormattableTreeGroup -> el.formatElementCount
                }
                childIndexBefore
            }
        }

        for ((placeholder, indexInFtElements) in placeholders) {
            val indexFromPlaceholder = placeholder.relativeIndex
            val elementSeparator = placeholder.elementSeparator

            // And we need to figure out which children we're substituting
            var skipCurlies = false

            val element = tree.formatElement(indexFromPlaceholder)

            var placeholderEnd = indexInFtElements + 1
            if (element.isCurlyBracketBlock()) {
                // If the placeholder is contained inside a { \n {{123...}} \n }
                // then we need to treat those brackets as redundant with any from the element.
                // This allows safe nesting by specifying an IF statement to look like
                //
                //       if ({{0}}) { \n {{1}} \n } else { \n {{2}} \n }
                //
                // And we can defensively make the substitutions form {{1}} and {{2}} be blocks
                // without getting a bunch of unnecessary nesting.
                if (ftElements.getOrNull(indexInFtElements - 2).matchesToken(OutToks.leftCurly) &&
                    ftElements.getOrNull(indexInFtElements - 1) is CodeFormattingTemplate.NewLine &&
                    ftElements.getOrNull(indexInFtElements + 1) is CodeFormattingTemplate.NewLine &&
                    ftElements.getOrNull(indexInFtElements + 2).matchesToken(OutToks.rightCurly)
                ) {
                    // Don't put { ... } inside { ... }
                    skipCurlies = true
                    if (element.formatElementCount == 0) {
                        // Skip the newline but use the close curly.  Leave as {\n}
                        placeholderEnd += 1
                    }
                }
            }

            emitTokens(ftElements, ftIndex, indexInFtElements)
            ftIndex = placeholderEnd

            val subTrees = when (element) {
                is FormattableTree -> {
                    check(elementSeparator == CodeFormattingTemplate.empty)
                    listOf(element)
                }
                is FormattableTreeGroup ->
                    (0 until element.formatElementCount).map {
                        element.formatElement(it)
                    }
            }

            // Parenthesize comma operators in parameter lists and lists of array
            // elements and object properties.
            val isCommaSeparated = elementSeparator.asConcatListElements().any { it.matchesToken(OutToks.comma) }

            val childIndex0 = childIndicesForFormatElementIndices[indexFromPlaceholder]
            for ((index, subTree) in subTrees.withIndex()) {
                if (index != 0) {
                    emitTokens(elementSeparator.asConcatListElements())
                }

                val parenthesize = !tree.canNest(
                    subTree,
                    childIndex0 + index,
                ) || (isCommaSeparated && subTree.operatorDefinition?.isCommaOperator == true)

                val (before, after) = if (parenthesize) {
                    (tokenSink as? FormattingTokenSink)
                        ?.formattingHints?.parenthesize(tree, subTree)
                        ?: parenthesizerPair
                } else {
                    doNothingDuo
                }

                before(tokenSink)
                format(subTree, skipOuterCurlies = skipCurlies)
                after(tokenSink)
            }
        }

        emitTokens(ftElements, ftIndex)

        if (pos != null) {
            tokenSink.position(pos, LeftOrRight.Right)
        }
    }

    private fun emitTokens(elements: List<CodeFormattingTemplate>, left: Int = 0, right: Int = elements.size) {
        for (i in left until right) {
            when (val element = elements[i]) {
                CodeFormattingTemplate.NewLine -> tokenSink.endLine()
                CodeFormattingTemplate.Space -> tokenSink.emit(OutToks.oneSpace)
                is CodeFormattingTemplate.LiteralToken -> tokenSink.emit(element.token)
                is CodeFormattingTemplate.Concatenation -> {
                    emitTokens(element.elements)
                }
                is CodeFormattingTemplate.Substitution -> error("$element")
            }
        }
    }
}

private val doNothingDuo = Pair(::ignore, ::ignore)

private fun CodeFormattingTemplate?.matchesToken(t: OutputToken): Boolean {
    val actual = (this as? CodeFormattingTemplate.LiteralToken)?.token
    return actual != null && t.type == actual.type && t.text == actual.text
}

private fun CodeFormattingTemplate.asConcatListElements() =
    (this as? CodeFormattingTemplate.Concatenation)?.elements
        ?: listOf(this)
