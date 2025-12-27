package lang.temper.astbuild

import lang.temper.common.jsonEscaper

/** https://github.com/tabatkins/railroad-diagrams/blob/gh-pages/README-js.md#diagrams */
data class GrammarDoc(
    val name: String,
    val body: Component,
) {
    internal enum class Precedence {
        OrOr,
        AndAnd,
        Or,
        And,
        Leaf,
        ;

        fun maybeParenthesize(inner: Precedence, content: String): String =
            if (this > inner) { "($content)" } else { content }
    }

    /** https://github.com/tabatkins/railroad-diagrams/blob/gh-pages/README-js.md#components */
    sealed class Component {
        abstract fun toBnf(): String
        internal abstract val precedence: Precedence
    }

    sealed class Leaf : Component() {
        override val precedence get() = Precedence.Leaf
    }

    sealed class Container : Component() {
        abstract val children: List<Component>
    }

    /**
     * represents literal text.
     *
     * @param text the literal text.
     * @param href makes the text a hyperlink with the given URL
     * @param title adds an SVG `<title>` element to the element, giving it "hover text" and a
     *     description for screen-readers and other assistive tech
     * @param cls is additional classes to apply to the element, beyond the default 'terminal'.
     */
    data class Terminal(
        val text: String,
        val href: String? = null,
        val title: String? = null,
        val cls: String? = null,
    ) : Leaf() {
        override fun toBnf(): String = jsonEscaper.escape(text)
    }

    /**
     * represents an instruction or another production.
     *
     * (This is a "leaf" in the diagram, but, since it is by-reference, it does not represent
     * a leaf in the parse graph.)
     *
     * @param text the instruction description or production name.
     * @param href makes the text a hyperlink with the given URL
     * @param title adds an SVG `<title>` element to the element, giving it "hover text" and a
     *     description for screen-readers and other assistive tech
     * @param cls is additional classes to apply to the element, beyond the default 'non-terminal'.
     */
    data class NonTerminal(
        val text: String,
        val href: String? = null,
        val title: String? = null,
        val cls: String? = null,
    ) : Leaf() {
        override fun toBnf(): String = text
    }

    /**
     * a comment.
     *
     * @param text the comment text
     * @param href makes the text a hyperlink with the given URL
     * @param title adds an SVG `<title>` element to the element, giving it "hover text" and a
     *     description for screen-readers and other assistive tech
     * @param cls is additional classes to apply to the element, beyond the default, 'comment'.
     */
    data class Comment(
        val text: String,
        val href: String? = null,
        val title: String? = null,
        val cls: String? = null,
    ) : Leaf() {
        override fun toBnf(): String = text
    }

    /** an empty line */
    object Skip : Leaf() {
        override fun toBnf(): String = "()"
    }

    /** like simple concatenation in a regex. */
    data class Sequence(
        override val children: List<Component>,
    ) : Container() {
        override val precedence: Precedence get() = Precedence.And

        init {
            check(children.isNotEmpty()) // The JS library hates empty sequences.
        }

        override fun toBnf(): String = children.joinToString(" ") {
            this.precedence.maybeParenthesize(it.precedence, it.toBnf())
        }
    }

    /**
     * identical to a [Sequence], but the items are stacked vertically rather than horizontally.
     * Best used when a simple Sequence would be too wide; instead, you can break the items up into
     * a Stack of Sequences of an appropriate width.
     */
    data class Stack(
        override val children: List<Component>,
    ) : Container() {
        override val precedence: Precedence get() = Precedence.And

        override fun toBnf(): String = children.joinToString(" ") {
            this.precedence.maybeParenthesize(it.precedence, it.toBnf())
        }
    }

    /**
     * like a [Sequence] where every item is individually optional,
     * but at least one item must be chosen.
     */
    data class OptionalSequence(
        override val children: List<Component>,
    ) : Container() {
        override val precedence: Precedence get() = Precedence.And

        override fun toBnf(): String = children.joinToString(" ") {
            "(${it.toBnf()})?"
        }
    }

    /**
     * like `|` in a regex. The index argument specifies which child is the "normal" choice and
     * should go in the middle.
     *
     * The *middle* of a choice is the one that continues the incoming line and outgoing lines
     * instead of branching off to the top or bottom.
     *
     *         ╭────top───╮
     *         │          │
     *     →───┼──middle──┼───→
     *         │          │
     *         ╰──bottom──╯
     */
    data class Choice(
        val index: Int,
        override val children: List<Component>,
    ) : Container() {
        override val precedence: Precedence get() = Precedence.Or

        override fun toBnf(): String = children.joinToString(" | ") {
            this.precedence.maybeParenthesize(it.precedence, it.toBnf())
        }
    }

    /**
     * like `||` or `&&` in a CSS grammar; it's similar to a [Choice],
     * but more than one branch can be taken.
     * The index argument specifies which child is the "normal" choice and should go in the middle,
     * while the type argument must be either "any" (1+ branches can be taken) or "all"
     * (all branches must be taken).
     */
    data class MultipleChoice(
        val index: Int,
        val type: AnyOrAll,
        override val children: List<Component>,
    ) : Container() {
        enum class AnyOrAll { Any, All }

        override val precedence: Precedence get() = when (type) {
            AnyOrAll.Any -> Precedence.OrOr
            AnyOrAll.All -> Precedence.AndAnd
        }

        override fun toBnf(): String {
            val precedence = this.precedence
            val op = when (type) {
                AnyOrAll.Any -> "||"
                AnyOrAll.All -> "&&"
            }
            return children.joinToString(op) {
                precedence.maybeParenthesize(it.precedence, it.toBnf())
            }
        }
    }

    /**
     * Identical to [Choice], but the items are stacked horizontally rather than vertically.
     * There's no "straight-line" choice, so it just takes a list of children.
     * Best used when a simple Choice would be too tall; instead, you can break up the items into a
     * HorizontalChoice of [Choice]s of an appropriate height.
     */
    data class HorizontalChoice(
        override val children: List<Component>,
    ) : Container() {
        override val precedence: Precedence get() = Precedence.Or

        override fun toBnf(): String = children.joinToString(" | ") {
            this.precedence.maybeParenthesize(it.precedence, it.toBnf())
        }
    }

    /**
     * like `?` in a regex. A shorthand for [Choice]\(1, [Skip]\(\), [child]\).
     * If the optional skip parameter has the value "skip", it instead puts the [Skip] in the
     * straight-line path, for when the "normal" behavior is to omit the item.
     */
    data class Optional(
        val child: Component,
        val skip: SkipOrNoSkip = SkipOrNoSkip.NoSkip,
    ) : Container() {
        override val children: List<Component> get() = listOf(child)

        override val precedence: Precedence
            get() = Precedence.Leaf

        override fun toBnf() = "${precedence.maybeParenthesize(child.precedence, child.toBnf())}?"
    }

    enum class SkipOrNoSkip {
        Skip,
        NoSkip,
    }

    /**
     * like `+` in a regex.
     * The 'repeat' argument is optional, and specifies something that must go between the
     * repetitions (usually a [Comment], but sometimes things like ",", etc.).
     */
    data class OneOrMore(
        val child: Component,
        val repeat: Component?,
    ) : Container() {
        override val children: List<Component> get() = listOfNotNull(child, repeat)

        override val precedence: Precedence
            get() = Precedence.Leaf

        override fun toBnf() =
            "${precedence.maybeParenthesize(child.precedence, child.toBnf())}+${
                if (repeat == null) { "" } else { "(${repeat.toBnf()})" }
            }"
    }

    /**
     * similar to a [OneOrMore], where you must alternate between the two choices, but allows you
     * to start and end with either element.
     * (OneOrMore requires you to start and end with the "child" node.)
     */
    data class AlternatingSequence(
        val option1: Component,
        val option2: Component,
    ) : Container() {
        override val children: List<Component> get() = listOf(option1, option2)

        override val precedence: Precedence
            get() = Precedence.Leaf

        override fun toBnf(): String = "alternating(${option1.toBnf()}, ${option2.toBnf()})"
    }

    /**
     * like `*` in a regex.
     * A shorthand for [Optional]\([OneOrMore]\([child], [repeat]\), [skip]\).
     * Both `repeat` (same as in [OneOrMore]) and `skip` (same as in [Optional]) are optional.
     */
    data class ZeroOrMore(
        val child: Component,
        val repeat: Component?,
        val skip: SkipOrNoSkip = SkipOrNoSkip.NoSkip,
    ) : Container() {
        override val children: List<Component> get() = listOfNotNull(child, repeat)

        override val precedence: Precedence
            get() = Precedence.Leaf

        override fun toBnf(): String =
            "${precedence.maybeParenthesize(child.precedence, child.toBnf())}*${
                if (repeat == null) {
                    ""
                } else {
                    "(${repeat.toBnf()})"
                }
            }"
    }

    /**
     * highlights its child with a dashed outline, and optionally labels it.
     */
    data class Group(
        val child: Component,
        val label: Comment?,
    ) : Container() {
        override val children: List<Component> get() = listOfNotNull(child, label)

        override val precedence: Precedence
            get() = Precedence.Leaf

        override fun toBnf(): String = if (label != null) {
            "(${label.text}: ${child.toBnf()})"
        } else {
            "(${child.toBnf()})"
        }
    }
}
