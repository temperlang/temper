@file:lang.temper.common.Generated("OutputGrammarCodeGenerator")
@file:Suppress("ktlint", "unused", "CascadeIf", "MagicNumber", "MemberNameEqualsClassName", "MemberVisibilityCanBePrivate")

package lang.temper.be.elixir
import lang.temper.ast.ChildMemberRelationships
import lang.temper.ast.OutData
import lang.temper.ast.OutTree
import lang.temper.ast.deepCopy
import lang.temper.be.BaseOutData
import lang.temper.be.BaseOutTree
import lang.temper.format.CodeFormattingTemplate
import lang.temper.format.FormattableTreeGroup
import lang.temper.format.FormattingHints
import lang.temper.format.IndexableFormattableTreeElement
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenAssociation
import lang.temper.format.TokenSink
import lang.temper.log.Position
import lang.temper.name.OutName
import lang.temper.name.name

object Elixir {
    sealed interface Tree : OutTree<Tree> {
        override fun formattingHints(): FormattingHints = ElixirFormattingHints.getInstance()
        override val operatorDefinition: ElixirOperatorDefinition?
        override fun deepCopy(): Tree
    }
    sealed class BaseTree(
        pos: Position,
    ) : BaseOutTree<Tree>(pos), Tree
    sealed interface Data : OutData<Data> {
        override fun formattingHints(): FormattingHints = ElixirFormattingHints.getInstance()
        override val operatorDefinition: ElixirOperatorDefinition?
    }
    sealed class BaseData : BaseOutData<Data>(), Data

    sealed interface Program : Tree {
        override fun deepCopy(): Program
    }

