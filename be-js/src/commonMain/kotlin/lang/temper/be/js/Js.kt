@file:lang.temper.common.Generated("OutputGrammarCodeGenerator")
@file:Suppress("ktlint", "unused", "CascadeIf", "MagicNumber", "MemberNameEqualsClassName", "MemberVisibilityCanBePrivate")

package lang.temper.be.js
import lang.temper.ast.ChildMemberRelationships
import lang.temper.ast.OutData
import lang.temper.ast.OutTree
import lang.temper.ast.deepCopy
import lang.temper.be.BaseOutData
import lang.temper.be.BaseOutTree
import lang.temper.be.tmpl.operatorTokenType
import lang.temper.format.CodeFormattingTemplate
import lang.temper.format.FormattableEnum
import lang.temper.format.FormattableTreeGroup
import lang.temper.format.FormattingHints
import lang.temper.format.IndexableFormattableTreeElement
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenAssociation
import lang.temper.format.TokenSink
import lang.temper.log.Position
import lang.temper.name.OutName
import lang.temper.name.TemperName

object Js {
    sealed interface Tree : OutTree<Tree> {
        override fun formattingHints(): FormattingHints = JsFormattingHints.getInstance()
        override val operatorDefinition: JsOperatorDefinition?
        override fun deepCopy(): Tree
    }
    sealed class BaseTree(
        pos: Position,
    ) : BaseOutTree<Tree>(pos), Tree
    sealed interface Data : OutData<Data> {
        override fun formattingHints(): FormattingHints = JsFormattingHints.getInstance()
        override val operatorDefinition: JsOperatorDefinition?
    }
    sealed class BaseData : BaseOutData<Data>(), Data

    enum class ClassMethodKind : FormattableEnum {
        Constructor,
        Method,
        Get,
        Set,
    }

    enum class DeclarationKind : FormattableEnum {
        Const,
        Let,
        Var,
    }

    enum class ObjectMethodKind : FormattableEnum {
        Get,
        Set,
        Method,
    }

