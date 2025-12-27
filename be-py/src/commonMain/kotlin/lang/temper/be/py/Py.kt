@file:lang.temper.common.Generated("OutputGrammarCodeGenerator")
@file:Suppress("ktlint", "unused", "CascadeIf", "MagicNumber", "MemberNameEqualsClassName", "MemberVisibilityCanBePrivate")

package lang.temper.be.py
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
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.SpecialTokens
import lang.temper.format.TokenAssociation
import lang.temper.format.TokenSink
import lang.temper.lexer.Genre
import lang.temper.log.FilePath
import lang.temper.log.Position
import lang.temper.name.OutName
import lang.temper.name.TemperName
import lang.temper.name.name
import lang.temper.value.DependencyCategory

object Py {
    sealed interface Tree : OutTree<Tree> {
        override fun formattingHints(): FormattingHints = PyFormattingHints.getInstance()
        override val operatorDefinition: PyOperatorDefinition?
        override fun deepCopy(): Tree
    }
    sealed class BaseTree(
        pos: Position,
    ) : BaseOutTree<Tree>(pos), Tree
    sealed interface Data : OutData<Data> {
        override fun formattingHints(): FormattingHints = PyFormattingHints.getInstance()
        override val operatorDefinition: PyOperatorDefinition?
    }
    sealed class BaseData : BaseOutData<Data>(), Data

    enum class ArgPrefix : FormattableEnum {
        None,
        Star,
        DoubleStar,
    }