    class SourceFile(
        pos: Position,
        items: Iterable<TopLevel> = listOf(),
    ) : BaseTree(pos), Program {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate0
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.items)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _items: MutableList<TopLevel> = mutableListOf()
        var items: List<TopLevel>
            get() = _items
            set(newValue) { updateTreeConnections(_items, newValue) }
        override fun deepCopy(): SourceFile {
            return SourceFile(pos, items = this.items.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SourceFile && this.items == other.items
        }
        override fun hashCode(): Int {
            return items.hashCode()
        }
        init {
            updateTreeConnections(this._items, items)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SourceFile).items },
            )
        }
    }

    sealed interface TopLevel : Tree {
        override fun deepCopy(): TopLevel
    }

    sealed interface ModuleItem : Tree {
        override fun deepCopy(): ModuleItem
    }

    sealed interface BlockItem : Tree {
        override fun deepCopy(): BlockItem
    }

    class Comment(
        pos: Position,
        var text: String,
    ) : BaseTree(pos), TopLevel, ModuleItem, BlockItem {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.comment(elixirCommentText(text))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): Comment {
            return Comment(pos, text = this.text)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Comment && this.text == other.text
        }
        override fun hashCode(): Int {
            return text.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    sealed interface Expr : Tree, TopLevel, BlockItem {
        override fun deepCopy(): Expr
    }

    /** `defmodule Temper.Std.Io do ... end` */
    class ModuleDef(
        pos: Position,
        doc: Comment? = null,
        name: ModuleName,
        items: Iterable<ModuleItem> = listOf(),
    ) : BaseTree(pos), TopLevel, ModuleItem {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate1
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.doc ?: FormattableTreeGroup.empty
                1 -> this.name
                2 -> FormattableTreeGroup(this.items)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _doc: Comment?
        var doc: Comment?
            get() = _doc
            set(newValue) { _doc = updateTreeConnection(_doc, newValue) }
        private var _name: ModuleName
        var name: ModuleName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private val _items: MutableList<ModuleItem> = mutableListOf()
        var items: List<ModuleItem>
            get() = _items
            set(newValue) { updateTreeConnections(_items, newValue) }
        override fun deepCopy(): ModuleDef {
            return ModuleDef(pos, doc = this.doc?.deepCopy(), name = this.name.deepCopy(), items = this.items.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ModuleDef && this.doc == other.doc && this.name == other.name && this.items == other.items
        }
        override fun hashCode(): Int {
            var hc = doc.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + items.hashCode()
            return hc
        }
        init {
            this._doc = updateTreeConnection(null, doc)
            this._name = updateTreeConnection(null, name)
            updateTreeConnections(this._items, items)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ModuleDef).doc },
                { n -> (n as ModuleDef).name },
                { n -> (n as ModuleDef).items },
            )
        }
    }

    /** An alias: `Temper.Core`, each segment capitalised. */
    class ModuleName(
        pos: Position,
        segments: Iterable<Id>,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = ElixirOperatorDefinition.Postfix
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate2
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.segments)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _segments: MutableList<Id> = mutableListOf()
        var segments: List<Id>
            get() = _segments
            set(newValue) { updateTreeConnections(_segments, newValue) }
        override fun deepCopy(): ModuleName {
            return ModuleName(pos, segments = this.segments.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ModuleName && this.segments == other.segments
        }
        override fun hashCode(): Int {
            return segments.hashCode()
        }
        init {
            updateTreeConnections(this._segments, segments)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ModuleName).segments },
            )
        }
    }

    /**
     * `def name(a, b) when guard do ... end`, or `defp` when [isPrivate].
     *
     * Parameters are patterns: Elixir binds arguments by matching.
     */
    class FunDef(
        pos: Position,
        doc: Comment? = null,
        id: Id,
        params: Iterable<Pattern> = listOf(),
        guard: Expr? = null,
        body: Block,
        var isPrivate: Boolean = false,
    ) : BaseTree(pos), ModuleItem {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (isPrivate && guard != null) {
                    sharedCodeFormattingTemplate3
                } else if (isPrivate) {
                    sharedCodeFormattingTemplate4
                } else if (guard != null) {
                    sharedCodeFormattingTemplate5
                } else {
                    sharedCodeFormattingTemplate6
                }
        override val formatElementCount
            get() = 5
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.doc ?: FormattableTreeGroup.empty
                1 -> this.id
                2 -> FormattableTreeGroup(this.params)
                3 -> this.guard ?: FormattableTreeGroup.empty
                4 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _doc: Comment?
        var doc: Comment?
            get() = _doc
            set(newValue) { _doc = updateTreeConnection(_doc, newValue) }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private val _params: MutableList<Pattern> = mutableListOf()
        var params: List<Pattern>
            get() = _params
            set(newValue) { updateTreeConnections(_params, newValue) }
        private var _guard: Expr?
        var guard: Expr?
            get() = _guard
            set(newValue) { _guard = updateTreeConnection(_guard, newValue) }
        private var _body: Block
        var body: Block
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): FunDef {
            return FunDef(pos, doc = this.doc?.deepCopy(), id = this.id.deepCopy(), params = this.params.deepCopy(), guard = this.guard?.deepCopy(), body = this.body.deepCopy(), isPrivate = this.isPrivate)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FunDef && this.doc == other.doc && this.id == other.id && this.params == other.params && this.guard == other.guard && this.body == other.body && this.isPrivate == other.isPrivate
        }
        override fun hashCode(): Int {
            var hc = doc.hashCode()
            hc = 31 * hc + id.hashCode()
            hc = 31 * hc + params.hashCode()
            hc = 31 * hc + guard.hashCode()
            hc = 31 * hc + body.hashCode()
            hc = 31 * hc + isPrivate.hashCode()
            return hc
        }
        init {
            this._doc = updateTreeConnection(null, doc)
            this._id = updateTreeConnection(null, id)
            updateTreeConnections(this._params, params)
            this._guard = updateTreeConnection(null, guard)
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FunDef).doc },
                { n -> (n as FunDef).id },
                { n -> (n as FunDef).params },
                { n -> (n as FunDef).guard },
                { n -> (n as FunDef).body },
            )
        }
    }

    /** `@moduledoc false` */
    class ModuleAttr(
        pos: Position,
        id: Id,
        value: Expr,
    ) : BaseTree(pos), ModuleItem {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate7
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id
                1 -> this.value
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private var _value: Expr
        var value: Expr
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): ModuleAttr {
            return ModuleAttr(pos, id = this.id.deepCopy(), value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ModuleAttr && this.id == other.id && this.value == other.value
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + value.hashCode()
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ModuleAttr).id },
                { n -> (n as ModuleAttr).value },
            )
        }
    }

    /** `defstruct [:x, :y]` */
    class StructDef(
        pos: Position,
        fields: Iterable<Atom> = listOf(),
    ) : BaseTree(pos), ModuleItem {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate8
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.fields)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _fields: MutableList<Atom> = mutableListOf()
        var fields: List<Atom>
            get() = _fields
            set(newValue) { updateTreeConnections(_fields, newValue) }
        override fun deepCopy(): StructDef {
            return StructDef(pos, fields = this.fields.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StructDef && this.fields == other.fields
        }
        override fun hashCode(): Int {
            return fields.hashCode()
        }
        init {
            updateTreeConnections(this._fields, fields)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as StructDef).fields },
            )
        }
    }

    sealed interface Pattern : Tree {
        override fun deepCopy(): Pattern
    }

    /** A variable or function name. */
    class Id(
        pos: Position,
        var outName: OutName,
    ) : BaseTree(pos), Expr, Pattern {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.name(outName, inOperatorPosition = false)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): Id {
            return Id(pos, outName = this.outName)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Id && this.outName == other.outName
        }
        override fun hashCode(): Int {
            return outName.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /** `:ok`, or `:"with spaces"` when the text is not a plain identifier. */
    class Atom(
        pos: Position,
        var text: String,
    ) : BaseTree(pos), Expr, Pattern {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(OutputToken(elixirAtomText(text), OutputTokenType.OtherValue))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): Atom {
            return Atom(pos, text = this.text)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Atom && this.text == other.text
        }
        override fun hashCode(): Int {
            return text.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class Block(
        pos: Position,
        exprs: Iterable<BlockItem> = listOf(),
    ) : BaseTree(pos) {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate0
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.exprs)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _exprs: MutableList<BlockItem> = mutableListOf()
        var exprs: List<BlockItem>
            get() = _exprs
            set(newValue) { updateTreeConnections(_exprs, newValue) }
        override fun deepCopy(): Block {
            return Block(pos, exprs = this.exprs.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Block && this.exprs == other.exprs
        }
        override fun hashCode(): Int {
            return exprs.hashCode()
        }
        init {
            updateTreeConnections(this._exprs, exprs)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Block).exprs },
            )
        }
    }

    /** `f.(a, b)`: calling an anonymous function. */
    class AnonCall(
        pos: Position,
        fn: Expr,
        args: Iterable<Expr> = listOf(),
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = ElixirOperatorDefinition.Postfix
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate9
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.fn
                1 -> FormattableTreeGroup(this.args)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _fn: Expr
        var fn: Expr
            get() = _fn
            set(newValue) { _fn = updateTreeConnection(_fn, newValue) }
        private val _args: MutableList<Expr> = mutableListOf()
        var args: List<Expr>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): AnonCall {
            return AnonCall(pos, fn = this.fn.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AnonCall && this.fn == other.fn && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = fn.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._fn = updateTreeConnection(null, fn)
            updateTreeConnections(this._args, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AnonCall).fn },
                { n -> (n as AnonCall).args },
            )
        }
    }

    class BoolLit(
        pos: Position,
        var value: Boolean,
    ) : BaseTree(pos), Expr, Pattern {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (value) {
                    sharedCodeFormattingTemplate10
                } else {
                    sharedCodeFormattingTemplate11
                }
        override val formatElementCount
            get() = 0
        override fun deepCopy(): BoolLit {
            return BoolLit(pos, value = this.value)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BoolLit && this.value == other.value
        }
        override fun hashCode(): Int {
            return value.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /** A local call, `name(args)`. */
    class Call(
        pos: Position,
        callee: Id,
        args: Iterable<Expr> = listOf(),
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = ElixirOperatorDefinition.Postfix
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate12
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.callee
                1 -> FormattableTreeGroup(this.args)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _callee: Id
        var callee: Id
            get() = _callee
            set(newValue) { _callee = updateTreeConnection(_callee, newValue) }
        private val _args: MutableList<Expr> = mutableListOf()
        var args: List<Expr>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): Call {
            return Call(pos, callee = this.callee.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Call && this.callee == other.callee && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = callee.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._callee = updateTreeConnection(null, callee)
            updateTreeConnections(this._args, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Call).callee },
                { n -> (n as Call).args },
            )
        }
    }

    /** `&name/2`: a named function as a value. */
    class Capture(
        pos: Position,
        fn: Id,
        arity: NumberLit,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate13
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.fn
                1 -> this.arity
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _fn: Id
        var fn: Id
            get() = _fn
            set(newValue) { _fn = updateTreeConnection(_fn, newValue) }
        private var _arity: NumberLit
        var arity: NumberLit
            get() = _arity
            set(newValue) { _arity = updateTreeConnection(_arity, newValue) }
        override fun deepCopy(): Capture {
            return Capture(pos, fn = this.fn.deepCopy(), arity = this.arity.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Capture && this.fn == other.fn && this.arity == other.arity
        }
        override fun hashCode(): Int {
            var hc = fn.hashCode()
            hc = 31 * hc + arity.hashCode()
            return hc
        }
        init {
            this._fn = updateTreeConnection(null, fn)
            this._arity = updateTreeConnection(null, arity)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Capture).fn },
                { n -> (n as Capture).arity },
            )
        }
    }

    /** `case subject do pattern when guard -> body ... end` */
    class Case(
        pos: Position,
        subject: Expr,
        clauses: Iterable<Clause>,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate14
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.subject
                1 -> FormattableTreeGroup(this.clauses)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _subject: Expr
        var subject: Expr
            get() = _subject
            set(newValue) { _subject = updateTreeConnection(_subject, newValue) }
        private val _clauses: MutableList<Clause> = mutableListOf()
        var clauses: List<Clause>
            get() = _clauses
            set(newValue) { updateTreeConnections(_clauses, newValue) }
        override fun deepCopy(): Case {
            return Case(pos, subject = this.subject.deepCopy(), clauses = this.clauses.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Case && this.subject == other.subject && this.clauses == other.clauses
        }
        override fun hashCode(): Int {
            var hc = subject.hashCode()
            hc = 31 * hc + clauses.hashCode()
            return hc
        }
        init {
            this._subject = updateTreeConnection(null, subject)
            updateTreeConnections(this._clauses, clauses)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Case).subject },
                { n -> (n as Case).clauses },
            )
        }
    }

    /** `cond do test -> body ... end` */
    class Cond(
        pos: Position,
        arms: Iterable<CondArm>,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate15
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.arms)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _arms: MutableList<CondArm> = mutableListOf()
        var arms: List<CondArm>
            get() = _arms
            set(newValue) { updateTreeConnections(_arms, newValue) }
        override fun deepCopy(): Cond {
            return Cond(pos, arms = this.arms.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Cond && this.arms == other.arms
        }
        override fun hashCode(): Int {
            return arms.hashCode()
        }
        init {
            updateTreeConnections(this._arms, arms)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Cond).arms },
            )
        }
    }

    /** `[head | tail]` as an expression: prepend. */
    class ConsList(
        pos: Position,
        items: Iterable<Expr>,
        tail: Expr,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate16
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.items)
                1 -> this.tail
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _items: MutableList<Expr> = mutableListOf()
        var items: List<Expr>
            get() = _items
            set(newValue) { updateTreeConnections(_items, newValue) }
        private var _tail: Expr
        var tail: Expr
            get() = _tail
            set(newValue) { _tail = updateTreeConnection(_tail, newValue) }
        override fun deepCopy(): ConsList {
            return ConsList(pos, items = this.items.deepCopy(), tail = this.tail.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ConsList && this.items == other.items && this.tail == other.tail
        }
        override fun hashCode(): Int {
            var hc = items.hashCode()
            hc = 31 * hc + tail.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._items, items)
            this._tail = updateTreeConnection(null, tail)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ConsList).items },
                { n -> (n as ConsList).tail },
            )
        }
    }

    /** `point.x`: a struct's field, or a map's atom key. Raises KeyError if absent. */
    class Field(
        pos: Position,
        obj: Expr,
        id: Id,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = ElixirOperatorDefinition.Postfix
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate17
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.obj
                1 -> this.id
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _obj: Expr
        var obj: Expr
            get() = _obj
            set(newValue) { _obj = updateTreeConnection(_obj, newValue) }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        override fun deepCopy(): Field {
            return Field(pos, obj = this.obj.deepCopy(), id = this.id.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Field && this.obj == other.obj && this.id == other.id
        }
        override fun hashCode(): Int {
            var hc = obj.hashCode()
            hc = 31 * hc + id.hashCode()
            return hc
        }
        init {
            this._obj = updateTreeConnection(null, obj)
            this._id = updateTreeConnection(null, id)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Field).obj },
                { n -> (n as Field).id },
            )
        }
    }

    /** `fn a, b -> body end`, one clause. */
    class Fn(
        pos: Position,
        params: Iterable<Pattern> = listOf(),
        body: Block,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate18
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.params)
                1 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _params: MutableList<Pattern> = mutableListOf()
        var params: List<Pattern>
            get() = _params
            set(newValue) { updateTreeConnections(_params, newValue) }
        private var _body: Block
        var body: Block
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): Fn {
            return Fn(pos, params = this.params.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Fn && this.params == other.params && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = params.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._params, params)
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Fn).params },
                { n -> (n as Fn).body },
            )
        }
    }

    /** `if test do ... else ... end` */
    class If(
        pos: Position,
        test: Expr,
        then: Block,
        otherwise: Block? = null,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (otherwise != null) {
                    sharedCodeFormattingTemplate19
                } else {
                    sharedCodeFormattingTemplate20
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.test
                1 -> this.then
                2 -> this.otherwise ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _test: Expr
        var test: Expr
            get() = _test
            set(newValue) { _test = updateTreeConnection(_test, newValue) }
        private var _then: Block
        var then: Block
            get() = _then
            set(newValue) { _then = updateTreeConnection(_then, newValue) }
        private var _otherwise: Block?
        var otherwise: Block?
            get() = _otherwise
            set(newValue) { _otherwise = updateTreeConnection(_otherwise, newValue) }
        override fun deepCopy(): If {
            return If(pos, test = this.test.deepCopy(), then = this.then.deepCopy(), otherwise = this.otherwise?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is If && this.test == other.test && this.then == other.then && this.otherwise == other.otherwise
        }
        override fun hashCode(): Int {
            var hc = test.hashCode()
            hc = 31 * hc + then.hashCode()
            hc = 31 * hc + otherwise.hashCode()
            return hc
        }
        init {
            this._test = updateTreeConnection(null, test)
            this._then = updateTreeConnection(null, then)
            this._otherwise = updateTreeConnection(null, otherwise)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as If).test },
                { n -> (n as If).then },
                { n -> (n as If).otherwise },
            )
        }
    }

    class ListLit(
        pos: Position,
        items: Iterable<Expr> = listOf(),
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate21
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.items)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _items: MutableList<Expr> = mutableListOf()
        var items: List<Expr>
            get() = _items
            set(newValue) { updateTreeConnections(_items, newValue) }
        override fun deepCopy(): ListLit {
            return ListLit(pos, items = this.items.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ListLit && this.items == other.items
        }
        override fun hashCode(): Int {
            return items.hashCode()
        }
        init {
            updateTreeConnections(this._items, items)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ListLit).items },
            )
        }
    }

    /** `%{key => value}`. Always the arrow form, which takes any key. */
    class MapLit(
        pos: Position,
        entries: Iterable<MapEntry> = listOf(),
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate22
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.entries)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _entries: MutableList<MapEntry> = mutableListOf()
        var entries: List<MapEntry>
            get() = _entries
            set(newValue) { updateTreeConnections(_entries, newValue) }
        override fun deepCopy(): MapLit {
            return MapLit(pos, entries = this.entries.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MapLit && this.entries == other.entries
        }
        override fun hashCode(): Int {
            return entries.hashCode()
        }
        init {
            updateTreeConnections(this._entries, entries)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as MapLit).entries },
            )
        }
    }

    /** `%{map | key => value}`: replaces keys that already exist, raises otherwise. */
    class MapUpdate(
        pos: Position,
        base: Expr,
        entries: Iterable<MapEntry>,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate23
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.base
                1 -> FormattableTreeGroup(this.entries)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _base: Expr
        var base: Expr
            get() = _base
            set(newValue) { _base = updateTreeConnection(_base, newValue) }
        private val _entries: MutableList<MapEntry> = mutableListOf()
        var entries: List<MapEntry>
            get() = _entries
            set(newValue) { updateTreeConnections(_entries, newValue) }
        override fun deepCopy(): MapUpdate {
            return MapUpdate(pos, base = this.base.deepCopy(), entries = this.entries.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MapUpdate && this.base == other.base && this.entries == other.entries
        }
        override fun hashCode(): Int {
            var hc = base.hashCode()
            hc = 31 * hc + entries.hashCode()
            return hc
        }
        init {
            this._base = updateTreeConnection(null, base)
            updateTreeConnections(this._entries, entries)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as MapUpdate).base },
                { n -> (n as MapUpdate).entries },
            )
        }
    }

    /** `left = right`: Elixir's only binding form, a match. */
    class Match(
        pos: Position,
        left: Pattern,
        right: Expr,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = ElixirOperatorDefinition.Match
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate24
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.left
                1 -> this.right
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _left: Pattern
        var left: Pattern
            get() = _left
            set(newValue) { _left = updateTreeConnection(_left, newValue) }
        private var _right: Expr
        var right: Expr
            get() = _right
            set(newValue) { _right = updateTreeConnection(_right, newValue) }
        override fun deepCopy(): Match {
            return Match(pos, left = this.left.deepCopy(), right = this.right.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Match && this.left == other.left && this.right == other.right
        }
        override fun hashCode(): Int {
            var hc = left.hashCode()
            hc = 31 * hc + right.hashCode()
            return hc
        }
        init {
            this._left = updateTreeConnection(null, left)
            this._right = updateTreeConnection(null, right)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Match).left },
                { n -> (n as Match).right },
            )
        }
    }

    class NilLit(
        pos: Position,
    ) : BaseTree(pos), Expr, Pattern {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate25
        override val formatElementCount
            get() = 0
        override fun deepCopy(): NilLit {
            return NilLit(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is NilLit
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class NumberLit(
        pos: Position,
        var value: Number,
    ) : BaseTree(pos), Expr, Pattern {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.number(elixirNumberText(value))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): NumberLit {
            return NumberLit(pos, value = this.value)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is NumberLit && this.value == other.value
        }
        override fun hashCode(): Int {
            return value.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class Operation(
        pos: Position,
        left: Expr? = null,
        operator: Operator,
        right: Expr? = null,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = operator.operator.operatorDefinition
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (left != null && right != null) {
                    sharedCodeFormattingTemplate26
                } else if (left != null) {
                    sharedCodeFormattingTemplate27
                } else if (right != null) {
                    sharedCodeFormattingTemplate28
                } else {
                    sharedCodeFormattingTemplate29
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.left ?: FormattableTreeGroup.empty
                1 -> this.operator
                2 -> this.right ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _left: Expr?
        var left: Expr?
            get() = _left
            set(newValue) { _left = updateTreeConnection(_left, newValue) }
        private var _operator: Operator
        var operator: Operator
            get() = _operator
            set(newValue) { _operator = updateTreeConnection(_operator, newValue) }
        private var _right: Expr?
        var right: Expr?
            get() = _right
            set(newValue) { _right = updateTreeConnection(_right, newValue) }
        override fun deepCopy(): Operation {
            return Operation(pos, left = this.left?.deepCopy(), operator = this.operator.deepCopy(), right = this.right?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Operation && this.left == other.left && this.operator == other.operator && this.right == other.right
        }
        override fun hashCode(): Int {
            var hc = left.hashCode()
            hc = 31 * hc + operator.hashCode()
            hc = 31 * hc + right.hashCode()
            return hc
        }
        init {
            this._left = updateTreeConnection(null, left)
            this._operator = updateTreeConnection(null, operator)
            this._right = updateTreeConnection(null, right)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Operation).left },
                { n -> (n as Operation).operator },
                { n -> (n as Operation).right },
            )
        }
    }

    /** `Temper.Core.puts(x)`, `:erlang.abs(x)`. A field read is [Field]. */
    class RemoteCall(
        pos: Position,
        module: Expr,
        fn: Id,
        args: Iterable<Expr> = listOf(),
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = ElixirOperatorDefinition.Postfix
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate30
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.module
                1 -> this.fn
                2 -> FormattableTreeGroup(this.args)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _module: Expr
        var module: Expr
            get() = _module
            set(newValue) { _module = updateTreeConnection(_module, newValue) }
        private var _fn: Id
        var fn: Id
            get() = _fn
            set(newValue) { _fn = updateTreeConnection(_fn, newValue) }
        private val _args: MutableList<Expr> = mutableListOf()
        var args: List<Expr>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): RemoteCall {
            return RemoteCall(pos, module = this.module.deepCopy(), fn = this.fn.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is RemoteCall && this.module == other.module && this.fn == other.fn && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = module.hashCode()
            hc = 31 * hc + fn.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._module = updateTreeConnection(null, module)
            this._fn = updateTreeConnection(null, fn)
            updateTreeConnections(this._args, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as RemoteCall).module },
                { n -> (n as RemoteCall).fn },
                { n -> (n as RemoteCall).args },
            )
        }
    }

    class StringLit(
        pos: Position,
        var value: String,
    ) : BaseTree(pos), Expr, Pattern {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.quoted(elixirStringText(value))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): StringLit {
            return StringLit(pos, value = this.value)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StringLit && this.value == other.value
        }
        override fun hashCode(): Int {
            return value.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /** `%Point{x: 1, y: 2}` */
    class StructLit(
        pos: Position,
        name: ModuleName,
        fields: Iterable<KeywordEntry> = listOf(),
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate31
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name
                1 -> FormattableTreeGroup(this.fields)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _name: ModuleName
        var name: ModuleName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private val _fields: MutableList<KeywordEntry> = mutableListOf()
        var fields: List<KeywordEntry>
            get() = _fields
            set(newValue) { updateTreeConnections(_fields, newValue) }
        override fun deepCopy(): StructLit {
            return StructLit(pos, name = this.name.deepCopy(), fields = this.fields.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StructLit && this.name == other.name && this.fields == other.fields
        }
        override fun hashCode(): Int {
            var hc = name.hashCode()
            hc = 31 * hc + fields.hashCode()
            return hc
        }
        init {
            this._name = updateTreeConnection(null, name)
            updateTreeConnections(this._fields, fields)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as StructLit).name },
                { n -> (n as StructLit).fields },
            )
        }
    }

    /**
     * `try do ... rescue pattern -> ... catch kind, value -> ... end`
     *
     * Temper's bubbles are raised as exceptions and land in [rescues].
     */
    class Try(
        pos: Position,
        body: Block,
        rescues: Iterable<Clause> = listOf(),
        catches: Iterable<Clause> = listOf(),
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (rescues.isNotEmpty() && catches.isNotEmpty()) {
                    sharedCodeFormattingTemplate32
                } else if (rescues.isNotEmpty()) {
                    sharedCodeFormattingTemplate33
                } else if (catches.isNotEmpty()) {
                    sharedCodeFormattingTemplate34
                } else {
                    sharedCodeFormattingTemplate35
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.body
                1 -> FormattableTreeGroup(this.rescues)
                2 -> FormattableTreeGroup(this.catches)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _body: Block
        var body: Block
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        private val _rescues: MutableList<Clause> = mutableListOf()
        var rescues: List<Clause>
            get() = _rescues
            set(newValue) { updateTreeConnections(_rescues, newValue) }
        private val _catches: MutableList<Clause> = mutableListOf()
        var catches: List<Clause>
            get() = _catches
            set(newValue) { updateTreeConnections(_catches, newValue) }
        override fun deepCopy(): Try {
            return Try(pos, body = this.body.deepCopy(), rescues = this.rescues.deepCopy(), catches = this.catches.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Try && this.body == other.body && this.rescues == other.rescues && this.catches == other.catches
        }
        override fun hashCode(): Int {
            var hc = body.hashCode()
            hc = 31 * hc + rescues.hashCode()
            hc = 31 * hc + catches.hashCode()
            return hc
        }
        init {
            this._body = updateTreeConnection(null, body)
            updateTreeConnections(this._rescues, rescues)
            updateTreeConnections(this._catches, catches)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Try).body },
                { n -> (n as Try).rescues },
                { n -> (n as Try).catches },
            )
        }
    }

    class TupleLit(
        pos: Position,
        items: Iterable<Expr> = listOf(),
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate36
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.items)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _items: MutableList<Expr> = mutableListOf()
        var items: List<Expr>
            get() = _items
            set(newValue) { updateTreeConnections(_items, newValue) }
        override fun deepCopy(): TupleLit {
            return TupleLit(pos, items = this.items.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TupleLit && this.items == other.items
        }
        override fun hashCode(): Int {
            return items.hashCode()
        }
        init {
            updateTreeConnections(this._items, items)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TupleLit).items },
            )
        }
    }

    class Clause(
        pos: Position,
        pattern: Pattern,
        guard: Expr? = null,
        body: Block,
    ) : BaseTree(pos) {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (guard != null) {
                    sharedCodeFormattingTemplate37
                } else {
                    sharedCodeFormattingTemplate38
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.pattern
                1 -> this.guard ?: FormattableTreeGroup.empty
                2 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _pattern: Pattern
        var pattern: Pattern
            get() = _pattern
            set(newValue) { _pattern = updateTreeConnection(_pattern, newValue) }
        private var _guard: Expr?
        var guard: Expr?
            get() = _guard
            set(newValue) { _guard = updateTreeConnection(_guard, newValue) }
        private var _body: Block
        var body: Block
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): Clause {
            return Clause(pos, pattern = this.pattern.deepCopy(), guard = this.guard?.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Clause && this.pattern == other.pattern && this.guard == other.guard && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = pattern.hashCode()
            hc = 31 * hc + guard.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._pattern = updateTreeConnection(null, pattern)
            this._guard = updateTreeConnection(null, guard)
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Clause).pattern },
                { n -> (n as Clause).guard },
                { n -> (n as Clause).body },
            )
        }
    }

    class CondArm(
        pos: Position,
        test: Expr,
        body: Block,
    ) : BaseTree(pos) {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate39
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.test
                1 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _test: Expr
        var test: Expr
            get() = _test
            set(newValue) { _test = updateTreeConnection(_test, newValue) }
        private var _body: Block
        var body: Block
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): CondArm {
            return CondArm(pos, test = this.test.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is CondArm && this.test == other.test && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = test.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._test = updateTreeConnection(null, test)
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as CondArm).test },
                { n -> (n as CondArm).body },
            )
        }
    }

    class MapEntry(
        pos: Position,
        key: Expr,
        value: Expr,
    ) : BaseTree(pos) {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate40
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.key
                1 -> this.value
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _key: Expr
        var key: Expr
            get() = _key
            set(newValue) { _key = updateTreeConnection(_key, newValue) }
        private var _value: Expr
        var value: Expr
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): MapEntry {
            return MapEntry(pos, key = this.key.deepCopy(), value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MapEntry && this.key == other.key && this.value == other.value
        }
        override fun hashCode(): Int {
            var hc = key.hashCode()
            hc = 31 * hc + value.hashCode()
            return hc
        }
        init {
            this._key = updateTreeConnection(null, key)
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as MapEntry).key },
                { n -> (n as MapEntry).value },
            )
        }
    }

    /** `x: 1` -- the colon hugs the key. */
    class KeywordEntry(
        pos: Position,
        key: Id,
        value: Expr,
    ) : BaseTree(pos) {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate41
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.key
                1 -> this.value
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _key: Id
        var key: Id
            get() = _key
            set(newValue) { _key = updateTreeConnection(_key, newValue) }
        private var _value: Expr
        var value: Expr
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): KeywordEntry {
            return KeywordEntry(pos, key = this.key.deepCopy(), value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is KeywordEntry && this.key == other.key && this.value == other.value
        }
        override fun hashCode(): Int {
            var hc = key.hashCode()
            hc = 31 * hc + value.hashCode()
            return hc
        }
        init {
            this._key = updateTreeConnection(null, key)
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as KeywordEntry).key },
                { n -> (n as KeywordEntry).value },
            )
        }
    }

    class Operator(
        pos: Position,
        var operator: ElixirOperator,
    ) : BaseTree(pos) {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            operator.emit(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): Operator {
            return Operator(pos, operator = this.operator)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Operator && this.operator == other.operator
        }
        override fun hashCode(): Int {
            return operator.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class ConsPattern(
        pos: Position,
        head: Pattern,
        tail: Pattern,
    ) : BaseTree(pos), Pattern {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate42
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.head
                1 -> this.tail
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _head: Pattern
        var head: Pattern
            get() = _head
            set(newValue) { _head = updateTreeConnection(_head, newValue) }
        private var _tail: Pattern
        var tail: Pattern
            get() = _tail
            set(newValue) { _tail = updateTreeConnection(_tail, newValue) }
        override fun deepCopy(): ConsPattern {
            return ConsPattern(pos, head = this.head.deepCopy(), tail = this.tail.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ConsPattern && this.head == other.head && this.tail == other.tail
        }
        override fun hashCode(): Int {
            var hc = head.hashCode()
            hc = 31 * hc + tail.hashCode()
            return hc
        }
        init {
            this._head = updateTreeConnection(null, head)
            this._tail = updateTreeConnection(null, tail)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ConsPattern).head },
                { n -> (n as ConsPattern).tail },
            )
        }
    }

    class ListPattern(
        pos: Position,
        items: Iterable<Pattern> = listOf(),
    ) : BaseTree(pos), Pattern {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate21
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.items)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _items: MutableList<Pattern> = mutableListOf()
        var items: List<Pattern>
            get() = _items
            set(newValue) { updateTreeConnections(_items, newValue) }
        override fun deepCopy(): ListPattern {
            return ListPattern(pos, items = this.items.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ListPattern && this.items == other.items
        }
        override fun hashCode(): Int {
            return items.hashCode()
        }
        init {
            updateTreeConnections(this._items, items)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ListPattern).items },
            )
        }
    }

    /** `%{key => pattern}`: matches maps that have at least these keys. */
    class MapPattern(
        pos: Position,
        entries: Iterable<MapPatternEntry> = listOf(),
    ) : BaseTree(pos), Pattern {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate22
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.entries)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _entries: MutableList<MapPatternEntry> = mutableListOf()
        var entries: List<MapPatternEntry>
            get() = _entries
            set(newValue) { updateTreeConnections(_entries, newValue) }
        override fun deepCopy(): MapPattern {
            return MapPattern(pos, entries = this.entries.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MapPattern && this.entries == other.entries
        }
        override fun hashCode(): Int {
            return entries.hashCode()
        }
        init {
            updateTreeConnections(this._entries, entries)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as MapPattern).entries },
            )
        }
    }

    /** `^x`: match against x's value instead of rebinding x. */
    class Pin(
        pos: Position,
        id: Id,
    ) : BaseTree(pos), Pattern {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate43
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        override fun deepCopy(): Pin {
            return Pin(pos, id = this.id.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Pin && this.id == other.id
        }
        override fun hashCode(): Int {
            return id.hashCode()
        }
        init {
            this._id = updateTreeConnection(null, id)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Pin).id },
            )
        }
    }

    /** `e in TemperCore.Bubble`, only in a `rescue` clause. */
    class RescueIn(
        pos: Position,
        binding: Pattern,
        module: ModuleName,
    ) : BaseTree(pos), Pattern {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate44
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.binding
                1 -> this.module
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _binding: Pattern
        var binding: Pattern
            get() = _binding
            set(newValue) { _binding = updateTreeConnection(_binding, newValue) }
        private var _module: ModuleName
        var module: ModuleName
            get() = _module
            set(newValue) { _module = updateTreeConnection(_module, newValue) }
        override fun deepCopy(): RescueIn {
            return RescueIn(pos, binding = this.binding.deepCopy(), module = this.module.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is RescueIn && this.binding == other.binding && this.module == other.module
        }
        override fun hashCode(): Int {
            var hc = binding.hashCode()
            hc = 31 * hc + module.hashCode()
            return hc
        }
        init {
            this._binding = updateTreeConnection(null, binding)
            this._module = updateTreeConnection(null, module)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as RescueIn).binding },
                { n -> (n as RescueIn).module },
            )
        }
    }

    class StructPattern(
        pos: Position,
        name: ModuleName,
        fields: Iterable<KeywordPattern> = listOf(),
    ) : BaseTree(pos), Pattern {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate31
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name
                1 -> FormattableTreeGroup(this.fields)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _name: ModuleName
        var name: ModuleName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private val _fields: MutableList<KeywordPattern> = mutableListOf()
        var fields: List<KeywordPattern>
            get() = _fields
            set(newValue) { updateTreeConnections(_fields, newValue) }
        override fun deepCopy(): StructPattern {
            return StructPattern(pos, name = this.name.deepCopy(), fields = this.fields.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StructPattern && this.name == other.name && this.fields == other.fields
        }
        override fun hashCode(): Int {
            var hc = name.hashCode()
            hc = 31 * hc + fields.hashCode()
            return hc
        }
        init {
            this._name = updateTreeConnection(null, name)
            updateTreeConnections(this._fields, fields)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as StructPattern).name },
                { n -> (n as StructPattern).fields },
            )
        }
    }

    class TuplePattern(
        pos: Position,
        items: Iterable<Pattern> = listOf(),
    ) : BaseTree(pos), Pattern {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate36
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.items)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _items: MutableList<Pattern> = mutableListOf()
        var items: List<Pattern>
            get() = _items
            set(newValue) { updateTreeConnections(_items, newValue) }
        override fun deepCopy(): TuplePattern {
            return TuplePattern(pos, items = this.items.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TuplePattern && this.items == other.items
        }
        override fun hashCode(): Int {
            return items.hashCode()
        }
        init {
            updateTreeConnections(this._items, items)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TuplePattern).items },
            )
        }
    }

    class Wildcard(
        pos: Position,
    ) : BaseTree(pos), Pattern {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate45
        override val formatElementCount
            get() = 0
        override fun deepCopy(): Wildcard {
            return Wildcard(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Wildcard
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class MapPatternEntry(
        pos: Position,
        key: Expr,
        value: Pattern,
    ) : BaseTree(pos) {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate40
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.key
                1 -> this.value
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _key: Expr
        var key: Expr
            get() = _key
            set(newValue) { _key = updateTreeConnection(_key, newValue) }
        private var _value: Pattern
        var value: Pattern
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): MapPatternEntry {
            return MapPatternEntry(pos, key = this.key.deepCopy(), value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MapPatternEntry && this.key == other.key && this.value == other.value
        }
        override fun hashCode(): Int {
            var hc = key.hashCode()
            hc = 31 * hc + value.hashCode()
            return hc
        }
        init {
            this._key = updateTreeConnection(null, key)
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as MapPatternEntry).key },
                { n -> (n as MapPatternEntry).value },
            )
        }
    }

    class KeywordPattern(
        pos: Position,
        key: Id,
        value: Pattern,
    ) : BaseTree(pos) {
        override val operatorDefinition: ElixirOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate41
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.key
                1 -> this.value
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _key: Id
        var key: Id
            get() = _key
            set(newValue) { _key = updateTreeConnection(_key, newValue) }
        private var _value: Pattern
        var value: Pattern
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): KeywordPattern {
            return KeywordPattern(pos, key = this.key.deepCopy(), value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is KeywordPattern && this.key == other.key && this.value == other.value
        }
        override fun hashCode(): Int {
            var hc = key.hashCode()
            hc = 31 * hc + value.hashCode()
            return hc
        }
        init {
            this._key = updateTreeConnection(null, key)
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as KeywordPattern).key },
                { n -> (n as KeywordPattern).value },
            )
        }
    }

    /** `{{0*\n}}` */
    private val sharedCodeFormattingTemplate0 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.NewLine,
        )

    /** `{{0}} defmodule {{1}} do {{2*\n}} end` */
    private val sharedCodeFormattingTemplate1 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("defmodule", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `{{0*.}}` */
    private val sharedCodeFormattingTemplate2 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
        )

    /** `{{0}} defp {{1}} ( {{2*,}} ) when {{3}} do {{4}} end` */
    private val sharedCodeFormattingTemplate3 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("defp", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("when", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `{{0}} defp {{1}} ( {{2*,}} ) do {{4}} end` */
    private val sharedCodeFormattingTemplate4 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("defp", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `{{0}} def {{1}} ( {{2*,}} ) when {{3}} do {{4}} end` */
    private val sharedCodeFormattingTemplate5 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("def", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("when", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `{{0}} def {{1}} ( {{2*,}} ) do {{4}} end` */
    private val sharedCodeFormattingTemplate6 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("def", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `@ {{0}} {{1}}` */
    private val sharedCodeFormattingTemplate7 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("@", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `defstruct [ {{0*,}} ]` */
    private val sharedCodeFormattingTemplate8 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("defstruct", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}} . ( {{1*,}} )` */
    private val sharedCodeFormattingTemplate9 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `true` */
    private val sharedCodeFormattingTemplate10 =
        CodeFormattingTemplate.LiteralToken("true", OutputTokenType.Word)

    /** `false` */
    private val sharedCodeFormattingTemplate11 =
        CodeFormattingTemplate.LiteralToken("false", OutputTokenType.Word)

    /** `{{0}} ( {{1*,}} )` */
    private val sharedCodeFormattingTemplate12 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `& {{0}} / {{1}}` */
    private val sharedCodeFormattingTemplate13 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("\u0026", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("/", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `case {{0}} do {{1*\n}} end` */
    private val sharedCodeFormattingTemplate14 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("case", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `cond do {{0*\n}} end` */
    private val sharedCodeFormattingTemplate15 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("cond", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `[ {{0*,}} | {{1}} ]` */
    private val sharedCodeFormattingTemplate16 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("|", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}} . {{1}}` */
    private val sharedCodeFormattingTemplate17 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `fn {{0*,}} -> {{1}} end` */
    private val sharedCodeFormattingTemplate18 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `if {{0}} do {{1}} else {{2}} end` */
    private val sharedCodeFormattingTemplate19 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `if {{0}} do {{1}} end` */
    private val sharedCodeFormattingTemplate20 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `[ {{0*,}} ]` */
    private val sharedCodeFormattingTemplate21 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `%\{ {{0*,}} \}` */
    private val sharedCodeFormattingTemplate22 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("%{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `%\{ {{0}} | {{1*,}} \}` */
    private val sharedCodeFormattingTemplate23 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("%{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("|", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} = {{1}}` */
    private val sharedCodeFormattingTemplate24 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `nil` */
    private val sharedCodeFormattingTemplate25 =
        CodeFormattingTemplate.LiteralToken("nil", OutputTokenType.Word)

    /** `{{0}} {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate26 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0}} {{1}}` */
    private val sharedCodeFormattingTemplate27 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{1}} {{2}}` */
    private val sharedCodeFormattingTemplate28 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{1}}` */
    private val sharedCodeFormattingTemplate29 =
        CodeFormattingTemplate.OneSubstitution(1)

    /** `{{0}} . {{1}} ( {{2*,}} )` */
    private val sharedCodeFormattingTemplate30 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `% {{0}} \{ {{1*,}} \}` */
    private val sharedCodeFormattingTemplate31 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("%", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `try do {{0}} rescue {{1*\n}} catch {{2*\n}} end` */
    private val sharedCodeFormattingTemplate32 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("rescue", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("catch", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `try do {{0}} rescue {{1*\n}} end` */
    private val sharedCodeFormattingTemplate33 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("rescue", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `try do {{0}} catch {{2*\n}} end` */
    private val sharedCodeFormattingTemplate34 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("catch", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `try do {{0}} end` */
    private val sharedCodeFormattingTemplate35 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `\{ {{0*,}} \}` */
    private val sharedCodeFormattingTemplate36 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} when {{1}} -> {{2}}` */
    private val sharedCodeFormattingTemplate37 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("when", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0}} -> {{2}}` */
    private val sharedCodeFormattingTemplate38 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0}} -> {{1}}` */
    private val sharedCodeFormattingTemplate39 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} => {{1}}` */
    private val sharedCodeFormattingTemplate40 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} : {{1}}` */
    private val sharedCodeFormattingTemplate41 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `[ {{0}} | {{1}} ]` */
    private val sharedCodeFormattingTemplate42 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("|", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `^ {{0}}` */
    private val sharedCodeFormattingTemplate43 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("^", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `{{0}} in {{1}}` */
    private val sharedCodeFormattingTemplate44 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("in", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `_` */
    private val sharedCodeFormattingTemplate45 =
        CodeFormattingTemplate.LiteralToken("_", OutputTokenType.Word)
}
