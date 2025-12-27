@file:lang.temper.common.Generated("OutputGrammarCodeGenerator")
@file:Suppress("ktlint", "unused", "CascadeIf", "MagicNumber", "MemberNameEqualsClassName", "MemberVisibilityCanBePrivate")

package lang.temper.be.cpp
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
import lang.temper.name.TemperName

object Cpp {
    sealed interface Tree : OutTree<Tree> {
        override fun formattingHints(): FormattingHints = CppFormattingHints.getInstance()
        override val operatorDefinition: CppOperatorDefinition?
        override fun deepCopy(): Tree
    }
    sealed class BaseTree(
        pos: Position,
    ) : BaseOutTree<Tree>(pos), Tree
    sealed interface Data : OutData<Data> {
        override fun formattingHints(): FormattingHints = CppFormattingHints.getInstance()
        override val operatorDefinition: CppOperatorDefinition?
    }
    sealed class BaseData : BaseOutData<Data>(), Data

    enum class DefMod : FormattableEnum {
        Static,
    }

    class Program(
        pos: Position,
        stmts: Iterable<Global>,
    ) : BaseTree(pos) {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate0
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.stmts)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _stmts: MutableList<Global> = mutableListOf()
        var stmts: List<Global>
            get() = _stmts
            set(newValue) { updateTreeConnections(_stmts, newValue) }
        override fun deepCopy(): Program {
            return Program(pos, stmts = this.stmts.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Program && this.stmts == other.stmts
        }
        override fun hashCode(): Int {
            return stmts.hashCode()
        }
        init {
            updateTreeConnections(this._stmts, stmts)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Program).stmts },
            )
        }
    }

    sealed interface Global : Tree {
        override fun deepCopy(): Global
    }

    class StructDecl(
        pos: Position,
        name: Name,
    ) : BaseTree(pos), Global {
        override val operatorDefinition: CppOperatorDefinition?
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
        override fun deepCopy(): StructDecl {
            return StructDecl(pos, name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StructDecl && this.name == other.name
        }
        override fun hashCode(): Int {
            return name.hashCode()
        }
        init {
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as StructDecl).name },
            )
        }
    }

    sealed interface StructPart : Tree {
        override fun deepCopy(): StructPart
    }

    class FuncDecl(
        pos: Position,
        ret: Type?,
        convention: SingleName? = null,
        name: SingleName,
        args: Iterable<Type>,
    ) : BaseTree(pos), Global, StructPart {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate2
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.ret ?: FormattableTreeGroup.empty
                1 -> this.convention ?: FormattableTreeGroup.empty
                2 -> this.name
                3 -> FormattableTreeGroup(this.args)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _ret: Type?
        var ret: Type?
            get() = _ret
            set(newValue) { _ret = updateTreeConnection(_ret, newValue) }
        private var _convention: SingleName?
        var convention: SingleName?
            get() = _convention
            set(newValue) { _convention = updateTreeConnection(_convention, newValue) }
        private var _name: SingleName
        var name: SingleName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private val _args: MutableList<Type> = mutableListOf()
        var args: List<Type>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): FuncDecl {
            return FuncDecl(pos, ret = this.ret?.deepCopy(), convention = this.convention?.deepCopy(), name = this.name.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FuncDecl && this.ret == other.ret && this.convention == other.convention && this.name == other.name && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = (ret?.hashCode() ?: 0)
            hc = 31 * hc + (convention?.hashCode() ?: 0)
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._ret = updateTreeConnection(null, ret)
            this._convention = updateTreeConnection(null, convention)
            this._name = updateTreeConnection(null, name)
            updateTreeConnections(this._args, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FuncDecl).ret },
                { n -> (n as FuncDecl).convention },
                { n -> (n as FuncDecl).name },
                { n -> (n as FuncDecl).args },
            )
        }
    }

    class VarDecl(
        pos: Position,
        type: Type,
        name: SingleName,
    ) : BaseTree(pos), Global {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate3
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type
                1 -> this.name
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: Type
        var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _name: SingleName
        var name: SingleName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override fun deepCopy(): VarDecl {
            return VarDecl(pos, type = this.type.deepCopy(), name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is VarDecl && this.type == other.type && this.name == other.name
        }
        override fun hashCode(): Int {
            var hc = type.hashCode()
            hc = 31 * hc + name.hashCode()
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as VarDecl).type },
                { n -> (n as VarDecl).name },
            )
        }
    }

    class StructDef(
        pos: Position,
        name: Name,
        fields: Iterable<StructPart>,
    ) : BaseTree(pos), Global {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate4
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
        private var _name: Name
        var name: Name
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private val _fields: MutableList<StructPart> = mutableListOf()
        var fields: List<StructPart>
            get() = _fields
            set(newValue) { updateTreeConnections(_fields, newValue) }
        override fun deepCopy(): StructDef {
            return StructDef(pos, name = this.name.deepCopy(), fields = this.fields.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StructDef && this.name == other.name && this.fields == other.fields
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
                { n -> (n as StructDef).name },
                { n -> (n as StructDef).fields },
            )
        }
    }

    class TypeDef(
        pos: Position,
        type: Type,
        name: SingleName,
    ) : BaseTree(pos), Global {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate5
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type
                1 -> this.name
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: Type
        var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _name: SingleName
        var name: SingleName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override fun deepCopy(): TypeDef {
            return TypeDef(pos, type = this.type.deepCopy(), name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeDef && this.type == other.type && this.name == other.name
        }
        override fun hashCode(): Int {
            var hc = type.hashCode()
            hc = 31 * hc + name.hashCode()
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeDef).type },
                { n -> (n as TypeDef).name },
            )
        }
    }

    class FuncDef(
        pos: Position,
        var mod: DefMod? = null,
        ret: Type?,
        convention: SingleName? = null,
        name: Name,
        args: Iterable<FuncParam>,
        body: BlockStmt,
    ) : BaseTree(pos), Global, StructPart {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate6
        override val formatElementCount
            get() = 6
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.mod ?: FormattableTreeGroup.empty
                1 -> this.ret ?: FormattableTreeGroup.empty
                2 -> this.convention ?: FormattableTreeGroup.empty
                3 -> this.name
                4 -> FormattableTreeGroup(this.args)
                5 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _ret: Type?
        var ret: Type?
            get() = _ret
            set(newValue) { _ret = updateTreeConnection(_ret, newValue) }
        private var _convention: SingleName?
        var convention: SingleName?
            get() = _convention
            set(newValue) { _convention = updateTreeConnection(_convention, newValue) }
        private var _name: Name
        var name: Name
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private val _args: MutableList<FuncParam> = mutableListOf()
        var args: List<FuncParam>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        private var _body: BlockStmt
        var body: BlockStmt
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): FuncDef {
            return FuncDef(pos, mod = this.mod, ret = this.ret?.deepCopy(), convention = this.convention?.deepCopy(), name = this.name.deepCopy(), args = this.args.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FuncDef && this.mod == other.mod && this.ret == other.ret && this.convention == other.convention && this.name == other.name && this.args == other.args && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = (mod?.hashCode() ?: 0)
            hc = 31 * hc + (ret?.hashCode() ?: 0)
            hc = 31 * hc + (convention?.hashCode() ?: 0)
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + args.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._ret = updateTreeConnection(null, ret)
            this._convention = updateTreeConnection(null, convention)
            this._name = updateTreeConnection(null, name)
            updateTreeConnections(this._args, args)
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FuncDef).ret },
                { n -> (n as FuncDef).convention },
                { n -> (n as FuncDef).name },
                { n -> (n as FuncDef).args },
                { n -> (n as FuncDef).body },
            )
        }
    }

    sealed interface Stmt : Tree {
        override fun deepCopy(): Stmt
    }

    class VarDef(
        pos: Position,
        var mod: DefMod? = null,
        type: Type,
        name: Name,
        init: Expr?,
    ) : BaseTree(pos), Global, Stmt {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (init != null) {
                    sharedCodeFormattingTemplate7
                } else {
                    sharedCodeFormattingTemplate8
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.mod ?: FormattableTreeGroup.empty
                1 -> this.type
                2 -> this.name
                3 -> this.init ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: Type
        var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _name: Name
        var name: Name
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _init: Expr?
        var init: Expr?
            get() = _init
            set(newValue) { _init = updateTreeConnection(_init, newValue) }
        override fun deepCopy(): VarDef {
            return VarDef(pos, mod = this.mod, type = this.type.deepCopy(), name = this.name.deepCopy(), init = this.init?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is VarDef && this.mod == other.mod && this.type == other.type && this.name == other.name && this.init == other.init
        }
        override fun hashCode(): Int {
            var hc = (mod?.hashCode() ?: 0)
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + (init?.hashCode() ?: 0)
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            this._name = updateTreeConnection(null, name)
            this._init = updateTreeConnection(null, init)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as VarDef).type },
                { n -> (n as VarDef).name },
                { n -> (n as VarDef).init },
            )
        }
    }

    sealed interface PreProc : Tree, Global {
        override fun deepCopy(): PreProc
    }

    class Comment(
        pos: Position,
        data: Raw,
    ) : BaseTree(pos), Global, StructPart, Stmt {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate9
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.data
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _data: Raw
        var data: Raw
            get() = _data
            set(newValue) { _data = updateTreeConnection(_data, newValue) }
        override fun deepCopy(): Comment {
            return Comment(pos, data = this.data.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Comment && this.data == other.data
        }
        override fun hashCode(): Int {
            return data.hashCode()
        }
        init {
            this._data = updateTreeConnection(null, data)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Comment).data },
            )
        }
    }

    class Namespace(
        pos: Position,
        name: SingleName?,
        body: Iterable<Global>,
    ) : BaseTree(pos), Global {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (name != null) {
                    sharedCodeFormattingTemplate10
                } else {
                    sharedCodeFormattingTemplate11
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name ?: FormattableTreeGroup.empty
                1 -> FormattableTreeGroup(this.body)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _name: SingleName?
        var name: SingleName?
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private val _body: MutableList<Global> = mutableListOf()
        var body: List<Global>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        override fun deepCopy(): Namespace {
            return Namespace(pos, name = this.name?.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Namespace && this.name == other.name && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = (name?.hashCode() ?: 0)
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._name = updateTreeConnection(null, name)
            updateTreeConnections(this._body, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Namespace).name },
                { n -> (n as Namespace).body },
            )
        }
    }

    class Define(
        pos: Position,
        name: SingleName,
        value: Expr?,
    ) : BaseTree(pos), PreProc {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate12
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name
                1 -> this.value ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _name: SingleName
        var name: SingleName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _value: Expr?
        var value: Expr?
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): Define {
            return Define(pos, name = this.name.deepCopy(), value = this.value?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Define && this.name == other.name && this.value == other.value
        }
        override fun hashCode(): Int {
            var hc = name.hashCode()
            hc = 31 * hc + (value?.hashCode() ?: 0)
            return hc
        }
        init {
            this._name = updateTreeConnection(null, name)
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Define).name },
                { n -> (n as Define).value },
            )
        }
    }

    class Undef(
        pos: Position,
        name: SingleName,
    ) : BaseTree(pos), PreProc {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate13
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
        private var _name: SingleName
        var name: SingleName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override fun deepCopy(): Undef {
            return Undef(pos, name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Undef && this.name == other.name
        }
        override fun hashCode(): Int {
            return name.hashCode()
        }
        init {
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Undef).name },
            )
        }
    }

    class Pragma(
        pos: Position,
        text: Raw,
    ) : BaseTree(pos), PreProc {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate14
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.text
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _text: Raw
        var text: Raw
            get() = _text
            set(newValue) { _text = updateTreeConnection(_text, newValue) }
        override fun deepCopy(): Pragma {
            return Pragma(pos, text = this.text.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Pragma && this.text == other.text
        }
        override fun hashCode(): Int {
            return text.hashCode()
        }
        init {
            this._text = updateTreeConnection(null, text)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Pragma).text },
            )
        }
    }

    class IncludeGuard(
        pos: Position,
        name: SingleName,
        program: Program,
    ) : BaseTree(pos), PreProc {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate15
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name
                1 -> this.program
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _name: SingleName
        var name: SingleName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _program: Program
        var program: Program
            get() = _program
            set(newValue) { _program = updateTreeConnection(_program, newValue) }
        override fun deepCopy(): IncludeGuard {
            return IncludeGuard(pos, name = this.name.deepCopy(), program = this.program.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is IncludeGuard && this.name == other.name && this.program == other.program
        }
        override fun hashCode(): Int {
            var hc = name.hashCode()
            hc = 31 * hc + program.hashCode()
            return hc
        }
        init {
            this._name = updateTreeConnection(null, name)
            this._program = updateTreeConnection(null, program)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as IncludeGuard).name },
                { n -> (n as IncludeGuard).program },
            )
        }
    }

    class Include(
        pos: Position,
        path: Raw,
    ) : BaseTree(pos), PreProc {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate16
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.path
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _path: Raw
        var path: Raw
            get() = _path
            set(newValue) { _path = updateTreeConnection(_path, newValue) }
        override fun deepCopy(): Include {
            return Include(pos, path = this.path.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Include && this.path == other.path
        }
        override fun hashCode(): Int {
            return path.hashCode()
        }
        init {
            this._path = updateTreeConnection(null, path)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Include).path },
            )
        }
    }

    class IfPreProc(
        pos: Position,
        cond: Expr,
        ifTrue: Program,
        elifs: Iterable<ElifPreProc>,
        ifFalse: Program?,
    ) : BaseTree(pos), PreProc {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (ifFalse != null) {
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
                0 -> this.cond
                1 -> this.ifTrue
                2 -> FormattableTreeGroup(this.elifs)
                3 -> this.ifFalse ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _cond: Expr
        var cond: Expr
            get() = _cond
            set(newValue) { _cond = updateTreeConnection(_cond, newValue) }
        private var _ifTrue: Program
        var ifTrue: Program
            get() = _ifTrue
            set(newValue) { _ifTrue = updateTreeConnection(_ifTrue, newValue) }
        private val _elifs: MutableList<ElifPreProc> = mutableListOf()
        var elifs: List<ElifPreProc>
            get() = _elifs
            set(newValue) { updateTreeConnections(_elifs, newValue) }
        private var _ifFalse: Program?
        var ifFalse: Program?
            get() = _ifFalse
            set(newValue) { _ifFalse = updateTreeConnection(_ifFalse, newValue) }
        override fun deepCopy(): IfPreProc {
            return IfPreProc(pos, cond = this.cond.deepCopy(), ifTrue = this.ifTrue.deepCopy(), elifs = this.elifs.deepCopy(), ifFalse = this.ifFalse?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is IfPreProc && this.cond == other.cond && this.ifTrue == other.ifTrue && this.elifs == other.elifs && this.ifFalse == other.ifFalse
        }
        override fun hashCode(): Int {
            var hc = cond.hashCode()
            hc = 31 * hc + ifTrue.hashCode()
            hc = 31 * hc + elifs.hashCode()
            hc = 31 * hc + (ifFalse?.hashCode() ?: 0)
            return hc
        }
        init {
            this._cond = updateTreeConnection(null, cond)
            this._ifTrue = updateTreeConnection(null, ifTrue)
            updateTreeConnections(this._elifs, elifs)
            this._ifFalse = updateTreeConnection(null, ifFalse)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as IfPreProc).cond },
                { n -> (n as IfPreProc).ifTrue },
                { n -> (n as IfPreProc).elifs },
                { n -> (n as IfPreProc).ifFalse },
            )
        }
    }

    class Raw(
        pos: Position,
        var s: String,
    ) : BaseTree(pos) {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.quoted(s)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): Raw {
            return Raw(pos, s = this.s)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Raw && this.s == other.s
        }
        override fun hashCode(): Int {
            return s.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    sealed interface Type : Tree {
        override fun deepCopy(): Type
    }

    sealed interface Expr : Tree {
        override fun deepCopy(): Expr
    }

    sealed interface Name : Tree, Type, Expr {
        override fun deepCopy(): Name
    }

    class SingleName(
        pos: Position,
        var id: CppName,
        var sourceIdentifier: TemperName? = null,
    ) : BaseTree(pos), Name {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.word(outName.toToken(inOperatorPosition = false).text)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        val outName: OutName
            get() = OutName(id.text, sourceIdentifier)
        override fun deepCopy(): SingleName {
            return SingleName(pos, id = this.id, sourceIdentifier = this.sourceIdentifier)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SingleName && this.id == other.id && this.sourceIdentifier == other.sourceIdentifier
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

    class ElifPreProc(
        pos: Position,
        cond: Expr,
        program: Program,
    ) : BaseTree(pos) {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate19
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.cond
                1 -> this.program
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _cond: Expr
        var cond: Expr
            get() = _cond
            set(newValue) { _cond = updateTreeConnection(_cond, newValue) }
        private var _program: Program
        var program: Program
            get() = _program
            set(newValue) { _program = updateTreeConnection(_program, newValue) }
        override fun deepCopy(): ElifPreProc {
            return ElifPreProc(pos, cond = this.cond.deepCopy(), program = this.program.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ElifPreProc && this.cond == other.cond && this.program == other.program
        }
        override fun hashCode(): Int {
            var hc = cond.hashCode()
            hc = 31 * hc + program.hashCode()
            return hc
        }
        init {
            this._cond = updateTreeConnection(null, cond)
            this._program = updateTreeConnection(null, program)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ElifPreProc).cond },
                { n -> (n as ElifPreProc).program },
            )
        }
    }

    class StructField(
        pos: Position,
        type: Type,
        name: SingleName,
    ) : BaseTree(pos), StructPart {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate20
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type
                1 -> this.name
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: Type
        var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _name: SingleName
        var name: SingleName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override fun deepCopy(): StructField {
            return StructField(pos, type = this.type.deepCopy(), name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StructField && this.type == other.type && this.name == other.name
        }
        override fun hashCode(): Int {
            var hc = type.hashCode()
            hc = 31 * hc + name.hashCode()
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as StructField).type },
                { n -> (n as StructField).name },
            )
        }
    }

    class FuncParam(
        pos: Position,
        type: Type,
        name: SingleName,
    ) : BaseTree(pos) {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate21
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type
                1 -> this.name
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: Type
        var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _name: SingleName
        var name: SingleName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override fun deepCopy(): FuncParam {
            return FuncParam(pos, type = this.type.deepCopy(), name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FuncParam && this.type == other.type && this.name == other.name
        }
        override fun hashCode(): Int {
            var hc = type.hashCode()
            hc = 31 * hc + name.hashCode()
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FuncParam).type },
                { n -> (n as FuncParam).name },
            )
        }
    }

    class BlockStmt(
        pos: Position,
        stmts: Iterable<Stmt>,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate22
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.stmts)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _stmts: MutableList<Stmt> = mutableListOf()
        var stmts: List<Stmt>
            get() = _stmts
            set(newValue) { updateTreeConnections(_stmts, newValue) }
        override fun deepCopy(): BlockStmt {
            return BlockStmt(pos, stmts = this.stmts.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BlockStmt && this.stmts == other.stmts
        }
        override fun hashCode(): Int {
            return stmts.hashCode()
        }
        init {
            updateTreeConnections(this._stmts, stmts)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as BlockStmt).stmts },
            )
        }
    }

    class PreComment(
        pos: Position,
        text: Raw,
        value: Expr,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate23
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.text
                1 -> this.value
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _text: Raw
        var text: Raw
            get() = _text
            set(newValue) { _text = updateTreeConnection(_text, newValue) }
        private var _value: Expr
        var value: Expr
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): PreComment {
            return PreComment(pos, text = this.text.deepCopy(), value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PreComment && this.text == other.text && this.value == other.value
        }
        override fun hashCode(): Int {
            var hc = text.hashCode()
            hc = 31 * hc + value.hashCode()
            return hc
        }
        init {
            this._text = updateTreeConnection(null, text)
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as PreComment).text },
                { n -> (n as PreComment).value },
            )
        }
    }

    class PostComment(
        pos: Position,
        value: Expr,
        text: Raw,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate24
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.value
                1 -> this.text
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _value: Expr
        var value: Expr
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        private var _text: Raw
        var text: Raw
            get() = _text
            set(newValue) { _text = updateTreeConnection(_text, newValue) }
        override fun deepCopy(): PostComment {
            return PostComment(pos, value = this.value.deepCopy(), text = this.text.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PostComment && this.value == other.value && this.text == other.text
        }
        override fun hashCode(): Int {
            var hc = value.hashCode()
            hc = 31 * hc + text.hashCode()
            return hc
        }
        init {
            this._value = updateTreeConnection(null, value)
            this._text = updateTreeConnection(null, text)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as PostComment).value },
                { n -> (n as PostComment).text },
            )
        }
    }

    class ConstType(
        pos: Position,
        base: Type,
    ) : BaseTree(pos), Type {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate25
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.base
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _base: Type
        var base: Type
            get() = _base
            set(newValue) { _base = updateTreeConnection(_base, newValue) }
        override fun deepCopy(): ConstType {
            return ConstType(pos, base = this.base.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ConstType && this.base == other.base
        }
        override fun hashCode(): Int {
            return base.hashCode()
        }
        init {
            this._base = updateTreeConnection(null, base)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ConstType).base },
            )
        }
    }

    class PtrType(
        pos: Position,
        base: Type,
    ) : BaseTree(pos), Type {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate26
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.base
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _base: Type
        var base: Type
            get() = _base
            set(newValue) { _base = updateTreeConnection(_base, newValue) }
        override fun deepCopy(): PtrType {
            return PtrType(pos, base = this.base.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PtrType && this.base == other.base
        }
        override fun hashCode(): Int {
            return base.hashCode()
        }
        init {
            this._base = updateTreeConnection(null, base)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as PtrType).base },
            )
        }
    }

    class TemplateType(
        pos: Position,
        base: Type,
        args: Iterable<Type>,
    ) : BaseTree(pos), Type, Expr {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate27
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.base
                1 -> FormattableTreeGroup(this.args)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _base: Type
        var base: Type
            get() = _base
            set(newValue) { _base = updateTreeConnection(_base, newValue) }
        private val _args: MutableList<Type> = mutableListOf()
        var args: List<Type>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): TemplateType {
            return TemplateType(pos, base = this.base.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TemplateType && this.base == other.base && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = base.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._base = updateTreeConnection(null, base)
            updateTreeConnections(this._args, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TemplateType).base },
                { n -> (n as TemplateType).args },
            )
        }
    }

    class ExprStmt(
        pos: Position,
        expr: Expr,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate28
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
        override fun deepCopy(): ExprStmt {
            return ExprStmt(pos, expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ExprStmt && this.expr == other.expr
        }
        override fun hashCode(): Int {
            return expr.hashCode()
        }
        init {
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ExprStmt).expr },
            )
        }
    }

    class LabelStmt(
        pos: Position,
        label: SingleName,
        stmt: Stmt,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate29
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.label
                1 -> this.stmt
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _label: SingleName
        var label: SingleName
            get() = _label
            set(newValue) { _label = updateTreeConnection(_label, newValue) }
        private var _stmt: Stmt
        var stmt: Stmt
            get() = _stmt
            set(newValue) { _stmt = updateTreeConnection(_stmt, newValue) }
        override fun deepCopy(): LabelStmt {
            return LabelStmt(pos, label = this.label.deepCopy(), stmt = this.stmt.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LabelStmt && this.label == other.label && this.stmt == other.stmt
        }
        override fun hashCode(): Int {
            var hc = label.hashCode()
            hc = 31 * hc + stmt.hashCode()
            return hc
        }
        init {
            this._label = updateTreeConnection(null, label)
            this._stmt = updateTreeConnection(null, stmt)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LabelStmt).label },
                { n -> (n as LabelStmt).stmt },
            )
        }
    }

    class GotoStmt(
        pos: Position,
        label: SingleName,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate30
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.label
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _label: SingleName
        var label: SingleName
            get() = _label
            set(newValue) { _label = updateTreeConnection(_label, newValue) }
        override fun deepCopy(): GotoStmt {
            return GotoStmt(pos, label = this.label.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is GotoStmt && this.label == other.label
        }
        override fun hashCode(): Int {
            return label.hashCode()
        }
        init {
            this._label = updateTreeConnection(null, label)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as GotoStmt).label },
            )
        }
    }

    class ReturnStmt(
        pos: Position,
        value: Expr?,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (value != null) {
                    sharedCodeFormattingTemplate31
                } else {
                    sharedCodeFormattingTemplate32
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
        override fun deepCopy(): ReturnStmt {
            return ReturnStmt(pos, value = this.value?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ReturnStmt && this.value == other.value
        }
        override fun hashCode(): Int {
            return (value?.hashCode() ?: 0)
        }
        init {
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ReturnStmt).value },
            )
        }
    }

    class ThrowStmt(
        pos: Position,
        value: Expr,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate33
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
        override fun deepCopy(): ThrowStmt {
            return ThrowStmt(pos, value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ThrowStmt && this.value == other.value
        }
        override fun hashCode(): Int {
            return value.hashCode()
        }
        init {
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ThrowStmt).value },
            )
        }
    }

    class IfStmt(
        pos: Position,
        cond: Expr,
        ifTrue: Stmt,
        ifFalse: Stmt?,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (ifFalse != null) {
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
                0 -> this.cond
                1 -> this.ifTrue
                2 -> this.ifFalse ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _cond: Expr
        var cond: Expr
            get() = _cond
            set(newValue) { _cond = updateTreeConnection(_cond, newValue) }
        private var _ifTrue: Stmt
        var ifTrue: Stmt
            get() = _ifTrue
            set(newValue) { _ifTrue = updateTreeConnection(_ifTrue, newValue) }
        private var _ifFalse: Stmt?
        var ifFalse: Stmt?
            get() = _ifFalse
            set(newValue) { _ifFalse = updateTreeConnection(_ifFalse, newValue) }
        override fun deepCopy(): IfStmt {
            return IfStmt(pos, cond = this.cond.deepCopy(), ifTrue = this.ifTrue.deepCopy(), ifFalse = this.ifFalse?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is IfStmt && this.cond == other.cond && this.ifTrue == other.ifTrue && this.ifFalse == other.ifFalse
        }
        override fun hashCode(): Int {
            var hc = cond.hashCode()
            hc = 31 * hc + ifTrue.hashCode()
            hc = 31 * hc + (ifFalse?.hashCode() ?: 0)
            return hc
        }
        init {
            this._cond = updateTreeConnection(null, cond)
            this._ifTrue = updateTreeConnection(null, ifTrue)
            this._ifFalse = updateTreeConnection(null, ifFalse)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as IfStmt).cond },
                { n -> (n as IfStmt).ifTrue },
                { n -> (n as IfStmt).ifFalse },
            )
        }
    }

    class WhileStmt(
        pos: Position,
        cond: Expr,
        body: Stmt,
    ) : BaseTree(pos), Stmt {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate36
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
        private var _body: Stmt
        var body: Stmt
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

    class IndexExpr(
        pos: Position,
        base: Expr,
        index: Expr,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate37
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.base
                1 -> this.index
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _base: Expr
        var base: Expr
            get() = _base
            set(newValue) { _base = updateTreeConnection(_base, newValue) }
        private var _index: Expr
        var index: Expr
            get() = _index
            set(newValue) { _index = updateTreeConnection(_index, newValue) }
        override fun deepCopy(): IndexExpr {
            return IndexExpr(pos, base = this.base.deepCopy(), index = this.index.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is IndexExpr && this.base == other.base && this.index == other.index
        }
        override fun hashCode(): Int {
            var hc = base.hashCode()
            hc = 31 * hc + index.hashCode()
            return hc
        }
        init {
            this._base = updateTreeConnection(null, base)
            this._index = updateTreeConnection(null, index)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as IndexExpr).base },
                { n -> (n as IndexExpr).index },
            )
        }
    }

    class CallExpr(
        pos: Position,
        func: Expr,
        args: Iterable<Expr>,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate38
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
        private val _args: MutableList<Expr> = mutableListOf()
        var args: List<Expr>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): CallExpr {
            return CallExpr(pos, func = this.func.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is CallExpr && this.func == other.func && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = func.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._func = updateTreeConnection(null, func)
            updateTreeConnections(this._args, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as CallExpr).func },
                { n -> (n as CallExpr).args },
            )
        }
    }

    class MemberExpr(
        pos: Position,
        obj: Expr,
        member: SingleName,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate39
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.obj
                1 -> this.member
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _obj: Expr
        var obj: Expr
            get() = _obj
            set(newValue) { _obj = updateTreeConnection(_obj, newValue) }
        private var _member: SingleName
        var member: SingleName
            get() = _member
            set(newValue) { _member = updateTreeConnection(_member, newValue) }
        override fun deepCopy(): MemberExpr {
            return MemberExpr(pos, obj = this.obj.deepCopy(), member = this.member.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MemberExpr && this.obj == other.obj && this.member == other.member
        }
        override fun hashCode(): Int {
            var hc = obj.hashCode()
            hc = 31 * hc + member.hashCode()
            return hc
        }
        init {
            this._obj = updateTreeConnection(null, obj)
            this._member = updateTreeConnection(null, member)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as MemberExpr).obj },
                { n -> (n as MemberExpr).member },
            )
        }
    }

    class LiteralExpr(
        pos: Position,
        repr: Raw,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate40
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.repr
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _repr: Raw
        var repr: Raw
            get() = _repr
            set(newValue) { _repr = updateTreeConnection(_repr, newValue) }
        override fun deepCopy(): LiteralExpr {
            return LiteralExpr(pos, repr = this.repr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LiteralExpr && this.repr == other.repr
        }
        override fun hashCode(): Int {
            return repr.hashCode()
        }
        init {
            this._repr = updateTreeConnection(null, repr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LiteralExpr).repr },
            )
        }
    }

    class BinaryExpr(
        pos: Position,
        left: Expr,
        op: BinaryOp,
        right: Expr,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition
            get() = op.opEnum.operatorDefinition
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate41
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
        override val operatorDefinition
            get() = op.opEnum.operatorDefinition
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate21
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

    class CastExpr(
        pos: Position,
        type: Type,
        expr: Expr,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate42
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type
                1 -> this.expr
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: Type
        var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _expr: Expr
        var expr: Expr
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): CastExpr {
            return CastExpr(pos, type = this.type.deepCopy(), expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is CastExpr && this.type == other.type && this.expr == other.expr
        }
        override fun hashCode(): Int {
            var hc = type.hashCode()
            hc = 31 * hc + expr.hashCode()
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as CastExpr).type },
                { n -> (n as CastExpr).expr },
            )
        }
    }

    class ThisExpr(
        pos: Position,
    ) : BaseTree(pos), Expr {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate43
        override val formatElementCount
            get() = 0
        override fun deepCopy(): ThisExpr {
            return ThisExpr(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ThisExpr
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class UnaryOp(
        pos: Position,
        var opEnum: UnaryOpEnum,
    ) : BaseTree(pos) {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            opEnum.renderTo(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): UnaryOp {
            return UnaryOp(pos, opEnum = this.opEnum)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is UnaryOp && this.opEnum == other.opEnum
        }
        override fun hashCode(): Int {
            return opEnum.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class BinaryOp(
        pos: Position,
        var opEnum: BinaryOpEnum,
    ) : BaseTree(pos) {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            opEnum.renderTo(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): BinaryOp {
            return BinaryOp(pos, opEnum = this.opEnum)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BinaryOp && this.opEnum == other.opEnum
        }
        override fun hashCode(): Int {
            return opEnum.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class ScopedName(
        pos: Position,
        base: Type,
        member: SingleName,
    ) : BaseTree(pos), Name {
        override val operatorDefinition: CppOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate44
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.base
                1 -> this.member
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _base: Type
        var base: Type
            get() = _base
            set(newValue) { _base = updateTreeConnection(_base, newValue) }
        private var _member: SingleName
        var member: SingleName
            get() = _member
            set(newValue) { _member = updateTreeConnection(_member, newValue) }
        override fun deepCopy(): ScopedName {
            return ScopedName(pos, base = this.base.deepCopy(), member = this.member.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ScopedName && this.base == other.base && this.member == other.member
        }
        override fun hashCode(): Int {
            var hc = base.hashCode()
            hc = 31 * hc + member.hashCode()
            return hc
        }
        init {
            this._base = updateTreeConnection(null, base)
            this._member = updateTreeConnection(null, member)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ScopedName).base },
                { n -> (n as ScopedName).member },
            )
        }
    }

    class Num(
        pos: Position,
        var n: Double,
    ) : BaseTree(pos) {
        override val operatorDefinition: CppOperatorDefinition?
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

    /** `{{0*\n}} \n` */
    private val sharedCodeFormattingTemplate0 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `struct {{0}} ;` */
    private val sharedCodeFormattingTemplate1 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("struct", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} {{2}} ( {{3*,}} ) ;` */
    private val sharedCodeFormattingTemplate2 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `extern {{0}} {{1}} ;` */
    private val sharedCodeFormattingTemplate3 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("extern", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `struct {{0}} \{ {{1*}} \} ;` */
    private val sharedCodeFormattingTemplate4 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("struct", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `typedef {{0}} {{1}} ;` */
    private val sharedCodeFormattingTemplate5 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("typedef", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} {{2}} {{3}} ( {{4*,}} ) {{5}}` */
    private val sharedCodeFormattingTemplate6 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(5),
            ),
        )

    /** `{{0}} {{1}} {{2}} = {{3}} ;` */
    private val sharedCodeFormattingTemplate7 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} {{2}} ;` */
    private val sharedCodeFormattingTemplate8 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `// {{0}} \n` */
    private val sharedCodeFormattingTemplate9 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("//", OutputTokenType.Comment),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `namespace {{0}} \{ {{1*}} \}` */
    private val sharedCodeFormattingTemplate10 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("namespace", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `namespace \{ {{1*}} \}` */
    private val sharedCodeFormattingTemplate11 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("namespace", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `# define {{0}} {{1}} \n` */
    private val sharedCodeFormattingTemplate12 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("#", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("define", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `# undef {{0}} \n` */
    private val sharedCodeFormattingTemplate13 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("#", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("undef", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `# pragma {{0}} \n` */
    private val sharedCodeFormattingTemplate14 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("#", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("pragma", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `# if ! defined ( {{0}} ) \n # define {{0}} \n {{1}} # endif` */
    private val sharedCodeFormattingTemplate15 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("#", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("!", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("defined", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("#", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("define", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("#", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("endif", OutputTokenType.Word),
            ),
        )

    /** `# include < {{0}} > \n` */
    private val sharedCodeFormattingTemplate16 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("#", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("include", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `# if {{0}} \n {{1}} \n {{2*}} # else \n {{3}} # endif` */
    private val sharedCodeFormattingTemplate17 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("#", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("#", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("#", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("endif", OutputTokenType.Word),
            ),
        )

    /** `# if {{0}} \n {{1}} \n {{2*}} # endif` */
    private val sharedCodeFormattingTemplate18 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("#", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("#", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("endif", OutputTokenType.Word),
            ),
        )

    /** `# elif {{0}} \n {{1}}` */
    private val sharedCodeFormattingTemplate19 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("#", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("elif", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} {{1}} ;` */
    private val sharedCodeFormattingTemplate20 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}}` */
    private val sharedCodeFormattingTemplate21 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `\{ {{0*}} \}` */
    private val sharedCodeFormattingTemplate22 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `/\* {{0}} *\/ {{1}}` */
    private val sharedCodeFormattingTemplate23 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("/*", OutputTokenType.Comment),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("*/", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} /\* {{1}} *\/` */
    private val sharedCodeFormattingTemplate24 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("/*", OutputTokenType.Comment),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("*/", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} const` */
    private val sharedCodeFormattingTemplate25 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("const", OutputTokenType.Word),
            ),
        )

    /** `{{0}} *` */
    private val sharedCodeFormattingTemplate26 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} < {{1*,}} >` */
    private val sharedCodeFormattingTemplate27 =
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

    /** `{{0}} ;` */
    private val sharedCodeFormattingTemplate28 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} : {{1}}` */
    private val sharedCodeFormattingTemplate29 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `goto {{0}} ;` */
    private val sharedCodeFormattingTemplate30 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("goto", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `return {{0}} ;` */
    private val sharedCodeFormattingTemplate31 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("return", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `return ;` */
    private val sharedCodeFormattingTemplate32 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("return", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `throw {{0}} ;` */
    private val sharedCodeFormattingTemplate33 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("throw", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `if ( {{0}} ) {{1}} else {{2}}` */
    private val sharedCodeFormattingTemplate34 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `if ( {{0}} ) {{1}}` */
    private val sharedCodeFormattingTemplate35 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `while ( {{0}} ) {{1}}` */
    private val sharedCodeFormattingTemplate36 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("while", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} [ {{1}} ]` */
    private val sharedCodeFormattingTemplate37 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}} ( {{1*,}} )` */
    private val sharedCodeFormattingTemplate38 =
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
    private val sharedCodeFormattingTemplate39 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}}` */
    private val sharedCodeFormattingTemplate40 =
        CodeFormattingTemplate.OneSubstitution(0)

    /** `{{0}} {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate41 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `( {{0}} ) {{1}}` */
    private val sharedCodeFormattingTemplate42 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `this` */
    private val sharedCodeFormattingTemplate43 =
        CodeFormattingTemplate.LiteralToken("this", OutputTokenType.Word)

    /** `{{0}} :: {{1}}` */
    private val sharedCodeFormattingTemplate44 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("::", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )
}