    class Program(
        pos: Position,
        body: Iterable<Stmt>,
        var dependencyCategory: DependencyCategory,
        var genre: Genre,
        var outputPath: FilePath,
    ) : BaseTree(pos) {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate0
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.body)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _body: MutableList<Stmt> = mutableListOf()
        var body: List<Stmt>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        override fun deepCopy(): Program {
            return Program(pos, body = this.body.deepCopy(), dependencyCategory = this.dependencyCategory, genre = this.genre, outputPath = this.outputPath)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Program && this.body == other.body && this.dependencyCategory == other.dependencyCategory && this.genre == other.genre && this.outputPath == other.outputPath
        }
        override fun hashCode(): Int {
            var hc = body.hashCode()
            hc = 31 * hc + dependencyCategory.hashCode()
            hc = 31 * hc + genre.hashCode()
            hc = 31 * hc + outputPath.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._body, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Program).body },
            )
        }
    }

    sealed interface Stmt : Tree {
        override fun deepCopy(): Stmt
    }

    /**
     * FunctionDef(identifier name, arguments args,
     *             stmt* body, expr* decorator_list, expr? returns)
     * funcdef: 'def' NAME parameters ['->' test] ':' suite
     */
    class FunctionDef(
        pos: Position,
        decoratorList: Iterable<Decorator> = listOf(),
        name: Identifier,
        args: Arguments,
        returns: Expr? = null,
        body: Iterable<Stmt>,
        var async: Boolean = false,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (async && returns != null && bodyNeedsPass) {
                    sharedCodeFormattingTemplate1
                } else if (async && returns != null) {
                    sharedCodeFormattingTemplate2
                } else if (async && bodyNeedsPass) {
                    sharedCodeFormattingTemplate3
                } else if (async) {
                    sharedCodeFormattingTemplate4
                } else if (returns != null && bodyNeedsPass) {
                    sharedCodeFormattingTemplate5
                } else if (returns != null) {
                    sharedCodeFormattingTemplate6
                } else if (bodyNeedsPass) {
                    sharedCodeFormattingTemplate7
                } else {
                    sharedCodeFormattingTemplate8
                }
        override val formatElementCount
            get() = 5
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.decoratorList)
                1 -> this.name
                2 -> this.args
                3 -> this.returns ?: FormattableTreeGroup.empty
                4 -> FormattableTreeGroup(this.body)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _decoratorList: MutableList<Decorator> = mutableListOf()
        var decoratorList: List<Decorator>
            get() = _decoratorList
            set(newValue) { updateTreeConnections(_decoratorList, newValue) }
        private var _name: Identifier
        var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _args: Arguments
        var args: Arguments
            get() = _args
            set(newValue) { _args = updateTreeConnection(_args, newValue) }
        private var _returns: Expr?
        var returns: Expr?
            get() = _returns
            set(newValue) { _returns = updateTreeConnection(_returns, newValue) }
        private val _body: MutableList<Stmt> = mutableListOf()
        var body: List<Stmt>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        val bodyNeedsPass: Boolean
            get() = body.needsPass()
        override fun deepCopy(): FunctionDef {
            return FunctionDef(pos, decoratorList = this.decoratorList.deepCopy(), name = this.name.deepCopy(), args = this.args.deepCopy(), returns = this.returns?.deepCopy(), body = this.body.deepCopy(), async = this.async)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FunctionDef && this.decoratorList == other.decoratorList && this.name == other.name && this.args == other.args && this.returns == other.returns && this.body == other.body && this.async == other.async
        }
        override fun hashCode(): Int {
            var hc = decoratorList.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + args.hashCode()
            hc = 31 * hc + (returns?.hashCode() ?: 0)
            hc = 31 * hc + body.hashCode()
            hc = 31 * hc + async.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._decoratorList, decoratorList)
            this._name = updateTreeConnection(null, name)
            this._args = updateTreeConnection(null, args)
            this._returns = updateTreeConnection(null, returns)
            updateTreeConnections(this._body, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FunctionDef).decoratorList },
                { n -> (n as FunctionDef).name },
                { n -> (n as FunctionDef).args },
                { n -> (n as FunctionDef).returns },
                { n -> (n as FunctionDef).body },
            )
        }
    }

    /**
     * ClassDef(identifier name,
     *          expr* bases,
     *          keyword* keywords,
     *          stmt* body,
     *          expr* decorator_list)
     * `classdef: 'class' NAME ['(' [args] ')'] ':' suite`
     */
    class ClassDef(
        pos: Position,
        decoratorList: Iterable<Decorator> = listOf(),
        name: Identifier,
        args: Iterable<CallArg>,
        body: Iterable<Stmt>,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (args.isNotEmpty() && bodyNeedsPass) {
                    sharedCodeFormattingTemplate9
                } else if (args.isNotEmpty()) {
                    sharedCodeFormattingTemplate10
                } else if (bodyNeedsPass) {
                    sharedCodeFormattingTemplate11
                } else {
                    sharedCodeFormattingTemplate12
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.decoratorList)
                1 -> this.name
                2 -> FormattableTreeGroup(this.args)
                3 -> FormattableTreeGroup(this.body)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _decoratorList: MutableList<Decorator> = mutableListOf()
        var decoratorList: List<Decorator>
            get() = _decoratorList
            set(newValue) { updateTreeConnections(_decoratorList, newValue) }
        private var _name: Identifier
        var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private val _args: MutableList<CallArg> = mutableListOf()
        var args: List<CallArg>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        private val _body: MutableList<Stmt> = mutableListOf()
        var body: List<Stmt>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        val bodyNeedsPass: Boolean
            get() = body.needsPass()
        override fun deepCopy(): ClassDef {
            return ClassDef(pos, decoratorList = this.decoratorList.deepCopy(), name = this.name.deepCopy(), args = this.args.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ClassDef && this.decoratorList == other.decoratorList && this.name == other.name && this.args == other.args && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = decoratorList.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + args.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._decoratorList, decoratorList)
            this._name = updateTreeConnection(null, name)
            updateTreeConnections(this._args, args)
            updateTreeConnections(this._body, body)
            require(callArgsValid(args), issue { callArgsIssues(args) })
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ClassDef).decoratorList },
                { n -> (n as ClassDef).name },
                { n -> (n as ClassDef).args },
                { n -> (n as ClassDef).body },
            )
        }
    }

    class Return(
        pos: Position,
        value: Expr? = null,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate13
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
        override fun deepCopy(): Return {
            return Return(pos, value = this.value?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Return && this.value == other.value
        }
        override fun hashCode(): Int {
            return (value?.hashCode() ?: 0)
        }
        init {
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Return).value },
            )
        }
    }

    class Delete(
        pos: Position,
        targets: Iterable<Expr>,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate14
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
        private val _targets: MutableList<Expr> = mutableListOf()
        var targets: List<Expr>
            get() = _targets
            set(newValue) { updateTreeConnections(_targets, newValue) }
        override fun deepCopy(): Delete {
            return Delete(pos, targets = this.targets.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Delete && this.targets == other.targets
        }
        override fun hashCode(): Int {
            return targets.hashCode()
        }
        init {
            updateTreeConnections(this._targets, targets)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Delete).targets },
            )
        }
    }

    /**
     * Assign(expr* targets, expr value)
     * AugAssign(expr target, operator op, expr value)
     * AnnAssign(expr target, expr annotation, expr? value, int simple)
     * expr_stmt: testlist_star_expr (annassign | augassign (yield_expr|testlist) |
     *            ('=' (yield_expr|testlist_star_expr))*)
     * annassign: ':' test ['=' test]
     * testlist_star_expr: (test|star_expr) (',' (test|star_expr))* [',']
     * augassign: ('+=' | '-=' | '*=' | '@=' | '/=' | '%=' | '&=' | '|=' | '^=' |
     *             '<<=' | '>>=' | '**=' | '//=')
     */
    class Assign(
        pos: Position,
        targets: Iterable<Expr>,
        value: Expr,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate15
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.targets)
                1 -> this.value
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _targets: MutableList<Expr> = mutableListOf()
        var targets: List<Expr>
            get() = _targets
            set(newValue) { updateTreeConnections(_targets, newValue) }
        private var _value: Expr
        var value: Expr
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): Assign {
            return Assign(pos, targets = this.targets.deepCopy(), value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Assign && this.targets == other.targets && this.value == other.value
        }
        override fun hashCode(): Int {
            var hc = targets.hashCode()
            hc = 31 * hc + value.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._targets, targets)
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Assign).targets },
                { n -> (n as Assign).value },
            )
        }
    }

    class AugAssign(
        pos: Position,
        target: Expr,
        op: AugAssignOp,
        value: Expr,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate16
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.target
                1 -> this.op
                2 -> this.value
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _target: Expr
        var target: Expr
            get() = _target
            set(newValue) { _target = updateTreeConnection(_target, newValue) }
        private var _op: AugAssignOp
        var op: AugAssignOp
            get() = _op
            set(newValue) { _op = updateTreeConnection(_op, newValue) }
        private var _value: Expr
        var value: Expr
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): AugAssign {
            return AugAssign(pos, target = this.target.deepCopy(), op = this.op.deepCopy(), value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AugAssign && this.target == other.target && this.op == other.op && this.value == other.value
        }
        override fun hashCode(): Int {
            var hc = target.hashCode()
            hc = 31 * hc + op.hashCode()
            hc = 31 * hc + value.hashCode()
            return hc
        }
        init {
            this._target = updateTreeConnection(null, target)
            this._op = updateTreeConnection(null, op)
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AugAssign).target },
                { n -> (n as AugAssign).op },
                { n -> (n as AugAssign).value },
            )
        }
    }

    class AnnAssign(
        pos: Position,
        target: Expr,
        annotation: Expr,
        value: Expr?,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (value != null) {
                    sharedCodeFormattingTemplate17
                } else {
                    sharedCodeFormattingTemplate18
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.target
                1 -> this.annotation
                2 -> this.value ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _target: Expr
        var target: Expr
            get() = _target
            set(newValue) { _target = updateTreeConnection(_target, newValue) }
        private var _annotation: Expr
        var annotation: Expr
            get() = _annotation
            set(newValue) { _annotation = updateTreeConnection(_annotation, newValue) }
        private var _value: Expr?
        var value: Expr?
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): AnnAssign {
            return AnnAssign(pos, target = this.target.deepCopy(), annotation = this.annotation.deepCopy(), value = this.value?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AnnAssign && this.target == other.target && this.annotation == other.annotation && this.value == other.value
        }
        override fun hashCode(): Int {
            var hc = target.hashCode()
            hc = 31 * hc + annotation.hashCode()
            hc = 31 * hc + (value?.hashCode() ?: 0)
            return hc
        }
        init {
            this._target = updateTreeConnection(null, target)
            this._annotation = updateTreeConnection(null, annotation)
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AnnAssign).target },
                { n -> (n as AnnAssign).annotation },
                { n -> (n as AnnAssign).value },
            )
        }
    }

    class For(
        pos: Position,
        target: Expr,
        iter: Expr,
        body: Iterable<Stmt>,
        orElse: Iterable<Stmt> = listOf(),
        var async: Boolean = false,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (async && bodyNeedsPass && orElse.isNotEmpty() && elseNeedsPass) {
                    sharedCodeFormattingTemplate19
                } else if (async && bodyNeedsPass && orElse.isNotEmpty()) {
                    sharedCodeFormattingTemplate20
                } else if (async && bodyNeedsPass) {
                    sharedCodeFormattingTemplate21
                } else if (async && orElse.isNotEmpty() && elseNeedsPass) {
                    sharedCodeFormattingTemplate22
                } else if (async && orElse.isNotEmpty()) {
                    sharedCodeFormattingTemplate23
                } else if (async) {
                    sharedCodeFormattingTemplate24
                } else if (bodyNeedsPass && orElse.isNotEmpty() && elseNeedsPass) {
                    sharedCodeFormattingTemplate25
                } else if (bodyNeedsPass && orElse.isNotEmpty()) {
                    sharedCodeFormattingTemplate26
                } else if (bodyNeedsPass) {
                    sharedCodeFormattingTemplate27
                } else if (orElse.isNotEmpty() && elseNeedsPass) {
                    sharedCodeFormattingTemplate28
                } else if (orElse.isNotEmpty()) {
                    sharedCodeFormattingTemplate29
                } else {
                    sharedCodeFormattingTemplate30
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.target
                1 -> this.iter
                2 -> FormattableTreeGroup(this.body)
                3 -> FormattableTreeGroup(this.orElse)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _target: Expr
        var target: Expr
            get() = _target
            set(newValue) { _target = updateTreeConnection(_target, newValue) }
        private var _iter: Expr
        var iter: Expr
            get() = _iter
            set(newValue) { _iter = updateTreeConnection(_iter, newValue) }
        private val _body: MutableList<Stmt> = mutableListOf()
        var body: List<Stmt>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        private val _orElse: MutableList<Stmt> = mutableListOf()
        var orElse: List<Stmt>
            get() = _orElse
            set(newValue) { updateTreeConnections(_orElse, newValue) }
        val bodyNeedsPass: Boolean
            get() = body.needsPass()
        val elseNeedsPass: Boolean
            get() = orElse.needsPass()
        override fun deepCopy(): For {
            return For(pos, target = this.target.deepCopy(), iter = this.iter.deepCopy(), body = this.body.deepCopy(), orElse = this.orElse.deepCopy(), async = this.async)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is For && this.target == other.target && this.iter == other.iter && this.body == other.body && this.orElse == other.orElse && this.async == other.async
        }
        override fun hashCode(): Int {
            var hc = target.hashCode()
            hc = 31 * hc + iter.hashCode()
            hc = 31 * hc + body.hashCode()
            hc = 31 * hc + orElse.hashCode()
            hc = 31 * hc + async.hashCode()
            return hc
        }
        init {
            this._target = updateTreeConnection(null, target)
            this._iter = updateTreeConnection(null, iter)
            updateTreeConnections(this._body, body)
            updateTreeConnections(this._orElse, orElse)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as For).target },
                { n -> (n as For).iter },
                { n -> (n as For).body },
                { n -> (n as For).orElse },
            )
        }
    }

    class While(
        pos: Position,
        test: Expr,
        body: Iterable<Stmt>,
        orElse: Iterable<Stmt> = listOf(),
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (bodyNeedsPass && orElse.isNotEmpty() && elseNeedsPass) {
                    sharedCodeFormattingTemplate31
                } else if (bodyNeedsPass && orElse.isNotEmpty()) {
                    sharedCodeFormattingTemplate32
                } else if (bodyNeedsPass) {
                    sharedCodeFormattingTemplate33
                } else if (orElse.isNotEmpty() && elseNeedsPass) {
                    sharedCodeFormattingTemplate34
                } else if (orElse.isNotEmpty()) {
                    sharedCodeFormattingTemplate35
                } else {
                    sharedCodeFormattingTemplate36
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.test
                1 -> FormattableTreeGroup(this.body)
                2 -> FormattableTreeGroup(this.orElse)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _test: Expr
        var test: Expr
            get() = _test
            set(newValue) { _test = updateTreeConnection(_test, newValue) }
        private val _body: MutableList<Stmt> = mutableListOf()
        var body: List<Stmt>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        private val _orElse: MutableList<Stmt> = mutableListOf()
        var orElse: List<Stmt>
            get() = _orElse
            set(newValue) { updateTreeConnections(_orElse, newValue) }
        val bodyNeedsPass: Boolean
            get() = body.needsPass()
        val elseNeedsPass: Boolean
            get() = orElse.needsPass()
        override fun deepCopy(): While {
            return While(pos, test = this.test.deepCopy(), body = this.body.deepCopy(), orElse = this.orElse.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is While && this.test == other.test && this.body == other.body && this.orElse == other.orElse
        }
        override fun hashCode(): Int {
            var hc = test.hashCode()
            hc = 31 * hc + body.hashCode()
            hc = 31 * hc + orElse.hashCode()
            return hc
        }
        init {
            this._test = updateTreeConnection(null, test)
            updateTreeConnections(this._body, body)
            updateTreeConnections(this._orElse, orElse)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as While).test },
                { n -> (n as While).body },
                { n -> (n as While).orElse },
            )
        }
    }

    class If(
        pos: Position,
        test: Expr,
        body: Iterable<Stmt>,
        elifs: Iterable<Elif> = listOf(),
        orElse: Iterable<Stmt> = listOf(),
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (bodyNeedsPass && orElse.isNotEmpty() && elseNeedsPass) {
                    sharedCodeFormattingTemplate37
                } else if (bodyNeedsPass && orElse.isNotEmpty()) {
                    sharedCodeFormattingTemplate38
                } else if (bodyNeedsPass) {
                    sharedCodeFormattingTemplate39
                } else if (orElse.isNotEmpty() && elseNeedsPass) {
                    sharedCodeFormattingTemplate40
                } else if (orElse.isNotEmpty()) {
                    sharedCodeFormattingTemplate41
                } else {
                    sharedCodeFormattingTemplate42
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.test
                1 -> FormattableTreeGroup(this.body)
                2 -> FormattableTreeGroup(this.elifs)
                3 -> FormattableTreeGroup(this.orElse)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _test: Expr
        var test: Expr
            get() = _test
            set(newValue) { _test = updateTreeConnection(_test, newValue) }
        private val _body: MutableList<Stmt> = mutableListOf()
        var body: List<Stmt>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        private val _elifs: MutableList<Elif> = mutableListOf()
        var elifs: List<Elif>
            get() = _elifs
            set(newValue) { updateTreeConnections(_elifs, newValue) }
        private val _orElse: MutableList<Stmt> = mutableListOf()
        var orElse: List<Stmt>
            get() = _orElse
            set(newValue) { updateTreeConnections(_orElse, newValue) }
        val bodyNeedsPass: Boolean
            get() = body.needsPass()
        val elseNeedsPass: Boolean
            get() = orElse.needsPass()
        override fun deepCopy(): If {
            return If(pos, test = this.test.deepCopy(), body = this.body.deepCopy(), elifs = this.elifs.deepCopy(), orElse = this.orElse.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is If && this.test == other.test && this.body == other.body && this.elifs == other.elifs && this.orElse == other.orElse
        }
        override fun hashCode(): Int {
            var hc = test.hashCode()
            hc = 31 * hc + body.hashCode()
            hc = 31 * hc + elifs.hashCode()
            hc = 31 * hc + orElse.hashCode()
            return hc
        }
        init {
            this._test = updateTreeConnection(null, test)
            updateTreeConnections(this._body, body)
            updateTreeConnections(this._elifs, elifs)
            updateTreeConnections(this._orElse, orElse)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as If).test },
                { n -> (n as If).body },
                { n -> (n as If).elifs },
                { n -> (n as If).orElse },
            )
        }
    }

    class With(
        pos: Position,
        items: Iterable<WithItem>,
        body: Iterable<Stmt>,
        var async: Boolean = false,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (async && bodyNeedsPass) {
                    sharedCodeFormattingTemplate43
                } else if (async) {
                    sharedCodeFormattingTemplate44
                } else if (bodyNeedsPass) {
                    sharedCodeFormattingTemplate45
                } else {
                    sharedCodeFormattingTemplate46
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.items)
                1 -> FormattableTreeGroup(this.body)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _items: MutableList<WithItem> = mutableListOf()
        var items: List<WithItem>
            get() = _items
            set(newValue) { updateTreeConnections(_items, newValue) }
        private val _body: MutableList<Stmt> = mutableListOf()
        var body: List<Stmt>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        val bodyNeedsPass: Boolean
            get() = body.needsPass()
        override fun deepCopy(): With {
            return With(pos, items = this.items.deepCopy(), body = this.body.deepCopy(), async = this.async)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is With && this.items == other.items && this.body == other.body && this.async == other.async
        }
        override fun hashCode(): Int {
            var hc = items.hashCode()
            hc = 31 * hc + body.hashCode()
            hc = 31 * hc + async.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._items, items)
            updateTreeConnections(this._body, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as With).items },
                { n -> (n as With).body },
            )
        }
    }

    class Raise(
        pos: Position,
        exc: Expr? = null,
        cause: Expr? = null,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (exc != null && cause != null) {
                    sharedCodeFormattingTemplate47
                } else if (exc != null) {
                    sharedCodeFormattingTemplate48
                } else {
                    sharedCodeFormattingTemplate49
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.exc ?: FormattableTreeGroup.empty
                1 -> this.cause ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _exc: Expr?
        var exc: Expr?
            get() = _exc
            set(newValue) { _exc = updateTreeConnection(_exc, newValue) }
        private var _cause: Expr?
        var cause: Expr?
            get() = _cause
            set(newValue) { _cause = updateTreeConnection(_cause, newValue) }
        override fun deepCopy(): Raise {
            return Raise(pos, exc = this.exc?.deepCopy(), cause = this.cause?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Raise && this.exc == other.exc && this.cause == other.cause
        }
        override fun hashCode(): Int {
            var hc = (exc?.hashCode() ?: 0)
            hc = 31 * hc + (cause?.hashCode() ?: 0)
            return hc
        }
        init {
            this._exc = updateTreeConnection(null, exc)
            this._cause = updateTreeConnection(null, cause)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Raise).exc },
                { n -> (n as Raise).cause },
            )
        }
    }

    /**
     * Try(stmt* body, excepthandler* handlers, stmt* orelse, stmt* finalbody)
     * try_stmt: ('try' ':' suite
     *            ((except_clause ':' suite)+
     *             ['else' ':' suite]
     *             ['finally' ':' suite] |
     *            'finally' ':' suite))
     */
    class Try(
        pos: Position,
        body: Iterable<Stmt>,
        handlers: Iterable<ExceptHandler> = listOf(),
        orElse: Iterable<Stmt> = listOf(),
        finalbody: Iterable<Stmt> = listOf(),
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (bodyNeedsPass && orElse.isNotEmpty() && elseNeedsPass && finalbody.isNotEmpty() && finallyNeedsPass) {
                    sharedCodeFormattingTemplate50
                } else if (bodyNeedsPass && orElse.isNotEmpty() && elseNeedsPass && finalbody.isNotEmpty()) {
                    sharedCodeFormattingTemplate51
                } else if (bodyNeedsPass && orElse.isNotEmpty() && elseNeedsPass) {
                    sharedCodeFormattingTemplate52
                } else if (bodyNeedsPass && orElse.isNotEmpty() && finalbody.isNotEmpty() && finallyNeedsPass) {
                    sharedCodeFormattingTemplate53
                } else if (bodyNeedsPass && orElse.isNotEmpty() && finalbody.isNotEmpty()) {
                    sharedCodeFormattingTemplate54
                } else if (bodyNeedsPass && orElse.isNotEmpty()) {
                    sharedCodeFormattingTemplate55
                } else if (bodyNeedsPass && finalbody.isNotEmpty() && finallyNeedsPass) {
                    sharedCodeFormattingTemplate56
                } else if (bodyNeedsPass && finalbody.isNotEmpty()) {
                    sharedCodeFormattingTemplate57
                } else if (bodyNeedsPass) {
                    sharedCodeFormattingTemplate58
                } else if (orElse.isNotEmpty() && elseNeedsPass && finalbody.isNotEmpty() && finallyNeedsPass) {
                    sharedCodeFormattingTemplate59
                } else if (orElse.isNotEmpty() && elseNeedsPass && finalbody.isNotEmpty()) {
                    sharedCodeFormattingTemplate60
                } else if (orElse.isNotEmpty() && elseNeedsPass) {
                    sharedCodeFormattingTemplate61
                } else if (orElse.isNotEmpty() && finalbody.isNotEmpty() && finallyNeedsPass) {
                    sharedCodeFormattingTemplate62
                } else if (orElse.isNotEmpty() && finalbody.isNotEmpty()) {
                    sharedCodeFormattingTemplate63
                } else if (orElse.isNotEmpty()) {
                    sharedCodeFormattingTemplate64
                } else if (finalbody.isNotEmpty() && finallyNeedsPass) {
                    sharedCodeFormattingTemplate65
                } else if (finalbody.isNotEmpty()) {
                    sharedCodeFormattingTemplate66
                } else {
                    sharedCodeFormattingTemplate67
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.body)
                1 -> FormattableTreeGroup(this.handlers)
                2 -> FormattableTreeGroup(this.orElse)
                3 -> FormattableTreeGroup(this.finalbody)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _body: MutableList<Stmt> = mutableListOf()
        var body: List<Stmt>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        private val _handlers: MutableList<ExceptHandler> = mutableListOf()
        var handlers: List<ExceptHandler>
            get() = _handlers
            set(newValue) { updateTreeConnections(_handlers, newValue) }
        private val _orElse: MutableList<Stmt> = mutableListOf()
        var orElse: List<Stmt>
            get() = _orElse
            set(newValue) { updateTreeConnections(_orElse, newValue) }
        private val _finalbody: MutableList<Stmt> = mutableListOf()
        var finalbody: List<Stmt>
            get() = _finalbody
            set(newValue) { updateTreeConnections(_finalbody, newValue) }
        val bodyNeedsPass: Boolean
            get() = body.needsPass()
        val elseNeedsPass: Boolean
            get() = orElse.needsPass()
        val finallyNeedsPass: Boolean
            get() = finalbody.needsPass()
        override fun deepCopy(): Try {
            return Try(pos, body = this.body.deepCopy(), handlers = this.handlers.deepCopy(), orElse = this.orElse.deepCopy(), finalbody = this.finalbody.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Try && this.body == other.body && this.handlers == other.handlers && this.orElse == other.orElse && this.finalbody == other.finalbody
        }
        override fun hashCode(): Int {
            var hc = body.hashCode()
            hc = 31 * hc + handlers.hashCode()
            hc = 31 * hc + orElse.hashCode()
            hc = 31 * hc + finalbody.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._body, body)
            updateTreeConnections(this._handlers, handlers)
            updateTreeConnections(this._orElse, orElse)
            updateTreeConnections(this._finalbody, finalbody)
            require(handlers.any() || finalbody.any())
            require(orElse.none() || (orElse.any() && handlers.any()))
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Try).body },
                { n -> (n as Try).handlers },
                { n -> (n as Try).orElse },
                { n -> (n as Try).finalbody },
            )
        }
    }

    class Assert(
        pos: Position,
        test: Expr?,
        msg: Expr? = null,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (test != null && msg != null) {
                    sharedCodeFormattingTemplate68
                } else if (test != null) {
                    sharedCodeFormattingTemplate69
                } else {
                    sharedCodeFormattingTemplate70
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.test ?: FormattableTreeGroup.empty
                1 -> this.msg ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _test: Expr?
        var test: Expr?
            get() = _test
            set(newValue) { _test = updateTreeConnection(_test, newValue) }
        private var _msg: Expr?
        var msg: Expr?
            get() = _msg
            set(newValue) { _msg = updateTreeConnection(_msg, newValue) }
        override fun deepCopy(): Assert {
            return Assert(pos, test = this.test?.deepCopy(), msg = this.msg?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Assert && this.test == other.test && this.msg == other.msg
        }
        override fun hashCode(): Int {
            var hc = (test?.hashCode() ?: 0)
            hc = 31 * hc + (msg?.hashCode() ?: 0)
            return hc
        }
        init {
            this._test = updateTreeConnection(null, test)
            this._msg = updateTreeConnection(null, msg)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Assert).test },
                { n -> (n as Assert).msg },
            )
        }
    }

    sealed interface ImportStmt : Tree, Stmt {
        override fun deepCopy(): ImportStmt
    }

    class Global(
        pos: Position,
        names: Iterable<Identifier>,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate71
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.names)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _names: MutableList<Identifier> = mutableListOf()
        var names: List<Identifier>
            get() = _names
            set(newValue) { updateTreeConnections(_names, newValue) }
        override fun deepCopy(): Global {
            return Global(pos, names = this.names.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Global && this.names == other.names
        }
        override fun hashCode(): Int {
            return names.hashCode()
        }
        init {
            updateTreeConnections(this._names, names)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Global).names },
            )
        }
    }

    class Nonlocal(
        pos: Position,
        names: Iterable<Identifier>,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate72
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.names)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _names: MutableList<Identifier> = mutableListOf()
        var names: List<Identifier>
            get() = _names
            set(newValue) { updateTreeConnections(_names, newValue) }
        override fun deepCopy(): Nonlocal {
            return Nonlocal(pos, names = this.names.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Nonlocal && this.names == other.names
        }
        override fun hashCode(): Int {
            return names.hashCode()
        }
        init {
            updateTreeConnections(this._names, names)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Nonlocal).names },
            )
        }
    }

    class ExprStmt(
        pos: Position,
        value: Expr,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate73
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
        override fun deepCopy(): ExprStmt {
            return ExprStmt(pos, value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ExprStmt && this.value == other.value
        }
        override fun hashCode(): Int {
            return value.hashCode()
        }
        init {
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ExprStmt).value },
            )
        }
        constructor(expr: Expr) : this(expr.pos, expr)
    }

    class Pass(
        pos: Position,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate74
        override val formatElementCount
            get() = 0
        override fun deepCopy(): Pass {
            return Pass(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Pass
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class Break(
        pos: Position,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate75
        override val formatElementCount
            get() = 0
        override fun deepCopy(): Break {
            return Break(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Break
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class Continue(
        pos: Position,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate76
        override val formatElementCount
            get() = 0
        override fun deepCopy(): Continue {
            return Continue(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Continue
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * `#commentText`
     */
    class CommentLine(
        pos: Position,
        var commentText: String,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            val tokenText = if (commentText.isEmpty()) {
                "#"
            } else {
                "# $commentText"
            }
            tokenSink.emit(OutputToken(tokenText, OutputTokenType.Comment))
            tokenSink.endLine()
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): CommentLine {
            return CommentLine(pos, commentText = this.commentText)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is CommentLine && this.commentText == other.commentText
        }
        override fun hashCode(): Int {
            return commentText.hashCode()
        }
        init {
            // No newlines in line comments.
            require(!pyLineTerminator.matches(commentText)) { commentText }
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * decorator: `'@' dotted_name [ '(' [args] ')' ] NEWLINE`
     * PEP-614 / 3.9: '@' named_expr_test NEWLINE
     */
    class Decorator(
        pos: Position,
        name: Iterable<Identifier>,
        args: Iterable<CallArg>,
        var called: Boolean,
    ) : BaseTree(pos) {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (called) {
                    sharedCodeFormattingTemplate77
                } else {
                    sharedCodeFormattingTemplate78
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.name)
                1 -> FormattableTreeGroup(this.args)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _name: MutableList<Identifier> = mutableListOf()
        var name: List<Identifier>
            get() = _name
            set(newValue) { updateTreeConnections(_name, newValue) }
        private val _args: MutableList<CallArg> = mutableListOf()
        var args: List<CallArg>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): Decorator {
            return Decorator(pos, name = this.name.deepCopy(), args = this.args.deepCopy(), called = this.called)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Decorator && this.name == other.name && this.args == other.args && this.called == other.called
        }
        override fun hashCode(): Int {
            var hc = name.hashCode()
            hc = 31 * hc + args.hashCode()
            hc = 31 * hc + called.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._name, name)
            updateTreeConnections(this._args, args)
            require(callArgsValid(args), issue { callArgsIssues(args) })
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Decorator).name },
                { n -> (n as Decorator).args },
            )
        }
    }

    /** A Python identifier that does not participate in expressions. */
    class Identifier(
        pos: Position,
        var id: PyIdentifierName,
        var sourceIdentifier: TemperName? = null,
    ) : BaseTree(pos) {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.name(outName, inOperatorPosition = false)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        val outName: OutName
            get() = OutName(id.text, sourceIdentifier)
        override fun deepCopy(): Identifier {
            return Identifier(pos, id = this.id, sourceIdentifier = this.sourceIdentifier)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Identifier && this.id == other.id && this.sourceIdentifier == other.sourceIdentifier
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

    /**
     * A Call uses a list of expressions for arguments (including Starred) and a list of keyword arguments.
     * > keyword = (identifier? arg, expr value)
     */
    class CallArg(
        pos: Position,
        arg: Identifier? = null,
        value: Expr,
        var prefix: ArgPrefix = ArgPrefix.None,
    ) : BaseTree(pos) {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (prefix == ArgPrefix.DoubleStar && arg != null) {
                    sharedCodeFormattingTemplate79
                } else if (prefix == ArgPrefix.DoubleStar) {
                    sharedCodeFormattingTemplate80
                } else if (arg != null) {
                    sharedCodeFormattingTemplate81
                } else {
                    sharedCodeFormattingTemplate82
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.arg ?: FormattableTreeGroup.empty
                1 -> this.value
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _arg: Identifier?
        var arg: Identifier?
            get() = _arg
            set(newValue) { _arg = updateTreeConnection(_arg, newValue) }
        private var _value: Expr
        var value: Expr
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        val positional: Boolean
            get() = prefix == ArgPrefix.None
        override fun deepCopy(): CallArg {
            return CallArg(pos, arg = this.arg?.deepCopy(), value = this.value.deepCopy(), prefix = this.prefix)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is CallArg && this.arg == other.arg && this.value == other.value && this.prefix == other.prefix
        }
        override fun hashCode(): Int {
            var hc = (arg?.hashCode() ?: 0)
            hc = 31 * hc + value.hashCode()
            hc = 31 * hc + prefix.hashCode()
            return hc
        }
        init {
            this._arg = updateTreeConnection(null, arg)
            this._value = updateTreeConnection(null, value)
            require(prefix == ArgPrefix.None || arg == null)
            require(prefix != ArgPrefix.Star)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as CallArg).arg },
                { n -> (n as CallArg).value },
            )
        }
        constructor(value: Expr) : this(pos = value.pos, value = value)
    }

    /**
     * This whole structure is a mess to represent, so we just have a list of Arg objects that can have prefixes.
     * Correctness is enforced by the constructor.
     *
     * ```
     * parameter_list          ::=  defparameter ("," defparameter)* ["," [parameter_list_starargs]]
     *                              | parameter_list_starargs
     * parameter_list_starargs ::=  "*" [parameter] ("," defparameter)* ["," ["**" parameter [","]]]
     *                              | "**" parameter [","]
     * parameter               ::=  identifier [":" expression]
     * defparameter            ::=  parameter ["=" expression]
     * ```
     */
    class Arguments(
        pos: Position,
        args: Iterable<Arg>,
    ) : BaseTree(pos) {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate83
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
        private val _args: MutableList<Arg> = mutableListOf()
        var args: List<Arg>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        val hasAnnotations: Boolean
            get() = args.any { it.annotation != null }
        override fun deepCopy(): Arguments {
            return Arguments(pos, args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Arguments && this.args == other.args
        }
        override fun hashCode(): Int {
            return args.hashCode()
        }
        init {
            updateTreeConnections(this._args, args)
            require(argumentsValid(args), issue { argumentsIssues(args) })
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Arguments).args },
            )
        }
    }

    sealed interface SubscriptSlice : Tree {
        override fun deepCopy(): SubscriptSlice
    }

    sealed interface Expr : Tree, SubscriptSlice {
        override fun deepCopy(): Expr
    }

    /** An augmented assignment operator constructed from [AugAssignOpEnum.atom] */
    class AugAssignOp(
        pos: Position,
        var opEnum: AugAssignOpEnum,
    ) : BaseTree(pos) {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            opEnum.renderTo(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): AugAssignOp {
            return AugAssignOp(pos, opEnum = this.opEnum)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AugAssignOp && this.opEnum == other.opEnum
        }
        override fun hashCode(): Int {
            return opEnum.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /** Elif branches must be constructed manually. */
    class Elif(
        pos: Position,
        test: Expr,
        body: Iterable<Stmt>,
    ) : BaseTree(pos) {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (bodyNeedsPass) {
                    sharedCodeFormattingTemplate84
                } else {
                    sharedCodeFormattingTemplate85
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.test
                1 -> FormattableTreeGroup(this.body)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _test: Expr
        var test: Expr
            get() = _test
            set(newValue) { _test = updateTreeConnection(_test, newValue) }
        private val _body: MutableList<Stmt> = mutableListOf()
        var body: List<Stmt>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        val bodyNeedsPass: Boolean
            get() = body.needsPass()
        override fun deepCopy(): Elif {
            return Elif(pos, test = this.test.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Elif && this.test == other.test && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = test.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._test = updateTreeConnection(null, test)
            updateTreeConnections(this._body, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Elif).test },
                { n -> (n as Elif).body },
            )
        }
    }

    class WithItem(
        pos: Position,
        contextExpr: Expr,
        optionalVars: Expr? = null,
    ) : BaseTree(pos) {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (optionalVars != null) {
                    sharedCodeFormattingTemplate86
                } else {
                    sharedCodeFormattingTemplate87
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.contextExpr
                1 -> this.optionalVars ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _contextExpr: Expr
        var contextExpr: Expr
            get() = _contextExpr
            set(newValue) { _contextExpr = updateTreeConnection(_contextExpr, newValue) }
        private var _optionalVars: Expr?
        var optionalVars: Expr?
            get() = _optionalVars
            set(newValue) { _optionalVars = updateTreeConnection(_optionalVars, newValue) }
        override fun deepCopy(): WithItem {
            return WithItem(pos, contextExpr = this.contextExpr.deepCopy(), optionalVars = this.optionalVars?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is WithItem && this.contextExpr == other.contextExpr && this.optionalVars == other.optionalVars
        }
        override fun hashCode(): Int {
            var hc = contextExpr.hashCode()
            hc = 31 * hc + (optionalVars?.hashCode() ?: 0)
            return hc
        }
        init {
            this._contextExpr = updateTreeConnection(null, contextExpr)
            this._optionalVars = updateTreeConnection(null, optionalVars)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as WithItem).contextExpr },
                { n -> (n as WithItem).optionalVars },
            )
        }
    }

    class ExceptHandler(
        pos: Position,
        type: Expr? = null,
        name: Identifier? = null,
        body: Iterable<Stmt>,
    ) : BaseTree(pos) {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (type != null && name != null && bodyNeedsPass) {
                    sharedCodeFormattingTemplate88
                } else if (type != null && name != null) {
                    sharedCodeFormattingTemplate89
                } else if (type != null && bodyNeedsPass) {
                    sharedCodeFormattingTemplate90
                } else if (type != null) {
                    sharedCodeFormattingTemplate91
                } else if (bodyNeedsPass) {
                    sharedCodeFormattingTemplate92
                } else {
                    sharedCodeFormattingTemplate93
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type ?: FormattableTreeGroup.empty
                1 -> this.name ?: FormattableTreeGroup.empty
                2 -> FormattableTreeGroup(this.body)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: Expr?
        var type: Expr?
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _name: Identifier?
        var name: Identifier?
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private val _body: MutableList<Stmt> = mutableListOf()
        var body: List<Stmt>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        val bodyNeedsPass: Boolean
            get() = body.needsPass()
        override fun deepCopy(): ExceptHandler {
            return ExceptHandler(pos, type = this.type?.deepCopy(), name = this.name?.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ExceptHandler && this.type == other.type && this.name == other.name && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = (type?.hashCode() ?: 0)
            hc = 31 * hc + (name?.hashCode() ?: 0)
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            this._name = updateTreeConnection(null, name)
            updateTreeConnections(this._body, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ExceptHandler).type },
                { n -> (n as ExceptHandler).name },
                { n -> (n as ExceptHandler).body },
            )
        }
    }

    class Import(
        pos: Position,
        names: Iterable<ImportAlias>,
    ) : BaseTree(pos), ImportStmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate94
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.names)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _names: MutableList<ImportAlias> = mutableListOf()
        var names: List<ImportAlias>
            get() = _names
            set(newValue) { updateTreeConnections(_names, newValue) }
        override fun deepCopy(): Import {
            return Import(pos, names = this.names.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Import && this.names == other.names
        }
        override fun hashCode(): Int {
            return names.hashCode()
        }
        init {
            updateTreeConnections(this._names, names)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Import).names },
            )
        }
    }

    class ImportFrom(
        pos: Position,
        module: ImportDotted,
        names: Iterable<ImportAlias>,
    ) : BaseTree(pos), ImportStmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate95
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.module
                1 -> FormattableTreeGroup(this.names)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _module: ImportDotted
        var module: ImportDotted
            get() = _module
            set(newValue) { _module = updateTreeConnection(_module, newValue) }
        private val _names: MutableList<ImportAlias> = mutableListOf()
        var names: List<ImportAlias>
            get() = _names
            set(newValue) { updateTreeConnections(_names, newValue) }
        override fun deepCopy(): ImportFrom {
            return ImportFrom(pos, module = this.module.deepCopy(), names = this.names.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImportFrom && this.module == other.module && this.names == other.names
        }
        override fun hashCode(): Int {
            var hc = module.hashCode()
            hc = 31 * hc + names.hashCode()
            return hc
        }
        init {
            this._module = updateTreeConnection(null, module)
            updateTreeConnections(this._names, names)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ImportFrom).module },
                { n -> (n as ImportFrom).names },
            )
        }
    }

    class ImportWildcardFrom(
        pos: Position,
        module: ImportDotted,
    ) : BaseTree(pos), ImportStmt {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate96
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.module
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _module: ImportDotted
        var module: ImportDotted
            get() = _module
            set(newValue) { _module = updateTreeConnection(_module, newValue) }
        override fun deepCopy(): ImportWildcardFrom {
            return ImportWildcardFrom(pos, module = this.module.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImportWildcardFrom && this.module == other.module
        }
        override fun hashCode(): Int {
            return module.hashCode()
        }
        init {
            this._module = updateTreeConnection(null, module)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ImportWildcardFrom).module },
            )
        }
    }

    class ImportAlias(
        pos: Position,
        name: ImportDotted,
        asname: Identifier?,
    ) : BaseTree(pos) {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (asname != null) {
                    sharedCodeFormattingTemplate86
                } else {
                    sharedCodeFormattingTemplate87
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name
                1 -> this.asname ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _name: ImportDotted
        var name: ImportDotted
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _asname: Identifier?
        var asname: Identifier?
            get() = _asname
            set(newValue) { _asname = updateTreeConnection(_asname, newValue) }
        override fun deepCopy(): ImportAlias {
            return ImportAlias(pos, name = this.name.deepCopy(), asname = this.asname?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImportAlias && this.name == other.name && this.asname == other.asname
        }
        override fun hashCode(): Int {
            var hc = name.hashCode()
            hc = 31 * hc + (asname?.hashCode() ?: 0)
            return hc
        }
        init {
            this._name = updateTreeConnection(null, name)
            this._asname = updateTreeConnection(null, asname)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ImportAlias).name },
                { n -> (n as ImportAlias).asname },
            )
        }
    }

    class ImportDotted(
        pos: Position,
        var module: PyDottedIdentifier,
    ) : BaseTree(pos) {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            module.renderTo(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): ImportDotted {
            return ImportDotted(pos, module = this.module)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImportDotted && this.module == other.module
        }
        override fun hashCode(): Int {
            return module.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class BinExpr(
        pos: Position,
        left: Expr,
        op: BinaryOp,
        right: Expr,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = op.expressionOperatorDefinition
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate97
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
        override fun deepCopy(): BinExpr {
            return BinExpr(pos, left = this.left.deepCopy(), op = this.op.deepCopy(), right = this.right.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BinExpr && this.left == other.left && this.op == other.op && this.right == other.right
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
                { n -> (n as BinExpr).left },
                { n -> (n as BinExpr).op },
                { n -> (n as BinExpr).right },
            )
        }
    }

    class UnaryExpr(
        pos: Position,
        op: UnaryOp,
        operand: Expr,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = op.expressionOperatorDefinition
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate98
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.op
                1 -> this.operand
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _op: UnaryOp
        var op: UnaryOp
            get() = _op
            set(newValue) { _op = updateTreeConnection(_op, newValue) }
        private var _operand: Expr
        var operand: Expr
            get() = _operand
            set(newValue) { _operand = updateTreeConnection(_operand, newValue) }
        override fun deepCopy(): UnaryExpr {
            return UnaryExpr(pos, op = this.op.deepCopy(), operand = this.operand.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is UnaryExpr && this.op == other.op && this.operand == other.operand
        }
        override fun hashCode(): Int {
            var hc = op.hashCode()
            hc = 31 * hc + operand.hashCode()
            return hc
        }
        init {
            this._op = updateTreeConnection(null, op)
            this._operand = updateTreeConnection(null, operand)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as UnaryExpr).op },
                { n -> (n as UnaryExpr).operand },
            )
        }
    }

    class Lambda(
        pos: Position,
        args: Arguments?,
        body: Expr,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = PyOperatorDefinition.Lambda
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (args != null) {
                    sharedCodeFormattingTemplate99
                } else {
                    sharedCodeFormattingTemplate100
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.args ?: FormattableTreeGroup.empty
                1 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _args: Arguments?
        var args: Arguments?
            get() = _args
            set(newValue) { _args = updateTreeConnection(_args, newValue) }
        private var _body: Expr
        var body: Expr
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): Lambda {
            return Lambda(pos, args = this.args?.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Lambda && this.args == other.args && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = (args?.hashCode() ?: 0)
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._args = updateTreeConnection(null, args)
            this._body = updateTreeConnection(null, body)
            require(args == null || !args.hasAnnotations)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Lambda).args },
                { n -> (n as Lambda).body },
            )
        }
    }

    class IfExpr(
        pos: Position,
        body: Expr,
        test: Expr,
        orElse: Expr,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = PyOperatorDefinition.Test
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate101
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.body
                1 -> this.test
                2 -> this.orElse
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _body: Expr
        var body: Expr
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        private var _test: Expr
        var test: Expr
            get() = _test
            set(newValue) { _test = updateTreeConnection(_test, newValue) }
        private var _orElse: Expr
        var orElse: Expr
            get() = _orElse
            set(newValue) { _orElse = updateTreeConnection(_orElse, newValue) }
        override fun deepCopy(): IfExpr {
            return IfExpr(pos, body = this.body.deepCopy(), test = this.test.deepCopy(), orElse = this.orElse.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is IfExpr && this.body == other.body && this.test == other.test && this.orElse == other.orElse
        }
        override fun hashCode(): Int {
            var hc = body.hashCode()
            hc = 31 * hc + test.hashCode()
            hc = 31 * hc + orElse.hashCode()
            return hc
        }
        init {
            this._body = updateTreeConnection(null, body)
            this._test = updateTreeConnection(null, test)
            this._orElse = updateTreeConnection(null, orElse)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as IfExpr).body },
                { n -> (n as IfExpr).test },
                { n -> (n as IfExpr).orElse },
            )
        }
    }

    class Dict(
        pos: Position,
        items: Iterable<DictPair>,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate102
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
        private val _items: MutableList<DictPair> = mutableListOf()
        var items: List<DictPair>
            get() = _items
            set(newValue) { updateTreeConnections(_items, newValue) }
        override fun deepCopy(): Dict {
            return Dict(pos, items = this.items.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Dict && this.items == other.items
        }
        override fun hashCode(): Int {
            return items.hashCode()
        }
        init {
            updateTreeConnections(this._items, items)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Dict).items },
            )
        }
    }

    class SetExpr(
        pos: Position,
        elts: Iterable<Expr>,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (elts.isNotEmpty()) {
                    sharedCodeFormattingTemplate102
                } else {
                    sharedCodeFormattingTemplate103
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.elts)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _elts: MutableList<Expr> = mutableListOf()
        var elts: List<Expr>
            get() = _elts
            set(newValue) { updateTreeConnections(_elts, newValue) }
        override fun deepCopy(): SetExpr {
            return SetExpr(pos, elts = this.elts.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SetExpr && this.elts == other.elts
        }
        override fun hashCode(): Int {
            return elts.hashCode()
        }
        init {
            updateTreeConnections(this._elts, elts)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SetExpr).elts },
            )
        }
    }

    class ListComp(
        pos: Position,
        elt: Expr,
        generators: Iterable<Comprehension>,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate104
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.elt
                1 -> FormattableTreeGroup(this.generators)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _elt: Expr
        var elt: Expr
            get() = _elt
            set(newValue) { _elt = updateTreeConnection(_elt, newValue) }
        private val _generators: MutableList<Comprehension> = mutableListOf()
        var generators: List<Comprehension>
            get() = _generators
            set(newValue) { updateTreeConnections(_generators, newValue) }
        override fun deepCopy(): ListComp {
            return ListComp(pos, elt = this.elt.deepCopy(), generators = this.generators.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ListComp && this.elt == other.elt && this.generators == other.generators
        }
        override fun hashCode(): Int {
            var hc = elt.hashCode()
            hc = 31 * hc + generators.hashCode()
            return hc
        }
        init {
            this._elt = updateTreeConnection(null, elt)
            updateTreeConnections(this._generators, generators)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ListComp).elt },
                { n -> (n as ListComp).generators },
            )
        }
    }

    class SetComp(
        pos: Position,
        elt: Expr,
        generators: Iterable<Comprehension>,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate105
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.elt
                1 -> FormattableTreeGroup(this.generators)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _elt: Expr
        var elt: Expr
            get() = _elt
            set(newValue) { _elt = updateTreeConnection(_elt, newValue) }
        private val _generators: MutableList<Comprehension> = mutableListOf()
        var generators: List<Comprehension>
            get() = _generators
            set(newValue) { updateTreeConnections(_generators, newValue) }
        override fun deepCopy(): SetComp {
            return SetComp(pos, elt = this.elt.deepCopy(), generators = this.generators.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SetComp && this.elt == other.elt && this.generators == other.generators
        }
        override fun hashCode(): Int {
            var hc = elt.hashCode()
            hc = 31 * hc + generators.hashCode()
            return hc
        }
        init {
            this._elt = updateTreeConnection(null, elt)
            updateTreeConnections(this._generators, generators)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SetComp).elt },
                { n -> (n as SetComp).generators },
            )
        }
    }

    class DictComp(
        pos: Position,
        key: Expr,
        value: Expr,
        generators: Iterable<Comprehension>,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate106
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.key
                1 -> this.value
                2 -> FormattableTreeGroup(this.generators)
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
        private val _generators: MutableList<Comprehension> = mutableListOf()
        var generators: List<Comprehension>
            get() = _generators
            set(newValue) { updateTreeConnections(_generators, newValue) }
        override fun deepCopy(): DictComp {
            return DictComp(pos, key = this.key.deepCopy(), value = this.value.deepCopy(), generators = this.generators.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is DictComp && this.key == other.key && this.value == other.value && this.generators == other.generators
        }
        override fun hashCode(): Int {
            var hc = key.hashCode()
            hc = 31 * hc + value.hashCode()
            hc = 31 * hc + generators.hashCode()
            return hc
        }
        init {
            this._key = updateTreeConnection(null, key)
            this._value = updateTreeConnection(null, value)
            updateTreeConnections(this._generators, generators)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as DictComp).key },
                { n -> (n as DictComp).value },
                { n -> (n as DictComp).generators },
            )
        }
    }

    class GeneratorComp(
        pos: Position,
        elt: Expr,
        generators: Iterable<Comprehension>,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate107
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.elt
                1 -> FormattableTreeGroup(this.generators)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _elt: Expr
        var elt: Expr
            get() = _elt
            set(newValue) { _elt = updateTreeConnection(_elt, newValue) }
        private val _generators: MutableList<Comprehension> = mutableListOf()
        var generators: List<Comprehension>
            get() = _generators
            set(newValue) { updateTreeConnections(_generators, newValue) }
        override fun deepCopy(): GeneratorComp {
            return GeneratorComp(pos, elt = this.elt.deepCopy(), generators = this.generators.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is GeneratorComp && this.elt == other.elt && this.generators == other.generators
        }
        override fun hashCode(): Int {
            var hc = elt.hashCode()
            hc = 31 * hc + generators.hashCode()
            return hc
        }
        init {
            this._elt = updateTreeConnection(null, elt)
            updateTreeConnections(this._generators, generators)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as GeneratorComp).elt },
                { n -> (n as GeneratorComp).generators },
            )
        }
    }

    class Await(
        pos: Position,
        value: Expr,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = PyOperatorDefinition.Await
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate108
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
        override fun deepCopy(): Await {
            return Await(pos, value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Await && this.value == other.value
        }
        override fun hashCode(): Int {
            return value.hashCode()
        }
        init {
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Await).value },
            )
        }
    }

    class Yield(
        pos: Position,
        value: Expr? = null,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = PyOperatorDefinition.Yield
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate109
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
        override fun deepCopy(): Yield {
            return Yield(pos, value = this.value?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Yield && this.value == other.value
        }
        override fun hashCode(): Int {
            return (value?.hashCode() ?: 0)
        }
        init {
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Yield).value },
            )
        }
    }

    class YieldFrom(
        pos: Position,
        value: Expr?,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = PyOperatorDefinition.YieldFrom
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate110
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
        override fun deepCopy(): YieldFrom {
            return YieldFrom(pos, value = this.value?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is YieldFrom && this.value == other.value
        }
        override fun hashCode(): Int {
            return (value?.hashCode() ?: 0)
        }
        init {
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as YieldFrom).value },
            )
        }
    }

    class Call(
        pos: Position,
        func: Expr,
        args: Iterable<CallArg>,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = PyOperatorDefinition.Call
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate111
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.func
                1 -> FormattableTreeGroup(this.args)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _func: Expr
        var func: Expr
            get() = _func
            set(newValue) { _func = updateTreeConnection(_func, newValue) }
        private val _args: MutableList<CallArg> = mutableListOf()
        var args: List<CallArg>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): Call {
            return Call(pos, func = this.func.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Call && this.func == other.func && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = func.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._func = updateTreeConnection(null, func)
            updateTreeConnections(this._args, args)
            require(callArgsValid(args), issue { callArgsIssues(args) })
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Call).func },
                { n -> (n as Call).args },
            )
        }
    }

    class Num(
        pos: Position,
        var n: Number,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = PyOperatorDefinition.Atom
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
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = PyOperatorDefinition.Atom
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

    class TypeStr(
        pos: Position,
        x: Expr,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = PyOperatorDefinition.Atom
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.quoted(stringTokenText(x.toString().trimEnd()))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        private var _x: Expr
        var x: Expr
            get() = _x
            set(newValue) { _x = updateTreeConnection(_x, newValue) }
        override fun deepCopy(): TypeStr {
            return TypeStr(pos, x = this.x.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeStr && this.x == other.x
        }
        override fun hashCode(): Int {
            return x.hashCode()
        }
        init {
            this._x = updateTreeConnection(null, x)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeStr).x },
            )
        }
    }

    class Constant(
        pos: Position,
        var value: PyConstant,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = PyOperatorDefinition.Atom
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.word(value.text)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): Constant {
            return Constant(pos, value = this.value)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Constant && this.value == other.value
        }
        override fun hashCode(): Int {
            return value.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class Attribute(
        pos: Position,
        value: Expr,
        attr: Identifier,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = PyOperatorDefinition.Attribute
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate112
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.value
                1 -> this.attr
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _value: Expr
        var value: Expr
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        private var _attr: Identifier
        var attr: Identifier
            get() = _attr
            set(newValue) { _attr = updateTreeConnection(_attr, newValue) }
        override fun deepCopy(): Attribute {
            return Attribute(pos, value = this.value.deepCopy(), attr = this.attr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Attribute && this.value == other.value && this.attr == other.attr
        }
        override fun hashCode(): Int {
            var hc = value.hashCode()
            hc = 31 * hc + attr.hashCode()
            return hc
        }
        init {
            this._value = updateTreeConnection(null, value)
            this._attr = updateTreeConnection(null, attr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Attribute).value },
                { n -> (n as Attribute).attr },
            )
        }
    }

    class Subscript(
        pos: Position,
        value: Expr,
        slice: Iterable<SubscriptSlice>,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = PyOperatorDefinition.Subscript
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate113
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.value
                1 -> FormattableTreeGroup(this.slice)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _value: Expr
        var value: Expr
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        private val _slice: MutableList<SubscriptSlice> = mutableListOf()
        var slice: List<SubscriptSlice>
            get() = _slice
            set(newValue) { updateTreeConnections(_slice, newValue) }
        override fun deepCopy(): Subscript {
            return Subscript(pos, value = this.value.deepCopy(), slice = this.slice.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Subscript && this.value == other.value && this.slice == other.slice
        }
        override fun hashCode(): Int {
            var hc = value.hashCode()
            hc = 31 * hc + slice.hashCode()
            return hc
        }
        init {
            this._value = updateTreeConnection(null, value)
            updateTreeConnections(this._slice, slice)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Subscript).value },
                { n -> (n as Subscript).slice },
            )
        }
    }

    class Starred(
        pos: Position,
        value: Expr,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = PyOperatorDefinition.StarExpr
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate114
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
        override fun deepCopy(): Starred {
            return Starred(pos, value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Starred && this.value == other.value
        }
        override fun hashCode(): Int {
            return value.hashCode()
        }
        init {
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Starred).value },
            )
        }
    }

    /** A Python identifier that serves as an atom in an expression. */
    class Name(
        pos: Position,
        var id: PyIdentifierName,
        var sourceIdentifier: TemperName? = null,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = PyOperatorDefinition.Atom
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.name(outName, inOperatorPosition = false)
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

    class ListExpr(
        pos: Position,
        elts: Iterable<Expr>,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate115
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.elts)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _elts: MutableList<Expr> = mutableListOf()
        var elts: List<Expr>
            get() = _elts
            set(newValue) { updateTreeConnections(_elts, newValue) }
        override fun deepCopy(): ListExpr {
            return ListExpr(pos, elts = this.elts.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ListExpr && this.elts == other.elts
        }
        override fun hashCode(): Int {
            return elts.hashCode()
        }
        init {
            updateTreeConnections(this._elts, elts)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ListExpr).elts },
            )
        }
    }

    class Tuple(
        pos: Position,
        elts: Iterable<Expr>,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (eltsIsOne) {
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
                0 -> FormattableTreeGroup(this.elts)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _elts: MutableList<Expr> = mutableListOf()
        var elts: List<Expr>
            get() = _elts
            set(newValue) { updateTreeConnections(_elts, newValue) }
        val eltsIsOne: Boolean
            get() = elts.size == 1
        override fun deepCopy(): Tuple {
            return Tuple(pos, elts = this.elts.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Tuple && this.elts == other.elts
        }
        override fun hashCode(): Int {
            return elts.hashCode()
        }
        init {
            updateTreeConnections(this._elts, elts)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Tuple).elts },
            )
        }
    }

    /** A binary operator constructed from [BinaryOpEnum.atom] */
    class BinaryOp(
        pos: Position,
        var opEnum: BinaryOpEnum,
        var expressionOperatorDefinition: PyOperatorDefinition,
    ) : BaseTree(pos) {
        override val operatorDefinition: PyOperatorDefinition?
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

    /** A unary operator constructed from [UnaryOpEnum.atom] */
    class UnaryOp(
        pos: Position,
        var opEnum: UnaryOpEnum,
        var expressionOperatorDefinition: PyOperatorDefinition,
    ) : BaseTree(pos) {
        override val operatorDefinition: PyOperatorDefinition?
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

    class DictPair(
        pos: Position,
        key: Expr,
        value: Expr,
    ) : BaseTree(pos) {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate118
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
        override fun deepCopy(): DictPair {
            return DictPair(pos, key = this.key.deepCopy(), value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is DictPair && this.key == other.key && this.value == other.value
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
                { n -> (n as DictPair).key },
                { n -> (n as DictPair).value },
            )
        }
    }

    class Comprehension(
        pos: Position,
        target: Expr,
        iter: Expr,
        ifs: Iterable<ComprehensionIf> = listOf(),
        var async: Boolean = false,
    ) : BaseTree(pos) {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (async) {
                    sharedCodeFormattingTemplate119
                } else {
                    sharedCodeFormattingTemplate120
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.target
                1 -> this.iter
                2 -> FormattableTreeGroup(this.ifs)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _target: Expr
        var target: Expr
            get() = _target
            set(newValue) { _target = updateTreeConnection(_target, newValue) }
        private var _iter: Expr
        var iter: Expr
            get() = _iter
            set(newValue) { _iter = updateTreeConnection(_iter, newValue) }
        private val _ifs: MutableList<ComprehensionIf> = mutableListOf()
        var ifs: List<ComprehensionIf>
            get() = _ifs
            set(newValue) { updateTreeConnections(_ifs, newValue) }
        override fun deepCopy(): Comprehension {
            return Comprehension(pos, target = this.target.deepCopy(), iter = this.iter.deepCopy(), ifs = this.ifs.deepCopy(), async = this.async)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Comprehension && this.target == other.target && this.iter == other.iter && this.ifs == other.ifs && this.async == other.async
        }
        override fun hashCode(): Int {
            var hc = target.hashCode()
            hc = 31 * hc + iter.hashCode()
            hc = 31 * hc + ifs.hashCode()
            hc = 31 * hc + async.hashCode()
            return hc
        }
        init {
            this._target = updateTreeConnection(null, target)
            this._iter = updateTreeConnection(null, iter)
            updateTreeConnections(this._ifs, ifs)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Comprehension).target },
                { n -> (n as Comprehension).iter },
                { n -> (n as Comprehension).ifs },
            )
        }
    }

    class Slice(
        pos: Position,
        lower: Expr? = null,
        upper: Expr? = null,
        step: Expr? = null,
    ) : BaseTree(pos), SubscriptSlice {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (step != null) {
                    sharedCodeFormattingTemplate121
                } else {
                    sharedCodeFormattingTemplate122
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.lower ?: FormattableTreeGroup.empty
                1 -> this.upper ?: FormattableTreeGroup.empty
                2 -> this.step ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _lower: Expr?
        var lower: Expr?
            get() = _lower
            set(newValue) { _lower = updateTreeConnection(_lower, newValue) }
        private var _upper: Expr?
        var upper: Expr?
            get() = _upper
            set(newValue) { _upper = updateTreeConnection(_upper, newValue) }
        private var _step: Expr?
        var step: Expr?
            get() = _step
            set(newValue) { _step = updateTreeConnection(_step, newValue) }
        override fun deepCopy(): Slice {
            return Slice(pos, lower = this.lower?.deepCopy(), upper = this.upper?.deepCopy(), step = this.step?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Slice && this.lower == other.lower && this.upper == other.upper && this.step == other.step
        }
        override fun hashCode(): Int {
            var hc = (lower?.hashCode() ?: 0)
            hc = 31 * hc + (upper?.hashCode() ?: 0)
            hc = 31 * hc + (step?.hashCode() ?: 0)
            return hc
        }
        init {
            this._lower = updateTreeConnection(null, lower)
            this._upper = updateTreeConnection(null, upper)
            this._step = updateTreeConnection(null, step)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Slice).lower },
                { n -> (n as Slice).upper },
                { n -> (n as Slice).step },
            )
        }
    }

    class ComprehensionIf(
        pos: Position,
        test: Expr,
    ) : BaseTree(pos) {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate123
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.test
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _test: Expr
        var test: Expr
            get() = _test
            set(newValue) { _test = updateTreeConnection(_test, newValue) }
        override fun deepCopy(): ComprehensionIf {
            return ComprehensionIf(pos, test = this.test.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ComprehensionIf && this.test == other.test
        }
        override fun hashCode(): Int {
            return test.hashCode()
        }
        init {
            this._test = updateTreeConnection(null, test)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ComprehensionIf).test },
            )
        }
    }

    class Arg(
        pos: Position,
        arg: Identifier,
        annotation: Expr? = null,
        defaultValue: Expr? = null,
        var prefix: ArgPrefix = ArgPrefix.None,
    ) : BaseTree(pos) {
        override val operatorDefinition: PyOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (prefix == ArgPrefix.Star && annotation != null && defaultValue != null) {
                    sharedCodeFormattingTemplate124
                } else if (prefix == ArgPrefix.Star && annotation != null) {
                    sharedCodeFormattingTemplate125
                } else if (prefix == ArgPrefix.Star && defaultValue != null) {
                    sharedCodeFormattingTemplate126
                } else if (prefix == ArgPrefix.Star) {
                    sharedCodeFormattingTemplate114
                } else if (prefix == ArgPrefix.DoubleStar && annotation != null && defaultValue != null) {
                    sharedCodeFormattingTemplate127
                } else if (prefix == ArgPrefix.DoubleStar && annotation != null) {
                    sharedCodeFormattingTemplate128
                } else if (prefix == ArgPrefix.DoubleStar && defaultValue != null) {
                    sharedCodeFormattingTemplate129
                } else if (prefix == ArgPrefix.DoubleStar) {
                    sharedCodeFormattingTemplate130
                } else if (annotation != null && defaultValue != null) {
                    sharedCodeFormattingTemplate131
                } else if (annotation != null) {
                    sharedCodeFormattingTemplate118
                } else if (defaultValue != null) {
                    sharedCodeFormattingTemplate132
                } else {
                    sharedCodeFormattingTemplate87
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.arg
                1 -> this.annotation ?: FormattableTreeGroup.empty
                2 -> this.defaultValue ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _arg: Identifier
        var arg: Identifier
            get() = _arg
            set(newValue) { _arg = updateTreeConnection(_arg, newValue) }
        private var _annotation: Expr?
        var annotation: Expr?
            get() = _annotation
            set(newValue) { _annotation = updateTreeConnection(_annotation, newValue) }
        private var _defaultValue: Expr?
        var defaultValue: Expr?
            get() = _defaultValue
            set(newValue) { _defaultValue = updateTreeConnection(_defaultValue, newValue) }
        override fun deepCopy(): Arg {
            return Arg(pos, arg = this.arg.deepCopy(), annotation = this.annotation?.deepCopy(), defaultValue = this.defaultValue?.deepCopy(), prefix = this.prefix)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Arg && this.arg == other.arg && this.annotation == other.annotation && this.defaultValue == other.defaultValue && this.prefix == other.prefix
        }
        override fun hashCode(): Int {
            var hc = arg.hashCode()
            hc = 31 * hc + (annotation?.hashCode() ?: 0)
            hc = 31 * hc + (defaultValue?.hashCode() ?: 0)
            hc = 31 * hc + prefix.hashCode()
            return hc
        }
        init {
            this._arg = updateTreeConnection(null, arg)
            this._annotation = updateTreeConnection(null, annotation)
            this._defaultValue = updateTreeConnection(null, defaultValue)
            require(prefix == ArgPrefix.None || defaultValue == null)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Arg).arg },
                { n -> (n as Arg).annotation },
                { n -> (n as Arg).defaultValue },
            )
        }
    }

    /** `{{0*}}` */
    private val sharedCodeFormattingTemplate0 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.empty,
        )

    /** `{{0*}} async def {{1}} ( {{2}} ) -> {{3}} : \n `SpecialTokens.indent` {{4*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate1 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("def", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `{{0*}} async def {{1}} ( {{2}} ) -> {{3}} : \n `SpecialTokens.indent` {{4*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate2 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("def", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `{{0*}} async def {{1}} ( {{2}} ) : \n `SpecialTokens.indent` {{4*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate3 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("def", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `{{0*}} async def {{1}} ( {{2}} ) : \n `SpecialTokens.indent` {{4*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate4 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("def", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `{{0*}} def {{1}} ( {{2}} ) -> {{3}} : \n `SpecialTokens.indent` {{4*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate5 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("def", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `{{0*}} def {{1}} ( {{2}} ) -> {{3}} : \n `SpecialTokens.indent` {{4*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate6 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("def", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `{{0*}} def {{1}} ( {{2}} ) : \n `SpecialTokens.indent` {{4*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate7 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("def", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `{{0*}} def {{1}} ( {{2}} ) : \n `SpecialTokens.indent` {{4*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate8 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("def", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `{{0*}} class {{1}} ( {{2*,}} ) : \n `SpecialTokens.indent` {{3*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate9 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `{{0*}} class {{1}} ( {{2*,}} ) : \n `SpecialTokens.indent` {{3*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate10 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `{{0*}} class {{1}} : \n `SpecialTokens.indent` {{3*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate11 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `{{0*}} class {{1}} : \n `SpecialTokens.indent` {{3*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate12 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `return {{0}} \n` */
    private val sharedCodeFormattingTemplate13 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("return", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `del {{0*,}} \n` */
    private val sharedCodeFormattingTemplate14 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("del", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `{{0*=}} = {{1}} \n` */
    private val sharedCodeFormattingTemplate15 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `{{0}} {{1}} {{2}} \n` */
    private val sharedCodeFormattingTemplate16 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `{{0}} : {{1}} = {{2}} \n` */
    private val sharedCodeFormattingTemplate17 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `{{0}} : {{1}} \n` */
    private val sharedCodeFormattingTemplate18 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `async for {{0}} in {{1}} : \n `SpecialTokens.indent` {{2*}} pass \n `SpecialTokens.dedent` else : \n `SpecialTokens.indent` {{3*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate19 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("in", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `async for {{0}} in {{1}} : \n `SpecialTokens.indent` {{2*}} pass \n `SpecialTokens.dedent` else : \n `SpecialTokens.indent` {{3*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate20 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("in", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `async for {{0}} in {{1}} : \n `SpecialTokens.indent` {{2*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate21 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("in", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `async for {{0}} in {{1}} : \n `SpecialTokens.indent` {{2*}} `SpecialTokens.dedent` else : \n `SpecialTokens.indent` {{3*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate22 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("in", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `async for {{0}} in {{1}} : \n `SpecialTokens.indent` {{2*}} `SpecialTokens.dedent` else : \n `SpecialTokens.indent` {{3*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate23 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("in", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `async for {{0}} in {{1}} : \n `SpecialTokens.indent` {{2*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate24 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("in", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `for {{0}} in {{1}} : \n `SpecialTokens.indent` {{2*}} pass \n `SpecialTokens.dedent` else : \n `SpecialTokens.indent` {{3*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate25 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("in", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `for {{0}} in {{1}} : \n `SpecialTokens.indent` {{2*}} pass \n `SpecialTokens.dedent` else : \n `SpecialTokens.indent` {{3*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate26 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("in", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `for {{0}} in {{1}} : \n `SpecialTokens.indent` {{2*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate27 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("in", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `for {{0}} in {{1}} : \n `SpecialTokens.indent` {{2*}} `SpecialTokens.dedent` else : \n `SpecialTokens.indent` {{3*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate28 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("in", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `for {{0}} in {{1}} : \n `SpecialTokens.indent` {{2*}} `SpecialTokens.dedent` else : \n `SpecialTokens.indent` {{3*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate29 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("in", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `for {{0}} in {{1}} : \n `SpecialTokens.indent` {{2*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate30 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("in", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `while {{0}} : \n `SpecialTokens.indent` {{1*}} pass \n `SpecialTokens.dedent` else : \n `SpecialTokens.indent` {{2*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate31 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("while", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `while {{0}} : \n `SpecialTokens.indent` {{1*}} pass \n `SpecialTokens.dedent` else : \n `SpecialTokens.indent` {{2*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate32 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("while", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `while {{0}} : \n `SpecialTokens.indent` {{1*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate33 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("while", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `while {{0}} : \n `SpecialTokens.indent` {{1*}} `SpecialTokens.dedent` else : \n `SpecialTokens.indent` {{2*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate34 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("while", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `while {{0}} : \n `SpecialTokens.indent` {{1*}} `SpecialTokens.dedent` else : \n `SpecialTokens.indent` {{2*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate35 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("while", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `while {{0}} : \n `SpecialTokens.indent` {{1*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate36 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("while", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `if {{0}} : \n `SpecialTokens.indent` {{1*}} pass \n `SpecialTokens.dedent` {{2*}} else : \n `SpecialTokens.indent` {{3*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate37 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `if {{0}} : \n `SpecialTokens.indent` {{1*}} pass \n `SpecialTokens.dedent` {{2*}} else : \n `SpecialTokens.indent` {{3*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate38 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `if {{0}} : \n `SpecialTokens.indent` {{1*}} pass \n `SpecialTokens.dedent` {{2*}}` */
    private val sharedCodeFormattingTemplate39 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
            ),
        )

    /** `if {{0}} : \n `SpecialTokens.indent` {{1*}} `SpecialTokens.dedent` {{2*}} else : \n `SpecialTokens.indent` {{3*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate40 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `if {{0}} : \n `SpecialTokens.indent` {{1*}} `SpecialTokens.dedent` {{2*}} else : \n `SpecialTokens.indent` {{3*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate41 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `if {{0}} : \n `SpecialTokens.indent` {{1*}} `SpecialTokens.dedent` {{2*}}` */
    private val sharedCodeFormattingTemplate42 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
            ),
        )

    /** `async with {{0*,}} : \n `SpecialTokens.indent` {{1*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate43 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("with", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `async with {{0*,}} : \n `SpecialTokens.indent` {{1*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate44 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("with", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `with {{0*,}} : \n `SpecialTokens.indent` {{1*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate45 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("with", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `with {{0*,}} : \n `SpecialTokens.indent` {{1*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate46 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("with", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `raise {{0}} from {{1}} \n` */
    private val sharedCodeFormattingTemplate47 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("raise", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("from", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `raise {{0}} \n` */
    private val sharedCodeFormattingTemplate48 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("raise", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `raise \n` */
    private val sharedCodeFormattingTemplate49 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("raise", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} pass \n `SpecialTokens.dedent` {{1*}} else : \n `SpecialTokens.indent` {{2*}} pass \n `SpecialTokens.dedent` finally : \n `SpecialTokens.indent` {{3*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate50 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} pass \n `SpecialTokens.dedent` {{1*}} else : \n `SpecialTokens.indent` {{2*}} pass \n `SpecialTokens.dedent` finally : \n `SpecialTokens.indent` {{3*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate51 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} pass \n `SpecialTokens.dedent` {{1*}} else : \n `SpecialTokens.indent` {{2*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate52 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} pass \n `SpecialTokens.dedent` {{1*}} else : \n `SpecialTokens.indent` {{2*}} `SpecialTokens.dedent` finally : \n `SpecialTokens.indent` {{3*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate53 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} pass \n `SpecialTokens.dedent` {{1*}} else : \n `SpecialTokens.indent` {{2*}} `SpecialTokens.dedent` finally : \n `SpecialTokens.indent` {{3*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate54 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} pass \n `SpecialTokens.dedent` {{1*}} else : \n `SpecialTokens.indent` {{2*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate55 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} pass \n `SpecialTokens.dedent` {{1*}} finally : \n `SpecialTokens.indent` {{3*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate56 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} pass \n `SpecialTokens.dedent` {{1*}} finally : \n `SpecialTokens.indent` {{3*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate57 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} pass \n `SpecialTokens.dedent` {{1*}}` */
    private val sharedCodeFormattingTemplate58 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} `SpecialTokens.dedent` {{1*}} else : \n `SpecialTokens.indent` {{2*}} pass \n `SpecialTokens.dedent` finally : \n `SpecialTokens.indent` {{3*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate59 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} `SpecialTokens.dedent` {{1*}} else : \n `SpecialTokens.indent` {{2*}} pass \n `SpecialTokens.dedent` finally : \n `SpecialTokens.indent` {{3*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate60 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} `SpecialTokens.dedent` {{1*}} else : \n `SpecialTokens.indent` {{2*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate61 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} `SpecialTokens.dedent` {{1*}} else : \n `SpecialTokens.indent` {{2*}} `SpecialTokens.dedent` finally : \n `SpecialTokens.indent` {{3*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate62 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} `SpecialTokens.dedent` {{1*}} else : \n `SpecialTokens.indent` {{2*}} `SpecialTokens.dedent` finally : \n `SpecialTokens.indent` {{3*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate63 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} `SpecialTokens.dedent` {{1*}} else : \n `SpecialTokens.indent` {{2*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate64 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} `SpecialTokens.dedent` {{1*}} finally : \n `SpecialTokens.indent` {{3*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate65 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} `SpecialTokens.dedent` {{1*}} finally : \n `SpecialTokens.indent` {{3*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate66 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `try : \n `SpecialTokens.indent` {{0*}} `SpecialTokens.dedent` {{1*}}` */
    private val sharedCodeFormattingTemplate67 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
            ),
        )

    /** `assert {{0}} , {{1}} \n` */
    private val sharedCodeFormattingTemplate68 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("assert", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `assert {{0}} \n` */
    private val sharedCodeFormattingTemplate69 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("assert", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `assert \n` */
    private val sharedCodeFormattingTemplate70 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("assert", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `global {{0*,}} \n` */
    private val sharedCodeFormattingTemplate71 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("global", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `nonlocal {{0*,}} \n` */
    private val sharedCodeFormattingTemplate72 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("nonlocal", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `{{0}} \n` */
    private val sharedCodeFormattingTemplate73 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `pass \n` */
    private val sharedCodeFormattingTemplate74 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `break \n` */
    private val sharedCodeFormattingTemplate75 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("break", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `continue \n` */
    private val sharedCodeFormattingTemplate76 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("continue", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `@ {{0*.}} ( {{1*,}} ) \n` */
    private val sharedCodeFormattingTemplate77 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("@", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `@ {{0*.}} \n` */
    private val sharedCodeFormattingTemplate78 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("@", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** ``starStarToken` {{0}} = {{1}}` */
    private val sharedCodeFormattingTemplate79 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken(starStarToken),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** ``starStarToken` {{1}}` */
    private val sharedCodeFormattingTemplate80 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken(starStarToken),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} = {{1}}` */
    private val sharedCodeFormattingTemplate81 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{1}}` */
    private val sharedCodeFormattingTemplate82 =
        CodeFormattingTemplate.OneSubstitution(1)

    /** `{{0*,}}` */
    private val sharedCodeFormattingTemplate83 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
        )

    /** `elif {{0}} : \n `SpecialTokens.indent` {{1*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate84 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("elif", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `elif {{0}} : \n `SpecialTokens.indent` {{1*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate85 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("elif", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `{{0}} as {{1}}` */
    private val sharedCodeFormattingTemplate86 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("as", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}}` */
    private val sharedCodeFormattingTemplate87 =
        CodeFormattingTemplate.OneSubstitution(0)

    /** `except {{0}} as {{1}} : \n `SpecialTokens.indent` {{2*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate88 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("except", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("as", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `except {{0}} as {{1}} : \n `SpecialTokens.indent` {{2*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate89 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("except", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("as", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `except {{0}} : \n `SpecialTokens.indent` {{2*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate90 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("except", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `except {{0}} : \n `SpecialTokens.indent` {{2*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate91 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("except", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `except : \n `SpecialTokens.indent` {{2*}} pass \n `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate92 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("except", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("pass", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `except : \n `SpecialTokens.indent` {{2*}} `SpecialTokens.dedent`` */
    private val sharedCodeFormattingTemplate93 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("except", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(SpecialTokens.indent),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(SpecialTokens.dedent),
            ),
        )

    /** `import {{0*,}} \n` */
    private val sharedCodeFormattingTemplate94 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `from {{0}} import {{1*,}} \n` */
    private val sharedCodeFormattingTemplate95 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("from", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `from {{0}} import * \n` */
    private val sharedCodeFormattingTemplate96 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("from", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `{{0}} {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate97 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0}} {{1}}` */
    private val sharedCodeFormattingTemplate98 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `lambda {{0}} : {{1}}` */
    private val sharedCodeFormattingTemplate99 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("lambda", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `lambda : {{1}}` */
    private val sharedCodeFormattingTemplate100 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("lambda", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} if {{1}} else {{2}}` */
    private val sharedCodeFormattingTemplate101 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `\{ {{0*,}} \}` */
    private val sharedCodeFormattingTemplate102 =
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

    /** `__builtins__.set()` */
    private val sharedCodeFormattingTemplate103 =
        CodeFormattingTemplate.LiteralToken("__builtins__.set()", OutputTokenType.Word)

    /** `[ {{0}} {{1*}} ]` */
    private val sharedCodeFormattingTemplate104 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `\{ {{0}} {{1*}} \}` */
    private val sharedCodeFormattingTemplate105 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `\{ {{0}} : {{1}} {{2*}} \}` */
    private val sharedCodeFormattingTemplate106 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `( {{0}} {{1*}} )` */
    private val sharedCodeFormattingTemplate107 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `await {{0}}` */
    private val sharedCodeFormattingTemplate108 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("await", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `yield {{0}}` */
    private val sharedCodeFormattingTemplate109 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("yield", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `yield from {{0}}` */
    private val sharedCodeFormattingTemplate110 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("yield", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("from", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `{{0}} ( {{1*,}} )` */
    private val sharedCodeFormattingTemplate111 =
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

    /** `{{0}} . {{1}}` */
    private val sharedCodeFormattingTemplate112 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} [ {{1*,}} ]` */
    private val sharedCodeFormattingTemplate113 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** ``starToken` {{0}}` */
    private val sharedCodeFormattingTemplate114 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken(starToken),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `[ {{0*,}} ]` */
    private val sharedCodeFormattingTemplate115 =
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

    /** `( {{0*,}} , )` */
    private val sharedCodeFormattingTemplate116 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( {{0*,}} )` */
    private val sharedCodeFormattingTemplate117 =
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

    /** `{{0}} : {{1}}` */
    private val sharedCodeFormattingTemplate118 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `async for {{0}} in {{1}} {{2*}}` */
    private val sharedCodeFormattingTemplate119 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("in", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
            ),
        )

    /** `for {{0}} in {{1}} {{2*}}` */
    private val sharedCodeFormattingTemplate120 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("in", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
            ),
        )

    /** `{{0}} `sliceColonToken` {{1}} `sliceColonToken` {{2}}` */
    private val sharedCodeFormattingTemplate121 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(sliceColonToken),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(sliceColonToken),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0}} `sliceColonToken` {{1}}` */
    private val sharedCodeFormattingTemplate122 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(sliceColonToken),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `if {{0}}` */
    private val sharedCodeFormattingTemplate123 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** ``starToken` {{0}} : {{1}} = {{2}}` */
    private val sharedCodeFormattingTemplate124 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken(starToken),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** ``starToken` {{0}} : {{1}}` */
    private val sharedCodeFormattingTemplate125 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken(starToken),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** ``starToken` {{0}} = {{2}}` */
    private val sharedCodeFormattingTemplate126 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken(starToken),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** ``starStarToken` {{0}} : {{1}} = {{2}}` */
    private val sharedCodeFormattingTemplate127 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken(starStarToken),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** ``starStarToken` {{0}} : {{1}}` */
    private val sharedCodeFormattingTemplate128 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken(starStarToken),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** ``starStarToken` {{0}} = {{2}}` */
    private val sharedCodeFormattingTemplate129 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken(starStarToken),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** ``starStarToken` {{0}}` */
    private val sharedCodeFormattingTemplate130 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken(starStarToken),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `{{0}} : {{1}} = {{2}}` */
    private val sharedCodeFormattingTemplate131 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0}} = {{2}}` */
    private val sharedCodeFormattingTemplate132 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )
}