    class Program(
        pos: Position,
        topLevel: Iterable<TopLevel>,
    ) : BaseTree(pos) {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate0
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.topLevel)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _topLevel: MutableList<TopLevel> = mutableListOf()
        var topLevel: List<TopLevel>
            get() = _topLevel
            set(newValue) { updateTreeConnections(_topLevel, newValue) }
        override fun deepCopy(): Program {
            return Program(pos, topLevel = this.topLevel.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Program && this.topLevel == other.topLevel
        }
        override fun hashCode(): Int {
            return topLevel.hashCode()
        }
        init {
            updateTreeConnections(this._topLevel, topLevel)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Program).topLevel },
            )
        }
    }

    sealed interface TopLevel : Tree {
        override fun deepCopy(): TopLevel
    }

    sealed interface ModuleDeclaration : Tree, TopLevel {
        override fun deepCopy(): ModuleDeclaration
    }

    sealed interface Statement : Tree, TopLevel {
        override fun deepCopy(): Statement
    }

    class ImportDeclaration(
        pos: Position,
        specifiers: Iterable<Imported>,
        source: StringLiteral,
    ) : BaseTree(pos), ModuleDeclaration {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (specifiers.isNotEmpty()) {
                    sharedCodeFormattingTemplate1
                } else {
                    sharedCodeFormattingTemplate2
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.specifiers)
                1 -> this.source
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _specifiers: MutableList<Imported> = mutableListOf()
        var specifiers: List<Imported>
            get() = _specifiers
            set(newValue) { updateTreeConnections(_specifiers, newValue) }
        private var _source: StringLiteral
        var source: StringLiteral
            get() = _source
            set(newValue) { _source = updateTreeConnection(_source, newValue) }
        override fun deepCopy(): ImportDeclaration {
            return ImportDeclaration(pos, specifiers = this.specifiers.deepCopy(), source = this.source.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImportDeclaration && this.specifiers == other.specifiers && this.source == other.source
        }
        override fun hashCode(): Int {
            var hc = specifiers.hashCode()
            hc = 31 * hc + source.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._specifiers, specifiers)
            this._source = updateTreeConnection(null, source)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ImportDeclaration).specifiers },
                { n -> (n as ImportDeclaration).source },
            )
        }
    }

    sealed interface ExportDeclaration : Tree, ModuleDeclaration {
        override fun deepCopy(): ExportDeclaration
    }

    class ExportNamedDeclaration(
        pos: Position,
        doc: MaybeJsDocComment,
        declaration: Declaration?,
        specifiers: Iterable<ExportSpecifier>,
        source: StringLiteral?,
    ) : BaseTree(pos), ExportDeclaration {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (declaration != null && source != null) {
                    sharedCodeFormattingTemplate3
                } else if (declaration != null) {
                    sharedCodeFormattingTemplate4
                } else if (source != null) {
                    sharedCodeFormattingTemplate5
                } else {
                    sharedCodeFormattingTemplate6
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.doc
                1 -> this.declaration ?: FormattableTreeGroup.empty
                2 -> FormattableTreeGroup(this.specifiers)
                3 -> this.source ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _doc: MaybeJsDocComment
        var doc: MaybeJsDocComment
            get() = _doc
            set(newValue) { _doc = updateTreeConnection(_doc, newValue) }
        private var _declaration: Declaration?
        var declaration: Declaration?
            get() = _declaration
            set(newValue) { _declaration = updateTreeConnection(_declaration, newValue) }
        private val _specifiers: MutableList<ExportSpecifier> = mutableListOf()
        var specifiers: List<ExportSpecifier>
            get() = _specifiers
            set(newValue) { updateTreeConnections(_specifiers, newValue) }
        private var _source: StringLiteral?
        var source: StringLiteral?
            get() = _source
            set(newValue) { _source = updateTreeConnection(_source, newValue) }
        override fun deepCopy(): ExportNamedDeclaration {
            return ExportNamedDeclaration(pos, doc = this.doc.deepCopy(), declaration = this.declaration?.deepCopy(), specifiers = this.specifiers.deepCopy(), source = this.source?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ExportNamedDeclaration && this.doc == other.doc && this.declaration == other.declaration && this.specifiers == other.specifiers && this.source == other.source
        }
        override fun hashCode(): Int {
            var hc = doc.hashCode()
            hc = 31 * hc + (declaration?.hashCode() ?: 0)
            hc = 31 * hc + specifiers.hashCode()
            hc = 31 * hc + (source?.hashCode() ?: 0)
            return hc
        }
        init {
            this._doc = updateTreeConnection(null, doc)
            this._declaration = updateTreeConnection(null, declaration)
            updateTreeConnections(this._specifiers, specifiers)
            this._source = updateTreeConnection(null, source)
            require(this.declaration == null || this.specifiers.isEmpty())
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ExportNamedDeclaration).doc },
                { n -> (n as ExportNamedDeclaration).declaration },
                { n -> (n as ExportNamedDeclaration).specifiers },
                { n -> (n as ExportNamedDeclaration).source },
            )
        }
    }

    class ExportDefaultDeclaration(
        pos: Position,
        declaration: Expression,
    ) : BaseTree(pos), ExportDeclaration {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate7
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.declaration
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _declaration: Expression
        var declaration: Expression
            get() = _declaration
            set(newValue) { _declaration = updateTreeConnection(_declaration, newValue) }
        override fun deepCopy(): ExportDefaultDeclaration {
            return ExportDefaultDeclaration(pos, declaration = this.declaration.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ExportDefaultDeclaration && this.declaration == other.declaration
        }
        override fun hashCode(): Int {
            return declaration.hashCode()
        }
        init {
            this._declaration = updateTreeConnection(null, declaration)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ExportDefaultDeclaration).declaration },
            )
        }
    }

    class ExportAllDeclaration(
        pos: Position,
        source: StringLiteral,
    ) : BaseTree(pos), ExportDeclaration {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate8
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.source
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _source: StringLiteral
        var source: StringLiteral
            get() = _source
            set(newValue) { _source = updateTreeConnection(_source, newValue) }
        override fun deepCopy(): ExportAllDeclaration {
            return ExportAllDeclaration(pos, source = this.source.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ExportAllDeclaration && this.source == other.source
        }
        override fun hashCode(): Int {
            return source.hashCode()
        }
        init {
            this._source = updateTreeConnection(null, source)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ExportAllDeclaration).source },
            )
        }
    }

    sealed interface Imported : Tree {
        override fun deepCopy(): Imported
    }

    sealed interface MemberKey : Tree {
        override fun deepCopy(): MemberKey
    }

    sealed interface Actual : Tree {
        override fun deepCopy(): Actual
    }

    sealed interface Callee : Tree {
        override fun deepCopy(): Callee
    }

    sealed interface ArrayElement : Tree {
        override fun deepCopy(): ArrayElement
    }

    sealed interface LoopLeft : Tree {
        override fun deepCopy(): LoopLeft
    }

    sealed interface Type : Tree {
        override fun deepCopy(): Type
    }

    sealed interface Expression : Tree, MemberKey, Actual, Callee, ArrayElement, LoopLeft, Type {
        override fun deepCopy(): Expression
    }

    sealed interface Literal : Tree, Expression {
        override fun deepCopy(): Literal
    }

    class StringLiteral(
        pos: Position,
        var value: String,
    ) : BaseTree(pos), Literal {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(
                OutputToken(
                    stringTokenText(value),
                    OutputTokenType.QuotedValue,
                ),
            )
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

    class MaybeJsDocComment(
        pos: Position,
        doc: JsDocComment?,
    ) : BaseTree(pos) {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (doc != null) {
                    sharedCodeFormattingTemplate9
                } else {
                    sharedCodeFormattingTemplate10
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.doc ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _doc: JsDocComment?
        var doc: JsDocComment?
            get() = _doc
            set(newValue) { _doc = updateTreeConnection(_doc, newValue) }
        override fun deepCopy(): MaybeJsDocComment {
            return MaybeJsDocComment(pos, doc = this.doc?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MaybeJsDocComment && this.doc == other.doc
        }
        override fun hashCode(): Int {
            return (doc?.hashCode() ?: 0)
        }
        init {
            this._doc = updateTreeConnection(null, doc)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as MaybeJsDocComment).doc },
            )
        }
    }

    sealed interface Declaration : Tree, Statement {
        override fun deepCopy(): Declaration
    }

    sealed interface ModuleSpecifier : Tree {
        val local: Identifier
        override fun deepCopy(): ModuleSpecifier
    }

    class ExportSpecifier(
        pos: Position,
        local: Identifier,
        exported: Identifier,
    ) : BaseTree(pos), ModuleSpecifier {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (sameName) {
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
                0 -> this.local
                1 -> this.exported
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _local: Identifier
        override var local: Identifier
            get() = _local
            set(newValue) { _local = updateTreeConnection(_local, newValue) }
        private var _exported: Identifier
        var exported: Identifier
            get() = _exported
            set(newValue) { _exported = updateTreeConnection(_exported, newValue) }
        val sameName: Boolean
            get() = this.exported.name == this.local.name
        override fun deepCopy(): ExportSpecifier {
            return ExportSpecifier(pos, local = this.local.deepCopy(), exported = this.exported.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ExportSpecifier && this.local == other.local && this.exported == other.exported
        }
        override fun hashCode(): Int {
            var hc = local.hashCode()
            hc = 31 * hc + exported.hashCode()
            return hc
        }
        init {
            this._local = updateTreeConnection(null, local)
            this._exported = updateTreeConnection(null, exported)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ExportSpecifier).local },
                { n -> (n as ExportSpecifier).exported },
            )
        }
    }

    /**
     * ImportSpecifiers is not defined by Babel.
     * We use it to group together adjacent specifiers.
     */
    class ImportSpecifiers(
        pos: Position,
        specifiers: Iterable<ImportSpecifier>,
    ) : BaseTree(pos), Imported {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate13
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.specifiers)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _specifiers: MutableList<ImportSpecifier> = mutableListOf()
        var specifiers: List<ImportSpecifier>
            get() = _specifiers
            set(newValue) { updateTreeConnections(_specifiers, newValue) }
        override fun deepCopy(): ImportSpecifiers {
            return ImportSpecifiers(pos, specifiers = this.specifiers.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImportSpecifiers && this.specifiers == other.specifiers
        }
        override fun hashCode(): Int {
            return specifiers.hashCode()
        }
        init {
            updateTreeConnections(this._specifiers, specifiers)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ImportSpecifiers).specifiers },
            )
        }
    }

    class ImportDefaultSpecifier(
        pos: Position,
        local: Identifier,
    ) : BaseTree(pos), Imported, ModuleSpecifier {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate9
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.local
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _local: Identifier
        override var local: Identifier
            get() = _local
            set(newValue) { _local = updateTreeConnection(_local, newValue) }
        override fun deepCopy(): ImportDefaultSpecifier {
            return ImportDefaultSpecifier(pos, local = this.local.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImportDefaultSpecifier && this.local == other.local
        }
        override fun hashCode(): Int {
            return local.hashCode()
        }
        init {
            this._local = updateTreeConnection(null, local)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ImportDefaultSpecifier).local },
            )
        }
    }

    class ImportNamespaceSpecifier(
        pos: Position,
        local: Identifier,
    ) : BaseTree(pos), Imported, ModuleSpecifier {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate14
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.local
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _local: Identifier
        override var local: Identifier
            get() = _local
            set(newValue) { _local = updateTreeConnection(_local, newValue) }
        override fun deepCopy(): ImportNamespaceSpecifier {
            return ImportNamespaceSpecifier(pos, local = this.local.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImportNamespaceSpecifier && this.local == other.local
        }
        override fun hashCode(): Int {
            return local.hashCode()
        }
        init {
            this._local = updateTreeConnection(null, local)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ImportNamespaceSpecifier).local },
            )
        }
    }

    class ImportSpecifier(
        pos: Position,
        imported: Identifier,
        local: Identifier,
    ) : BaseTree(pos), ModuleSpecifier {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (sameName) {
                    sharedCodeFormattingTemplate9
                } else {
                    sharedCodeFormattingTemplate12
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.imported
                1 -> this.local
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _imported: Identifier
        var imported: Identifier
            get() = _imported
            set(newValue) { _imported = updateTreeConnection(_imported, newValue) }
        private var _local: Identifier
        override var local: Identifier
            get() = _local
            set(newValue) { _local = updateTreeConnection(_local, newValue) }
        val sameName: Boolean
            get() = this.imported.name == this.local.name
        override fun deepCopy(): ImportSpecifier {
            return ImportSpecifier(pos, imported = this.imported.deepCopy(), local = this.local.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImportSpecifier && this.imported == other.imported && this.local == other.local
        }
        override fun hashCode(): Int {
            var hc = imported.hashCode()
            hc = 31 * hc + local.hashCode()
            return hc
        }
        init {
            this._imported = updateTreeConnection(null, imported)
            this._local = updateTreeConnection(null, local)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ImportSpecifier).imported },
                { n -> (n as ImportSpecifier).local },
            )
        }
    }

    sealed interface ArrayPatternElement : Tree {
        override fun deepCopy(): ArrayPatternElement
    }

    sealed interface Pattern : Tree, Expression, ArrayPatternElement {
        override fun deepCopy(): Pattern
    }

    /**
     * For use in type expressions, a type name like Name or
     * a member expression like namespace.Name
     */
    sealed interface SimpleRef : Tree, Expression {
        override fun deepCopy(): SimpleRef
    }

    class Identifier(
        pos: Position,
        var name: JsIdentifierName,
        var sourceIdentifier: TemperName?,
    ) : BaseTree(pos), Pattern, Expression, SimpleRef {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(outName.toToken(inOperatorPosition = false))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        val outName: OutName
            get() = OutName(name.text, sourceIdentifier)
        override fun deepCopy(): Identifier {
            return Identifier(pos, name = this.name, sourceIdentifier = this.sourceIdentifier)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Identifier && this.name == other.name && this.sourceIdentifier == other.sourceIdentifier
        }
        override fun hashCode(): Int {
            var hc = name.hashCode()
            hc = 31 * hc + (sourceIdentifier?.hashCode() ?: 0)
            return hc
        }
        init {
            require(name.text !in jsReservedWords) { "$pos: `${name.text}` is a JS reserved word" }
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class DocumentedDeclaration(
        pos: Position,
        doc: JsDocComment,
        decl: Declaration,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate15
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.doc
                1 -> this.decl
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _doc: JsDocComment
        var doc: JsDocComment
            get() = _doc
            set(newValue) { _doc = updateTreeConnection(_doc, newValue) }
        private var _decl: Declaration
        var decl: Declaration
            get() = _decl
            set(newValue) { _decl = updateTreeConnection(_decl, newValue) }
        override fun deepCopy(): DocumentedDeclaration {
            return DocumentedDeclaration(pos, doc = this.doc.deepCopy(), decl = this.decl.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is DocumentedDeclaration && this.doc == other.doc && this.decl == other.decl
        }
        override fun hashCode(): Int {
            var hc = doc.hashCode()
            hc = 31 * hc + decl.hashCode()
            return hc
        }
        init {
            this._doc = updateTreeConnection(null, doc)
            this._decl = updateTreeConnection(null, decl)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as DocumentedDeclaration).doc },
                { n -> (n as DocumentedDeclaration).decl },
            )
        }
    }

    class JsDocComment(
        pos: Position,
        typeInfo: JsDocTypeInfo,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate16
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.typeInfo
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _typeInfo: JsDocTypeInfo
        var typeInfo: JsDocTypeInfo
            get() = _typeInfo
            set(newValue) { _typeInfo = updateTreeConnection(_typeInfo, newValue) }
        override fun deepCopy(): JsDocComment {
            return JsDocComment(pos, typeInfo = this.typeInfo.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is JsDocComment && this.typeInfo == other.typeInfo
        }
        override fun hashCode(): Int {
            return typeInfo.hashCode()
        }
        init {
            this._typeInfo = updateTreeConnection(null, typeInfo)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as JsDocComment).typeInfo },
            )
        }
    }

    sealed interface Function : Tree {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (async && generator && id != null && body != null) {
                    sharedCodeFormattingTemplate17
                } else if (async && generator && id != null) {
                    sharedCodeFormattingTemplate18
                } else if (async && generator && body != null) {
                    sharedCodeFormattingTemplate19
                } else if (async && generator) {
                    sharedCodeFormattingTemplate20
                } else if (async && id != null && body != null) {
                    sharedCodeFormattingTemplate21
                } else if (async && id != null) {
                    sharedCodeFormattingTemplate22
                } else if (async && body != null) {
                    sharedCodeFormattingTemplate23
                } else if (async) {
                    sharedCodeFormattingTemplate24
                } else if (generator && id != null && body != null) {
                    sharedCodeFormattingTemplate25
                } else if (generator && id != null) {
                    sharedCodeFormattingTemplate26
                } else if (generator && body != null) {
                    sharedCodeFormattingTemplate27
                } else if (generator) {
                    sharedCodeFormattingTemplate28
                } else if (id != null && body != null) {
                    sharedCodeFormattingTemplate29
                } else if (id != null) {
                    sharedCodeFormattingTemplate30
                } else if (body != null) {
                    sharedCodeFormattingTemplate31
                } else {
                    sharedCodeFormattingTemplate32
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id ?: FormattableTreeGroup.empty
                1 -> this.params
                2 -> this.body ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        val async: Boolean
        val generator: Boolean
        val id: Identifier?
        val params: Formals
        val body: BlockStatement?
        override fun deepCopy(): Function
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Function).id },
                { n -> (n as Function).params },
                { n -> (n as Function).body },
            )
        }
    }

    class FunctionDeclaration(
        pos: Position,
        id: Identifier,
        params: Formals,
        body: BlockStatement?,
        override var async: Boolean = false,
        override var generator: Boolean = false,
    ) : BaseTree(pos), Declaration, Function {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        private var _id: Identifier
        override var id: Identifier
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private var _params: Formals
        override var params: Formals
            get() = _params
            set(newValue) { _params = updateTreeConnection(_params, newValue) }
        private var _body: BlockStatement?
        override var body: BlockStatement?
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): FunctionDeclaration {
            return FunctionDeclaration(pos, id = this.id.deepCopy(), params = this.params.deepCopy(), body = this.body?.deepCopy(), async = this.async, generator = this.generator)
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FunctionDeclaration && this.id == other.id && this.params == other.params && this.body == other.body && this.async == other.async && this.generator == other.generator
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + params.hashCode()
            hc = 31 * hc + (body?.hashCode() ?: 0)
            hc = 31 * hc + async.hashCode()
            hc = 31 * hc + generator.hashCode()
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            this._params = updateTreeConnection(null, params)
            this._body = updateTreeConnection(null, body)
        }
    }

    sealed interface Class : Tree {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (id != null && superClass != null) {
                    sharedCodeFormattingTemplate33
                } else if (id != null) {
                    sharedCodeFormattingTemplate34
                } else if (superClass != null) {
                    sharedCodeFormattingTemplate35
                } else {
                    sharedCodeFormattingTemplate36
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.decorators
                1 -> this.id ?: FormattableTreeGroup.empty
                2 -> this.superClass ?: FormattableTreeGroup.empty
                3 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        val decorators: Decorators
        val id: Identifier?
        val superClass: Expression?
        val body: ClassBody
        override fun deepCopy(): Class
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Class).decorators },
                { n -> (n as Class).id },
                { n -> (n as Class).superClass },
                { n -> (n as Class).body },
            )
        }
    }

    class ClassDeclaration(
        pos: Position,
        decorators: Decorators,
        id: Identifier,
        superClass: Expression?,
        body: ClassBody,
    ) : BaseTree(pos), Declaration, Class {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        private var _decorators: Decorators
        override var decorators: Decorators
            get() = _decorators
            set(newValue) { _decorators = updateTreeConnection(_decorators, newValue) }
        private var _id: Identifier
        override var id: Identifier
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private var _superClass: Expression?
        override var superClass: Expression?
            get() = _superClass
            set(newValue) { _superClass = updateTreeConnection(_superClass, newValue) }
        private var _body: ClassBody
        override var body: ClassBody
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): ClassDeclaration {
            return ClassDeclaration(pos, decorators = this.decorators.deepCopy(), id = this.id.deepCopy(), superClass = this.superClass?.deepCopy(), body = this.body.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ClassDeclaration && this.decorators == other.decorators && this.id == other.id && this.superClass == other.superClass && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = decorators.hashCode()
            hc = 31 * hc + id.hashCode()
            hc = 31 * hc + (superClass?.hashCode() ?: 0)
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._decorators = updateTreeConnection(null, decorators)
            this._id = updateTreeConnection(null, id)
            this._superClass = updateTreeConnection(null, superClass)
            this._body = updateTreeConnection(null, body)
        }
    }

    class ExceptionDeclaration(
        pos: Position,
        id: Pattern,
    ) : BaseTree(pos), Declaration {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate9
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
        private var _id: Pattern
        var id: Pattern
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        override fun deepCopy(): ExceptionDeclaration {
            return ExceptionDeclaration(pos, id = this.id.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ExceptionDeclaration && this.id == other.id
        }
        override fun hashCode(): Int {
            return id.hashCode()
        }
        init {
            this._id = updateTreeConnection(null, id)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ExceptionDeclaration).id },
            )
        }
    }

    class VariableDeclaration(
        pos: Position,
        declarations: Iterable<VariableDeclarator>,
        var kind: DeclarationKind,
    ) : BaseTree(pos), Declaration, LoopLeft {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (kind == DeclarationKind.Const && needsSemicolon) {
                    sharedCodeFormattingTemplate37
                } else if (kind == DeclarationKind.Const) {
                    sharedCodeFormattingTemplate38
                } else if (kind == DeclarationKind.Let && needsSemicolon) {
                    sharedCodeFormattingTemplate39
                } else if (kind == DeclarationKind.Let) {
                    sharedCodeFormattingTemplate40
                } else if (needsSemicolon) {
                    sharedCodeFormattingTemplate41
                } else {
                    sharedCodeFormattingTemplate42
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.declarations)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _declarations: MutableList<VariableDeclarator> = mutableListOf()
        var declarations: List<VariableDeclarator>
            get() = _declarations
            set(newValue) { updateTreeConnections(_declarations, newValue) }

        /**
         * VariableDeclarations are semicolon terminated when in a statement context or in
         *
         *     for (declaration; condition; increment)
         *
         * but not in
         *
         *     for (declaration of iterable)
         *     for (declaration in object)
         */
        val needsSemicolon: Boolean
            get() =
                (this.parent as? ForOfStatement)?.left != this &&
                    (this.parent !is ExportDeclaration)
        override fun deepCopy(): VariableDeclaration {
            return VariableDeclaration(pos, declarations = this.declarations.deepCopy(), kind = this.kind)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is VariableDeclaration && this.declarations == other.declarations && this.kind == other.kind
        }
        override fun hashCode(): Int {
            var hc = declarations.hashCode()
            hc = 31 * hc + kind.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._declarations, declarations)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as VariableDeclaration).declarations },
            )
        }
    }

    class ArrayPattern(
        pos: Position,
        elements: Iterable<ArrayPatternElement>,
    ) : BaseTree(pos), Pattern {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate43
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.elements)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _elements: MutableList<ArrayPatternElement> = mutableListOf()
        var elements: List<ArrayPatternElement>
            get() = _elements
            set(newValue) { updateTreeConnections(_elements, newValue) }
        override fun deepCopy(): ArrayPattern {
            return ArrayPattern(pos, elements = this.elements.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ArrayPattern && this.elements == other.elements
        }
        override fun hashCode(): Int {
            return elements.hashCode()
        }
        init {
            updateTreeConnections(this._elements, elements)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ArrayPattern).elements },
            )
        }
    }

    class AssignmentPattern(
        pos: Position,
        left: Pattern,
        right: Expression,
    ) : BaseTree(pos), Pattern {
        override val operatorDefinition
            get() = JsOperatorDefinition.Eq
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate44
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
        private var _right: Expression
        var right: Expression
            get() = _right
            set(newValue) { _right = updateTreeConnection(_right, newValue) }
        override fun deepCopy(): AssignmentPattern {
            return AssignmentPattern(pos, left = this.left.deepCopy(), right = this.right.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AssignmentPattern && this.left == other.left && this.right == other.right
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
                { n -> (n as AssignmentPattern).left },
                { n -> (n as AssignmentPattern).right },
            )
        }
    }

    class ObjectPattern(
        pos: Position,
        properties: Iterable<ObjectPatternMemberOrMembers>,
    ) : BaseTree(pos), Pattern {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (properties.isNotEmpty()) {
                    sharedCodeFormattingTemplate13
                } else {
                    sharedCodeFormattingTemplate45
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.properties)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _properties: MutableList<ObjectPatternMemberOrMembers> = mutableListOf()
        var properties: List<ObjectPatternMemberOrMembers>
            get() = _properties
            set(newValue) { updateTreeConnections(_properties, newValue) }
        override fun deepCopy(): ObjectPattern {
            return ObjectPattern(pos, properties = this.properties.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ObjectPattern && this.properties == other.properties
        }
        override fun hashCode(): Int {
            return properties.hashCode()
        }
        init {
            updateTreeConnections(this._properties, properties)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ObjectPattern).properties },
            )
        }
    }

    sealed interface ObjectPatternMemberOrMembers : Tree {
        override fun deepCopy(): ObjectPatternMemberOrMembers
    }

    class RestElement(
        pos: Position,
        argument: Pattern,
    ) : BaseTree(pos), Pattern, ObjectPatternMemberOrMembers {
        override val operatorDefinition
            get() = JsOperatorDefinition.DotDotDot
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate46
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.argument
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _argument: Pattern
        var argument: Pattern
            get() = _argument
            set(newValue) { _argument = updateTreeConnection(_argument, newValue) }
        override fun deepCopy(): RestElement {
            return RestElement(pos, argument = this.argument.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is RestElement && this.argument == other.argument
        }
        override fun hashCode(): Int {
            return argument.hashCode()
        }
        init {
            this._argument = updateTreeConnection(null, argument)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as RestElement).argument },
            )
        }
    }

    class MemberExpression(
        pos: Position,
        obj: Callee,
        property: MemberKey,
        var computed: Boolean = false,
        var optional: Boolean = false,
    ) : BaseTree(pos), Pattern, SimpleRef {
        override val operatorDefinition
            get() =
                when {
                    computed ->
                        if (optional) {
                            JsOperatorDefinition.ComputedOptionalChaining
                        } else {
                            JsOperatorDefinition.ComputedMemberAccess
                        }
                    optional -> JsOperatorDefinition.OptionalChaining
                    else -> JsOperatorDefinition.MemberAccess
                }
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (computed && optional) {
                    sharedCodeFormattingTemplate47
                } else if (computed) {
                    sharedCodeFormattingTemplate48
                } else if (optional) {
                    sharedCodeFormattingTemplate49
                } else {
                    sharedCodeFormattingTemplate50
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.obj
                1 -> this.property
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _obj: Callee
        var obj: Callee
            get() = _obj
            set(newValue) { _obj = updateTreeConnection(_obj, newValue) }
        private var _property: MemberKey
        var property: MemberKey
            get() = _property
            set(newValue) { _property = updateTreeConnection(_property, newValue) }
        override fun deepCopy(): MemberExpression {
            return MemberExpression(pos, obj = this.obj.deepCopy(), property = this.property.deepCopy(), computed = this.computed, optional = this.optional)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MemberExpression && this.obj == other.obj && this.property == other.property && this.computed == other.computed && this.optional == other.optional
        }
        override fun hashCode(): Int {
            var hc = obj.hashCode()
            hc = 31 * hc + property.hashCode()
            hc = 31 * hc + computed.hashCode()
            hc = 31 * hc + optional.hashCode()
            return hc
        }
        init {
            this._obj = updateTreeConnection(null, obj)
            this._property = updateTreeConnection(null, property)
            require(!(this.property is PrivateName && this.computed))
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as MemberExpression).obj },
                { n -> (n as MemberExpression).property },
            )
        }
    }

    sealed interface ObjectMemberOrMembers : Tree {
        override fun deepCopy(): ObjectMemberOrMembers
    }

    class SpreadElement(
        pos: Position,
        argument: Expression,
    ) : BaseTree(pos), ObjectMemberOrMembers, Actual, ArrayElement {
        override val operatorDefinition
            get() = JsOperatorDefinition.DotDotDot
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate46
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.argument
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _argument: Expression
        var argument: Expression
            get() = _argument
            set(newValue) { _argument = updateTreeConnection(_argument, newValue) }
        override fun deepCopy(): SpreadElement {
            return SpreadElement(pos, argument = this.argument.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SpreadElement && this.argument == other.argument
        }
        override fun hashCode(): Int {
            return argument.hashCode()
        }
        init {
            this._argument = updateTreeConnection(null, argument)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SpreadElement).argument },
            )
        }
    }

    sealed interface ObjectMember : Tree, ObjectMemberOrMembers {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (computed) {
                    sharedCodeFormattingTemplate51
                } else {
                    sharedCodeFormattingTemplate52
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.key
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        val computed: Boolean
        val key: Expression
        override fun deepCopy(): ObjectMember
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ObjectMember).key },
            )
        }
    }

    sealed interface ObjectPatternMember : Tree, ObjectPatternMemberOrMembers {
        override fun deepCopy(): ObjectPatternMember
    }

    sealed interface ClassBodyMember : Tree {
        val key: MemberKey
        val computed: Boolean
        val static: Boolean
        override fun deepCopy(): ClassBodyMember
    }

    class ClassMethod(
        pos: Position,
        doc: MaybeJsDocComment,
        key: MemberKey,
        params: Formals,
        body: BlockStatement,
        override var async: Boolean = false,
        override var generator: Boolean = false,
        override var computed: Boolean = false,
        override var static: Boolean = false,
        var kind: ClassMethodKind,
    ) : BaseTree(pos), Function, ClassBodyMember {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (async && static && generator && kind == ClassMethodKind.Get && computed) {
                    sharedCodeFormattingTemplate53
                } else if (async && static && generator && kind == ClassMethodKind.Get) {
                    sharedCodeFormattingTemplate54
                } else if (async && static && generator && kind == ClassMethodKind.Set && computed) {
                    sharedCodeFormattingTemplate55
                } else if (async && static && generator && kind == ClassMethodKind.Set) {
                    sharedCodeFormattingTemplate56
                } else if (async && static && generator && computed) {
                    sharedCodeFormattingTemplate57
                } else if (async && static && generator) {
                    sharedCodeFormattingTemplate58
                } else if (async && static && kind == ClassMethodKind.Get && computed) {
                    sharedCodeFormattingTemplate59
                } else if (async && static && kind == ClassMethodKind.Get) {
                    sharedCodeFormattingTemplate60
                } else if (async && static && kind == ClassMethodKind.Set && computed) {
                    sharedCodeFormattingTemplate61
                } else if (async && static && kind == ClassMethodKind.Set) {
                    sharedCodeFormattingTemplate62
                } else if (async && static && computed) {
                    sharedCodeFormattingTemplate63
                } else if (async && static) {
                    sharedCodeFormattingTemplate64
                } else if (async && generator && kind == ClassMethodKind.Get && computed) {
                    sharedCodeFormattingTemplate65
                } else if (async && generator && kind == ClassMethodKind.Get) {
                    sharedCodeFormattingTemplate66
                } else if (async && generator && kind == ClassMethodKind.Set && computed) {
                    sharedCodeFormattingTemplate67
                } else if (async && generator && kind == ClassMethodKind.Set) {
                    sharedCodeFormattingTemplate68
                } else if (async && generator && computed) {
                    sharedCodeFormattingTemplate69
                } else if (async && generator) {
                    sharedCodeFormattingTemplate70
                } else if (async && kind == ClassMethodKind.Get && computed) {
                    sharedCodeFormattingTemplate71
                } else if (async && kind == ClassMethodKind.Get) {
                    sharedCodeFormattingTemplate72
                } else if (async && kind == ClassMethodKind.Set && computed) {
                    sharedCodeFormattingTemplate73
                } else if (async && kind == ClassMethodKind.Set) {
                    sharedCodeFormattingTemplate74
                } else if (async && computed) {
                    sharedCodeFormattingTemplate75
                } else if (async) {
                    sharedCodeFormattingTemplate76
                } else if (static && generator && kind == ClassMethodKind.Get && computed) {
                    sharedCodeFormattingTemplate77
                } else if (static && generator && kind == ClassMethodKind.Get) {
                    sharedCodeFormattingTemplate78
                } else if (static && generator && kind == ClassMethodKind.Set && computed) {
                    sharedCodeFormattingTemplate79
                } else if (static && generator && kind == ClassMethodKind.Set) {
                    sharedCodeFormattingTemplate80
                } else if (static && generator && computed) {
                    sharedCodeFormattingTemplate81
                } else if (static && generator) {
                    sharedCodeFormattingTemplate82
                } else if (static && kind == ClassMethodKind.Get && computed) {
                    sharedCodeFormattingTemplate83
                } else if (static && kind == ClassMethodKind.Get) {
                    sharedCodeFormattingTemplate84
                } else if (static && kind == ClassMethodKind.Set && computed) {
                    sharedCodeFormattingTemplate85
                } else if (static && kind == ClassMethodKind.Set) {
                    sharedCodeFormattingTemplate86
                } else if (static && computed) {
                    sharedCodeFormattingTemplate87
                } else if (static) {
                    sharedCodeFormattingTemplate88
                } else if (generator && kind == ClassMethodKind.Get && computed) {
                    sharedCodeFormattingTemplate89
                } else if (generator && kind == ClassMethodKind.Get) {
                    sharedCodeFormattingTemplate90
                } else if (generator && kind == ClassMethodKind.Set && computed) {
                    sharedCodeFormattingTemplate91
                } else if (generator && kind == ClassMethodKind.Set) {
                    sharedCodeFormattingTemplate92
                } else if (generator && computed) {
                    sharedCodeFormattingTemplate93
                } else if (generator) {
                    sharedCodeFormattingTemplate94
                } else if (kind == ClassMethodKind.Get && computed) {
                    sharedCodeFormattingTemplate95
                } else if (kind == ClassMethodKind.Get) {
                    sharedCodeFormattingTemplate96
                } else if (kind == ClassMethodKind.Set && computed) {
                    sharedCodeFormattingTemplate97
                } else if (kind == ClassMethodKind.Set) {
                    sharedCodeFormattingTemplate98
                } else if (computed) {
                    sharedCodeFormattingTemplate99
                } else {
                    sharedCodeFormattingTemplate100
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.doc
                1 -> this.key
                2 -> this.params
                3 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _doc: MaybeJsDocComment
        var doc: MaybeJsDocComment
            get() = _doc
            set(newValue) { _doc = updateTreeConnection(_doc, newValue) }
        private var _key: MemberKey
        override var key: MemberKey
            get() = _key
            set(newValue) { _key = updateTreeConnection(_key, newValue) }
        private var _params: Formals
        override var params: Formals
            get() = _params
            set(newValue) { _params = updateTreeConnection(_params, newValue) }
        private var _body: BlockStatement
        override var body: BlockStatement
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override val id: Identifier?
            get() = if (!this.computed) { this.key as? Identifier } else { null }
        override fun deepCopy(): ClassMethod {
            return ClassMethod(pos, doc = this.doc.deepCopy(), key = this.key.deepCopy(), params = this.params.deepCopy(), body = this.body.deepCopy(), async = this.async, generator = this.generator, computed = this.computed, static = this.static, kind = this.kind)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ClassMethod && this.doc == other.doc && this.key == other.key && this.params == other.params && this.body == other.body && this.async == other.async && this.generator == other.generator && this.computed == other.computed && this.static == other.static && this.kind == other.kind
        }
        override fun hashCode(): Int {
            var hc = doc.hashCode()
            hc = 31 * hc + key.hashCode()
            hc = 31 * hc + params.hashCode()
            hc = 31 * hc + body.hashCode()
            hc = 31 * hc + async.hashCode()
            hc = 31 * hc + generator.hashCode()
            hc = 31 * hc + computed.hashCode()
            hc = 31 * hc + static.hashCode()
            hc = 31 * hc + kind.hashCode()
            return hc
        }
        init {
            this._doc = updateTreeConnection(null, doc)
            this._key = updateTreeConnection(null, key)
            this._params = updateTreeConnection(null, params)
            this._body = updateTreeConnection(null, body)
            require(!(this.key is PrivateName && this.computed))
            require(this.kind != ClassMethodKind.Constructor || (!this.computed && !this.async && !this.static && !this.generator && "constructor" == (this.key as? Identifier)?.name?.text))
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ClassMethod).doc },
                { n -> (n as ClassMethod).key },
                { n -> (n as ClassMethod).params },
                { n -> (n as ClassMethod).body },
            )
        }
    }

    class ObjectMethod(
        pos: Position,
        key: Expression,
        params: Formals,
        body: BlockStatement?,
        override var computed: Boolean = false,
        var kind: ObjectMethodKind = ObjectMethodKind.Method,
    ) : BaseTree(pos), Function, ObjectMember {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (kind == ObjectMethodKind.Get && computed && body != null) {
                    sharedCodeFormattingTemplate101
                } else if (kind == ObjectMethodKind.Get && computed) {
                    sharedCodeFormattingTemplate102
                } else if (kind == ObjectMethodKind.Get && body != null) {
                    sharedCodeFormattingTemplate103
                } else if (kind == ObjectMethodKind.Get) {
                    sharedCodeFormattingTemplate104
                } else if (kind == ObjectMethodKind.Set && computed && body != null) {
                    sharedCodeFormattingTemplate105
                } else if (kind == ObjectMethodKind.Set && computed) {
                    sharedCodeFormattingTemplate106
                } else if (kind == ObjectMethodKind.Set && body != null) {
                    sharedCodeFormattingTemplate107
                } else if (kind == ObjectMethodKind.Set) {
                    sharedCodeFormattingTemplate108
                } else if (computed && body != null) {
                    sharedCodeFormattingTemplate109
                } else if (computed) {
                    sharedCodeFormattingTemplate110
                } else if (body != null) {
                    sharedCodeFormattingTemplate111
                } else {
                    sharedCodeFormattingTemplate15
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.key
                1 -> this.params
                2 -> this.body ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _key: Expression
        override var key: Expression
            get() = _key
            set(newValue) { _key = updateTreeConnection(_key, newValue) }
        private var _params: Formals
        override var params: Formals
            get() = _params
            set(newValue) { _params = updateTreeConnection(_params, newValue) }
        private var _body: BlockStatement?
        override var body: BlockStatement?
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override val async: Boolean
            get() = false
        override val generator: Boolean
            get() = false
        override val id: Identifier?
            get() = key as? Identifier
        override fun deepCopy(): ObjectMethod {
            return ObjectMethod(pos, key = this.key.deepCopy(), params = this.params.deepCopy(), body = this.body?.deepCopy(), computed = this.computed, kind = this.kind)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ObjectMethod && this.key == other.key && this.params == other.params && this.body == other.body && this.computed == other.computed && this.kind == other.kind
        }
        override fun hashCode(): Int {
            var hc = key.hashCode()
            hc = 31 * hc + params.hashCode()
            hc = 31 * hc + (body?.hashCode() ?: 0)
            hc = 31 * hc + computed.hashCode()
            hc = 31 * hc + kind.hashCode()
            return hc
        }
        init {
            this._key = updateTreeConnection(null, key)
            this._params = updateTreeConnection(null, params)
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ObjectMethod).key },
                { n -> (n as ObjectMethod).params },
                { n -> (n as ObjectMethod).body },
            )
        }
    }

    class FunctionExpression(
        pos: Position,
        id: Identifier?,
        params: Formals,
        body: BlockStatement?,
        override var async: Boolean = false,
        override var generator: Boolean = false,
    ) : BaseTree(pos), Function, Expression {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        private var _id: Identifier?
        override var id: Identifier?
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private var _params: Formals
        override var params: Formals
            get() = _params
            set(newValue) { _params = updateTreeConnection(_params, newValue) }
        private var _body: BlockStatement?
        override var body: BlockStatement?
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): FunctionExpression {
            return FunctionExpression(pos, id = this.id?.deepCopy(), params = this.params.deepCopy(), body = this.body?.deepCopy(), async = this.async, generator = this.generator)
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FunctionExpression && this.id == other.id && this.params == other.params && this.body == other.body && this.async == other.async && this.generator == other.generator
        }
        override fun hashCode(): Int {
            var hc = (id?.hashCode() ?: 0)
            hc = 31 * hc + params.hashCode()
            hc = 31 * hc + (body?.hashCode() ?: 0)
            hc = 31 * hc + async.hashCode()
            hc = 31 * hc + generator.hashCode()
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            this._params = updateTreeConnection(null, params)
            this._body = updateTreeConnection(null, body)
        }
    }

    class ArrowFunctionExpression(
        pos: Position,
        params: Formals,
        body: BlockStatement,
    ) : BaseTree(pos), Function, Expression {
        override val operatorDefinition
            get() = JsOperatorDefinition.ArrowFunction
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (parenthesizeBody) {
                    sharedCodeFormattingTemplate112
                } else if (bodyExpression != null) {
                    sharedCodeFormattingTemplate113
                } else {
                    sharedCodeFormattingTemplate114
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.params
                1 -> this.bodyExpression ?: FormattableTreeGroup.empty
                2 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _params: Formals
        override var params: Formals
            get() = _params
            set(newValue) { _params = updateTreeConnection(_params, newValue) }
        private var _body: BlockStatement
        override var body: BlockStatement
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override val async: Boolean
            get() = false
        override val generator: Boolean
            get() = false
        override val id: Identifier?
            get() = null
        val parenthesizeBody: Boolean
            get() = bodyExpression is ObjectExpression
        val bodyExpression: Expression?
            get() =
                if (body.childCount == 1) {
                    (body.body[0] as? ReturnStatement)?.expr
                } else {
                    null
                }
        val expression: Boolean
            get() = bodyExpression != null
        override fun deepCopy(): ArrowFunctionExpression {
            return ArrowFunctionExpression(pos, params = this.params.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ArrowFunctionExpression && this.params == other.params && this.body == other.body
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
                { n -> (n as ArrowFunctionExpression).params },
                { n -> (n as ArrowFunctionExpression).body },
            )
        }
        constructor(pos: Position, params: Formals, body: Expression) : this(
            pos,
            params,
            BlockStatement(body.pos, listOf(ReturnStatement(body.pos, body))),
        )
    }

    class Formals(
        pos: Position,
        params: Iterable<Param>,
        returnType: Type? = null,
    ) : BaseTree(pos) {
        override val operatorDefinition
            get() = JsOperatorDefinition.Grouping
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (returnType != null) {
                    sharedCodeFormattingTemplate115
                } else {
                    sharedCodeFormattingTemplate116
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.params)
                1 -> this.returnType ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _params: MutableList<Param> = mutableListOf()
        var params: List<Param>
            get() = _params
            set(newValue) { updateTreeConnections(_params, newValue) }
        private var _returnType: Type?
        var returnType: Type?
            get() = _returnType
            set(newValue) { _returnType = updateTreeConnection(_returnType, newValue) }
        override fun deepCopy(): Formals {
            return Formals(pos, params = this.params.deepCopy(), returnType = this.returnType?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Formals && this.params == other.params && this.returnType == other.returnType
        }
        override fun hashCode(): Int {
            var hc = params.hashCode()
            hc = 31 * hc + (returnType?.hashCode() ?: 0)
            return hc
        }
        init {
            updateTreeConnections(this._params, params)
            this._returnType = updateTreeConnection(null, returnType)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Formals).params },
                { n -> (n as Formals).returnType },
            )
        }
    }

    class BlockStatement(
        pos: Position,
        body: Iterable<Statement>,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate117
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
        private val _body: MutableList<Statement> = mutableListOf()
        var body: List<Statement>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        override fun deepCopy(): BlockStatement {
            return BlockStatement(pos, body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BlockStatement && this.body == other.body
        }
        override fun hashCode(): Int {
            return body.hashCode()
        }
        init {
            updateTreeConnections(this._body, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as BlockStatement).body },
            )
        }

        /**
         * The statements of the body, but with a null parent so that they may
         * be reused as another node's children.
         *
         * Destructive, so after being called, <code>this</code> will be an empty block.
         */
        fun takeBody(): List<Statement> {
            val statements = body.toList()
            this.body = emptyList()
            return statements
        }
    }

    class Param(
        pos: Position,
        pattern: Pattern,
        type: Type? = null,
    ) : BaseTree(pos) {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (type != null) {
                    sharedCodeFormattingTemplate118
                } else {
                    sharedCodeFormattingTemplate9
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
        override fun deepCopy(): Param {
            return Param(pos, pattern = this.pattern.deepCopy(), type = this.type?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Param && this.pattern == other.pattern && this.type == other.type
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
                { n -> (n as Param).pattern },
                { n -> (n as Param).type },
            )
        }
    }

    class Decorators(
        pos: Position,
        decorators: Iterable<Decorator>,
    ) : BaseTree(pos) {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate119
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.decorators)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _decorators: MutableList<Decorator> = mutableListOf()
        var decorators: List<Decorator>
            get() = _decorators
            set(newValue) { updateTreeConnections(_decorators, newValue) }
        override fun deepCopy(): Decorators {
            return Decorators(pos, decorators = this.decorators.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Decorators && this.decorators == other.decorators
        }
        override fun hashCode(): Int {
            return decorators.hashCode()
        }
        init {
            updateTreeConnections(this._decorators, decorators)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Decorators).decorators },
            )
        }
    }

    class Decorator(
        pos: Position,
        expression: Expression,
    ) : BaseTree(pos) {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate120
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expression
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expression: Expression
        var expression: Expression
            get() = _expression
            set(newValue) { _expression = updateTreeConnection(_expression, newValue) }
        override fun deepCopy(): Decorator {
            return Decorator(pos, expression = this.expression.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Decorator && this.expression == other.expression
        }
        override fun hashCode(): Int {
            return expression.hashCode()
        }
        init {
            this._expression = updateTreeConnection(null, expression)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Decorator).expression },
            )
        }
    }

    class ClassExpression(
        pos: Position,
        decorators: Decorators,
        id: Identifier?,
        superClass: Expression?,
        body: ClassBody,
    ) : BaseTree(pos), Class, Expression {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        private var _decorators: Decorators
        override var decorators: Decorators
            get() = _decorators
            set(newValue) { _decorators = updateTreeConnection(_decorators, newValue) }
        private var _id: Identifier?
        override var id: Identifier?
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private var _superClass: Expression?
        override var superClass: Expression?
            get() = _superClass
            set(newValue) { _superClass = updateTreeConnection(_superClass, newValue) }
        private var _body: ClassBody
        override var body: ClassBody
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): ClassExpression {
            return ClassExpression(pos, decorators = this.decorators.deepCopy(), id = this.id?.deepCopy(), superClass = this.superClass?.deepCopy(), body = this.body.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ClassExpression && this.decorators == other.decorators && this.id == other.id && this.superClass == other.superClass && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = decorators.hashCode()
            hc = 31 * hc + (id?.hashCode() ?: 0)
            hc = 31 * hc + (superClass?.hashCode() ?: 0)
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._decorators = updateTreeConnection(null, decorators)
            this._id = updateTreeConnection(null, id)
            this._superClass = updateTreeConnection(null, superClass)
            this._body = updateTreeConnection(null, body)
        }
    }

    class ClassBody(
        pos: Position,
        body: Iterable<ClassBodyMember>,
    ) : BaseTree(pos) {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate117
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
        private val _body: MutableList<ClassBodyMember> = mutableListOf()
        var body: List<ClassBodyMember>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        override fun deepCopy(): ClassBody {
            return ClassBody(pos, body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ClassBody && this.body == other.body
        }
        override fun hashCode(): Int {
            return body.hashCode()
        }
        init {
            updateTreeConnections(this._body, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ClassBody).body },
            )
        }
    }

    class ClassProperty(
        pos: Position,
        doc: MaybeJsDocComment,
        key: MemberKey,
        value: Expression?,
        override var computed: Boolean = false,
        override var static: Boolean = false,
    ) : BaseTree(pos), ClassBodyMember {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (static && computed && value != null) {
                    sharedCodeFormattingTemplate121
                } else if (static && computed) {
                    sharedCodeFormattingTemplate122
                } else if (static && value != null) {
                    sharedCodeFormattingTemplate123
                } else if (static) {
                    sharedCodeFormattingTemplate124
                } else if (computed && value != null) {
                    sharedCodeFormattingTemplate125
                } else if (computed) {
                    sharedCodeFormattingTemplate126
                } else if (value != null) {
                    sharedCodeFormattingTemplate127
                } else {
                    sharedCodeFormattingTemplate128
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.doc
                1 -> this.key
                2 -> this.value ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _doc: MaybeJsDocComment
        var doc: MaybeJsDocComment
            get() = _doc
            set(newValue) { _doc = updateTreeConnection(_doc, newValue) }
        private var _key: MemberKey
        override var key: MemberKey
            get() = _key
            set(newValue) { _key = updateTreeConnection(_key, newValue) }
        private var _value: Expression?
        var value: Expression?
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): ClassProperty {
            return ClassProperty(pos, doc = this.doc.deepCopy(), key = this.key.deepCopy(), value = this.value?.deepCopy(), computed = this.computed, static = this.static)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ClassProperty && this.doc == other.doc && this.key == other.key && this.value == other.value && this.computed == other.computed && this.static == other.static
        }
        override fun hashCode(): Int {
            var hc = doc.hashCode()
            hc = 31 * hc + key.hashCode()
            hc = 31 * hc + (value?.hashCode() ?: 0)
            hc = 31 * hc + computed.hashCode()
            hc = 31 * hc + static.hashCode()
            return hc
        }
        init {
            this._doc = updateTreeConnection(null, doc)
            this._key = updateTreeConnection(null, key)
            this._value = updateTreeConnection(null, value)
            require(!(this.key is PrivateName && this.computed))
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ClassProperty).doc },
                { n -> (n as ClassProperty).key },
                { n -> (n as ClassProperty).value },
            )
        }
    }

    class VariableDeclarator(
        pos: Position,
        id: Pattern,
        init: Expression?,
    ) : BaseTree(pos) {
        override val operatorDefinition
            get() = JsOperatorDefinition.Eq
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (init != null) {
                    sharedCodeFormattingTemplate44
                } else {
                    sharedCodeFormattingTemplate9
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id
                1 -> this.init ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: Pattern
        var id: Pattern
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private var _init: Expression?
        var init: Expression?
            get() = _init
            set(newValue) { _init = updateTreeConnection(_init, newValue) }
        override fun deepCopy(): VariableDeclarator {
            return VariableDeclarator(pos, id = this.id.deepCopy(), init = this.init?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is VariableDeclarator && this.id == other.id && this.init == other.init
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + (init?.hashCode() ?: 0)
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            this._init = updateTreeConnection(null, init)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as VariableDeclarator).id },
                { n -> (n as VariableDeclarator).init },
            )
        }
    }

    /** A generic type expression */
    class GenericRef(
        pos: Position,
        id: SimpleRef,
        args: Iterable<Type>,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition
            get() = JsOperatorDefinition.GenericRef
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate129
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.id
                1 -> FormattableTreeGroup(this.args)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _id: SimpleRef
        var id: SimpleRef
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        private val _args: MutableList<Type> = mutableListOf()
        var args: List<Type>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): GenericRef {
            return GenericRef(pos, id = this.id.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is GenericRef && this.id == other.id && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
            updateTreeConnections(this._args, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as GenericRef).id },
                { n -> (n as GenericRef).args },
            )
        }
    }

    sealed interface Call : Tree {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (optional) {
                    sharedCodeFormattingTemplate130
                } else {
                    sharedCodeFormattingTemplate131
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.callee
                1 -> FormattableTreeGroup(this.arguments)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        val callee: Callee
        val optional: Boolean
        val arguments: List<Actual>
        override fun deepCopy(): Call
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Call).callee },
                { n -> (n as Call).arguments },
            )
        }
    }

    class CallExpression(
        pos: Position,
        callee: Callee,
        arguments: Iterable<Actual>,
        override var optional: Boolean = false,
    ) : BaseTree(pos), Expression, Call {
        override val operatorDefinition
            get() = JsOperatorDefinition.FunctionCall
        private var _callee: Callee
        override var callee: Callee
            get() = _callee
            set(newValue) { _callee = updateTreeConnection(_callee, newValue) }
        private val _arguments: MutableList<Actual> = mutableListOf()
        override var arguments: List<Actual>
            get() = _arguments
            set(newValue) { updateTreeConnections(_arguments, newValue) }
        override fun deepCopy(): CallExpression {
            return CallExpression(pos, callee = this.callee.deepCopy(), arguments = this.arguments.deepCopy(), optional = this.optional)
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is CallExpression && this.callee == other.callee && this.arguments == other.arguments && this.optional == other.optional
        }
        override fun hashCode(): Int {
            var hc = callee.hashCode()
            hc = 31 * hc + arguments.hashCode()
            hc = 31 * hc + optional.hashCode()
            return hc
        }
        init {
            this._callee = updateTreeConnection(null, callee)
            updateTreeConnections(this._arguments, arguments)
        }
    }

    class NewExpression(
        pos: Position,
        callee: Callee,
        arguments: Iterable<Actual>,
        override var optional: Boolean = false,
    ) : BaseTree(pos), Expression, Call {
        override val operatorDefinition
            get() = JsOperatorDefinition.NewWithArgs
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (optional) {
                    sharedCodeFormattingTemplate132
                } else {
                    sharedCodeFormattingTemplate133
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.callee
                1 -> FormattableTreeGroup(this.arguments)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _callee: Callee
        override var callee: Callee
            get() = _callee
            set(newValue) { _callee = updateTreeConnection(_callee, newValue) }
        private val _arguments: MutableList<Actual> = mutableListOf()
        override var arguments: List<Actual>
            get() = _arguments
            set(newValue) { updateTreeConnections(_arguments, newValue) }
        override fun deepCopy(): NewExpression {
            return NewExpression(pos, callee = this.callee.deepCopy(), arguments = this.arguments.deepCopy(), optional = this.optional)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is NewExpression && this.callee == other.callee && this.arguments == other.arguments && this.optional == other.optional
        }
        override fun hashCode(): Int {
            var hc = callee.hashCode()
            hc = 31 * hc + arguments.hashCode()
            hc = 31 * hc + optional.hashCode()
            return hc
        }
        init {
            this._callee = updateTreeConnection(null, callee)
            updateTreeConnections(this._arguments, arguments)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as NewExpression).callee },
                { n -> (n as NewExpression).arguments },
            )
        }
    }

    class ArrayExpression(
        pos: Position,
        elements: Iterable<ArrayElement>,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate43
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.elements)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _elements: MutableList<ArrayElement> = mutableListOf()
        var elements: List<ArrayElement>
            get() = _elements
            set(newValue) { updateTreeConnections(_elements, newValue) }
        override fun deepCopy(): ArrayExpression {
            return ArrayExpression(pos, elements = this.elements.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ArrayExpression && this.elements == other.elements
        }
        override fun hashCode(): Int {
            return elements.hashCode()
        }
        init {
            updateTreeConnections(this._elements, elements)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ArrayExpression).elements },
            )
        }
    }

    sealed interface InfixExpression : Tree, Expression {
        override val operatorDefinition
            get() = operatorTable.getValue(operator.tokenText)
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate111
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.left
                1 -> this.operator
                2 -> this.right
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        val left: Expression
        val operator: Operator
        val right: Expression
        val operatorTable: Map<String, JsOperatorDefinition>
        override fun deepCopy(): InfixExpression
        override val childMemberRelationships
            get() = cmr
        companion object {
            operator fun invoke(
                pos: Position,
                left: Expression,
                operator: Operator,
                right: Expression,
            ): Expression = when (operator.tokenText) {
                in binaryOperator -> BinaryExpression(pos, left, operator, right)
                in logicalOperator -> LogicalExpression(pos, left, operator, right)
                in assignmentOperator -> AssignmentExpression(pos, left as Pattern, operator, right)
                else -> throw IllegalArgumentException(operator.tokenText)
            }

            val binaryOperator = mapOf(
                "==" to JsOperatorDefinition.EqEq,
                "!=" to JsOperatorDefinition.BangEq,
                "===" to JsOperatorDefinition.EqEqEq,
                "!==" to JsOperatorDefinition.BangEqEq,
                "<" to JsOperatorDefinition.Lt,
                "<=" to JsOperatorDefinition.Lte,
                ">" to JsOperatorDefinition.Gt,
                ">=" to JsOperatorDefinition.Gte,
                "<<" to JsOperatorDefinition.LtLt,
                ">>" to JsOperatorDefinition.GtGt,
                ">>>" to JsOperatorDefinition.GtGtGt,
                "+" to JsOperatorDefinition.Plus,
                "-" to JsOperatorDefinition.Minus,
                "*" to JsOperatorDefinition.Star,
                "**" to JsOperatorDefinition.Exp,
                "/" to JsOperatorDefinition.Div,
                "%" to JsOperatorDefinition.Rem,
                "|" to JsOperatorDefinition.BitwiseOr,
                "^" to JsOperatorDefinition.BitwiseXor,
                "&" to JsOperatorDefinition.BitwiseAnd,
                "in" to JsOperatorDefinition.In,
                "instanceof" to JsOperatorDefinition.Instanceof,
                // "|>" to JsOperatorDefinition.Pipeline,
            )

            val logicalOperator = mapOf(
                "||" to JsOperatorDefinition.LogicalOr,
                "&&" to JsOperatorDefinition.LogicalAnd,
                "??" to JsOperatorDefinition.NullishCoalescing,
            )

            val assignmentOperator = mapOf(
                "=" to JsOperatorDefinition.Eq,
                "+=" to JsOperatorDefinition.PlusEq,
                "-=" to JsOperatorDefinition.MinusEq,
                "*=" to JsOperatorDefinition.StarEq,
                "/=" to JsOperatorDefinition.DivEq,
                "%=" to JsOperatorDefinition.PctEq,
                "<<=" to JsOperatorDefinition.LtLtEq,
                ">>=" to JsOperatorDefinition.GtGtEq,
                ">>>=" to JsOperatorDefinition.GtGtGtEq,
                "|=" to JsOperatorDefinition.BarEq,
                "^=" to JsOperatorDefinition.CaretEq,
                "&=" to JsOperatorDefinition.AmpEq,
            )
            private val cmr = ChildMemberRelationships(
                { n -> (n as InfixExpression).left },
                { n -> (n as InfixExpression).operator },
                { n -> (n as InfixExpression).right },
            )
        }
        fun checkOperator() {
            require(operatorTable[operator.tokenText] == operatorDefinition)
        }
    }

    class UnaryExpression(
        pos: Position,
        operator: Operator,
        argument: Expression,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition
            get() = unaryOperator[operator.tokenText]
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate15
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.operator
                1 -> this.argument
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _operator: Operator
        var operator: Operator
            get() = _operator
            set(newValue) { _operator = updateTreeConnection(_operator, newValue) }
        private var _argument: Expression
        var argument: Expression
            get() = _argument
            set(newValue) { _argument = updateTreeConnection(_argument, newValue) }
        override fun deepCopy(): UnaryExpression {
            return UnaryExpression(pos, operator = this.operator.deepCopy(), argument = this.argument.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is UnaryExpression && this.operator == other.operator && this.argument == other.argument
        }
        override fun hashCode(): Int {
            var hc = operator.hashCode()
            hc = 31 * hc + argument.hashCode()
            return hc
        }
        init {
            this._operator = updateTreeConnection(null, operator)
            this._argument = updateTreeConnection(null, argument)
        }
        companion object {
            val unaryOperator = mapOf(
                "-" to JsOperatorDefinition.UnaryMinus,
                "+" to JsOperatorDefinition.UnaryPlus,
                "!" to JsOperatorDefinition.LogicalNot,
                "~" to JsOperatorDefinition.BitwiseNot,
                "typeof" to JsOperatorDefinition.Typeof,
                "void" to JsOperatorDefinition.Void,
                "await" to JsOperatorDefinition.Await,
                "delete" to JsOperatorDefinition.Delete,
                // "throw" to ...
            )
            private val cmr = ChildMemberRelationships(
                { n -> (n as UnaryExpression).operator },
                { n -> (n as UnaryExpression).argument },
            )
        }
    }

    class UpdateExpression(
        pos: Position,
        argument: Expression,
        operator: Operator,
        var prefix: Boolean,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition
            get() = updateOperator.getValue(operator.tokenText)[if (prefix) 0 else 1]
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (prefix) {
                    sharedCodeFormattingTemplate134
                } else {
                    sharedCodeFormattingTemplate15
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.argument
                1 -> this.operator
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _argument: Expression
        var argument: Expression
            get() = _argument
            set(newValue) { _argument = updateTreeConnection(_argument, newValue) }
        private var _operator: Operator
        var operator: Operator
            get() = _operator
            set(newValue) { _operator = updateTreeConnection(_operator, newValue) }
        override fun deepCopy(): UpdateExpression {
            return UpdateExpression(pos, argument = this.argument.deepCopy(), operator = this.operator.deepCopy(), prefix = this.prefix)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is UpdateExpression && this.argument == other.argument && this.operator == other.operator && this.prefix == other.prefix
        }
        override fun hashCode(): Int {
            var hc = argument.hashCode()
            hc = 31 * hc + operator.hashCode()
            hc = 31 * hc + prefix.hashCode()
            return hc
        }
        init {
            this._argument = updateTreeConnection(null, argument)
            this._operator = updateTreeConnection(null, operator)
            require(operator.tokenText in updateOperator)
        }
        companion object {
            val updateOperator = mapOf(
                "++" to listOf(JsOperatorDefinition.PreIncr, JsOperatorDefinition.PostIncr),
                "--" to listOf(JsOperatorDefinition.PreDecr, JsOperatorDefinition.PostDecr),
            )
            private val cmr = ChildMemberRelationships(
                { n -> (n as UpdateExpression).argument },
                { n -> (n as UpdateExpression).operator },
            )
        }
    }

    class ConditionalExpression(
        pos: Position,
        test: Expression,
        consequent: Expression,
        alternate: Expression,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition
            get() = JsOperatorDefinition.Conditional
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate135
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.test
                1 -> this.consequent
                2 -> this.alternate
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _test: Expression
        var test: Expression
            get() = _test
            set(newValue) { _test = updateTreeConnection(_test, newValue) }
        private var _consequent: Expression
        var consequent: Expression
            get() = _consequent
            set(newValue) { _consequent = updateTreeConnection(_consequent, newValue) }
        private var _alternate: Expression
        var alternate: Expression
            get() = _alternate
            set(newValue) { _alternate = updateTreeConnection(_alternate, newValue) }
        override fun deepCopy(): ConditionalExpression {
            return ConditionalExpression(pos, test = this.test.deepCopy(), consequent = this.consequent.deepCopy(), alternate = this.alternate.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ConditionalExpression && this.test == other.test && this.consequent == other.consequent && this.alternate == other.alternate
        }
        override fun hashCode(): Int {
            var hc = test.hashCode()
            hc = 31 * hc + consequent.hashCode()
            hc = 31 * hc + alternate.hashCode()
            return hc
        }
        init {
            this._test = updateTreeConnection(null, test)
            this._consequent = updateTreeConnection(null, consequent)
            this._alternate = updateTreeConnection(null, alternate)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ConditionalExpression).test },
                { n -> (n as ConditionalExpression).consequent },
                { n -> (n as ConditionalExpression).alternate },
            )
        }
    }

    class BooleanLiteral(
        pos: Position,
        var value: Boolean,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (value) {
                    sharedCodeFormattingTemplate136
                } else {
                    sharedCodeFormattingTemplate137
                }
        override val formatElementCount
            get() = 0
        override fun deepCopy(): BooleanLiteral {
            return BooleanLiteral(pos, value = this.value)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BooleanLiteral && this.value == other.value
        }
        override fun hashCode(): Int {
            return value.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class NullLiteral(
        pos: Position,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate138
        override val formatElementCount
            get() = 0
        override fun deepCopy(): NullLiteral {
            return NullLiteral(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is NullLiteral
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class VoidType(
        pos: Position,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate139
        override val formatElementCount
            get() = 0
        override fun deepCopy(): VoidType {
            return VoidType(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is VoidType
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class ObjectExpression(
        pos: Position,
        properties: Iterable<ObjectMemberOrMembers>,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (properties.isNotEmpty()) {
                    sharedCodeFormattingTemplate13
                } else {
                    sharedCodeFormattingTemplate45
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.properties)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _properties: MutableList<ObjectMemberOrMembers> = mutableListOf()
        var properties: List<ObjectMemberOrMembers>
            get() = _properties
            set(newValue) { updateTreeConnections(_properties, newValue) }
        override fun deepCopy(): ObjectExpression {
            return ObjectExpression(pos, properties = this.properties.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ObjectExpression && this.properties == other.properties
        }
        override fun hashCode(): Int {
            return properties.hashCode()
        }
        init {
            updateTreeConnections(this._properties, properties)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ObjectExpression).properties },
            )
        }
    }

    /** Comma expression */
    class SequenceExpression(
        pos: Position,
        expressions: Iterable<Expression>,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition
            get() = JsOperatorDefinition.Comma
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate140
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.expressions)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _expressions: MutableList<Expression> = mutableListOf()
        var expressions: List<Expression>
            get() = _expressions
            set(newValue) { updateTreeConnections(_expressions, newValue) }
        override fun deepCopy(): SequenceExpression {
            return SequenceExpression(pos, expressions = this.expressions.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SequenceExpression && this.expressions == other.expressions
        }
        override fun hashCode(): Int {
            return expressions.hashCode()
        }
        init {
            updateTreeConnections(this._expressions, expressions)
            require(this.expressions.size >= 2)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SequenceExpression).expressions },
            )
        }
    }

    /** Back-ticked string expression. */
    class TemplateExpression(
        pos: Position,
        quasis: Iterable<TemplateElement>,
        expressions: Iterable<Expression>,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            JsTemplateHelpers.renderTemplateTo(
                tokenSink,
                pos,
                this.quasis,
                this.expressions,
            )
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        private val _quasis: MutableList<TemplateElement> = mutableListOf()
        var quasis: List<TemplateElement>
            get() = _quasis
            set(newValue) { updateTreeConnections(_quasis, newValue) }
        private val _expressions: MutableList<Expression> = mutableListOf()
        var expressions: List<Expression>
            get() = _expressions
            set(newValue) { updateTreeConnections(_expressions, newValue) }
        override fun deepCopy(): TemplateExpression {
            return TemplateExpression(pos, quasis = this.quasis.deepCopy(), expressions = this.expressions.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TemplateExpression && this.quasis == other.quasis && this.expressions == other.expressions
        }
        override fun hashCode(): Int {
            var hc = quasis.hashCode()
            hc = 31 * hc + expressions.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._quasis, quasis)
            updateTreeConnections(this._expressions, expressions)
            require(this.quasis.size == this.expressions.size + 1)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TemplateExpression).quasis },
                { n -> (n as TemplateExpression).expressions },
            )
        }
    }

    class ThisExpression(
        pos: Position,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate141
        override val formatElementCount
            get() = 0
        override fun deepCopy(): ThisExpression {
            return ThisExpression(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ThisExpression
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class YieldExpression(
        pos: Position,
        expr: Expression,
        var delegate: Boolean = false,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (delegate) {
                    sharedCodeFormattingTemplate142
                } else {
                    sharedCodeFormattingTemplate143
                }
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
        override fun deepCopy(): YieldExpression {
            return YieldExpression(pos, expr = this.expr.deepCopy(), delegate = this.delegate)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is YieldExpression && this.expr == other.expr && this.delegate == other.delegate
        }
        override fun hashCode(): Int {
            var hc = expr.hashCode()
            hc = 31 * hc + delegate.hashCode()
            return hc
        }
        init {
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as YieldExpression).expr },
            )
        }
    }

    /**
     * In addition to normal identifiers, there are private names like `#name`
     * that may be used in some prescribed contexts in class definitions.
     */
    class PrivateName(
        pos: Position,
        var name: JsIdentifierName,
        var sourceIdentifier: TemperName?,
    ) : BaseTree(pos), MemberKey {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(outName.toToken(inOperatorPosition = false))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        val outName: OutName
            get() = OutName("#${name.text}", sourceIdentifier)
        override fun deepCopy(): PrivateName {
            return PrivateName(pos, name = this.name, sourceIdentifier = this.sourceIdentifier)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PrivateName && this.name == other.name && this.sourceIdentifier == other.sourceIdentifier
        }
        override fun hashCode(): Int {
            var hc = name.hashCode()
            hc = 31 * hc + (sourceIdentifier?.hashCode() ?: 0)
            return hc
        }
        init {
            require(name.text !in jsReservedWords) { "$pos: `${name.text}` is a JS reserved word" }
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class Super(
        pos: Position,
    ) : BaseTree(pos), Callee {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate144
        override val formatElementCount
            get() = 0
        override fun deepCopy(): Super {
            return Super(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Super
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class Import(
        pos: Position,
    ) : BaseTree(pos), Callee {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate145
        override val formatElementCount
            get() = 0
        override fun deepCopy(): Import {
            return Import(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Import
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class ArrayHole(
        pos: Position,
    ) : BaseTree(pos), ArrayElement, ArrayPatternElement {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate10
        override val formatElementCount
            get() = 0
        override fun deepCopy(): ArrayHole {
            return ArrayHole(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ArrayHole
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class ObjectProperty(
        pos: Position,
        key: Expression,
        value: Expression,
        override var computed: Boolean = false,
        var optional: Boolean = false,
    ) : BaseTree(pos), ObjectMember {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (computed && optional) {
                    sharedCodeFormattingTemplate146
                } else if (computed) {
                    sharedCodeFormattingTemplate147
                } else if (optional) {
                    sharedCodeFormattingTemplate148
                } else {
                    sharedCodeFormattingTemplate118
                }
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
        private var _key: Expression
        override var key: Expression
            get() = _key
            set(newValue) { _key = updateTreeConnection(_key, newValue) }
        private var _value: Expression
        var value: Expression
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): ObjectProperty {
            return ObjectProperty(pos, key = this.key.deepCopy(), value = this.value.deepCopy(), computed = this.computed, optional = this.optional)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ObjectProperty && this.key == other.key && this.value == other.value && this.computed == other.computed && this.optional == other.optional
        }
        override fun hashCode(): Int {
            var hc = key.hashCode()
            hc = 31 * hc + value.hashCode()
            hc = 31 * hc + computed.hashCode()
            hc = 31 * hc + optional.hashCode()
            return hc
        }
        init {
            this._key = updateTreeConnection(null, key)
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ObjectProperty).key },
                { n -> (n as ObjectProperty).value },
            )
        }
    }

    class ObjectPropertyPattern(
        pos: Position,
        key: Expression,
        pattern: Pattern,
        var computed: Boolean = false,
    ) : BaseTree(pos), ObjectPatternMember {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (computed) {
                    sharedCodeFormattingTemplate147
                } else {
                    sharedCodeFormattingTemplate118
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.key
                1 -> this.pattern
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _key: Expression
        var key: Expression
            get() = _key
            set(newValue) { _key = updateTreeConnection(_key, newValue) }
        private var _pattern: Pattern
        var pattern: Pattern
            get() = _pattern
            set(newValue) { _pattern = updateTreeConnection(_pattern, newValue) }
        override fun deepCopy(): ObjectPropertyPattern {
            return ObjectPropertyPattern(pos, key = this.key.deepCopy(), pattern = this.pattern.deepCopy(), computed = this.computed)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ObjectPropertyPattern && this.key == other.key && this.pattern == other.pattern && this.computed == other.computed
        }
        override fun hashCode(): Int {
            var hc = key.hashCode()
            hc = 31 * hc + pattern.hashCode()
            hc = 31 * hc + computed.hashCode()
            return hc
        }
        init {
            this._key = updateTreeConnection(null, key)
            this._pattern = updateTreeConnection(null, pattern)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ObjectPropertyPattern).key },
                { n -> (n as ObjectPropertyPattern).pattern },
            )
        }
    }

    class NumericLiteral(
        pos: Position,
        var value: Number,
    ) : BaseTree(pos), Literal {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(OutputToken("$value", OutputTokenType.NumericValue))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): NumericLiteral {
            return NumericLiteral(pos, value = this.value)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is NumericLiteral && this.value == other.value
        }
        override fun hashCode(): Int {
            return value.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class Operator(
        pos: Position,
        var tokenText: String,
    ) : BaseTree(pos) {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(OutputToken(tokenText, operatorTokenType(tokenText)))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): Operator {
            return Operator(pos, tokenText = this.tokenText)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Operator && this.tokenText == other.tokenText
        }
        override fun hashCode(): Int {
            return tokenText.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class BinaryExpression(
        pos: Position,
        left: Expression,
        operator: Operator,
        right: Expression,
    ) : BaseTree(pos), InfixExpression {
        private var _left: Expression
        override var left: Expression
            get() = _left
            set(newValue) { _left = updateTreeConnection(_left, newValue) }
        private var _operator: Operator
        override var operator: Operator
            get() = _operator
            set(newValue) { _operator = updateTreeConnection(_operator, newValue) }
        private var _right: Expression
        override var right: Expression
            get() = _right
            set(newValue) { _right = updateTreeConnection(_right, newValue) }
        override val operatorTable: Map<String, JsOperatorDefinition>
            get() = InfixExpression.binaryOperator
        override fun deepCopy(): BinaryExpression {
            return BinaryExpression(pos, left = this.left.deepCopy(), operator = this.operator.deepCopy(), right = this.right.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BinaryExpression && this.left == other.left && this.operator == other.operator && this.right == other.right
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
        init { checkOperator() }
    }

    class LogicalExpression(
        pos: Position,
        left: Expression,
        operator: Operator,
        right: Expression,
    ) : BaseTree(pos), InfixExpression {
        private var _left: Expression
        override var left: Expression
            get() = _left
            set(newValue) { _left = updateTreeConnection(_left, newValue) }
        private var _operator: Operator
        override var operator: Operator
            get() = _operator
            set(newValue) { _operator = updateTreeConnection(_operator, newValue) }
        private var _right: Expression
        override var right: Expression
            get() = _right
            set(newValue) { _right = updateTreeConnection(_right, newValue) }
        override val operatorTable: Map<String, JsOperatorDefinition>
            get() = InfixExpression.logicalOperator
        override fun deepCopy(): LogicalExpression {
            return LogicalExpression(pos, left = this.left.deepCopy(), operator = this.operator.deepCopy(), right = this.right.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LogicalExpression && this.left == other.left && this.operator == other.operator && this.right == other.right
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
        init { checkOperator() }
    }

    class AssignmentExpression(
        pos: Position,
        left: Pattern,
        operator: Operator,
        right: Expression,
    ) : BaseTree(pos), InfixExpression {
        private var _left: Pattern
        override var left: Pattern
            get() = _left
            set(newValue) { _left = updateTreeConnection(_left, newValue) }
        private var _operator: Operator
        override var operator: Operator
            get() = _operator
            set(newValue) { _operator = updateTreeConnection(_operator, newValue) }
        private var _right: Expression
        override var right: Expression
            get() = _right
            set(newValue) { _right = updateTreeConnection(_right, newValue) }
        override val operatorTable: Map<String, JsOperatorDefinition>
            get() = InfixExpression.assignmentOperator
        override fun deepCopy(): AssignmentExpression {
            return AssignmentExpression(pos, left = this.left.deepCopy(), operator = this.operator.deepCopy(), right = this.right.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AssignmentExpression && this.left == other.left && this.operator == other.operator && this.right == other.right
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
        init { checkOperator() }
    }

    /** A chunk of textual content in a template string. */
    class TemplateElement(
        pos: Position,
        var raw: String,
    ) : BaseTree(pos) {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(OutputToken("`" + raw + "`", OutputTokenType.QuotedValue))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): TemplateElement {
            return TemplateElement(pos, raw = this.raw)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TemplateElement && this.raw == other.raw
        }
        override fun hashCode(): Int {
            return raw.hashCode()
        }
        init {
            require(JsTemplateHelpers.checkAllowedTemplateElementText(raw) == null)
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class ExpressionStatement(
        pos: Position,
        expression: Expression,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (needsParentheses) {
                    sharedCodeFormattingTemplate149
                } else {
                    sharedCodeFormattingTemplate150
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expression
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expression: Expression
        var expression: Expression
            get() = _expression
            set(newValue) { _expression = updateTreeConnection(_expression, newValue) }
        val needsParentheses: Boolean
            get() =
                run {
                    var needsParentheses = false

                    // https://www.ecma-international.org/ecma-262/10.0/index.html#sec-expression-statement
                    fun lookahead(n: Tree) {
                        when (n) {
                            // These two productions are sufficient to avoid the negative lookahead via a
                            // word token.  *async* is part and parcel of Js.Function.
                            is Function, is Class -> needsParentheses = true
                            is Identifier -> when (n.name.text) {
                                // These words are sufficient to avoid the negative lookahead via a name
                                // token.
                                "async", "class", "function", "let" -> needsParentheses = true
                            }
                            is ObjectExpression -> needsParentheses = true
                            else -> lookahead(n.childOrNull(0) ?: return)
                        }
                    }
                    lookahead(expression)
                    return needsParentheses
                }
        override fun deepCopy(): ExpressionStatement {
            return ExpressionStatement(pos, expression = this.expression.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ExpressionStatement && this.expression == other.expression
        }
        override fun hashCode(): Int {
            return expression.hashCode()
        }
        init {
            this._expression = updateTreeConnection(null, expression)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ExpressionStatement).expression },
            )
        }
        constructor(expression: Expression) : this(expression.pos, expression)
    }

    class IfStatement(
        pos: Position,
        test: Expression,
        consequent: Statement,
        alternate: Statement?,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (isElseIf) {
                    sharedCodeFormattingTemplate151
                } else if (hasElse) {
                    sharedCodeFormattingTemplate152
                } else {
                    sharedCodeFormattingTemplate153
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
        val isElseIf: Boolean
            get() = alternate is IfStatement
        val hasElse: Boolean
            get() =
                alternate != null &&
                    (alternate !is BlockStatement || alternate?.childCount != 0)
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

    class WhileStatement(
        pos: Position,
        test: Expression,
        body: Statement,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate154
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
        private var _body: Statement
        var body: Statement
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

    class ForOfStatement(
        pos: Position,
        left: LoopLeft,
        right: Expression,
        body: Statement,
        var awaits: Boolean,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (awaits) {
                    sharedCodeFormattingTemplate155
                } else {
                    sharedCodeFormattingTemplate156
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.left
                1 -> this.right
                2 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _left: LoopLeft
        var left: LoopLeft
            get() = _left
            set(newValue) { _left = updateTreeConnection(_left, newValue) }
        private var _right: Expression
        var right: Expression
            get() = _right
            set(newValue) { _right = updateTreeConnection(_right, newValue) }
        private var _body: Statement
        var body: Statement
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): ForOfStatement {
            return ForOfStatement(pos, left = this.left.deepCopy(), right = this.right.deepCopy(), body = this.body.deepCopy(), awaits = this.awaits)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ForOfStatement && this.left == other.left && this.right == other.right && this.body == other.body && this.awaits == other.awaits
        }
        override fun hashCode(): Int {
            var hc = left.hashCode()
            hc = 31 * hc + right.hashCode()
            hc = 31 * hc + body.hashCode()
            hc = 31 * hc + awaits.hashCode()
            return hc
        }
        init {
            this._left = updateTreeConnection(null, left)
            this._right = updateTreeConnection(null, right)
            this._body = updateTreeConnection(null, body)
            require((this.left as? VariableDeclaration)?.let { it.declarations.size == 1 } ?: true)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ForOfStatement).left },
                { n -> (n as ForOfStatement).right },
                { n -> (n as ForOfStatement).body },
            )
        }
    }

    class SwitchStatement(
        pos: Position,
        discriminant: Expression,
        cases: Iterable<SwitchCase>,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate157
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.discriminant
                1 -> FormattableTreeGroup(this.cases)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _discriminant: Expression
        var discriminant: Expression
            get() = _discriminant
            set(newValue) { _discriminant = updateTreeConnection(_discriminant, newValue) }
        private val _cases: MutableList<SwitchCase> = mutableListOf()
        var cases: List<SwitchCase>
            get() = _cases
            set(newValue) { updateTreeConnections(_cases, newValue) }
        override fun deepCopy(): SwitchStatement {
            return SwitchStatement(pos, discriminant = this.discriminant.deepCopy(), cases = this.cases.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SwitchStatement && this.discriminant == other.discriminant && this.cases == other.cases
        }
        override fun hashCode(): Int {
            var hc = discriminant.hashCode()
            hc = 31 * hc + cases.hashCode()
            return hc
        }
        init {
            this._discriminant = updateTreeConnection(null, discriminant)
            updateTreeConnections(this._cases, cases)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SwitchStatement).discriminant },
                { n -> (n as SwitchStatement).cases },
            )
        }
    }

    class TryStatement(
        pos: Position,
        block: BlockStatement,
        handler: CatchClause?,
        finalizer: BlockStatement?,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (handler != null && finalizer != null) {
                    sharedCodeFormattingTemplate158
                } else if (handler != null) {
                    sharedCodeFormattingTemplate159
                } else if (finalizer != null) {
                    sharedCodeFormattingTemplate160
                } else {
                    sharedCodeFormattingTemplate161
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.block
                1 -> this.handler ?: FormattableTreeGroup.empty
                2 -> this.finalizer ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _block: BlockStatement
        var block: BlockStatement
            get() = _block
            set(newValue) { _block = updateTreeConnection(_block, newValue) }
        private var _handler: CatchClause?
        var handler: CatchClause?
            get() = _handler
            set(newValue) { _handler = updateTreeConnection(_handler, newValue) }
        private var _finalizer: BlockStatement?
        var finalizer: BlockStatement?
            get() = _finalizer
            set(newValue) { _finalizer = updateTreeConnection(_finalizer, newValue) }
        override fun deepCopy(): TryStatement {
            return TryStatement(pos, block = this.block.deepCopy(), handler = this.handler?.deepCopy(), finalizer = this.finalizer?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TryStatement && this.block == other.block && this.handler == other.handler && this.finalizer == other.finalizer
        }
        override fun hashCode(): Int {
            var hc = block.hashCode()
            hc = 31 * hc + (handler?.hashCode() ?: 0)
            hc = 31 * hc + (finalizer?.hashCode() ?: 0)
            return hc
        }
        init {
            this._block = updateTreeConnection(null, block)
            this._handler = updateTreeConnection(null, handler)
            this._finalizer = updateTreeConnection(null, finalizer)
            require(this.handler != null || this.finalizer != null)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TryStatement).block },
                { n -> (n as TryStatement).handler },
                { n -> (n as TryStatement).finalizer },
            )
        }
    }

    class ThrowStatement(
        pos: Position,
        argument: Expression,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate162
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.argument
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _argument: Expression
        var argument: Expression
            get() = _argument
            set(newValue) { _argument = updateTreeConnection(_argument, newValue) }
        override fun deepCopy(): ThrowStatement {
            return ThrowStatement(pos, argument = this.argument.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ThrowStatement && this.argument == other.argument
        }
        override fun hashCode(): Int {
            return argument.hashCode()
        }
        init {
            this._argument = updateTreeConnection(null, argument)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ThrowStatement).argument },
            )
        }
    }

    class ReturnStatement(
        pos: Position,
        expr: Expression?,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (expr != null) {
                    sharedCodeFormattingTemplate163
                } else {
                    sharedCodeFormattingTemplate164
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

    class LabeledStatement(
        pos: Position,
        label: Identifier,
        body: Statement,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate118
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.label
                1 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _label: Identifier
        var label: Identifier
            get() = _label
            set(newValue) { _label = updateTreeConnection(_label, newValue) }
        private var _body: Statement
        var body: Statement
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): LabeledStatement {
            return LabeledStatement(pos, label = this.label.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LabeledStatement && this.label == other.label && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = label.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._label = updateTreeConnection(null, label)
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LabeledStatement).label },
                { n -> (n as LabeledStatement).body },
            )
        }
    }

    class BreakStatement(
        pos: Position,
        label: Identifier?,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (label != null) {
                    sharedCodeFormattingTemplate165
                } else {
                    sharedCodeFormattingTemplate166
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.label ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _label: Identifier?
        var label: Identifier?
            get() = _label
            set(newValue) { _label = updateTreeConnection(_label, newValue) }
        override fun deepCopy(): BreakStatement {
            return BreakStatement(pos, label = this.label?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BreakStatement && this.label == other.label
        }
        override fun hashCode(): Int {
            return (label?.hashCode() ?: 0)
        }
        init {
            this._label = updateTreeConnection(null, label)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as BreakStatement).label },
            )
        }
    }

    class ContinueStatement(
        pos: Position,
        label: Identifier?,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (label != null) {
                    sharedCodeFormattingTemplate167
                } else {
                    sharedCodeFormattingTemplate168
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.label ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _label: Identifier?
        var label: Identifier?
            get() = _label
            set(newValue) { _label = updateTreeConnection(_label, newValue) }
        override fun deepCopy(): ContinueStatement {
            return ContinueStatement(pos, label = this.label?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ContinueStatement && this.label == other.label
        }
        override fun hashCode(): Int {
            return (label?.hashCode() ?: 0)
        }
        init {
            this._label = updateTreeConnection(null, label)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ContinueStatement).label },
            )
        }
    }

    /**
     * CommentLine is not defined by Babel.
     * A JS line comment that allows embedding metadata markers in generated JS.
     */
    class CommentLine(
        pos: Position,
        var commentText: String,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            // Start each comment token on its own line.  Not following a semi or brace.
            tokenSink.endLine()
            val tokenText = if (commentText.isEmpty()) {
                "//"
            } else {
                "// $commentText"
            }
            tokenSink.emit(OutputToken(tokenText, OutputTokenType.Comment))
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
            require(commentText.none { it.isJsLineTerminatorChar }) { commentText }
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class SwitchCase(
        pos: Position,
        test: Expression?,
        consequent: Iterable<Statement>,
    ) : BaseTree(pos) {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (test != null) {
                    sharedCodeFormattingTemplate169
                } else {
                    sharedCodeFormattingTemplate170
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.test ?: FormattableTreeGroup.empty
                1 -> FormattableTreeGroup(this.consequent)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _test: Expression?
        var test: Expression?
            get() = _test
            set(newValue) { _test = updateTreeConnection(_test, newValue) }
        private val _consequent: MutableList<Statement> = mutableListOf()
        var consequent: List<Statement>
            get() = _consequent
            set(newValue) { updateTreeConnections(_consequent, newValue) }
        override fun deepCopy(): SwitchCase {
            return SwitchCase(pos, test = this.test?.deepCopy(), consequent = this.consequent.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SwitchCase && this.test == other.test && this.consequent == other.consequent
        }
        override fun hashCode(): Int {
            var hc = (test?.hashCode() ?: 0)
            hc = 31 * hc + consequent.hashCode()
            return hc
        }
        init {
            this._test = updateTreeConnection(null, test)
            updateTreeConnections(this._consequent, consequent)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SwitchCase).test },
                { n -> (n as SwitchCase).consequent },
            )
        }
    }

    class CatchClause(
        pos: Position,
        exceptionDeclaration: ExceptionDeclaration?,
        body: BlockStatement,
    ) : BaseTree(pos) {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (exceptionDeclaration != null) {
                    sharedCodeFormattingTemplate171
                } else {
                    sharedCodeFormattingTemplate172
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.exceptionDeclaration ?: FormattableTreeGroup.empty
                1 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _exceptionDeclaration: ExceptionDeclaration?
        var exceptionDeclaration: ExceptionDeclaration?
            get() = _exceptionDeclaration
            set(newValue) { _exceptionDeclaration = updateTreeConnection(_exceptionDeclaration, newValue) }
        private var _body: BlockStatement
        var body: BlockStatement
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): CatchClause {
            return CatchClause(pos, exceptionDeclaration = this.exceptionDeclaration?.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is CatchClause && this.exceptionDeclaration == other.exceptionDeclaration && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = (exceptionDeclaration?.hashCode() ?: 0)
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._exceptionDeclaration = updateTreeConnection(null, exceptionDeclaration)
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as CatchClause).exceptionDeclaration },
                { n -> (n as CatchClause).body },
            )
        }
    }

    sealed interface JsDocTypeInfo : Tree {
        override fun deepCopy(): JsDocTypeInfo
    }

    class JsDocClassType(
        pos: Position,
        templates: Iterable<JsDocTagTemplate>,
    ) : BaseTree(pos), JsDocTypeInfo {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate0
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.templates)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _templates: MutableList<JsDocTagTemplate> = mutableListOf()
        var templates: List<JsDocTagTemplate>
            get() = _templates
            set(newValue) { updateTreeConnections(_templates, newValue) }
        override fun deepCopy(): JsDocClassType {
            return JsDocClassType(pos, templates = this.templates.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is JsDocClassType && this.templates == other.templates
        }
        override fun hashCode(): Int {
            return templates.hashCode()
        }
        init {
            updateTreeConnections(this._templates, templates)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as JsDocClassType).templates },
            )
        }
    }

    class JsDocFunctionType(
        pos: Position,
        templates: Iterable<JsDocTagTemplate>,
        params: Iterable<JsDocTagParam>,
        returnType: JsDocTagReturn?,
    ) : BaseTree(pos), JsDocTypeInfo {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (returnType != null) {
                    sharedCodeFormattingTemplate173
                } else {
                    sharedCodeFormattingTemplate174
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.templates)
                1 -> FormattableTreeGroup(this.params)
                2 -> this.returnType ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _templates: MutableList<JsDocTagTemplate> = mutableListOf()
        var templates: List<JsDocTagTemplate>
            get() = _templates
            set(newValue) { updateTreeConnections(_templates, newValue) }
        private val _params: MutableList<JsDocTagParam> = mutableListOf()
        var params: List<JsDocTagParam>
            get() = _params
            set(newValue) { updateTreeConnections(_params, newValue) }
        private var _returnType: JsDocTagReturn?
        var returnType: JsDocTagReturn?
            get() = _returnType
            set(newValue) { _returnType = updateTreeConnection(_returnType, newValue) }
        override fun deepCopy(): JsDocFunctionType {
            return JsDocFunctionType(pos, templates = this.templates.deepCopy(), params = this.params.deepCopy(), returnType = this.returnType?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is JsDocFunctionType && this.templates == other.templates && this.params == other.params && this.returnType == other.returnType
        }
        override fun hashCode(): Int {
            var hc = templates.hashCode()
            hc = 31 * hc + params.hashCode()
            hc = 31 * hc + (returnType?.hashCode() ?: 0)
            return hc
        }
        init {
            updateTreeConnections(this._templates, templates)
            updateTreeConnections(this._params, params)
            this._returnType = updateTreeConnection(null, returnType)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as JsDocFunctionType).templates },
                { n -> (n as JsDocFunctionType).params },
                { n -> (n as JsDocFunctionType).returnType },
            )
        }
    }

    class JsDocTagType(
        pos: Position,
        type: JsDocTypeWrap,
    ) : BaseTree(pos), JsDocTypeInfo {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate175
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
        private var _type: JsDocTypeWrap
        var type: JsDocTypeWrap
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        override fun deepCopy(): JsDocTagType {
            return JsDocTagType(pos, type = this.type.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is JsDocTagType && this.type == other.type
        }
        override fun hashCode(): Int {
            return type.hashCode()
        }
        init {
            this._type = updateTreeConnection(null, type)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as JsDocTagType).type },
            )
        }
    }

    class JsDocTypedef(
        pos: Position,
        templates: Iterable<JsDocTagTemplate>,
        typedef: JsDocTagTypedef,
    ) : BaseTree(pos), JsDocTypeInfo {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate176
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.templates)
                1 -> this.typedef
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _templates: MutableList<JsDocTagTemplate> = mutableListOf()
        var templates: List<JsDocTagTemplate>
            get() = _templates
            set(newValue) { updateTreeConnections(_templates, newValue) }
        private var _typedef: JsDocTagTypedef
        var typedef: JsDocTagTypedef
            get() = _typedef
            set(newValue) { _typedef = updateTreeConnection(_typedef, newValue) }
        override fun deepCopy(): JsDocTypedef {
            return JsDocTypedef(pos, templates = this.templates.deepCopy(), typedef = this.typedef.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is JsDocTypedef && this.templates == other.templates && this.typedef == other.typedef
        }
        override fun hashCode(): Int {
            var hc = templates.hashCode()
            hc = 31 * hc + typedef.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._templates, templates)
            this._typedef = updateTreeConnection(null, typedef)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as JsDocTypedef).templates },
                { n -> (n as JsDocTypedef).typedef },
            )
        }
    }

    class JsDocTagTemplate(
        pos: Position,
        type: JsDocTypeWrap?,
        id: Identifier,
    ) : BaseTree(pos) {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (type != null) {
                    sharedCodeFormattingTemplate177
                } else {
                    sharedCodeFormattingTemplate178
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type ?: FormattableTreeGroup.empty
                1 -> this.id
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: JsDocTypeWrap?
        var type: JsDocTypeWrap?
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _id: Identifier
        var id: Identifier
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        override fun deepCopy(): JsDocTagTemplate {
            return JsDocTagTemplate(pos, type = this.type?.deepCopy(), id = this.id.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is JsDocTagTemplate && this.type == other.type && this.id == other.id
        }
        override fun hashCode(): Int {
            var hc = (type?.hashCode() ?: 0)
            hc = 31 * hc + id.hashCode()
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            this._id = updateTreeConnection(null, id)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as JsDocTagTemplate).type },
                { n -> (n as JsDocTagTemplate).id },
            )
        }
    }

    class JsDocTagParam(
        pos: Position,
        type: JsDocTypeWrap,
        id: Identifier,
        var optional: Boolean = false,
    ) : BaseTree(pos) {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (optional) {
                    sharedCodeFormattingTemplate179
                } else {
                    sharedCodeFormattingTemplate180
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type
                1 -> this.id
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: JsDocTypeWrap
        var type: JsDocTypeWrap
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _id: Identifier
        var id: Identifier
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        override fun deepCopy(): JsDocTagParam {
            return JsDocTagParam(pos, type = this.type.deepCopy(), id = this.id.deepCopy(), optional = this.optional)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is JsDocTagParam && this.type == other.type && this.id == other.id && this.optional == other.optional
        }
        override fun hashCode(): Int {
            var hc = type.hashCode()
            hc = 31 * hc + id.hashCode()
            hc = 31 * hc + optional.hashCode()
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            this._id = updateTreeConnection(null, id)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as JsDocTagParam).type },
                { n -> (n as JsDocTagParam).id },
            )
        }
    }

    class JsDocTagReturn(
        pos: Position,
        type: JsDocTypeWrap,
    ) : BaseTree(pos) {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate181
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
        private var _type: JsDocTypeWrap
        var type: JsDocTypeWrap
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        override fun deepCopy(): JsDocTagReturn {
            return JsDocTagReturn(pos, type = this.type.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is JsDocTagReturn && this.type == other.type
        }
        override fun hashCode(): Int {
            return type.hashCode()
        }
        init {
            this._type = updateTreeConnection(null, type)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as JsDocTagReturn).type },
            )
        }
    }

    class JsDocTagTypedef(
        pos: Position,
        type: JsDocTypeWrap,
        id: Identifier,
    ) : BaseTree(pos) {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate182
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type
                1 -> this.id
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: JsDocTypeWrap
        var type: JsDocTypeWrap
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _id: Identifier
        var id: Identifier
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        override fun deepCopy(): JsDocTagTypedef {
            return JsDocTagTypedef(pos, type = this.type.deepCopy(), id = this.id.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is JsDocTagTypedef && this.type == other.type && this.id == other.id
        }
        override fun hashCode(): Int {
            var hc = type.hashCode()
            hc = 31 * hc + id.hashCode()
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            this._id = updateTreeConnection(null, id)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as JsDocTagTypedef).type },
                { n -> (n as JsDocTagTypedef).id },
            )
        }
    }

    class JsDocTypeWrap(
        pos: Position,
        type: Type,
    ) : BaseTree(pos) {
        override val operatorDefinition: JsOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate183
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
        override fun deepCopy(): JsDocTypeWrap {
            return JsDocTypeWrap(pos, type = this.type.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is JsDocTypeWrap && this.type == other.type
        }
        override fun hashCode(): Int {
            return type.hashCode()
        }
        init {
            this._type = updateTreeConnection(null, type)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as JsDocTypeWrap).type },
            )
        }
    }

    /** `{{0*\n}}` */
    private val sharedCodeFormattingTemplate0 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.NewLine,
        )

    /** `import {{0*,}} from {{1}} ;` */
    private val sharedCodeFormattingTemplate1 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("from", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `import {{1}} ;` */
    private val sharedCodeFormattingTemplate2 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} export {{1}} from {{3}} ;` */
    private val sharedCodeFormattingTemplate3 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("export", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("from", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} export {{1}} ;` */
    private val sharedCodeFormattingTemplate4 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("export", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} export \{ {{2*,}} \} from {{3}} ;` */
    private val sharedCodeFormattingTemplate5 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("export", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("from", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} export \{ {{2*,}} \} ;` */
    private val sharedCodeFormattingTemplate6 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("export", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `export default {{0}} ;` */
    private val sharedCodeFormattingTemplate7 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("export", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("default", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `export * from {{0}} ;` */
    private val sharedCodeFormattingTemplate8 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("export", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("from", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}}` */
    private val sharedCodeFormattingTemplate9 =
        CodeFormattingTemplate.OneSubstitution(0)

    /** `` */
    private val sharedCodeFormattingTemplate10 =
        CodeFormattingTemplate.empty

    /** `{{1}}` */
    private val sharedCodeFormattingTemplate11 =
        CodeFormattingTemplate.OneSubstitution(1)

    /** `{{0}} as {{1}}` */
    private val sharedCodeFormattingTemplate12 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("as", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `\{ {{0*,}} \}` */
    private val sharedCodeFormattingTemplate13 =
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

    /** `* as {{0}}` */
    private val sharedCodeFormattingTemplate14 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("as", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
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

    /** ``JsDocTokens.commentStart` {{0}} `JsDocTokens.commentEnd` \n` */
    private val sharedCodeFormattingTemplate16 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken(JsDocTokens.commentStart),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(JsDocTokens.commentEnd),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `async function * {{0}} {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate17 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `async function * {{0}} {{1}}` */
    private val sharedCodeFormattingTemplate18 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `async function * {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate19 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `async function * {{1}}` */
    private val sharedCodeFormattingTemplate20 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `async function {{0}} {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate21 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `async function {{0}} {{1}}` */
    private val sharedCodeFormattingTemplate22 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `async function {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate23 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `async function {{1}}` */
    private val sharedCodeFormattingTemplate24 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `function * {{0}} {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate25 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `function * {{0}} {{1}}` */
    private val sharedCodeFormattingTemplate26 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `function * {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate27 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `function * {{1}}` */
    private val sharedCodeFormattingTemplate28 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `function {{0}} {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate29 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `function {{0}} {{1}}` */
    private val sharedCodeFormattingTemplate30 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `function {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate31 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `function {{1}}` */
    private val sharedCodeFormattingTemplate32 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("function", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} class {{1}} extends {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate33 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} class {{1}} {{3}}` */
    private val sharedCodeFormattingTemplate34 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} class extends {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate35 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} class {{3}}` */
    private val sharedCodeFormattingTemplate36 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `const {{0*,}} ;` */
    private val sharedCodeFormattingTemplate37 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("const", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `const {{0*,}}` */
    private val sharedCodeFormattingTemplate38 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("const", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
            ),
        )

    /** `let {{0*,}} ;` */
    private val sharedCodeFormattingTemplate39 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `let {{0*,}}` */
    private val sharedCodeFormattingTemplate40 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
            ),
        )

    /** `var {{0*,}} ;` */
    private val sharedCodeFormattingTemplate41 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("var", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `var {{0*,}}` */
    private val sharedCodeFormattingTemplate42 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("var", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
            ),
        )

    /** `[ {{0*,}} ]` */
    private val sharedCodeFormattingTemplate43 =
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

    /** `{{0}} = {{1}}` */
    private val sharedCodeFormattingTemplate44 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `\{\}` */
    private val sharedCodeFormattingTemplate45 =
        CodeFormattingTemplate.LiteralToken("{}", OutputTokenType.Punctuation)

    /** `... {{0}}` */
    private val sharedCodeFormattingTemplate46 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("...", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `{{0}} ?. [ {{1}} ]` */
    private val sharedCodeFormattingTemplate47 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("?.", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}} [ {{1}} ]` */
    private val sharedCodeFormattingTemplate48 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}} ?. {{1}}` */
    private val sharedCodeFormattingTemplate49 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("?.", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} . {{1}}` */
    private val sharedCodeFormattingTemplate50 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `[ {{0}} ] :` */
    private val sharedCodeFormattingTemplate51 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} :` */
    private val sharedCodeFormattingTemplate52 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} async static * get [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate53 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async static * get {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate54 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async static * set [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate55 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async static * set {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate56 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async static * [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate57 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async static * {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate58 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async static get [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate59 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async static get {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate60 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async static set [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate61 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async static set {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate62 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async static [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate63 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async static {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate64 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async * get [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate65 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async * get {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate66 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async * set [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate67 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async * set {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate68 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async * [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate69 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async * {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate70 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async get [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate71 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async get {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate72 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async set [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate73 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async set {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate74 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate75 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} async {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate76 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("async", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} static * get [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate77 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} static * get {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate78 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} static * set [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate79 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} static * set {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate80 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} static * [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate81 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} static * {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate82 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} static get [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate83 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} static get {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate84 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} static set [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate85 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} static set {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate86 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} static [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate87 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} static {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate88 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} * get [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate89 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} * get {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate90 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} * set [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate91 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} * set {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate92 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} * [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate93 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} * {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate94 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} get [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate95 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} get {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate96 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} set [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate97 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} set {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate98 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} [ {{1}} ] {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate99 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate100 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `get [ {{0}} ] {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate101 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `get [ {{0}} ] {{1}}` */
    private val sharedCodeFormattingTemplate102 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `get {{0}} {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate103 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `get {{0}} {{1}}` */
    private val sharedCodeFormattingTemplate104 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `set [ {{0}} ] {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate105 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `set [ {{0}} ] {{1}}` */
    private val sharedCodeFormattingTemplate106 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `set {{0}} {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate107 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `set {{0}} {{1}}` */
    private val sharedCodeFormattingTemplate108 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `[ {{0}} ] {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate109 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `[ {{0}} ] {{1}}` */
    private val sharedCodeFormattingTemplate110 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate111 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0}} => ( {{1}} )` */
    private val sharedCodeFormattingTemplate112 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}} => {{1}}` */
    private val sharedCodeFormattingTemplate113 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} => {{2}}` */
    private val sharedCodeFormattingTemplate114 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `( {{0*,}} ) : {{1}}` */
    private val sharedCodeFormattingTemplate115 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `( {{0*,}} )` */
    private val sharedCodeFormattingTemplate116 =
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

    /** `\{ \n {{0*\n}} \n \}` */
    private val sharedCodeFormattingTemplate117 =
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

    /** `{{0}} : {{1}}` */
    private val sharedCodeFormattingTemplate118 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0*}}` */
    private val sharedCodeFormattingTemplate119 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.empty,
        )

    /** `@ {{0}}` */
    private val sharedCodeFormattingTemplate120 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("@", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `{{0}} static [ {{1}} ] = {{2}} ;` */
    private val sharedCodeFormattingTemplate121 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} static [ {{1}} ] ;` */
    private val sharedCodeFormattingTemplate122 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} static {{1}} = {{2}} ;` */
    private val sharedCodeFormattingTemplate123 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} static {{1}} ;` */
    private val sharedCodeFormattingTemplate124 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} [ {{1}} ] = {{2}} ;` */
    private val sharedCodeFormattingTemplate125 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} [ {{1}} ] ;` */
    private val sharedCodeFormattingTemplate126 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} = {{2}} ;` */
    private val sharedCodeFormattingTemplate127 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} ;` */
    private val sharedCodeFormattingTemplate128 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} < {{1*,}} >` */
    private val sharedCodeFormattingTemplate129 =
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

    /** `{{0}} ?. ( {{1*,}} )` */
    private val sharedCodeFormattingTemplate130 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("?.", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}} ( {{1*,}} )` */
    private val sharedCodeFormattingTemplate131 =
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

    /** `new {{0}} ?. ( {{1*,}} )` */
    private val sharedCodeFormattingTemplate132 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("new", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("?.", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `new {{0}} ( {{1*,}} )` */
    private val sharedCodeFormattingTemplate133 =
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

    /** `{{1}} {{0}}` */
    private val sharedCodeFormattingTemplate134 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `{{0}} ? {{1}} : {{2}}` */
    private val sharedCodeFormattingTemplate135 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("?", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `true` */
    private val sharedCodeFormattingTemplate136 =
        CodeFormattingTemplate.LiteralToken("true", OutputTokenType.Word)

    /** `false` */
    private val sharedCodeFormattingTemplate137 =
        CodeFormattingTemplate.LiteralToken("false", OutputTokenType.Word)

    /** `null` */
    private val sharedCodeFormattingTemplate138 =
        CodeFormattingTemplate.LiteralToken("null", OutputTokenType.Word)

    /** `void` */
    private val sharedCodeFormattingTemplate139 =
        CodeFormattingTemplate.LiteralToken("void", OutputTokenType.Word)

    /** `{{0*,}}` */
    private val sharedCodeFormattingTemplate140 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
        )

    /** `this` */
    private val sharedCodeFormattingTemplate141 =
        CodeFormattingTemplate.LiteralToken("this", OutputTokenType.Word)

    /** `yield * {{0}}` */
    private val sharedCodeFormattingTemplate142 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("yield", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `yield {{0}}` */
    private val sharedCodeFormattingTemplate143 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("yield", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `super` */
    private val sharedCodeFormattingTemplate144 =
        CodeFormattingTemplate.LiteralToken("super", OutputTokenType.Word)

    /** `import` */
    private val sharedCodeFormattingTemplate145 =
        CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word)

    /** `[ {{0}} ] ? : {{1}}` */
    private val sharedCodeFormattingTemplate146 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("?", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `[ {{0}} ] : {{1}}` */
    private val sharedCodeFormattingTemplate147 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} ? : {{1}}` */
    private val sharedCodeFormattingTemplate148 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("?", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `( {{0}} ) ;` */
    private val sharedCodeFormattingTemplate149 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} ;` */
    private val sharedCodeFormattingTemplate150 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `if ( {{0}} ) \{ \n {{1}} \n \} else {{2}}` */
    private val sharedCodeFormattingTemplate151 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `if ( {{0}} ) \{ \n {{1}} \n \} else \{ \n {{2}} \n \}` */
    private val sharedCodeFormattingTemplate152 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `if ( {{0}} ) \{ \n {{1}} \n \}` */
    private val sharedCodeFormattingTemplate153 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `while ( {{0}} ) \{ \n {{1}} \n \}` */
    private val sharedCodeFormattingTemplate154 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("while", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `for await ( {{0}} of {{1}} ) \{ \n {{2}} \n \}` */
    private val sharedCodeFormattingTemplate155 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("await", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("of", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `for ( {{0}} of {{1}} ) \{ \n {{2}} \n \}` */
    private val sharedCodeFormattingTemplate156 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("for", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("of", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `switch ( {{0}} ) \{ \n {{1*\n}} \n \}` */
    private val sharedCodeFormattingTemplate157 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("switch", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `try \{ \n {{0}} \n \} {{1}} finally {{2}}` */
    private val sharedCodeFormattingTemplate158 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `try \{ \n {{0}} \n \} {{1}}` */
    private val sharedCodeFormattingTemplate159 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `try \{ \n {{0}} \n \} finally {{2}}` */
    private val sharedCodeFormattingTemplate160 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `try \{ \n {{0}} \n \}` */
    private val sharedCodeFormattingTemplate161 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `throw {{0}} ;` */
    private val sharedCodeFormattingTemplate162 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("throw", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `return {{0}} ;` */
    private val sharedCodeFormattingTemplate163 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("return", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `return ;` */
    private val sharedCodeFormattingTemplate164 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("return", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `break {{0}} ;` */
    private val sharedCodeFormattingTemplate165 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("break", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `break ;` */
    private val sharedCodeFormattingTemplate166 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("break", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `continue {{0}} ;` */
    private val sharedCodeFormattingTemplate167 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("continue", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `continue ;` */
    private val sharedCodeFormattingTemplate168 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("continue", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `case {{0}} : \n {{1*\n}}` */
    private val sharedCodeFormattingTemplate169 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("case", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.NewLine,
                ),
            ),
        )

    /** `default : \n {{1*\n}}` */
    private val sharedCodeFormattingTemplate170 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("default", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.NewLine,
                ),
            ),
        )

    /** `catch ( {{0}} ) {{1}}` */
    private val sharedCodeFormattingTemplate171 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("catch", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `catch {{1}}` */
    private val sharedCodeFormattingTemplate172 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("catch", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0*\n}} \n {{1*\n}} \n {{2}}` */
    private val sharedCodeFormattingTemplate173 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0*\n}} \n {{1*\n}}` */
    private val sharedCodeFormattingTemplate174 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.NewLine,
                ),
            ),
        )

    /** `@ type \  {{0}}` */
    private val sharedCodeFormattingTemplate175 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("@", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("type", OutputTokenType.Word),
                CodeFormattingTemplate.Space,
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `{{0*\n}} \n {{1}}` */
    private val sharedCodeFormattingTemplate176 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `@ template \  {{0}} \  {{1}}` */
    private val sharedCodeFormattingTemplate177 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("@", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("template", OutputTokenType.Word),
                CodeFormattingTemplate.Space,
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.Space,
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `@ template \  \  {{1}}` */
    private val sharedCodeFormattingTemplate178 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("@", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("template", OutputTokenType.Word),
                CodeFormattingTemplate.Space,
                CodeFormattingTemplate.Space,
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `@ param \  {{0}} \  [ {{1}} ]` */
    private val sharedCodeFormattingTemplate179 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("@", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("param", OutputTokenType.Word),
                CodeFormattingTemplate.Space,
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.Space,
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `@ param \  {{0}} \  {{1}}` */
    private val sharedCodeFormattingTemplate180 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("@", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("param", OutputTokenType.Word),
                CodeFormattingTemplate.Space,
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.Space,
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `@ returns \  {{0}}` */
    private val sharedCodeFormattingTemplate181 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("@", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("returns", OutputTokenType.Word),
                CodeFormattingTemplate.Space,
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `@ typedef \  {{0}} {{1}}` */
    private val sharedCodeFormattingTemplate182 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("@", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("typedef", OutputTokenType.Word),
                CodeFormattingTemplate.Space,
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** ``JsDocTokens.openCurly` {{0}} `JsDocTokens.closeCurly`` */
    private val sharedCodeFormattingTemplate183 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken(JsDocTokens.openCurly),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(JsDocTokens.closeCurly),
            ),
        )
}
