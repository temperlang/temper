@file:lang.temper.common.Generated("OutputGrammarCodeGenerator")
@file:Suppress("ktlint", "unused", "CascadeIf", "MagicNumber", "MemberNameEqualsClassName", "MemberVisibilityCanBePrivate")

package lang.temper.be.rust
import lang.temper.ast.ChildMemberRelationships
import lang.temper.ast.OutData
import lang.temper.ast.OutTree
import lang.temper.ast.deepCopy
import lang.temper.be.BaseOutData
import lang.temper.be.BaseOutTree
import lang.temper.format.CodeFormattingTemplate
import lang.temper.format.FormattableEnum
import lang.temper.format.FormattableTreeGroup
import lang.temper.format.FormattingHints
import lang.temper.format.IndexableFormattableTreeElement
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenAssociation
import lang.temper.format.TokenSink
import lang.temper.log.Position
import lang.temper.name.OutName
import lang.temper.name.name

object Rust {
    sealed interface Tree : OutTree<Tree> {
        override fun formattingHints(): FormattingHints = RustFormattingHints.getInstance()
        override val operatorDefinition: RustOperatorDefinition?
        override fun deepCopy(): Tree
    }
    sealed class BaseTree(
        pos: Position,
    ) : BaseOutTree<Tree>(pos), Tree
    sealed interface Data : OutData<Data> {
        override fun formattingHints(): FormattingHints = RustFormattingHints.getInstance()
        override val operatorDefinition: RustOperatorDefinition?
    }
    sealed class BaseData : BaseOutData<Data>(), Data

    enum class VisibilityScopeOption : FormattableEnum {
        Crate,
        Self,
        Super,
    }

    sealed interface Program : Tree {
        override fun deepCopy(): Program
    }

