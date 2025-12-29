@file:lang.temper.common.Generated("OutputGrammarCodeGenerator")
@file:Suppress("ktlint", "unused", "CascadeIf", "MagicNumber", "MemberNameEqualsClassName", "MemberVisibilityCanBePrivate")

package lang.temper.be.java
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
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenAssociation
import lang.temper.format.TokenSink
import lang.temper.log.Position
import lang.temper.name.OutName
import lang.temper.name.name

object Java {
    sealed interface Tree : OutTree<Tree> {
        override fun formattingHints(): FormattingHints = JavaFormattingHints.getInstance()
        override val operatorDefinition: JavaOperatorDefinition?
        override fun deepCopy(): Tree
    }
    sealed class BaseTree(
        pos: Position,
    ) : BaseOutTree<Tree>(pos), Tree
    sealed interface Data : OutData<Data> {
        override fun formattingHints(): FormattingHints = JavaFormattingHints.getInstance()
        override val operatorDefinition: JavaOperatorDefinition?
    }
    sealed class BaseData : BaseOutData<Data>(), Data

    enum class SourceDirectory : FormattableEnum {
        MainJava,
        TestJava,
    }

    enum class EntryPoint : FormattableEnum {
        MainMethod,
        None,
    }

    /** JLS 7.7 An open module does not require explicit [OpensDirective]s to open packages for use. */
    enum class ModOpen : FormattableEnum {
        Closed,
        Open,
    }

    /**
     * Modifier keyword used by the module system to mark dependency that will be passed transitively to modules
     * that depend on it. JLS 7.7.1 and example 7.1.1-1
     */
    enum class ModTransitive : FormattableEnum {
        Terminal,
        Transitive,
    }

    /** Heavily overloaded modifier keyword used to mark elements that are global or otherwise not dynamically allocated. */
    enum class ModStatic : FormattableEnum {
        Dynamic,
        Static,
    }

    /** The specific flavor of an interface method. */
    enum class ModInterfaceMethod : FormattableEnum {
        Abstract,
        Default,
        Private,
        Static,
        PrivateStatic,
    }

    /** Modifier keyword to mark classes or methods as relying on a subclass to provide an implementation. */
    enum class ModAbstract : FormattableEnum {
        Concrete,
        Abstract,
    }

    /** The degree of access a class or member grants to a calling method. */
    enum class ModAccess : FormattableEnum {
        Private,
        PackagePrivate,
        Protected,
        Public,
    }

    /** Modifier keyword to mark whether a member of a class is open to being overridden. See also [ModSealedFinal]. */
    enum class ModFinal : FormattableEnum {
        Open,
        Final,
    }

    enum class ModNative : FormattableEnum {
        Java,
        Native,
    }

    /** Modifier keyword for classes that may identify specific classes that are permitted to override. */
    enum class ModSealedFinal : FormattableEnum {
        Open,
        NonSealed,
        Sealed,
        Final,
    }

    /** Modifier keyword for interfaces that may identify specific classes that are permitted to override. */
    enum class ModSealed : FormattableEnum {
        Open,
        NonSealed,
        Sealed,
    }

    /** Modifier keyword to, effectively, wrap the method in a synchronized block. */
    enum class ModSynchronized : FormattableEnum {
        Unsynchronized,
        Synchronized,
    }

    /** Modifier keyword to indicate a field does not need to be persisted by serialization. */
    enum class ModTransient : FormattableEnum {
        Persistent,
        Transient,
    }

    /** Modifier keyword to mark that a field may be changed by other threads. */
    enum class ModVolatile : FormattableEnum {
        Stable,
        Volatile,
    }

    /** Indicates whether to mark a literal as single or double precision. */
    enum class Precision : FormattableEnum {
        Single,
        Double,
    }

    /**
     * Represents one of three .java file types.
     *
     * This grammar differs from JLS which treats class and interface separately.
     * See JLS 7.6
     */
    sealed interface Program : Tree {
        val packageStatement: PackageStatement?
        val programMeta: ProgramMeta
        override fun deepCopy(): Program
    }

