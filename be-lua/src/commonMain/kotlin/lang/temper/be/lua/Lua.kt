@file:lang.temper.common.Generated("OutputGrammarCodeGenerator")
@file:Suppress("ktlint", "unused", "CascadeIf", "MagicNumber", "MemberNameEqualsClassName", "MemberVisibilityCanBePrivate")

package lang.temper.be.lua
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
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenAssociation
import lang.temper.format.TokenSink
import lang.temper.log.Position
import lang.temper.name.OutName
import lang.temper.name.TemperName

object Lua {
    sealed interface Tree : OutTree<Tree> {
        override fun formattingHints(): FormattingHints = LuaFormattingHints.getInstance()
        override val operatorDefinition: LuaOperatorDefinition?
        override fun deepCopy(): Tree
    }
    sealed class BaseTree(
        pos: Position,
    ) : BaseOutTree<Tree>(pos), Tree
    sealed interface Data : OutData<Data> {
        override fun formattingHints(): FormattingHints = LuaFormattingHints.getInstance()
        override val operatorDefinition: LuaOperatorDefinition?
    }
    sealed class BaseData : BaseOutData<Data>(), Data

    sealed interface Program : Tree {
        override fun deepCopy(): Program
    }

    class Chunk(
        pos: Position,
        body: Iterable<Stmt>,
        last: LastStmt?,
    ) : BaseTree(pos), Program {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate0
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.body)
                1 -> this.last ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _body: MutableList<Stmt> = mutableListOf()
        var body: List<Stmt>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        private var _last: LastStmt?
        var last: LastStmt?
            get() = _last
            set(newValue) { _last = updateTreeConnection(_last, newValue) }
        override fun deepCopy(): Chunk {
            return Chunk(pos, body = this.body.deepCopy(), last = this.last?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Chunk && this.body == other.body && this.last == other.last
        }
        override fun hashCode(): Int {
            var hc = body.hashCode()
            hc = 31 * hc + (last?.hashCode() ?: 0)
            return hc
        }
        init {
            updateTreeConnections(this._body, body)
            this._last = updateTreeConnection(null, last)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Chunk).body },
                { n -> (n as Chunk).last },
            )
        }
    }

    sealed interface Stmt : Tree {
        override fun deepCopy(): Stmt
    }

    sealed interface LastStmt : Tree {
        override fun deepCopy(): LastStmt
    }

    class LabelStmt(
        pos: Position,
        name: Name,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate1
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _name: Name
        var name: Name
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override fun deepCopy(): LabelStmt {
            return LabelStmt(pos, name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LabelStmt && this.name == other.name
        }
        override fun hashCode(): Int {
            return name.hashCode()
        }
        init {
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LabelStmt).name },
            )
        }
    }

    class DoStmt(
        pos: Position,
        body: Chunk,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate2
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _body: Chunk
        var body: Chunk
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): DoStmt {
            return DoStmt(pos, body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is DoStmt && this.body == other.body
        }
        override fun hashCode(): Int {
            return body.hashCode()
        }
        init {
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as DoStmt).body },
            )
        }
    }

    class WhileStmt(
        pos: Position,
        cond: Expr,
        body: Chunk,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate3
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.cond
                1 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _cond: Expr
        var cond: Expr
            get() = _cond
            set(newValue) { _cond = updateTreeConnection(_cond, newValue) }
        private var _body: Chunk
        var body: Chunk
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): WhileStmt {
            return WhileStmt(pos, cond = this.cond.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is WhileStmt && this.cond == other.cond && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = cond.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._cond = updateTreeConnection(null, cond)
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as WhileStmt).cond },
                { n -> (n as WhileStmt).body },
            )
        }
    }

    class IfStmt(
        pos: Position,
        cond: Expr,
        then: Chunk,
        elseIfs: Iterable<ElseIf>,
        els: Else?,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate4
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.cond
                1 -> this.then
                2 -> FormattableTreeGroup(this.elseIfs)
                3 -> this.els ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _cond: Expr
        var cond: Expr
            get() = _cond
            set(newValue) { _cond = updateTreeConnection(_cond, newValue) }
        private var _then: Chunk
        var then: Chunk
            get() = _then
            set(newValue) { _then = updateTreeConnection(_then, newValue) }
        private val _elseIfs: MutableList<ElseIf> = mutableListOf()
        var elseIfs: List<ElseIf>
            get() = _elseIfs
            set(newValue) { updateTreeConnections(_elseIfs, newValue) }
        private var _els: Else?
        var els: Else?
            get() = _els
            set(newValue) { _els = updateTreeConnection(_els, newValue) }
        override fun deepCopy(): IfStmt {
            return IfStmt(pos, cond = this.cond.deepCopy(), then = this.then.deepCopy(), elseIfs = this.elseIfs.deepCopy(), els = this.els?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is IfStmt && this.cond == other.cond && this.then == other.then && this.elseIfs == other.elseIfs && this.els == other.els
        }
        override fun hashCode(): Int {
            var hc = cond.hashCode()
            hc = 31 * hc + then.hashCode()
            hc = 31 * hc + elseIfs.hashCode()
            hc = 31 * hc + (els?.hashCode() ?: 0)
            return hc
        }
        init {
            this._cond = updateTreeConnection(null, cond)
            this._then = updateTreeConnection(null, then)
            updateTreeConnections(this._elseIfs, elseIfs)
            this._els = updateTreeConnection(null, els)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as IfStmt).cond },
                { n -> (n as IfStmt).then },
                { n -> (n as IfStmt).elseIfs },
                { n -> (n as IfStmt).els },
            )
        }
    }

    class SetStmt(
        pos: Position,
        targets: SetTargets,
        exprs: Exprs,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate5
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.targets
                1 -> this.exprs
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _targets: SetTargets
        var targets: SetTargets
            get() = _targets
            set(newValue) { _targets = updateTreeConnection(_targets, newValue) }
        private var _exprs: Exprs
        var exprs: Exprs
            get() = _exprs
            set(newValue) { _exprs = updateTreeConnection(_exprs, newValue) }
        override fun deepCopy(): SetStmt {
            return SetStmt(pos, targets = this.targets.deepCopy(), exprs = this.exprs.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SetStmt && this.targets == other.targets && this.exprs == other.exprs
        }
        override fun hashCode(): Int {
            var hc = targets.hashCode()
            hc = 31 * hc + exprs.hashCode()
            return hc
        }
        init {
            this._targets = updateTreeConnection(null, targets)
            this._exprs = updateTreeConnection(null, exprs)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SetStmt).targets },
                { n -> (n as SetStmt).exprs },
            )
        }
    }

    class FunctionStmt(
        pos: Position,
        dest: SetTarget,
        params: Params,
        body: Chunk,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate6
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.dest
                1 -> this.params
                2 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _dest: SetTarget
        var dest: SetTarget
            get() = _dest
            set(newValue) { _dest = updateTreeConnection(_dest, newValue) }
        private var _params: Params
        var params: Params
            get() = _params
            set(newValue) { _params = updateTreeConnection(_params, newValue) }
        private var _body: Chunk
        var body: Chunk
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): FunctionStmt {
            return FunctionStmt(pos, dest = this.dest.deepCopy(), params = this.params.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FunctionStmt && this.dest == other.dest && this.params == other.params && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = dest.hashCode()
            hc = 31 * hc + params.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._dest = updateTreeConnection(null, dest)
            this._params = updateTreeConnection(null, params)
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FunctionStmt).dest },
                { n -> (n as FunctionStmt).params },
                { n -> (n as FunctionStmt).body },
            )
        }
    }

    class LocalDeclStmt(
        pos: Position,
        targets: SetTargets,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate7
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.targets
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _targets: SetTargets
        var targets: SetTargets
            get() = _targets
            set(newValue) { _targets = updateTreeConnection(_targets, newValue) }
        override fun deepCopy(): LocalDeclStmt {
            return LocalDeclStmt(pos, targets = this.targets.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LocalDeclStmt && this.targets == other.targets
        }
        override fun hashCode(): Int {
            return targets.hashCode()
        }
        init {
            this._targets = updateTreeConnection(null, targets)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LocalDeclStmt).targets },
            )
        }
    }

    class LocalStmt(
        pos: Position,
        targets: SetTargets,
        exprs: Exprs,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate8
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.targets
                1 -> this.exprs
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _targets: SetTargets
        var targets: SetTargets
            get() = _targets
            set(newValue) { _targets = updateTreeConnection(_targets, newValue) }
        private var _exprs: Exprs
        var exprs: Exprs
            get() = _exprs
            set(newValue) { _exprs = updateTreeConnection(_exprs, newValue) }
        override fun deepCopy(): LocalStmt {
            return LocalStmt(pos, targets = this.targets.deepCopy(), exprs = this.exprs.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LocalStmt && this.targets == other.targets && this.exprs == other.exprs
        }
        override fun hashCode(): Int {
            var hc = targets.hashCode()
            hc = 31 * hc + exprs.hashCode()
            return hc
        }
        init {
            this._targets = updateTreeConnection(null, targets)
            this._exprs = updateTreeConnection(null, exprs)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LocalStmt).targets },
                { n -> (n as LocalStmt).exprs },
            )
        }
    }

    class LocalFunctionStmt(
        pos: Position,
        name: Name,
        params: Params,
        body: Chunk,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate9
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name
                1 -> this.params
                2 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _name: Name
        var name: Name
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _params: Params
        var params: Params
            get() = _params
            set(newValue) { _params = updateTreeConnection(_params, newValue) }
        private var _body: Chunk
        var body: Chunk
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): LocalFunctionStmt {
            return LocalFunctionStmt(pos, name = this.name.deepCopy(), params = this.params.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LocalFunctionStmt && this.name == other.name && this.params == other.params && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = name.hashCode()
            hc = 31 * hc + params.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._name = updateTreeConnection(null, name)
            this._params = updateTreeConnection(null, params)
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LocalFunctionStmt).name },
                { n -> (n as LocalFunctionStmt).params },
                { n -> (n as LocalFunctionStmt).body },
            )
        }
    }

    class CallStmt(
        pos: Position,
        callExpr: CallExpr,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate10
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.callExpr
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _callExpr: CallExpr
        var callExpr: CallExpr
            get() = _callExpr
            set(newValue) { _callExpr = updateTreeConnection(_callExpr, newValue) }
        override fun deepCopy(): CallStmt {
            return CallStmt(pos, callExpr = this.callExpr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is CallStmt && this.callExpr == other.callExpr
        }
        override fun hashCode(): Int {
            return callExpr.hashCode()
        }
        init {
            this._callExpr = updateTreeConnection(null, callExpr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as CallStmt).callExpr },
            )
        }
    }

    class Comment(
        pos: Position,
        var text: String,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.comment("--[[${text}]]")
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

    class GotoStmt(
        pos: Position,
        name: Name,
    ) : BaseTree(pos), Stmt, LastStmt {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate11
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _name: Name
        var name: Name
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override fun deepCopy(): GotoStmt {
            return GotoStmt(pos, name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is GotoStmt && this.name == other.name
        }
        override fun hashCode(): Int {
            return name.hashCode()
        }
        init {
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as GotoStmt).name },
            )
        }
    }

    class ReturnStmt(
        pos: Position,
        exprs: Exprs,
    ) : BaseTree(pos), LastStmt {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate12
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.exprs
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _exprs: Exprs
        var exprs: Exprs
            get() = _exprs
            set(newValue) { _exprs = updateTreeConnection(_exprs, newValue) }
        override fun deepCopy(): ReturnStmt {
            return ReturnStmt(pos, exprs = this.exprs.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ReturnStmt && this.exprs == other.exprs
        }
        override fun hashCode(): Int {
            return exprs.hashCode()
        }
        init {
            this._exprs = updateTreeConnection(null, exprs)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ReturnStmt).exprs },
            )
        }
    }

    class BreakStmt(
        pos: Position,
    ) : BaseTree(pos), LastStmt {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate13
        override val formatElementCount
            get() = 0
        override fun deepCopy(): BreakStmt {
            return BreakStmt(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BreakStmt
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    sealed interface Expr : Tree {
        override fun deepCopy(): Expr
    }

    sealed interface CallExpr : Tree, Expr {
        override fun deepCopy(): CallExpr
    }

    class MethodCallExpr(
        pos: Position,
        func: Expr,
        method: Name,
        args: Args,
    ) : BaseTree(pos), CallExpr {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate14
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.func
                1 -> this.method
                2 -> this.args
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _func: Expr
        var func: Expr
            get() = _func
            set(newValue) { _func = updateTreeConnection(_func, newValue) }
        private var _method: Name
        var method: Name
            get() = _method
            set(newValue) { _method = updateTreeConnection(_method, newValue) }
        private var _args: Args
        var args: Args
            get() = _args
            set(newValue) { _args = updateTreeConnection(_args, newValue) }
        override fun deepCopy(): MethodCallExpr {
            return MethodCallExpr(pos, func = this.func.deepCopy(), method = this.method.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MethodCallExpr && this.func == other.func && this.method == other.method && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = func.hashCode()
            hc = 31 * hc + method.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._func = updateTreeConnection(null, func)
            this._method = updateTreeConnection(null, method)
            this._args = updateTreeConnection(null, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as MethodCallExpr).func },
                { n -> (n as MethodCallExpr).method },
                { n -> (n as MethodCallExpr).args },
            )
        }
    }

    class FunctionCallExpr(
        pos: Position,
        func: Expr,
        args: Args,
    ) : BaseTree(pos), CallExpr {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate15
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.func
                1 -> this.args
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _func: Expr
        var func: Expr
            get() = _func
            set(newValue) { _func = updateTreeConnection(_func, newValue) }
        private var _args: Args
        var args: Args
            get() = _args
            set(newValue) { _args = updateTreeConnection(_args, newValue) }
        override fun deepCopy(): FunctionCallExpr {
            return FunctionCallExpr(pos, func = this.func.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FunctionCallExpr && this.func == other.func && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = func.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._func = updateTreeConnection(null, func)
            this._args = updateTreeConnection(null, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FunctionCallExpr).func },
                { n -> (n as FunctionCallExpr).args },
            )
        }
    }

    class Args(
        pos: Position,
        exprs: Exprs,
    ) : BaseTree(pos) {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate16
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.exprs
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _exprs: Exprs
        var exprs: Exprs
            get() = _exprs
            set(newValue) { _exprs = updateTreeConnection(_exprs, newValue) }
        override fun deepCopy(): Args {
            return Args(pos, exprs = this.exprs.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Args && this.exprs == other.exprs
        }
        override fun hashCode(): Int {
            return exprs.hashCode()
        }
        init {
            this._exprs = updateTreeConnection(null, exprs)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Args).exprs },
            )
        }
    }

    sealed interface LiteralExpr : Tree, Expr {
        override fun deepCopy(): LiteralExpr
    }

    class Name(
        pos: Position,
        var id: LuaName,
        var sourceIdentifier: TemperName? = null,
    ) : BaseTree(pos), LiteralExpr {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(outName.toToken(inOperatorPosition = false))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        val outName: OutName
            get() = OutName(id.text, sourceIdentifier)
        override fun deepCopy(): Name {
            return Name(pos, id = this.id, sourceIdentifier = this.sourceIdentifier)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Name && this.id == other.id && this.sourceIdentifier == other.sourceIdentifier
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + (sourceIdentifier?.hashCode() ?: 0)
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class ElseIf(
        pos: Position,
        cond: Expr,
        then: Chunk,
    ) : BaseTree(pos) {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate17
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.cond
                1 -> this.then
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _cond: Expr
        var cond: Expr
            get() = _cond
            set(newValue) { _cond = updateTreeConnection(_cond, newValue) }
        private var _then: Chunk
        var then: Chunk
            get() = _then
            set(newValue) { _then = updateTreeConnection(_then, newValue) }
        override fun deepCopy(): ElseIf {
            return ElseIf(pos, cond = this.cond.deepCopy(), then = this.then.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ElseIf && this.cond == other.cond && this.then == other.then
        }
        override fun hashCode(): Int {
            var hc = cond.hashCode()
            hc = 31 * hc + then.hashCode()
            return hc
        }
        init {
            this._cond = updateTreeConnection(null, cond)
            this._then = updateTreeConnection(null, then)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ElseIf).cond },
                { n -> (n as ElseIf).then },
            )
        }
    }

    class Else(
        pos: Position,
        then: Chunk,
    ) : BaseTree(pos) {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate18
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.then
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _then: Chunk
        var then: Chunk
            get() = _then
            set(newValue) { _then = updateTreeConnection(_then, newValue) }
        override fun deepCopy(): Else {
            return Else(pos, then = this.then.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Else && this.then == other.then
        }
        override fun hashCode(): Int {
            return then.hashCode()
        }
        init {
            this._then = updateTreeConnection(null, then)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Else).then },
            )
        }
    }

    sealed interface SetTargetOrWrapped : Tree {
        override fun deepCopy(): SetTargetOrWrapped
    }

    sealed interface SetTarget : Tree, SetTargetOrWrapped {
        override fun deepCopy(): SetTarget
    }

    class Params(
        pos: Position,
        params: Iterable<ParamOrRest>,
    ) : BaseTree(pos) {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate19
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.params)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _params: MutableList<ParamOrRest> = mutableListOf()
        var params: List<ParamOrRest>
            get() = _params
            set(newValue) { updateTreeConnections(_params, newValue) }
        override fun deepCopy(): Params {
            return Params(pos, params = this.params.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Params && this.params == other.params
        }
        override fun hashCode(): Int {
            return params.hashCode()
        }
        init {
            updateTreeConnections(this._params, params)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Params).params },
            )
        }
    }

    class SetTargets(
        pos: Position,
        targets: Iterable<SetTarget>,
    ) : BaseTree(pos) {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate20
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.targets)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _targets: MutableList<SetTarget> = mutableListOf()
        var targets: List<SetTarget>
            get() = _targets
            set(newValue) { updateTreeConnections(_targets, newValue) }
        override fun deepCopy(): SetTargets {
            return SetTargets(pos, targets = this.targets.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SetTargets && this.targets == other.targets
        }
        override fun hashCode(): Int {
            return targets.hashCode()
        }
        init {
            updateTreeConnections(this._targets, targets)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SetTargets).targets },
            )
        }
    }

    class Exprs(
        pos: Position,
        exprs: Iterable<Expr>,
    ) : BaseTree(pos) {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate20
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
        private val _exprs: MutableList<Expr> = mutableListOf()
        var exprs: List<Expr>
            get() = _exprs
            set(newValue) { updateTreeConnections(_exprs, newValue) }
        override fun deepCopy(): Exprs {
            return Exprs(pos, exprs = this.exprs.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Exprs && this.exprs == other.exprs
        }
        override fun hashCode(): Int {
            return exprs.hashCode()
        }
        init {
            updateTreeConnections(this._exprs, exprs)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Exprs).exprs },
            )
        }
    }

    sealed interface ParamOrRest : Tree {
        override fun deepCopy(): ParamOrRest
    }

    class Param(
        pos: Position,
        name: Name,
    ) : BaseTree(pos), ParamOrRest {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate21
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _name: Name
        var name: Name
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override fun deepCopy(): Param {
            return Param(pos, name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Param && this.name == other.name
        }
        override fun hashCode(): Int {
            return name.hashCode()
        }
        init {
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Param).name },
            )
        }
    }

    class RestExpr(
        pos: Position,
    ) : BaseTree(pos), ParamOrRest, Expr {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.punctuation("...")
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): RestExpr {
            return RestExpr(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is RestExpr
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class Rest(
        pos: Position,
    ) : BaseTree(pos) {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate22
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

    class WrappedExpr(
        pos: Position,
        expr: Expr,
    ) : BaseTree(pos), SetTargetOrWrapped, LiteralExpr {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate16
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
        override fun deepCopy(): WrappedExpr {
            return WrappedExpr(pos, expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is WrappedExpr && this.expr == other.expr
        }
        override fun hashCode(): Int {
            return expr.hashCode()
        }
        init {
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as WrappedExpr).expr },
            )
        }
    }

    class NameSetTarget(
        pos: Position,
        target: Name,
    ) : BaseTree(pos), SetTarget {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate21
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.target
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _target: Name
        var target: Name
            get() = _target
            set(newValue) { _target = updateTreeConnection(_target, newValue) }
        override fun deepCopy(): NameSetTarget {
            return NameSetTarget(pos, target = this.target.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is NameSetTarget && this.target == other.target
        }
        override fun hashCode(): Int {
            return target.hashCode()
        }
        init {
            this._target = updateTreeConnection(null, target)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as NameSetTarget).target },
            )
        }
    }

    class DotSetTarget(
        pos: Position,
        obj: SetTargetOrWrapped,
        index: Name,
    ) : BaseTree(pos), SetTarget {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate23
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.obj
                1 -> this.index
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _obj: SetTargetOrWrapped
        var obj: SetTargetOrWrapped
            get() = _obj
            set(newValue) { _obj = updateTreeConnection(_obj, newValue) }
        private var _index: Name
        var index: Name
            get() = _index
            set(newValue) { _index = updateTreeConnection(_index, newValue) }
        override fun deepCopy(): DotSetTarget {
            return DotSetTarget(pos, obj = this.obj.deepCopy(), index = this.index.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is DotSetTarget && this.obj == other.obj && this.index == other.index
        }
        override fun hashCode(): Int {
            var hc = obj.hashCode()
            hc = 31 * hc + index.hashCode()
            return hc
        }
        init {
            this._obj = updateTreeConnection(null, obj)
            this._index = updateTreeConnection(null, index)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as DotSetTarget).obj },
                { n -> (n as DotSetTarget).index },
            )
        }
    }

    class IndexSetTarget(
        pos: Position,
        obj: SetTargetOrWrapped,
        index: Expr,
    ) : BaseTree(pos), SetTarget {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate24
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.obj
                1 -> this.index
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _obj: SetTargetOrWrapped
        var obj: SetTargetOrWrapped
            get() = _obj
            set(newValue) { _obj = updateTreeConnection(_obj, newValue) }
        private var _index: Expr
        var index: Expr
            get() = _index
            set(newValue) { _index = updateTreeConnection(_index, newValue) }
        override fun deepCopy(): IndexSetTarget {
            return IndexSetTarget(pos, obj = this.obj.deepCopy(), index = this.index.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is IndexSetTarget && this.obj == other.obj && this.index == other.index
        }
        override fun hashCode(): Int {
            var hc = obj.hashCode()
            hc = 31 * hc + index.hashCode()
            return hc
        }
        init {
            this._obj = updateTreeConnection(null, obj)
            this._index = updateTreeConnection(null, index)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as IndexSetTarget).obj },
                { n -> (n as IndexSetTarget).index },
            )
        }
    }

    class BinaryExpr(
        pos: Position,
        left: Expr,
        op: BinaryOp,
        right: Expr,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate25
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.left
                1 -> this.op
                2 -> this.right
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _left: Expr
        var left: Expr
            get() = _left
            set(newValue) { _left = updateTreeConnection(_left, newValue) }
        private var _op: BinaryOp
        var op: BinaryOp
            get() = _op
            set(newValue) { _op = updateTreeConnection(_op, newValue) }
        private var _right: Expr
        var right: Expr
            get() = _right
            set(newValue) { _right = updateTreeConnection(_right, newValue) }
        override fun deepCopy(): BinaryExpr {
            return BinaryExpr(pos, left = this.left.deepCopy(), op = this.op.deepCopy(), right = this.right.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BinaryExpr && this.left == other.left && this.op == other.op && this.right == other.right
        }
        override fun hashCode(): Int {
            var hc = left.hashCode()
            hc = 31 * hc + op.hashCode()
            hc = 31 * hc + right.hashCode()
            return hc
        }
        init {
            this._left = updateTreeConnection(null, left)
            this._op = updateTreeConnection(null, op)
            this._right = updateTreeConnection(null, right)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as BinaryExpr).left },
                { n -> (n as BinaryExpr).op },
                { n -> (n as BinaryExpr).right },
            )
        }
    }

    class UnaryExpr(
        pos: Position,
        op: UnaryOp,
        right: Expr,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate15
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.op
                1 -> this.right
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _op: UnaryOp
        var op: UnaryOp
            get() = _op
            set(newValue) { _op = updateTreeConnection(_op, newValue) }
        private var _right: Expr
        var right: Expr
            get() = _right
            set(newValue) { _right = updateTreeConnection(_right, newValue) }
        override fun deepCopy(): UnaryExpr {
            return UnaryExpr(pos, op = this.op.deepCopy(), right = this.right.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is UnaryExpr && this.op == other.op && this.right == other.right
        }
        override fun hashCode(): Int {
            var hc = op.hashCode()
            hc = 31 * hc + right.hashCode()
            return hc
        }
        init {
            this._op = updateTreeConnection(null, op)
            this._right = updateTreeConnection(null, right)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as UnaryExpr).op },
                { n -> (n as UnaryExpr).right },
            )
        }
    }

    class FunctionExpr(
        pos: Position,
        params: Params,
        body: Chunk,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate26
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.params
                1 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _params: Params
        var params: Params
            get() = _params
            set(newValue) { _params = updateTreeConnection(_params, newValue) }
        private var _body: Chunk
        var body: Chunk
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): FunctionExpr {
            return FunctionExpr(pos, params = this.params.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FunctionExpr && this.params == other.params && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = params.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._params = updateTreeConnection(null, params)
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FunctionExpr).params },
                { n -> (n as FunctionExpr).body },
            )
        }
    }

    class BinaryOp(
        pos: Position,
        var opEnum: BinaryOpEnum,
        var expressionOperatorDefinition: LuaOperatorDefinition,
    ) : BaseTree(pos) {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            opEnum.renderTo(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): BinaryOp {
            return BinaryOp(pos, opEnum = this.opEnum, expressionOperatorDefinition = this.expressionOperatorDefinition)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BinaryOp && this.opEnum == other.opEnum && this.expressionOperatorDefinition == other.expressionOperatorDefinition
        }
        override fun hashCode(): Int {
            var hc = opEnum.hashCode()
            hc = 31 * hc + expressionOperatorDefinition.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class UnaryOp(
        pos: Position,
        var opEnum: UnaryOpEnum,
        var expressionOperatorDefinition: LuaOperatorDefinition,
    ) : BaseTree(pos) {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            opEnum.renderTo(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): UnaryOp {
            return UnaryOp(pos, opEnum = this.opEnum, expressionOperatorDefinition = this.expressionOperatorDefinition)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is UnaryOp && this.opEnum == other.opEnum && this.expressionOperatorDefinition == other.expressionOperatorDefinition
        }
        override fun hashCode(): Int {
            var hc = opEnum.hashCode()
            hc = 31 * hc + expressionOperatorDefinition.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class DotIndexExpr(
        pos: Position,
        obj: LiteralExpr,
        index: Name,
    ) : BaseTree(pos), LiteralExpr {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate23
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.obj
                1 -> this.index
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _obj: LiteralExpr
        var obj: LiteralExpr
            get() = _obj
            set(newValue) { _obj = updateTreeConnection(_obj, newValue) }
        private var _index: Name
        var index: Name
            get() = _index
            set(newValue) { _index = updateTreeConnection(_index, newValue) }
        override fun deepCopy(): DotIndexExpr {
            return DotIndexExpr(pos, obj = this.obj.deepCopy(), index = this.index.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is DotIndexExpr && this.obj == other.obj && this.index == other.index
        }
        override fun hashCode(): Int {
            var hc = obj.hashCode()
            hc = 31 * hc + index.hashCode()
            return hc
        }
        init {
            this._obj = updateTreeConnection(null, obj)
            this._index = updateTreeConnection(null, index)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as DotIndexExpr).obj },
                { n -> (n as DotIndexExpr).index },
            )
        }
    }

    class TableExpr(
        pos: Position,
        args: Iterable<TableEntry>,
    ) : BaseTree(pos), LiteralExpr {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate27
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
        private val _args: MutableList<TableEntry> = mutableListOf()
        var args: List<TableEntry>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): TableExpr {
            return TableExpr(pos, args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TableExpr && this.args == other.args
        }
        override fun hashCode(): Int {
            return args.hashCode()
        }
        init {
            updateTreeConnections(this._args, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TableExpr).args },
            )
        }
    }

    sealed interface TableEntry : Tree {
        override fun deepCopy(): TableEntry
    }

    class NamedTableEntry(
        pos: Position,
        key: Name,
        value: Expr,
    ) : BaseTree(pos), TableEntry {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate28
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
        private var _key: Name
        var key: Name
            get() = _key
            set(newValue) { _key = updateTreeConnection(_key, newValue) }
        private var _value: Expr
        var value: Expr
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): NamedTableEntry {
            return NamedTableEntry(pos, key = this.key.deepCopy(), value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is NamedTableEntry && this.key == other.key && this.value == other.value
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
                { n -> (n as NamedTableEntry).key },
                { n -> (n as NamedTableEntry).value },
            )
        }
    }

    class WrappedTableEntry(
        pos: Position,
        key: Expr,
        value: Expr,
    ) : BaseTree(pos), TableEntry {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate29
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
        override fun deepCopy(): WrappedTableEntry {
            return WrappedTableEntry(pos, key = this.key.deepCopy(), value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is WrappedTableEntry && this.key == other.key && this.value == other.value
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
                { n -> (n as WrappedTableEntry).key },
                { n -> (n as WrappedTableEntry).value },
            )
        }
    }

    class IndexTableEntry(
        pos: Position,
        value: Expr,
    ) : BaseTree(pos), TableEntry {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate21
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.value
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _value: Expr
        var value: Expr
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): IndexTableEntry {
            return IndexTableEntry(pos, value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is IndexTableEntry && this.value == other.value
        }
        override fun hashCode(): Int {
            return value.hashCode()
        }
        init {
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as IndexTableEntry).value },
            )
        }
    }

    class Num(
        pos: Position,
        var n: Number,
    ) : BaseTree(pos), LiteralExpr {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.number("$n")
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): Num {
            return Num(pos, n = this.n)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Num && this.n == other.n
        }
        override fun hashCode(): Int {
            return n.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class Str(
        pos: Position,
        var s: String,
    ) : BaseTree(pos), LiteralExpr {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.quoted(stringTokenText(s))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): Str {
            return Str(pos, s = this.s)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Str && this.s == other.s
        }
        override fun hashCode(): Int {
            return s.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class IndexExpr(
        pos: Position,
        obj: LiteralExpr,
        index: Expr,
    ) : BaseTree(pos), LiteralExpr {
        override val operatorDefinition: LuaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate24
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.obj
                1 -> this.index
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _obj: LiteralExpr
        var obj: LiteralExpr
            get() = _obj
            set(newValue) { _obj = updateTreeConnection(_obj, newValue) }
        private var _index: Expr
        var index: Expr
            get() = _index
            set(newValue) { _index = updateTreeConnection(_index, newValue) }
        override fun deepCopy(): IndexExpr {
            return IndexExpr(pos, obj = this.obj.deepCopy(), index = this.index.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is IndexExpr && this.obj == other.obj && this.index == other.index
        }
        override fun hashCode(): Int {
            var hc = obj.hashCode()
            hc = 31 * hc + index.hashCode()
            return hc
        }
        init {
            this._obj = updateTreeConnection(null, obj)
            this._index = updateTreeConnection(null, index)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as IndexExpr).obj },
                { n -> (n as IndexExpr).index },
            )
        }
    }

    /** `{{0*}} {{1}}` */
    private val sharedCodeFormattingTemplate0 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `:: {{0}} ::` */
    private val sharedCodeFormattingTemplate1 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("::", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("::", OutputTokenType.Punctuation),
            ),
        )

    /** `do {{0}} end` */
    private val sharedCodeFormattingTemplate2 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `while {{0}} do {{1}} end` */
    private val sharedCodeFormattingTemplate3 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("while", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `if {{0}} then {{1}} {{2*}} {{3}} end` */
    private val sharedCodeFormattingTemplate4 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("then", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `{{0}} = {{1}} ;` */
    private val sharedCodeFormattingTemplate5 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `function {{0}} {{1}} {{2}} end` */
    private val sharedCodeFormattingTemplate6 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `local {{0}} ;` */
    private val sharedCodeFormattingTemplate7 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("local", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `local {{0}} = {{1}} ;` */
    private val sharedCodeFormattingTemplate8 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("local", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `local function {{0}} {{1}} {{2}} end` */
    private val sharedCodeFormattingTemplate9 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("local", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `{{0}} ;` */
    private val sharedCodeFormattingTemplate10 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `goto {{0}} ;` */
    private val sharedCodeFormattingTemplate11 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("goto", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `return {{0}} ;` */
    private val sharedCodeFormattingTemplate12 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("return", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `break ;` */
    private val sharedCodeFormattingTemplate13 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("break", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} : {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate14 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0}} {{1}}` */
    private val sharedCodeFormattingTemplate15 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `( {{0}} )` */
    private val sharedCodeFormattingTemplate16 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `elseif {{0}} then {{1}}` */
    private val sharedCodeFormattingTemplate17 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("elseif", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("then", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `else {{0}}` */
    private val sharedCodeFormattingTemplate18 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `( {{0*,}} )` */
    private val sharedCodeFormattingTemplate19 =
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

    /** `{{0*,}}` */
    private val sharedCodeFormattingTemplate20 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
        )

    /** `{{0}}` */
    private val sharedCodeFormattingTemplate21 =
        CodeFormattingTemplate.OneSubstitution(0)

    /** `...` */
    private val sharedCodeFormattingTemplate22 =
        CodeFormattingTemplate.LiteralToken("...", OutputTokenType.Punctuation)

    /** `{{0}} . {{1}}` */
    private val sharedCodeFormattingTemplate23 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} [ {{1}} ]` */
    private val sharedCodeFormattingTemplate24 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( {{0}} {{1}} {{2}} )` */
    private val sharedCodeFormattingTemplate25 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `function {{0}} {{1}} end` */
    private val sharedCodeFormattingTemplate26 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("end", OutputTokenType.Word),
            ),
        )

    /** `\{ {{0*,}} \}` */
    private val sharedCodeFormattingTemplate27 =
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

    /** `{{0}} = {{1}}` */
    private val sharedCodeFormattingTemplate28 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `[( {{0}} )] = {{1}}` */
    private val sharedCodeFormattingTemplate29 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("[(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )
}
