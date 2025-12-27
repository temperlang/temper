@file:lang.temper.common.Generated("OutputGrammarCodeGenerator")
@file:Suppress("ktlint", "unused", "CascadeIf", "MagicNumber", "MemberNameEqualsClassName", "MemberVisibilityCanBePrivate")

package lang.temper.be.csharp
import lang.temper.ast.ChildMemberRelationships
import lang.temper.ast.OutData
import lang.temper.ast.OutTree
import lang.temper.ast.deepCopy
import lang.temper.be.BaseOutData
import lang.temper.be.BaseOutTree
import lang.temper.common.isNotEmpty
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
import lang.temper.type.TypeFormal

object CSharp {
    sealed interface Tree : OutTree<Tree> {
        override fun formattingHints(): FormattingHints = CSharpFormattingHints.getInstance()
        override val operatorDefinition: CSharpOperatorDefinition?
        override fun deepCopy(): Tree
    }
    sealed class BaseTree(
        pos: Position,
    ) : BaseOutTree<Tree>(pos), Tree
    sealed interface Data : OutData<Data> {
        override fun formattingHints(): FormattingHints = CSharpFormattingHints.getInstance()
        override val operatorDefinition: CSharpOperatorDefinition?
    }
    sealed class BaseData : BaseOutData<Data>(), Data

    enum class ModAccess : FormattableEnum {
        Private,
        Internal,
        Protected,
        ProtectedInternal,
        Public,
    }

    enum class ModNew : FormattableEnum {
        Implied,
        New,
    }

    enum class ModStatic : FormattableEnum {
        Instance,
        Static,
    }

    enum class ModTypeKind : FormattableEnum {
        Class,
        Interface,
        Struct,
    }

    enum class ModWritable : FormattableEnum {
        ReadOnly,
        ReadWrite,
    }

    enum class ModAccessorKind : FormattableEnum {
        Get,
        Set,
    }

    sealed interface Program : Tree {
        override fun deepCopy(): Program
    }