    /**
     * JLS 7.4 represents a package.java file
     */
    class PackageDeclaration(
        pos: Position,
        programMeta: ProgramMeta,
        packageStatement: PackageStatement,
    ) : BaseTree(pos), Program {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate0
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.programMeta
                1 -> this.packageStatement
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _programMeta: ProgramMeta
        override var programMeta: ProgramMeta
            get() = _programMeta
            set(newValue) { _programMeta = updateTreeConnection(_programMeta, newValue) }
        private var _packageStatement: PackageStatement
        override var packageStatement: PackageStatement
            get() = _packageStatement
            set(newValue) { _packageStatement = updateTreeConnection(_packageStatement, newValue) }
        override fun deepCopy(): PackageDeclaration {
            return PackageDeclaration(pos, programMeta = this.programMeta.deepCopy(), packageStatement = this.packageStatement.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PackageDeclaration && this.programMeta == other.programMeta && this.packageStatement == other.packageStatement
        }
        override fun hashCode(): Int {
            var hc = programMeta.hashCode()
            hc = 31 * hc + packageStatement.hashCode()
            return hc
        }
        init {
            this._programMeta = updateTreeConnection(null, programMeta)
            this._packageStatement = updateTreeConnection(null, packageStatement)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as PackageDeclaration).programMeta },
                { n -> (n as PackageDeclaration).packageStatement },
            )
        }
    }

    /**
     * JLS 7.7 represents module-info.java.
     * Since Java 9, per JEP 200: https://openjdk.org/jeps/200
     */
    class ModuleDeclaration(
        pos: Position,
        moduleName: QualIdentifier,
        directives: Iterable<ModuleDirective>,
        programMeta: ProgramMeta,
        var modOpen: ModOpen = ModOpen.Closed,
    ) : BaseTree(pos), Program {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (modOpen == ModOpen.Open) {
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
                0 -> this.moduleName
                1 -> FormattableTreeGroup(this.directives)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _moduleName: QualIdentifier
        var moduleName: QualIdentifier
            get() = _moduleName
            set(newValue) { _moduleName = updateTreeConnection(_moduleName, newValue) }
        private val _directives: MutableList<ModuleDirective> = mutableListOf()
        var directives: List<ModuleDirective>
            get() = _directives
            set(newValue) { updateTreeConnections(_directives, newValue) }
        private var _programMeta: ProgramMeta
        override var programMeta: ProgramMeta
            get() = _programMeta
            set(newValue) { _programMeta = updateTreeConnection(_programMeta, newValue) }
        override val packageStatement: PackageStatement?
            get() = null
        override fun deepCopy(): ModuleDeclaration {
            return ModuleDeclaration(pos, moduleName = this.moduleName.deepCopy(), directives = this.directives.deepCopy(), programMeta = this.programMeta.deepCopy(), modOpen = this.modOpen)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ModuleDeclaration && this.moduleName == other.moduleName && this.directives == other.directives && this.programMeta == other.programMeta && this.modOpen == other.modOpen
        }
        override fun hashCode(): Int {
            var hc = moduleName.hashCode()
            hc = 31 * hc + directives.hashCode()
            hc = 31 * hc + programMeta.hashCode()
            hc = 31 * hc + modOpen.hashCode()
            return hc
        }
        init {
            this._moduleName = updateTreeConnection(null, moduleName)
            updateTreeConnections(this._directives, directives)
            this._programMeta = updateTreeConnection(null, programMeta)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ModuleDeclaration).moduleName },
                { n -> (n as ModuleDeclaration).directives },
                { n -> (n as ModuleDeclaration).programMeta },
            )
        }
    }

    /**
     * Regular java file; includes interfaces. JLS 7.6
     */
    class TopLevelClassDeclaration(
        pos: Position,
        programMeta: ProgramMeta,
        packageStatement: PackageStatement? = null,
        imports: Iterable<ImportStatement> = listOf(),
        classDef: ClassOrInterfaceDeclaration,
    ) : BaseTree(pos), Program {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (packageStatement != null && imports.isNotEmpty()) {
                    sharedCodeFormattingTemplate3
                } else if (packageStatement != null) {
                    sharedCodeFormattingTemplate4
                } else if (imports.isNotEmpty()) {
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
                0 -> this.programMeta
                1 -> this.packageStatement ?: FormattableTreeGroup.empty
                2 -> FormattableTreeGroup(this.imports)
                3 -> this.classDef
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _programMeta: ProgramMeta
        override var programMeta: ProgramMeta
            get() = _programMeta
            set(newValue) { _programMeta = updateTreeConnection(_programMeta, newValue) }
        private var _packageStatement: PackageStatement?
        override var packageStatement: PackageStatement?
            get() = _packageStatement
            set(newValue) { _packageStatement = updateTreeConnection(_packageStatement, newValue) }
        private val _imports: MutableList<ImportStatement> = mutableListOf()
        var imports: List<ImportStatement>
            get() = _imports
            set(newValue) { updateTreeConnections(_imports, newValue) }
        private var _classDef: ClassOrInterfaceDeclaration
        var classDef: ClassOrInterfaceDeclaration
            get() = _classDef
            set(newValue) { _classDef = updateTreeConnection(_classDef, newValue) }
        override fun deepCopy(): TopLevelClassDeclaration {
            return TopLevelClassDeclaration(pos, programMeta = this.programMeta.deepCopy(), packageStatement = this.packageStatement?.deepCopy(), imports = this.imports.deepCopy(), classDef = this.classDef.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TopLevelClassDeclaration && this.programMeta == other.programMeta && this.packageStatement == other.packageStatement && this.imports == other.imports && this.classDef == other.classDef
        }
        override fun hashCode(): Int {
            var hc = programMeta.hashCode()
            hc = 31 * hc + (packageStatement?.hashCode() ?: 0)
            hc = 31 * hc + imports.hashCode()
            hc = 31 * hc + classDef.hashCode()
            return hc
        }
        init {
            this._programMeta = updateTreeConnection(null, programMeta)
            this._packageStatement = updateTreeConnection(null, packageStatement)
            updateTreeConnections(this._imports, imports)
            this._classDef = updateTreeConnection(null, classDef)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TopLevelClassDeclaration).programMeta },
                { n -> (n as TopLevelClassDeclaration).packageStatement },
                { n -> (n as TopLevelClassDeclaration).imports },
                { n -> (n as TopLevelClassDeclaration).classDef },
            )
        }
    }

    class PackageStatement(
        pos: Position,
        packageName: QualIdentifier,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate7
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.packageName
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _packageName: QualIdentifier
        var packageName: QualIdentifier
            get() = _packageName
            set(newValue) { _packageName = updateTreeConnection(_packageName, newValue) }
        override fun deepCopy(): PackageStatement {
            return PackageStatement(pos, packageName = this.packageName.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PackageStatement && this.packageName == other.packageName
        }
        override fun hashCode(): Int {
            return packageName.hashCode()
        }
        init {
            this._packageName = updateTreeConnection(null, packageName)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as PackageStatement).packageName },
            )
        }
    }

    /**
     * The metadata for a program adds some additional context that is represented through comments.
     * // source-directory: test
     * // entry-point
     */
    class ProgramMeta(
        pos: Position,
        var sourceDirectory: SourceDirectory = SourceDirectory.MainJava,
        var entryPoint: EntryPoint = EntryPoint.None,
        var testClass: Boolean = false,
        var neededNames: Set<QualifiedName> = emptySet(),
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate8
        override val formatElementCount
            get() = 0
        override fun deepCopy(): ProgramMeta {
            return ProgramMeta(pos, sourceDirectory = this.sourceDirectory, entryPoint = this.entryPoint, testClass = this.testClass, neededNames = this.neededNames)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ProgramMeta && this.sourceDirectory == other.sourceDirectory && this.entryPoint == other.entryPoint && this.testClass == other.testClass && this.neededNames == other.neededNames
        }
        override fun hashCode(): Int {
            var hc = sourceDirectory.hashCode()
            hc = 31 * hc + entryPoint.hashCode()
            hc = 31 * hc + testClass.hashCode()
            hc = 31 * hc + neededNames.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * Used for qualified identifiers in non-expression contexts.
     * See [NameExpr] for a name in an expression.
     * Corresponds to various names in JLS 6.5.
     */
    class QualIdentifier(
        pos: Position,
        ident: Iterable<Identifier>,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate9
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.ident)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _ident: MutableList<Identifier> = mutableListOf()
        var ident: List<Identifier>
            get() = _ident
            set(newValue) { updateTreeConnections(_ident, newValue) }
        override fun deepCopy(): QualIdentifier {
            return QualIdentifier(pos, ident = this.ident.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is QualIdentifier && this.ident == other.ident
        }
        override fun hashCode(): Int {
            return ident.hashCode()
        }
        init {
            updateTreeConnections(this._ident, ident)
            require(ident.isNotEmpty())
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as QualIdentifier).ident },
            )
        }
    }

    sealed interface ModuleDirective : Tree {
        override fun deepCopy(): ModuleDirective
    }

    class RequiresDirective(
        pos: Position,
        moduleName: QualIdentifier,
        var modTransitive: ModTransitive = ModTransitive.Terminal,
        var modStatic: ModStatic = ModStatic.Dynamic,
    ) : BaseTree(pos), ModuleDirective {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (modTransitive == ModTransitive.Transitive && modStatic == ModStatic.Static) {
                    sharedCodeFormattingTemplate10
                } else if (modTransitive == ModTransitive.Transitive) {
                    sharedCodeFormattingTemplate11
                } else if (modStatic == ModStatic.Static) {
                    sharedCodeFormattingTemplate12
                } else {
                    sharedCodeFormattingTemplate13
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.moduleName
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _moduleName: QualIdentifier
        var moduleName: QualIdentifier
            get() = _moduleName
            set(newValue) { _moduleName = updateTreeConnection(_moduleName, newValue) }
        override fun deepCopy(): RequiresDirective {
            return RequiresDirective(pos, moduleName = this.moduleName.deepCopy(), modTransitive = this.modTransitive, modStatic = this.modStatic)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is RequiresDirective && this.moduleName == other.moduleName && this.modTransitive == other.modTransitive && this.modStatic == other.modStatic
        }
        override fun hashCode(): Int {
            var hc = moduleName.hashCode()
            hc = 31 * hc + modTransitive.hashCode()
            hc = 31 * hc + modStatic.hashCode()
            return hc
        }
        init {
            this._moduleName = updateTreeConnection(null, moduleName)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as RequiresDirective).moduleName },
            )
        }
    }

    class ExportsDirective(
        pos: Position,
        packageName: QualIdentifier,
        targetModules: Iterable<QualIdentifier>,
    ) : BaseTree(pos), ModuleDirective {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (targetModules.isNotEmpty()) {
                    sharedCodeFormattingTemplate14
                } else {
                    sharedCodeFormattingTemplate15
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.packageName
                1 -> FormattableTreeGroup(this.targetModules)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _packageName: QualIdentifier
        var packageName: QualIdentifier
            get() = _packageName
            set(newValue) { _packageName = updateTreeConnection(_packageName, newValue) }
        private val _targetModules: MutableList<QualIdentifier> = mutableListOf()
        var targetModules: List<QualIdentifier>
            get() = _targetModules
            set(newValue) { updateTreeConnections(_targetModules, newValue) }
        override fun deepCopy(): ExportsDirective {
            return ExportsDirective(pos, packageName = this.packageName.deepCopy(), targetModules = this.targetModules.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ExportsDirective && this.packageName == other.packageName && this.targetModules == other.targetModules
        }
        override fun hashCode(): Int {
            var hc = packageName.hashCode()
            hc = 31 * hc + targetModules.hashCode()
            return hc
        }
        init {
            this._packageName = updateTreeConnection(null, packageName)
            updateTreeConnections(this._targetModules, targetModules)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ExportsDirective).packageName },
                { n -> (n as ExportsDirective).targetModules },
            )
        }
    }

    class OpensDirective(
        pos: Position,
        packageName: QualIdentifier,
        targetModules: Iterable<QualIdentifier>,
    ) : BaseTree(pos), ModuleDirective {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (targetModules.isNotEmpty()) {
                    sharedCodeFormattingTemplate16
                } else {
                    sharedCodeFormattingTemplate17
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.packageName
                1 -> FormattableTreeGroup(this.targetModules)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _packageName: QualIdentifier
        var packageName: QualIdentifier
            get() = _packageName
            set(newValue) { _packageName = updateTreeConnection(_packageName, newValue) }
        private val _targetModules: MutableList<QualIdentifier> = mutableListOf()
        var targetModules: List<QualIdentifier>
            get() = _targetModules
            set(newValue) { updateTreeConnections(_targetModules, newValue) }
        override fun deepCopy(): OpensDirective {
            return OpensDirective(pos, packageName = this.packageName.deepCopy(), targetModules = this.targetModules.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is OpensDirective && this.packageName == other.packageName && this.targetModules == other.targetModules
        }
        override fun hashCode(): Int {
            var hc = packageName.hashCode()
            hc = 31 * hc + targetModules.hashCode()
            return hc
        }
        init {
            this._packageName = updateTreeConnection(null, packageName)
            updateTreeConnections(this._targetModules, targetModules)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as OpensDirective).packageName },
                { n -> (n as OpensDirective).targetModules },
            )
        }
    }

    class UsesDirective(
        pos: Position,
        typeName: QualIdentifier,
    ) : BaseTree(pos), ModuleDirective {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate18
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.typeName
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _typeName: QualIdentifier
        var typeName: QualIdentifier
            get() = _typeName
            set(newValue) { _typeName = updateTreeConnection(_typeName, newValue) }
        override fun deepCopy(): UsesDirective {
            return UsesDirective(pos, typeName = this.typeName.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is UsesDirective && this.typeName == other.typeName
        }
        override fun hashCode(): Int {
            return typeName.hashCode()
        }
        init {
            this._typeName = updateTreeConnection(null, typeName)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as UsesDirective).typeName },
            )
        }
    }

    class ProvidesDirective(
        pos: Position,
        typeName: QualIdentifier,
        withTypes: Iterable<QualIdentifier>,
    ) : BaseTree(pos), ModuleDirective {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (withTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate19
                } else {
                    sharedCodeFormattingTemplate20
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.typeName
                1 -> FormattableTreeGroup(this.withTypes)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _typeName: QualIdentifier
        var typeName: QualIdentifier
            get() = _typeName
            set(newValue) { _typeName = updateTreeConnection(_typeName, newValue) }
        private val _withTypes: MutableList<QualIdentifier> = mutableListOf()
        var withTypes: List<QualIdentifier>
            get() = _withTypes
            set(newValue) { updateTreeConnections(_withTypes, newValue) }
        override fun deepCopy(): ProvidesDirective {
            return ProvidesDirective(pos, typeName = this.typeName.deepCopy(), withTypes = this.withTypes.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ProvidesDirective && this.typeName == other.typeName && this.withTypes == other.withTypes
        }
        override fun hashCode(): Int {
            var hc = typeName.hashCode()
            hc = 31 * hc + withTypes.hashCode()
            return hc
        }
        init {
            this._typeName = updateTreeConnection(null, typeName)
            updateTreeConnections(this._withTypes, withTypes)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ProvidesDirective).typeName },
                { n -> (n as ProvidesDirective).withTypes },
            )
        }
    }

    /** See JLS 7.5 */
    sealed interface ImportStatement : Tree {
        override fun deepCopy(): ImportStatement
    }

    /**
     * Class declarations also include inner classes.
     */
    sealed interface ClassOrInterfaceDeclaration : Tree {
        val name: Identifier
        val params: TypeParameters
        override fun deepCopy(): ClassOrInterfaceDeclaration
    }

    class ImportClassStatement(
        pos: Position,
        qualifiedName: QualIdentifier,
    ) : BaseTree(pos), ImportStatement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate21
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.qualifiedName
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _qualifiedName: QualIdentifier
        var qualifiedName: QualIdentifier
            get() = _qualifiedName
            set(newValue) { _qualifiedName = updateTreeConnection(_qualifiedName, newValue) }
        override fun deepCopy(): ImportClassStatement {
            return ImportClassStatement(pos, qualifiedName = this.qualifiedName.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImportClassStatement && this.qualifiedName == other.qualifiedName
        }
        override fun hashCode(): Int {
            return qualifiedName.hashCode()
        }
        init {
            this._qualifiedName = updateTreeConnection(null, qualifiedName)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ImportClassStatement).qualifiedName },
            )
        }
    }

    class ImportStaticStatement(
        pos: Position,
        qualifiedName: QualIdentifier,
    ) : BaseTree(pos), ImportStatement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate22
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.qualifiedName
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _qualifiedName: QualIdentifier
        var qualifiedName: QualIdentifier
            get() = _qualifiedName
            set(newValue) { _qualifiedName = updateTreeConnection(_qualifiedName, newValue) }
        override fun deepCopy(): ImportStaticStatement {
            return ImportStaticStatement(pos, qualifiedName = this.qualifiedName.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImportStaticStatement && this.qualifiedName == other.qualifiedName
        }
        override fun hashCode(): Int {
            return qualifiedName.hashCode()
        }
        init {
            this._qualifiedName = updateTreeConnection(null, qualifiedName)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ImportStaticStatement).qualifiedName },
            )
        }
    }

    class ImportClassOnDemand(
        pos: Position,
        qualifiedName: QualIdentifier,
    ) : BaseTree(pos), ImportStatement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate23
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.qualifiedName
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _qualifiedName: QualIdentifier
        var qualifiedName: QualIdentifier
            get() = _qualifiedName
            set(newValue) { _qualifiedName = updateTreeConnection(_qualifiedName, newValue) }
        override fun deepCopy(): ImportClassOnDemand {
            return ImportClassOnDemand(pos, qualifiedName = this.qualifiedName.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImportClassOnDemand && this.qualifiedName == other.qualifiedName
        }
        override fun hashCode(): Int {
            return qualifiedName.hashCode()
        }
        init {
            this._qualifiedName = updateTreeConnection(null, qualifiedName)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ImportClassOnDemand).qualifiedName },
            )
        }
    }

    class ImportStaticOnDemand(
        pos: Position,
        qualifiedName: QualIdentifier,
    ) : BaseTree(pos), ImportStatement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate24
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.qualifiedName
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _qualifiedName: QualIdentifier
        var qualifiedName: QualIdentifier
            get() = _qualifiedName
            set(newValue) { _qualifiedName = updateTreeConnection(_qualifiedName, newValue) }
        override fun deepCopy(): ImportStaticOnDemand {
            return ImportStaticOnDemand(pos, qualifiedName = this.qualifiedName.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImportStaticOnDemand && this.qualifiedName == other.qualifiedName
        }
        override fun hashCode(): Int {
            return qualifiedName.hashCode()
        }
        init {
            this._qualifiedName = updateTreeConnection(null, qualifiedName)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ImportStaticOnDemand).qualifiedName },
            )
        }
    }

    /**
     * Elements that live inside a class body. JLS 8.1.7
     */
    sealed interface ClassBodyDeclaration : Tree {
        override fun deepCopy(): ClassBodyDeclaration
    }

    /**
     * Elements that live inside a class body. JLS 8.1.7
     */
    sealed interface InterfaceBodyDeclaration : Tree {
        override fun deepCopy(): InterfaceBodyDeclaration
    }

    /**
     * "BlockStatement" in JLS 14.2. This includes statements that can't be placed
     * after a LabeledStatement.
     */
    sealed interface BlockLevelStatement : Tree {
        override fun deepCopy(): BlockLevelStatement
    }

    /**
     * Uses the standard slash-star comments; preferred to enable single-line code output.
     */
    class CommentLine(
        pos: Position,
        var commentText: String,
    ) : BaseTree(pos), ImportStatement, ClassBodyDeclaration, InterfaceBodyDeclaration, BlockLevelStatement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            val tokenText = if (commentText.isEmpty()) {
                "/* */"
            } else {
                "/* $commentText */"
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
            // Prohibit the closing comment sequence
            checkCommentText(commentText)
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * A normal class declaration. JLS 8.1
     */
    class ClassDeclaration(
        pos: Position,
        javadoc: JavadocComment? = null,
        anns: Iterable<Annotation> = listOf(),
        mods: ClassModifiers = ClassModifiers(pos),
        name: Identifier,
        params: TypeParameters = TypeParameters(pos),
        classExtends: ClassType? = null,
        classImplements: Iterable<ClassType> = listOf(),
        permits: Iterable<ClassType> = listOf(),
        body: Iterable<ClassBodyDeclaration>,
    ) : BaseTree(pos), ClassOrInterfaceDeclaration, ClassBodyDeclaration, InterfaceBodyDeclaration {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (javadoc != null && classExtends != null && classImplements.isNotEmpty() && permits.isNotEmpty()) {
                    sharedCodeFormattingTemplate25
                } else if (javadoc != null && classExtends != null && classImplements.isNotEmpty()) {
                    sharedCodeFormattingTemplate26
                } else if (javadoc != null && classExtends != null && permits.isNotEmpty()) {
                    sharedCodeFormattingTemplate27
                } else if (javadoc != null && classExtends != null) {
                    sharedCodeFormattingTemplate28
                } else if (javadoc != null && classImplements.isNotEmpty() && permits.isNotEmpty()) {
                    sharedCodeFormattingTemplate29
                } else if (javadoc != null && classImplements.isNotEmpty()) {
                    sharedCodeFormattingTemplate30
                } else if (javadoc != null && permits.isNotEmpty()) {
                    sharedCodeFormattingTemplate31
                } else if (javadoc != null) {
                    sharedCodeFormattingTemplate32
                } else if (classExtends != null && classImplements.isNotEmpty() && permits.isNotEmpty()) {
                    sharedCodeFormattingTemplate33
                } else if (classExtends != null && classImplements.isNotEmpty()) {
                    sharedCodeFormattingTemplate34
                } else if (classExtends != null && permits.isNotEmpty()) {
                    sharedCodeFormattingTemplate35
                } else if (classExtends != null) {
                    sharedCodeFormattingTemplate36
                } else if (classImplements.isNotEmpty() && permits.isNotEmpty()) {
                    sharedCodeFormattingTemplate37
                } else if (classImplements.isNotEmpty()) {
                    sharedCodeFormattingTemplate38
                } else if (permits.isNotEmpty()) {
                    sharedCodeFormattingTemplate39
                } else {
                    sharedCodeFormattingTemplate40
                }
        override val formatElementCount
            get() = 9
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.javadoc ?: FormattableTreeGroup.empty
                1 -> FormattableTreeGroup(this.anns)
                2 -> this.mods
                3 -> this.name
                4 -> this.params
                5 -> this.classExtends ?: FormattableTreeGroup.empty
                6 -> FormattableTreeGroup(this.classImplements)
                7 -> FormattableTreeGroup(this.permits)
                8 -> FormattableTreeGroup(this.body)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _javadoc: JavadocComment?
        var javadoc: JavadocComment?
            get() = _javadoc
            set(newValue) { _javadoc = updateTreeConnection(_javadoc, newValue) }
        private val _anns: MutableList<Annotation> = mutableListOf()
        var anns: List<Annotation>
            get() = _anns
            set(newValue) { updateTreeConnections(_anns, newValue) }
        private var _mods: ClassModifiers
        var mods: ClassModifiers
            get() = _mods
            set(newValue) { _mods = updateTreeConnection(_mods, newValue) }
        private var _name: Identifier
        override var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _params: TypeParameters
        override var params: TypeParameters
            get() = _params
            set(newValue) { _params = updateTreeConnection(_params, newValue) }
        private var _classExtends: ClassType?
        var classExtends: ClassType?
            get() = _classExtends
            set(newValue) { _classExtends = updateTreeConnection(_classExtends, newValue) }
        private val _classImplements: MutableList<ClassType> = mutableListOf()
        var classImplements: List<ClassType>
            get() = _classImplements
            set(newValue) { updateTreeConnections(_classImplements, newValue) }
        private val _permits: MutableList<ClassType> = mutableListOf()
        var permits: List<ClassType>
            get() = _permits
            set(newValue) { updateTreeConnections(_permits, newValue) }
        private val _body: MutableList<ClassBodyDeclaration> = mutableListOf()
        var body: List<ClassBodyDeclaration>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        override fun deepCopy(): ClassDeclaration {
            return ClassDeclaration(pos, javadoc = this.javadoc?.deepCopy(), anns = this.anns.deepCopy(), mods = this.mods.deepCopy(), name = this.name.deepCopy(), params = this.params.deepCopy(), classExtends = this.classExtends?.deepCopy(), classImplements = this.classImplements.deepCopy(), permits = this.permits.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ClassDeclaration && this.javadoc == other.javadoc && this.anns == other.anns && this.mods == other.mods && this.name == other.name && this.params == other.params && this.classExtends == other.classExtends && this.classImplements == other.classImplements && this.permits == other.permits && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = (javadoc?.hashCode() ?: 0)
            hc = 31 * hc + anns.hashCode()
            hc = 31 * hc + mods.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + params.hashCode()
            hc = 31 * hc + (classExtends?.hashCode() ?: 0)
            hc = 31 * hc + classImplements.hashCode()
            hc = 31 * hc + permits.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._javadoc = updateTreeConnection(null, javadoc)
            updateTreeConnections(this._anns, anns)
            this._mods = updateTreeConnection(null, mods)
            this._name = updateTreeConnection(null, name)
            this._params = updateTreeConnection(null, params)
            this._classExtends = updateTreeConnection(null, classExtends)
            updateTreeConnections(this._classImplements, classImplements)
            updateTreeConnections(this._permits, permits)
            updateTreeConnections(this._body, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ClassDeclaration).javadoc },
                { n -> (n as ClassDeclaration).anns },
                { n -> (n as ClassDeclaration).mods },
                { n -> (n as ClassDeclaration).name },
                { n -> (n as ClassDeclaration).params },
                { n -> (n as ClassDeclaration).classExtends },
                { n -> (n as ClassDeclaration).classImplements },
                { n -> (n as ClassDeclaration).permits },
                { n -> (n as ClassDeclaration).body },
            )
        }
    }

    /**
     * An interface declaration. JLS 8.1
     */
    class InterfaceDeclaration(
        pos: Position,
        javadoc: JavadocComment? = null,
        anns: Iterable<Annotation> = listOf(),
        mods: InterfaceModifiers = InterfaceModifiers(pos),
        name: Identifier,
        params: TypeParameters = TypeParameters(pos),
        classExtends: Iterable<ClassType> = listOf(),
        permits: Iterable<ClassType> = listOf(),
        body: Iterable<InterfaceBodyDeclaration>,
    ) : BaseTree(pos), ClassOrInterfaceDeclaration, ClassBodyDeclaration, InterfaceBodyDeclaration {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (javadoc != null && classExtends.isNotEmpty() && permits.isNotEmpty()) {
                    sharedCodeFormattingTemplate41
                } else if (javadoc != null && classExtends.isNotEmpty()) {
                    sharedCodeFormattingTemplate42
                } else if (javadoc != null && permits.isNotEmpty()) {
                    sharedCodeFormattingTemplate43
                } else if (javadoc != null) {
                    sharedCodeFormattingTemplate44
                } else if (classExtends.isNotEmpty() && permits.isNotEmpty()) {
                    sharedCodeFormattingTemplate45
                } else if (classExtends.isNotEmpty()) {
                    sharedCodeFormattingTemplate46
                } else if (permits.isNotEmpty()) {
                    sharedCodeFormattingTemplate47
                } else {
                    sharedCodeFormattingTemplate48
                }
        override val formatElementCount
            get() = 8
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.javadoc ?: FormattableTreeGroup.empty
                1 -> FormattableTreeGroup(this.anns)
                2 -> this.mods
                3 -> this.name
                4 -> this.params
                5 -> FormattableTreeGroup(this.classExtends)
                6 -> FormattableTreeGroup(this.permits)
                7 -> FormattableTreeGroup(this.body)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _javadoc: JavadocComment?
        var javadoc: JavadocComment?
            get() = _javadoc
            set(newValue) { _javadoc = updateTreeConnection(_javadoc, newValue) }
        private val _anns: MutableList<Annotation> = mutableListOf()
        var anns: List<Annotation>
            get() = _anns
            set(newValue) { updateTreeConnections(_anns, newValue) }
        private var _mods: InterfaceModifiers
        var mods: InterfaceModifiers
            get() = _mods
            set(newValue) { _mods = updateTreeConnection(_mods, newValue) }
        private var _name: Identifier
        override var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _params: TypeParameters
        override var params: TypeParameters
            get() = _params
            set(newValue) { _params = updateTreeConnection(_params, newValue) }
        private val _classExtends: MutableList<ClassType> = mutableListOf()
        var classExtends: List<ClassType>
            get() = _classExtends
            set(newValue) { updateTreeConnections(_classExtends, newValue) }
        private val _permits: MutableList<ClassType> = mutableListOf()
        var permits: List<ClassType>
            get() = _permits
            set(newValue) { updateTreeConnections(_permits, newValue) }
        private val _body: MutableList<InterfaceBodyDeclaration> = mutableListOf()
        var body: List<InterfaceBodyDeclaration>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        override fun deepCopy(): InterfaceDeclaration {
            return InterfaceDeclaration(pos, javadoc = this.javadoc?.deepCopy(), anns = this.anns.deepCopy(), mods = this.mods.deepCopy(), name = this.name.deepCopy(), params = this.params.deepCopy(), classExtends = this.classExtends.deepCopy(), permits = this.permits.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is InterfaceDeclaration && this.javadoc == other.javadoc && this.anns == other.anns && this.mods == other.mods && this.name == other.name && this.params == other.params && this.classExtends == other.classExtends && this.permits == other.permits && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = (javadoc?.hashCode() ?: 0)
            hc = 31 * hc + anns.hashCode()
            hc = 31 * hc + mods.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + params.hashCode()
            hc = 31 * hc + classExtends.hashCode()
            hc = 31 * hc + permits.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._javadoc = updateTreeConnection(null, javadoc)
            updateTreeConnections(this._anns, anns)
            this._mods = updateTreeConnection(null, mods)
            this._name = updateTreeConnection(null, name)
            this._params = updateTreeConnection(null, params)
            updateTreeConnections(this._classExtends, classExtends)
            updateTreeConnections(this._permits, permits)
            updateTreeConnections(this._body, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as InterfaceDeclaration).javadoc },
                { n -> (n as InterfaceDeclaration).anns },
                { n -> (n as InterfaceDeclaration).mods },
                { n -> (n as InterfaceDeclaration).name },
                { n -> (n as InterfaceDeclaration).params },
                { n -> (n as InterfaceDeclaration).classExtends },
                { n -> (n as InterfaceDeclaration).permits },
                { n -> (n as InterfaceDeclaration).body },
            )
        }
    }

    /**
     * Used for short identifiers in non-expression contexts. See [NameExpr] for a name in an expression.
     * Corresponds to Identifier, TypeIdentifier and UnqualifiedMethodIdentifier in JLS 3.8.
     * See also notes at [isIdentifier].
     */
    class Identifier(
        pos: Position,
        var outName: OutName,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
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
        init {
            require(outName.outputNameText.isIdentifier())
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * Generic type parameters. Parameters introduce new types. JLS 8.1.2
     * For example, in `class Foo<T>` the parameter T can be used in fields and methods.
     * TODO add type bounds
     */
    class TypeParameters(
        pos: Position,
        params: Iterable<TypeParameter> = listOf(),
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (params.isNotEmpty()) {
                    sharedCodeFormattingTemplate49
                } else {
                    sharedCodeFormattingTemplate50
                }
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
        private val _params: MutableList<TypeParameter> = mutableListOf()
        var params: List<TypeParameter>
            get() = _params
            set(newValue) { updateTreeConnections(_params, newValue) }
        override fun deepCopy(): TypeParameters {
            return TypeParameters(pos, params = this.params.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeParameters && this.params == other.params
        }
        override fun hashCode(): Int {
            return params.hashCode()
        }
        init {
            updateTreeConnections(this._params, params)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeParameters).params },
            )
        }
    }

    /** A javadoc comment broken into separate lines each of which is a comment token */
    class JavadocComment(
        pos: Position,
        val commentLines: MutableList<OutputToken>,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (commentLines.isNotEmpty()) {
                    sharedCodeFormattingTemplate51
                } else {
                    sharedCodeFormattingTemplate50
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> IndexableFormattableTreeElement.wrap(this.commentLines)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        override fun deepCopy(): JavadocComment {
            return JavadocComment(pos, commentLines = this.commentLines)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is JavadocComment && this.commentLines == other.commentLines
        }
        override fun hashCode(): Int {
            return commentLines.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * Java annotations allow a programmer to annotate structural elements with data that can be read
     * by third party processors. There are a few potential points of confusion to note.
     *
     * When introduced in Java 5, all annotations applied to a declaration. For example, `@Override` or
     * `@Deprecated` applied to the entire method.
     *
     * Java 8 allowed annotations to be applied to types. If writing an annotation like `@NotNull` from scratch,
     * you'd probably only allow it to target `ElementType.TYPE_USE` but it's defined to target `METHOD`, `FIELD`,
     * `LOCAL_VARIABLE`, etc. See: https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.7.4
     *
     * The common convention is to import annotations; we're using fully qualified names that can be replaced with imports
     * by the import pass.
     *
     * See JLS 9.7
     */
    class Annotation(
        pos: Position,
        name: QualIdentifier,
        params: Iterable<AnnotationParam> = listOf(),
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (params.isNotEmpty()) {
                    sharedCodeFormattingTemplate52
                } else {
                    sharedCodeFormattingTemplate53
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name
                1 -> FormattableTreeGroup(this.params)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _name: QualIdentifier
        var name: QualIdentifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private val _params: MutableList<AnnotationParam> = mutableListOf()
        var params: List<AnnotationParam>
            get() = _params
            set(newValue) { updateTreeConnections(_params, newValue) }
        override fun deepCopy(): Annotation {
            return Annotation(pos, name = this.name.deepCopy(), params = this.params.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Annotation && this.name == other.name && this.params == other.params
        }
        override fun hashCode(): Int {
            var hc = name.hashCode()
            hc = 31 * hc + params.hashCode()
            return hc
        }
        init {
            this._name = updateTreeConnection(null, name)
            updateTreeConnections(this._params, params)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Annotation).name },
                { n -> (n as Annotation).params },
            )
        }
    }

    /**
     * Annotations and various keywords applicable to classes. JLS 8.1.1
     */
    class ClassModifiers(
        pos: Position,
        var modAccess: ModAccess = ModAccess.PackagePrivate,
        var modAbstract: ModAbstract = ModAbstract.Concrete,
        var modStatic: ModStatic = ModStatic.Dynamic,
        var modFinal: ModSealedFinal = ModSealedFinal.Open,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            modAccess.emit(tokenSink)
            modAbstract.emit(tokenSink)
            modStatic.emit(tokenSink)
            modFinal.emit(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): ClassModifiers {
            return ClassModifiers(pos, modAccess = this.modAccess, modAbstract = this.modAbstract, modStatic = this.modStatic, modFinal = this.modFinal)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ClassModifiers && this.modAccess == other.modAccess && this.modAbstract == other.modAbstract && this.modStatic == other.modStatic && this.modFinal == other.modFinal
        }
        override fun hashCode(): Int {
            var hc = modAccess.hashCode()
            hc = 31 * hc + modAbstract.hashCode()
            hc = 31 * hc + modStatic.hashCode()
            hc = 31 * hc + modFinal.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * Result types are any regular type, but also a void type to mark methods that must be called for side-effects.
     */
    sealed interface ResultType : Tree {
        override fun deepCopy(): ResultType
    }

    /** A type of a local variable or field. */
    sealed interface Type : Tree, ResultType {
        override fun deepCopy(): Type
    }

    /** A type that is implemented by reference. */
    sealed interface ReferenceType : Tree {
        override fun deepCopy(): ReferenceType
    }

    /**
     * Identifies a reference type; a class or interface. JLS 8.3
     */
    class ClassType(
        pos: Position,
        anns: Iterable<Annotation> = listOf(),
        type: QualIdentifier,
        args: TypeArguments? = null,
    ) : BaseTree(pos), Type, ReferenceType {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (args != null) {
                    sharedCodeFormattingTemplate54
                } else {
                    sharedCodeFormattingTemplate55
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.anns)
                1 -> this.type
                2 -> this.args ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _anns: MutableList<Annotation> = mutableListOf()
        var anns: List<Annotation>
            get() = _anns
            set(newValue) { updateTreeConnections(_anns, newValue) }
        private var _type: QualIdentifier
        var type: QualIdentifier
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _args: TypeArguments?
        var args: TypeArguments?
            get() = _args
            set(newValue) { _args = updateTreeConnection(_args, newValue) }
        override fun deepCopy(): ClassType {
            return ClassType(pos, anns = this.anns.deepCopy(), type = this.type.deepCopy(), args = this.args?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ClassType && this.anns == other.anns && this.type == other.type && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = anns.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + (args?.hashCode() ?: 0)
            return hc
        }
        init {
            updateTreeConnections(this._anns, anns)
            this._type = updateTreeConnection(null, type)
            this._args = updateTreeConnection(null, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ClassType).anns },
                { n -> (n as ClassType).type },
                { n -> (n as ClassType).args },
            )
        }
    }

    /**
     * Annotations and various keywords applicable to interfaces. JLS 9.1.1
     */
    class InterfaceModifiers(
        pos: Position,
        var modAccess: ModAccess = ModAccess.PackagePrivate,
        var modSealed: ModSealed = ModSealed.Open,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            modAccess.emit(tokenSink)
            modSealed.emit(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): InterfaceModifiers {
            return InterfaceModifiers(pos, modAccess = this.modAccess, modSealed = this.modSealed)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is InterfaceModifiers && this.modAccess == other.modAccess && this.modSealed == other.modSealed
        }
        override fun hashCode(): Int {
            var hc = modAccess.hashCode()
            hc = 31 * hc + modSealed.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * A local class declaration. JLS 14.3
     */
    class LocalClassDeclaration(
        pos: Position,
        javadoc: JavadocComment? = null,
        anns: Iterable<Annotation> = listOf(),
        mods: LocalClassModifiers = LocalClassModifiers(pos),
        name: Identifier,
        params: TypeParameters = TypeParameters(pos),
        classExtends: ClassType? = null,
        classImplements: Iterable<ClassType> = listOf(),
        body: Iterable<ClassBodyDeclaration>,
    ) : BaseTree(pos), BlockLevelStatement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (javadoc != null && classExtends != null && classImplements.isNotEmpty()) {
                    sharedCodeFormattingTemplate56
                } else if (javadoc != null && classExtends != null) {
                    sharedCodeFormattingTemplate57
                } else if (javadoc != null && classImplements.isNotEmpty()) {
                    sharedCodeFormattingTemplate58
                } else if (javadoc != null) {
                    sharedCodeFormattingTemplate59
                } else if (classExtends != null && classImplements.isNotEmpty()) {
                    sharedCodeFormattingTemplate60
                } else if (classExtends != null) {
                    sharedCodeFormattingTemplate61
                } else if (classImplements.isNotEmpty()) {
                    sharedCodeFormattingTemplate62
                } else {
                    sharedCodeFormattingTemplate63
                }
        override val formatElementCount
            get() = 8
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.javadoc ?: FormattableTreeGroup.empty
                1 -> FormattableTreeGroup(this.anns)
                2 -> this.mods
                3 -> this.name
                4 -> this.params
                5 -> this.classExtends ?: FormattableTreeGroup.empty
                6 -> FormattableTreeGroup(this.classImplements)
                7 -> FormattableTreeGroup(this.body)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _javadoc: JavadocComment?
        var javadoc: JavadocComment?
            get() = _javadoc
            set(newValue) { _javadoc = updateTreeConnection(_javadoc, newValue) }
        private val _anns: MutableList<Annotation> = mutableListOf()
        var anns: List<Annotation>
            get() = _anns
            set(newValue) { updateTreeConnections(_anns, newValue) }
        private var _mods: LocalClassModifiers
        var mods: LocalClassModifiers
            get() = _mods
            set(newValue) { _mods = updateTreeConnection(_mods, newValue) }
        private var _name: Identifier
        var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _params: TypeParameters
        var params: TypeParameters
            get() = _params
            set(newValue) { _params = updateTreeConnection(_params, newValue) }
        private var _classExtends: ClassType?
        var classExtends: ClassType?
            get() = _classExtends
            set(newValue) { _classExtends = updateTreeConnection(_classExtends, newValue) }
        private val _classImplements: MutableList<ClassType> = mutableListOf()
        var classImplements: List<ClassType>
            get() = _classImplements
            set(newValue) { updateTreeConnections(_classImplements, newValue) }
        private val _body: MutableList<ClassBodyDeclaration> = mutableListOf()
        var body: List<ClassBodyDeclaration>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        override fun deepCopy(): LocalClassDeclaration {
            return LocalClassDeclaration(pos, javadoc = this.javadoc?.deepCopy(), anns = this.anns.deepCopy(), mods = this.mods.deepCopy(), name = this.name.deepCopy(), params = this.params.deepCopy(), classExtends = this.classExtends?.deepCopy(), classImplements = this.classImplements.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LocalClassDeclaration && this.javadoc == other.javadoc && this.anns == other.anns && this.mods == other.mods && this.name == other.name && this.params == other.params && this.classExtends == other.classExtends && this.classImplements == other.classImplements && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = (javadoc?.hashCode() ?: 0)
            hc = 31 * hc + anns.hashCode()
            hc = 31 * hc + mods.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + params.hashCode()
            hc = 31 * hc + (classExtends?.hashCode() ?: 0)
            hc = 31 * hc + classImplements.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._javadoc = updateTreeConnection(null, javadoc)
            updateTreeConnections(this._anns, anns)
            this._mods = updateTreeConnection(null, mods)
            this._name = updateTreeConnection(null, name)
            this._params = updateTreeConnection(null, params)
            this._classExtends = updateTreeConnection(null, classExtends)
            updateTreeConnections(this._classImplements, classImplements)
            updateTreeConnections(this._body, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LocalClassDeclaration).javadoc },
                { n -> (n as LocalClassDeclaration).anns },
                { n -> (n as LocalClassDeclaration).mods },
                { n -> (n as LocalClassDeclaration).name },
                { n -> (n as LocalClassDeclaration).params },
                { n -> (n as LocalClassDeclaration).classExtends },
                { n -> (n as LocalClassDeclaration).classImplements },
                { n -> (n as LocalClassDeclaration).body },
            )
        }
    }

    /**
     * Annotations and various keywords applicable to local classes. JLS 8.1.1
     */
    class LocalClassModifiers(
        pos: Position,
        var modAbstract: ModAbstract = ModAbstract.Concrete,
        var modFinal: ModFinal = ModFinal.Open,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            modAbstract.emit(tokenSink)
            modFinal.emit(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): LocalClassModifiers {
            return LocalClassModifiers(pos, modAbstract = this.modAbstract, modFinal = this.modFinal)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LocalClassModifiers && this.modAbstract == other.modAbstract && this.modFinal == other.modFinal
        }
        override fun hashCode(): Int {
            var hc = modAbstract.hashCode()
            hc = 31 * hc + modFinal.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * A local interface declaration, since Java 16. JLS 14.3
     */
    class LocalInterfaceDeclaration(
        pos: Position,
        javadoc: JavadocComment? = null,
        anns: Iterable<Annotation> = listOf(),
        name: Identifier,
        params: TypeParameters = TypeParameters(pos),
        classExtends: Iterable<ClassType> = listOf(),
        body: Iterable<InterfaceBodyDeclaration>,
    ) : BaseTree(pos), BlockLevelStatement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (javadoc != null && classExtends.isNotEmpty()) {
                    sharedCodeFormattingTemplate64
                } else if (javadoc != null) {
                    sharedCodeFormattingTemplate65
                } else if (classExtends.isNotEmpty()) {
                    sharedCodeFormattingTemplate66
                } else {
                    sharedCodeFormattingTemplate67
                }
        override val formatElementCount
            get() = 6
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.javadoc ?: FormattableTreeGroup.empty
                1 -> FormattableTreeGroup(this.anns)
                2 -> this.name
                3 -> this.params
                4 -> FormattableTreeGroup(this.classExtends)
                5 -> FormattableTreeGroup(this.body)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _javadoc: JavadocComment?
        var javadoc: JavadocComment?
            get() = _javadoc
            set(newValue) { _javadoc = updateTreeConnection(_javadoc, newValue) }
        private val _anns: MutableList<Annotation> = mutableListOf()
        var anns: List<Annotation>
            get() = _anns
            set(newValue) { updateTreeConnections(_anns, newValue) }
        private var _name: Identifier
        var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _params: TypeParameters
        var params: TypeParameters
            get() = _params
            set(newValue) { _params = updateTreeConnection(_params, newValue) }
        private val _classExtends: MutableList<ClassType> = mutableListOf()
        var classExtends: List<ClassType>
            get() = _classExtends
            set(newValue) { updateTreeConnections(_classExtends, newValue) }
        private val _body: MutableList<InterfaceBodyDeclaration> = mutableListOf()
        var body: List<InterfaceBodyDeclaration>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        override fun deepCopy(): LocalInterfaceDeclaration {
            return LocalInterfaceDeclaration(pos, javadoc = this.javadoc?.deepCopy(), anns = this.anns.deepCopy(), name = this.name.deepCopy(), params = this.params.deepCopy(), classExtends = this.classExtends.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LocalInterfaceDeclaration && this.javadoc == other.javadoc && this.anns == other.anns && this.name == other.name && this.params == other.params && this.classExtends == other.classExtends && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = (javadoc?.hashCode() ?: 0)
            hc = 31 * hc + anns.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + params.hashCode()
            hc = 31 * hc + classExtends.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._javadoc = updateTreeConnection(null, javadoc)
            updateTreeConnections(this._anns, anns)
            this._name = updateTreeConnection(null, name)
            this._params = updateTreeConnection(null, params)
            updateTreeConnections(this._classExtends, classExtends)
            updateTreeConnections(this._body, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LocalInterfaceDeclaration).javadoc },
                { n -> (n as LocalInterfaceDeclaration).anns },
                { n -> (n as LocalInterfaceDeclaration).name },
                { n -> (n as LocalInterfaceDeclaration).params },
                { n -> (n as LocalInterfaceDeclaration).classExtends },
                { n -> (n as LocalInterfaceDeclaration).body },
            )
        }
    }

    class TypeParameter(
        pos: Position,
        anns: Iterable<Annotation> = listOf(),
        type: Identifier,
        upperBounds: Iterable<ReferenceType>,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (upperBounds.isNotEmpty()) {
                    sharedCodeFormattingTemplate68
                } else {
                    sharedCodeFormattingTemplate55
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.anns)
                1 -> this.type
                2 -> FormattableTreeGroup(this.upperBounds)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _anns: MutableList<Annotation> = mutableListOf()
        var anns: List<Annotation>
            get() = _anns
            set(newValue) { updateTreeConnections(_anns, newValue) }
        private var _type: Identifier
        var type: Identifier
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private val _upperBounds: MutableList<ReferenceType> = mutableListOf()
        var upperBounds: List<ReferenceType>
            get() = _upperBounds
            set(newValue) { updateTreeConnections(_upperBounds, newValue) }
        override fun deepCopy(): TypeParameter {
            return TypeParameter(pos, anns = this.anns.deepCopy(), type = this.type.deepCopy(), upperBounds = this.upperBounds.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeParameter && this.anns == other.anns && this.type == other.type && this.upperBounds == other.upperBounds
        }
        override fun hashCode(): Int {
            var hc = anns.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + upperBounds.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._anns, anns)
            this._type = updateTreeConnection(null, type)
            updateTreeConnections(this._upperBounds, upperBounds)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeParameter).anns },
                { n -> (n as TypeParameter).type },
                { n -> (n as TypeParameter).upperBounds },
            )
        }
    }

    /**
     * Generic type arguments. Arguments make a generic type concrete. For instance, in `List<String>`
     * the generic type `List` accepts a type parameter, thus a list of strings. JLS 4.5.1
     */
    class TypeArguments(
        pos: Position,
        args: Iterable<TypeArgument> = listOf(),
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate49
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
        private val _args: MutableList<TypeArgument> = mutableListOf()
        var args: List<TypeArgument>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): TypeArguments {
            return TypeArguments(pos, args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeArguments && this.args == other.args
        }
        override fun hashCode(): Int {
            return args.hashCode()
        }
        init {
            updateTreeConnections(this._args, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeArguments).args },
            )
        }
    }

    sealed interface TypeArgument : Tree {
        override fun deepCopy(): TypeArgument
    }

    /**
     * Generic type argument of the form `@annotation Name<Arg, Arg>`. This is roughly ReferenceType from JLS 4.3
     */
    class ReferenceTypeArgument(
        pos: Position,
        annType: AnnotatedQualIdentifier,
        args: TypeArguments? = null,
    ) : BaseTree(pos), TypeArgument {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (args != null) {
                    sharedCodeFormattingTemplate0
                } else {
                    sharedCodeFormattingTemplate69
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.annType
                1 -> this.args ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _annType: AnnotatedQualIdentifier
        var annType: AnnotatedQualIdentifier
            get() = _annType
            set(newValue) { _annType = updateTreeConnection(_annType, newValue) }
        private var _args: TypeArguments?
        var args: TypeArguments?
            get() = _args
            set(newValue) { _args = updateTreeConnection(_args, newValue) }
        override fun deepCopy(): ReferenceTypeArgument {
            return ReferenceTypeArgument(pos, annType = this.annType.deepCopy(), args = this.args?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ReferenceTypeArgument && this.annType == other.annType && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = annType.hashCode()
            hc = 31 * hc + (args?.hashCode() ?: 0)
            return hc
        }
        init {
            this._annType = updateTreeConnection(null, annType)
            this._args = updateTreeConnection(null, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ReferenceTypeArgument).annType },
                { n -> (n as ReferenceTypeArgument).args },
            )
        }
    }

    /**
     * Generic type argument of the form `@annotation ?`. This is roughly ReferenceType from JLS 4.3
     */
    class WildcardTypeArgument(
        pos: Position,
        anns: Iterable<Annotation> = listOf(),
    ) : BaseTree(pos), TypeArgument {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate70
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.anns)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _anns: MutableList<Annotation> = mutableListOf()
        var anns: List<Annotation>
            get() = _anns
            set(newValue) { updateTreeConnections(_anns, newValue) }
        override fun deepCopy(): WildcardTypeArgument {
            return WildcardTypeArgument(pos, anns = this.anns.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is WildcardTypeArgument && this.anns == other.anns
        }
        override fun hashCode(): Int {
            return anns.hashCode()
        }
        init {
            updateTreeConnections(this._anns, anns)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as WildcardTypeArgument).anns },
            )
        }
    }

    /**
     * Generic upper-bound type argument of the form `@annotation ? extends Name<Arg, Arg>`.
     */
    class ExtendsTypeArgument(
        pos: Position,
        anns: Iterable<Annotation> = listOf(),
        type: QualIdentifier,
        args: TypeArguments? = null,
    ) : BaseTree(pos), TypeArgument {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (args != null) {
                    sharedCodeFormattingTemplate71
                } else {
                    sharedCodeFormattingTemplate72
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.anns)
                1 -> this.type
                2 -> this.args ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _anns: MutableList<Annotation> = mutableListOf()
        var anns: List<Annotation>
            get() = _anns
            set(newValue) { updateTreeConnections(_anns, newValue) }
        private var _type: QualIdentifier
        var type: QualIdentifier
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _args: TypeArguments?
        var args: TypeArguments?
            get() = _args
            set(newValue) { _args = updateTreeConnection(_args, newValue) }
        override fun deepCopy(): ExtendsTypeArgument {
            return ExtendsTypeArgument(pos, anns = this.anns.deepCopy(), type = this.type.deepCopy(), args = this.args?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ExtendsTypeArgument && this.anns == other.anns && this.type == other.type && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = anns.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + (args?.hashCode() ?: 0)
            return hc
        }
        init {
            updateTreeConnections(this._anns, anns)
            this._type = updateTreeConnection(null, type)
            this._args = updateTreeConnection(null, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ExtendsTypeArgument).anns },
                { n -> (n as ExtendsTypeArgument).type },
                { n -> (n as ExtendsTypeArgument).args },
            )
        }
    }

    /**
     * Generic lower-bound type argument of the form `@annotation ? super Name<Arg, Arg>`.
     */
    class SuperTypeArgument(
        pos: Position,
        anns: Iterable<Annotation> = listOf(),
        type: QualIdentifier,
        args: TypeArguments? = null,
    ) : BaseTree(pos), TypeArgument {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (args != null) {
                    sharedCodeFormattingTemplate73
                } else {
                    sharedCodeFormattingTemplate74
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.anns)
                1 -> this.type
                2 -> this.args ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _anns: MutableList<Annotation> = mutableListOf()
        var anns: List<Annotation>
            get() = _anns
            set(newValue) { updateTreeConnections(_anns, newValue) }
        private var _type: QualIdentifier
        var type: QualIdentifier
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _args: TypeArguments?
        var args: TypeArguments?
            get() = _args
            set(newValue) { _args = updateTreeConnection(_args, newValue) }
        override fun deepCopy(): SuperTypeArgument {
            return SuperTypeArgument(pos, anns = this.anns.deepCopy(), type = this.type.deepCopy(), args = this.args?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SuperTypeArgument && this.anns == other.anns && this.type == other.type && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = anns.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + (args?.hashCode() ?: 0)
            return hc
        }
        init {
            updateTreeConnections(this._anns, anns)
            this._type = updateTreeConnection(null, type)
            this._args = updateTreeConnection(null, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SuperTypeArgument).anns },
                { n -> (n as SuperTypeArgument).type },
                { n -> (n as SuperTypeArgument).args },
            )
        }
    }

    /**
     * Annotations on a type argument are special. This implements `pkg.pkg.@annotation Name`.
     */
    class AnnotatedQualIdentifier(
        pos: Position,
        pkg: Iterable<Identifier>,
        anns: Iterable<Annotation>,
        type: Identifier,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (pkg.isNotEmpty()) {
                    sharedCodeFormattingTemplate75
                } else {
                    sharedCodeFormattingTemplate76
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.pkg)
                1 -> FormattableTreeGroup(this.anns)
                2 -> this.type
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _pkg: MutableList<Identifier> = mutableListOf()
        var pkg: List<Identifier>
            get() = _pkg
            set(newValue) { updateTreeConnections(_pkg, newValue) }
        private val _anns: MutableList<Annotation> = mutableListOf()
        var anns: List<Annotation>
            get() = _anns
            set(newValue) { updateTreeConnections(_anns, newValue) }
        private var _type: Identifier
        var type: Identifier
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        override fun deepCopy(): AnnotatedQualIdentifier {
            return AnnotatedQualIdentifier(pos, pkg = this.pkg.deepCopy(), anns = this.anns.deepCopy(), type = this.type.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AnnotatedQualIdentifier && this.pkg == other.pkg && this.anns == other.anns && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = pkg.hashCode()
            hc = 31 * hc + anns.hashCode()
            hc = 31 * hc + type.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._pkg, pkg)
            updateTreeConnections(this._anns, anns)
            this._type = updateTreeConnection(null, type)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AnnotatedQualIdentifier).pkg },
                { n -> (n as AnnotatedQualIdentifier).anns },
                { n -> (n as AnnotatedQualIdentifier).type },
            )
        }
    }

    class PrimitiveType(
        pos: Position,
        var type: Primitive,
    ) : BaseTree(pos), Type {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.word(type.primitiveName)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): PrimitiveType {
            return PrimitiveType(pos, type = this.type)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PrimitiveType && this.type == other.type
        }
        override fun hashCode(): Int {
            return type.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * Modifies a type to be an array type.
     */
    class ArrayType(
        pos: Position,
        type: Type,
    ) : BaseTree(pos), Type, ReferenceType {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate77
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
        override fun deepCopy(): ArrayType {
            return ArrayType(pos, type = this.type.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ArrayType && this.type == other.type
        }
        override fun hashCode(): Int {
            return type.hashCode()
        }
        init {
            this._type = updateTreeConnection(null, type)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ArrayType).type },
            )
        }
    }

    class VoidType(
        pos: Position,
    ) : BaseTree(pos), ResultType {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate78
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

    /**
     * A regular field. JLS 8.3
     */
    class FieldDeclaration(
        pos: Position,
        javadoc: JavadocComment? = null,
        anns: Iterable<Annotation> = listOf(),
        mods: FieldModifiers = FieldModifiers(pos),
        type: Type,
        variables: Iterable<VariableDeclarator>,
    ) : BaseTree(pos), ClassBodyDeclaration {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (javadoc != null) {
                    sharedCodeFormattingTemplate79
                } else {
                    sharedCodeFormattingTemplate80
                }
        override val formatElementCount
            get() = 5
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.javadoc ?: FormattableTreeGroup.empty
                1 -> FormattableTreeGroup(this.anns)
                2 -> this.mods
                3 -> this.type
                4 -> FormattableTreeGroup(this.variables)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _javadoc: JavadocComment?
        var javadoc: JavadocComment?
            get() = _javadoc
            set(newValue) { _javadoc = updateTreeConnection(_javadoc, newValue) }
        private val _anns: MutableList<Annotation> = mutableListOf()
        var anns: List<Annotation>
            get() = _anns
            set(newValue) { updateTreeConnections(_anns, newValue) }
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
        override fun deepCopy(): FieldDeclaration {
            return FieldDeclaration(pos, javadoc = this.javadoc?.deepCopy(), anns = this.anns.deepCopy(), mods = this.mods.deepCopy(), type = this.type.deepCopy(), variables = this.variables.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FieldDeclaration && this.javadoc == other.javadoc && this.anns == other.anns && this.mods == other.mods && this.type == other.type && this.variables == other.variables
        }
        override fun hashCode(): Int {
            var hc = (javadoc?.hashCode() ?: 0)
            hc = 31 * hc + anns.hashCode()
            hc = 31 * hc + mods.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + variables.hashCode()
            return hc
        }
        init {
            this._javadoc = updateTreeConnection(null, javadoc)
            updateTreeConnections(this._anns, anns)
            this._mods = updateTreeConnection(null, mods)
            this._type = updateTreeConnection(null, type)
            updateTreeConnections(this._variables, variables)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FieldDeclaration).javadoc },
                { n -> (n as FieldDeclaration).anns },
                { n -> (n as FieldDeclaration).mods },
                { n -> (n as FieldDeclaration).type },
                { n -> (n as FieldDeclaration).variables },
            )
        }
        constructor(
            pos: Position,
            javadoc: JavadocComment? = null,
            anns: Iterable<Annotation> = listOf(),
            mods: FieldModifiers = FieldModifiers(pos),
            type: Type,
            variable: Identifier,
            initializer: Expression?,
        ) : this(
            pos = pos,
            javadoc = javadoc,
            anns = anns,
            mods = mods,
            type = type,
            variables = listOf(
                VariableDeclarator(
                    pos = pos,
                    variable = variable,
                    initializer = initializer,
                ),
            ),
        )
    }

    /**
     * A regular method. See JLS 8.4
     */
    class MethodDeclaration(
        pos: Position,
        javadoc: JavadocComment? = null,
        anns: Iterable<Annotation> = listOf(),
        mods: MethodModifiers = MethodModifiers(pos),
        typeParams: TypeParameters = TypeParameters(pos),
        result: ResultType,
        name: Identifier,
        parameters: Iterable<MethodParameter>,
        exceptionTypes: Iterable<ClassType> = listOf(),
        body: BlockStatement?,
    ) : BaseTree(pos), ClassBodyDeclaration {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (javadoc != null && exceptionTypes.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate81
                } else if (javadoc != null && exceptionTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate82
                } else if (javadoc != null && body != null) {
                    sharedCodeFormattingTemplate83
                } else if (javadoc != null) {
                    sharedCodeFormattingTemplate84
                } else if (exceptionTypes.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate85
                } else if (exceptionTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate86
                } else if (body != null) {
                    sharedCodeFormattingTemplate87
                } else {
                    sharedCodeFormattingTemplate88
                }
        override val formatElementCount
            get() = 9
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.javadoc ?: FormattableTreeGroup.empty
                1 -> FormattableTreeGroup(this.anns)
                2 -> this.mods
                3 -> this.typeParams
                4 -> this.result
                5 -> this.name
                6 -> FormattableTreeGroup(this.parameters)
                7 -> FormattableTreeGroup(this.exceptionTypes)
                8 -> this.body ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _javadoc: JavadocComment?
        var javadoc: JavadocComment?
            get() = _javadoc
            set(newValue) { _javadoc = updateTreeConnection(_javadoc, newValue) }
        private val _anns: MutableList<Annotation> = mutableListOf()
        var anns: List<Annotation>
            get() = _anns
            set(newValue) { updateTreeConnections(_anns, newValue) }
        private var _mods: MethodModifiers
        var mods: MethodModifiers
            get() = _mods
            set(newValue) { _mods = updateTreeConnection(_mods, newValue) }
        private var _typeParams: TypeParameters
        var typeParams: TypeParameters
            get() = _typeParams
            set(newValue) { _typeParams = updateTreeConnection(_typeParams, newValue) }
        private var _result: ResultType
        var result: ResultType
            get() = _result
            set(newValue) { _result = updateTreeConnection(_result, newValue) }
        private var _name: Identifier
        var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private val _parameters: MutableList<MethodParameter> = mutableListOf()
        var parameters: List<MethodParameter>
            get() = _parameters
            set(newValue) { updateTreeConnections(_parameters, newValue) }
        private val _exceptionTypes: MutableList<ClassType> = mutableListOf()
        var exceptionTypes: List<ClassType>
            get() = _exceptionTypes
            set(newValue) { updateTreeConnections(_exceptionTypes, newValue) }
        private var _body: BlockStatement?
        var body: BlockStatement?
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): MethodDeclaration {
            return MethodDeclaration(pos, javadoc = this.javadoc?.deepCopy(), anns = this.anns.deepCopy(), mods = this.mods.deepCopy(), typeParams = this.typeParams.deepCopy(), result = this.result.deepCopy(), name = this.name.deepCopy(), parameters = this.parameters.deepCopy(), exceptionTypes = this.exceptionTypes.deepCopy(), body = this.body?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MethodDeclaration && this.javadoc == other.javadoc && this.anns == other.anns && this.mods == other.mods && this.typeParams == other.typeParams && this.result == other.result && this.name == other.name && this.parameters == other.parameters && this.exceptionTypes == other.exceptionTypes && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = (javadoc?.hashCode() ?: 0)
            hc = 31 * hc + anns.hashCode()
            hc = 31 * hc + mods.hashCode()
            hc = 31 * hc + typeParams.hashCode()
            hc = 31 * hc + result.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + parameters.hashCode()
            hc = 31 * hc + exceptionTypes.hashCode()
            hc = 31 * hc + (body?.hashCode() ?: 0)
            return hc
        }
        init {
            this._javadoc = updateTreeConnection(null, javadoc)
            updateTreeConnections(this._anns, anns)
            this._mods = updateTreeConnection(null, mods)
            this._typeParams = updateTreeConnection(null, typeParams)
            this._result = updateTreeConnection(null, result)
            this._name = updateTreeConnection(null, name)
            updateTreeConnections(this._parameters, parameters)
            updateTreeConnections(this._exceptionTypes, exceptionTypes)
            this._body = updateTreeConnection(null, body)
            require((mods.modAbstract == ModAbstract.Abstract || mods.modNative == ModNative.Native) == (body == null))
            require(parameters.validArity())
            require(body.regularBlock())
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as MethodDeclaration).javadoc },
                { n -> (n as MethodDeclaration).anns },
                { n -> (n as MethodDeclaration).mods },
                { n -> (n as MethodDeclaration).typeParams },
                { n -> (n as MethodDeclaration).result },
                { n -> (n as MethodDeclaration).name },
                { n -> (n as MethodDeclaration).parameters },
                { n -> (n as MethodDeclaration).exceptionTypes },
                { n -> (n as MethodDeclaration).body },
            )
        }
    }

    /**
     * A constructor. JLS 8.8
     * TODO: explicit constructor invocation, e.g. this(...) or super(...)
     * TODO: type parameters, e.g. <A, B> MyClass(A foo, B bar)
     */
    class ConstructorDeclaration(
        pos: Position,
        mods: ConstructorModifiers = ConstructorModifiers(pos),
        name: Identifier,
        parameters: Iterable<MethodParameter>,
        exceptionTypes: Iterable<Type> = listOf(),
        body: BlockStatement,
    ) : BaseTree(pos), ClassBodyDeclaration {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (exceptionTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate89
                } else {
                    sharedCodeFormattingTemplate90
                }
        override val formatElementCount
            get() = 5
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.mods
                1 -> this.name
                2 -> FormattableTreeGroup(this.parameters)
                3 -> FormattableTreeGroup(this.exceptionTypes)
                4 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _mods: ConstructorModifiers
        var mods: ConstructorModifiers
            get() = _mods
            set(newValue) { _mods = updateTreeConnection(_mods, newValue) }
        private var _name: Identifier
        var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private val _parameters: MutableList<MethodParameter> = mutableListOf()
        var parameters: List<MethodParameter>
            get() = _parameters
            set(newValue) { updateTreeConnections(_parameters, newValue) }
        private val _exceptionTypes: MutableList<Type> = mutableListOf()
        var exceptionTypes: List<Type>
            get() = _exceptionTypes
            set(newValue) { updateTreeConnections(_exceptionTypes, newValue) }
        private var _body: BlockStatement
        var body: BlockStatement
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): ConstructorDeclaration {
            return ConstructorDeclaration(pos, mods = this.mods.deepCopy(), name = this.name.deepCopy(), parameters = this.parameters.deepCopy(), exceptionTypes = this.exceptionTypes.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ConstructorDeclaration && this.mods == other.mods && this.name == other.name && this.parameters == other.parameters && this.exceptionTypes == other.exceptionTypes && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = mods.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + parameters.hashCode()
            hc = 31 * hc + exceptionTypes.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._mods = updateTreeConnection(null, mods)
            this._name = updateTreeConnection(null, name)
            updateTreeConnections(this._parameters, parameters)
            updateTreeConnections(this._exceptionTypes, exceptionTypes)
            this._body = updateTreeConnection(null, body)
            require(parameters.validArity())
            require(body.constructorBlock())
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ConstructorDeclaration).mods },
                { n -> (n as ConstructorDeclaration).name },
                { n -> (n as ConstructorDeclaration).parameters },
                { n -> (n as ConstructorDeclaration).exceptionTypes },
                { n -> (n as ConstructorDeclaration).body },
            )
        }
    }

    /**
     * Includes both instance initializer (a bare { } block) and a static initializer.
     * See JS 8.6 and 8.7
     */
    class Initializer(
        pos: Position,
        body: BlockStatement,
        var modStatic: ModStatic = ModStatic.Dynamic,
    ) : BaseTree(pos), ClassBodyDeclaration {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (modStatic == ModStatic.Static) {
                    sharedCodeFormattingTemplate91
                } else {
                    sharedCodeFormattingTemplate69
                }
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
        private var _body: BlockStatement
        var body: BlockStatement
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): Initializer {
            return Initializer(pos, body = this.body.deepCopy(), modStatic = this.modStatic)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Initializer && this.body == other.body && this.modStatic == other.modStatic
        }
        override fun hashCode(): Int {
            var hc = body.hashCode()
            hc = 31 * hc + modStatic.hashCode()
            return hc
        }
        init {
            this._body = updateTreeConnection(null, body)
            require(body.regularBlock())
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Initializer).body },
            )
        }
    }

    /**
     * An interface field, which is really a constant. JLS 9.3
     * Note: fields on an interface don't need any modifiers.
     */
    class InterfaceFieldDeclaration(
        pos: Position,
        javadoc: JavadocComment? = null,
        anns: Iterable<Annotation> = listOf(),
        type: Type,
        variables: Iterable<VariableDeclarator>,
    ) : BaseTree(pos), InterfaceBodyDeclaration {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (javadoc != null) {
                    sharedCodeFormattingTemplate92
                } else {
                    sharedCodeFormattingTemplate93
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.javadoc ?: FormattableTreeGroup.empty
                1 -> FormattableTreeGroup(this.anns)
                2 -> this.type
                3 -> FormattableTreeGroup(this.variables)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _javadoc: JavadocComment?
        var javadoc: JavadocComment?
            get() = _javadoc
            set(newValue) { _javadoc = updateTreeConnection(_javadoc, newValue) }
        private val _anns: MutableList<Annotation> = mutableListOf()
        var anns: List<Annotation>
            get() = _anns
            set(newValue) { updateTreeConnections(_anns, newValue) }
        private var _type: Type
        var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private val _variables: MutableList<VariableDeclarator> = mutableListOf()
        var variables: List<VariableDeclarator>
            get() = _variables
            set(newValue) { updateTreeConnections(_variables, newValue) }
        override fun deepCopy(): InterfaceFieldDeclaration {
            return InterfaceFieldDeclaration(pos, javadoc = this.javadoc?.deepCopy(), anns = this.anns.deepCopy(), type = this.type.deepCopy(), variables = this.variables.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is InterfaceFieldDeclaration && this.javadoc == other.javadoc && this.anns == other.anns && this.type == other.type && this.variables == other.variables
        }
        override fun hashCode(): Int {
            var hc = (javadoc?.hashCode() ?: 0)
            hc = 31 * hc + anns.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + variables.hashCode()
            return hc
        }
        init {
            this._javadoc = updateTreeConnection(null, javadoc)
            updateTreeConnections(this._anns, anns)
            this._type = updateTreeConnection(null, type)
            updateTreeConnections(this._variables, variables)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as InterfaceFieldDeclaration).javadoc },
                { n -> (n as InterfaceFieldDeclaration).anns },
                { n -> (n as InterfaceFieldDeclaration).type },
                { n -> (n as InterfaceFieldDeclaration).variables },
            )
        }
    }

    /**
     * A regular method. See JLS 8.4
     */
    class InterfaceMethodDeclaration(
        pos: Position,
        javadoc: JavadocComment? = null,
        anns: Iterable<Annotation> = listOf(),
        typeParams: TypeParameters = TypeParameters(pos),
        result: ResultType,
        name: Identifier,
        parameters: Iterable<MethodParameter>,
        exceptionTypes: Iterable<ClassType> = listOf(),
        body: BlockStatement?,
        var mods: ModInterfaceMethod = ModInterfaceMethod.Abstract,
    ) : BaseTree(pos), InterfaceBodyDeclaration {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (javadoc != null && mods == ModInterfaceMethod.Default && exceptionTypes.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate94
                } else if (javadoc != null && mods == ModInterfaceMethod.Default && exceptionTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate95
                } else if (javadoc != null && mods == ModInterfaceMethod.Default && body != null) {
                    sharedCodeFormattingTemplate96
                } else if (javadoc != null && mods == ModInterfaceMethod.Default) {
                    sharedCodeFormattingTemplate97
                } else if (javadoc != null && mods == ModInterfaceMethod.Private && exceptionTypes.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate98
                } else if (javadoc != null && mods == ModInterfaceMethod.Private && exceptionTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate99
                } else if (javadoc != null && mods == ModInterfaceMethod.Private && body != null) {
                    sharedCodeFormattingTemplate100
                } else if (javadoc != null && mods == ModInterfaceMethod.Private) {
                    sharedCodeFormattingTemplate101
                } else if (javadoc != null && mods == ModInterfaceMethod.Static && exceptionTypes.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate102
                } else if (javadoc != null && mods == ModInterfaceMethod.Static && exceptionTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate103
                } else if (javadoc != null && mods == ModInterfaceMethod.Static && body != null) {
                    sharedCodeFormattingTemplate104
                } else if (javadoc != null && mods == ModInterfaceMethod.Static) {
                    sharedCodeFormattingTemplate105
                } else if (javadoc != null && mods == ModInterfaceMethod.PrivateStatic && exceptionTypes.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate106
                } else if (javadoc != null && mods == ModInterfaceMethod.PrivateStatic && exceptionTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate107
                } else if (javadoc != null && mods == ModInterfaceMethod.PrivateStatic && body != null) {
                    sharedCodeFormattingTemplate108
                } else if (javadoc != null && mods == ModInterfaceMethod.PrivateStatic) {
                    sharedCodeFormattingTemplate109
                } else if (javadoc != null && exceptionTypes.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate110
                } else if (javadoc != null && exceptionTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate111
                } else if (javadoc != null && body != null) {
                    sharedCodeFormattingTemplate112
                } else if (javadoc != null) {
                    sharedCodeFormattingTemplate113
                } else if (mods == ModInterfaceMethod.Default && exceptionTypes.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate114
                } else if (mods == ModInterfaceMethod.Default && exceptionTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate115
                } else if (mods == ModInterfaceMethod.Default && body != null) {
                    sharedCodeFormattingTemplate116
                } else if (mods == ModInterfaceMethod.Default) {
                    sharedCodeFormattingTemplate117
                } else if (mods == ModInterfaceMethod.Private && exceptionTypes.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate118
                } else if (mods == ModInterfaceMethod.Private && exceptionTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate119
                } else if (mods == ModInterfaceMethod.Private && body != null) {
                    sharedCodeFormattingTemplate120
                } else if (mods == ModInterfaceMethod.Private) {
                    sharedCodeFormattingTemplate121
                } else if (mods == ModInterfaceMethod.Static && exceptionTypes.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate122
                } else if (mods == ModInterfaceMethod.Static && exceptionTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate123
                } else if (mods == ModInterfaceMethod.Static && body != null) {
                    sharedCodeFormattingTemplate124
                } else if (mods == ModInterfaceMethod.Static) {
                    sharedCodeFormattingTemplate125
                } else if (mods == ModInterfaceMethod.PrivateStatic && exceptionTypes.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate126
                } else if (mods == ModInterfaceMethod.PrivateStatic && exceptionTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate127
                } else if (mods == ModInterfaceMethod.PrivateStatic && body != null) {
                    sharedCodeFormattingTemplate128
                } else if (mods == ModInterfaceMethod.PrivateStatic) {
                    sharedCodeFormattingTemplate129
                } else if (exceptionTypes.isNotEmpty() && body != null) {
                    sharedCodeFormattingTemplate130
                } else if (exceptionTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate131
                } else if (body != null) {
                    sharedCodeFormattingTemplate132
                } else {
                    sharedCodeFormattingTemplate133
                }
        override val formatElementCount
            get() = 8
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.javadoc ?: FormattableTreeGroup.empty
                1 -> FormattableTreeGroup(this.anns)
                2 -> this.typeParams
                3 -> this.result
                4 -> this.name
                5 -> FormattableTreeGroup(this.parameters)
                6 -> FormattableTreeGroup(this.exceptionTypes)
                7 -> this.body ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _javadoc: JavadocComment?
        var javadoc: JavadocComment?
            get() = _javadoc
            set(newValue) { _javadoc = updateTreeConnection(_javadoc, newValue) }
        private val _anns: MutableList<Annotation> = mutableListOf()
        var anns: List<Annotation>
            get() = _anns
            set(newValue) { updateTreeConnections(_anns, newValue) }
        private var _typeParams: TypeParameters
        var typeParams: TypeParameters
            get() = _typeParams
            set(newValue) { _typeParams = updateTreeConnection(_typeParams, newValue) }
        private var _result: ResultType
        var result: ResultType
            get() = _result
            set(newValue) { _result = updateTreeConnection(_result, newValue) }
        private var _name: Identifier
        var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private val _parameters: MutableList<MethodParameter> = mutableListOf()
        var parameters: List<MethodParameter>
            get() = _parameters
            set(newValue) { updateTreeConnections(_parameters, newValue) }
        private val _exceptionTypes: MutableList<ClassType> = mutableListOf()
        var exceptionTypes: List<ClassType>
            get() = _exceptionTypes
            set(newValue) { updateTreeConnections(_exceptionTypes, newValue) }
        private var _body: BlockStatement?
        var body: BlockStatement?
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): InterfaceMethodDeclaration {
            return InterfaceMethodDeclaration(pos, javadoc = this.javadoc?.deepCopy(), anns = this.anns.deepCopy(), typeParams = this.typeParams.deepCopy(), result = this.result.deepCopy(), name = this.name.deepCopy(), parameters = this.parameters.deepCopy(), exceptionTypes = this.exceptionTypes.deepCopy(), body = this.body?.deepCopy(), mods = this.mods)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is InterfaceMethodDeclaration && this.javadoc == other.javadoc && this.anns == other.anns && this.typeParams == other.typeParams && this.result == other.result && this.name == other.name && this.parameters == other.parameters && this.exceptionTypes == other.exceptionTypes && this.body == other.body && this.mods == other.mods
        }
        override fun hashCode(): Int {
            var hc = (javadoc?.hashCode() ?: 0)
            hc = 31 * hc + anns.hashCode()
            hc = 31 * hc + typeParams.hashCode()
            hc = 31 * hc + result.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + parameters.hashCode()
            hc = 31 * hc + exceptionTypes.hashCode()
            hc = 31 * hc + (body?.hashCode() ?: 0)
            hc = 31 * hc + mods.hashCode()
            return hc
        }
        init {
            this._javadoc = updateTreeConnection(null, javadoc)
            updateTreeConnections(this._anns, anns)
            this._typeParams = updateTreeConnection(null, typeParams)
            this._result = updateTreeConnection(null, result)
            this._name = updateTreeConnection(null, name)
            updateTreeConnections(this._parameters, parameters)
            updateTreeConnections(this._exceptionTypes, exceptionTypes)
            this._body = updateTreeConnection(null, body)
            require((mods == ModInterfaceMethod.Abstract) == (body == null))
            require(parameters.validArity())
            require(body.regularBlock())
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as InterfaceMethodDeclaration).javadoc },
                { n -> (n as InterfaceMethodDeclaration).anns },
                { n -> (n as InterfaceMethodDeclaration).typeParams },
                { n -> (n as InterfaceMethodDeclaration).result },
                { n -> (n as InterfaceMethodDeclaration).name },
                { n -> (n as InterfaceMethodDeclaration).parameters },
                { n -> (n as InterfaceMethodDeclaration).exceptionTypes },
                { n -> (n as InterfaceMethodDeclaration).body },
            )
        }
    }

    /**
     * Annotations and various keywords applicable to fields. JLS 8.3.1
     */
    class FieldModifiers(
        pos: Position,
        var modAccess: ModAccess = ModAccess.PackagePrivate,
        var modStatic: ModStatic = ModStatic.Dynamic,
        var modFinal: ModFinal = ModFinal.Open,
        var modTransient: ModTransient = ModTransient.Persistent,
        var modVolatile: ModVolatile = ModVolatile.Stable,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            modAccess.emit(tokenSink)
            modStatic.emit(tokenSink)
            modFinal.emit(tokenSink)
            modTransient.emit(tokenSink)
            modVolatile.emit(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): FieldModifiers {
            return FieldModifiers(pos, modAccess = this.modAccess, modStatic = this.modStatic, modFinal = this.modFinal, modTransient = this.modTransient, modVolatile = this.modVolatile)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FieldModifiers && this.modAccess == other.modAccess && this.modStatic == other.modStatic && this.modFinal == other.modFinal && this.modTransient == other.modTransient && this.modVolatile == other.modVolatile
        }
        override fun hashCode(): Int {
            var hc = modAccess.hashCode()
            hc = 31 * hc + modStatic.hashCode()
            hc = 31 * hc + modFinal.hashCode()
            hc = 31 * hc + modTransient.hashCode()
            hc = 31 * hc + modVolatile.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * Declares a field or local variable; Java allows multiple assignments of the same type.
     * This production is consumed by e.g. FieldDeclaration.
     * JLS 8.3 TODO: support arrays
     */
    class VariableDeclarator(
        pos: Position,
        variable: Identifier,
        initializer: Expression?,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (initializer != null) {
                    sharedCodeFormattingTemplate134
                } else {
                    sharedCodeFormattingTemplate69
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

    sealed interface LambdaBody : Tree {
        override fun deepCopy(): LambdaBody
    }

    /**
     * The JLS breaks out expression nodes into a precedence tree; this grammar collapses that tree.
     * See JLS 15 generally. Rules for parentheses are in 15.8.5
     */
    sealed interface Expression : Tree, LambdaBody {
        override fun deepCopy(): Expression
    }

    /**
     * General statements. JLS 14.5
     *
     * This grammar omits:
     * - all the productions related to dropping braces.
     *
     * Includes AlternateConstructorInvocation, which deviates from JLS.
     */
    sealed interface Statement : Tree, BlockLevelStatement {
        override fun deepCopy(): Statement
    }

    /**
     * Rather than add complexity to the IfStatement, enforce clean if-else-if chains by restricting the contents
     * of the alternate.
     */
    sealed interface ElseBlockStatement : Tree {
        override fun deepCopy(): ElseBlockStatement
    }

    /**
     * A curly braces block of statements. "Block" in JLS 14.2
     */
    class BlockStatement(
        pos: Position,
        body: Iterable<BlockLevelStatement>,
    ) : BaseTree(pos), Statement, ElseBlockStatement, LambdaBody {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate135
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
        private val _body: MutableList<BlockLevelStatement> = mutableListOf()
        var body: List<BlockLevelStatement>
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
    }

    /**
     * Annotations and various keywords applicable to methods. JLS 8.4.3
     */
    class MethodModifiers(
        pos: Position,
        var modAccess: ModAccess = ModAccess.PackagePrivate,
        var modAbstract: ModAbstract = ModAbstract.Concrete,
        var modStatic: ModStatic = ModStatic.Dynamic,
        var modFinal: ModFinal = ModFinal.Open,
        var modSynchronized: ModSynchronized = ModSynchronized.Unsynchronized,
        var modNative: ModNative = ModNative.Java,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            modAccess.emit(tokenSink)
            modAbstract.emit(tokenSink)
            modStatic.emit(tokenSink)
            modFinal.emit(tokenSink)
            modSynchronized.emit(tokenSink)
            modNative.emit(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): MethodModifiers {
            return MethodModifiers(pos, modAccess = this.modAccess, modAbstract = this.modAbstract, modStatic = this.modStatic, modFinal = this.modFinal, modSynchronized = this.modSynchronized, modNative = this.modNative)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MethodModifiers && this.modAccess == other.modAccess && this.modAbstract == other.modAbstract && this.modStatic == other.modStatic && this.modFinal == other.modFinal && this.modSynchronized == other.modSynchronized && this.modNative == other.modNative
        }
        override fun hashCode(): Int {
            var hc = modAccess.hashCode()
            hc = 31 * hc + modAbstract.hashCode()
            hc = 31 * hc + modStatic.hashCode()
            hc = 31 * hc + modFinal.hashCode()
            hc = 31 * hc + modSynchronized.hashCode()
            hc = 31 * hc + modNative.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    sealed interface MethodParameter : Tree {
        val mods: VariableModifiers
        val type: Type
        val name: Identifier
        override fun deepCopy(): MethodParameter
    }

    /**
     * Annotations and various keywords applicable to constructors. JLS 8.8.3
     */
    class ConstructorModifiers(
        pos: Position,
        anns: Iterable<Annotation> = listOf(),
        var modAccess: ModAccess = ModAccess.PackagePrivate,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (modAccess == ModAccess.Public) {
                    sharedCodeFormattingTemplate136
                } else if (modAccess == ModAccess.Protected) {
                    sharedCodeFormattingTemplate137
                } else if (modAccess == ModAccess.Private) {
                    sharedCodeFormattingTemplate138
                } else {
                    sharedCodeFormattingTemplate139
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.anns)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _anns: MutableList<Annotation> = mutableListOf()
        var anns: List<Annotation>
            get() = _anns
            set(newValue) { updateTreeConnections(_anns, newValue) }
        override fun deepCopy(): ConstructorModifiers {
            return ConstructorModifiers(pos, anns = this.anns.deepCopy(), modAccess = this.modAccess)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ConstructorModifiers && this.anns == other.anns && this.modAccess == other.modAccess
        }
        override fun hashCode(): Int {
            var hc = anns.hashCode()
            hc = 31 * hc + modAccess.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._anns, anns)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ConstructorModifiers).anns },
            )
        }
    }

    /**
     * A formal parameter to a method. JLS 8.4.1
     */
    class FormalParameter(
        pos: Position,
        mods: VariableModifiers = VariableModifiers(pos),
        type: Type,
        name: Identifier,
    ) : BaseTree(pos), MethodParameter {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate140
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.mods
                1 -> this.type
                2 -> this.name
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _mods: VariableModifiers
        override var mods: VariableModifiers
            get() = _mods
            set(newValue) { _mods = updateTreeConnection(_mods, newValue) }
        private var _type: Type
        override var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _name: Identifier
        override var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override fun deepCopy(): FormalParameter {
            return FormalParameter(pos, mods = this.mods.deepCopy(), type = this.type.deepCopy(), name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FormalParameter && this.mods == other.mods && this.type == other.type && this.name == other.name
        }
        override fun hashCode(): Int {
            var hc = mods.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + name.hashCode()
            return hc
        }
        init {
            this._mods = updateTreeConnection(null, mods)
            this._type = updateTreeConnection(null, type)
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FormalParameter).mods },
                { n -> (n as FormalParameter).type },
                { n -> (n as FormalParameter).name },
            )
        }
    }

    /**
     * A variable arity formal parameter to a method. The effective type is an array of `type`. JLS 8.4.1
     */
    class VariableArityParameter(
        pos: Position,
        mods: VariableModifiers = VariableModifiers(pos),
        type: Type,
        name: Identifier,
    ) : BaseTree(pos), MethodParameter {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate141
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.mods
                1 -> this.type
                2 -> this.name
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _mods: VariableModifiers
        override var mods: VariableModifiers
            get() = _mods
            set(newValue) { _mods = updateTreeConnection(_mods, newValue) }
        private var _type: Type
        override var type: Type
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _name: Identifier
        override var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override fun deepCopy(): VariableArityParameter {
            return VariableArityParameter(pos, mods = this.mods.deepCopy(), type = this.type.deepCopy(), name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is VariableArityParameter && this.mods == other.mods && this.type == other.type && this.name == other.name
        }
        override fun hashCode(): Int {
            var hc = mods.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + name.hashCode()
            return hc
        }
        init {
            this._mods = updateTreeConnection(null, mods)
            this._type = updateTreeConnection(null, type)
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as VariableArityParameter).mods },
                { n -> (n as VariableArityParameter).type },
                { n -> (n as VariableArityParameter).name },
            )
        }
    }

    class VariableModifiers(
        pos: Position,
        anns: Iterable<Annotation> = listOf(),
        var modFinal: ModFinal = ModFinal.Open,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (modFinal == ModFinal.Final) {
                    sharedCodeFormattingTemplate142
                } else {
                    sharedCodeFormattingTemplate139
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.anns)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _anns: MutableList<Annotation> = mutableListOf()
        var anns: List<Annotation>
            get() = _anns
            set(newValue) { updateTreeConnections(_anns, newValue) }
        override fun deepCopy(): VariableModifiers {
            return VariableModifiers(pos, anns = this.anns.deepCopy(), modFinal = this.modFinal)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is VariableModifiers && this.anns == other.anns && this.modFinal == other.modFinal
        }
        override fun hashCode(): Int {
            var hc = anns.hashCode()
            hc = 31 * hc + modFinal.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._anns, anns)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as VariableModifiers).anns },
            )
        }
    }

    /** An annotation parameter takes a list of named parameters. JLS 9.7 */
    class AnnotationParam(
        pos: Position,
        name: Identifier,
        value: AnnotationExpr,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate134
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name
                1 -> this.value
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _name: Identifier
        var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _value: AnnotationExpr
        var value: AnnotationExpr
            get() = _value
            set(newValue) { _value = updateTreeConnection(_value, newValue) }
        override fun deepCopy(): AnnotationParam {
            return AnnotationParam(pos, name = this.name.deepCopy(), value = this.value.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AnnotationParam && this.name == other.name && this.value == other.value
        }
        override fun hashCode(): Int {
            var hc = name.hashCode()
            hc = 31 * hc + value.hashCode()
            return hc
        }
        init {
            this._name = updateTreeConnection(null, name)
            this._value = updateTreeConnection(null, value)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AnnotationParam).name },
                { n -> (n as AnnotationParam).value },
            )
        }
    }

    /**
     * Annotation values are "constant" expressions. JLS 9.7.1
     *
     * This presents a subset of possible annotation expressions, under the assumption that we will not be using complex
     * annotations that are determined by `javac`.
     */
    sealed interface AnnotationExpr : Tree {
        override fun deepCopy(): AnnotationExpr
    }

    /** Arrays in annotations have a shorthand syntax. */
    class AnnotationArrayExpr(
        pos: Position,
        elems: Iterable<AnnotationExpr>,
    ) : BaseTree(pos), AnnotationExpr {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate143
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.elems)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _elems: MutableList<AnnotationExpr> = mutableListOf()
        var elems: List<AnnotationExpr>
            get() = _elems
            set(newValue) { updateTreeConnections(_elems, newValue) }
        override fun deepCopy(): AnnotationArrayExpr {
            return AnnotationArrayExpr(pos, elems = this.elems.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AnnotationArrayExpr && this.elems == other.elems
        }
        override fun hashCode(): Int {
            return elems.hashCode()
        }
        init {
            updateTreeConnections(this._elems, elems)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AnnotationArrayExpr).elems },
            )
        }
    }

    /**
     * Java literals. JLS 15.8.2
     */
    sealed interface LiteralExpr : Tree, AnnotationExpr, Expression {
        override val operatorDefinition
            get() = JavaOperatorDefinition.Atom
        override fun deepCopy(): LiteralExpr
    }

    /**
     * The valid forms of try(resources) {}:
     * 1. more common `try(FileReader reader = new FileReader("some-file.txt")) {}`
     * 2. less common `try(reader) {}` that will close an already opened stream.
     * 3. less common `try(foo.field) {}`
     */
    sealed interface ResourceSpecification : Tree {
        override fun deepCopy(): ResourceSpecification
    }

    /**
     * The target of an assignment may be a name, or an accessor.
     * JLS 15.26 TODO add array access
     */
    sealed interface LeftHandSide : Tree {
        override fun deepCopy(): LeftHandSide
    }

    /**
     * An expression name is a dotted identifier.
     * JLS 15.14.1
     */
    class NameExpr(
        pos: Position,
        ident: Iterable<Identifier>,
    ) : BaseTree(pos), AnnotationExpr, ResourceSpecification, Expression, LeftHandSide {
        override val operatorDefinition
            get() = JavaOperatorDefinition.Atom
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate9
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.ident)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _ident: MutableList<Identifier> = mutableListOf()
        var ident: List<Identifier>
            get() = _ident
            set(newValue) { updateTreeConnections(_ident, newValue) }
        override fun deepCopy(): NameExpr {
            return NameExpr(pos, ident = this.ident.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is NameExpr && this.ident == other.ident
        }
        override fun hashCode(): Int {
            return ident.hashCode()
        }
        init {
            updateTreeConnections(this._ident, ident)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as NameExpr).ident },
            )
        }
        constructor(ident: Identifier) : this(ident.pos, listOf(ident))
    }

    /**
     * Declares local variables and optionally assigns an initial value. JLS 14.4
     * TODO: group multiple declarations and initializations.
     */
    class LocalVariableDeclaration(
        pos: Position,
        mods: VariableModifiers = VariableModifiers(pos),
        type: Type?,
        name: Identifier,
        expr: Expression?,
    ) : BaseTree(pos), BlockLevelStatement, ResourceSpecification {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (type != null && expr != null) {
                    sharedCodeFormattingTemplate144
                } else if (type != null) {
                    sharedCodeFormattingTemplate145
                } else if (expr != null) {
                    sharedCodeFormattingTemplate146
                } else {
                    sharedCodeFormattingTemplate147
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.mods
                1 -> this.type ?: FormattableTreeGroup.empty
                2 -> this.name
                3 -> this.expr ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _mods: VariableModifiers
        var mods: VariableModifiers
            get() = _mods
            set(newValue) { _mods = updateTreeConnection(_mods, newValue) }
        private var _type: Type?
        var type: Type?
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _name: Identifier
        var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _expr: Expression?
        var expr: Expression?
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): LocalVariableDeclaration {
            return LocalVariableDeclaration(pos, mods = this.mods.deepCopy(), type = this.type?.deepCopy(), name = this.name.deepCopy(), expr = this.expr?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LocalVariableDeclaration && this.mods == other.mods && this.type == other.type && this.name == other.name && this.expr == other.expr
        }
        override fun hashCode(): Int {
            var hc = mods.hashCode()
            hc = 31 * hc + (type?.hashCode() ?: 0)
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + (expr?.hashCode() ?: 0)
            return hc
        }
        init {
            this._mods = updateTreeConnection(null, mods)
            this._type = updateTreeConnection(null, type)
            this._name = updateTreeConnection(null, name)
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LocalVariableDeclaration).mods },
                { n -> (n as LocalVariableDeclaration).type },
                { n -> (n as LocalVariableDeclaration).name },
                { n -> (n as LocalVariableDeclaration).expr },
            )
        }
    }

    /**
     * The empty statement. JLS 14.6
     */
    class EmptyStatement(
        pos: Position,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate148
        override val formatElementCount
            get() = 0
        override fun deepCopy(): EmptyStatement {
            return EmptyStatement(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is EmptyStatement
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * Labeled statement. Not reconstructing short if blocks. JLS 14.7
     */
    class LabeledStatement(
        pos: Position,
        label: Identifier,
        stmt: Statement,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate149
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
        private var _label: Identifier
        var label: Identifier
            get() = _label
            set(newValue) { _label = updateTreeConnection(_label, newValue) }
        private var _stmt: Statement
        var stmt: Statement
            get() = _stmt
            set(newValue) { _stmt = updateTreeConnection(_stmt, newValue) }
        override fun deepCopy(): LabeledStatement {
            return LabeledStatement(pos, label = this.label.deepCopy(), stmt = this.stmt.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LabeledStatement && this.label == other.label && this.stmt == other.stmt
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
                { n -> (n as LabeledStatement).label },
                { n -> (n as LabeledStatement).stmt },
            )
        }
    }

    /**
     * Embed an expression as a statement, usually a void method call for effect. JLS 14.8
     *
     * This node prohibits expressions that should be statements.
     * TODO the JLS restricts this to specific expressions; see if this is an issue
     */
    class ExpressionStatement(
        pos: Position,
        expr: ExpressionStatementExpr,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate150
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
        private var _expr: ExpressionStatementExpr
        var expr: ExpressionStatementExpr
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
        constructor(expr: ExpressionStatementExpr) : this(expr.pos, expr)
    }

    /**
     * This production represents all the variants of if-then-else in JLS 14.9
     */
    class IfStatement(
        pos: Position,
        test: Expression,
        consequent: BlockStatement,
        alternate: ElseBlockStatement? = null,
    ) : BaseTree(pos), Statement, ElseBlockStatement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (elsePresent) {
                    sharedCodeFormattingTemplate151
                } else {
                    sharedCodeFormattingTemplate152
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
        private var _consequent: BlockStatement
        var consequent: BlockStatement
            get() = _consequent
            set(newValue) { _consequent = updateTreeConnection(_consequent, newValue) }
        private var _alternate: ElseBlockStatement?
        var alternate: ElseBlockStatement?
            get() = _alternate
            set(newValue) { _alternate = updateTreeConnection(_alternate, newValue) }
        val elsePresent: Boolean
            get() = alternate != null
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
            require(consequent.regularBlock())
            require(
                when (val a = alternate) {
                    null, is IfStatement -> true
                    is BlockStatement -> a.regularBlock()
                },
            )
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as IfStatement).test },
                { n -> (n as IfStatement).consequent },
                { n -> (n as IfStatement).alternate },
            )
        }
    }

    /**
     * An assertion that is enabled _only_ when the application is invoked with `-ea`.
     * Generally, tests should prefer e.g. `assertEquals()` methods provided by test frameworks.
     * To check at runtime: `bool enabled = false; assert (enabled = true);`
     * JLS 14.10
     */
    class AssertStatement(
        pos: Position,
        test: Expression,
        msg: Expression? = null,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (msg != null) {
                    sharedCodeFormattingTemplate153
                } else {
                    sharedCodeFormattingTemplate154
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.test
                1 -> this.msg ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _test: Expression
        var test: Expression
            get() = _test
            set(newValue) { _test = updateTreeConnection(_test, newValue) }
        private var _msg: Expression?
        var msg: Expression?
            get() = _msg
            set(newValue) { _msg = updateTreeConnection(_msg, newValue) }
        override fun deepCopy(): AssertStatement {
            return AssertStatement(pos, test = this.test.deepCopy(), msg = this.msg?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AssertStatement && this.test == other.test && this.msg == other.msg
        }
        override fun hashCode(): Int {
            var hc = test.hashCode()
            hc = 31 * hc + (msg?.hashCode() ?: 0)
            return hc
        }
        init {
            this._test = updateTreeConnection(null, test)
            this._msg = updateTreeConnection(null, msg)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AssertStatement).test },
                { n -> (n as AssertStatement).msg },
            )
        }
    }

    /**
     * Switch statements. JLS 14.11
     */
    class SwitchStatement(
        pos: Position,
        selector: Expression,
        block: SwitchBlock,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate155
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.selector
                1 -> this.block
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _selector: Expression
        var selector: Expression
            get() = _selector
            set(newValue) { _selector = updateTreeConnection(_selector, newValue) }
        private var _block: SwitchBlock
        var block: SwitchBlock
            get() = _block
            set(newValue) { _block = updateTreeConnection(_block, newValue) }
        override fun deepCopy(): SwitchStatement {
            return SwitchStatement(pos, selector = this.selector.deepCopy(), block = this.block.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SwitchStatement && this.selector == other.selector && this.block == other.block
        }
        override fun hashCode(): Int {
            var hc = selector.hashCode()
            hc = 31 * hc + block.hashCode()
            return hc
        }
        init {
            this._selector = updateTreeConnection(null, selector)
            this._block = updateTreeConnection(null, block)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SwitchStatement).selector },
                { n -> (n as SwitchStatement).block },
            )
        }
    }

    /**
     * This production represents all the variants of while; JLS 14.12
     */
    class WhileStatement(
        pos: Position,
        test: Expression,
        body: BlockStatement,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate156
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
            require(body.regularBlock())
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as WhileStatement).test },
                { n -> (n as WhileStatement).body },
            )
        }
    }

    /**
     * This production represents the do-while; JLS 14.13
     */
    class DoStatement(
        pos: Position,
        body: BlockStatement,
        test: Expression,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate157
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.body
                1 -> this.test
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _body: BlockStatement
        var body: BlockStatement
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        private var _test: Expression
        var test: Expression
            get() = _test
            set(newValue) { _test = updateTreeConnection(_test, newValue) }
        override fun deepCopy(): DoStatement {
            return DoStatement(pos, body = this.body.deepCopy(), test = this.test.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is DoStatement && this.body == other.body && this.test == other.test
        }
        override fun hashCode(): Int {
            var hc = body.hashCode()
            hc = 31 * hc + test.hashCode()
            return hc
        }
        init {
            this._body = updateTreeConnection(null, body)
            this._test = updateTreeConnection(null, test)
            require(body.regularBlock())
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as DoStatement).body },
                { n -> (n as DoStatement).test },
            )
        }
    }

    /**
     * Breaks either break out of an enclosing loop, switch or to a labeled statement. JLS 14.15
     */
    class BreakStatement(
        pos: Position,
        target: Identifier? = null,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (target != null) {
                    sharedCodeFormattingTemplate158
                } else {
                    sharedCodeFormattingTemplate159
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.target ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _target: Identifier?
        var target: Identifier?
            get() = _target
            set(newValue) { _target = updateTreeConnection(_target, newValue) }
        override fun deepCopy(): BreakStatement {
            return BreakStatement(pos, target = this.target?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BreakStatement && this.target == other.target
        }
        override fun hashCode(): Int {
            return (target?.hashCode() ?: 0)
        }
        init {
            this._target = updateTreeConnection(null, target)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as BreakStatement).target },
            )
        }
    }

    /**
     * Continues transfer control to the enclosing loop or to a labeled loop. JLS 14.16
     */
    class ContinueStatement(
        pos: Position,
        target: Identifier? = null,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (target != null) {
                    sharedCodeFormattingTemplate160
                } else {
                    sharedCodeFormattingTemplate161
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.target ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _target: Identifier?
        var target: Identifier?
            get() = _target
            set(newValue) { _target = updateTreeConnection(_target, newValue) }
        override fun deepCopy(): ContinueStatement {
            return ContinueStatement(pos, target = this.target?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ContinueStatement && this.target == other.target
        }
        override fun hashCode(): Int {
            return (target?.hashCode() ?: 0)
        }
        init {
            this._target = updateTreeConnection(null, target)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ContinueStatement).target },
            )
        }
    }

    /**
     * Return transfers control to the invoker of the method. JLS 14.17
     */
    class ReturnStatement(
        pos: Position,
        expr: Expression? = null,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (expr != null) {
                    sharedCodeFormattingTemplate162
                } else {
                    sharedCodeFormattingTemplate163
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

    /**
     * Cause an exception to be thrown. JLS 14.18
     */
    class ThrowStatement(
        pos: Position,
        expr: Expression,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate164
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
        override fun deepCopy(): ThrowStatement {
            return ThrowStatement(pos, expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ThrowStatement && this.expr == other.expr
        }
        override fun hashCode(): Int {
            return expr.hashCode()
        }
        init {
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ThrowStatement).expr },
            )
        }
    }

    /**
     * Combines all of try with resources, catch and finally blocks. JLS 14.20
     */
    class TryStatement(
        pos: Position,
        resources: Iterable<ResourceSpecification> = listOf(),
        bodyBlock: BlockStatement,
        catchBlocks: Iterable<CatchBlock>,
        finallyBlock: BlockStatement? = null,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (resources.isNotEmpty() && finallyBlock != null) {
                    sharedCodeFormattingTemplate165
                } else if (resources.isNotEmpty()) {
                    sharedCodeFormattingTemplate166
                } else if (finallyBlock != null) {
                    sharedCodeFormattingTemplate167
                } else {
                    sharedCodeFormattingTemplate168
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.resources)
                1 -> this.bodyBlock
                2 -> FormattableTreeGroup(this.catchBlocks)
                3 -> this.finallyBlock ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _resources: MutableList<ResourceSpecification> = mutableListOf()
        var resources: List<ResourceSpecification>
            get() = _resources
            set(newValue) { updateTreeConnections(_resources, newValue) }
        private var _bodyBlock: BlockStatement
        var bodyBlock: BlockStatement
            get() = _bodyBlock
            set(newValue) { _bodyBlock = updateTreeConnection(_bodyBlock, newValue) }
        private val _catchBlocks: MutableList<CatchBlock> = mutableListOf()
        var catchBlocks: List<CatchBlock>
            get() = _catchBlocks
            set(newValue) { updateTreeConnections(_catchBlocks, newValue) }
        private var _finallyBlock: BlockStatement?
        var finallyBlock: BlockStatement?
            get() = _finallyBlock
            set(newValue) { _finallyBlock = updateTreeConnection(_finallyBlock, newValue) }
        override fun deepCopy(): TryStatement {
            return TryStatement(pos, resources = this.resources.deepCopy(), bodyBlock = this.bodyBlock.deepCopy(), catchBlocks = this.catchBlocks.deepCopy(), finallyBlock = this.finallyBlock?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TryStatement && this.resources == other.resources && this.bodyBlock == other.bodyBlock && this.catchBlocks == other.catchBlocks && this.finallyBlock == other.finallyBlock
        }
        override fun hashCode(): Int {
            var hc = resources.hashCode()
            hc = 31 * hc + bodyBlock.hashCode()
            hc = 31 * hc + catchBlocks.hashCode()
            hc = 31 * hc + (finallyBlock?.hashCode() ?: 0)
            return hc
        }
        init {
            updateTreeConnections(this._resources, resources)
            this._bodyBlock = updateTreeConnection(null, bodyBlock)
            updateTreeConnections(this._catchBlocks, catchBlocks)
            this._finallyBlock = updateTreeConnection(null, finallyBlock)
            require(resources.isNotEmpty() || catchBlocks.isNotEmpty() || finallyBlock != null)
            require(bodyBlock.regularBlock())
            require(finallyBlock.regularBlock())
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TryStatement).resources },
                { n -> (n as TryStatement).bodyBlock },
                { n -> (n as TryStatement).catchBlocks },
                { n -> (n as TryStatement).finallyBlock },
            )
        }
    }

    /**
     * Transfers control to the enclosing switch expression to return its value. JLS 14.21
     */
    class YieldStatement(
        pos: Position,
        expr: Expression,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate169
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
        override fun deepCopy(): YieldStatement {
            return YieldStatement(pos, expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is YieldStatement && this.expr == other.expr
        }
        override fun hashCode(): Int {
            return expr.hashCode()
        }
        init {
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as YieldStatement).expr },
            )
        }
    }

    /**
     * Deviating from JLS to keep things simple, we specify these as a regular statement.
     * Basically, a constructor can call `this(...)` once at the beginning to invoke another
     * constructor. JLS 8.8.7.1
     */
    class AlternateConstructorInvocation(
        pos: Position,
        args: Iterable<Argument>,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate170
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
        private val _args: MutableList<Argument> = mutableListOf()
        var args: List<Argument>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): AlternateConstructorInvocation {
            return AlternateConstructorInvocation(pos, args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AlternateConstructorInvocation && this.args == other.args
        }
        override fun hashCode(): Int {
            return args.hashCode()
        }
        init {
            updateTreeConnections(this._args, args)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AlternateConstructorInvocation).args },
            )
        }
    }

    /**
     * Only certain elements can be used as standalone [ExpressionStatement]s. Operations must be further restricted.
     */
    sealed interface ExpressionStatementExpr : Tree, Expression {
        override fun deepCopy(): ExpressionStatementExpr
    }

    /**
     * Distinguishes between old and new style switch statements.
     *
     * JEP 325 switch statements are represented here because backends are statement oriented, but
     * also because there are some cosmetic and functional differences. See the SwitchExpr for its
     * limitations.
     */
    sealed interface SwitchBlock : Tree {
        override fun deepCopy(): SwitchBlock
    }

    class SwitchCaseBlock(
        pos: Position,
        cases: Iterable<CaseStatement>,
    ) : BaseTree(pos), SwitchBlock {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate135
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.cases)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _cases: MutableList<CaseStatement> = mutableListOf()
        var cases: List<CaseStatement>
            get() = _cases
            set(newValue) { updateTreeConnections(_cases, newValue) }
        override fun deepCopy(): SwitchCaseBlock {
            return SwitchCaseBlock(pos, cases = this.cases.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SwitchCaseBlock && this.cases == other.cases
        }
        override fun hashCode(): Int {
            return cases.hashCode()
        }
        init {
            updateTreeConnections(this._cases, cases)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SwitchCaseBlock).cases },
            )
        }
    }

    /**
     * JEP 325 case syntax is finalized in Java 14. JLS 14.11.1
     */
    class SwitchRuleBlock(
        pos: Position,
        rules: Iterable<RuleStatement>,
    ) : BaseTree(pos), SwitchBlock {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate135
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.rules)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _rules: MutableList<RuleStatement> = mutableListOf()
        var rules: List<RuleStatement>
            get() = _rules
            set(newValue) { updateTreeConnections(_rules, newValue) }
        override fun deepCopy(): SwitchRuleBlock {
            return SwitchRuleBlock(pos, rules = this.rules.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SwitchRuleBlock && this.rules == other.rules
        }
        override fun hashCode(): Int {
            return rules.hashCode()
        }
        init {
            updateTreeConnections(this._rules, rules)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SwitchRuleBlock).rules },
            )
        }
    }

    sealed interface SwitchBodyStatement : Tree {
        val label: SwitchLabel
        override fun deepCopy(): SwitchBodyStatement
    }

    /**
     * Old-style case syntax allows arbitrarily stacking case and default statements to allow
     * flexibility in styles; this grammar is more restrictive.
     * JLS 14.11.1
     */
    class CaseStatement(
        pos: Position,
        label: SwitchLabel,
        body: Iterable<BlockLevelStatement>,
    ) : BaseTree(pos), SwitchBodyStatement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate171
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.label
                1 -> FormattableTreeGroup(this.body)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _label: SwitchLabel
        override var label: SwitchLabel
            get() = _label
            set(newValue) { _label = updateTreeConnection(_label, newValue) }
        private val _body: MutableList<BlockLevelStatement> = mutableListOf()
        var body: List<BlockLevelStatement>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        override fun deepCopy(): CaseStatement {
            return CaseStatement(pos, label = this.label.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is CaseStatement && this.label == other.label && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = label.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._label = updateTreeConnection(null, label)
            updateTreeConnections(this._body, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as CaseStatement).label },
                { n -> (n as CaseStatement).body },
            )
        }
    }

    sealed interface SwitchLabel : Tree {
        override fun deepCopy(): SwitchLabel
    }

    class SwitchCaseLabel(
        pos: Position,
        cases: Iterable<Expression>,
    ) : BaseTree(pos), SwitchLabel {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate172
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.cases)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _cases: MutableList<Expression> = mutableListOf()
        var cases: List<Expression>
            get() = _cases
            set(newValue) { updateTreeConnections(_cases, newValue) }
        override fun deepCopy(): SwitchCaseLabel {
            return SwitchCaseLabel(pos, cases = this.cases.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SwitchCaseLabel && this.cases == other.cases
        }
        override fun hashCode(): Int {
            return cases.hashCode()
        }
        init {
            updateTreeConnections(this._cases, cases)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SwitchCaseLabel).cases },
            )
        }
    }

    class SwitchDefaultLabel(
        pos: Position,
    ) : BaseTree(pos), SwitchLabel {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate173
        override val formatElementCount
            get() = 0
        override fun deepCopy(): SwitchDefaultLabel {
            return SwitchDefaultLabel(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SwitchDefaultLabel
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    sealed interface RuleStatement : Tree, SwitchBodyStatement {
        override fun deepCopy(): RuleStatement
    }

    class ExpressionRuleStatement(
        pos: Position,
        label: SwitchLabel,
        expr: Expression,
    ) : BaseTree(pos), RuleStatement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate174
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.label
                1 -> this.expr
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _label: SwitchLabel
        override var label: SwitchLabel
            get() = _label
            set(newValue) { _label = updateTreeConnection(_label, newValue) }
        private var _expr: Expression
        var expr: Expression
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): ExpressionRuleStatement {
            return ExpressionRuleStatement(pos, label = this.label.deepCopy(), expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ExpressionRuleStatement && this.label == other.label && this.expr == other.expr
        }
        override fun hashCode(): Int {
            var hc = label.hashCode()
            hc = 31 * hc + expr.hashCode()
            return hc
        }
        init {
            this._label = updateTreeConnection(null, label)
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ExpressionRuleStatement).label },
                { n -> (n as ExpressionRuleStatement).expr },
            )
        }
    }

    class BlockRuleStatement(
        pos: Position,
        label: SwitchLabel,
        block: BlockStatement,
    ) : BaseTree(pos), RuleStatement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate175
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.label
                1 -> this.block
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _label: SwitchLabel
        override var label: SwitchLabel
            get() = _label
            set(newValue) { _label = updateTreeConnection(_label, newValue) }
        private var _block: BlockStatement
        var block: BlockStatement
            get() = _block
            set(newValue) { _block = updateTreeConnection(_block, newValue) }
        override fun deepCopy(): BlockRuleStatement {
            return BlockRuleStatement(pos, label = this.label.deepCopy(), block = this.block.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BlockRuleStatement && this.label == other.label && this.block == other.block
        }
        override fun hashCode(): Int {
            var hc = label.hashCode()
            hc = 31 * hc + block.hashCode()
            return hc
        }
        init {
            this._label = updateTreeConnection(null, label)
            this._block = updateTreeConnection(null, block)
            require(block.regularBlock())
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as BlockRuleStatement).label },
                { n -> (n as BlockRuleStatement).block },
            )
        }
    }

    class ThrowRuleStatement(
        pos: Position,
        label: SwitchLabel,
        expr: Expression,
    ) : BaseTree(pos), RuleStatement {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate176
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.label
                1 -> this.expr
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _label: SwitchLabel
        override var label: SwitchLabel
            get() = _label
            set(newValue) { _label = updateTreeConnection(_label, newValue) }
        private var _expr: Expression
        var expr: Expression
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): ThrowRuleStatement {
            return ThrowRuleStatement(pos, label = this.label.deepCopy(), expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ThrowRuleStatement && this.label == other.label && this.expr == other.expr
        }
        override fun hashCode(): Int {
            var hc = label.hashCode()
            hc = 31 * hc + expr.hashCode()
            return hc
        }
        init {
            this._label = updateTreeConnection(null, label)
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ThrowRuleStatement).label },
                { n -> (n as ThrowRuleStatement).expr },
            )
        }
    }

    class CatchBlock(
        pos: Position,
        mods: VariableModifiers = VariableModifiers(pos),
        types: Iterable<ClassType>,
        name: Identifier,
        body: BlockStatement,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate177
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.mods
                1 -> FormattableTreeGroup(this.types)
                2 -> this.name
                3 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _mods: VariableModifiers
        var mods: VariableModifiers
            get() = _mods
            set(newValue) { _mods = updateTreeConnection(_mods, newValue) }
        private val _types: MutableList<ClassType> = mutableListOf()
        var types: List<ClassType>
            get() = _types
            set(newValue) { updateTreeConnections(_types, newValue) }
        private var _name: Identifier
        var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _body: BlockStatement
        var body: BlockStatement
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): CatchBlock {
            return CatchBlock(pos, mods = this.mods.deepCopy(), types = this.types.deepCopy(), name = this.name.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is CatchBlock && this.mods == other.mods && this.types == other.types && this.name == other.name && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = mods.hashCode()
            hc = 31 * hc + types.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._mods = updateTreeConnection(null, mods)
            updateTreeConnections(this._types, types)
            this._name = updateTreeConnection(null, name)
            this._body = updateTreeConnection(null, body)
            require(body.regularBlock())
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as CatchBlock).mods },
                { n -> (n as CatchBlock).types },
                { n -> (n as CatchBlock).name },
                { n -> (n as CatchBlock).body },
            )
        }
    }

    /**
     * A field access expression looks up a field of a reference value. JLS 15.11
     *
     * Won't do: super and qualified super expressions; Temper only needs inheritance from interfaces, so the default
     * super() is sufficient.
     */
    class FieldAccessExpr(
        pos: Position,
        expr: Expression,
        field: Identifier,
    ) : BaseTree(pos), ResourceSpecification, Expression, LeftHandSide {
        override val operatorDefinition
            get() = JavaOperatorDefinition.Atom
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate178
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expr
                1 -> this.field
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expr: Expression
        var expr: Expression
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        private var _field: Identifier
        var field: Identifier
            get() = _field
            set(newValue) { _field = updateTreeConnection(_field, newValue) }
        override fun deepCopy(): FieldAccessExpr {
            return FieldAccessExpr(pos, expr = this.expr.deepCopy(), field = this.field.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FieldAccessExpr && this.expr == other.expr && this.field == other.field
        }
        override fun hashCode(): Int {
            var hc = expr.hashCode()
            hc = 31 * hc + field.hashCode()
            return hc
        }
        init {
            this._expr = updateTreeConnection(null, expr)
            this._field = updateTreeConnection(null, field)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FieldAccessExpr).expr },
                { n -> (n as FieldAccessExpr).field },
            )
        }
    }

    /** An argument to a method invocation holds an expression; helps with operator precedence. */
    class Argument(
        pos: Position,
        expr: Expression,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate69
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
        override fun deepCopy(): Argument {
            return Argument(pos, expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Argument && this.expr == other.expr
        }
        override fun hashCode(): Int {
            return expr.hashCode()
        }
        init {
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Argument).expr },
            )
        }
    }

    /**
     * The `this` keyword. JLS 15.8.3, 15.8.4
     *
     * Won't do: qualified this is only required if we need inner classes
     */
    class ThisExpr(
        pos: Position,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition
            get() = JavaOperatorDefinition.Atom
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate179
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

    /**
     * A static field access expression looks up a field of an enum, class or interface type.
     * 6.5.6.2
     *
     * 15.11 Allows for static methods by invoking a *Primary* as in
     *
     *     String s = null;
     *     s.join(", ", "Hello", "World!")
     *
     * This only allows the `String.join` form.
     */
    class StaticFieldAccessExpr(
        pos: Position,
        type: QualIdentifier,
        field: Identifier,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition
            get() = JavaOperatorDefinition.Atom
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate178
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type
                1 -> this.field
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: QualIdentifier
        var type: QualIdentifier
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _field: Identifier
        var field: Identifier
            get() = _field
            set(newValue) { _field = updateTreeConnection(_field, newValue) }
        override fun deepCopy(): StaticFieldAccessExpr {
            return StaticFieldAccessExpr(pos, type = this.type.deepCopy(), field = this.field.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StaticFieldAccessExpr && this.type == other.type && this.field == other.field
        }
        override fun hashCode(): Int {
            var hc = type.hashCode()
            hc = 31 * hc + field.hashCode()
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            this._field = updateTreeConnection(null, field)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as StaticFieldAccessExpr).type },
                { n -> (n as StaticFieldAccessExpr).field },
            )
        }
    }

    /**
     * Shorthand for a lambda that invokes a method on an object; `object::method`. JLS 15.13
     * TODO type arguments
     */
    class InstanceMethodReferenceExpr(
        pos: Position,
        expr: Expression,
        method: Identifier,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition
            get() = JavaOperatorDefinition.Atom
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate180
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expr
                1 -> this.method
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expr: Expression
        var expr: Expression
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        private var _method: Identifier
        var method: Identifier
            get() = _method
            set(newValue) { _method = updateTreeConnection(_method, newValue) }
        override fun deepCopy(): InstanceMethodReferenceExpr {
            return InstanceMethodReferenceExpr(pos, expr = this.expr.deepCopy(), method = this.method.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is InstanceMethodReferenceExpr && this.expr == other.expr && this.method == other.method
        }
        override fun hashCode(): Int {
            var hc = expr.hashCode()
            hc = 31 * hc + method.hashCode()
            return hc
        }
        init {
            this._expr = updateTreeConnection(null, expr)
            this._method = updateTreeConnection(null, method)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as InstanceMethodReferenceExpr).expr },
                { n -> (n as InstanceMethodReferenceExpr).method },
            )
        }
    }

    /**
     * Shorthand for a lambda that invokes a static method; `Type::method`. JLS 15.13
     * TODO type arguments
     */
    class StaticMethodReferenceExpr(
        pos: Position,
        type: QualIdentifier,
        method: Identifier,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition
            get() = JavaOperatorDefinition.Atom
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate180
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type
                1 -> this.method
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: QualIdentifier
        var type: QualIdentifier
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _method: Identifier
        var method: Identifier
            get() = _method
            set(newValue) { _method = updateTreeConnection(_method, newValue) }
        override fun deepCopy(): StaticMethodReferenceExpr {
            return StaticMethodReferenceExpr(pos, type = this.type.deepCopy(), method = this.method.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StaticMethodReferenceExpr && this.type == other.type && this.method == other.method
        }
        override fun hashCode(): Int {
            var hc = type.hashCode()
            hc = 31 * hc + method.hashCode()
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            this._method = updateTreeConnection(null, method)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as StaticMethodReferenceExpr).type },
                { n -> (n as StaticMethodReferenceExpr).method },
            )
        }
    }

    /**
     * Shorthand for a lambda that invokes a constructor; `Type::new`. JLS 15.13
     * TODO type arguments
     */
    class ConstructorReferenceExpr(
        pos: Position,
        type: QualIdentifier,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition
            get() = JavaOperatorDefinition.Atom
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
        private var _type: QualIdentifier
        var type: QualIdentifier
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        override fun deepCopy(): ConstructorReferenceExpr {
            return ConstructorReferenceExpr(pos, type = this.type.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ConstructorReferenceExpr && this.type == other.type
        }
        override fun hashCode(): Int {
            return type.hashCode()
        }
        init {
            this._type = updateTreeConnection(null, type)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ConstructorReferenceExpr).type },
            )
        }
    }

    /**
     * Casts a reference type to a new type.
     * JLS 15.16. TODO stub
     */
    class CastExpr(
        pos: Position,
        type: Type,
        expr: Expression,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition
            get() = JavaOperatorDefinition.Unary
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate182
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

    class InstanceofExpr(
        pos: Position,
        left: Expression,
        right: Type,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition
            get() = JavaOperatorDefinition.Relational
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate183
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
        private var _left: Expression
        var left: Expression
            get() = _left
            set(newValue) { _left = updateTreeConnection(_left, newValue) }
        private var _right: Type
        var right: Type
            get() = _right
            set(newValue) { _right = updateTreeConnection(_right, newValue) }
        override fun deepCopy(): InstanceofExpr {
            return InstanceofExpr(pos, left = this.left.deepCopy(), right = this.right.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is InstanceofExpr && this.left == other.left && this.right == other.right
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
                { n -> (n as InstanceofExpr).left },
                { n -> (n as InstanceofExpr).right },
            )
        }
    }

    /**
     * A lambda expression. JLS 15.27
     */
    class LambdaExpr(
        pos: Position,
        params: LambdaParams,
        body: LambdaBody,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition
            get() = JavaOperatorDefinition.Lambda
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate175
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
        private var _params: LambdaParams
        var params: LambdaParams
            get() = _params
            set(newValue) { _params = updateTreeConnection(_params, newValue) }
        private var _body: LambdaBody
        var body: LambdaBody
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): LambdaExpr {
            return LambdaExpr(pos, params = this.params.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LambdaExpr && this.params == other.params && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = params.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._params = updateTreeConnection(null, params)
            this._body = updateTreeConnection(null, body)
            require(
                when (val b = body) {
                    is BlockStatement -> b.regularBlock()
                    is Expression -> true
                },
            )
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LambdaExpr).params },
                { n -> (n as LambdaExpr).body },
            )
        }
    }

    /**
     * Switch expression; structurally identical to a switch statement. JLS 15.28
     */
    class SwitchExpr(
        pos: Position,
        selector: Expression,
        block: SwitchBlock,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition
            get() = JavaOperatorDefinition.Unary
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate155
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.selector
                1 -> this.block
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _selector: Expression
        var selector: Expression
            get() = _selector
            set(newValue) { _selector = updateTreeConnection(_selector, newValue) }
        private var _block: SwitchBlock
        var block: SwitchBlock
            get() = _block
            set(newValue) { _block = updateTreeConnection(_block, newValue) }
        override fun deepCopy(): SwitchExpr {
            return SwitchExpr(pos, selector = this.selector.deepCopy(), block = this.block.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SwitchExpr && this.selector == other.selector && this.block == other.block
        }
        override fun hashCode(): Int {
            var hc = selector.hashCode()
            hc = 31 * hc + block.hashCode()
            return hc
        }
        init {
            this._selector = updateTreeConnection(null, selector)
            this._block = updateTreeConnection(null, block)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SwitchExpr).selector },
                { n -> (n as SwitchExpr).block },
            )
        }
    }

    /**
     * Assignments have special restrictions on the left-hand side.
     * JLS 15.26
     */
    class AssignmentExpr(
        pos: Position,
        left: LeftHandSide,
        operator: Operator,
        right: Expression,
    ) : BaseTree(pos), ExpressionStatementExpr {
        override val operatorDefinition
            get() = operator.operatorDefinition
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate140
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
        private var _left: LeftHandSide
        var left: LeftHandSide
            get() = _left
            set(newValue) { _left = updateTreeConnection(_left, newValue) }
        private var _operator: Operator
        var operator: Operator
            get() = _operator
            set(newValue) { _operator = updateTreeConnection(_operator, newValue) }
        private var _right: Expression
        var right: Expression
            get() = _right
            set(newValue) { _right = updateTreeConnection(_right, newValue) }
        override fun deepCopy(): AssignmentExpr {
            return AssignmentExpr(pos, left = this.left.deepCopy(), operator = this.operator.deepCopy(), right = this.right.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AssignmentExpr && this.left == other.left && this.operator == other.operator && this.right == other.right
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
            require(operator.operator.isAssignment())
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AssignmentExpr).left },
                { n -> (n as AssignmentExpr).operator },
                { n -> (n as AssignmentExpr).right },
            )
        }
    }

    /** Identify anything with an operator as individual AST elements require the correct operator. */
    sealed interface Operation : Tree, ExpressionStatementExpr {
        override val operatorDefinition
            get() = operator.operator.operatorDefinition
        val operator: Operator
        override fun deepCopy(): Operation
    }

    /**
     * Invokes a method on an object. `object.method(arg, arg, ...)` JLS 15.12
     * If there are no type arguments and the object is `this`, the subject expression can be omitted.
     */
    class InstanceMethodInvocationExpr(
        pos: Position,
        expr: Expression?,
        typeArgs: TypeArguments? = null,
        method: Identifier,
        args: Iterable<Argument>,
    ) : BaseTree(pos), ExpressionStatementExpr {
        override val operatorDefinition
            get() = JavaOperatorDefinition.Atom
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (expr != null && typeArgs != null) {
                    sharedCodeFormattingTemplate184
                } else if (expr != null) {
                    sharedCodeFormattingTemplate185
                } else if (typeArgs != null) {
                    sharedCodeFormattingTemplate186
                } else {
                    sharedCodeFormattingTemplate187
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expr ?: FormattableTreeGroup.empty
                1 -> this.typeArgs ?: FormattableTreeGroup.empty
                2 -> this.method
                3 -> FormattableTreeGroup(this.args)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expr: Expression?
        var expr: Expression?
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        private var _typeArgs: TypeArguments?
        var typeArgs: TypeArguments?
            get() = _typeArgs
            set(newValue) { _typeArgs = updateTreeConnection(_typeArgs, newValue) }
        private var _method: Identifier
        var method: Identifier
            get() = _method
            set(newValue) { _method = updateTreeConnection(_method, newValue) }
        private val _args: MutableList<Argument> = mutableListOf()
        var args: List<Argument>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): InstanceMethodInvocationExpr {
            return InstanceMethodInvocationExpr(pos, expr = this.expr?.deepCopy(), typeArgs = this.typeArgs?.deepCopy(), method = this.method.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is InstanceMethodInvocationExpr && this.expr == other.expr && this.typeArgs == other.typeArgs && this.method == other.method && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = (expr?.hashCode() ?: 0)
            hc = 31 * hc + (typeArgs?.hashCode() ?: 0)
            hc = 31 * hc + method.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._expr = updateTreeConnection(null, expr)
            this._typeArgs = updateTreeConnection(null, typeArgs)
            this._method = updateTreeConnection(null, method)
            updateTreeConnections(this._args, args)
            require(expr != null || typeArgs == null)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as InstanceMethodInvocationExpr).expr },
                { n -> (n as InstanceMethodInvocationExpr).typeArgs },
                { n -> (n as InstanceMethodInvocationExpr).method },
                { n -> (n as InstanceMethodInvocationExpr).args },
            )
        }
    }

    /**
     * Invokes a static method. `Type.<args>method(arg, arg, ...)` JLS 15.12
     */
    class StaticMethodInvocationExpr(
        pos: Position,
        type: QualIdentifier?,
        typeArgs: TypeArguments? = null,
        method: Identifier,
        args: Iterable<Argument>,
    ) : BaseTree(pos), ExpressionStatementExpr {
        override val operatorDefinition
            get() = JavaOperatorDefinition.Atom
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (type != null && typeArgs != null) {
                    sharedCodeFormattingTemplate184
                } else if (type != null) {
                    sharedCodeFormattingTemplate185
                } else if (typeArgs != null) {
                    sharedCodeFormattingTemplate186
                } else {
                    sharedCodeFormattingTemplate187
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type ?: FormattableTreeGroup.empty
                1 -> this.typeArgs ?: FormattableTreeGroup.empty
                2 -> this.method
                3 -> FormattableTreeGroup(this.args)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: QualIdentifier?
        var type: QualIdentifier?
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _typeArgs: TypeArguments?
        var typeArgs: TypeArguments?
            get() = _typeArgs
            set(newValue) { _typeArgs = updateTreeConnection(_typeArgs, newValue) }
        private var _method: Identifier
        var method: Identifier
            get() = _method
            set(newValue) { _method = updateTreeConnection(_method, newValue) }
        private val _args: MutableList<Argument> = mutableListOf()
        var args: List<Argument>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        override fun deepCopy(): StaticMethodInvocationExpr {
            return StaticMethodInvocationExpr(pos, type = this.type?.deepCopy(), typeArgs = this.typeArgs?.deepCopy(), method = this.method.deepCopy(), args = this.args.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StaticMethodInvocationExpr && this.type == other.type && this.typeArgs == other.typeArgs && this.method == other.method && this.args == other.args
        }
        override fun hashCode(): Int {
            var hc = (type?.hashCode() ?: 0)
            hc = 31 * hc + (typeArgs?.hashCode() ?: 0)
            hc = 31 * hc + method.hashCode()
            hc = 31 * hc + args.hashCode()
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            this._typeArgs = updateTreeConnection(null, typeArgs)
            this._method = updateTreeConnection(null, method)
            updateTreeConnections(this._args, args)
            require(type != null || typeArgs == null)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as StaticMethodInvocationExpr).type },
                { n -> (n as StaticMethodInvocationExpr).typeArgs },
                { n -> (n as StaticMethodInvocationExpr).method },
                { n -> (n as StaticMethodInvocationExpr).args },
            )
        }
    }

    /**
     * Constructs a new instance of a class type. JLS 15.9
     */
    class InstanceCreationExpr(
        pos: Position,
        type: ClassType,
        typeArgs: TypeArguments? = null,
        args: Iterable<Argument>,
        classBody: AnonymousClassBody? = null,
    ) : BaseTree(pos), ExpressionStatementExpr {
        override val operatorDefinition
            get() = JavaOperatorDefinition.Atom
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (typeArgs != null && classBody != null) {
                    sharedCodeFormattingTemplate188
                } else if (typeArgs != null) {
                    sharedCodeFormattingTemplate189
                } else if (classBody != null) {
                    sharedCodeFormattingTemplate190
                } else {
                    sharedCodeFormattingTemplate191
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.type
                1 -> this.typeArgs ?: FormattableTreeGroup.empty
                2 -> FormattableTreeGroup(this.args)
                3 -> this.classBody ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _type: ClassType
        var type: ClassType
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _typeArgs: TypeArguments?
        var typeArgs: TypeArguments?
            get() = _typeArgs
            set(newValue) { _typeArgs = updateTreeConnection(_typeArgs, newValue) }
        private val _args: MutableList<Argument> = mutableListOf()
        var args: List<Argument>
            get() = _args
            set(newValue) { updateTreeConnections(_args, newValue) }
        private var _classBody: AnonymousClassBody?
        var classBody: AnonymousClassBody?
            get() = _classBody
            set(newValue) { _classBody = updateTreeConnection(_classBody, newValue) }
        override fun deepCopy(): InstanceCreationExpr {
            return InstanceCreationExpr(pos, type = this.type.deepCopy(), typeArgs = this.typeArgs?.deepCopy(), args = this.args.deepCopy(), classBody = this.classBody?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is InstanceCreationExpr && this.type == other.type && this.typeArgs == other.typeArgs && this.args == other.args && this.classBody == other.classBody
        }
        override fun hashCode(): Int {
            var hc = type.hashCode()
            hc = 31 * hc + (typeArgs?.hashCode() ?: 0)
            hc = 31 * hc + args.hashCode()
            hc = 31 * hc + (classBody?.hashCode() ?: 0)
            return hc
        }
        init {
            this._type = updateTreeConnection(null, type)
            this._typeArgs = updateTreeConnection(null, typeArgs)
            updateTreeConnections(this._args, args)
            this._classBody = updateTreeConnection(null, classBody)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as InstanceCreationExpr).type },
                { n -> (n as InstanceCreationExpr).typeArgs },
                { n -> (n as InstanceCreationExpr).args },
                { n -> (n as InstanceCreationExpr).classBody },
            )
        }
    }

    /**
     * A token to represent the operator itself.
     */
    class Operator(
        pos: Position,
        var operator: JavaOperator,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
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

    /**
     * The postincrement and postdecrement operators. JLS 15.14.2, 15.14.3
     */
    class PostfixExpr(
        pos: Position,
        expr: Expression,
        operator: Operator,
    ) : BaseTree(pos), Operation {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate0
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expr
                1 -> this.operator
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expr: Expression
        var expr: Expression
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        private var _operator: Operator
        override var operator: Operator
            get() = _operator
            set(newValue) { _operator = updateTreeConnection(_operator, newValue) }
        override fun deepCopy(): PostfixExpr {
            return PostfixExpr(pos, expr = this.expr.deepCopy(), operator = this.operator.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PostfixExpr && this.expr == other.expr && this.operator == other.operator
        }
        override fun hashCode(): Int {
            var hc = expr.hashCode()
            hc = 31 * hc + operator.hashCode()
            return hc
        }
        init {
            this._expr = updateTreeConnection(null, expr)
            this._operator = updateTreeConnection(null, operator)
            require(operator.isPostfix())
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as PostfixExpr).expr },
                { n -> (n as PostfixExpr).operator },
            )
        }
    }

    /**
     * Includes unary operators. JLS 15.15.1, 15.15.2, 15.15.3, 15.15.4, 15.15.5, 15.15.6
     */
    class PrefixExpr(
        pos: Position,
        operator: Operator,
        expr: Expression,
    ) : BaseTree(pos), Operation {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate0
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.operator
                1 -> this.expr
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _operator: Operator
        override var operator: Operator
            get() = _operator
            set(newValue) { _operator = updateTreeConnection(_operator, newValue) }
        private var _expr: Expression
        var expr: Expression
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): PrefixExpr {
            return PrefixExpr(pos, operator = this.operator.deepCopy(), expr = this.expr.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PrefixExpr && this.operator == other.operator && this.expr == other.expr
        }
        override fun hashCode(): Int {
            var hc = operator.hashCode()
            hc = 31 * hc + expr.hashCode()
            return hc
        }
        init {
            this._operator = updateTreeConnection(null, operator)
            this._expr = updateTreeConnection(null, expr)
            require(operator.isPrefix())
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as PrefixExpr).operator },
                { n -> (n as PrefixExpr).expr },
            )
        }
    }

    /**
     * Most binary expressions, see also [AssignmentExpr].
     * JLS 15.17 through 15.25.
     */
    class InfixExpr(
        pos: Position,
        left: Expression,
        operator: Operator,
        right: Expression,
    ) : BaseTree(pos), Operation {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate140
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
        private var _left: Expression
        var left: Expression
            get() = _left
            set(newValue) { _left = updateTreeConnection(_left, newValue) }
        private var _operator: Operator
        override var operator: Operator
            get() = _operator
            set(newValue) { _operator = updateTreeConnection(_operator, newValue) }
        private var _right: Expression
        var right: Expression
            get() = _right
            set(newValue) { _right = updateTreeConnection(_right, newValue) }
        override fun deepCopy(): InfixExpr {
            return InfixExpr(pos, left = this.left.deepCopy(), operator = this.operator.deepCopy(), right = this.right.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is InfixExpr && this.left == other.left && this.operator == other.operator && this.right == other.right
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
            require(operator.isInfix())
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as InfixExpr).left },
                { n -> (n as InfixExpr).operator },
                { n -> (n as InfixExpr).right },
            )
        }
    }

    /**
     * A literal integer expression.
     * Generally see JLS 15.8.1. There are some corner cases for integers noted in 15.8.3
     */
    class IntegerLiteral(
        pos: Position,
        var value: Long,
    ) : BaseTree(pos), LiteralExpr {
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            value.emit(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): IntegerLiteral {
            return IntegerLiteral(pos, value = this.value)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is IntegerLiteral && this.value == other.value
        }
        override fun hashCode(): Int {
            return value.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /** JLS 15.8.1 */
    class FloatingPointLiteral(
        pos: Position,
        var value: Number,
        var precision: Precision,
    ) : BaseTree(pos), LiteralExpr {
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            value.emit(tokenSink, precision)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): FloatingPointLiteral {
            return FloatingPointLiteral(pos, value = this.value, precision = this.precision)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FloatingPointLiteral && this.value == other.value && this.precision == other.precision
        }
        override fun hashCode(): Int {
            var hc = value.hashCode()
            hc = 31 * hc + precision.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /** JLS 15.8.1 */
    class BooleanLiteral(
        pos: Position,
        var value: Boolean,
    ) : BaseTree(pos), LiteralExpr {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (value) {
                    sharedCodeFormattingTemplate192
                } else {
                    sharedCodeFormattingTemplate193
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

    /** JLS 15.8.1 */
    class CharacterLiteral(
        pos: Position,
        var value: Char,
    ) : BaseTree(pos), LiteralExpr {
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            value.emit(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): CharacterLiteral {
            return CharacterLiteral(pos, value = this.value)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is CharacterLiteral && this.value == other.value
        }
        override fun hashCode(): Int {
            return value.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /** JLS 15.8.1 */
    class StringLiteral(
        pos: Position,
        var value: String,
    ) : BaseTree(pos), LiteralExpr {
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            value.emit(tokenSink)
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

    /** JLS 15.8.1 */
    class NullLiteral(
        pos: Position,
    ) : BaseTree(pos), LiteralExpr {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate194
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

    /**
     * Literal expression to specify a type. `SomeType.class` JLS 15.8.2
     *
     * The restriction that a class literal must be a raw class is enforced by
     * the requires mechanism.
     *
     * Won't do: handle `void.class` and other oddities; we don't seem to need class literals in code gen.
     */
    class ClassLiteral(
        pos: Position,
        type: Type,
    ) : BaseTree(pos), LiteralExpr {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate195
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
        override fun deepCopy(): ClassLiteral {
            return ClassLiteral(pos, type = this.type.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ClassLiteral && this.type == other.type
        }
        override fun hashCode(): Int {
            return type.hashCode()
        }
        init {
            this._type = updateTreeConnection(null, type)
            require(type.isRaw())
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ClassLiteral).type },
            )
        }
    }

    /**
     * A class body within an instance creation expression. JLS 15.9
     */
    class AnonymousClassBody(
        pos: Position,
        body: Iterable<ClassBodyDeclaration>,
    ) : BaseTree(pos) {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate135
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
        private val _body: MutableList<ClassBodyDeclaration> = mutableListOf()
        var body: List<ClassBodyDeclaration>
            get() = _body
            set(newValue) { updateTreeConnections(_body, newValue) }
        override fun deepCopy(): AnonymousClassBody {
            return AnonymousClassBody(pos, body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AnonymousClassBody && this.body == other.body
        }
        override fun hashCode(): Int {
            return body.hashCode()
        }
        init {
            updateTreeConnections(this._body, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AnonymousClassBody).body },
            )
        }
    }

    sealed interface LambdaParams : Tree {
        override fun deepCopy(): LambdaParams
    }

    class LambdaSimpleParams(
        pos: Position,
        params: Iterable<Identifier>,
    ) : BaseTree(pos), LambdaParams {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (oneParam) {
                    sharedCodeFormattingTemplate196
                } else {
                    sharedCodeFormattingTemplate197
                }
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
        private val _params: MutableList<Identifier> = mutableListOf()
        var params: List<Identifier>
            get() = _params
            set(newValue) { updateTreeConnections(_params, newValue) }
        val oneParam: Boolean
            get() = params.size == 1
        override fun deepCopy(): LambdaSimpleParams {
            return LambdaSimpleParams(pos, params = this.params.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LambdaSimpleParams && this.params == other.params
        }
        override fun hashCode(): Int {
            return params.hashCode()
        }
        init {
            updateTreeConnections(this._params, params)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LambdaSimpleParams).params },
            )
        }
    }

    class LambdaComplexParams(
        pos: Position,
        params: Iterable<LambdaParameter>,
    ) : BaseTree(pos), LambdaParams {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate197
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
        private val _params: MutableList<LambdaParameter> = mutableListOf()
        var params: List<LambdaParameter>
            get() = _params
            set(newValue) { updateTreeConnections(_params, newValue) }
        override fun deepCopy(): LambdaComplexParams {
            return LambdaComplexParams(pos, params = this.params.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LambdaComplexParams && this.params == other.params
        }
        override fun hashCode(): Int {
            return params.hashCode()
        }
        init {
            updateTreeConnections(this._params, params)
            require(params.lambdaValidArity())
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LambdaComplexParams).params },
            )
        }
    }

    sealed interface LambdaParameter : Tree {
        override fun deepCopy(): LambdaParameter
    }

    /**
     * A typed parameter for a lambda. JLS 15.27.1
     */
    class LambdaParam(
        pos: Position,
        mods: VariableModifiers = VariableModifiers(pos),
        type: Type?,
        name: Identifier,
    ) : BaseTree(pos), LambdaParameter {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (type != null) {
                    sharedCodeFormattingTemplate140
                } else {
                    sharedCodeFormattingTemplate198
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.mods
                1 -> this.type ?: FormattableTreeGroup.empty
                2 -> this.name
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _mods: VariableModifiers
        var mods: VariableModifiers
            get() = _mods
            set(newValue) { _mods = updateTreeConnection(_mods, newValue) }
        private var _type: Type?
        var type: Type?
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _name: Identifier
        var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override fun deepCopy(): LambdaParam {
            return LambdaParam(pos, mods = this.mods.deepCopy(), type = this.type?.deepCopy(), name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LambdaParam && this.mods == other.mods && this.type == other.type && this.name == other.name
        }
        override fun hashCode(): Int {
            var hc = mods.hashCode()
            hc = 31 * hc + (type?.hashCode() ?: 0)
            hc = 31 * hc + name.hashCode()
            return hc
        }
        init {
            this._mods = updateTreeConnection(null, mods)
            this._type = updateTreeConnection(null, type)
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LambdaParam).mods },
                { n -> (n as LambdaParam).type },
                { n -> (n as LambdaParam).name },
            )
        }
    }

    /**
     * A variable arity parameter for a lambda. JLS 15.27.1
     */
    class LambdaVarParam(
        pos: Position,
        mods: VariableModifiers = VariableModifiers(pos),
        type: Type?,
        name: Identifier,
    ) : BaseTree(pos), LambdaParameter {
        override val operatorDefinition: JavaOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (type != null) {
                    sharedCodeFormattingTemplate141
                } else {
                    sharedCodeFormattingTemplate199
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.mods
                1 -> this.type ?: FormattableTreeGroup.empty
                2 -> this.name
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _mods: VariableModifiers
        var mods: VariableModifiers
            get() = _mods
            set(newValue) { _mods = updateTreeConnection(_mods, newValue) }
        private var _type: Type?
        var type: Type?
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _name: Identifier
        var name: Identifier
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override fun deepCopy(): LambdaVarParam {
            return LambdaVarParam(pos, mods = this.mods.deepCopy(), type = this.type?.deepCopy(), name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LambdaVarParam && this.mods == other.mods && this.type == other.type && this.name == other.name
        }
        override fun hashCode(): Int {
            var hc = mods.hashCode()
            hc = 31 * hc + (type?.hashCode() ?: 0)
            hc = 31 * hc + name.hashCode()
            return hc
        }
        init {
            this._mods = updateTreeConnection(null, mods)
            this._type = updateTreeConnection(null, type)
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as LambdaVarParam).mods },
                { n -> (n as LambdaVarParam).type },
                { n -> (n as LambdaVarParam).name },
            )
        }
    }

    /** `{{0}} {{1}}` */
    private val sharedCodeFormattingTemplate0 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `open module {{0}} \{ \n {{1*\n}} \n \}` */
    private val sharedCodeFormattingTemplate1 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("open", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("module", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
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

    /** `module {{0}} \{ \n {{1*\n}} \n \}` */
    private val sharedCodeFormattingTemplate2 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("module", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
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

    /** `{{0}} {{1}} \n {{2*\n}} \n {{3}}` */
    private val sharedCodeFormattingTemplate3 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} {{1}} \n {{3}}` */
    private val sharedCodeFormattingTemplate4 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} {{2*\n}} \n {{3}}` */
    private val sharedCodeFormattingTemplate5 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} {{3}}` */
    private val sharedCodeFormattingTemplate6 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `package {{0}} ;` */
    private val sharedCodeFormattingTemplate7 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("package", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `` */
    private val sharedCodeFormattingTemplate8 =
        CodeFormattingTemplate.empty

    /** `{{0*.}}` */
    private val sharedCodeFormattingTemplate9 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
        )

    /** `requires transitive static {{0}} ;` */
    private val sharedCodeFormattingTemplate10 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("requires", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("transitive", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `requires transitive {{0}} ;` */
    private val sharedCodeFormattingTemplate11 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("requires", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("transitive", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `requires static {{0}} ;` */
    private val sharedCodeFormattingTemplate12 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("requires", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `requires {{0}} ;` */
    private val sharedCodeFormattingTemplate13 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("requires", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `exports {{0}} to {{1*,}} ;` */
    private val sharedCodeFormattingTemplate14 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("exports", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("to", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `exports {{0}} ;` */
    private val sharedCodeFormattingTemplate15 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("exports", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `opens {{0}} to {{1*,}} ;` */
    private val sharedCodeFormattingTemplate16 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("opens", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("to", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `opens {{0}} ;` */
    private val sharedCodeFormattingTemplate17 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("opens", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `uses {{0}} ;` */
    private val sharedCodeFormattingTemplate18 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("uses", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `provides {{0}} with {{1*,}} ;` */
    private val sharedCodeFormattingTemplate19 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("provides", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("with", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `provides {{0}} ;` */
    private val sharedCodeFormattingTemplate20 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("provides", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `import {{0}} ;` */
    private val sharedCodeFormattingTemplate21 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `import static {{0}} ;` */
    private val sharedCodeFormattingTemplate22 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `import {{0}} . * ;` */
    private val sharedCodeFormattingTemplate23 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `import static {{0}} . * ;` */
    private val sharedCodeFormattingTemplate24 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} class {{3}} {{4}} extends {{5}} implements {{6*,}} permits {{7*,}} \{ \n {{8*\n}} \n \}` */
    private val sharedCodeFormattingTemplate25 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("implements", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("permits", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    8,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} class {{3}} {{4}} extends {{5}} implements {{6*,}} \{ \n {{8*\n}} \n \}` */
    private val sharedCodeFormattingTemplate26 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("implements", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    8,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} class {{3}} {{4}} extends {{5}} permits {{7*,}} \{ \n {{8*\n}} \n \}` */
    private val sharedCodeFormattingTemplate27 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("permits", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    8,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} class {{3}} {{4}} extends {{5}} \{ \n {{8*\n}} \n \}` */
    private val sharedCodeFormattingTemplate28 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    8,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} class {{3}} {{4}} implements {{6*,}} permits {{7*,}} \{ \n {{8*\n}} \n \}` */
    private val sharedCodeFormattingTemplate29 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("implements", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("permits", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    8,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} class {{3}} {{4}} implements {{6*,}} \{ \n {{8*\n}} \n \}` */
    private val sharedCodeFormattingTemplate30 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("implements", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    8,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} class {{3}} {{4}} permits {{7*,}} \{ \n {{8*\n}} \n \}` */
    private val sharedCodeFormattingTemplate31 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("permits", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    8,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} class {{3}} {{4}} \{ \n {{8*\n}} \n \}` */
    private val sharedCodeFormattingTemplate32 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    8,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} class {{3}} {{4}} extends {{5}} implements {{6*,}} permits {{7*,}} \{ \n {{8*\n}} \n \}` */
    private val sharedCodeFormattingTemplate33 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("implements", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("permits", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    8,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} class {{3}} {{4}} extends {{5}} implements {{6*,}} \{ \n {{8*\n}} \n \}` */
    private val sharedCodeFormattingTemplate34 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("implements", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    8,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} class {{3}} {{4}} extends {{5}} permits {{7*,}} \{ \n {{8*\n}} \n \}` */
    private val sharedCodeFormattingTemplate35 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("permits", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    8,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} class {{3}} {{4}} extends {{5}} \{ \n {{8*\n}} \n \}` */
    private val sharedCodeFormattingTemplate36 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    8,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} class {{3}} {{4}} implements {{6*,}} permits {{7*,}} \{ \n {{8*\n}} \n \}` */
    private val sharedCodeFormattingTemplate37 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("implements", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("permits", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    8,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} class {{3}} {{4}} implements {{6*,}} \{ \n {{8*\n}} \n \}` */
    private val sharedCodeFormattingTemplate38 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("implements", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    8,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} class {{3}} {{4}} permits {{7*,}} \{ \n {{8*\n}} \n \}` */
    private val sharedCodeFormattingTemplate39 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("permits", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    8,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} class {{3}} {{4}} \{ \n {{8*\n}} \n \}` */
    private val sharedCodeFormattingTemplate40 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    8,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} interface {{3}} {{4}} extends {{5*,}} permits {{6*,}} \{ \n {{7*\n}} \n \}` */
    private val sharedCodeFormattingTemplate41 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("interface", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("permits", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} interface {{3}} {{4}} extends {{5*,}} \{ \n {{7*\n}} \n \}` */
    private val sharedCodeFormattingTemplate42 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("interface", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} interface {{3}} {{4}} permits {{6*,}} \{ \n {{7*\n}} \n \}` */
    private val sharedCodeFormattingTemplate43 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("interface", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("permits", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} interface {{3}} {{4}} \{ \n {{7*\n}} \n \}` */
    private val sharedCodeFormattingTemplate44 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("interface", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} interface {{3}} {{4}} extends {{5*,}} permits {{6*,}} \{ \n {{7*\n}} \n \}` */
    private val sharedCodeFormattingTemplate45 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("interface", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("permits", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} interface {{3}} {{4}} extends {{5*,}} \{ \n {{7*\n}} \n \}` */
    private val sharedCodeFormattingTemplate46 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("interface", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} interface {{3}} {{4}} permits {{6*,}} \{ \n {{7*\n}} \n \}` */
    private val sharedCodeFormattingTemplate47 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("interface", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("permits", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} interface {{3}} {{4}} \{ \n {{7*\n}} \n \}` */
    private val sharedCodeFormattingTemplate48 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("interface", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `< {{0*,}} >` */
    private val sharedCodeFormattingTemplate49 =
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

    /** `` */
    private val sharedCodeFormattingTemplate50 =
        CodeFormattingTemplate.empty

    /** ``JavadocTokens.open` {{0*\n}} `JavadocTokens.close`` */
    private val sharedCodeFormattingTemplate51 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken(JavadocTokens.open),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken(JavadocTokens.close),
            ),
        )

    /** `@ {{0}} ( {{1*,}} )` */
    private val sharedCodeFormattingTemplate52 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("@", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `@ {{0}}` */
    private val sharedCodeFormattingTemplate53 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("@", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `{{0*}} {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate54 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0*}} {{1}}` */
    private val sharedCodeFormattingTemplate55 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} {{1*}} {{2}} class {{3}} {{4}} extends {{5}} implements {{6*,}} \{ \n {{7*\n}} \n \}` */
    private val sharedCodeFormattingTemplate56 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("implements", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} class {{3}} {{4}} extends {{5}} \{ \n {{7*\n}} \n \}` */
    private val sharedCodeFormattingTemplate57 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} class {{3}} {{4}} implements {{6*,}} \{ \n {{7*\n}} \n \}` */
    private val sharedCodeFormattingTemplate58 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("implements", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} class {{3}} {{4}} \{ \n {{7*\n}} \n \}` */
    private val sharedCodeFormattingTemplate59 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} class {{3}} {{4}} extends {{5}} implements {{6*,}} \{ \n {{7*\n}} \n \}` */
    private val sharedCodeFormattingTemplate60 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("implements", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} class {{3}} {{4}} extends {{5}} \{ \n {{7*\n}} \n \}` */
    private val sharedCodeFormattingTemplate61 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} class {{3}} {{4}} implements {{6*,}} \{ \n {{7*\n}} \n \}` */
    private val sharedCodeFormattingTemplate62 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("implements", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} class {{3}} {{4}} \{ \n {{7*\n}} \n \}` */
    private val sharedCodeFormattingTemplate63 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} interface {{2}} {{3}} extends {{4*,}} \{ \n {{5*\n}} \n \}` */
    private val sharedCodeFormattingTemplate64 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("interface", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} interface {{2}} {{3}} \{ \n {{5*\n}} \n \}` */
    private val sharedCodeFormattingTemplate65 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("interface", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} interface {{2}} {{3}} extends {{4*,}} \{ \n {{5*\n}} \n \}` */
    private val sharedCodeFormattingTemplate66 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("interface", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} interface {{2}} {{3}} \{ \n {{5*\n}} \n \}` */
    private val sharedCodeFormattingTemplate67 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("interface", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} {{1}} extends {{2*&}}` */
    private val sharedCodeFormattingTemplate68 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken("\u0026", OutputTokenType.Punctuation),
                ),
            ),
        )

    /** `{{0}}` */
    private val sharedCodeFormattingTemplate69 =
        CodeFormattingTemplate.OneSubstitution(0)

    /** `{{0*}} ?` */
    private val sharedCodeFormattingTemplate70 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("?", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} ? extends {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate71 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("?", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0*}} ? extends {{1}}` */
    private val sharedCodeFormattingTemplate72 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("?", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0*}} ? super {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate73 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("?", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("super", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0*}} ? super {{1}}` */
    private val sharedCodeFormattingTemplate74 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("?", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("super", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0*.}} . {{1*}} {{2}}` */
    private val sharedCodeFormattingTemplate75 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{1*}} {{2}}` */
    private val sharedCodeFormattingTemplate76 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0}} [ ]` */
    private val sharedCodeFormattingTemplate77 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `void` */
    private val sharedCodeFormattingTemplate78 =
        CodeFormattingTemplate.LiteralToken("void", OutputTokenType.Word)

    /** `{{0}} {{1*}} {{2}} {{3}} {{4*,}} ;` */
    private val sharedCodeFormattingTemplate79 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} {{3}} {{4*,}} ;` */
    private val sharedCodeFormattingTemplate80 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} {{3}} {{4}} {{5}} ( {{6*,}} ) throws {{7*,}} {{8}}` */
    private val sharedCodeFormattingTemplate81 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(8),
            ),
        )

    /** `{{0}} {{1*}} {{2}} {{3}} {{4}} {{5}} ( {{6*,}} ) throws {{7*,}} ;` */
    private val sharedCodeFormattingTemplate82 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} {{3}} {{4}} {{5}} ( {{6*,}} ) {{8}}` */
    private val sharedCodeFormattingTemplate83 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(8),
            ),
        )

    /** `{{0}} {{1*}} {{2}} {{3}} {{4}} {{5}} ( {{6*,}} ) ;` */
    private val sharedCodeFormattingTemplate84 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} {{3}} {{4}} {{5}} ( {{6*,}} ) throws {{7*,}} {{8}}` */
    private val sharedCodeFormattingTemplate85 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(8),
            ),
        )

    /** `{{1*}} {{2}} {{3}} {{4}} {{5}} ( {{6*,}} ) throws {{7*,}} ;` */
    private val sharedCodeFormattingTemplate86 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    7,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} {{3}} {{4}} {{5}} ( {{6*,}} ) {{8}}` */
    private val sharedCodeFormattingTemplate87 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(8),
            ),
        )

    /** `{{1*}} {{2}} {{3}} {{4}} {{5}} ( {{6*,}} ) ;` */
    private val sharedCodeFormattingTemplate88 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} ( {{2*,}} ) throws {{3*,}} {{4}}` */
    private val sharedCodeFormattingTemplate89 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(4),
            ),
        )

    /** `{{0}} {{1}} ( {{2*,}} ) {{4}}` */
    private val sharedCodeFormattingTemplate90 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(4),
            ),
        )

    /** `static {{0}}` */
    private val sharedCodeFormattingTemplate91 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `{{0}} {{1*}} {{2}} {{3*,}} ;` */
    private val sharedCodeFormattingTemplate92 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} {{3*,}} ;` */
    private val sharedCodeFormattingTemplate93 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} default {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} {{7}}` */
    private val sharedCodeFormattingTemplate94 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("default", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} {{1*}} default {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} ;` */
    private val sharedCodeFormattingTemplate95 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("default", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} default {{2}} {{3}} {{4}} ( {{5*,}} ) {{7}}` */
    private val sharedCodeFormattingTemplate96 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("default", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} {{1*}} default {{2}} {{3}} {{4}} ( {{5*,}} ) ;` */
    private val sharedCodeFormattingTemplate97 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("default", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} private {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} {{7}}` */
    private val sharedCodeFormattingTemplate98 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("private", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} {{1*}} private {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} ;` */
    private val sharedCodeFormattingTemplate99 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("private", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} private {{2}} {{3}} {{4}} ( {{5*,}} ) {{7}}` */
    private val sharedCodeFormattingTemplate100 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("private", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} {{1*}} private {{2}} {{3}} {{4}} ( {{5*,}} ) ;` */
    private val sharedCodeFormattingTemplate101 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("private", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} static {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} {{7}}` */
    private val sharedCodeFormattingTemplate102 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} {{1*}} static {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} ;` */
    private val sharedCodeFormattingTemplate103 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} static {{2}} {{3}} {{4}} ( {{5*,}} ) {{7}}` */
    private val sharedCodeFormattingTemplate104 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} {{1*}} static {{2}} {{3}} {{4}} ( {{5*,}} ) ;` */
    private val sharedCodeFormattingTemplate105 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} private static {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} {{7}}` */
    private val sharedCodeFormattingTemplate106 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("private", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} {{1*}} private static {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} ;` */
    private val sharedCodeFormattingTemplate107 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("private", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} private static {{2}} {{3}} {{4}} ( {{5*,}} ) {{7}}` */
    private val sharedCodeFormattingTemplate108 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("private", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} {{1*}} private static {{2}} {{3}} {{4}} ( {{5*,}} ) ;` */
    private val sharedCodeFormattingTemplate109 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("private", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} {{7}}` */
    private val sharedCodeFormattingTemplate110 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} {{1*}} {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} ;` */
    private val sharedCodeFormattingTemplate111 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1*}} {{2}} {{3}} {{4}} ( {{5*,}} ) {{7}}` */
    private val sharedCodeFormattingTemplate112 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} {{1*}} {{2}} {{3}} {{4}} ( {{5*,}} ) ;` */
    private val sharedCodeFormattingTemplate113 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} default {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} {{7}}` */
    private val sharedCodeFormattingTemplate114 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("default", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{1*}} default {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} ;` */
    private val sharedCodeFormattingTemplate115 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("default", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} default {{2}} {{3}} {{4}} ( {{5*,}} ) {{7}}` */
    private val sharedCodeFormattingTemplate116 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("default", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{1*}} default {{2}} {{3}} {{4}} ( {{5*,}} ) ;` */
    private val sharedCodeFormattingTemplate117 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("default", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} private {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} {{7}}` */
    private val sharedCodeFormattingTemplate118 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("private", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{1*}} private {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} ;` */
    private val sharedCodeFormattingTemplate119 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("private", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} private {{2}} {{3}} {{4}} ( {{5*,}} ) {{7}}` */
    private val sharedCodeFormattingTemplate120 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("private", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{1*}} private {{2}} {{3}} {{4}} ( {{5*,}} ) ;` */
    private val sharedCodeFormattingTemplate121 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("private", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} static {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} {{7}}` */
    private val sharedCodeFormattingTemplate122 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{1*}} static {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} ;` */
    private val sharedCodeFormattingTemplate123 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} static {{2}} {{3}} {{4}} ( {{5*,}} ) {{7}}` */
    private val sharedCodeFormattingTemplate124 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{1*}} static {{2}} {{3}} {{4}} ( {{5*,}} ) ;` */
    private val sharedCodeFormattingTemplate125 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} private static {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} {{7}}` */
    private val sharedCodeFormattingTemplate126 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("private", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{1*}} private static {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} ;` */
    private val sharedCodeFormattingTemplate127 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("private", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} private static {{2}} {{3}} {{4}} ( {{5*,}} ) {{7}}` */
    private val sharedCodeFormattingTemplate128 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("private", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{1*}} private static {{2}} {{3}} {{4}} ( {{5*,}} ) ;` */
    private val sharedCodeFormattingTemplate129 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("private", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} {{7}}` */
    private val sharedCodeFormattingTemplate130 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{1*}} {{2}} {{3}} {{4}} ( {{5*,}} ) throws {{6*,}} ;` */
    private val sharedCodeFormattingTemplate131 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("throws", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{1*}} {{2}} {{3}} {{4}} ( {{5*,}} ) {{7}}` */
    private val sharedCodeFormattingTemplate132 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{1*}} {{2}} {{3}} {{4}} ( {{5*,}} ) ;` */
    private val sharedCodeFormattingTemplate133 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    5,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} = {{1}}` */
    private val sharedCodeFormattingTemplate134 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `\{ \n {{0*\n}} \n \}` */
    private val sharedCodeFormattingTemplate135 =
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

    /** `{{0*}} public` */
    private val sharedCodeFormattingTemplate136 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("public", OutputTokenType.Word),
            ),
        )

    /** `{{0*}} protected` */
    private val sharedCodeFormattingTemplate137 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("protected", OutputTokenType.Word),
            ),
        )

    /** `{{0*}} private` */
    private val sharedCodeFormattingTemplate138 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("private", OutputTokenType.Word),
            ),
        )

    /** `{{0*}}` */
    private val sharedCodeFormattingTemplate139 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.empty,
        )

    /** `{{0}} {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate140 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0}} {{1}} ... {{2}}` */
    private val sharedCodeFormattingTemplate141 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("...", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0*}} final` */
    private val sharedCodeFormattingTemplate142 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("final", OutputTokenType.Word),
            ),
        )

    /** `\{ {{0*,}} \}` */
    private val sharedCodeFormattingTemplate143 =
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

    /** `{{0}} {{1}} {{2}} = {{3}} ;` */
    private val sharedCodeFormattingTemplate144 =
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
    private val sharedCodeFormattingTemplate145 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} var {{2}} = {{3}} ;` */
    private val sharedCodeFormattingTemplate146 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("var", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} var {{2}} ;` */
    private val sharedCodeFormattingTemplate147 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("var", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `;` */
    private val sharedCodeFormattingTemplate148 =
        CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation)

    /** `{{0}} : {{1}}` */
    private val sharedCodeFormattingTemplate149 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
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

    /** `if ( {{0}} ) {{1}} else {{2}}` */
    private val sharedCodeFormattingTemplate151 =
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
    private val sharedCodeFormattingTemplate152 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("if", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `assert {{0}} : {{1}} ;` */
    private val sharedCodeFormattingTemplate153 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("assert", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `assert {{0}} ;` */
    private val sharedCodeFormattingTemplate154 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("assert", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `switch ( {{0}} ) {{1}}` */
    private val sharedCodeFormattingTemplate155 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("switch", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `while ( {{0}} ) {{1}}` */
    private val sharedCodeFormattingTemplate156 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("while", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `do {{0}} while ( {{1}} ) ;` */
    private val sharedCodeFormattingTemplate157 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("while", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `break {{0}} ;` */
    private val sharedCodeFormattingTemplate158 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("break", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `break ;` */
    private val sharedCodeFormattingTemplate159 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("break", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `continue {{0}} ;` */
    private val sharedCodeFormattingTemplate160 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("continue", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `continue ;` */
    private val sharedCodeFormattingTemplate161 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("continue", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `return {{0}} ;` */
    private val sharedCodeFormattingTemplate162 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("return", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `return ;` */
    private val sharedCodeFormattingTemplate163 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("return", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `throw {{0}} ;` */
    private val sharedCodeFormattingTemplate164 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("throw", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `try ( {{0*;}} ) {{1}} {{2*}} finally {{3}}` */
    private val sharedCodeFormattingTemplate165 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `try ( {{0*;}} ) {{1}} {{2*}}` */
    private val sharedCodeFormattingTemplate166 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
            ),
        )

    /** `try {{1}} {{2*}} finally {{3}}` */
    private val sharedCodeFormattingTemplate167 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("finally", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `try {{1}} {{2*}}` */
    private val sharedCodeFormattingTemplate168 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.empty,
                ),
            ),
        )

    /** `yield {{0}} ;` */
    private val sharedCodeFormattingTemplate169 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("yield", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `this ( {{0*,}} ) ;` */
    private val sharedCodeFormattingTemplate170 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("this", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} : \n {{1*\n}}` */
    private val sharedCodeFormattingTemplate171 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.NewLine,
                ),
            ),
        )

    /** `case {{0*,}}` */
    private val sharedCodeFormattingTemplate172 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("case", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
            ),
        )

    /** `default` */
    private val sharedCodeFormattingTemplate173 =
        CodeFormattingTemplate.LiteralToken("default", OutputTokenType.Word)

    /** `{{0}} -> {{1}} ;` */
    private val sharedCodeFormattingTemplate174 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} -> {{1}}` */
    private val sharedCodeFormattingTemplate175 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} -> throw {{1}} ;` */
    private val sharedCodeFormattingTemplate176 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("throw", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `catch ( {{0}} {{1*|}} {{2}} ) {{3}}` */
    private val sharedCodeFormattingTemplate177 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("catch", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken("|", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} . {{1}}` */
    private val sharedCodeFormattingTemplate178 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `this` */
    private val sharedCodeFormattingTemplate179 =
        CodeFormattingTemplate.LiteralToken("this", OutputTokenType.Word)

    /** `{{0}} :: {{1}}` */
    private val sharedCodeFormattingTemplate180 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("::", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} :: new` */
    private val sharedCodeFormattingTemplate181 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("::", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("new", OutputTokenType.Word),
            ),
        )

    /** `( {{0}} ) {{1}}` */
    private val sharedCodeFormattingTemplate182 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} instanceof {{1}}` */
    private val sharedCodeFormattingTemplate183 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("instanceof", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} . {{1}} {{2}} ( {{3*,}} )` */
    private val sharedCodeFormattingTemplate184 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}} . {{2}} ( {{3*,}} )` */
    private val sharedCodeFormattingTemplate185 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{1}} {{2}} ( {{3*,}} )` */
    private val sharedCodeFormattingTemplate186 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{2}} ( {{3*,}} )` */
    private val sharedCodeFormattingTemplate187 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `new {{0}} {{1}} ( {{2*,}} ) {{3}}` */
    private val sharedCodeFormattingTemplate188 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("new", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `new {{0}} {{1}} ( {{2*,}} )` */
    private val sharedCodeFormattingTemplate189 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("new", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `new {{0}} ( {{2*,}} ) {{3}}` */
    private val sharedCodeFormattingTemplate190 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("new", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `new {{0}} ( {{2*,}} )` */
    private val sharedCodeFormattingTemplate191 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("new", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `true` */
    private val sharedCodeFormattingTemplate192 =
        CodeFormattingTemplate.LiteralToken("true", OutputTokenType.Word)

    /** `false` */
    private val sharedCodeFormattingTemplate193 =
        CodeFormattingTemplate.LiteralToken("false", OutputTokenType.Word)

    /** `null` */
    private val sharedCodeFormattingTemplate194 =
        CodeFormattingTemplate.LiteralToken("null", OutputTokenType.Word)

    /** `{{0}} . class` */
    private val sharedCodeFormattingTemplate195 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
            ),
        )

    /** `{{0*,}}` */
    private val sharedCodeFormattingTemplate196 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
        )

    /** `( {{0*,}} )` */
    private val sharedCodeFormattingTemplate197 =
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

    /** `{{0}} var {{2}}` */
    private val sharedCodeFormattingTemplate198 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("var", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0}} var ... {{2}}` */
    private val sharedCodeFormattingTemplate199 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("var", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("...", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )
}