    class SourceFile(
        pos: Position,
        attrs: Iterable<AttrInner> = listOf(),
        items: Iterable<Item>,
    ) : BaseTree(pos), Program {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate0
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.attrs)
                1 -> FormattableTreeGroup(this.items)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _attrs: MutableList<AttrInner> = mutableListOf()
        var attrs: List<AttrInner>
            get() = _attrs
            set(newValue) { updateTreeConnections(_attrs, newValue) }
        private val _items: MutableList<Item> = mutableListOf()
        var items: List<Item>
            get() = _items
            set(newValue) { updateTreeConnections(_items, newValue) }
        override fun deepCopy(): SourceFile {
            return SourceFile(pos, attrs = this.attrs.deepCopy(), items = this.items.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SourceFile && this.attrs == other.attrs && this.items == other.items
        }
        override fun hashCode(): Int {
            var hc = attrs.hashCode()
            hc = 31 * hc + items.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._attrs, attrs)
            updateTreeConnections(this._items, items)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SourceFile).attrs },
                { n -> (n as SourceFile).items },
            )
        }
    }

    class AttrInner(
        pos: Position,
        expr: Expr,
    ) : BaseTree(pos) {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate1
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expr
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expr: Expr
        var expr: Expr
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): AttrInner {
            return AttrInner(pos, expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AttrInner && this.expr == other.expr
        }
        override fun hashCode(): Int {
            return expr.hashCode()
        }
        init {
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AttrInner).expr },
            )
        }
    }

    sealed interface Statement : Tree {
        override fun deepCopy(): Statement
    }

    class Item(
        pos: Position,
        attrs: Iterable<AttrOuter> = listOf(),
        pub: VisibilityPub? = null,
        item: ItemBase,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (pub != null) {
                    sharedCodeFormattingTemplate2
                } else {
                    sharedCodeFormattingTemplate3
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.attrs)
                1 -> this.pub ?: FormattableTreeGroup.empty
                2 -> this.item
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _attrs: MutableList<AttrOuter> = mutableListOf()
        var attrs: List<AttrOuter>
            get() = _attrs
            set(newValue) { updateTreeConnections(_attrs, newValue) }
        private var _pub: VisibilityPub?
        var pub: VisibilityPub?
            get() = _pub
            set(newValue) { _pub = updateTreeConnection(_pub, newValue) }
        private var _item: ItemBase
        var item: ItemBase
            get() = _item
            set(newValue) { _item = updateTreeConnection(_item, newValue) }
        override fun deepCopy(): Item {
            return Item(pos, attrs = this.attrs.deepCopy(), pub = this.pub?.deepCopy(), item = this.item.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Item && this.attrs == other.attrs && this.pub == other.pub && this.item == other.item
        }
        override fun hashCode(): Int {
            var hc = attrs.hashCode()
            hc = 31 * hc + (pub?.hashCode() ?: 0)
            hc = 31 * hc + item.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._attrs, attrs)
            this._pub = updateTreeConnection(null, pub)
            this._item = updateTreeConnection(null, item)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Item).attrs },
                { n -> (n as Item).pub },
                { n -> (n as Item).item },
            )
        }
    }

    sealed interface Expr : Tree {
        override fun deepCopy(): Expr
    }

    sealed interface ExprWithoutBlock : Tree, Expr {
        override fun deepCopy(): ExprWithoutBlock
    }

    sealed interface MacroArgs : Tree {
        override fun deepCopy(): MacroArgs
    }

    class Array(
        pos: Position,
        values: Iterable<Expr>,
    ) : BaseTree(pos), ExprWithoutBlock, MacroArgs {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate4
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.values)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _values: MutableList<Expr> = mutableListOf()
        var values: List<Expr>
            get() = _values
            set(newValue) { updateTreeConnections(_values, newValue) }
        override fun deepCopy(): Array {
            return Array(pos, values = this.values.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Array && this.values == other.values
        }
        override fun hashCode(): Int {
            return values.hashCode()
        }
        init {
            updateTreeConnections(this._values, values)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Array).values },
            )
        }
    }

    class AttrOuter(
        pos: Position,
        expr: Expr,
    ) : BaseTree(pos) {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate5
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expr
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expr: Expr
        var expr: Expr
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): AttrOuter {
            return AttrOuter(pos, expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AttrOuter && this.expr == other.expr
        }
        override fun hashCode(): Int {
            return expr.hashCode()
        }
        init {
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AttrOuter).expr },
            )
        }
    }

    sealed interface ExprWithBlock : Tree, Expr, Statement {
        override fun deepCopy(): ExprWithBlock
    }

    class AttrExprWithBlock(
        pos: Position,
        attrs: Iterable<AttrOuter>,
        expr: ExprWithBlock,
    ) : BaseTree(pos), ExprWithBlock {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate6
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.attrs)
                1 -> this.expr
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _attrs: MutableList<AttrOuter> = mutableListOf()
        var attrs: List<AttrOuter>
            get() = _attrs
            set(newValue) { updateTreeConnections(_attrs, newValue) }
        private var _expr: ExprWithBlock
        var expr: ExprWithBlock
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): AttrExprWithBlock {
            return AttrExprWithBlock(pos, attrs = this.attrs.deepCopy(), expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AttrExprWithBlock && this.attrs == other.attrs && this.expr == other.expr
        }
        override fun hashCode(): Int {
            var hc = attrs.hashCode()
            hc = 31 * hc + expr.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._attrs, attrs)
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AttrExprWithBlock).attrs },
                { n -> (n as AttrExprWithBlock).expr },
            )
        }
    }

    class AttrExprWithoutBlock(
        pos: Position,
        attrs: Iterable<AttrOuter>,
        expr: ExprWithoutBlock,
    ) : BaseTree(pos), ExprWithoutBlock {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate6
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.attrs)
                1 -> this.expr
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _attrs: MutableList<AttrOuter> = mutableListOf()
        var attrs: List<AttrOuter>
            get() = _attrs
            set(newValue) { updateTreeConnections(_attrs, newValue) }
        private var _expr: ExprWithoutBlock
        var expr: ExprWithoutBlock
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): AttrExprWithoutBlock {
            return AttrExprWithoutBlock(pos, attrs = this.attrs.deepCopy(), expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AttrExprWithoutBlock && this.attrs == other.attrs && this.expr == other.expr
        }
        override fun hashCode(): Int {
            var hc = attrs.hashCode()
            hc = 31 * hc + expr.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._attrs, attrs)
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AttrExprWithoutBlock).attrs },
                { n -> (n as AttrExprWithoutBlock).expr },
            )
        }
    }

    sealed interface IfOrBlock : Tree {
        override fun deepCopy(): IfOrBlock
    }

    class Block(
        pos: Position,
        attrs: Iterable<AttrInner> = listOf(),
        statements: Iterable<Statement> = listOf(),
        result: Expr? = null,
    ) : BaseTree(pos), ExprWithBlock, IfOrBlock, MacroArgs {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (result != null) {
                    sharedCodeFormattingTemplate7
                } else {
                    sharedCodeFormattingTemplate8
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.attrs)
                1 -> FormattableTreeGroup(this.statements)
                2 -> this.result ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _attrs: MutableList<AttrInner> = mutableListOf()
        var attrs: List<AttrInner>
            get() = _attrs
            set(newValue) { updateTreeConnections(_attrs, newValue) }
        private val _statements: MutableList<Statement> = mutableListOf()
        var statements: List<Statement>
            get() = _statements
            set(newValue) { updateTreeConnections(_statements, newValue) }
        private var _result: Expr?
        var result: Expr?
            get() = _result
            set(newValue) { _result = updateTreeConnection(_result, newValue) }
        override fun deepCopy(): Block {
            return Block(pos, attrs = this.attrs.deepCopy(), statements = this.statements.deepCopy(), result = this.result?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Block && this.attrs == other.attrs && this.statements == other.statements && this.result == other.result
        }
        override fun hashCode(): Int {
            var hc = attrs.hashCode()
            hc = 31 * hc + statements.hashCode()
            hc = 31 * hc + (result?.hashCode() ?: 0)
            return hc
        }
        init {
            updateTreeConnections(this._attrs, attrs)
            updateTreeConnections(this._statements, statements)
            this._result = updateTreeConnection(null, result)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Block).attrs },
                { n -> (n as Block).statements },
                { n -> (n as Block).result },
            )
        }
    }

    class BreakExpr(
        pos: Position,
        id: Id?,
        value: Expr? = null,
    ) : BaseTree(pos), ExprWithoutBlock {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (id != null && value != null) {
                    sharedCodeFormattingTemplate9
                } else if (id != null) {
                    sharedCodeFormattingTemplate10
                } else if (value != null) {
                    sharedCodeFormattingTemplate11
                } else {
                    sharedCodeFormattingTemplate12
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id ?: FormattableTreeGroup.empty
                1 -> this.value ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Id?
        var id: Id?
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private var _value: Expr?
        var value: Expr?
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): BreakExpr {
            return BreakExpr(pos, id = this.id?.deepCopy(), value = this.value?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BreakExpr && this.id == other.id && this.value == other.value
        }
        override fun hashCode(): Int {
            var hc = (id?.hashCode() ?: 0)
            hc = 31 * hc + (value?.hashCode() ?: 0)
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as BreakExpr).id },
                { n -> (n as BreakExpr).value },
            )
        }
    }

    sealed interface FunctionParamOption : Tree {
        override fun deepCopy(): FunctionParamOption
    }

    sealed interface GenericParam : Tree {
        override fun deepCopy(): GenericParam
    }

    sealed interface GenericArg : Tree {
        override fun deepCopy(): GenericArg
    }

    sealed interface TypeParamBound : Tree {
        override fun deepCopy(): TypeParamBound
    }

    sealed interface Type : Tree, ExprWithoutBlock, GenericArg, TypeParamBound {
        override fun deepCopy(): Type
    }

    sealed interface UseWhat : Tree {
        override fun deepCopy(): UseWhat
    }

    sealed interface Path : Tree, ExprWithoutBlock, GenericParam, Type, UseWhat {
        override fun deepCopy(): Path
    }

    sealed interface PathSegment : Tree {
        override fun deepCopy(): PathSegment
    }

    sealed interface PatternAny : Tree {
        override fun deepCopy(): PatternAny
    }

    sealed interface Pattern : Tree, PatternAny {
        override fun deepCopy(): Pattern
    }

    class Id(
        pos: Position,
        var outName: OutName,
    ) : BaseTree(pos), FunctionParamOption, Path, PathSegment, Pattern {
        override val operatorDefinition: RustOperatorDefinition?
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

    class Call(
        pos: Position,
        callee: Expr,
        args: Iterable<Expr>,
        var needsParens: Boolean = (callee as? Operation)?.operator?.operator == RustOperator.MemberNotMethod,
    ) : BaseTree(pos), ExprWithoutBlock {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (needsParens) {
                    sharedCodeFormattingTemplate13
                } else {
                    sharedCodeFormattingTemplate14
                }
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
        private var _callee: Expr
        var callee: Expr
            get() = _callee
            set(newValue) { _callee = updateTreeConnection(_callee, newValue) }
        private val _args: MutableList<Expr> = mutableListOf()
        var args: List<Expr>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): Call {
            return Call(pos, callee = this.callee.deepCopy(), args = this.args.deepCopy(), needsParens = this.needsParens)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Call && this.callee == other.callee && this.args == other.args && this.needsParens == other.needsParens
        }
        override fun hashCode(): Int {
            var hc = callee.hashCode()
            hc = 31 * hc + args.hashCode()
            hc = 31 * hc + needsParens.hashCode()
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

    class Closure(
        pos: Position,
        move: Move? = null,
        params: Iterable<FunctionParamOption>,
        returnType: Type? = null,
        value: Expr,
    ) : BaseTree(pos), ExprWithoutBlock {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (move != null && returnType != null) {
                    sharedCodeFormattingTemplate15
                } else if (move != null) {
                    sharedCodeFormattingTemplate16
                } else if (returnType != null) {
                    sharedCodeFormattingTemplate17
                } else {
                    sharedCodeFormattingTemplate18
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.move ?: FormattableTreeGroup.empty
                1 -> FormattableTreeGroup(this.params)
                2 -> this.returnType ?: FormattableTreeGroup.empty
                3 -> this.value
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _move: Move?
        var move: Move?
            get() = _move
            set(newValue) { _move = updateTreeConnection(_move, newValue) }
        private val _params: MutableList<FunctionParamOption> = mutableListOf()
        var params: List<FunctionParamOption>
            get() = _params
            set(newValue) { updateTreeConnections(_params, newValue) }
        private var _returnType: Type?
        var returnType: Type?
            get() = _returnType
            set(newValue) { _returnType = updateTreeConnection(_returnType, newValue) }
        private var _value: Expr
        var value: Expr
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): Closure {
            return Closure(pos, move = this.move?.deepCopy(), params = this.params.deepCopy(), returnType = this.returnType?.deepCopy(), value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Closure && this.move == other.move && this.params == other.params && this.returnType == other.returnType && this.value == other.value
        }
        override fun hashCode(): Int {
            var hc = (move?.hashCode() ?: 0)
            hc = 31 * hc + params.hashCode()
            hc = 31 * hc + (returnType?.hashCode() ?: 0)
            hc = 31 * hc + value.hashCode()
            return hc
        }
        init {
            this._move = updateTreeConnection(null, move)
            updateTreeConnections(this._params, params)
            this._returnType = updateTreeConnection(null, returnType)
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Closure).move },
                { n -> (n as Closure).params },
                { n -> (n as Closure).returnType },
                { n -> (n as Closure).value },
            )
        }
    }

    class Move(
        pos: Position,
    ) : BaseTree(pos) {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate19
        override val formatElementCount
            get() = 0
        override fun deepCopy(): Move {
            return Move(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Move
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class ContinueExpr(
        pos: Position,
        id: Id?,
    ) : BaseTree(pos), ExprWithoutBlock {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (id != null) {
                    sharedCodeFormattingTemplate20
                } else {
                    sharedCodeFormattingTemplate21
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Id?
        var id: Id?
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        override fun deepCopy(): ContinueExpr {
            return ContinueExpr(pos, id = this.id?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ContinueExpr && this.id == other.id
        }
        override fun hashCode(): Int {
            return (id?.hashCode() ?: 0)
        }
        init {
            this._id = updateTreeConnection(null, id)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ContinueExpr).id },
            )
        }
    }

    sealed interface ItemBase : Tree {
        override fun deepCopy(): ItemBase
    }

    class Enum(
        pos: Position,
        id: Id,
        items: Iterable<EnumItem>,
    ) : BaseTree(pos), ItemBase {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate22
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id
                1 -> FormattableTreeGroup(this.items)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private val _items: MutableList<EnumItem> = mutableListOf()
        var items: List<EnumItem>
            get() = _items
            set(newValue) { updateTreeConnections(_items, newValue) }
        override fun deepCopy(): Enum {
            return Enum(pos, id = this.id.deepCopy(), items = this.items.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Enum && this.id == other.id && this.items == other.items
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + items.hashCode()
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            updateTreeConnections(this._items, items)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Enum).id },
                { n -> (n as Enum).items },
            )
        }
    }

    sealed interface EnumItem : Tree {
        override fun deepCopy(): EnumItem
    }

    class EnumItemStruct(
        pos: Position,
        id: Id,
        fields: Iterable<StructField>,
    ) : BaseTree(pos), EnumItem {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate23
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id
                1 -> FormattableTreeGroup(this.fields)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private val _fields: MutableList<StructField> = mutableListOf()
        var fields: List<StructField>
            get() = _fields
            set(newValue) { updateTreeConnections(_fields, newValue) }
        override fun deepCopy(): EnumItemStruct {
            return EnumItemStruct(pos, id = this.id.deepCopy(), fields = this.fields.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is EnumItemStruct && this.id == other.id && this.fields == other.fields
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + fields.hashCode()
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            updateTreeConnections(this._fields, fields)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as EnumItemStruct).id },
                { n -> (n as EnumItemStruct).fields },
            )
        }
    }

    class EnumItemTuple(
        pos: Position,
        id: Id,
        fields: Iterable<TupleField>,
    ) : BaseTree(pos), EnumItem {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate14
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id
                1 -> FormattableTreeGroup(this.fields)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private val _fields: MutableList<TupleField> = mutableListOf()
        var fields: List<TupleField>
            get() = _fields
            set(newValue) { updateTreeConnections(_fields, newValue) }
        override fun deepCopy(): EnumItemTuple {
            return EnumItemTuple(pos, id = this.id.deepCopy(), fields = this.fields.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is EnumItemTuple && this.id == other.id && this.fields == other.fields
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + fields.hashCode()
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            updateTreeConnections(this._fields, fields)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as EnumItemTuple).id },
                { n -> (n as EnumItemTuple).fields },
            )
        }
    }

    class StructField(
        pos: Position,
        attrs: Iterable<AttrOuter> = listOf(),
        pub: VisibilityPub? = null,
        id: Id,
        type: Type?,
    ) : BaseTree(pos) {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (pub != null && type != null) {
                    sharedCodeFormattingTemplate24
                } else if (pub != null) {
                    sharedCodeFormattingTemplate2
                } else if (type != null) {
                    sharedCodeFormattingTemplate25
                } else {
                    sharedCodeFormattingTemplate3
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.attrs)
                1 -> this.pub ?: FormattableTreeGroup.empty
                2 -> this.id
                3 -> this.type ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _attrs: MutableList<AttrOuter> = mutableListOf()
        var attrs: List<AttrOuter>
            get() = _attrs
            set(newValue) { updateTreeConnections(_attrs, newValue) }
        private var _pub: VisibilityPub?
        var pub: VisibilityPub?
            get() = _pub
            set(newValue) { _pub = updateTreeConnection(_pub, newValue) }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private var _type: Type?
        var type: Type?
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        override fun deepCopy(): StructField {
            return StructField(pos, attrs = this.attrs.deepCopy(), pub = this.pub?.deepCopy(), id = this.id.deepCopy(), type = this.type?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StructField && this.attrs == other.attrs && this.pub == other.pub && this.id == other.id && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = attrs.hashCode()
            hc = 31 * hc + (pub?.hashCode() ?: 0)
            hc = 31 * hc + id.hashCode()
            hc = 31 * hc + (type?.hashCode() ?: 0)
            return hc
        }
        init {
            updateTreeConnections(this._attrs, attrs)
            this._pub = updateTreeConnection(null, pub)
            this._id = updateTreeConnection(null, id)
            this._type = updateTreeConnection(null, type)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as StructField).attrs },
                { n -> (n as StructField).pub },
                { n -> (n as StructField).id },
                { n -> (n as StructField).type },
            )
        }
    }

    class TupleField(
        pos: Position,
        attrs: Iterable<AttrOuter> = listOf(),
        pub: VisibilityPub? = null,
        type: Type?,
    ) : BaseTree(pos) {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (pub != null && type != null) {
                    sharedCodeFormattingTemplate2
                } else if (pub != null) {
                    sharedCodeFormattingTemplate6
                } else if (type != null) {
                    sharedCodeFormattingTemplate3
                } else {
                    sharedCodeFormattingTemplate26
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.attrs)
                1 -> this.pub ?: FormattableTreeGroup.empty
                2 -> this.type ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _attrs: MutableList<AttrOuter> = mutableListOf()
        var attrs: List<AttrOuter>
            get() = _attrs
            set(newValue) { updateTreeConnections(_attrs, newValue) }
        private var _pub: VisibilityPub?
        var pub: VisibilityPub?
            get() = _pub
            set(newValue) { _pub = updateTreeConnection(_pub, newValue) }
        private var _type: Type?
        var type: Type?
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        override fun deepCopy(): TupleField {
            return TupleField(pos, attrs = this.attrs.deepCopy(), pub = this.pub?.deepCopy(), type = this.type?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TupleField && this.attrs == other.attrs && this.pub == other.pub && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = attrs.hashCode()
            hc = 31 * hc + (pub?.hashCode() ?: 0)
            hc = 31 * hc + (type?.hashCode() ?: 0)
            return hc
        }
        init {
            updateTreeConnections(this._attrs, attrs)
            this._pub = updateTreeConnection(null, pub)
            this._type = updateTreeConnection(null, type)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TupleField).attrs },
                { n -> (n as TupleField).pub },
                { n -> (n as TupleField).type },
            )
        }
    }

    class IfExpr(
        pos: Position,
        test: Expr,
        consequent: Block,
        alternate: IfOrBlock?,
    ) : BaseTree(pos), ExprWithBlock, IfOrBlock {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (alternate != null) {
                    sharedCodeFormattingTemplate27
                } else {
                    sharedCodeFormattingTemplate28
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.test
                1 -> this.consequent
                2 -> this.alternate ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _test: Expr
        var test: Expr
            get() = _test
            set(newValue) { _test = updateTreeConnection(_test, newValue) }
        private var _consequent: Block
        var consequent: Block
            get() = _consequent
            set(newValue) { _consequent = updateTreeConnection(_consequent, newValue) }
        private var _alternate: IfOrBlock?
        var alternate: IfOrBlock?
            get() = _alternate
            set(newValue) { _alternate = updateTreeConnection(_alternate, newValue) }
        override fun deepCopy(): IfExpr {
            return IfExpr(pos, test = this.test.deepCopy(), consequent = this.consequent.deepCopy(), alternate = this.alternate?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is IfExpr && this.test == other.test && this.consequent == other.consequent && this.alternate == other.alternate
        }
        override fun hashCode(): Int {
            var hc = test.hashCode()
            hc = 31 * hc + consequent.hashCode()
            hc = 31 * hc + (alternate?.hashCode() ?: 0)
            return hc
        }
        init {
            this._test = updateTreeConnection(null, test)
            this._consequent = updateTreeConnection(null, consequent)
            this._alternate = updateTreeConnection(null, alternate)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as IfExpr).test },
                { n -> (n as IfExpr).consequent },
                { n -> (n as IfExpr).alternate },
            )
        }
    }

    class LabeledExpr(
        pos: Position,
        id: Id,
        expr: ExprWithBlock,
    ) : BaseTree(pos), ExprWithBlock {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate29
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id
                1 -> this.expr
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private var _expr: ExprWithBlock
        var expr: ExprWithBlock
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): LabeledExpr {
            return LabeledExpr(pos, id = this.id.deepCopy(), expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LabeledExpr && this.id == other.id && this.expr == other.expr
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + expr.hashCode()
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LabeledExpr).id },
                { n -> (n as LabeledExpr).expr },
            )
        }
    }

    class Loop(
        pos: Position,
        block: Block,
    ) : BaseTree(pos), ExprWithBlock {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate30
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.block
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _block: Block
        var block: Block
            get() = _block
            set(newValue) { _block = updateTreeConnection(_block, newValue) }
        override fun deepCopy(): Loop {
            return Loop(pos, block = this.block.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Loop && this.block == other.block
        }
        override fun hashCode(): Int {
            return block.hashCode()
        }
        init {
            this._block = updateTreeConnection(null, block)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Loop).block },
            )
        }
    }

    class Match(
        pos: Position,
        expr: Expr,
        arms: Iterable<MatchArm>,
    ) : BaseTree(pos), ExprWithBlock {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate31
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expr
                1 -> FormattableTreeGroup(this.arms)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expr: Expr
        var expr: Expr
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        private val _arms: MutableList<MatchArm> = mutableListOf()
        var arms: List<MatchArm>
            get() = _arms
            set(newValue) { updateTreeConnections(_arms, newValue) }
        override fun deepCopy(): Match {
            return Match(pos, expr = this.expr.deepCopy(), arms = this.arms.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Match && this.expr == other.expr && this.arms == other.arms
        }
        override fun hashCode(): Int {
            var hc = expr.hashCode()
            hc = 31 * hc + arms.hashCode()
            return hc
        }
        init {
            this._expr = updateTreeConnection(null, expr)
            updateTreeConnections(this._arms, arms)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Match).expr },
                { n -> (n as Match).arms },
            )
        }
    }

    class WhileLoop(
        pos: Position,
        test: Expr,
        block: Block,
    ) : BaseTree(pos), ExprWithBlock {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate32
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.test
                1 -> this.block
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _test: Expr
        var test: Expr
            get() = _test
            set(newValue) { _test = updateTreeConnection(_test, newValue) }
        private var _block: Block
        var block: Block
            get() = _block
            set(newValue) { _block = updateTreeConnection(_block, newValue) }
        override fun deepCopy(): WhileLoop {
            return WhileLoop(pos, test = this.test.deepCopy(), block = this.block.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is WhileLoop && this.test == other.test && this.block == other.block
        }
        override fun hashCode(): Int {
            var hc = test.hashCode()
            hc = 31 * hc + block.hashCode()
            return hc
        }
        init {
            this._test = updateTreeConnection(null, test)
            this._block = updateTreeConnection(null, block)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as WhileLoop).test },
                { n -> (n as WhileLoop).block },
            )
        }
    }

    class IndexExpr(
        pos: Position,
        expr: Expr,
        index: Expr,
    ) : BaseTree(pos), ExprWithoutBlock {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate33
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expr
                1 -> this.index
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expr: Expr
        var expr: Expr
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        private var _index: Expr
        var index: Expr
            get() = _index
            set(newValue) { _index = updateTreeConnection(_index, newValue) }
        override fun deepCopy(): IndexExpr {
            return IndexExpr(pos, expr = this.expr.deepCopy(), index = this.index.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is IndexExpr && this.expr == other.expr && this.index == other.index
        }
        override fun hashCode(): Int {
            var hc = expr.hashCode()
            hc = 31 * hc + index.hashCode()
            return hc
        }
        init {
            this._expr = updateTreeConnection(null, expr)
            this._index = updateTreeConnection(null, index)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as IndexExpr).expr },
                { n -> (n as IndexExpr).index },
            )
        }
    }

    sealed interface Literal : Tree, ExprWithoutBlock, Pattern {
        override fun deepCopy(): Literal
    }

    class MacroCall(
        pos: Position,
        path: Path,
        args: MacroArgs,
    ) : BaseTree(pos), ExprWithoutBlock {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate34
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.path
                1 -> this.args
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _path: Path
        var path: Path
            get() = _path
            set(newValue) { _path = updateTreeConnection(_path, newValue) }
        private var _args: MacroArgs
        var args: MacroArgs
            get() = _args
            set(newValue) { _args = updateTreeConnection(_args, newValue) }
        override fun deepCopy(): MacroCall {
            return MacroCall(pos, path = this.path.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MacroCall && this.path == other.path && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = path.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._path = updateTreeConnection(null, path)
            this._args = updateTreeConnection(null, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as MacroCall).path },
                { n -> (n as MacroCall).args },
            )
        }
    }

    class Operation(
        pos: Position,
        left: Expr?,
        operator: Operator,
        right: Expr?,
    ) : BaseTree(pos), ExprWithoutBlock {
        override val operatorDefinition
            get() = operator.operator.operatorDefinition
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (left != null && right != null) {
                    sharedCodeFormattingTemplate35
                } else if (left != null) {
                    sharedCodeFormattingTemplate34
                } else if (right != null) {
                    sharedCodeFormattingTemplate36
                } else {
                    sharedCodeFormattingTemplate37
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
            var hc = (left?.hashCode() ?: 0)
            hc = 31 * hc + operator.hashCode()
            hc = 31 * hc + (right?.hashCode() ?: 0)
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

    class ReturnExpr(
        pos: Position,
        value: Expr?,
    ) : BaseTree(pos), ExprWithoutBlock {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (value != null) {
                    sharedCodeFormattingTemplate38
                } else {
                    sharedCodeFormattingTemplate39
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.value ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _value: Expr?
        var value: Expr?
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): ReturnExpr {
            return ReturnExpr(pos, value = this.value?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ReturnExpr && this.value == other.value
        }
        override fun hashCode(): Int {
            return (value?.hashCode() ?: 0)
        }
        init {
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ReturnExpr).value },
            )
        }
    }

    class StructExpr(
        pos: Position,
        id: Id,
        members: Iterable<StructExprMember>,
    ) : BaseTree(pos), ExprWithoutBlock {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate23
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id
                1 -> FormattableTreeGroup(this.members)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private val _members: MutableList<StructExprMember> = mutableListOf()
        var members: List<StructExprMember>
            get() = _members
            set(newValue) { updateTreeConnections(_members, newValue) }
        override fun deepCopy(): StructExpr {
            return StructExpr(pos, id = this.id.deepCopy(), members = this.members.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StructExpr && this.id == other.id && this.members == other.members
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + members.hashCode()
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            updateTreeConnections(this._members, members)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as StructExpr).id },
                { n -> (n as StructExpr).members },
            )
        }
    }

    class Tuple(
        pos: Position,
        values: Iterable<Expr>,
    ) : BaseTree(pos), ExprWithoutBlock, MacroArgs {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate40
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.values)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _values: MutableList<Expr> = mutableListOf()
        var values: List<Expr>
            get() = _values
            set(newValue) { updateTreeConnections(_values, newValue) }
        override fun deepCopy(): Tuple {
            return Tuple(pos, values = this.values.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Tuple && this.values == other.values
        }
        override fun hashCode(): Int {
            return values.hashCode()
        }
        init {
            updateTreeConnections(this._values, values)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Tuple).values },
            )
        }
    }

    class ExprStatement(
        pos: Position,
        expr: Expr,
    ) : BaseTree(pos), ItemBase, Statement {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate41
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expr
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expr: Expr
        var expr: Expr
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): ExprStatement {
            return ExprStatement(pos, expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ExprStatement && this.expr == other.expr
        }
        override fun hashCode(): Int {
            return expr.hashCode()
        }
        init {
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ExprStatement).expr },
            )
        }
    }

    class Function(
        pos: Position,
        id: Id,
        generics: Iterable<GenericParam> = listOf(),
        params: Iterable<FunctionParamOption>,
        returnType: Type? = null,
        whereItems: Iterable<WhereItem> = listOf(),
        block: Block?,
    ) : BaseTree(pos), ItemBase {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (generics.isNotEmpty() && returnType != null && whereItems.isNotEmpty() && block != null) {
                    sharedCodeFormattingTemplate42
                } else if (generics.isNotEmpty() && returnType != null && whereItems.isNotEmpty()) {
                    sharedCodeFormattingTemplate43
                } else if (generics.isNotEmpty() && returnType != null && block != null) {
                    sharedCodeFormattingTemplate44
                } else if (generics.isNotEmpty() && returnType != null) {
                    sharedCodeFormattingTemplate45
                } else if (generics.isNotEmpty() && whereItems.isNotEmpty() && block != null) {
                    sharedCodeFormattingTemplate46
                } else if (generics.isNotEmpty() && whereItems.isNotEmpty()) {
                    sharedCodeFormattingTemplate47
                } else if (generics.isNotEmpty() && block != null) {
                    sharedCodeFormattingTemplate48
                } else if (generics.isNotEmpty()) {
                    sharedCodeFormattingTemplate49
                } else if (returnType != null && whereItems.isNotEmpty() && block != null) {
                    sharedCodeFormattingTemplate50
                } else if (returnType != null && whereItems.isNotEmpty()) {
                    sharedCodeFormattingTemplate51
                } else if (returnType != null && block != null) {
                    sharedCodeFormattingTemplate52
                } else if (returnType != null) {
                    sharedCodeFormattingTemplate53
                } else if (whereItems.isNotEmpty() && block != null) {
                    sharedCodeFormattingTemplate54
                } else if (whereItems.isNotEmpty()) {
                    sharedCodeFormattingTemplate55
                } else if (block != null) {
                    sharedCodeFormattingTemplate56
                } else {
                    sharedCodeFormattingTemplate57
                }
        override val formatElementCount
            get() = 6
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id
                1 -> FormattableTreeGroup(this.generics)
                2 -> FormattableTreeGroup(this.params)
                3 -> this.returnType ?: FormattableTreeGroup.empty
                4 -> FormattableTreeGroup(this.whereItems)
                5 -> this.block ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private val _generics: MutableList<GenericParam> = mutableListOf()
        var generics: List<GenericParam>
            get() = _generics
            set(newValue) { updateTreeConnections(_generics, newValue) }
        private val _params: MutableList<FunctionParamOption> = mutableListOf()
        var params: List<FunctionParamOption>
            get() = _params
            set(newValue) { updateTreeConnections(_params, newValue) }
        private var _returnType: Type?
        var returnType: Type?
            get() = _returnType
            set(newValue) { _returnType = updateTreeConnection(_returnType, newValue) }
        private val _whereItems: MutableList<WhereItem> = mutableListOf()
        var whereItems: List<WhereItem>
            get() = _whereItems
            set(newValue) { updateTreeConnections(_whereItems, newValue) }
        private var _block: Block?
        var block: Block?
            get() = _block
            set(newValue) { _block = updateTreeConnection(_block, newValue) }
        override fun deepCopy(): Function {
            return Function(pos, id = this.id.deepCopy(), generics = this.generics.deepCopy(), params = this.params.deepCopy(), returnType = this.returnType?.deepCopy(), whereItems = this.whereItems.deepCopy(), block = this.block?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Function && this.id == other.id && this.generics == other.generics && this.params == other.params && this.returnType == other.returnType && this.whereItems == other.whereItems && this.block == other.block
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + generics.hashCode()
            hc = 31 * hc + params.hashCode()
            hc = 31 * hc + (returnType?.hashCode() ?: 0)
            hc = 31 * hc + whereItems.hashCode()
            hc = 31 * hc + (block?.hashCode() ?: 0)
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            updateTreeConnections(this._generics, generics)
            updateTreeConnections(this._params, params)
            this._returnType = updateTreeConnection(null, returnType)
            updateTreeConnections(this._whereItems, whereItems)
            this._block = updateTreeConnection(null, block)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Function).id },
                { n -> (n as Function).generics },
                { n -> (n as Function).params },
                { n -> (n as Function).returnType },
                { n -> (n as Function).whereItems },
                { n -> (n as Function).block },
            )
        }
    }

    sealed interface WhereItem : Tree {
        override fun deepCopy(): WhereItem
    }

    class FunctionParam(
        pos: Position,
        pattern: Pattern,
        type: Type?,
    ) : BaseTree(pos), FunctionParamOption {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (type != null) {
                    sharedCodeFormattingTemplate58
                } else {
                    sharedCodeFormattingTemplate59
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.pattern
                1 -> this.type ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _pattern: Pattern
        var pattern: Pattern
            get() = _pattern
            set(newValue) { _pattern = updateTreeConnection(_pattern, newValue) }
        private var _type: Type?
        var type: Type?
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        override fun deepCopy(): FunctionParam {
            return FunctionParam(pos, pattern = this.pattern.deepCopy(), type = this.type?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FunctionParam && this.pattern == other.pattern && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = pattern.hashCode()
            hc = 31 * hc + (type?.hashCode() ?: 0)
            return hc
        }
        init {
            this._pattern = updateTreeConnection(null, pattern)
            this._type = updateTreeConnection(null, type)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FunctionParam).pattern },
                { n -> (n as FunctionParam).type },
            )
        }
    }

    class RefType(
        pos: Position,
        type: Type,
    ) : BaseTree(pos), FunctionParamOption, Type {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate60
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: Type
        var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        override fun deepCopy(): RefType {
            return RefType(pos, type = this.type.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is RefType && this.type == other.type
        }
        override fun hashCode(): Int {
            return type.hashCode()
        }
        init {
            this._type = updateTreeConnection(null, type)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as RefType).type },
            )
        }
    }

    class FunctionType(
        pos: Position,
        params: Iterable<Type>,
        returnType: Type,
    ) : BaseTree(pos), Type {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (returnsUnit) {
                    sharedCodeFormattingTemplate61
                } else {
                    sharedCodeFormattingTemplate62
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.params)
                1 -> this.returnType
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _params: MutableList<Type> = mutableListOf()
        var params: List<Type>
            get() = _params
            set(newValue) { updateTreeConnections(_params, newValue) }
        private var _returnType: Type
        var returnType: Type
            get() = _returnType
            set(newValue) { _returnType = updateTreeConnection(_returnType, newValue) }
        val returnsUnit: Boolean
            get() = returnType.isUnit()
        override fun deepCopy(): FunctionType {
            return FunctionType(pos, params = this.params.deepCopy(), returnType = this.returnType.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FunctionType && this.params == other.params && this.returnType == other.returnType
        }
        override fun hashCode(): Int {
            var hc = params.hashCode()
            hc = 31 * hc + returnType.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._params, params)
            this._returnType = updateTreeConnection(null, returnType)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FunctionType).params },
                { n -> (n as FunctionType).returnType },
            )
        }
    }

    class GenericArgs(
        pos: Position,
        args: Iterable<GenericArg>,
    ) : BaseTree(pos), PathSegment {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate63
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.args)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _args: MutableList<GenericArg> = mutableListOf()
        var args: List<GenericArg>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): GenericArgs {
            return GenericArgs(pos, args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is GenericArgs && this.args == other.args
        }
        override fun hashCode(): Int {
            return args.hashCode()
        }
        init {
            updateTreeConnections(this._args, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as GenericArgs).args },
            )
        }
    }

    class TypeParam(
        pos: Position,
        id: Id,
        bounds: Iterable<TypeParamBound> = listOf(),
        default: Type? = null,
    ) : BaseTree(pos), GenericParam, WhereItem {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (bounds.isNotEmpty() && default != null) {
                    sharedCodeFormattingTemplate64
                } else if (bounds.isNotEmpty()) {
                    sharedCodeFormattingTemplate65
                } else if (default != null) {
                    sharedCodeFormattingTemplate66
                } else {
                    sharedCodeFormattingTemplate59
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id
                1 -> FormattableTreeGroup(this.bounds)
                2 -> this.default ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private val _bounds: MutableList<TypeParamBound> = mutableListOf()
        var bounds: List<TypeParamBound>
            get() = _bounds
            set(newValue) { updateTreeConnections(_bounds, newValue) }
        private var _default: Type?
        var default: Type?
            get() = _default
            set(newValue) { _default = updateTreeConnection(_default, newValue) }
        override fun deepCopy(): TypeParam {
            return TypeParam(pos, id = this.id.deepCopy(), bounds = this.bounds.deepCopy(), default = this.default?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeParam && this.id == other.id && this.bounds == other.bounds && this.default == other.default
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + bounds.hashCode()
            hc = 31 * hc + (default?.hashCode() ?: 0)
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            updateTreeConnections(this._bounds, bounds)
            this._default = updateTreeConnection(null, default)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeParam).id },
                { n -> (n as TypeParam).bounds },
                { n -> (n as TypeParam).default },
            )
        }
    }

    class GenericType(
        pos: Position,
        path: Path,
        args: Iterable<GenericArg>,
    ) : BaseTree(pos), Type {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate67
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.path
                1 -> FormattableTreeGroup(this.args)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _path: Path
        var path: Path
            get() = _path
            set(newValue) { _path = updateTreeConnection(_path, newValue) }
        private val _args: MutableList<GenericArg> = mutableListOf()
        var args: List<GenericArg>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): GenericType {
            return GenericType(pos, path = this.path.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is GenericType && this.path == other.path && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = path.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._path = updateTreeConnection(null, path)
            updateTreeConnections(this._args, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as GenericType).path },
                { n -> (n as GenericType).args },
            )
        }
    }

    class IdPattern(
        pos: Position,
        mut: IdPatternMut? = null,
        id: Id,
    ) : BaseTree(pos), Pattern {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (mut != null) {
                    sharedCodeFormattingTemplate34
                } else {
                    sharedCodeFormattingTemplate37
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.mut ?: FormattableTreeGroup.empty
                1 -> this.id
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _mut: IdPatternMut?
        var mut: IdPatternMut?
            get() = _mut
            set(newValue) { _mut = updateTreeConnection(_mut, newValue) }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        override fun deepCopy(): IdPattern {
            return IdPattern(pos, mut = this.mut?.deepCopy(), id = this.id.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is IdPattern && this.mut == other.mut && this.id == other.id
        }
        override fun hashCode(): Int {
            var hc = (mut?.hashCode() ?: 0)
            hc = 31 * hc + id.hashCode()
            return hc
        }
        init {
            this._mut = updateTreeConnection(null, mut)
            this._id = updateTreeConnection(null, id)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as IdPattern).mut },
                { n -> (n as IdPattern).id },
            )
        }
    }

    class IdPatternMut(
        pos: Position,
    ) : BaseTree(pos) {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate68
        override val formatElementCount
            get() = 0
        override fun deepCopy(): IdPatternMut {
            return IdPatternMut(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is IdPatternMut
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class Impl(
        pos: Position,
        generics: Iterable<GenericParam> = listOf(),
        trait: Type?,
        type: Type,
        whereItems: Iterable<WhereItem> = listOf(),
        attrs: Iterable<AttrInner> = listOf(),
        items: Iterable<Item>,
    ) : BaseTree(pos), ItemBase {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (generics.isNotEmpty() && trait != null && whereItems.isNotEmpty()) {
                    sharedCodeFormattingTemplate69
                } else if (generics.isNotEmpty() && trait != null) {
                    sharedCodeFormattingTemplate70
                } else if (generics.isNotEmpty() && whereItems.isNotEmpty()) {
                    sharedCodeFormattingTemplate71
                } else if (generics.isNotEmpty()) {
                    sharedCodeFormattingTemplate72
                } else if (trait != null && whereItems.isNotEmpty()) {
                    sharedCodeFormattingTemplate73
                } else if (trait != null) {
                    sharedCodeFormattingTemplate74
                } else if (whereItems.isNotEmpty()) {
                    sharedCodeFormattingTemplate75
                } else {
                    sharedCodeFormattingTemplate76
                }
        override val formatElementCount
            get() = 6
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.generics)
                1 -> this.trait ?: FormattableTreeGroup.empty
                2 -> this.type
                3 -> FormattableTreeGroup(this.whereItems)
                4 -> FormattableTreeGroup(this.attrs)
                5 -> FormattableTreeGroup(this.items)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _generics: MutableList<GenericParam> = mutableListOf()
        var generics: List<GenericParam>
            get() = _generics
            set(newValue) { updateTreeConnections(_generics, newValue) }
        private var _trait: Type?
        var trait: Type?
            get() = _trait
            set(newValue) { _trait = updateTreeConnection(_trait, newValue) }
        private var _type: Type
        var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private val _whereItems: MutableList<WhereItem> = mutableListOf()
        var whereItems: List<WhereItem>
            get() = _whereItems
            set(newValue) { updateTreeConnections(_whereItems, newValue) }
        private val _attrs: MutableList<AttrInner> = mutableListOf()
        var attrs: List<AttrInner>
            get() = _attrs
            set(newValue) { updateTreeConnections(_attrs, newValue) }
        private val _items: MutableList<Item> = mutableListOf()
        var items: List<Item>
            get() = _items
            set(newValue) { updateTreeConnections(_items, newValue) }
        override fun deepCopy(): Impl {
            return Impl(pos, generics = this.generics.deepCopy(), trait = this.trait?.deepCopy(), type = this.type.deepCopy(), whereItems = this.whereItems.deepCopy(), attrs = this.attrs.deepCopy(), items = this.items.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Impl && this.generics == other.generics && this.trait == other.trait && this.type == other.type && this.whereItems == other.whereItems && this.attrs == other.attrs && this.items == other.items
        }
        override fun hashCode(): Int {
            var hc = generics.hashCode()
            hc = 31 * hc + (trait?.hashCode() ?: 0)
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + whereItems.hashCode()
            hc = 31 * hc + attrs.hashCode()
            hc = 31 * hc + items.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._generics, generics)
            this._trait = updateTreeConnection(null, trait)
            this._type = updateTreeConnection(null, type)
            updateTreeConnections(this._whereItems, whereItems)
            updateTreeConnections(this._attrs, attrs)
            updateTreeConnections(this._items, items)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Impl).generics },
                { n -> (n as Impl).trait },
                { n -> (n as Impl).type },
                { n -> (n as Impl).whereItems },
                { n -> (n as Impl).attrs },
                { n -> (n as Impl).items },
            )
        }
    }

    class ImplTraitType(
        pos: Position,
        bounds: Iterable<TypeParamBound>,
    ) : BaseTree(pos), Type {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (bounds.isNotEmpty()) {
                    sharedCodeFormattingTemplate77
                } else {
                    sharedCodeFormattingTemplate78
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.bounds)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _bounds: MutableList<TypeParamBound> = mutableListOf()
        var bounds: List<TypeParamBound>
            get() = _bounds
            set(newValue) { updateTreeConnections(_bounds, newValue) }
        override fun deepCopy(): ImplTraitType {
            return ImplTraitType(pos, bounds = this.bounds.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImplTraitType && this.bounds == other.bounds
        }
        override fun hashCode(): Int {
            return bounds.hashCode()
        }
        init {
            updateTreeConnections(this._bounds, bounds)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ImplTraitType).bounds },
            )
        }
    }

    class VisibilityPub(
        pos: Position,
        scope: VisibilityScope? = null,
    ) : BaseTree(pos) {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (scope != null) {
                    sharedCodeFormattingTemplate79
                } else {
                    sharedCodeFormattingTemplate80
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.scope ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _scope: VisibilityScope?
        var scope: VisibilityScope?
            get() = _scope
            set(newValue) { _scope = updateTreeConnection(_scope, newValue) }
        override fun deepCopy(): VisibilityPub {
            return VisibilityPub(pos, scope = this.scope?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is VisibilityPub && this.scope == other.scope
        }
        override fun hashCode(): Int {
            return (scope?.hashCode() ?: 0)
        }
        init {
            this._scope = updateTreeConnection(null, scope)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as VisibilityPub).scope },
            )
        }
    }

    class Module(
        pos: Position,
        pub: VisibilityPub? = null,
        id: Id,
        block: Block? = null,
    ) : BaseTree(pos), ItemBase {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (pub != null && block != null) {
                    sharedCodeFormattingTemplate81
                } else if (pub != null) {
                    sharedCodeFormattingTemplate82
                } else if (block != null) {
                    sharedCodeFormattingTemplate83
                } else {
                    sharedCodeFormattingTemplate84
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.pub ?: FormattableTreeGroup.empty
                1 -> this.id
                2 -> this.block ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _pub: VisibilityPub?
        var pub: VisibilityPub?
            get() = _pub
            set(newValue) { _pub = updateTreeConnection(_pub, newValue) }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private var _block: Block?
        var block: Block?
            get() = _block
            set(newValue) { _block = updateTreeConnection(_block, newValue) }
        override fun deepCopy(): Module {
            return Module(pos, pub = this.pub?.deepCopy(), id = this.id.deepCopy(), block = this.block?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Module && this.pub == other.pub && this.id == other.id && this.block == other.block
        }
        override fun hashCode(): Int {
            var hc = (pub?.hashCode() ?: 0)
            hc = 31 * hc + id.hashCode()
            hc = 31 * hc + (block?.hashCode() ?: 0)
            return hc
        }
        init {
            this._pub = updateTreeConnection(null, pub)
            this._id = updateTreeConnection(null, id)
            this._block = updateTreeConnection(null, block)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Module).pub },
                { n -> (n as Module).id },
                { n -> (n as Module).block },
            )
        }
    }

    class Static(
        pos: Position,
        id: Id,
        type: Type,
        value: Expr?,
    ) : BaseTree(pos), ItemBase {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (value != null) {
                    sharedCodeFormattingTemplate85
                } else {
                    sharedCodeFormattingTemplate86
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id
                1 -> this.type
                2 -> this.value ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private var _type: Type
        var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _value: Expr?
        var value: Expr?
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): Static {
            return Static(pos, id = this.id.deepCopy(), type = this.type.deepCopy(), value = this.value?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Static && this.id == other.id && this.type == other.type && this.value == other.value
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + (value?.hashCode() ?: 0)
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            this._type = updateTreeConnection(null, type)
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Static).id },
                { n -> (n as Static).type },
                { n -> (n as Static).value },
            )
        }
    }

    class Struct(
        pos: Position,
        id: Id,
        generics: Iterable<GenericParam> = listOf(),
        whereItems: Iterable<WhereItem> = listOf(),
        fields: Iterable<StructField>,
    ) : BaseTree(pos), ItemBase {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (generics.isNotEmpty() && whereItems.isNotEmpty()) {
                    sharedCodeFormattingTemplate87
                } else if (generics.isNotEmpty()) {
                    sharedCodeFormattingTemplate88
                } else if (whereItems.isNotEmpty()) {
                    sharedCodeFormattingTemplate89
                } else {
                    sharedCodeFormattingTemplate90
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id
                1 -> FormattableTreeGroup(this.generics)
                2 -> FormattableTreeGroup(this.whereItems)
                3 -> FormattableTreeGroup(this.fields)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private val _generics: MutableList<GenericParam> = mutableListOf()
        var generics: List<GenericParam>
            get() = _generics
            set(newValue) { updateTreeConnections(_generics, newValue) }
        private val _whereItems: MutableList<WhereItem> = mutableListOf()
        var whereItems: List<WhereItem>
            get() = _whereItems
            set(newValue) { updateTreeConnections(_whereItems, newValue) }
        private val _fields: MutableList<StructField> = mutableListOf()
        var fields: List<StructField>
            get() = _fields
            set(newValue) { updateTreeConnections(_fields, newValue) }
        override fun deepCopy(): Struct {
            return Struct(pos, id = this.id.deepCopy(), generics = this.generics.deepCopy(), whereItems = this.whereItems.deepCopy(), fields = this.fields.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Struct && this.id == other.id && this.generics == other.generics && this.whereItems == other.whereItems && this.fields == other.fields
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + generics.hashCode()
            hc = 31 * hc + whereItems.hashCode()
            hc = 31 * hc + fields.hashCode()
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            updateTreeConnections(this._generics, generics)
            updateTreeConnections(this._whereItems, whereItems)
            updateTreeConnections(this._fields, fields)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Struct).id },
                { n -> (n as Struct).generics },
                { n -> (n as Struct).whereItems },
                { n -> (n as Struct).fields },
            )
        }
    }

    class Trait(
        pos: Position,
        id: Id,
        generics: Iterable<GenericParam> = listOf(),
        bounds: Iterable<TypeParamBound> = listOf(),
        whereItems: Iterable<WhereItem> = listOf(),
        attrs: Iterable<AttrInner> = listOf(),
        items: Iterable<Item>,
    ) : BaseTree(pos), ItemBase {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (generics.isNotEmpty() && bounds.isNotEmpty() && whereItems.isNotEmpty()) {
                    sharedCodeFormattingTemplate91
                } else if (generics.isNotEmpty() && bounds.isNotEmpty()) {
                    sharedCodeFormattingTemplate92
                } else if (generics.isNotEmpty() && whereItems.isNotEmpty()) {
                    sharedCodeFormattingTemplate93
                } else if (generics.isNotEmpty()) {
                    sharedCodeFormattingTemplate94
                } else if (bounds.isNotEmpty() && whereItems.isNotEmpty()) {
                    sharedCodeFormattingTemplate95
                } else if (bounds.isNotEmpty()) {
                    sharedCodeFormattingTemplate96
                } else if (whereItems.isNotEmpty()) {
                    sharedCodeFormattingTemplate97
                } else {
                    sharedCodeFormattingTemplate98
                }
        override val formatElementCount
            get() = 6
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id
                1 -> FormattableTreeGroup(this.generics)
                2 -> FormattableTreeGroup(this.bounds)
                3 -> FormattableTreeGroup(this.whereItems)
                4 -> FormattableTreeGroup(this.attrs)
                5 -> FormattableTreeGroup(this.items)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private val _generics: MutableList<GenericParam> = mutableListOf()
        var generics: List<GenericParam>
            get() = _generics
            set(newValue) { updateTreeConnections(_generics, newValue) }
        private val _bounds: MutableList<TypeParamBound> = mutableListOf()
        var bounds: List<TypeParamBound>
            get() = _bounds
            set(newValue) { updateTreeConnections(_bounds, newValue) }
        private val _whereItems: MutableList<WhereItem> = mutableListOf()
        var whereItems: List<WhereItem>
            get() = _whereItems
            set(newValue) { updateTreeConnections(_whereItems, newValue) }
        private val _attrs: MutableList<AttrInner> = mutableListOf()
        var attrs: List<AttrInner>
            get() = _attrs
            set(newValue) { updateTreeConnections(_attrs, newValue) }
        private val _items: MutableList<Item> = mutableListOf()
        var items: List<Item>
            get() = _items
            set(newValue) { updateTreeConnections(_items, newValue) }
        override fun deepCopy(): Trait {
            return Trait(pos, id = this.id.deepCopy(), generics = this.generics.deepCopy(), bounds = this.bounds.deepCopy(), whereItems = this.whereItems.deepCopy(), attrs = this.attrs.deepCopy(), items = this.items.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Trait && this.id == other.id && this.generics == other.generics && this.bounds == other.bounds && this.whereItems == other.whereItems && this.attrs == other.attrs && this.items == other.items
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + generics.hashCode()
            hc = 31 * hc + bounds.hashCode()
            hc = 31 * hc + whereItems.hashCode()
            hc = 31 * hc + attrs.hashCode()
            hc = 31 * hc + items.hashCode()
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            updateTreeConnections(this._generics, generics)
            updateTreeConnections(this._bounds, bounds)
            updateTreeConnections(this._whereItems, whereItems)
            updateTreeConnections(this._attrs, attrs)
            updateTreeConnections(this._items, items)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Trait).id },
                { n -> (n as Trait).generics },
                { n -> (n as Trait).bounds },
                { n -> (n as Trait).whereItems },
                { n -> (n as Trait).attrs },
                { n -> (n as Trait).items },
            )
        }
    }

    class TupleStruct(
        pos: Position,
        id: Id,
        generics: Iterable<GenericParam> = listOf(),
        fields: Iterable<TupleField>,
        whereItems: Iterable<WhereItem> = listOf(),
    ) : BaseTree(pos), ItemBase {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (generics.isNotEmpty() && whereItems.isNotEmpty()) {
                    sharedCodeFormattingTemplate99
                } else if (generics.isNotEmpty()) {
                    sharedCodeFormattingTemplate100
                } else if (whereItems.isNotEmpty()) {
                    sharedCodeFormattingTemplate101
                } else {
                    sharedCodeFormattingTemplate102
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id
                1 -> FormattableTreeGroup(this.generics)
                2 -> FormattableTreeGroup(this.fields)
                3 -> FormattableTreeGroup(this.whereItems)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private val _generics: MutableList<GenericParam> = mutableListOf()
        var generics: List<GenericParam>
            get() = _generics
            set(newValue) { updateTreeConnections(_generics, newValue) }
        private val _fields: MutableList<TupleField> = mutableListOf()
        var fields: List<TupleField>
            get() = _fields
            set(newValue) { updateTreeConnections(_fields, newValue) }
        private val _whereItems: MutableList<WhereItem> = mutableListOf()
        var whereItems: List<WhereItem>
            get() = _whereItems
            set(newValue) { updateTreeConnections(_whereItems, newValue) }
        override fun deepCopy(): TupleStruct {
            return TupleStruct(pos, id = this.id.deepCopy(), generics = this.generics.deepCopy(), fields = this.fields.deepCopy(), whereItems = this.whereItems.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TupleStruct && this.id == other.id && this.generics == other.generics && this.fields == other.fields && this.whereItems == other.whereItems
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + generics.hashCode()
            hc = 31 * hc + fields.hashCode()
            hc = 31 * hc + whereItems.hashCode()
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            updateTreeConnections(this._generics, generics)
            updateTreeConnections(this._fields, fields)
            updateTreeConnections(this._whereItems, whereItems)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TupleStruct).id },
                { n -> (n as TupleStruct).generics },
                { n -> (n as TupleStruct).fields },
                { n -> (n as TupleStruct).whereItems },
            )
        }
    }

    class TypeAlias(
        pos: Position,
        id: Id,
        generics: Iterable<GenericParam> = listOf(),
        type: Type,
    ) : BaseTree(pos), ItemBase {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (generics.isNotEmpty()) {
                    sharedCodeFormattingTemplate103
                } else {
                    sharedCodeFormattingTemplate104
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id
                1 -> FormattableTreeGroup(this.generics)
                2 -> this.type
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private val _generics: MutableList<GenericParam> = mutableListOf()
        var generics: List<GenericParam>
            get() = _generics
            set(newValue) { updateTreeConnections(_generics, newValue) }
        private var _type: Type
        var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        override fun deepCopy(): TypeAlias {
            return TypeAlias(pos, id = this.id.deepCopy(), generics = this.generics.deepCopy(), type = this.type.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeAlias && this.id == other.id && this.generics == other.generics && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + generics.hashCode()
            hc = 31 * hc + type.hashCode()
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            updateTreeConnections(this._generics, generics)
            this._type = updateTreeConnection(null, type)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeAlias).id },
                { n -> (n as TypeAlias).generics },
                { n -> (n as TypeAlias).type },
            )
        }
    }

    class Use(
        pos: Position,
        what: UseWhat,
    ) : BaseTree(pos), ItemBase {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate105
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.what
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _what: UseWhat
        var what: UseWhat
            get() = _what
            set(newValue) { _what = updateTreeConnection(_what, newValue) }
        override fun deepCopy(): Use {
            return Use(pos, what = this.what.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Use && this.what == other.what
        }
        override fun hashCode(): Int {
            return what.hashCode()
        }
        init {
            this._what = updateTreeConnection(null, what)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Use).what },
            )
        }
    }

    class LetStatement(
        pos: Position,
        attrs: Iterable<AttrOuter> = listOf(),
        pattern: Pattern,
        type: Type?,
        value: Expr?,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (type != null && value != null) {
                    sharedCodeFormattingTemplate106
                } else if (type != null) {
                    sharedCodeFormattingTemplate107
                } else if (value != null) {
                    sharedCodeFormattingTemplate108
                } else {
                    sharedCodeFormattingTemplate109
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.attrs)
                1 -> this.pattern
                2 -> this.type ?: FormattableTreeGroup.empty
                3 -> this.value ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _attrs: MutableList<AttrOuter> = mutableListOf()
        var attrs: List<AttrOuter>
            get() = _attrs
            set(newValue) { updateTreeConnections(_attrs, newValue) }
        private var _pattern: Pattern
        var pattern: Pattern
            get() = _pattern
            set(newValue) { _pattern = updateTreeConnection(_pattern, newValue) }
        private var _type: Type?
        var type: Type?
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _value: Expr?
        var value: Expr?
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): LetStatement {
            return LetStatement(pos, attrs = this.attrs.deepCopy(), pattern = this.pattern.deepCopy(), type = this.type?.deepCopy(), value = this.value?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LetStatement && this.attrs == other.attrs && this.pattern == other.pattern && this.type == other.type && this.value == other.value
        }
        override fun hashCode(): Int {
            var hc = attrs.hashCode()
            hc = 31 * hc + pattern.hashCode()
            hc = 31 * hc + (type?.hashCode() ?: 0)
            hc = 31 * hc + (value?.hashCode() ?: 0)
            return hc
        }
        init {
            updateTreeConnections(this._attrs, attrs)
            this._pattern = updateTreeConnection(null, pattern)
            this._type = updateTreeConnection(null, type)
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LetStatement).attrs },
                { n -> (n as LetStatement).pattern },
                { n -> (n as LetStatement).type },
                { n -> (n as LetStatement).value },
            )
        }
    }

    class NumberLiteral(
        pos: Position,
        var value: Number,
    ) : BaseTree(pos), Literal {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.number("$value$suffix")
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        val suffix: String
            get() =
                when (value) {
                    is Double -> "f64"
                    // is Long -> "i32" // TODO Ever needed?
                    else -> ""
                }
        override fun deepCopy(): NumberLiteral {
            return NumberLiteral(pos, value = this.value)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is NumberLiteral && this.value == other.value
        }
        override fun hashCode(): Int {
            return value.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class StringLiteral(
        pos: Position,
        var value: String,
    ) : BaseTree(pos), Literal {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.quoted(stringTokenText(value))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): StringLiteral {
            return StringLiteral(pos, value = this.value)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StringLiteral && this.value == other.value
        }
        override fun hashCode(): Int {
            return value.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class MatchArm(
        pos: Position,
        pattern: PatternAny,
        expr: Expr,
    ) : BaseTree(pos) {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate110
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.pattern
                1 -> this.expr
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _pattern: PatternAny
        var pattern: PatternAny
            get() = _pattern
            set(newValue) { _pattern = updateTreeConnection(_pattern, newValue) }
        private var _expr: Expr
        var expr: Expr
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): MatchArm {
            return MatchArm(pos, pattern = this.pattern.deepCopy(), expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MatchArm && this.pattern == other.pattern && this.expr == other.expr
        }
        override fun hashCode(): Int {
            var hc = pattern.hashCode()
            hc = 31 * hc + expr.hashCode()
            return hc
        }
        init {
            this._pattern = updateTreeConnection(null, pattern)
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as MatchArm).pattern },
                { n -> (n as MatchArm).expr },
            )
        }
    }

    class Operator(
        pos: Position,
        var operator: RustOperator,
    ) : BaseTree(pos) {
        override val operatorDefinition: RustOperatorDefinition?
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

    class PathSegments(
        pos: Position,
        segments: Iterable<PathSegment>,
    ) : BaseTree(pos), Path {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate111
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
        private val _segments: MutableList<PathSegment> = mutableListOf()
        var segments: List<PathSegment>
            get() = _segments
            set(newValue) { updateTreeConnections(_segments, newValue) }
        override fun deepCopy(): PathSegments {
            return PathSegments(pos, segments = this.segments.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PathSegments && this.segments == other.segments
        }
        override fun hashCode(): Int {
            return segments.hashCode()
        }
        init {
            updateTreeConnections(this._segments, segments)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as PathSegments).segments },
            )
        }
    }

    class Rest(
        pos: Position,
    ) : BaseTree(pos), Pattern {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate112
        override val formatElementCount
            get() = 0
        override fun deepCopy(): Rest {
            return Rest(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Rest
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class TupleStructPattern(
        pos: Position,
        path: Path,
        patterns: Iterable<Pattern>,
    ) : BaseTree(pos), Pattern {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate14
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.path
                1 -> FormattableTreeGroup(this.patterns)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _path: Path
        var path: Path
            get() = _path
            set(newValue) { _path = updateTreeConnection(_path, newValue) }
        private val _patterns: MutableList<Pattern> = mutableListOf()
        var patterns: List<Pattern>
            get() = _patterns
            set(newValue) { updateTreeConnections(_patterns, newValue) }
        override fun deepCopy(): TupleStructPattern {
            return TupleStructPattern(pos, path = this.path.deepCopy(), patterns = this.patterns.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TupleStructPattern && this.path == other.path && this.patterns == other.patterns
        }
        override fun hashCode(): Int {
            var hc = path.hashCode()
            hc = 31 * hc + patterns.hashCode()
            return hc
        }
        init {
            this._path = updateTreeConnection(null, path)
            updateTreeConnections(this._patterns, patterns)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TupleStructPattern).path },
                { n -> (n as TupleStructPattern).patterns },
            )
        }
    }

    class PatternAlt(
        pos: Position,
        patterns: Iterable<Pattern>,
    ) : BaseTree(pos), PatternAny {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate113
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.patterns)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _patterns: MutableList<Pattern> = mutableListOf()
        var patterns: List<Pattern>
            get() = _patterns
            set(newValue) { updateTreeConnections(_patterns, newValue) }
        override fun deepCopy(): PatternAlt {
            return PatternAlt(pos, patterns = this.patterns.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PatternAlt && this.patterns == other.patterns
        }
        override fun hashCode(): Int {
            return patterns.hashCode()
        }
        init {
            updateTreeConnections(this._patterns, patterns)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as PatternAlt).patterns },
            )
        }
    }

    class Semi(
        pos: Position,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate114
        override val formatElementCount
            get() = 0
        override fun deepCopy(): Semi {
            return Semi(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Semi
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    sealed interface StructExprMember : Tree {
        override fun deepCopy(): StructExprMember
    }

    class StructBase(
        pos: Position,
        expr: Expr,
    ) : BaseTree(pos), StructExprMember {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate115
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expr
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expr: Expr
        var expr: Expr
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): StructBase {
            return StructBase(pos, expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StructBase && this.expr == other.expr
        }
        override fun hashCode(): Int {
            return expr.hashCode()
        }
        init {
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as StructBase).expr },
            )
        }
    }

    class StructExprField(
        pos: Position,
        id: Id,
        expr: Expr?,
    ) : BaseTree(pos), StructExprMember {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (expr != null) {
                    sharedCodeFormattingTemplate58
                } else {
                    sharedCodeFormattingTemplate59
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id
                1 -> this.expr ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Id
        var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private var _expr: Expr?
        var expr: Expr?
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): StructExprField {
            return StructExprField(pos, id = this.id.deepCopy(), expr = this.expr?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StructExprField && this.id == other.id && this.expr == other.expr
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + (expr?.hashCode() ?: 0)
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as StructExprField).id },
                { n -> (n as StructExprField).expr },
            )
        }
    }

    class TraitObjectType(
        pos: Position,
        bounds: Iterable<TypeParamBound>,
    ) : BaseTree(pos), Type {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (bounds.isNotEmpty()) {
                    sharedCodeFormattingTemplate116
                } else {
                    sharedCodeFormattingTemplate117
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.bounds)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _bounds: MutableList<TypeParamBound> = mutableListOf()
        var bounds: List<TypeParamBound>
            get() = _bounds
            set(newValue) { updateTreeConnections(_bounds, newValue) }
        override fun deepCopy(): TraitObjectType {
            return TraitObjectType(pos, bounds = this.bounds.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TraitObjectType && this.bounds == other.bounds
        }
        override fun hashCode(): Int {
            return bounds.hashCode()
        }
        init {
            updateTreeConnections(this._bounds, bounds)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TraitObjectType).bounds },
            )
        }
    }

    class TupleType(
        pos: Position,
        types: Iterable<Type>,
    ) : BaseTree(pos), Type {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate40
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.types)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _types: MutableList<Type> = mutableListOf()
        var types: List<Type>
            get() = _types
            set(newValue) { updateTreeConnections(_types, newValue) }
        override fun deepCopy(): TupleType {
            return TupleType(pos, types = this.types.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TupleType && this.types == other.types
        }
        override fun hashCode(): Int {
            return types.hashCode()
        }
        init {
            updateTreeConnections(this._types, types)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TupleType).types },
            )
        }
    }

    class VisibilityScope(
        pos: Position,
        var scope: VisibilityScopeOption,
    ) : BaseTree(pos) {
        override val operatorDefinition: RustOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (scope == VisibilityScopeOption.Crate) {
                    sharedCodeFormattingTemplate118
                } else if (scope == VisibilityScopeOption.Self) {
                    sharedCodeFormattingTemplate119
                } else if (scope == VisibilityScopeOption.Super) {
                    sharedCodeFormattingTemplate120
                } else {
                    sharedCodeFormattingTemplate121
                }
        override val formatElementCount
            get() = 0
        override fun deepCopy(): VisibilityScope {
            return VisibilityScope(pos, scope = this.scope)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is VisibilityScope && this.scope == other.scope
        }
        override fun hashCode(): Int {
            return scope.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /** `{{0*\n}} {{1*\n}}` */
    private val sharedCodeFormattingTemplate0 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.NewLine,
                ),
            ),
        )

    /** `#![ {{0}} ]\n` */
    private val sharedCodeFormattingTemplate1 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("#![", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("]\n", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0*\n}} {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate2 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0*\n}} {{2}}` */
    private val sharedCodeFormattingTemplate3 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `[ {{0*,}} ]` */
    private val sharedCodeFormattingTemplate4 =
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

    /** `#[ {{0}} ]` */
    private val sharedCodeFormattingTemplate5 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("#[", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0*\n}} {{1}}` */
    private val sharedCodeFormattingTemplate6 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `\{ {{0*\n}} {{1*\n}} {{2}} \}` */
    private val sharedCodeFormattingTemplate7 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `\{ {{0*\n}} {{1*\n}} \}` */
    private val sharedCodeFormattingTemplate8 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `break ' {{0}} {{1}}` */
    private val sharedCodeFormattingTemplate9 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("break", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("'", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `break ' {{0}}` */
    private val sharedCodeFormattingTemplate10 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("break", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("'", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `break {{1}}` */
    private val sharedCodeFormattingTemplate11 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("break", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `break` */
    private val sharedCodeFormattingTemplate12 =
        CodeFormattingTemplate.LiteralToken("break", OutputTokenType.Word)

    /** `( {{0}} ) ( {{1*,}} )` */
    private val sharedCodeFormattingTemplate13 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}} ( {{1*,}} )` */
    private val sharedCodeFormattingTemplate14 =
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

    /** `{{0}} | {{1*,}} | -> {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate15 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("|", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("|", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} | {{1*,}} | {{3}}` */
    private val sharedCodeFormattingTemplate16 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("|", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("|", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `| {{1*,}} | -> {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate17 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("|", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("|", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `| {{1*,}} | {{3}}` */
    private val sharedCodeFormattingTemplate18 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("|", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("|", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `move` */
    private val sharedCodeFormattingTemplate19 =
        CodeFormattingTemplate.LiteralToken("move", OutputTokenType.Word)

    /** `continue ' {{0}}` */
    private val sharedCodeFormattingTemplate20 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("continue", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("'", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `continue` */
    private val sharedCodeFormattingTemplate21 =
        CodeFormattingTemplate.LiteralToken("continue", OutputTokenType.Word)

    /** `enum {{0}} \{ {{1*,}} \}` */
    private val sharedCodeFormattingTemplate22 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("enum", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} \{ {{1*,}} \}` */
    private val sharedCodeFormattingTemplate23 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} {{1}} {{2}} : {{3}}` */
    private val sharedCodeFormattingTemplate24 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0*\n}} {{2}} : {{3}}` */
    private val sharedCodeFormattingTemplate25 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0*\n}}` */
    private val sharedCodeFormattingTemplate26 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.NewLine,
        )

    /** `if {{0}} {{1}} else {{2}}` */
    private val sharedCodeFormattingTemplate27 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `if {{0}} {{1}}` */
    private val sharedCodeFormattingTemplate28 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `' {{0}} : {{1}}` */
    private val sharedCodeFormattingTemplate29 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("'", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `loop {{0}}` */
    private val sharedCodeFormattingTemplate30 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("loop", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `match {{0}} \{ {{1*, \n}} \}` */
    private val sharedCodeFormattingTemplate31 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("match", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.Concatenation(
                        listOf(
                            CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                            CodeFormattingTemplate.NewLine,
                        ),
                    ),
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `while {{0}} {{1}}` */
    private val sharedCodeFormattingTemplate32 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("while", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} [ {{1}} ]` */
    private val sharedCodeFormattingTemplate33 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}} {{1}}` */
    private val sharedCodeFormattingTemplate34 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate35 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{1}} {{2}}` */
    private val sharedCodeFormattingTemplate36 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{1}}` */
    private val sharedCodeFormattingTemplate37 =
        CodeFormattingTemplate.OneSubstitution(1)

    /** `return {{0}}` */
    private val sharedCodeFormattingTemplate38 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("return", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `return` */
    private val sharedCodeFormattingTemplate39 =
        CodeFormattingTemplate.LiteralToken("return", OutputTokenType.Word)

    /** `( {{0*,}} )` */
    private val sharedCodeFormattingTemplate40 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}} ;` */
    private val sharedCodeFormattingTemplate41 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `fn {{0}} < {{1*,}} > ( {{2*,}} ) -> {{3}} where {{4*,}} {{5}}` */
    private val sharedCodeFormattingTemplate42 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(5),
            ),
        )

    /** `fn {{0}} < {{1*,}} > ( {{2*,}} ) -> {{3}} where {{4*,}} ;` */
    private val sharedCodeFormattingTemplate43 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `fn {{0}} < {{1*,}} > ( {{2*,}} ) -> {{3}} {{5}}` */
    private val sharedCodeFormattingTemplate44 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(5),
            ),
        )

    /** `fn {{0}} < {{1*,}} > ( {{2*,}} ) -> {{3}} ;` */
    private val sharedCodeFormattingTemplate45 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `fn {{0}} < {{1*,}} > ( {{2*,}} ) where {{4*,}} {{5}}` */
    private val sharedCodeFormattingTemplate46 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(5),
            ),
        )

    /** `fn {{0}} < {{1*,}} > ( {{2*,}} ) where {{4*,}} ;` */
    private val sharedCodeFormattingTemplate47 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `fn {{0}} < {{1*,}} > ( {{2*,}} ) {{5}}` */
    private val sharedCodeFormattingTemplate48 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(5),
            ),
        )

    /** `fn {{0}} < {{1*,}} > ( {{2*,}} ) ;` */
    private val sharedCodeFormattingTemplate49 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `fn {{0}} ( {{2*,}} ) -> {{3}} where {{4*,}} {{5}}` */
    private val sharedCodeFormattingTemplate50 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(5),
            ),
        )

    /** `fn {{0}} ( {{2*,}} ) -> {{3}} where {{4*,}} ;` */
    private val sharedCodeFormattingTemplate51 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `fn {{0}} ( {{2*,}} ) -> {{3}} {{5}}` */
    private val sharedCodeFormattingTemplate52 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(5),
            ),
        )

    /** `fn {{0}} ( {{2*,}} ) -> {{3}} ;` */
    private val sharedCodeFormattingTemplate53 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `fn {{0}} ( {{2*,}} ) where {{4*,}} {{5}}` */
    private val sharedCodeFormattingTemplate54 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(5),
            ),
        )

    /** `fn {{0}} ( {{2*,}} ) where {{4*,}} ;` */
    private val sharedCodeFormattingTemplate55 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `fn {{0}} ( {{2*,}} ) {{5}}` */
    private val sharedCodeFormattingTemplate56 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(5),
            ),
        )

    /** `fn {{0}} ( {{2*,}} ) ;` */
    private val sharedCodeFormattingTemplate57 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} : {{1}}` */
    private val sharedCodeFormattingTemplate58 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}}` */
    private val sharedCodeFormattingTemplate59 =
        CodeFormattingTemplate.OneSubstitution(0)

    /** `& {{0}}` */
    private val sharedCodeFormattingTemplate60 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("\u0026", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `Fn ( {{0*,}} )` */
    private val sharedCodeFormattingTemplate61 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("Fn", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `Fn ( {{0*,}} ) -> {{1}}` */
    private val sharedCodeFormattingTemplate62 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("Fn", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `< {{0*,}} >` */
    private val sharedCodeFormattingTemplate63 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}} : {{1*+}} = {{2}}` */
    private val sharedCodeFormattingTemplate64 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken("+", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0}} : {{1*+}}` */
    private val sharedCodeFormattingTemplate65 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken("+", OutputTokenType.Punctuation),
                ),
            ),
        )

    /** `{{0}} = {{2}}` */
    private val sharedCodeFormattingTemplate66 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0}} < {{1*,}} >` */
    private val sharedCodeFormattingTemplate67 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `mut` */
    private val sharedCodeFormattingTemplate68 =
        CodeFormattingTemplate.LiteralToken("mut", OutputTokenType.Word)

    /** `impl < {{0*,}} > {{1}} for {{2}} where {{3*,}} \{ {{4*\n}} {{5*\n}} \}` */
    private val sharedCodeFormattingTemplate69 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("impl", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `impl < {{0*,}} > {{1}} for {{2}} \{ {{4*\n}} {{5*\n}} \}` */
    private val sharedCodeFormattingTemplate70 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("impl", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `impl < {{0*,}} > {{2}} where {{3*,}} \{ {{4*\n}} {{5*\n}} \}` */
    private val sharedCodeFormattingTemplate71 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("impl", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `impl < {{0*,}} > {{2}} \{ {{4*\n}} {{5*\n}} \}` */
    private val sharedCodeFormattingTemplate72 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("impl", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `impl {{1}} for {{2}} where {{3*,}} \{ {{4*\n}} {{5*\n}} \}` */
    private val sharedCodeFormattingTemplate73 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("impl", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `impl {{1}} for {{2}} \{ {{4*\n}} {{5*\n}} \}` */
    private val sharedCodeFormattingTemplate74 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("impl", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `impl {{2}} where {{3*,}} \{ {{4*\n}} {{5*\n}} \}` */
    private val sharedCodeFormattingTemplate75 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("impl", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `impl {{2}} \{ {{4*\n}} {{5*\n}} \}` */
    private val sharedCodeFormattingTemplate76 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("impl", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `impl {{0*+}}` */
    private val sharedCodeFormattingTemplate77 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("impl", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken("+", OutputTokenType.Punctuation),
                ),
            ),
        )

    /** `impl` */
    private val sharedCodeFormattingTemplate78 =
        CodeFormattingTemplate.LiteralToken("impl", OutputTokenType.Word)

    /** `pub ( {{0}} )` */
    private val sharedCodeFormattingTemplate79 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("pub", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `pub` */
    private val sharedCodeFormattingTemplate80 =
        CodeFormattingTemplate.LiteralToken("pub", OutputTokenType.Word)

    /** `{{0}} mod {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate81 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("mod", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0}} mod {{1}} ;` */
    private val sharedCodeFormattingTemplate82 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("mod", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `mod {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate83 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("mod", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `mod {{1}} ;` */
    private val sharedCodeFormattingTemplate84 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("mod", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `static {{0}} : {{1}} = {{2}} ;` */
    private val sharedCodeFormattingTemplate85 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `static {{0}} : {{1}} ;` */
    private val sharedCodeFormattingTemplate86 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `struct {{0}} < {{1*,}} > where {{2*,}} \{ {{3*,}} \}` */
    private val sharedCodeFormattingTemplate87 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("struct", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `struct {{0}} < {{1*,}} > \{ {{3*,}} \}` */
    private val sharedCodeFormattingTemplate88 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("struct", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `struct {{0}} where {{2*,}} \{ {{3*,}} \}` */
    private val sharedCodeFormattingTemplate89 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("struct", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `struct {{0}} \{ {{3*,}} \}` */
    private val sharedCodeFormattingTemplate90 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("struct", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `trait {{0}} < {{1*,}} > : {{2*+}} where {{3*,}} \{ {{4*\n}} {{5*\n}} \}` */
    private val sharedCodeFormattingTemplate91 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("trait", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken("+", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `trait {{0}} < {{1*,}} > : {{2*+}} \{ {{4*\n}} {{5*\n}} \}` */
    private val sharedCodeFormattingTemplate92 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("trait", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken("+", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `trait {{0}} < {{1*,}} > where {{3*,}} \{ {{4*\n}} {{5*\n}} \}` */
    private val sharedCodeFormattingTemplate93 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("trait", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `trait {{0}} < {{1*,}} > \{ {{4*\n}} {{5*\n}} \}` */
    private val sharedCodeFormattingTemplate94 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("trait", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `trait {{0}} : {{2*+}} where {{3*,}} \{ {{4*\n}} {{5*\n}} \}` */
    private val sharedCodeFormattingTemplate95 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("trait", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken("+", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `trait {{0}} : {{2*+}} \{ {{4*\n}} {{5*\n}} \}` */
    private val sharedCodeFormattingTemplate96 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("trait", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken("+", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `trait {{0}} where {{3*,}} \{ {{4*\n}} {{5*\n}} \}` */
    private val sharedCodeFormattingTemplate97 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("trait", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `trait {{0}} \{ {{4*\n}} {{5*\n}} \}` */
    private val sharedCodeFormattingTemplate98 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("trait", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `struct {{0}} < {{1*,}} > ( {{2*,}} ) where {{3*,}} ;` */
    private val sharedCodeFormattingTemplate99 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("struct", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `struct {{0}} < {{1*,}} > ( {{2*,}} ) ;` */
    private val sharedCodeFormattingTemplate100 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("struct", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `struct {{0}} ( {{2*,}} ) where {{3*,}} ;` */
    private val sharedCodeFormattingTemplate101 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("struct", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `struct {{0}} ( {{2*,}} ) ;` */
    private val sharedCodeFormattingTemplate102 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("struct", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `type {{0}} < {{1*,}} > = {{2}} ;` */
    private val sharedCodeFormattingTemplate103 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("type", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `type {{0}} = {{2}} ;` */
    private val sharedCodeFormattingTemplate104 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("type", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `use {{0}} ;` */
    private val sharedCodeFormattingTemplate105 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("use", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} let {{1}} : {{2}} = {{3}} ;` */
    private val sharedCodeFormattingTemplate106 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} let {{1}} : {{2}} ;` */
    private val sharedCodeFormattingTemplate107 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} let {{1}} = {{3}} ;` */
    private val sharedCodeFormattingTemplate108 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} let {{1}} ;` */
    private val sharedCodeFormattingTemplate109 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} => {{1}}` */
    private val sharedCodeFormattingTemplate110 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0*::}}` */
    private val sharedCodeFormattingTemplate111 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.LiteralToken("::", OutputTokenType.Punctuation),
        )

    /** `..` */
    private val sharedCodeFormattingTemplate112 =
        CodeFormattingTemplate.LiteralToken("..", OutputTokenType.Punctuation)

    /** `{{0*|}}` */
    private val sharedCodeFormattingTemplate113 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.LiteralToken("|", OutputTokenType.Punctuation),
        )

    /** `;` */
    private val sharedCodeFormattingTemplate114 =
        CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation)

    /** `.. {{0}}` */
    private val sharedCodeFormattingTemplate115 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("..", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `dyn {{0*+}}` */
    private val sharedCodeFormattingTemplate116 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("dyn", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken("+", OutputTokenType.Punctuation),
                ),
            ),
        )

    /** `dyn` */
    private val sharedCodeFormattingTemplate117 =
        CodeFormattingTemplate.LiteralToken("dyn", OutputTokenType.Word)

    /** `crate` */
    private val sharedCodeFormattingTemplate118 =
        CodeFormattingTemplate.LiteralToken("crate", OutputTokenType.Word)

    /** `self` */
    private val sharedCodeFormattingTemplate119 =
        CodeFormattingTemplate.LiteralToken("self", OutputTokenType.Word)

    /** `super` */
    private val sharedCodeFormattingTemplate120 =
        CodeFormattingTemplate.LiteralToken("super", OutputTokenType.Word)

    /** `` */
    private val sharedCodeFormattingTemplate121 =
        CodeFormattingTemplate.empty
}