    class CompilationUnit(
        pos: Position,
        usings: Iterable<UsingDirective> = listOf(),
        attributes: Iterable<AttributeSection> = listOf(),
        decls: Iterable<NamespaceMemberDecl>,
    ) : BaseTree(pos), Program {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (attributes.isNotEmpty()) {
                    sharedCodeFormattingTemplate0
                } else {
                    sharedCodeFormattingTemplate1
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.usings)
                1 -> FormattableTreeGroup(this.attributes)
                2 -> FormattableTreeGroup(this.decls)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _usings: MutableList<UsingDirective> = mutableListOf()
        var usings: List<UsingDirective>
            get() = _usings
            set(newValue) { updateTreeConnections(_usings, newValue) }
        private val _attributes: MutableList<AttributeSection> = mutableListOf()
        var attributes: List<AttributeSection>
            get() = _attributes
            set(newValue) { updateTreeConnections(_attributes, newValue) }
        private val _decls: MutableList<NamespaceMemberDecl> = mutableListOf()
        var decls: List<NamespaceMemberDecl>
            get() = _decls
            set(newValue) { updateTreeConnections(_decls, newValue) }
        override fun deepCopy(): CompilationUnit {
            return CompilationUnit(pos, usings = this.usings.deepCopy(), attributes = this.attributes.deepCopy(), decls = this.decls.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is CompilationUnit && this.usings == other.usings && this.attributes == other.attributes && this.decls == other.decls
        }
        override fun hashCode(): Int {
            var hc = usings.hashCode()
            hc = 31 * hc + attributes.hashCode()
            hc = 31 * hc + decls.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._usings, usings)
            updateTreeConnections(this._attributes, attributes)
            updateTreeConnections(this._decls, decls)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as CompilationUnit).usings },
                { n -> (n as CompilationUnit).attributes },
                { n -> (n as CompilationUnit).decls },
            )
        }
    }

    sealed interface UsingDirective : Tree {
        override fun deepCopy(): UsingDirective
    }

    class AttributeSection(
        pos: Position,
        target: Identifier? = null,
        attributes: Iterable<Attribute>,
    ) : BaseTree(pos) {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (target != null) {
                    sharedCodeFormattingTemplate2
                } else {
                    sharedCodeFormattingTemplate3
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.target ?: FormattableTreeGroup.empty
                1 -> FormattableTreeGroup(this.attributes)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _target: Identifier?
        var target: Identifier?
            get() = _target
            set(newValue) { _target = updateTreeConnection(_target, newValue) }
        private val _attributes: MutableList<Attribute> = mutableListOf()
        var attributes: List<Attribute>
            get() = _attributes
            set(newValue) { updateTreeConnections(_attributes, newValue) }
        override fun deepCopy(): AttributeSection {
            return AttributeSection(pos, target = this.target?.deepCopy(), attributes = this.attributes.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AttributeSection && this.target == other.target && this.attributes == other.attributes
        }
        override fun hashCode(): Int {
            var hc = (target?.hashCode() ?: 0)
            hc = 31 * hc + attributes.hashCode()
            return hc
        }
        init {
            this._target = updateTreeConnection(null, target)
            updateTreeConnections(this._attributes, attributes)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AttributeSection).target },
                { n -> (n as AttributeSection).attributes },
            )
        }
    }

    sealed interface NamespaceMemberDecl : Tree {
        override fun deepCopy(): NamespaceMemberDecl
    }

    class UsingNamespaceDirective(
        pos: Position,
        alias: Identifier? = null,
        ids: Iterable<Identifier>,
    ) : BaseTree(pos), UsingDirective {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (alias != null) {
                    sharedCodeFormattingTemplate4
                } else {
                    sharedCodeFormattingTemplate5
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.alias ?: FormattableTreeGroup.empty
                1 -> FormattableTreeGroup(this.ids)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _alias: Identifier?
        var alias: Identifier?
            get() = _alias
            set(newValue) { _alias = updateTreeConnection(_alias, newValue) }
        private val _ids: MutableList<Identifier> = mutableListOf()
        var ids: List<Identifier>
            get() = _ids
            set(newValue) { updateTreeConnections(_ids, newValue) }
        override fun deepCopy(): UsingNamespaceDirective {
            return UsingNamespaceDirective(pos, alias = this.alias?.deepCopy(), ids = this.ids.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is UsingNamespaceDirective && this.alias == other.alias && this.ids == other.ids
        }
        override fun hashCode(): Int {
            var hc = (alias?.hashCode() ?: 0)
            hc = 31 * hc + ids.hashCode()
            return hc
        }
        init {
            this._alias = updateTreeConnection(null, alias)
            updateTreeConnections(this._ids, ids)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as UsingNamespaceDirective).alias },
                { n -> (n as UsingNamespaceDirective).ids },
            )
        }
    }

    sealed interface TypeParameter : Tree {
        override fun deepCopy(): TypeParameter
    }

    /** The name of a declared type like `C` or `System.D` */
    sealed interface UnboundTypeName : Tree {
        override fun deepCopy(): UnboundTypeName
    }

    sealed interface Arg : Tree {
        override fun deepCopy(): Arg
    }

    sealed interface ArgValue : Tree {
        override fun deepCopy(): ArgValue
    }

    sealed interface Expression : Tree, Arg, ArgValue {
        override fun deepCopy(): Expression
    }

    sealed interface PrimaryExpression : Tree, Expression {
        override fun deepCopy(): PrimaryExpression
    }

    class Identifier(
        pos: Position,
        var outName: OutName,
    ) : BaseTree(pos), TypeParameter, UnboundTypeName, PrimaryExpression {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.name(outName, inOperatorPosition = false)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): Identifier {
            return Identifier(pos, outName = this.outName)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Identifier && this.outName == other.outName
        }
        override fun hashCode(): Int {
            return outName.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class Attribute(
        pos: Position,
        name: UnboundTypeName,
        args: Iterable<Arg> = listOf(),
    ) : BaseTree(pos) {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (args.isNotEmpty()) {
                    sharedCodeFormattingTemplate6
                } else {
                    sharedCodeFormattingTemplate7
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name
                1 -> FormattableTreeGroup(this.args)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _name: UnboundTypeName
        var name: UnboundTypeName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private val _args: MutableList<Arg> = mutableListOf()
        var args: List<Arg>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): Attribute {
            return Attribute(pos, name = this.name.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Attribute && this.name == other.name && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = name.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._name = updateTreeConnection(null, name)
            updateTreeConnections(this._args, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Attribute).name },
                { n -> (n as Attribute).args },
            )
        }
    }

    class NamespaceDecl(
        pos: Position,
        names: Iterable<Identifier>,
        usings: Iterable<UsingDirective> = listOf(),
        decls: Iterable<NamespaceMemberDecl>,
    ) : BaseTree(pos), NamespaceMemberDecl {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate8
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.names)
                1 -> FormattableTreeGroup(this.usings)
                2 -> FormattableTreeGroup(this.decls)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _names: MutableList<Identifier> = mutableListOf()
        var names: List<Identifier>
            get() = _names
            set(newValue) { updateTreeConnections(_names, newValue) }
        private val _usings: MutableList<UsingDirective> = mutableListOf()
        var usings: List<UsingDirective>
            get() = _usings
            set(newValue) { updateTreeConnections(_usings, newValue) }
        private val _decls: MutableList<NamespaceMemberDecl> = mutableListOf()
        var decls: List<NamespaceMemberDecl>
            get() = _decls
            set(newValue) { updateTreeConnections(_decls, newValue) }
        override fun deepCopy(): NamespaceDecl {
            return NamespaceDecl(pos, names = this.names.deepCopy(), usings = this.usings.deepCopy(), decls = this.decls.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is NamespaceDecl && this.names == other.names && this.usings == other.usings && this.decls == other.decls
        }
        override fun hashCode(): Int {
            var hc = names.hashCode()
            hc = 31 * hc + usings.hashCode()
            hc = 31 * hc + decls.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._names, names)
            updateTreeConnections(this._usings, usings)
            updateTreeConnections(this._decls, decls)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as NamespaceDecl).names },
                { n -> (n as NamespaceDecl).usings },
                { n -> (n as NamespaceDecl).decls },
            )
        }
    }

    class TypeDecl(
        pos: Position,
        attributes: Iterable<AttributeSection> = listOf(),
        mods: TypeModifiers,
        id: Identifier,
        typeParameters: Iterable<TypeParameter> = listOf(),
        baseTypes: Iterable<Type> = listOf(),
        whereConstraints: WhereConstraints = WhereConstraints(pos.leftEdge, listOf()),
        members: Iterable<ClassMember>,
    ) : BaseTree(pos), NamespaceMemberDecl {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (attributes.isNotEmpty() && typeParameters.isNotEmpty() && baseTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate9
                } else if (attributes.isNotEmpty() && typeParameters.isNotEmpty()) {
                    sharedCodeFormattingTemplate10
                } else if (attributes.isNotEmpty() && baseTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate11
                } else if (attributes.isNotEmpty()) {
                    sharedCodeFormattingTemplate12
                } else if (typeParameters.isNotEmpty() && baseTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate13
                } else if (typeParameters.isNotEmpty()) {
                    sharedCodeFormattingTemplate14
                } else if (baseTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate15
                } else {
                    sharedCodeFormattingTemplate16
                }
        override val formatElementCount
            get() = 7
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.attributes)
                1 -> this.mods
                2 -> this.id
                3 -> FormattableTreeGroup(this.typeParameters)
                4 -> FormattableTreeGroup(this.baseTypes)
                5 -> this.whereConstraints
                6 -> FormattableTreeGroup(this.members)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _attributes: MutableList<AttributeSection> = mutableListOf()
        var attributes: List<AttributeSection>
            get() = _attributes
            set(newValue) { updateTreeConnections(_attributes, newValue) }
        private var _mods: TypeModifiers
        var mods: TypeModifiers
            get() = _mods
            set(newValue) { _mods = updateTreeConnection(_mods, newValue) }
        private var _id: Identifier
        var id: Identifier
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private val _typeParameters: MutableList<TypeParameter> = mutableListOf()
        var typeParameters: List<TypeParameter>
            get() = _typeParameters
            set(newValue) { updateTreeConnections(_typeParameters, newValue) }
        private val _baseTypes: MutableList<Type> = mutableListOf()
        var baseTypes: List<Type>
            get() = _baseTypes
            set(newValue) { updateTreeConnections(_baseTypes, newValue) }
        private var _whereConstraints: WhereConstraints
        var whereConstraints: WhereConstraints
            get() = _whereConstraints
            set(newValue) { _whereConstraints = updateTreeConnection(_whereConstraints, newValue) }
        private val _members: MutableList<ClassMember> = mutableListOf()
        var members: List<ClassMember>
            get() = _members
            set(newValue) { updateTreeConnections(_members, newValue) }
        override fun deepCopy(): TypeDecl {
            return TypeDecl(pos, attributes = this.attributes.deepCopy(), mods = this.mods.deepCopy(), id = this.id.deepCopy(), typeParameters = this.typeParameters.deepCopy(), baseTypes = this.baseTypes.deepCopy(), whereConstraints = this.whereConstraints.deepCopy(), members = this.members.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeDecl && this.attributes == other.attributes && this.mods == other.mods && this.id == other.id && this.typeParameters == other.typeParameters && this.baseTypes == other.baseTypes && this.whereConstraints == other.whereConstraints && this.members == other.members
        }
        override fun hashCode(): Int {
            var hc = attributes.hashCode()
            hc = 31 * hc + mods.hashCode()
            hc = 31 * hc + id.hashCode()
            hc = 31 * hc + typeParameters.hashCode()
            hc = 31 * hc + baseTypes.hashCode()
            hc = 31 * hc + whereConstraints.hashCode()
            hc = 31 * hc + members.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._attributes, attributes)
            this._mods = updateTreeConnection(null, mods)
            this._id = updateTreeConnection(null, id)
            updateTreeConnections(this._typeParameters, typeParameters)
            updateTreeConnections(this._baseTypes, baseTypes)
            this._whereConstraints = updateTreeConnection(null, whereConstraints)
            updateTreeConnections(this._members, members)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeDecl).attributes },
                { n -> (n as TypeDecl).mods },
                { n -> (n as TypeDecl).id },
                { n -> (n as TypeDecl).typeParameters },
                { n -> (n as TypeDecl).baseTypes },
                { n -> (n as TypeDecl).whereConstraints },
                { n -> (n as TypeDecl).members },
            )
        }
    }

    class TypeModifiers(
        pos: Position,
        var modAccess: ModAccess,
        var modStatic: ModStatic = ModStatic.Instance,
        var modNew: ModNew = ModNew.Implied,
        var modTypeKind: ModTypeKind,
    ) : BaseTree(pos) {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            modAccess.emit(tokenSink, default = ModAccess.Internal)
            modStatic.emit(tokenSink)
            modNew.emit(tokenSink)
            modTypeKind.emit(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): TypeModifiers {
            return TypeModifiers(pos, modAccess = this.modAccess, modStatic = this.modStatic, modNew = this.modNew, modTypeKind = this.modTypeKind)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeModifiers && this.modAccess == other.modAccess && this.modStatic == other.modStatic && this.modNew == other.modNew && this.modTypeKind == other.modTypeKind
        }
        override fun hashCode(): Int {
            var hc = modAccess.hashCode()
            hc = 31 * hc + modStatic.hashCode()
            hc = 31 * hc + modNew.hashCode()
            hc = 31 * hc + modTypeKind.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    sealed interface Type : Tree {
        override fun deepCopy(): Type
    }

    /**
     * Constraints for zero or more [TypeParameter]s.
     *
     * ```
     * where U : A
     * where T : B, C
     * ```
     */
    class WhereConstraints(
        pos: Position,
        constraints: Iterable<WhereConstraintList>,
    ) : BaseTree(pos) {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate17
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.constraints)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _constraints: MutableList<WhereConstraintList> = mutableListOf()
        var constraints: List<WhereConstraintList>
            get() = _constraints
            set(newValue) { updateTreeConnections(_constraints, newValue) }
        override fun deepCopy(): WhereConstraints {
            return WhereConstraints(pos, constraints = this.constraints.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is WhereConstraints && this.constraints == other.constraints
        }
        override fun hashCode(): Int {
            return constraints.hashCode()
        }
        init {
            updateTreeConnections(this._constraints, constraints)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as WhereConstraints).constraints },
            )
        }
    }

    sealed interface ClassMember : Tree {
        override fun deepCopy(): ClassMember
    }

    /**
     * A group of one or more [WhereConstraint]s applied to a particular [TypeParameter]
     *
     * ```
     * where U : A
     * ```
     */
    class WhereConstraintList(
        pos: Position,
        typeParameterName: Identifier,
        constraints: Iterable<WhereConstraint>,
    ) : BaseTree(pos) {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate18
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.typeParameterName
                1 -> FormattableTreeGroup(this.constraints)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _typeParameterName: Identifier
        var typeParameterName: Identifier
            get() = _typeParameterName
            set(newValue) { _typeParameterName = updateTreeConnection(_typeParameterName, newValue) }
        private val _constraints: MutableList<WhereConstraint> = mutableListOf()
        var constraints: List<WhereConstraint>
            get() = _constraints
            set(newValue) { updateTreeConnections(_constraints, newValue) }
        override fun deepCopy(): WhereConstraintList {
            return WhereConstraintList(pos, typeParameterName = this.typeParameterName.deepCopy(), constraints = this.constraints.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is WhereConstraintList && this.typeParameterName == other.typeParameterName && this.constraints == other.constraints
        }
        override fun hashCode(): Int {
            var hc = typeParameterName.hashCode()
            hc = 31 * hc + constraints.hashCode()
            return hc
        }
        init {
            this._typeParameterName = updateTreeConnection(null, typeParameterName)
            updateTreeConnections(this._constraints, constraints)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as WhereConstraintList).typeParameterName },
                { n -> (n as WhereConstraintList).constraints },
            )
        }
    }

    /**
     * A constraint on a [TypeParameter].
     * learn.microsoft.com/en-us/dotnet/csharp/programming-guide/generics/constraints-on-type-parameters
     */
    sealed interface WhereConstraint : Tree {
        override fun deepCopy(): WhereConstraint
    }

    class StructWhereConstraint(
        pos: Position,
    ) : BaseTree(pos), WhereConstraint {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate19
        override val formatElementCount
            get() = 0
        override fun deepCopy(): StructWhereConstraint {
            return StructWhereConstraint(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StructWhereConstraint
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class ClassWhereConstraint(
        pos: Position,
        var allowNull: Boolean,
    ) : BaseTree(pos), WhereConstraint {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (allowNull) {
                    sharedCodeFormattingTemplate20
                } else {
                    sharedCodeFormattingTemplate21
                }
        override val formatElementCount
            get() = 0
        override fun deepCopy(): ClassWhereConstraint {
            return ClassWhereConstraint(pos, allowNull = this.allowNull)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ClassWhereConstraint && this.allowNull == other.allowNull
        }
        override fun hashCode(): Int {
            return allowNull.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class NotNullWhereConstraint(
        pos: Position,
    ) : BaseTree(pos), WhereConstraint {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate22
        override val formatElementCount
            get() = 0
        override fun deepCopy(): NotNullWhereConstraint {
            return NotNullWhereConstraint(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is NotNullWhereConstraint
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class UnmanagedWhereConstraint(
        pos: Position,
    ) : BaseTree(pos), WhereConstraint {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate23
        override val formatElementCount
            get() = 0
        override fun deepCopy(): UnmanagedWhereConstraint {
            return UnmanagedWhereConstraint(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is UnmanagedWhereConstraint
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class ZeroArgConstructorWhereConstraint(
        pos: Position,
    ) : BaseTree(pos), WhereConstraint {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate24
        override val formatElementCount
            get() = 0
        override fun deepCopy(): ZeroArgConstructorWhereConstraint {
            return ZeroArgConstructorWhereConstraint(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ZeroArgConstructorWhereConstraint
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class UpperBoundWhereConstraint(
        pos: Position,
        upperBound: Type,
    ) : BaseTree(pos), WhereConstraint {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate7
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.upperBound
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _upperBound: Type
        var upperBound: Type
            get() = _upperBound
            set(newValue) { _upperBound = updateTreeConnection(_upperBound, newValue) }
        override fun deepCopy(): UpperBoundWhereConstraint {
            return UpperBoundWhereConstraint(pos, upperBound = this.upperBound.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is UpperBoundWhereConstraint && this.upperBound == other.upperBound
        }
        override fun hashCode(): Int {
            return upperBound.hashCode()
        }
        init {
            this._upperBound = updateTreeConnection(null, upperBound)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as UpperBoundWhereConstraint).upperBound },
            )
        }
    }

    class DefaultWhereConstraint(
        pos: Position,
    ) : BaseTree(pos), WhereConstraint {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate25
        override val formatElementCount
            get() = 0
        override fun deepCopy(): DefaultWhereConstraint {
            return DefaultWhereConstraint(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is DefaultWhereConstraint
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class AllowsRefStructWhereConstraint(
        pos: Position,
    ) : BaseTree(pos), WhereConstraint {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate26
        override val formatElementCount
            get() = 0
        override fun deepCopy(): AllowsRefStructWhereConstraint {
            return AllowsRefStructWhereConstraint(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AllowsRefStructWhereConstraint
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class FieldDecl(
        pos: Position,
        attributes: Iterable<AttributeSection> = listOf(),
        mods: FieldModifiers,
        type: Type,
        variables: Iterable<VariableDeclarator>,
    ) : BaseTree(pos), ClassMember {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (attributes.isNotEmpty()) {
                    sharedCodeFormattingTemplate27
                } else {
                    sharedCodeFormattingTemplate28
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.attributes)
                1 -> this.mods
                2 -> this.type
                3 -> FormattableTreeGroup(this.variables)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _attributes: MutableList<AttributeSection> = mutableListOf()
        var attributes: List<AttributeSection>
            get() = _attributes
            set(newValue) { updateTreeConnections(_attributes, newValue) }
        private var _mods: FieldModifiers
        var mods: FieldModifiers
            get() = _mods
            set(newValue) { _mods = updateTreeConnection(_mods, newValue) }
        private var _type: Type
        var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private val _variables: MutableList<VariableDeclarator> = mutableListOf()
        var variables: List<VariableDeclarator>
            get() = _variables
            set(newValue) { updateTreeConnections(_variables, newValue) }
        override fun deepCopy(): FieldDecl {
            return FieldDecl(pos, attributes = this.attributes.deepCopy(), mods = this.mods.deepCopy(), type = this.type.deepCopy(), variables = this.variables.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FieldDecl && this.attributes == other.attributes && this.mods == other.mods && this.type == other.type && this.variables == other.variables
        }
        override fun hashCode(): Int {
            var hc = attributes.hashCode()
            hc = 31 * hc + mods.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + variables.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._attributes, attributes)
            this._mods = updateTreeConnection(null, mods)
            this._type = updateTreeConnection(null, type)
            updateTreeConnections(this._variables, variables)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FieldDecl).attributes },
                { n -> (n as FieldDecl).mods },
                { n -> (n as FieldDecl).type },
                { n -> (n as FieldDecl).variables },
            )
        }
    }

    sealed interface Statement : Tree {
        override fun deepCopy(): Statement
    }

    class MethodDecl(
        pos: Position,
        attributes: Iterable<AttributeSection> = listOf(),
        mods: MethodModifiers?,
        result: Type?,
        id: Identifier,
        typeParameters: Iterable<TypeParameter> = listOf(),
        parameters: Iterable<MethodParameter>,
        whereConstraints: WhereConstraints = WhereConstraints(pos.leftEdge, listOf()),
        body: BlockStatement?,
    ) : BaseTree(pos), ClassMember, Statement {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (attributes.isNotEmpty() && mods != null && result != null && typeParameters.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate29
                } else if (attributes.isNotEmpty() && mods != null && result != null && typeParameters.isNotEmpty()) {
                    sharedCodeFormattingTemplate30
                } else if (attributes.isNotEmpty() && mods != null && result != null && body != null) {
                    sharedCodeFormattingTemplate31
                } else if (attributes.isNotEmpty() && mods != null && result != null) {
                    sharedCodeFormattingTemplate32
                } else if (attributes.isNotEmpty() && mods != null && typeParameters.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate33
                } else if (attributes.isNotEmpty() && mods != null && typeParameters.isNotEmpty()) {
                    sharedCodeFormattingTemplate34
                } else if (attributes.isNotEmpty() && mods != null && body != null) {
                    sharedCodeFormattingTemplate35
                } else if (attributes.isNotEmpty() && mods != null) {
                    sharedCodeFormattingTemplate36
                } else if (attributes.isNotEmpty() && result != null && typeParameters.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate37
                } else if (attributes.isNotEmpty() && result != null && typeParameters.isNotEmpty()) {
                    sharedCodeFormattingTemplate38
                } else if (attributes.isNotEmpty() && result != null && body != null) {
                    sharedCodeFormattingTemplate39
                } else if (attributes.isNotEmpty() && result != null) {
                    sharedCodeFormattingTemplate40
                } else if (attributes.isNotEmpty() && typeParameters.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate41
                } else if (attributes.isNotEmpty() && typeParameters.isNotEmpty()) {
                    sharedCodeFormattingTemplate42
                } else if (attributes.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate43
                } else if (attributes.isNotEmpty()) {
                    sharedCodeFormattingTemplate44
                } else if (mods != null && result != null && typeParameters.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate45
                } else if (mods != null && result != null && typeParameters.isNotEmpty()) {
                    sharedCodeFormattingTemplate46
                } else if (mods != null && result != null && body != null) {
                    sharedCodeFormattingTemplate47
                } else if (mods != null && result != null) {
                    sharedCodeFormattingTemplate48
                } else if (mods != null && typeParameters.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate49
                } else if (mods != null && typeParameters.isNotEmpty()) {
                    sharedCodeFormattingTemplate50
                } else if (mods != null && body != null) {
                    sharedCodeFormattingTemplate51
                } else if (mods != null) {
                    sharedCodeFormattingTemplate52
                } else if (result != null && typeParameters.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate53
                } else if (result != null && typeParameters.isNotEmpty()) {
                    sharedCodeFormattingTemplate54
                } else if (result != null && body != null) {
                    sharedCodeFormattingTemplate55
                } else if (result != null) {
                    sharedCodeFormattingTemplate56
                } else if (typeParameters.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate57
                } else if (typeParameters.isNotEmpty()) {
                    sharedCodeFormattingTemplate58
                } else if (body != null) {
                    sharedCodeFormattingTemplate59
                } else {
                    sharedCodeFormattingTemplate60
                }
        override val formatElementCount
            get() = 8
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.attributes)
                1 -> this.mods ?: FormattableTreeGroup.empty
                2 -> this.result ?: FormattableTreeGroup.empty
                3 -> this.id
                4 -> FormattableTreeGroup(this.typeParameters)
                5 -> FormattableTreeGroup(this.parameters)
                6 -> this.whereConstraints
                7 -> this.body ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _attributes: MutableList<AttributeSection> = mutableListOf()
        var attributes: List<AttributeSection>
            get() = _attributes
            set(newValue) { updateTreeConnections(_attributes, newValue) }
        private var _mods: MethodModifiers?
        var mods: MethodModifiers?
            get() = _mods
            set(newValue) { _mods = updateTreeConnection(_mods, newValue) }
        private var _result: Type?
        var result: Type?
            get() = _result
            set(newValue) { _result = updateTreeConnection(_result, newValue) }
        private var _id: Identifier
        var id: Identifier
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private val _typeParameters: MutableList<TypeParameter> = mutableListOf()
        var typeParameters: List<TypeParameter>
            get() = _typeParameters
            set(newValue) { updateTreeConnections(_typeParameters, newValue) }
        private val _parameters: MutableList<MethodParameter> = mutableListOf()
        var parameters: List<MethodParameter>
            get() = _parameters
            set(newValue) { updateTreeConnections(_parameters, newValue) }
        private var _whereConstraints: WhereConstraints
        var whereConstraints: WhereConstraints
            get() = _whereConstraints
            set(newValue) { _whereConstraints = updateTreeConnection(_whereConstraints, newValue) }
        private var _body: BlockStatement?
        var body: BlockStatement?
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): MethodDecl {
            return MethodDecl(pos, attributes = this.attributes.deepCopy(), mods = this.mods?.deepCopy(), result = this.result?.deepCopy(), id = this.id.deepCopy(), typeParameters = this.typeParameters.deepCopy(), parameters = this.parameters.deepCopy(), whereConstraints = this.whereConstraints.deepCopy(), body = this.body?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MethodDecl && this.attributes == other.attributes && this.mods == other.mods && this.result == other.result && this.id == other.id && this.typeParameters == other.typeParameters && this.parameters == other.parameters && this.whereConstraints == other.whereConstraints && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = attributes.hashCode()
            hc = 31 * hc + (mods?.hashCode() ?: 0)
            hc = 31 * hc + (result?.hashCode() ?: 0)
            hc = 31 * hc + id.hashCode()
            hc = 31 * hc + typeParameters.hashCode()
            hc = 31 * hc + parameters.hashCode()
            hc = 31 * hc + whereConstraints.hashCode()
            hc = 31 * hc + (body?.hashCode() ?: 0)
            return hc
        }
        init {
            updateTreeConnections(this._attributes, attributes)
            this._mods = updateTreeConnection(null, mods)
            this._result = updateTreeConnection(null, result)
            this._id = updateTreeConnection(null, id)
            updateTreeConnections(this._typeParameters, typeParameters)
            updateTreeConnections(this._parameters, parameters)
            this._whereConstraints = updateTreeConnection(null, whereConstraints)
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as MethodDecl).attributes },
                { n -> (n as MethodDecl).mods },
                { n -> (n as MethodDecl).result },
                { n -> (n as MethodDecl).id },
                { n -> (n as MethodDecl).typeParameters },
                { n -> (n as MethodDecl).parameters },
                { n -> (n as MethodDecl).whereConstraints },
                { n -> (n as MethodDecl).body },
            )
        }
    }

    class PropertyDecl(
        pos: Position,
        attributes: Iterable<AttributeSection> = listOf(),
        mods: MethodModifiers,
        type: Type,
        id: Identifier,
        accessors: Iterable<PropertyAccessor>,
    ) : BaseTree(pos), ClassMember {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (attributes.isNotEmpty()) {
                    sharedCodeFormattingTemplate61
                } else {
                    sharedCodeFormattingTemplate62
                }
        override val formatElementCount
            get() = 5
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.attributes)
                1 -> this.mods
                2 -> this.type
                3 -> this.id
                4 -> FormattableTreeGroup(this.accessors)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _attributes: MutableList<AttributeSection> = mutableListOf()
        var attributes: List<AttributeSection>
            get() = _attributes
            set(newValue) { updateTreeConnections(_attributes, newValue) }
        private var _mods: MethodModifiers
        var mods: MethodModifiers
            get() = _mods
            set(newValue) { _mods = updateTreeConnection(_mods, newValue) }
        private var _type: Type
        var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _id: Identifier
        var id: Identifier
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private val _accessors: MutableList<PropertyAccessor> = mutableListOf()
        var accessors: List<PropertyAccessor>
            get() = _accessors
            set(newValue) { updateTreeConnections(_accessors, newValue) }
        override fun deepCopy(): PropertyDecl {
            return PropertyDecl(pos, attributes = this.attributes.deepCopy(), mods = this.mods.deepCopy(), type = this.type.deepCopy(), id = this.id.deepCopy(), accessors = this.accessors.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PropertyDecl && this.attributes == other.attributes && this.mods == other.mods && this.type == other.type && this.id == other.id && this.accessors == other.accessors
        }
        override fun hashCode(): Int {
            var hc = attributes.hashCode()
            hc = 31 * hc + mods.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + id.hashCode()
            hc = 31 * hc + accessors.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._attributes, attributes)
            this._mods = updateTreeConnection(null, mods)
            this._type = updateTreeConnection(null, type)
            this._id = updateTreeConnection(null, id)
            updateTreeConnections(this._accessors, accessors)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as PropertyDecl).attributes },
                { n -> (n as PropertyDecl).mods },
                { n -> (n as PropertyDecl).type },
                { n -> (n as PropertyDecl).id },
                { n -> (n as PropertyDecl).accessors },
            )
        }
    }

    class StaticConstructorDecl(
        pos: Position,
        attributes: Iterable<AttributeSection> = listOf(),
        id: Identifier,
        body: BlockStatement?,
    ) : BaseTree(pos), ClassMember {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (attributes.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate63
                } else if (attributes.isNotEmpty()) {
                    sharedCodeFormattingTemplate64
                } else if (body != null) {
                    sharedCodeFormattingTemplate65
                } else {
                    sharedCodeFormattingTemplate66
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.attributes)
                1 -> this.id
                2 -> this.body ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _attributes: MutableList<AttributeSection> = mutableListOf()
        var attributes: List<AttributeSection>
            get() = _attributes
            set(newValue) { updateTreeConnections(_attributes, newValue) }
        private var _id: Identifier
        var id: Identifier
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private var _body: BlockStatement?
        var body: BlockStatement?
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): StaticConstructorDecl {
            return StaticConstructorDecl(pos, attributes = this.attributes.deepCopy(), id = this.id.deepCopy(), body = this.body?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StaticConstructorDecl && this.attributes == other.attributes && this.id == other.id && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = attributes.hashCode()
            hc = 31 * hc + id.hashCode()
            hc = 31 * hc + (body?.hashCode() ?: 0)
            return hc
        }
        init {
            updateTreeConnections(this._attributes, attributes)
            this._id = updateTreeConnection(null, id)
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as StaticConstructorDecl).attributes },
                { n -> (n as StaticConstructorDecl).id },
                { n -> (n as StaticConstructorDecl).body },
            )
        }
    }

    class FieldModifiers(
        pos: Position,
        var modAccess: ModAccess,
        var modNew: ModNew = ModNew.Implied,
        var modStatic: ModStatic = ModStatic.Instance,
        var modWritable: ModWritable = ModWritable.ReadWrite,
    ) : BaseTree(pos) {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            modAccess.emit(tokenSink, default = ModAccess.Private)
            modNew.emit(tokenSink)
            modStatic.emit(tokenSink)
            modWritable.emit(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): FieldModifiers {
            return FieldModifiers(pos, modAccess = this.modAccess, modNew = this.modNew, modStatic = this.modStatic, modWritable = this.modWritable)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FieldModifiers && this.modAccess == other.modAccess && this.modNew == other.modNew && this.modStatic == other.modStatic && this.modWritable == other.modWritable
        }
        override fun hashCode(): Int {
            var hc = modAccess.hashCode()
            hc = 31 * hc + modNew.hashCode()
            hc = 31 * hc + modStatic.hashCode()
            hc = 31 * hc + modWritable.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class VariableDeclarator(
        pos: Position,
        variable: Identifier,
        initializer: Expression?,
    ) : BaseTree(pos) {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (initializer != null) {
                    sharedCodeFormattingTemplate67
                } else {
                    sharedCodeFormattingTemplate7
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.variable
                1 -> this.initializer ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _variable: Identifier
        var variable: Identifier
            get() = _variable
            set(newValue) { _variable = updateTreeConnection(_variable, newValue) }
        private var _initializer: Expression?
        var initializer: Expression?
            get() = _initializer
            set(newValue) { _initializer = updateTreeConnection(_initializer, newValue) }
        override fun deepCopy(): VariableDeclarator {
            return VariableDeclarator(pos, variable = this.variable.deepCopy(), initializer = this.initializer?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is VariableDeclarator && this.variable == other.variable && this.initializer == other.initializer
        }
        override fun hashCode(): Int {
            var hc = variable.hashCode()
            hc = 31 * hc + (initializer?.hashCode() ?: 0)
            return hc
        }
        init {
            this._variable = updateTreeConnection(null, variable)
            this._initializer = updateTreeConnection(null, initializer)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as VariableDeclarator).variable },
                { n -> (n as VariableDeclarator).initializer },
            )
        }
    }

    class MethodModifiers(
        pos: Position,
        var modAccess: ModAccess,
        var modStatic: ModStatic = ModStatic.Instance,
        var modNew: ModNew = ModNew.Implied,
    ) : BaseTree(pos) {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            modAccess.emit(
                tokenSink,
                default = when ((parent?.parent as? TypeDecl)?.mods?.modTypeKind) {
                    ModTypeKind.Interface -> ModAccess.Public
                    else -> ModAccess.Private
                },
            )
            modStatic.emit(tokenSink)
            modNew.emit(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): MethodModifiers {
            return MethodModifiers(pos, modAccess = this.modAccess, modStatic = this.modStatic, modNew = this.modNew)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MethodModifiers && this.modAccess == other.modAccess && this.modStatic == other.modStatic && this.modNew == other.modNew
        }
        override fun hashCode(): Int {
            var hc = modAccess.hashCode()
            hc = 31 * hc + modStatic.hashCode()
            hc = 31 * hc + modNew.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    sealed interface MethodParameter : Tree {
        val type: Type
        val name: Identifier
        override fun deepCopy(): MethodParameter
    }

    class BlockStatement(
        pos: Position,
        statements: Iterable<Statement>,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate68
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.statements)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _statements: MutableList<Statement> = mutableListOf()
        var statements: List<Statement>
            get() = _statements
            set(newValue) { updateTreeConnections(_statements, newValue) }
        override fun deepCopy(): BlockStatement {
            return BlockStatement(pos, statements = this.statements.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BlockStatement && this.statements == other.statements
        }
        override fun hashCode(): Int {
            return statements.hashCode()
        }
        init {
            updateTreeConnections(this._statements, statements)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as BlockStatement).statements },
            )
        }
    }

    class FixedParameter(
        pos: Position,
        type: Type,
        name: Identifier,
        defaultValue: Expression? = null,
    ) : BaseTree(pos), MethodParameter {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (defaultValue != null) {
                    sharedCodeFormattingTemplate69
                } else {
                    sharedCodeFormattingTemplate70
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type
                1 -> this.name
                2 -> this.defaultValue ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: Type
        override var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _name: Identifier
        override var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _defaultValue: Expression?
        var defaultValue: Expression?
            get() = _defaultValue
            set(newValue) { _defaultValue = updateTreeConnection(_defaultValue, newValue) }
        override fun deepCopy(): FixedParameter {
            return FixedParameter(pos, type = this.type.deepCopy(), name = this.name.deepCopy(), defaultValue = this.defaultValue?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FixedParameter && this.type == other.type && this.name == other.name && this.defaultValue == other.defaultValue
        }
        override fun hashCode(): Int {
            var hc = type.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + (defaultValue?.hashCode() ?: 0)
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            this._name = updateTreeConnection(null, name)
            this._defaultValue = updateTreeConnection(null, defaultValue)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FixedParameter).type },
                { n -> (n as FixedParameter).name },
                { n -> (n as FixedParameter).defaultValue },
            )
        }
    }

    class ParameterArray(
        pos: Position,
        type: Type,
        name: Identifier,
    ) : BaseTree(pos), MethodParameter {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate71
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
        override var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _name: Identifier
        override var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override fun deepCopy(): ParameterArray {
            return ParameterArray(pos, type = this.type.deepCopy(), name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ParameterArray && this.type == other.type && this.name == other.name
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
                { n -> (n as ParameterArray).type },
                { n -> (n as ParameterArray).name },
            )
        }
    }

    class PropertyAccessor(
        pos: Position,
        mods: PropertyAccessorModifiers,
        body: BlockStatement?,
    ) : BaseTree(pos) {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (body != null) {
                    sharedCodeFormattingTemplate70
                } else {
                    sharedCodeFormattingTemplate72
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.mods
                1 -> this.body ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _mods: PropertyAccessorModifiers
        var mods: PropertyAccessorModifiers
            get() = _mods
            set(newValue) { _mods = updateTreeConnection(_mods, newValue) }
        private var _body: BlockStatement?
        var body: BlockStatement?
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): PropertyAccessor {
            return PropertyAccessor(pos, mods = this.mods.deepCopy(), body = this.body?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PropertyAccessor && this.mods == other.mods && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = mods.hashCode()
            hc = 31 * hc + (body?.hashCode() ?: 0)
            return hc
        }
        init {
            this._mods = updateTreeConnection(null, mods)
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as PropertyAccessor).mods },
                { n -> (n as PropertyAccessor).body },
            )
        }
    }

    class PropertyAccessorModifiers(
        pos: Position,
        var modAccess: ModAccess?,
        var modAccessorKind: ModAccessorKind,
    ) : BaseTree(pos) {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            modAccess?.emit(tokenSink)
            modAccessorKind.emit(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): PropertyAccessorModifiers {
            return PropertyAccessorModifiers(pos, modAccess = this.modAccess, modAccessorKind = this.modAccessorKind)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PropertyAccessorModifiers && this.modAccess == other.modAccess && this.modAccessorKind == other.modAccessorKind
        }
        override fun hashCode(): Int {
            var hc = (modAccess?.hashCode() ?: 0)
            hc = 31 * hc + modAccessorKind.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    sealed interface NonNullableType : Tree, Type, PrimaryExpression {
        override fun deepCopy(): NonNullableType
    }

    class NullableType(
        pos: Position,
        type: NonNullableType,
    ) : BaseTree(pos), Type {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate73
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
        private var _type: NonNullableType
        var type: NonNullableType
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        override fun deepCopy(): NullableType {
            return NullableType(pos, type = this.type.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is NullableType && this.type == other.type
        }
        override fun hashCode(): Int {
            return type.hashCode()
        }
        init {
            this._type = updateTreeConnection(null, type)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as NullableType).type },
            )
        }
        constructor(type: NonNullableType) : this(type.pos, type)
    }

    /** A reference to a type argument like `<T>` */
    class TypeArgRef(
        pos: Position,
        name: Identifier,
        var definition: TypeFormal?,
    ) : BaseTree(pos), Type {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate7
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
        private var _name: Identifier
        var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override fun deepCopy(): TypeArgRef {
            return TypeArgRef(pos, name = this.name.deepCopy(), definition = this.definition)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeArgRef && this.name == other.name && this.definition == other.definition
        }
        override fun hashCode(): Int {
            var hc = name.hashCode()
            hc = 31 * hc + (definition?.hashCode() ?: 0)
            return hc
        }
        init {
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeArgRef).name },
            )
        }
        constructor(name: Identifier, definition: TypeFormal?) : this(name.pos, name, definition)
    }

    /** A named type with zero type parameters */
    class UnboundType(
        pos: Position,
        name: UnboundTypeName,
    ) : BaseTree(pos), NonNullableType {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate7
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
        private var _name: UnboundTypeName
        var name: UnboundTypeName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override fun deepCopy(): UnboundType {
            return UnboundType(pos, name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is UnboundType && this.name == other.name
        }
        override fun hashCode(): Int {
            return name.hashCode()
        }
        init {
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as UnboundType).name },
            )
        }
        constructor(name: UnboundTypeName) : this(name.pos, name)
    }

    /** A reference to a type with type arguments */
    class ConstructedType(
        pos: Position,
        type: UnboundTypeName,
        args: Iterable<Type>,
    ) : BaseTree(pos), NonNullableType {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate74
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type
                1 -> FormattableTreeGroup(this.args)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: UnboundTypeName
        var type: UnboundTypeName
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private val _args: MutableList<Type> = mutableListOf()
        var args: List<Type>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): ConstructedType {
            return ConstructedType(pos, type = this.type.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ConstructedType && this.type == other.type && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = type.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            updateTreeConnections(this._args, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ConstructedType).type },
                { n -> (n as ConstructedType).args },
            )
        }
    }

    class QualTypeName(
        pos: Position,
        namespaceAlias: Identifier? = null,
        id: Iterable<Identifier>,
    ) : BaseTree(pos), UnboundTypeName {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (namespaceAlias != null) {
                    sharedCodeFormattingTemplate75
                } else {
                    sharedCodeFormattingTemplate76
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.namespaceAlias ?: FormattableTreeGroup.empty
                1 -> FormattableTreeGroup(this.id)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _namespaceAlias: Identifier?
        var namespaceAlias: Identifier?
            get() = _namespaceAlias
            set(newValue) { _namespaceAlias = updateTreeConnection(_namespaceAlias, newValue) }
        private val _id: MutableList<Identifier> = mutableListOf()
        var id: List<Identifier>
            get() = _id
            set(newValue) { updateTreeConnections(_id, newValue) }
        override fun deepCopy(): QualTypeName {
            return QualTypeName(pos, namespaceAlias = this.namespaceAlias?.deepCopy(), id = this.id.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is QualTypeName && this.namespaceAlias == other.namespaceAlias && this.id == other.id
        }
        override fun hashCode(): Int {
            var hc = (namespaceAlias?.hashCode() ?: 0)
            hc = 31 * hc + id.hashCode()
            return hc
        }
        init {
            this._namespaceAlias = updateTreeConnection(null, namespaceAlias)
            updateTreeConnections(this._id, id)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as QualTypeName).namespaceAlias },
                { n -> (n as QualTypeName).id },
            )
        }
    }

    class BreakStatement(
        pos: Position,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate77
        override val formatElementCount
            get() = 0
        override fun deepCopy(): BreakStatement {
            return BreakStatement(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BreakStatement
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class ContinueStatement(
        pos: Position,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate78
        override val formatElementCount
            get() = 0
        override fun deepCopy(): ContinueStatement {
            return ContinueStatement(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ContinueStatement
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class ExpressionStatement(
        pos: Position,
        expr: StatementExpression,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate72
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
        private var _expr: StatementExpression
        var expr: StatementExpression
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): ExpressionStatement {
            return ExpressionStatement(pos, expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ExpressionStatement && this.expr == other.expr
        }
        override fun hashCode(): Int {
            return expr.hashCode()
        }
        init {
            this._expr = updateTreeConnection(null, expr)
            require(expr !is Operation || expr.operator.operator.makesStatement)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ExpressionStatement).expr },
            )
        }
        constructor(expr: StatementExpression) : this(expr.pos, expr)
    }

    class GotoStatement(
        pos: Position,
        label: Identifier,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate79
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
        private var _label: Identifier
        var label: Identifier
            get() = _label
            set(newValue) { _label = updateTreeConnection(_label, newValue) }
        override fun deepCopy(): GotoStatement {
            return GotoStatement(pos, label = this.label.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is GotoStatement && this.label == other.label
        }
        override fun hashCode(): Int {
            return label.hashCode()
        }
        init {
            this._label = updateTreeConnection(null, label)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as GotoStatement).label },
            )
        }
    }

    class IfStatement(
        pos: Position,
        test: Expression,
        consequent: Statement,
        alternate: Statement?,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (alternate != null) {
                    sharedCodeFormattingTemplate80
                } else {
                    sharedCodeFormattingTemplate81
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
        private var _test: Expression
        var test: Expression
            get() = _test
            set(newValue) { _test = updateTreeConnection(_test, newValue) }
        private var _consequent: Statement
        var consequent: Statement
            get() = _consequent
            set(newValue) { _consequent = updateTreeConnection(_consequent, newValue) }
        private var _alternate: Statement?
        var alternate: Statement?
            get() = _alternate
            set(newValue) { _alternate = updateTreeConnection(_alternate, newValue) }
        override fun deepCopy(): IfStatement {
            return IfStatement(pos, test = this.test.deepCopy(), consequent = this.consequent.deepCopy(), alternate = this.alternate?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is IfStatement && this.test == other.test && this.consequent == other.consequent && this.alternate == other.alternate
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
                { n -> (n as IfStatement).test },
                { n -> (n as IfStatement).consequent },
                { n -> (n as IfStatement).alternate },
            )
        }
    }

    class LabeledStatement(
        pos: Position,
        label: Identifier,
        statement: Statement,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate82
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.label
                1 -> this.statement
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _label: Identifier
        var label: Identifier
            get() = _label
            set(newValue) { _label = updateTreeConnection(_label, newValue) }
        private var _statement: Statement
        var statement: Statement
            get() = _statement
            set(newValue) { _statement = updateTreeConnection(_statement, newValue) }
        override fun deepCopy(): LabeledStatement {
            return LabeledStatement(pos, label = this.label.deepCopy(), statement = this.statement.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LabeledStatement && this.label == other.label && this.statement == other.statement
        }
        override fun hashCode(): Int {
            var hc = label.hashCode()
            hc = 31 * hc + statement.hashCode()
            return hc
        }
        init {
            this._label = updateTreeConnection(null, label)
            this._statement = updateTreeConnection(null, statement)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LabeledStatement).label },
                { n -> (n as LabeledStatement).statement },
            )
        }
    }

    class LocalVariableDecl(
        pos: Position,
        type: Type,
        variables: Iterable<VariableDeclarator>,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate83
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type
                1 -> FormattableTreeGroup(this.variables)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: Type
        var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private val _variables: MutableList<VariableDeclarator> = mutableListOf()
        var variables: List<VariableDeclarator>
            get() = _variables
            set(newValue) { updateTreeConnections(_variables, newValue) }
        override fun deepCopy(): LocalVariableDecl {
            return LocalVariableDecl(pos, type = this.type.deepCopy(), variables = this.variables.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LocalVariableDecl && this.type == other.type && this.variables == other.variables
        }
        override fun hashCode(): Int {
            var hc = type.hashCode()
            hc = 31 * hc + variables.hashCode()
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            updateTreeConnections(this._variables, variables)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LocalVariableDecl).type },
                { n -> (n as LocalVariableDecl).variables },
            )
        }
    }

    class ReturnStatement(
        pos: Position,
        expr: Expression? = null,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (expr != null) {
                    sharedCodeFormattingTemplate84
                } else {
                    sharedCodeFormattingTemplate85
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expr ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expr: Expression?
        var expr: Expression?
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): ReturnStatement {
            return ReturnStatement(pos, expr = this.expr?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ReturnStatement && this.expr == other.expr
        }
        override fun hashCode(): Int {
            return (expr?.hashCode() ?: 0)
        }
        init {
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ReturnStatement).expr },
            )
        }
    }

    class TryStatement(
        pos: Position,
        tryBlock: BlockStatement,
        catchBlock: BlockStatement? = null,
        finallyBlock: BlockStatement? = null,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (catchBlock != null && finallyBlock != null) {
                    sharedCodeFormattingTemplate86
                } else if (catchBlock != null) {
                    sharedCodeFormattingTemplate87
                } else if (finallyBlock != null) {
                    sharedCodeFormattingTemplate88
                } else {
                    sharedCodeFormattingTemplate89
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.tryBlock
                1 -> this.catchBlock ?: FormattableTreeGroup.empty
                2 -> this.finallyBlock ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _tryBlock: BlockStatement
        var tryBlock: BlockStatement
            get() = _tryBlock
            set(newValue) { _tryBlock = updateTreeConnection(_tryBlock, newValue) }
        private var _catchBlock: BlockStatement?
        var catchBlock: BlockStatement?
            get() = _catchBlock
            set(newValue) { _catchBlock = updateTreeConnection(_catchBlock, newValue) }
        private var _finallyBlock: BlockStatement?
        var finallyBlock: BlockStatement?
            get() = _finallyBlock
            set(newValue) { _finallyBlock = updateTreeConnection(_finallyBlock, newValue) }
        override fun deepCopy(): TryStatement {
            return TryStatement(pos, tryBlock = this.tryBlock.deepCopy(), catchBlock = this.catchBlock?.deepCopy(), finallyBlock = this.finallyBlock?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TryStatement && this.tryBlock == other.tryBlock && this.catchBlock == other.catchBlock && this.finallyBlock == other.finallyBlock
        }
        override fun hashCode(): Int {
            var hc = tryBlock.hashCode()
            hc = 31 * hc + (catchBlock?.hashCode() ?: 0)
            hc = 31 * hc + (finallyBlock?.hashCode() ?: 0)
            return hc
        }
        init {
            this._tryBlock = updateTreeConnection(null, tryBlock)
            this._catchBlock = updateTreeConnection(null, catchBlock)
            this._finallyBlock = updateTreeConnection(null, finallyBlock)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TryStatement).tryBlock },
                { n -> (n as TryStatement).catchBlock },
                { n -> (n as TryStatement).finallyBlock },
            )
        }
    }

    class WhileStatement(
        pos: Position,
        test: Expression,
        body: BlockStatement,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate90
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
        private var _test: Expression
        var test: Expression
            get() = _test
            set(newValue) { _test = updateTreeConnection(_test, newValue) }
        private var _body: BlockStatement
        var body: BlockStatement
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): WhileStatement {
            return WhileStatement(pos, test = this.test.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is WhileStatement && this.test == other.test && this.body == other.body
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
                { n -> (n as WhileStatement).test },
                { n -> (n as WhileStatement).body },
            )
        }
    }

    class YieldReturn(
        pos: Position,
        expr: Expression,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate91
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
        private var _expr: Expression
        var expr: Expression
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): YieldReturn {
            return YieldReturn(pos, expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is YieldReturn && this.expr == other.expr
        }
        override fun hashCode(): Int {
            return expr.hashCode()
        }
        init {
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as YieldReturn).expr },
            )
        }
    }

    /** Expressions that can appear in statement position. */
    sealed interface StatementExpression : Tree, PrimaryExpression {
        override fun deepCopy(): StatementExpression
    }

    class AwaitExpression(
        pos: Position,
        promise: Expression,
    ) : BaseTree(pos), StatementExpression {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate92
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.promise
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _promise: Expression
        var promise: Expression
            get() = _promise
            set(newValue) { _promise = updateTreeConnection(_promise, newValue) }
        override fun deepCopy(): AwaitExpression {
            return AwaitExpression(pos, promise = this.promise.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AwaitExpression && this.promise == other.promise
        }
        override fun hashCode(): Int {
            return promise.hashCode()
        }
        init {
            this._promise = updateTreeConnection(null, promise)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AwaitExpression).promise },
            )
        }
    }

    class InvocationExpression(
        pos: Position,
        expr: PrimaryExpression,
        typeArgs: Iterable<Type> = listOf(),
        args: Iterable<Arg>,
    ) : BaseTree(pos), StatementExpression {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (typeArgs.isNotEmpty()) {
                    sharedCodeFormattingTemplate93
                } else {
                    sharedCodeFormattingTemplate94
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expr
                1 -> FormattableTreeGroup(this.typeArgs)
                2 -> FormattableTreeGroup(this.args)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expr: PrimaryExpression
        var expr: PrimaryExpression
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        private val _typeArgs: MutableList<Type> = mutableListOf()
        var typeArgs: List<Type>
            get() = _typeArgs
            set(newValue) { updateTreeConnections(_typeArgs, newValue) }
        private val _args: MutableList<Arg> = mutableListOf()
        var args: List<Arg>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): InvocationExpression {
            return InvocationExpression(pos, expr = this.expr.deepCopy(), typeArgs = this.typeArgs.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is InvocationExpression && this.expr == other.expr && this.typeArgs == other.typeArgs && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = expr.hashCode()
            hc = 31 * hc + typeArgs.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._expr = updateTreeConnection(null, expr)
            updateTreeConnections(this._typeArgs, typeArgs)
            updateTreeConnections(this._args, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as InvocationExpression).expr },
                { n -> (n as InvocationExpression).typeArgs },
                { n -> (n as InvocationExpression).args },
            )
        }
    }

    class ObjectCreationExpression(
        pos: Position,
        type: Type?,
        args: Iterable<Arg>,
        members: Iterable<Arg> = listOf(),
    ) : BaseTree(pos), StatementExpression {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (type != null && members.isNotEmpty()) {
                    sharedCodeFormattingTemplate95
                } else if (type != null) {
                    sharedCodeFormattingTemplate96
                } else if (members.isNotEmpty()) {
                    sharedCodeFormattingTemplate97
                } else {
                    sharedCodeFormattingTemplate98
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type ?: FormattableTreeGroup.empty
                1 -> FormattableTreeGroup(this.args)
                2 -> FormattableTreeGroup(this.members)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: Type?
        var type: Type?
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private val _args: MutableList<Arg> = mutableListOf()
        var args: List<Arg>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        private val _members: MutableList<Arg> = mutableListOf()
        var members: List<Arg>
            get() = _members
            set(newValue) { updateTreeConnections(_members, newValue) }
        override fun deepCopy(): ObjectCreationExpression {
            return ObjectCreationExpression(pos, type = this.type?.deepCopy(), args = this.args.deepCopy(), members = this.members.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ObjectCreationExpression && this.type == other.type && this.args == other.args && this.members == other.members
        }
        override fun hashCode(): Int {
            var hc = (type?.hashCode() ?: 0)
            hc = 31 * hc + args.hashCode()
            hc = 31 * hc + members.hashCode()
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            updateTreeConnections(this._args, args)
            updateTreeConnections(this._members, members)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ObjectCreationExpression).type },
                { n -> (n as ObjectCreationExpression).args },
                { n -> (n as ObjectCreationExpression).members },
            )
        }
    }

    class Operation(
        pos: Position,
        left: Expression?,
        operator: Operator,
        right: Expression?,
    ) : BaseTree(pos), StatementExpression {
        override val operatorDefinition
            get() = operator.operator.operatorDefinition
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (left != null && right != null) {
                    sharedCodeFormattingTemplate99
                } else if (left != null) {
                    sharedCodeFormattingTemplate70
                } else if (right != null) {
                    sharedCodeFormattingTemplate100
                } else {
                    sharedCodeFormattingTemplate101
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
        private var _left: Expression?
        var left: Expression?
            get() = _left
            set(newValue) { _left = updateTreeConnection(_left, newValue) }
        private var _operator: Operator
        var operator: Operator
            get() = _operator
            set(newValue) { _operator = updateTreeConnection(_operator, newValue) }
        private var _right: Expression?
        var right: Expression?
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

    class ThrowExpression(
        pos: Position,
        expr: Expression,
    ) : BaseTree(pos), StatementExpression {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate102
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
        private var _expr: Expression
        var expr: Expression
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): ThrowExpression {
            return ThrowExpression(pos, expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ThrowExpression && this.expr == other.expr
        }
        override fun hashCode(): Int {
            return expr.hashCode()
        }
        init {
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ThrowExpression).expr },
            )
        }
    }

    class CastExpression(
        pos: Position,
        type: Type,
        expr: Expression,
    ) : BaseTree(pos), PrimaryExpression {
        override val operatorDefinition
            get() = CSharpOperatorDefinition.Cast
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate103
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
        private var _expr: Expression
        var expr: Expression
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): CastExpression {
            return CastExpression(pos, type = this.type.deepCopy(), expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is CastExpression && this.type == other.type && this.expr == other.expr
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
                { n -> (n as CastExpression).type },
                { n -> (n as CastExpression).expr },
            )
        }
    }

    class ElementAccess(
        pos: Position,
        expr: PrimaryExpression,
        args: Iterable<Arg>,
    ) : BaseTree(pos), PrimaryExpression {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate104
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expr
                1 -> FormattableTreeGroup(this.args)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expr: PrimaryExpression
        var expr: PrimaryExpression
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        private val _args: MutableList<Arg> = mutableListOf()
        var args: List<Arg>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): ElementAccess {
            return ElementAccess(pos, expr = this.expr.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ElementAccess && this.expr == other.expr && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = expr.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._expr = updateTreeConnection(null, expr)
            updateTreeConnections(this._args, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ElementAccess).expr },
                { n -> (n as ElementAccess).args },
            )
        }
    }

    sealed interface Literal : Tree, PrimaryExpression {
        override fun deepCopy(): Literal
    }

    class MemberAccess(
        pos: Position,
        expr: PrimaryExpression,
        id: Identifier,
        var extension: Boolean = false,
    ) : BaseTree(pos), PrimaryExpression {
        override val operatorDefinition
            get() = CSharpOperatorDefinition.Atom
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate105
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expr
                1 -> this.id
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expr: PrimaryExpression
        var expr: PrimaryExpression
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        private var _id: Identifier
        var id: Identifier
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        override fun deepCopy(): MemberAccess {
            return MemberAccess(pos, expr = this.expr.deepCopy(), id = this.id.deepCopy(), extension = this.extension)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MemberAccess && this.expr == other.expr && this.id == other.id && this.extension == other.extension
        }
        override fun hashCode(): Int {
            var hc = expr.hashCode()
            hc = 31 * hc + id.hashCode()
            hc = 31 * hc + extension.hashCode()
            return hc
        }
        init {
            this._expr = updateTreeConnection(null, expr)
            this._id = updateTreeConnection(null, id)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as MemberAccess).expr },
                { n -> (n as MemberAccess).id },
            )
        }
    }

    class TypeofExpression(
        pos: Position,
        type: Type,
    ) : BaseTree(pos), PrimaryExpression {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate106
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
        override fun deepCopy(): TypeofExpression {
            return TypeofExpression(pos, type = this.type.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeofExpression && this.type == other.type
        }
        override fun hashCode(): Int {
            return type.hashCode()
        }
        init {
            this._type = updateTreeConnection(null, type)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeofExpression).type },
            )
        }
    }

    class Operator(
        pos: Position,
        var operator: CSharpOperator,
    ) : BaseTree(pos) {
        override val operatorDefinition: CSharpOperatorDefinition?
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

    class FullArg(
        pos: Position,
        name: Identifier? = null,
        value: ArgValue,
    ) : BaseTree(pos), Arg {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (name != null) {
                    sharedCodeFormattingTemplate82
                } else {
                    sharedCodeFormattingTemplate101
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name ?: FormattableTreeGroup.empty
                1 -> this.value
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _name: Identifier?
        var name: Identifier?
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _value: ArgValue
        var value: ArgValue
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): FullArg {
            return FullArg(pos, name = this.name?.deepCopy(), value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FullArg && this.name == other.name && this.value == other.value
        }
        override fun hashCode(): Int {
            var hc = (name?.hashCode() ?: 0)
            hc = 31 * hc + value.hashCode()
            return hc
        }
        init {
            this._name = updateTreeConnection(null, name)
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FullArg).name },
                { n -> (n as FullArg).value },
            )
        }
    }

    class NumberLiteral(
        pos: Position,
        var value: Number,
    ) : BaseTree(pos), Literal {
        override val operatorDefinition: CSharpOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.number("$value")
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
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
        override val operatorDefinition: CSharpOperatorDefinition?
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

    /** `{{0*\n}} {{1*\n}} \n {{2*\n}}` */
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
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
            ),
        )

    /** `{{0*\n}} {{2*\n}}` */
    private val sharedCodeFormattingTemplate1 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
            ),
        )

    /** `[ {{0}} : {{1*,}} ]` */
    private val sharedCodeFormattingTemplate2 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `[ {{1*,}} ]` */
    private val sharedCodeFormattingTemplate3 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `using {{0}} = {{1*.}} ;` */
    private val sharedCodeFormattingTemplate4 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("using", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `using {{1*.}} ;` */
    private val sharedCodeFormattingTemplate5 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("using", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} ( {{1*,}} )` */
    private val sharedCodeFormattingTemplate6 =
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

    /** `{{0}}` */
    private val sharedCodeFormattingTemplate7 =
        CodeFormattingTemplate.OneSubstitution(0)

    /** `namespace {{0*.}} \{ {{1*\n}} {{2*\n}} \}` */
    private val sharedCodeFormattingTemplate8 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("namespace", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} \n {{1}} {{2}} < {{3*,}} > : {{4*,}} {{5}} \{ {{6*\n}} \}` */
    private val sharedCodeFormattingTemplate9 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} \n {{1}} {{2}} < {{3*,}} > {{5}} \{ {{6*\n}} \}` */
    private val sharedCodeFormattingTemplate10 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} \n {{1}} {{2}} : {{4*,}} {{5}} \{ {{6*\n}} \}` */
    private val sharedCodeFormattingTemplate11 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} \n {{1}} {{2}} {{5}} \{ {{6*\n}} \}` */
    private val sharedCodeFormattingTemplate12 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1}} {{2}} < {{3*,}} > : {{4*,}} {{5}} \{ {{6*\n}} \}` */
    private val sharedCodeFormattingTemplate13 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1}} {{2}} < {{3*,}} > {{5}} \{ {{6*\n}} \}` */
    private val sharedCodeFormattingTemplate14 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1}} {{2}} : {{4*,}} {{5}} \{ {{6*\n}} \}` */
    private val sharedCodeFormattingTemplate15 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1}} {{2}} {{5}} \{ {{6*\n}} \}` */
    private val sharedCodeFormattingTemplate16 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}}` */
    private val sharedCodeFormattingTemplate17 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.empty,
        )

    /** `where {{0}} : {{1*,}}` */
    private val sharedCodeFormattingTemplate18 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("where", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
            ),
        )

    /** `struct` */
    private val sharedCodeFormattingTemplate19 =
        CodeFormattingTemplate.LiteralToken("struct", OutputTokenType.Word)

    /** `class ?` */
    private val sharedCodeFormattingTemplate20 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("?", OutputTokenType.Punctuation),
            ),
        )

    /** `class` */
    private val sharedCodeFormattingTemplate21 =
        CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word)

    /** `notnull` */
    private val sharedCodeFormattingTemplate22 =
        CodeFormattingTemplate.LiteralToken("notnull", OutputTokenType.Word)

    /** `unmanaged` */
    private val sharedCodeFormattingTemplate23 =
        CodeFormattingTemplate.LiteralToken("unmanaged", OutputTokenType.Word)

    /** `new()` */
    private val sharedCodeFormattingTemplate24 =
        CodeFormattingTemplate.LiteralToken("new()", OutputTokenType.Word)

    /** `default` */
    private val sharedCodeFormattingTemplate25 =
        CodeFormattingTemplate.LiteralToken("default", OutputTokenType.Word)

    /** `allows ref struct` */
    private val sharedCodeFormattingTemplate26 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("allows", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("ref", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("struct", OutputTokenType.Word),
            ),
        )

    /** `{{0*\n}} \n {{1}} {{2}} {{3*,}} ;` */
    private val sharedCodeFormattingTemplate27 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1}} {{2}} {{3*,}} ;` */
    private val sharedCodeFormattingTemplate28 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} \n {{1}} {{2}} {{3}} < {{4*,}} > ( {{5*,}} ) {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate29 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0*\n}} \n {{1}} {{2}} {{3}} < {{4*,}} > ( {{5*,}} ) {{6}} ;` */
    private val sharedCodeFormattingTemplate30 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} \n {{1}} {{2}} {{3}} ( {{5*,}} ) {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate31 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0*\n}} \n {{1}} {{2}} {{3}} ( {{5*,}} ) {{6}} ;` */
    private val sharedCodeFormattingTemplate32 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} \n {{1}} {{3}} < {{4*,}} > ( {{5*,}} ) {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate33 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0*\n}} \n {{1}} {{3}} < {{4*,}} > ( {{5*,}} ) {{6}} ;` */
    private val sharedCodeFormattingTemplate34 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} \n {{1}} {{3}} ( {{5*,}} ) {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate35 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0*\n}} \n {{1}} {{3}} ( {{5*,}} ) {{6}} ;` */
    private val sharedCodeFormattingTemplate36 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} \n {{2}} {{3}} < {{4*,}} > ( {{5*,}} ) {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate37 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0*\n}} \n {{2}} {{3}} < {{4*,}} > ( {{5*,}} ) {{6}} ;` */
    private val sharedCodeFormattingTemplate38 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} \n {{2}} {{3}} ( {{5*,}} ) {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate39 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0*\n}} \n {{2}} {{3}} ( {{5*,}} ) {{6}} ;` */
    private val sharedCodeFormattingTemplate40 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} \n {{3}} < {{4*,}} > ( {{5*,}} ) {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate41 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0*\n}} \n {{3}} < {{4*,}} > ( {{5*,}} ) {{6}} ;` */
    private val sharedCodeFormattingTemplate42 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} \n {{3}} ( {{5*,}} ) {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate43 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0*\n}} \n {{3}} ( {{5*,}} ) {{6}} ;` */
    private val sharedCodeFormattingTemplate44 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1}} {{2}} {{3}} < {{4*,}} > ( {{5*,}} ) {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate45 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{1}} {{2}} {{3}} < {{4*,}} > ( {{5*,}} ) {{6}} ;` */
    private val sharedCodeFormattingTemplate46 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1}} {{2}} {{3}} ( {{5*,}} ) {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate47 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{1}} {{2}} {{3}} ( {{5*,}} ) {{6}} ;` */
    private val sharedCodeFormattingTemplate48 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1}} {{3}} < {{4*,}} > ( {{5*,}} ) {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate49 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{1}} {{3}} < {{4*,}} > ( {{5*,}} ) {{6}} ;` */
    private val sharedCodeFormattingTemplate50 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1}} {{3}} ( {{5*,}} ) {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate51 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{1}} {{3}} ( {{5*,}} ) {{6}} ;` */
    private val sharedCodeFormattingTemplate52 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{2}} {{3}} < {{4*,}} > ( {{5*,}} ) {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate53 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{2}} {{3}} < {{4*,}} > ( {{5*,}} ) {{6}} ;` */
    private val sharedCodeFormattingTemplate54 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{2}} {{3}} ( {{5*,}} ) {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate55 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{2}} {{3}} ( {{5*,}} ) {{6}} ;` */
    private val sharedCodeFormattingTemplate56 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{3}} < {{4*,}} > ( {{5*,}} ) {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate57 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{3}} < {{4*,}} > ( {{5*,}} ) {{6}} ;` */
    private val sharedCodeFormattingTemplate58 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{3}} ( {{5*,}} ) {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate59 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{3}} ( {{5*,}} ) {{6}} ;` */
    private val sharedCodeFormattingTemplate60 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} \n {{1}} {{2}} {{3}} \{ {{4*\n}} \}` */
    private val sharedCodeFormattingTemplate61 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1}} {{2}} {{3}} \{ {{4*\n}} \}` */
    private val sharedCodeFormattingTemplate62 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*\n}} \n static {{1}} ( ) {{2}}` */
    private val sharedCodeFormattingTemplate63 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0*\n}} \n static {{1}} ( ) ;` */
    private val sharedCodeFormattingTemplate64 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `static {{1}} ( ) {{2}}` */
    private val sharedCodeFormattingTemplate65 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `static {{1}} ( ) ;` */
    private val sharedCodeFormattingTemplate66 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} = {{1}}` */
    private val sharedCodeFormattingTemplate67 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `\{ \n {{0*\n}} \n \}` */
    private val sharedCodeFormattingTemplate68 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} = {{2}}` */
    private val sharedCodeFormattingTemplate69 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0}} {{1}}` */
    private val sharedCodeFormattingTemplate70 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `params {{0}} [ ] {{1}}` */
    private val sharedCodeFormattingTemplate71 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("params", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} ;` */
    private val sharedCodeFormattingTemplate72 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} ?` */
    private val sharedCodeFormattingTemplate73 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("?", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} < {{1*,}} >` */
    private val sharedCodeFormattingTemplate74 =
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

    /** `{{0}} :: {{1*.}}` */
    private val sharedCodeFormattingTemplate75 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("::", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                ),
            ),
        )

    /** `{{1*.}}` */
    private val sharedCodeFormattingTemplate76 =
        CodeFormattingTemplate.GroupSubstitution(
            1,
            CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
        )

    /** `break ;` */
    private val sharedCodeFormattingTemplate77 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("break", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `continue ;` */
    private val sharedCodeFormattingTemplate78 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("continue", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `goto {{0}} ;` */
    private val sharedCodeFormattingTemplate79 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("goto", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `if ( {{0}} ) {{1}} else {{2}}` */
    private val sharedCodeFormattingTemplate80 =
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
    private val sharedCodeFormattingTemplate81 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} : {{1}}` */
    private val sharedCodeFormattingTemplate82 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} {{1*,}} ;` */
    private val sharedCodeFormattingTemplate83 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `return {{0}} ;` */
    private val sharedCodeFormattingTemplate84 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("return", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `return ;` */
    private val sharedCodeFormattingTemplate85 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("return", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `try {{0}} catch {{1}} finally {{2}}` */
    private val sharedCodeFormattingTemplate86 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("catch", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `try {{0}} catch {{1}}` */
    private val sharedCodeFormattingTemplate87 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("catch", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `try {{0}} finally {{2}}` */
    private val sharedCodeFormattingTemplate88 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `try {{0}}` */
    private val sharedCodeFormattingTemplate89 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `while ( {{0}} ) {{1}}` */
    private val sharedCodeFormattingTemplate90 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("while", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `yield return {{0}} ;` */
    private val sharedCodeFormattingTemplate91 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("yield", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("return", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `await ( {{0}} )` */
    private val sharedCodeFormattingTemplate92 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("await", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}} < {{1*,}} > ( {{2*,}} )` */
    private val sharedCodeFormattingTemplate93 =
        CodeFormattingTemplate.Concatenation(
            listOf(
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
            ),
        )

    /** `{{0}} ( {{2*,}} )` */
    private val sharedCodeFormattingTemplate94 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `new {{0}} ( {{1*,}} ) \{ {{2*,}} \}` */
    private val sharedCodeFormattingTemplate95 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("new", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `new {{0}} ( {{1*,}} )` */
    private val sharedCodeFormattingTemplate96 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("new", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `new ( {{1*,}} ) \{ {{2*,}} \}` */
    private val sharedCodeFormattingTemplate97 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("new", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `new ( {{1*,}} )` */
    private val sharedCodeFormattingTemplate98 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("new", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}} {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate99 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{1}} {{2}}` */
    private val sharedCodeFormattingTemplate100 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{1}}` */
    private val sharedCodeFormattingTemplate101 =
        CodeFormattingTemplate.OneSubstitution(1)

    /** `throw {{0}}` */
    private val sharedCodeFormattingTemplate102 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("throw", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `( {{0}} ) {{1}}` */
    private val sharedCodeFormattingTemplate103 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} [ {{1*,}} ]` */
    private val sharedCodeFormattingTemplate104 =
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

    /** `{{0}} . {{1}}` */
    private val sharedCodeFormattingTemplate105 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `typeof ( {{0}} )` */
    private val sharedCodeFormattingTemplate106 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("typeof", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )
}
