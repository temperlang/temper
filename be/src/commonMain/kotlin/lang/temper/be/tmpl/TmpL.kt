@file:lang.temper.common.Generated("OutputGrammarCodeGenerator")
@file:Suppress("ktlint", "unused", "CascadeIf", "MagicNumber", "MemberNameEqualsClassName", "MemberVisibilityCanBePrivate")

package lang.temper.be.tmpl
import lang.temper.ast.ChildMemberRelationships
import lang.temper.ast.OutData
import lang.temper.ast.OutTree
import lang.temper.ast.deepCopy
import lang.temper.be.BaseOutData
import lang.temper.be.BaseOutTree
import lang.temper.be.Dependencies
import lang.temper.be.TargetLanguageName
import lang.temper.be.TargetLanguageTypeName
import lang.temper.common.Either
import lang.temper.common.TriState
import lang.temper.common.replaceSubList
import lang.temper.common.temperEscaper
import lang.temper.format.CodeFormattingTemplate
import lang.temper.format.FormattableEnum
import lang.temper.format.FormattableTreeGroup
import lang.temper.format.FormattingHints
import lang.temper.format.IndexableFormattableTreeElement
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenAssociation
import lang.temper.format.TokenSink
import lang.temper.lexer.Genre
import lang.temper.library.LibraryConfiguration
import lang.temper.library.LibraryConfigurations
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.FilePathSegment
import lang.temper.log.FilePathSegmentOrPseudoSegment
import lang.temper.log.Position
import lang.temper.log.SameDirPseudoFilePathSegment
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModularName
import lang.temper.name.ModuleName
import lang.temper.name.NamingContext
import lang.temper.name.OutName
import lang.temper.name.ParsedName
import lang.temper.name.QName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.Symbol
import lang.temper.type.MemberOverride2
import lang.temper.type.PropertyShape
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeShape
import lang.temper.type.VisibleMemberShape
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedType
import lang.temper.type2.Descriptor
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.value.DependencyCategory
import lang.temper.value.Value
import lang.temper.value.void

object TmpL {
    sealed interface Tree : OutTree<Tree> {
        override fun formattingHints(): FormattingHints = TmpLFormattingHints.getInstance()
        override val operatorDefinition: TmpLOperatorDefinition?
        override fun deepCopy(): Tree
    }
    sealed class BaseTree(
        pos: Position,
    ) : BaseOutTree<Tree>(pos), Tree
    sealed interface Data : OutData<Data> {
        override fun formattingHints(): FormattingHints = TmpLFormattingHints.getInstance()
        override val operatorDefinition: TmpLOperatorDefinition?
    }
    sealed class BaseData : BaseOutData<Data>(), Data

    enum class Visibility : FormattableEnum {
        Private,
        Protected,
        Public,
    }

    enum class TypeDefKind : FormattableEnum {
        Class,
        Interface,
    }

    enum class IdKind : FormattableEnum {
        Type,
        Value,
    }

    enum class IdReach : FormattableEnum {
        External,
        Internal,
    }

    enum class TypeDeclarationKind : FormattableEnum {
        Class,
        Interface,
        Enum,
    }

    /** The topmost top-level */
    class ModuleSet(
        pos: Position,
        modules: Iterable<Module>,
        var genre: Genre,
        var libraryConfigurations: LibraryConfigurations,
        var mergedNamingContext: NamingContext,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate0
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.modules)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _modules: MutableList<Module> = mutableListOf()
        var modules: List<Module>
            get() = _modules
            set(newValue) { updateTreeConnections(_modules, newValue) }
        val libraryConfiguration: LibraryConfiguration
            get() = libraryConfigurations.currentLibraryConfiguration

        /** A context path that can be used to trim error message locations */
        val context: FilePath
            get() = libraryConfiguration.libraryRoot
        override fun deepCopy(): ModuleSet {
            return ModuleSet(pos, modules = this.modules.deepCopy(), genre = this.genre, libraryConfigurations = this.libraryConfigurations, mergedNamingContext = this.mergedNamingContext)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ModuleSet && this.modules == other.modules && this.genre == other.genre && this.libraryConfigurations == other.libraryConfigurations && this.mergedNamingContext == other.mergedNamingContext
        }
        override fun hashCode(): Int {
            var hc = modules.hashCode()
            hc = 31 * hc + genre.hashCode()
            hc = 31 * hc + libraryConfigurations.hashCode()
            hc = 31 * hc + mergedNamingContext.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._modules, modules)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ModuleSet).modules },
            )
        }
    }

    /**
     * These roughly correspond to [lang.temper.frontend.Module]s but we create
     * modules to pool values referred to by multiple lispy ASTs.
     *
     * Most backends should be able to map these roughly to source files or directories.
     */
    class Module(
        pos: Position,
        codeLocation: CodeLocationMetadata,
        moduleMetadata: ModuleMetadata,
        deps: Iterable<LibraryDependency>,
        imports: Iterable<Import>,
        topLevels: Iterable<TopLevel>,
        result: Expression?,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (deps.isNotEmpty() && imports.isNotEmpty() && topLevels.isNotEmpty() && result != null) {
                    sharedCodeFormattingTemplate1
                } else if (deps.isNotEmpty() && imports.isNotEmpty() && topLevels.isNotEmpty()) {
                    sharedCodeFormattingTemplate2
                } else if (deps.isNotEmpty() && imports.isNotEmpty() && result != null) {
                    sharedCodeFormattingTemplate3
                } else if (deps.isNotEmpty() && imports.isNotEmpty()) {
                    sharedCodeFormattingTemplate4
                } else if (deps.isNotEmpty() && topLevels.isNotEmpty() && result != null) {
                    sharedCodeFormattingTemplate5
                } else if (deps.isNotEmpty() && topLevels.isNotEmpty()) {
                    sharedCodeFormattingTemplate6
                } else if (deps.isNotEmpty() && result != null) {
                    sharedCodeFormattingTemplate7
                } else if (deps.isNotEmpty()) {
                    sharedCodeFormattingTemplate8
                } else if (imports.isNotEmpty() && topLevels.isNotEmpty() && result != null) {
                    sharedCodeFormattingTemplate9
                } else if (imports.isNotEmpty() && topLevels.isNotEmpty()) {
                    sharedCodeFormattingTemplate10
                } else if (imports.isNotEmpty() && result != null) {
                    sharedCodeFormattingTemplate11
                } else if (imports.isNotEmpty()) {
                    sharedCodeFormattingTemplate12
                } else if (topLevels.isNotEmpty() && result != null) {
                    sharedCodeFormattingTemplate13
                } else if (topLevels.isNotEmpty()) {
                    sharedCodeFormattingTemplate14
                } else if (result != null) {
                    sharedCodeFormattingTemplate15
                } else {
                    sharedCodeFormattingTemplate16
                }
        override val formatElementCount
            get() = 6
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.codeLocation
                1 -> this.moduleMetadata
                2 -> FormattableTreeGroup(this.deps)
                3 -> FormattableTreeGroup(this.imports)
                4 -> FormattableTreeGroup(this.topLevels)
                5 -> this.result ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _codeLocation: CodeLocationMetadata
        var codeLocation: CodeLocationMetadata
            get() = _codeLocation
            set(newValue) { _codeLocation = newValue }
        private var _moduleMetadata: ModuleMetadata
        var moduleMetadata: ModuleMetadata
            get() = _moduleMetadata
            set(newValue) { _moduleMetadata = updateTreeConnection(_moduleMetadata, newValue) }
        private val _deps: MutableList<LibraryDependency> = mutableListOf()
        var deps: List<LibraryDependency>
            get() = _deps
            set(newValue) { updateTreeConnections(_deps, newValue) }
        private val _imports: MutableList<Import> = mutableListOf()
        var imports: List<Import>
            get() = _imports
            set(newValue) { updateTreeConnections(_imports, newValue) }
        private val _topLevels: MutableList<TopLevel> = mutableListOf()
        var topLevels: List<TopLevel>
            get() = _topLevels
            set(newValue) { updateTreeConnections(_topLevels, newValue) }
        private var _result: Expression?
        var result: Expression?
            get() = _result
            set(newValue) { _result = updateTreeConnection(_result, newValue) }
        override fun deepCopy(): Module {
            return Module(pos, codeLocation = this.codeLocation, moduleMetadata = this.moduleMetadata.deepCopy(), deps = this.deps.deepCopy(), imports = this.imports.deepCopy(), topLevels = this.topLevels.deepCopy(), result = this.result?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Module && this.codeLocation == other.codeLocation && this.moduleMetadata == other.moduleMetadata && this.deps == other.deps && this.imports == other.imports && this.topLevels == other.topLevels && this.result == other.result
        }
        override fun hashCode(): Int {
            var hc = codeLocation.hashCode()
            hc = 31 * hc + moduleMetadata.hashCode()
            hc = 31 * hc + deps.hashCode()
            hc = 31 * hc + imports.hashCode()
            hc = 31 * hc + topLevels.hashCode()
            hc = 31 * hc + (result?.hashCode() ?: 0)
            return hc
        }
        init {
            this._codeLocation = codeLocation
            this._moduleMetadata = updateTreeConnection(null, moduleMetadata)
            updateTreeConnections(this._deps, deps)
            updateTreeConnections(this._imports, imports)
            updateTreeConnections(this._topLevels, topLevels)
            this._result = updateTreeConnection(null, result)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Module).moduleMetadata },
                { n -> (n as Module).deps },
                { n -> (n as Module).imports },
                { n -> (n as Module).topLevels },
                { n -> (n as Module).result },
            )
        }
    }

    data class CodeLocationMetadata(
        override val sourceLibrary: DashedIdentifier,
        val codeLocation: ModuleName,
        val origin: NamingContext,
        val outputPath: FilePath,
    ) : BaseData() {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(
                OutputToken(
                    "//// " + commentSafe(codeLocation.diagnostic + " => " + outputPath) + "\n",
                    OutputTokenType.Comment,
                ),
            )
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * Information about a [Module].
     */
    class ModuleMetadata(
        pos: Position,
        var dependencyCategory: DependencyCategory,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            val text = buildString {
                when (dependencyCategory) {
                    DependencyCategory.Production -> {}
                    DependencyCategory.Test -> append("DependencyCategory.Test")
                }
            }
            if (text.isNotEmpty()) {
                tokenSink.emit(
                    OutputToken(
                        "//// " + commentSafe(text) + "\n",
                        OutputTokenType.Comment,
                    ),
                )
            }
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): ModuleMetadata {
            return ModuleMetadata(pos, dependencyCategory = this.dependencyCategory)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ModuleMetadata && this.dependencyCategory == other.dependencyCategory
        }
        override fun hashCode(): Int {
            return dependencyCategory.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class LibraryDependency(
        pos: Position,
        var libraryName: DashedIdentifier,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(OutputToken("require", OutputTokenType.Word))
            tokenSink.emit(OutputToken(libraryName.text, OutputTokenType.Name))
            tokenSink.emit(OutToks.semi)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): LibraryDependency {
            return LibraryDependency(pos, libraryName = this.libraryName)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LibraryDependency && this.libraryName == other.libraryName
        }
        override fun hashCode(): Int {
            return libraryName.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * Allows backends to recognize, before processing a [Module] body, which names in [Id]s
     * are defined externally, so that it can:
     *
     * - generate its own linking directives,
     * - compute complete (non-transitive) dependency metadata,
     */
    class Import(
        pos: Position,
        metadata: Iterable<DeclarationMetadata> = emptyList(),
        externalName: Id,
        localName: Id?,
        sig: ImportSignature?,
        path: ModulePath?,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (localName != null && sig != null && path != null) {
                    sharedCodeFormattingTemplate17
                } else if (localName != null && sig != null) {
                    sharedCodeFormattingTemplate18
                } else if (localName != null && path != null) {
                    sharedCodeFormattingTemplate19
                } else if (localName != null) {
                    sharedCodeFormattingTemplate20
                } else if (sig != null && path != null) {
                    sharedCodeFormattingTemplate21
                } else if (sig != null) {
                    sharedCodeFormattingTemplate22
                } else if (path != null) {
                    sharedCodeFormattingTemplate23
                } else {
                    sharedCodeFormattingTemplate24
                }
        override val formatElementCount
            get() = 5
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.externalName
                2 -> this.localName ?: FormattableTreeGroup.empty
                3 -> this.sig ?: FormattableTreeGroup.empty
                4 -> this.path ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _externalName: Id
        var externalName: Id
            get() = _externalName
            set(newValue) { _externalName = updateTreeConnection(_externalName, newValue) }
        private var _localName: Id?
        var localName: Id?
            get() = _localName
            set(newValue) { _localName = updateTreeConnection(_localName, newValue) }
        private var _sig: ImportSignature?
        var sig: ImportSignature?
            get() = _sig
            set(newValue) { _sig = updateTreeConnection(_sig, newValue) }
        private var _path: ModulePath?
        var path: ModulePath?
            get() = _path
            set(newValue) { _path = updateTreeConnection(_path, newValue) }
        val externalModuleName: ModuleName?
            get() = (externalName.name as? ModularName)?.origin?.loc as? ModuleName
        override fun deepCopy(): Import {
            return Import(pos, metadata = this.metadata, externalName = this.externalName.deepCopy(), localName = this.localName?.deepCopy(), sig = this.sig?.deepCopy(), path = this.path?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Import && this.metadata == other.metadata && this.externalName == other.externalName && this.localName == other.localName && this.sig == other.sig && this.path == other.path
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + externalName.hashCode()
            hc = 31 * hc + (localName?.hashCode() ?: 0)
            hc = 31 * hc + (sig?.hashCode() ?: 0)
            hc = 31 * hc + (path?.hashCode() ?: 0)
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._externalName = updateTreeConnection(null, externalName)
            this._localName = updateTreeConnection(null, localName)
            this._sig = updateTreeConnection(null, sig)
            this._path = updateTreeConnection(null, path)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Import).externalName },
                { n -> (n as Import).localName },
                { n -> (n as Import).sig },
                { n -> (n as Import).path },
            )
        }
    }

    sealed interface StatementOrTopLevel : Tree {
        override fun deepCopy(): StatementOrTopLevel
    }

    /**
     * A thing that can sit at the top level of a [Module]
     */
    sealed interface TopLevel : Tree, StatementOrTopLevel {
        override fun deepCopy(): TopLevel
    }

    /** Identifies an operation that may fail. */
    sealed interface Handled : Tree {
        override fun deepCopy(): Handled
    }

    /** That which may be assigned to a something that can hold a value. */
    sealed interface RightHandSide : Tree {
        override fun deepCopy(): RightHandSide
    }

    /**
     * Names can refer to expressions (see [Reference]) or to named functions
     * (see [FnReference]) so it is sometimes convenient to use the same
     * translation paths to prepare both.
     *
     * This is an abstraction that allows conflating the two temporarily
     * in the TmpL translator but might also be useful in functional language
     * backends that can conflate the two without compromising translation.
     */
    sealed interface ExpressionOrCallable : Tree {
        override fun deepCopy(): ExpressionOrCallable
    }

    /**
     * An input to a [call][CallExpression].
     */
    sealed interface Actual : Tree {
        override fun deepCopy(): Actual
    }

    /**
     * A subject may have properties and methods.
     *
     * This is either an expression, for [InstanceProperty] and [InstanceMethod]s,
     * or it is a type name, for [StaticProperty] and [StaticMethod]s.
     */
    sealed interface Subject : Tree {
        override fun deepCopy(): Subject
    }

    sealed interface Expression : Tree, Handled, RightHandSide, ExpressionOrCallable, Actual, Subject {
        val type: Type2
        override fun deepCopy(): Expression
    }

    /** A path from a module to another */
    sealed interface ModulePath : Tree {
        val libraryName: DashedIdentifier
        val to: ModuleName
        val translatedPath: FilePath
        override fun deepCopy(): ModulePath
    }

    class SameLibraryPath(
        pos: Position,
        override var libraryName: DashedIdentifier,
        override var to: ModuleName,
        override var translatedPath: FilePath,
    ) : BaseTree(pos), ModulePath {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            // Render with '.' or '..' as first segment CommonJS style.
            var segments = relativePath
            if (segments.firstOrNull() is FilePathSegment?) {
                segments = listOf(SameDirPseudoFilePathSegment) + segments
            }

            tokenSink.emit(
                OutputToken(
                    temperEscaper.escape(segments.join(isDir = false)),
                    OutputTokenType.QuotedValue,
                ),
            )
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null

        /**
         * Relative path from this module's outputPath to the targets output path.
         */
        val relativePath: List<FilePathSegmentOrPseudoSegment>
            get() =
                run {
                    val destination = translatedPath
                    var source = FilePath.emptyPath

                    var anc: Tree = this
                    while (true) {
                        if (anc is Module) {
                            source = anc.codeLocation.outputPath
                            break
                        }
                        anc = (anc.parent as? Tree) ?: break
                    }

                    source.relativePathTo(destination)
                }
        override fun deepCopy(): SameLibraryPath {
            return SameLibraryPath(pos, libraryName = this.libraryName, to = this.to, translatedPath = this.translatedPath)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SameLibraryPath && this.libraryName == other.libraryName && this.to == other.to && this.translatedPath == other.translatedPath
        }
        override fun hashCode(): Int {
            var hc = libraryName.hashCode()
            hc = 31 * hc + to.hashCode()
            hc = 31 * hc + translatedPath.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class CrossLibraryPath(
        pos: Position,
        override var libraryName: DashedIdentifier,
        override var to: ModuleName,
        override var translatedPath: FilePath,
    ) : BaseTree(pos), ModulePath {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            val segmentsWithLibraryName =
                listOf(FilePathSegment(libraryName.text)) + translatedPath.segments
            tokenSink.emit(
                OutputToken(
                    temperEscaper.escape(segmentsWithLibraryName.join(isDir = false)),
                    OutputTokenType.QuotedValue,
                ),
            )
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): CrossLibraryPath {
            return CrossLibraryPath(pos, libraryName = this.libraryName, to = this.to, translatedPath = this.translatedPath)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is CrossLibraryPath && this.libraryName == other.libraryName && this.to == other.to && this.translatedPath == other.translatedPath
        }
        override fun hashCode(): Int {
            var hc = libraryName.hashCode()
            hc = 31 * hc + to.hashCode()
            hc = 31 * hc + translatedPath.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    data class DeclarationMetadata(
        override val sourceLibrary: DashedIdentifier,
        val key: MetadataKey,
        val value: MetadataValue,
    ) : BaseData() {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (!isVoid) {
                    sharedCodeFormattingTemplate25
                } else {
                    sharedCodeFormattingTemplate26
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
        val isVoid: Boolean
            get() = (value as? ValueData)?.value == void
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as DeclarationMetadata).key },
                { n -> (n as DeclarationMetadata).value },
            )
        }
    }

    /**
     * A name for a definition.
     * A [MemberName] if it's part of a type definition, or an identifier otherwise.
     */
    sealed interface DefName : Tree {
        override fun deepCopy(): DefName
    }

    /**
     * Corresponds to an identifier.
     *
     * This type is being migrated from using a ResolvedName to a TargetLanguageName.
     *
     * The debugId field is meant to allow source-mapping.  By associating target
     * language names with Temper names, we can provide a better debugging experience.
     */
    class Id(
        pos: Position,
        var nameContent: Either<ResolvedName, TargetLanguageName>,
    ) : BaseTree(pos), DefName {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            when (val nameContent = this.nameContent) {
              is Either.Left -> {
                val nameToRender = outName ?: nameContent.item
                tokenSink.emit(nameToRender.toToken(inOperatorPosition = false))
              }
              is Either.Right -> nameContent.item.renderTo(tokenSink)
            }
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        val name: ResolvedName
            get() = (nameContent as Either.Left).item
        val newName: TargetLanguageName
            get() = (nameContent as Either.Right).item
        override fun deepCopy(): Id {
            return Id(pos, nameContent = this.nameContent)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Id && this.nameContent == other.nameContent
        }
        override fun hashCode(): Int {
            return nameContent.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
        private var _outName: OutName? = null
        private var _debugId: ResolvedParsedName? = null

        var outName: OutName?
            get() = _outName ?: outNameFor(name)
            set(newOutName) {
                this._outName = newOutName
                this._debugId = null
            }

        var debugId: ResolvedParsedName?
            get() = _debugId
            set(newDebugId) {
                this._debugId = newDebugId
                this._outName = null
            }

        constructor(
            pos: Position,
            name: ResolvedName,
            outName: OutName? = null,
        ) : this(pos, Either.Left(name)) {
            this._outName = outName
        }

        constructor(
            pos: Position,
            name: TargetLanguageName,
            debugId: ResolvedParsedName? = null,
        ) : this(pos, Either.Right(name)) {
            this._debugId = debugId
        }
    }

    sealed interface ImportSignature : Tree {
        val metadata: List<DeclarationMetadata>
        override fun deepCopy(): ImportSignature
    }

    /**
     * An externally defined name that may be used in a [DefinedType].
     */
    class ImportedType(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        var typeShape: TypeShape,
    ) : BaseTree(pos), ImportSignature {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            metadata.forEach {
                it.formatTo(tokenSink)
            }
            tokenSink.emit(OutputToken("type", OutputTokenType.Word))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        override fun deepCopy(): ImportedType {
            return ImportedType(pos, metadata = this.metadata, typeShape = this.typeShape)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImportedType && this.metadata == other.metadata && this.typeShape == other.typeShape
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + typeShape.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class ImportedFunction(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        var type: Signature2,
    ) : BaseTree(pos), ImportSignature {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate27
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> IndexableFormattableTreeElement.wrap(this.type)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        override fun deepCopy(): ImportedFunction {
            return ImportedFunction(pos, metadata = this.metadata, type = this.type)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImportedFunction && this.metadata == other.metadata && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + type.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class ImportedValue(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        type: AType,
    ) : BaseTree(pos), ImportSignature {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate28
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.type
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _type: AType
        var type: AType
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        override fun deepCopy(): ImportedValue {
            return ImportedValue(pos, metadata = this.metadata, type = this.type.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImportedValue && this.metadata == other.metadata && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + type.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._type = updateTreeConnection(null, type)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ImportedValue).type },
            )
        }
    }

    class ImportedConnection(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        connectedKey: ConnectedKey,
    ) : BaseTree(pos), ImportSignature {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate29
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.connectedKey
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _connectedKey: ConnectedKey
        var connectedKey: ConnectedKey
            get() = _connectedKey
            set(newValue) { _connectedKey = updateTreeConnection(_connectedKey, newValue) }
        override fun deepCopy(): ImportedConnection {
            return ImportedConnection(pos, metadata = this.metadata, connectedKey = this.connectedKey.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ImportedConnection && this.metadata == other.metadata && this.connectedKey == other.connectedKey
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + connectedKey.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._connectedKey = updateTreeConnection(null, connectedKey)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ImportedConnection).connectedKey },
            )
        }
    }

    /**
     * AType is either an old-style [Type] or a new style [NewType].
     * Once all backends have been adjusted to new style types, we will retire
     * this wrapper node along with the old style.
     */
    class AType(
        pos: Position,
        privOtOrNull: Type?,
        privNtOrNull: NewType?,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            val ot = privOtOrNull
            val nt = privNtOrNull
            val t: Tree = ot ?: nt!!
            t.formatTo(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        private var _privOtOrNull: Type?
        var privOtOrNull: Type?
            get() = _privOtOrNull
            set(newValue) { _privOtOrNull = updateTreeConnection(_privOtOrNull, newValue) }
        private var _privNtOrNull: NewType?
        var privNtOrNull: NewType?
            get() = _privNtOrNull
            set(newValue) { _privNtOrNull = updateTreeConnection(_privNtOrNull, newValue) }
        val t: Either<Type, NewType>
            get() =
                run {
                    val ot = privOtOrNull
                    val nt = privNtOrNull
                    if (ot != null) {
                        Either.Left(ot)
                    } else {
                        Either.Right(nt!!)
                    }
                }
        val ot: Type
            get() = privOtOrNull!!
        val nt: NewType
            get() = privNtOrNull!!
        override fun deepCopy(): AType {
            return AType(pos, privOtOrNull = this.privOtOrNull?.deepCopy(), privNtOrNull = this.privNtOrNull?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AType && this.privOtOrNull == other.privOtOrNull && this.privNtOrNull == other.privNtOrNull
        }
        override fun hashCode(): Int {
            var hc = (privOtOrNull?.hashCode() ?: 0)
            hc = 31 * hc + (privNtOrNull?.hashCode() ?: 0)
            return hc
        }
        init {
            this._privOtOrNull = updateTreeConnection(null, privOtOrNull)
            this._privNtOrNull = updateTreeConnection(null, privNtOrNull)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AType).privOtOrNull },
                { n -> (n as AType).privNtOrNull },
            )
        }
        constructor(ot: Type) : this(ot.pos, privOtOrNull = ot, privNtOrNull = null)
        constructor(nt: NewType) : this(nt.pos, privOtOrNull = null, privNtOrNull = nt)
    }

    class ConnectedKey(
        pos: Position,
        var key: String,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(
                OutputToken(
                    temperEscaper.escape(key),
                    OutputTokenType.QuotedValue,
                ),
            )
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): ConnectedKey {
            return ConnectedKey(pos, key = this.key)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ConnectedKey && this.key == other.key
        }
        override fun hashCode(): Int {
            return key.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class LibraryName(
        pos: Position,
        var libraryName: DashedIdentifier,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(
                OutputToken(
                    temperEscaper.escape(libraryName.text),
                    OutputTokenType.QuotedValue,
                ),
            )
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): LibraryName {
            return LibraryName(pos, libraryName = this.libraryName)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LibraryName && this.libraryName == other.libraryName
        }
        override fun hashCode(): Int {
            return libraryName.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    sealed interface DefNameData : Data

    data class IdData(
        override val sourceLibrary: DashedIdentifier,
        val nameContent: Either<ResolvedName, TargetLanguageName>,
    ) : BaseData(), DefNameData {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            when (val nameContent = this.nameContent) {
              is Either.Left -> {
                val nameToRender = outName ?: nameContent.item
                tokenSink.emit(nameToRender.toToken(inOperatorPosition = false))
              }
              is Either.Right -> nameContent.item.renderTo(tokenSink)
            }
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        val name: ResolvedName
            get() = (nameContent as Either.Left).item
        val newName: TargetLanguageName
            get() = (nameContent as Either.Right).item
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships()
        }
        private var _outName: OutName? = null
        private var _debugId: ResolvedParsedName? = null

        var outName: OutName?
            get() = _outName ?: outNameFor(name)
            set(newOutName) {
                this._outName = newOutName
                this._debugId = null
            }

        var debugId: ResolvedParsedName?
            get() = _debugId
            set(newDebugId) {
                this._debugId = newDebugId
                this._outName = null
            }

        constructor(
            sourceLibrary: DashedIdentifier,
            name: ResolvedName,
            outName: OutName? = null,
        ) : this(sourceLibrary, Either.Left(name)) {
            this._outName = outName
        }

        constructor(
            sourceLibrary: DashedIdentifier,
            name: TargetLanguageName,
            debugId: ResolvedParsedName? = null,
        ) : this(sourceLibrary, Either.Right(name)) {
            this._debugId = debugId
        }
    }

    class DotName(
        pos: Position,
        var dotNameText: String,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(dotNameToOutputToken(this.dotNameText))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): DotName {
            return DotName(pos, dotNameText = this.dotNameText)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is DotName && this.dotNameText == other.dotNameText
        }
        override fun hashCode(): Int {
            return dotNameText.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    sealed interface Type : Tree {
        override fun deepCopy(): Type
    }

    /**
     * NewType is a target to which existing backends are migrating.
     *
     * After the tricky parts of the migration are done:
     *
     * 1. the `ot` variant of [AType] will no longer be needed.
     *    A PR will remove that variant and the [Type] node type hierarchy.
     * 2. A PR will rework all uses of [AType].nt to refer to [NewType] directly
     *    and get rid of [AType]
     * 3. A PR will rename NewType to Type and NewTypeData to TypeData.
     */
    sealed interface NewType : Tree {
        val name: Id
        val canBeNull: Boolean
        val typeActuals: List<NewType>
        override fun deepCopy(): NewType
        fun typeDefinition(dependencies: Dependencies<*>): TypeDefData =
            dependencies.getTypeDef(name)
    }

    /** Like [AType], splits the difference between old and new style but for formal type parameter lists */
    class ATypeParameters(
        pos: Position,
        privOtOrNull: TypeParameters?,
        privNtOrNull: FormalTypeDefs?,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            val ot = privOtOrNull
            val nt = privNtOrNull
            val t: Tree = ot ?: nt!!
            t.formatTo(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        private var _privOtOrNull: TypeParameters?
        var privOtOrNull: TypeParameters?
            get() = _privOtOrNull
            set(newValue) { _privOtOrNull = updateTreeConnection(_privOtOrNull, newValue) }
        private var _privNtOrNull: FormalTypeDefs?
        var privNtOrNull: FormalTypeDefs?
            get() = _privNtOrNull
            set(newValue) { _privNtOrNull = updateTreeConnection(_privNtOrNull, newValue) }
        val t: Either<TypeParameters, FormalTypeDefs>
            get() =
                run {
                    val ot = privOtOrNull
                    val nt = privNtOrNull
                    if (ot != null) {
                        Either.Left(ot)
                    } else {
                        Either.Right(nt!!)
                    }
                }
        val ot: TypeParameters
            get() = privOtOrNull!!
        val nt: FormalTypeDefs
            get() = privNtOrNull!!
        override fun deepCopy(): ATypeParameters {
            return ATypeParameters(pos, privOtOrNull = this.privOtOrNull?.deepCopy(), privNtOrNull = this.privNtOrNull?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ATypeParameters && this.privOtOrNull == other.privOtOrNull && this.privNtOrNull == other.privNtOrNull
        }
        override fun hashCode(): Int {
            var hc = (privOtOrNull?.hashCode() ?: 0)
            hc = 31 * hc + (privNtOrNull?.hashCode() ?: 0)
            return hc
        }
        init {
            this._privOtOrNull = updateTreeConnection(null, privOtOrNull)
            this._privNtOrNull = updateTreeConnection(null, privNtOrNull)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ATypeParameters).privOtOrNull },
                { n -> (n as ATypeParameters).privNtOrNull },
            )
        }
        constructor(ot: TypeParameters) : this(ot.pos, privOtOrNull = ot, privNtOrNull = null)
        constructor(nt: FormalTypeDefs) : this(nt.pos, privOtOrNull = null, privNtOrNull = nt)
    }

    /** A group of formal type parameters in the declaration of a type polymorphic thing. */
    class TypeParameters(
        pos: Position,
        typeParameters: Iterable<TypeFormal>,
    ) : BaseTree(pos) {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.Angle
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (typeParameters.isNotEmpty()) {
                    sharedCodeFormattingTemplate30
                } else {
                    sharedCodeFormattingTemplate31
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.typeParameters)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _typeParameters: MutableList<TypeFormal> = mutableListOf()
        var typeParameters: List<TypeFormal>
            get() = _typeParameters
            set(newValue) { updateTreeConnections(_typeParameters, newValue) }
        override fun deepCopy(): TypeParameters {
            return TypeParameters(pos, typeParameters = this.typeParameters.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeParameters && this.typeParameters == other.typeParameters
        }
        override fun hashCode(): Int {
            return typeParameters.hashCode()
        }
        init {
            updateTreeConnections(this._typeParameters, typeParameters)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeParameters).typeParameters },
            )
        }
    }

    class FormalTypeDefs(
        pos: Position,
        defs: Iterable<FormalTypeDef>,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (defs.isNotEmpty()) {
                    sharedCodeFormattingTemplate30
                } else {
                    sharedCodeFormattingTemplate31
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.defs)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _defs: MutableList<FormalTypeDef> = mutableListOf()
        var defs: List<FormalTypeDef>
            get() = _defs
            set(newValue) { updateTreeConnections(_defs, newValue) }
        override fun deepCopy(): FormalTypeDefs {
            return FormalTypeDefs(pos, defs = this.defs.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FormalTypeDefs && this.defs == other.defs
        }
        override fun hashCode(): Int {
            return defs.hashCode()
        }
        init {
            updateTreeConnections(this._defs, defs)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FormalTypeDefs).defs },
            )
        }
    }

    sealed interface NewTypeData : Data {
        val name: IdData
        val canBeNull: Boolean
        val typeActuals: List<NewTypeData>
        fun typeDefinition(dependencies: Dependencies<*>): TypeDefData =
            dependencies.getTypeDef(name)
    }

    /**
     * A type which admits the null value when *canBeNull* is true.
     */
    sealed interface NullableType : Tree, NewType {
        val withoutNull: NullableType
        val withNull: NullableType
        override fun deepCopy(): NullableType
    }

    /**
     * A type that excludes the null value.
     */
    sealed interface NonNullType : Tree, NewType {
        override val canBeNull: Boolean
            get() = false
        override fun deepCopy(): NonNullType
    }

    /**
     * A possible return type for a function call.
     */
    sealed interface RetType : Tree, NewType {
        override fun deepCopy(): RetType
    }

    /**
     * A passing [ResultType] result can either be an expression or it can be void.
     * TmpL should never require that a result type be stored in a variable directly
     * because results are not first class.
     * *Result\<Void, E>* is even more special. The TmpL translator must never ask
     * the on-pass-unwrap operation to assign a pass result to a variable when the
     * pass type is [VoidType].
     *
     * Note that, since [VoidType] is not a valid bound for a type parameter,
     * *Result\<T, E>* is always allowed to unwrap.
     */
    sealed interface PassType : Tree, RetType {
        override fun deepCopy(): PassType
    }

    /**
     * An expression type is a type that describes the values to which the expression
     * can evaluate.
     * Expressions that never evaluate to a result can still be described using
     * the semi-special *Never\<IF_I_DID>* type.
     * Expressions have *first-class* values meaning they can be stored in variables
     * and passed as arguments in function calls.
     */
    sealed interface ExprType : Tree, PassType {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (typeActuals.isNotEmpty() && canBeNull) {
                    sharedCodeFormattingTemplate32
                } else if (typeActuals.isNotEmpty()) {
                    sharedCodeFormattingTemplate33
                } else if (canBeNull) {
                    sharedCodeFormattingTemplate34
                } else {
                    sharedCodeFormattingTemplate26
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name
                1 -> FormattableTreeGroup(this.typeActuals)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        override fun deepCopy(): ExprType
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ExprType).name },
                { n -> (n as ExprType).typeActuals },
            )
        }
    }

    /** An expression type that excludes the null value. */
    sealed interface NonNullExprType : Tree, NonNullType, ExprType {
        override fun deepCopy(): NonNullExprType
    }

    /**
     * Admits specification abstraction values, not first-class, which are either
     * a passing result or a failing result.
     *
     * The error types are nominal types.
     * There must be one or more of these.
     * If a backend translates result types to first-class result types in the
     * target language, then a result type is only valid if the backend also has
     * a way to convert values in any second and subsequent types to the values in
     * the first so that backends are not required to represent ad-hoc discriminated
     * unions.
     */
    class ResultType(
        pos: Position,
        passType: PassType,
        errTypes: Iterable<RegularTypeNotNull>,
        name: Id,
    ) : BaseTree(pos), NonNullType, RetType {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate35
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.passType
                1 -> FormattableTreeGroup(this.errTypes)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _passType: PassType
        var passType: PassType
            get() = _passType
            set(newValue) { _passType = updateTreeConnection(_passType, newValue) }
        private val _errTypes: MutableList<RegularTypeNotNull> = mutableListOf()
        var errTypes: List<RegularTypeNotNull>
            get() = _errTypes
            set(newValue) { updateTreeConnections(_errTypes, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override val typeActuals: List<NewType>
            get() =
                buildList {
                    add(passType)
                    addAll(errTypes)
                }
        override fun deepCopy(): ResultType {
            return ResultType(pos, passType = this.passType.deepCopy(), errTypes = this.errTypes.deepCopy(), name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ResultType && this.passType == other.passType && this.errTypes == other.errTypes && this.name == other.name
        }
        override fun hashCode(): Int {
            var hc = passType.hashCode()
            hc = 31 * hc + errTypes.hashCode()
            hc = 31 * hc + name.hashCode()
            return hc
        }
        init {
            this._passType = updateTreeConnection(null, passType)
            updateTreeConnections(this._errTypes, errTypes)
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ResultType).passType },
                { n -> (n as ResultType).errTypes },
                { n -> (n as ResultType).name },
            )
        }
    }

    /**
     * A return type for a call expression that indicates that the call completed
     * normally but does not produce a first-class value.  Often because it is called
     * for a sideeffect.
     */
    class VoidType(
        pos: Position,
        name: Id,
    ) : BaseTree(pos), NonNullType, PassType {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (canBeNull) {
                    sharedCodeFormattingTemplate36
                } else {
                    sharedCodeFormattingTemplate37
                }
        override val formatElementCount
            get() = 0
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override val typeActuals: List<NewType>
            get() = emptyList()
        override fun deepCopy(): VoidType {
            return VoidType(pos, name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is VoidType && this.name == other.name
        }
        override fun hashCode(): Int {
            return name.hashCode()
        }
        init {
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as VoidType).name },
            )
        }
    }

    /**
     * An expression type which admits the null value when *canBeNull* is true.
     */
    sealed interface NullableExprType : Tree, NullableType, ExprType {
        override fun deepCopy(): NullableExprType
    }

    /**
     * A parameterized class or interface type.
     * Unlike type arguments which are local to the type definition or function
     * definition that declares them, regular types definition has a global name.
     */
    sealed interface RegularType : Tree, NullableExprType {
        override val typeActuals: List<ExprType>
        override fun deepCopy(): RegularType
    }

    class RegularTypeNotNull(
        pos: Position,
        name: Id,
        typeActuals: Iterable<ExprType>,
    ) : BaseTree(pos), NonNullExprType, RegularType {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private val _typeActuals: MutableList<ExprType> = mutableListOf()
        override var typeActuals: List<ExprType>
            get() = _typeActuals
            set(newValue) { updateTreeConnections(_typeActuals, newValue) }
        override val withoutNull: RegularTypeNotNull
            get() = RegularTypeNotNull(pos, name.deepCopy(), typeActuals.deepCopy())
        override val withNull: RegularTypeOrNull
            get() = RegularTypeOrNull(pos, name.deepCopy(), typeActuals.deepCopy())
        override fun deepCopy(): RegularTypeNotNull {
            return RegularTypeNotNull(pos, name = this.name.deepCopy(), typeActuals = this.typeActuals.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is RegularTypeNotNull && this.name == other.name && this.typeActuals == other.typeActuals
        }
        override fun hashCode(): Int {
            var hc = name.hashCode()
            hc = 31 * hc + typeActuals.hashCode()
            return hc
        }
        init {
            this._name = updateTreeConnection(null, name)
            updateTreeConnections(this._typeActuals, typeActuals)
        }
    }

    /** A reference to a type argument like *\<T>*. */
    sealed interface TypeArg : Tree, NullableExprType {
        override val typeActuals: List<NewType>
            get() = emptyList()
        override fun deepCopy(): TypeArg
    }

    class TypeArgNotNull(
        pos: Position,
        name: Id,
    ) : BaseTree(pos), NonNullExprType, TypeArg {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override val withoutNull: TypeArgNotNull
            get() = TypeArgNotNull(pos, name.deepCopy())
        override val withNull: TypeArgOrNull
            get() = TypeArgOrNull(pos, name.deepCopy())
        override fun deepCopy(): TypeArgNotNull {
            return TypeArgNotNull(pos, name = this.name.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeArgNotNull && this.name == other.name
        }
        override fun hashCode(): Int {
            return name.hashCode()
        }
        init {
            this._name = updateTreeConnection(null, name)
        }
    }

    class TypeArgOrNull(
        pos: Position,
        name: Id,
    ) : BaseTree(pos), TypeArg {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override val canBeNull: Boolean
            get() = true
        override val withoutNull: TypeArgNotNull
            get() = TypeArgNotNull(pos, name.deepCopy())
        override val withNull: TypeArgOrNull
            get() = TypeArgOrNull(pos, name.deepCopy())
        override fun deepCopy(): TypeArgOrNull {
            return TypeArgOrNull(pos, name = this.name.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeArgOrNull && this.name == other.name
        }
        override fun hashCode(): Int {
            return name.hashCode()
        }
        init {
            this._name = updateTreeConnection(null, name)
        }
    }

    sealed interface NullableTypeData : Data, NewTypeData {
        val withoutNull: NullableTypeData
        val withNull: NullableTypeData
    }

    sealed interface RetTypeData : Data, NewTypeData

    sealed interface PassTypeData : Data, RetTypeData

    sealed interface ExprTypeData : Data, PassTypeData {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (typeActuals.isNotEmpty() && canBeNull) {
                    sharedCodeFormattingTemplate32
                } else if (typeActuals.isNotEmpty()) {
                    sharedCodeFormattingTemplate33
                } else if (canBeNull) {
                    sharedCodeFormattingTemplate34
                } else {
                    sharedCodeFormattingTemplate26
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name
                1 -> FormattableTreeGroup(this.typeActuals)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ExprTypeData).name },
                { n -> (n as ExprTypeData).typeActuals },
            )
        }
    }

    sealed interface NullableExprTypeData : Data, NullableTypeData, ExprTypeData

    sealed interface TypeArgData : Data, NullableExprTypeData {
        override val typeActuals: List<NewTypeData>
            get() = emptyList()
    }

    data class TypeArgOrNullData(
        override val sourceLibrary: DashedIdentifier,
        override val name: IdData,
    ) : BaseData(), TypeArgData {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val canBeNull: Boolean
            get() = true
        override val withoutNull: TypeArgNotNullData
            get() = TypeArgNotNullData(sourceLibrary, name)
        override val withNull: TypeArgOrNullData
            get() = this
    }

    sealed interface NonNullTypeData : Data, NewTypeData {
        override val canBeNull: Boolean
            get() = false
    }

    sealed interface NonNullExprTypeData : Data, NonNullTypeData, ExprTypeData

    data class TypeArgNotNullData(
        override val sourceLibrary: DashedIdentifier,
        override val name: IdData,
    ) : BaseData(), NonNullExprTypeData, TypeArgData {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val withoutNull: TypeArgNotNullData
            get() = this
        override val withNull: TypeArgOrNullData
            get() = TypeArgOrNullData(sourceLibrary, name)
    }

    class RegularTypeOrNull(
        pos: Position,
        name: Id,
        typeActuals: Iterable<ExprType>,
    ) : BaseTree(pos), RegularType {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private val _typeActuals: MutableList<ExprType> = mutableListOf()
        override var typeActuals: List<ExprType>
            get() = _typeActuals
            set(newValue) { updateTreeConnections(_typeActuals, newValue) }
        override val canBeNull: Boolean
            get() = true
        override val withoutNull: RegularTypeNotNull
            get() = RegularTypeNotNull(pos, name.deepCopy(), typeActuals.deepCopy())
        override val withNull: RegularTypeOrNull
            get() = RegularTypeOrNull(pos, name.deepCopy(), typeActuals.deepCopy())
        override fun deepCopy(): RegularTypeOrNull {
            return RegularTypeOrNull(pos, name = this.name.deepCopy(), typeActuals = this.typeActuals.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is RegularTypeOrNull && this.name == other.name && this.typeActuals == other.typeActuals
        }
        override fun hashCode(): Int {
            var hc = name.hashCode()
            hc = 31 * hc + typeActuals.hashCode()
            return hc
        }
        init {
            this._name = updateTreeConnection(null, name)
            updateTreeConnections(this._typeActuals, typeActuals)
        }
    }

    sealed interface RegularTypeData : Data, NullableExprTypeData {
        override val typeActuals: List<ExprTypeData>
    }

    data class RegularTypeOrNullData(
        override val sourceLibrary: DashedIdentifier,
        override val name: IdData,
        override val typeActuals: List<ExprTypeData>,
    ) : BaseData(), RegularTypeData {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val canBeNull: Boolean
            get() = true
        override val withoutNull: RegularTypeNotNullData
            get() = RegularTypeNotNullData(sourceLibrary, name, typeActuals)
        override val withNull: RegularTypeOrNullData
            get() = this
    }

    data class RegularTypeNotNullData(
        override val sourceLibrary: DashedIdentifier,
        override val name: IdData,
        override val typeActuals: List<ExprTypeData>,
    ) : BaseData(), NonNullExprTypeData, RegularTypeData {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val withoutNull: RegularTypeNotNullData
            get() = this
        override val withNull: RegularTypeOrNullData
            get() = RegularTypeOrNullData(sourceLibrary, name, typeActuals)
    }

    data class MetadataKey(
        override val sourceLibrary: DashedIdentifier,
        val symbol: Symbol,
    ) : BaseData() {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(OutputToken("@" + symbol.text, OutputTokenType.Name))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    sealed interface MetadataValue : Data

    data class ValueData(
        override val sourceLibrary: DashedIdentifier,
        val value: Value<*>,
    ) : BaseData(), MetadataValue {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            value.renderTo(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    data class NameData(
        override val sourceLibrary: DashedIdentifier,
        val qName: QName,
    ) : BaseData(), MetadataValue {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate26
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> IndexableFormattableTreeElement.wrap(this.qName)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class MemberName(
        pos: Position,
        containingType: Id,
        dotName: DotName,
    ) : BaseTree(pos), DefName {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate38
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.containingType
                1 -> this.dotName
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _containingType: Id
        var containingType: Id
            get() = _containingType
            set(newValue) { _containingType = updateTreeConnection(_containingType, newValue) }
        private var _dotName: DotName
        var dotName: DotName
            get() = _dotName
            set(newValue) { _dotName = updateTreeConnection(_dotName, newValue) }
        override fun deepCopy(): MemberName {
            return MemberName(pos, containingType = this.containingType.deepCopy(), dotName = this.dotName.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MemberName && this.containingType == other.containingType && this.dotName == other.dotName
        }
        override fun hashCode(): Int {
            var hc = containingType.hashCode()
            hc = 31 * hc + dotName.hashCode()
            return hc
        }
        init {
            this._containingType = updateTreeConnection(null, containingType)
            this._dotName = updateTreeConnection(null, dotName)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as MemberName).containingType },
                { n -> (n as MemberName).dotName },
            )
        }
    }

    /**
     * AnyDef represents a definition associated with a name.
     * It specifies a name and associated declaration metadata and type info like a signature.
     * It does not include implementation information like an initializer expression or
     * complete function bodies.
     */
    sealed interface AnyDef : Tree {
        val metadata: DeclarationMetadata
        val name: DefName
        val qName: NameData?
        val visibility: Visibility?
        val isStatic: Boolean
            get() = this is StaticMemberDef
        override fun deepCopy(): AnyDef
    }

    /**
     * Associates a name with a type definition.
     * In the case of a [formal type definition][FormalTypeDef], like `<T>`, the name is local.
     * In the case of a [class or interface definition][TypeDef], the name is global.
     */
    sealed interface AnyTypeDef : Tree, AnyDef {
        override val name: Id
        val qualifiers: TypeQualifiers
        override fun deepCopy(): AnyTypeDef
    }

    /** A definition that defines a calling convention: type parameters, value, parameters, and returns. */
    sealed interface FunctionLikeDef : Tree, AnyDef {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (isStatic && visibility != null && qName != null && skeletalBody != null) {
                    sharedCodeFormattingTemplate39
                } else if (isStatic && visibility != null && qName != null) {
                    sharedCodeFormattingTemplate40
                } else if (isStatic && visibility != null && skeletalBody != null) {
                    sharedCodeFormattingTemplate41
                } else if (isStatic && visibility != null) {
                    sharedCodeFormattingTemplate42
                } else if (isStatic && qName != null && skeletalBody != null) {
                    sharedCodeFormattingTemplate43
                } else if (isStatic && qName != null) {
                    sharedCodeFormattingTemplate44
                } else if (isStatic && skeletalBody != null) {
                    sharedCodeFormattingTemplate45
                } else if (isStatic) {
                    sharedCodeFormattingTemplate46
                } else if (visibility != null && qName != null && skeletalBody != null) {
                    sharedCodeFormattingTemplate47
                } else if (visibility != null && qName != null) {
                    sharedCodeFormattingTemplate48
                } else if (visibility != null && skeletalBody != null) {
                    sharedCodeFormattingTemplate49
                } else if (visibility != null) {
                    sharedCodeFormattingTemplate50
                } else if (qName != null && skeletalBody != null) {
                    sharedCodeFormattingTemplate51
                } else if (qName != null) {
                    sharedCodeFormattingTemplate52
                } else if (skeletalBody != null) {
                    sharedCodeFormattingTemplate53
                } else {
                    sharedCodeFormattingTemplate54
                }
        override val formatElementCount
            get() = 8
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.metadata
                1 -> this.visibility ?: FormattableTreeGroup.empty
                2 -> this.qName ?: FormattableTreeGroup.empty
                3 -> this.name
                4 -> this.typeParameters
                5 -> this.args
                6 -> this.retType
                7 -> this.skeletalBody ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        val typeParameters: FormalTypeDefs
        val args: FormalArgTypes
        val retType: RetType
        val skeletalBody: SkeletalBody?
        override fun deepCopy(): FunctionLikeDef
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FunctionLikeDef).name },
                { n -> (n as FunctionLikeDef).typeParameters },
                { n -> (n as FunctionLikeDef).args },
                { n -> (n as FunctionLikeDef).retType },
                { n -> (n as FunctionLikeDef).skeletalBody },
            )
        }
    }

    sealed interface ModuleLevelDef : Tree, AnyDef {
        override val name: Id
        override fun deepCopy(): ModuleLevelDef
    }

    /** A definition that uses an identifier as a name, not a type member */
    sealed interface SimpleDef : Tree, AnyDef, ModuleLevelDef {
        override val name: Id
        override fun deepCopy(): SimpleDef
    }

    sealed interface VariableOrPropertyDef : Tree, AnyDef {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (isStatic && visibility != null && qName != null) {
                    sharedCodeFormattingTemplate55
                } else if (isStatic && visibility != null) {
                    sharedCodeFormattingTemplate56
                } else if (isStatic && qName != null) {
                    sharedCodeFormattingTemplate57
                } else if (isStatic) {
                    sharedCodeFormattingTemplate58
                } else if (visibility != null && qName != null) {
                    sharedCodeFormattingTemplate59
                } else if (visibility != null) {
                    sharedCodeFormattingTemplate60
                } else if (qName != null) {
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
                0 -> this.metadata
                1 -> this.visibility ?: FormattableTreeGroup.empty
                2 -> this.qName ?: FormattableTreeGroup.empty
                3 -> this.name
                4 -> this.type
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        val type: ExprType
        override fun deepCopy(): VariableOrPropertyDef
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as VariableOrPropertyDef).name },
                { n -> (n as VariableOrPropertyDef).type },
            )
        }
    }

    sealed interface MemberDef : Tree, AnyDef {
        override val name: MemberName
        val declaringTypeName: Id

        /** Convenience for getting the dot name from the member name. */
        val dotName: DotName
            get() = name.dotName
        override fun deepCopy(): MemberDef
    }

    sealed interface AnyDefData : Data {
        val isStatic: Boolean
            get() = this is StaticMemberDefData
        val metadata: DeclarationMetadata
        val name: DefNameData
        val qName: NameData?
        val visibility: Visibility?
    }

    sealed interface AnyMethodDef : Tree, FunctionLikeDef, MemberDef {
        override val name: MemberName
        override fun deepCopy(): AnyMethodDef
    }

    class FunctionDef(
        pos: Position,
        metadata: DeclarationMetadata,
        qName: NameData?,
        name: Id,
        typeParameters: FormalTypeDefs,
        args: FormalArgTypes,
        retType: RetType,
        skeletalBody: SkeletalBody?,
    ) : BaseTree(pos), FunctionLikeDef, SimpleDef {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        private var _metadata: DeclarationMetadata
        override var metadata: DeclarationMetadata
            get() = _metadata
            set(newValue) { _metadata = newValue }
        private var _qName: NameData?
        override var qName: NameData?
            get() = _qName
            set(newValue) { _qName = newValue }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _typeParameters: FormalTypeDefs
        override var typeParameters: FormalTypeDefs
            get() = _typeParameters
            set(newValue) { _typeParameters = updateTreeConnection(_typeParameters, newValue) }
        private var _args: FormalArgTypes
        override var args: FormalArgTypes
            get() = _args
            set(newValue) { _args = updateTreeConnection(_args, newValue) }
        private var _retType: RetType
        override var retType: RetType
            get() = _retType
            set(newValue) { _retType = updateTreeConnection(_retType, newValue) }
        private var _skeletalBody: SkeletalBody?
        override var skeletalBody: SkeletalBody?
            get() = _skeletalBody
            set(newValue) { _skeletalBody = updateTreeConnection(_skeletalBody, newValue) }
        override val visibility: Visibility?
            get() = null
        override fun deepCopy(): FunctionDef {
            return FunctionDef(pos, metadata = this.metadata, qName = this.qName, name = this.name.deepCopy(), typeParameters = this.typeParameters.deepCopy(), args = this.args.deepCopy(), retType = this.retType.deepCopy(), skeletalBody = this.skeletalBody?.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FunctionDef && this.metadata == other.metadata && this.qName == other.qName && this.name == other.name && this.typeParameters == other.typeParameters && this.args == other.args && this.retType == other.retType && this.skeletalBody == other.skeletalBody
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + (qName?.hashCode() ?: 0)
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + typeParameters.hashCode()
            hc = 31 * hc + args.hashCode()
            hc = 31 * hc + retType.hashCode()
            hc = 31 * hc + (skeletalBody?.hashCode() ?: 0)
            return hc
        }
        init {
            this._metadata = metadata
            this._qName = qName
            this._name = updateTreeConnection(null, name)
            this._typeParameters = updateTreeConnection(null, typeParameters)
            this._args = updateTreeConnection(null, args)
            this._retType = updateTreeConnection(null, retType)
            this._skeletalBody = updateTreeConnection(null, skeletalBody)
        }
    }

    class VariableDef(
        pos: Position,
        metadata: DeclarationMetadata,
        qName: NameData?,
        name: Id,
        type: ExprType,
    ) : BaseTree(pos), SimpleDef, VariableOrPropertyDef {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        private var _metadata: DeclarationMetadata
        override var metadata: DeclarationMetadata
            get() = _metadata
            set(newValue) { _metadata = newValue }
        private var _qName: NameData?
        override var qName: NameData?
            get() = _qName
            set(newValue) { _qName = newValue }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _type: ExprType
        override var type: ExprType
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        override val visibility: Visibility?
            get() = null
        override fun deepCopy(): VariableDef {
            return VariableDef(pos, metadata = this.metadata, qName = this.qName, name = this.name.deepCopy(), type = this.type.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is VariableDef && this.metadata == other.metadata && this.qName == other.qName && this.name == other.name && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + (qName?.hashCode() ?: 0)
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + type.hashCode()
            return hc
        }
        init {
            this._metadata = metadata
            this._qName = qName
            this._name = updateTreeConnection(null, name)
            this._type = updateTreeConnection(null, type)
        }
    }

    /** A class or interface definition */
    class TypeDef(
        pos: Position,
        metadata: DeclarationMetadata,
        var kind: TypeDefKind,
        qName: NameData?,
        name: Id,
        formals: FormalTypeDefs,
        qualifiers: TypeQualifiers,
        members: Iterable<MemberDef>,
        override var visibility: Visibility?,
    ) : BaseTree(pos), ModuleLevelDef, AnyTypeDef {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (qName != null) {
                    sharedCodeFormattingTemplate63
                } else {
                    sharedCodeFormattingTemplate64
                }
        override val formatElementCount
            get() = 7
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.metadata
                1 -> this.kind
                2 -> this.qName ?: FormattableTreeGroup.empty
                3 -> this.name
                4 -> this.formals
                5 -> this.qualifiers
                6 -> FormattableTreeGroup(this.members)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _metadata: DeclarationMetadata
        override var metadata: DeclarationMetadata
            get() = _metadata
            set(newValue) { _metadata = newValue }
        private var _qName: NameData?
        override var qName: NameData?
            get() = _qName
            set(newValue) { _qName = newValue }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _formals: FormalTypeDefs
        var formals: FormalTypeDefs
            get() = _formals
            set(newValue) { _formals = updateTreeConnection(_formals, newValue) }
        private var _qualifiers: TypeQualifiers
        override var qualifiers: TypeQualifiers
            get() = _qualifiers
            set(newValue) { _qualifiers = updateTreeConnection(_qualifiers, newValue) }
        private val _members: MutableList<MemberDef> = mutableListOf()
        var members: List<MemberDef>
            get() = _members
            set(newValue) { updateTreeConnections(_members, newValue) }
        override fun deepCopy(): TypeDef {
            return TypeDef(pos, metadata = this.metadata, kind = this.kind, qName = this.qName, name = this.name.deepCopy(), formals = this.formals.deepCopy(), qualifiers = this.qualifiers.deepCopy(), members = this.members.deepCopy(), visibility = this.visibility)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeDef && this.metadata == other.metadata && this.kind == other.kind && this.qName == other.qName && this.name == other.name && this.formals == other.formals && this.qualifiers == other.qualifiers && this.members == other.members && this.visibility == other.visibility
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + kind.hashCode()
            hc = 31 * hc + (qName?.hashCode() ?: 0)
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + formals.hashCode()
            hc = 31 * hc + qualifiers.hashCode()
            hc = 31 * hc + members.hashCode()
            hc = 31 * hc + (visibility?.hashCode() ?: 0)
            return hc
        }
        init {
            this._metadata = metadata
            this._qName = qName
            this._name = updateTreeConnection(null, name)
            this._formals = updateTreeConnection(null, formals)
            this._qualifiers = updateTreeConnection(null, qualifiers)
            updateTreeConnections(this._members, members)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeDef).name },
                { n -> (n as TypeDef).formals },
                { n -> (n as TypeDef).qualifiers },
                { n -> (n as TypeDef).members },
            )
        }
        val methods get() = members.mapNotNull { it as? MethodDef }
        val properties get() = members.mapNotNull { it as? PropertyDef }
        val staticMethods get() = members.mapNotNull { it as? StaticMethodDef }
        val staticProperties get() = members.mapNotNull { it as? StaticPropertyDef }

        /** Instance methods and static methods */
        val allMethods get() = members.mapNotNull { it as? AnyMethodDef }

        /** Instance properties and static properties */
        val allProperties get() = members.mapNotNull { it as? AnyPropertyDef }
    }

    sealed interface AnyPropertyDef : Tree, VariableOrPropertyDef, MemberDef {
        override val name: MemberName
        override fun deepCopy(): AnyPropertyDef
    }

    class FormalArgTypes(
        pos: Position,
        argTypes: Iterable<ExprType>,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate65
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.argTypes)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _argTypes: MutableList<ExprType> = mutableListOf()
        var argTypes: List<ExprType>
            get() = _argTypes
            set(newValue) { updateTreeConnections(_argTypes, newValue) }
        override fun deepCopy(): FormalArgTypes {
            return FormalArgTypes(pos, argTypes = this.argTypes.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FormalArgTypes && this.argTypes == other.argTypes
        }
        override fun hashCode(): Int {
            return argTypes.hashCode()
        }
        init {
            updateTreeConnections(this._argTypes, argTypes)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FormalArgTypes).argTypes },
            )
        }
    }

    /**
     * A skeletal body def includes nested definitions but no implementation details.
     *
     * This allows reasoning about name masking and overloading and overriding based on
     * an analysis of definitions.
     */
    class SkeletalBody(
        pos: Position,
        defs: Iterable<SimpleDef>,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate66
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.defs)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _defs: MutableList<SimpleDef> = mutableListOf()
        var defs: List<SimpleDef>
            get() = _defs
            set(newValue) { updateTreeConnections(_defs, newValue) }
        override fun deepCopy(): SkeletalBody {
            return SkeletalBody(pos, defs = this.defs.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SkeletalBody && this.defs == other.defs
        }
        override fun hashCode(): Int {
            return defs.hashCode()
        }
        init {
            updateTreeConnections(this._defs, defs)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SkeletalBody).defs },
            )
        }
    }

    class FormalTypeDef(
        pos: Position,
        metadata: DeclarationMetadata,
        qName: NameData?,
        name: Id,
        qualifiers: TypeQualifiers,
        override var visibility: Visibility?,
    ) : BaseTree(pos), AnyTypeDef {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (qName != null) {
                    sharedCodeFormattingTemplate67
                } else {
                    sharedCodeFormattingTemplate68
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.metadata
                1 -> this.qName ?: FormattableTreeGroup.empty
                2 -> this.name
                3 -> this.qualifiers
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _metadata: DeclarationMetadata
        override var metadata: DeclarationMetadata
            get() = _metadata
            set(newValue) { _metadata = newValue }
        private var _qName: NameData?
        override var qName: NameData?
            get() = _qName
            set(newValue) { _qName = newValue }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _qualifiers: TypeQualifiers
        override var qualifiers: TypeQualifiers
            get() = _qualifiers
            set(newValue) { _qualifiers = updateTreeConnection(_qualifiers, newValue) }
        override fun deepCopy(): FormalTypeDef {
            return FormalTypeDef(pos, metadata = this.metadata, qName = this.qName, name = this.name.deepCopy(), qualifiers = this.qualifiers.deepCopy(), visibility = this.visibility)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FormalTypeDef && this.metadata == other.metadata && this.qName == other.qName && this.name == other.name && this.qualifiers == other.qualifiers && this.visibility == other.visibility
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + (qName?.hashCode() ?: 0)
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + qualifiers.hashCode()
            hc = 31 * hc + (visibility?.hashCode() ?: 0)
            return hc
        }
        init {
            this._metadata = metadata
            this._qName = qName
            this._name = updateTreeConnection(null, name)
            this._qualifiers = updateTreeConnection(null, qualifiers)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FormalTypeDef).name },
                { n -> (n as FormalTypeDef).qualifiers },
            )
        }
    }

    class TypeQualifiers(
        pos: Position,
        extendsClauses: Iterable<NonNullExprType>,
        supportsClauses: RegularTypeNotNull?,
        forbidsClauses: Iterable<NewType>,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (extendsClauses.isNotEmpty() && supportsClauses != null && forbidsClauses.isNotEmpty()) {
                    sharedCodeFormattingTemplate69
                } else if (extendsClauses.isNotEmpty() && supportsClauses != null) {
                    sharedCodeFormattingTemplate70
                } else if (extendsClauses.isNotEmpty() && forbidsClauses.isNotEmpty()) {
                    sharedCodeFormattingTemplate71
                } else if (extendsClauses.isNotEmpty()) {
                    sharedCodeFormattingTemplate72
                } else if (supportsClauses != null && forbidsClauses.isNotEmpty()) {
                    sharedCodeFormattingTemplate73
                } else if (supportsClauses != null) {
                    sharedCodeFormattingTemplate74
                } else if (forbidsClauses.isNotEmpty()) {
                    sharedCodeFormattingTemplate75
                } else {
                    sharedCodeFormattingTemplate31
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.extendsClauses)
                1 -> this.supportsClauses ?: FormattableTreeGroup.empty
                2 -> FormattableTreeGroup(this.forbidsClauses)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _extendsClauses: MutableList<NonNullExprType> = mutableListOf()
        var extendsClauses: List<NonNullExprType>
            get() = _extendsClauses
            set(newValue) { updateTreeConnections(_extendsClauses, newValue) }
        private var _supportsClauses: RegularTypeNotNull?
        var supportsClauses: RegularTypeNotNull?
            get() = _supportsClauses
            set(newValue) { _supportsClauses = updateTreeConnection(_supportsClauses, newValue) }
        private val _forbidsClauses: MutableList<NewType> = mutableListOf()
        var forbidsClauses: List<NewType>
            get() = _forbidsClauses
            set(newValue) { updateTreeConnections(_forbidsClauses, newValue) }
        override fun deepCopy(): TypeQualifiers {
            return TypeQualifiers(pos, extendsClauses = this.extendsClauses.deepCopy(), supportsClauses = this.supportsClauses?.deepCopy(), forbidsClauses = this.forbidsClauses.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeQualifiers && this.extendsClauses == other.extendsClauses && this.supportsClauses == other.supportsClauses && this.forbidsClauses == other.forbidsClauses
        }
        override fun hashCode(): Int {
            var hc = extendsClauses.hashCode()
            hc = 31 * hc + (supportsClauses?.hashCode() ?: 0)
            hc = 31 * hc + forbidsClauses.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._extendsClauses, extendsClauses)
            this._supportsClauses = updateTreeConnection(null, supportsClauses)
            updateTreeConnections(this._forbidsClauses, forbidsClauses)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeQualifiers).extendsClauses },
                { n -> (n as TypeQualifiers).supportsClauses },
                { n -> (n as TypeQualifiers).forbidsClauses },
            )
        }
    }

    sealed interface ModuleLevelDefData : Data, AnyDefData {
        override val name: IdData
    }

    sealed interface AnyTypeDefData : Data, AnyDefData {
        override val name: IdData
        val qualifiers: TypeQualifiersData
    }

    data class TypeDefData(
        override val sourceLibrary: DashedIdentifier,
        override val metadata: DeclarationMetadata,
        val kind: TypeDefKind,
        override val qName: NameData?,
        override val name: IdData,
        val formals: FormalTypeDefsData,
        override val qualifiers: TypeQualifiersData,
        val members: List<MemberDefData>,
        override val visibility: Visibility?,
    ) : BaseData(), ModuleLevelDefData, AnyTypeDefData {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (qName != null) {
                    sharedCodeFormattingTemplate63
                } else {
                    sharedCodeFormattingTemplate64
                }
        override val formatElementCount
            get() = 7
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.metadata
                1 -> this.kind
                2 -> this.qName ?: FormattableTreeGroup.empty
                3 -> this.name
                4 -> this.formals
                5 -> this.qualifiers
                6 -> FormattableTreeGroup(this.members)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeDefData).metadata },
                { n -> (n as TypeDefData).qName },
                { n -> (n as TypeDefData).name },
                { n -> (n as TypeDefData).formals },
                { n -> (n as TypeDefData).qualifiers },
                { n -> (n as TypeDefData).members },
            )
        }
        val methods get() = members.mapNotNull { it as? MethodDefData }
        val properties get() = members.mapNotNull { it as? PropertyDefData }
        val staticMethods get() = members.mapNotNull { it as? StaticMethodDefData }
        val staticProperties get() = members.mapNotNull { it as? StaticPropertyDefData }

        /** Instance methods and static methods */
        val allMethods get() = members.mapNotNull { it as? AnyMethodDefData }

        /** Instance properties and static properties */
        val allProperties get() = members.mapNotNull { it as? AnyPropertyDefData }
    }

    sealed interface InstanceMemberDef : Tree, MemberDef {
        override fun deepCopy(): InstanceMemberDef
    }

    /** An instance method signature */
    class MethodDef(
        pos: Position,
        metadata: DeclarationMetadata,
        override var visibility: Visibility?,
        qName: NameData?,
        name: MemberName,
        typeParameters: FormalTypeDefs,
        args: FormalArgTypes,
        retType: RetType,
        skeletalBody: SkeletalBody?,
        declaringTypeName: Id,
    ) : BaseTree(pos), MemberDef, AnyMethodDef, InstanceMemberDef {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        private var _metadata: DeclarationMetadata
        override var metadata: DeclarationMetadata
            get() = _metadata
            set(newValue) { _metadata = newValue }
        private var _qName: NameData?
        override var qName: NameData?
            get() = _qName
            set(newValue) { _qName = newValue }
        private var _name: MemberName
        override var name: MemberName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _typeParameters: FormalTypeDefs
        override var typeParameters: FormalTypeDefs
            get() = _typeParameters
            set(newValue) { _typeParameters = updateTreeConnection(_typeParameters, newValue) }
        private var _args: FormalArgTypes
        override var args: FormalArgTypes
            get() = _args
            set(newValue) { _args = updateTreeConnection(_args, newValue) }
        private var _retType: RetType
        override var retType: RetType
            get() = _retType
            set(newValue) { _retType = updateTreeConnection(_retType, newValue) }
        private var _skeletalBody: SkeletalBody?
        override var skeletalBody: SkeletalBody?
            get() = _skeletalBody
            set(newValue) { _skeletalBody = updateTreeConnection(_skeletalBody, newValue) }
        private var _declaringTypeName: Id
        override var declaringTypeName: Id
            get() = _declaringTypeName
            set(newValue) { _declaringTypeName = updateTreeConnection(_declaringTypeName, newValue) }
        override fun deepCopy(): MethodDef {
            return MethodDef(pos, metadata = this.metadata, visibility = this.visibility, qName = this.qName, name = this.name.deepCopy(), typeParameters = this.typeParameters.deepCopy(), args = this.args.deepCopy(), retType = this.retType.deepCopy(), skeletalBody = this.skeletalBody?.deepCopy(), declaringTypeName = this.declaringTypeName.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MethodDef && this.metadata == other.metadata && this.visibility == other.visibility && this.qName == other.qName && this.name == other.name && this.typeParameters == other.typeParameters && this.args == other.args && this.retType == other.retType && this.skeletalBody == other.skeletalBody && this.declaringTypeName == other.declaringTypeName
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + (visibility?.hashCode() ?: 0)
            hc = 31 * hc + (qName?.hashCode() ?: 0)
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + typeParameters.hashCode()
            hc = 31 * hc + args.hashCode()
            hc = 31 * hc + retType.hashCode()
            hc = 31 * hc + (skeletalBody?.hashCode() ?: 0)
            hc = 31 * hc + declaringTypeName.hashCode()
            return hc
        }
        init {
            this._metadata = metadata
            this._qName = qName
            this._name = updateTreeConnection(null, name)
            this._typeParameters = updateTreeConnection(null, typeParameters)
            this._args = updateTreeConnection(null, args)
            this._retType = updateTreeConnection(null, retType)
            this._skeletalBody = updateTreeConnection(null, skeletalBody)
            this._declaringTypeName = updateTreeConnection(null, declaringTypeName)
        }
    }

    sealed interface StaticMemberDef : Tree, MemberDef {
        override fun deepCopy(): StaticMemberDef
    }

    /** A static method signature */
    class StaticMethodDef(
        pos: Position,
        metadata: DeclarationMetadata,
        override var visibility: Visibility?,
        qName: NameData?,
        name: MemberName,
        typeParameters: FormalTypeDefs,
        args: FormalArgTypes,
        retType: RetType,
        skeletalBody: SkeletalBody?,
        declaringTypeName: Id,
    ) : BaseTree(pos), MemberDef, AnyMethodDef, StaticMemberDef {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        private var _metadata: DeclarationMetadata
        override var metadata: DeclarationMetadata
            get() = _metadata
            set(newValue) { _metadata = newValue }
        private var _qName: NameData?
        override var qName: NameData?
            get() = _qName
            set(newValue) { _qName = newValue }
        private var _name: MemberName
        override var name: MemberName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _typeParameters: FormalTypeDefs
        override var typeParameters: FormalTypeDefs
            get() = _typeParameters
            set(newValue) { _typeParameters = updateTreeConnection(_typeParameters, newValue) }
        private var _args: FormalArgTypes
        override var args: FormalArgTypes
            get() = _args
            set(newValue) { _args = updateTreeConnection(_args, newValue) }
        private var _retType: RetType
        override var retType: RetType
            get() = _retType
            set(newValue) { _retType = updateTreeConnection(_retType, newValue) }
        private var _skeletalBody: SkeletalBody?
        override var skeletalBody: SkeletalBody?
            get() = _skeletalBody
            set(newValue) { _skeletalBody = updateTreeConnection(_skeletalBody, newValue) }
        private var _declaringTypeName: Id
        override var declaringTypeName: Id
            get() = _declaringTypeName
            set(newValue) { _declaringTypeName = updateTreeConnection(_declaringTypeName, newValue) }
        override fun deepCopy(): StaticMethodDef {
            return StaticMethodDef(pos, metadata = this.metadata, visibility = this.visibility, qName = this.qName, name = this.name.deepCopy(), typeParameters = this.typeParameters.deepCopy(), args = this.args.deepCopy(), retType = this.retType.deepCopy(), skeletalBody = this.skeletalBody?.deepCopy(), declaringTypeName = this.declaringTypeName.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StaticMethodDef && this.metadata == other.metadata && this.visibility == other.visibility && this.qName == other.qName && this.name == other.name && this.typeParameters == other.typeParameters && this.args == other.args && this.retType == other.retType && this.skeletalBody == other.skeletalBody && this.declaringTypeName == other.declaringTypeName
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + (visibility?.hashCode() ?: 0)
            hc = 31 * hc + (qName?.hashCode() ?: 0)
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + typeParameters.hashCode()
            hc = 31 * hc + args.hashCode()
            hc = 31 * hc + retType.hashCode()
            hc = 31 * hc + (skeletalBody?.hashCode() ?: 0)
            hc = 31 * hc + declaringTypeName.hashCode()
            return hc
        }
        init {
            this._metadata = metadata
            this._qName = qName
            this._name = updateTreeConnection(null, name)
            this._typeParameters = updateTreeConnection(null, typeParameters)
            this._args = updateTreeConnection(null, args)
            this._retType = updateTreeConnection(null, retType)
            this._skeletalBody = updateTreeConnection(null, skeletalBody)
            this._declaringTypeName = updateTreeConnection(null, declaringTypeName)
        }
    }

    /** An instance property signature */
    class PropertyDef(
        pos: Position,
        metadata: DeclarationMetadata,
        override var visibility: Visibility?,
        qName: NameData?,
        name: MemberName,
        type: ExprType,
        declaringTypeName: Id,
    ) : BaseTree(pos), MemberDef, AnyPropertyDef, InstanceMemberDef {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        private var _metadata: DeclarationMetadata
        override var metadata: DeclarationMetadata
            get() = _metadata
            set(newValue) { _metadata = newValue }
        private var _qName: NameData?
        override var qName: NameData?
            get() = _qName
            set(newValue) { _qName = newValue }
        private var _name: MemberName
        override var name: MemberName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _type: ExprType
        override var type: ExprType
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _declaringTypeName: Id
        override var declaringTypeName: Id
            get() = _declaringTypeName
            set(newValue) { _declaringTypeName = updateTreeConnection(_declaringTypeName, newValue) }
        override fun deepCopy(): PropertyDef {
            return PropertyDef(pos, metadata = this.metadata, visibility = this.visibility, qName = this.qName, name = this.name.deepCopy(), type = this.type.deepCopy(), declaringTypeName = this.declaringTypeName.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PropertyDef && this.metadata == other.metadata && this.visibility == other.visibility && this.qName == other.qName && this.name == other.name && this.type == other.type && this.declaringTypeName == other.declaringTypeName
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + (visibility?.hashCode() ?: 0)
            hc = 31 * hc + (qName?.hashCode() ?: 0)
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + declaringTypeName.hashCode()
            return hc
        }
        init {
            this._metadata = metadata
            this._qName = qName
            this._name = updateTreeConnection(null, name)
            this._type = updateTreeConnection(null, type)
            this._declaringTypeName = updateTreeConnection(null, declaringTypeName)
        }
    }

    /** A static property signature */
    class StaticPropertyDef(
        pos: Position,
        metadata: DeclarationMetadata,
        override var visibility: Visibility?,
        qName: NameData?,
        name: MemberName,
        type: ExprType,
        declaringTypeName: Id,
    ) : BaseTree(pos), MemberDef, AnyPropertyDef, StaticMemberDef {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        private var _metadata: DeclarationMetadata
        override var metadata: DeclarationMetadata
            get() = _metadata
            set(newValue) { _metadata = newValue }
        private var _qName: NameData?
        override var qName: NameData?
            get() = _qName
            set(newValue) { _qName = newValue }
        private var _name: MemberName
        override var name: MemberName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _type: ExprType
        override var type: ExprType
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _declaringTypeName: Id
        override var declaringTypeName: Id
            get() = _declaringTypeName
            set(newValue) { _declaringTypeName = updateTreeConnection(_declaringTypeName, newValue) }
        override fun deepCopy(): StaticPropertyDef {
            return StaticPropertyDef(pos, metadata = this.metadata, visibility = this.visibility, qName = this.qName, name = this.name.deepCopy(), type = this.type.deepCopy(), declaringTypeName = this.declaringTypeName.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StaticPropertyDef && this.metadata == other.metadata && this.visibility == other.visibility && this.qName == other.qName && this.name == other.name && this.type == other.type && this.declaringTypeName == other.declaringTypeName
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + (visibility?.hashCode() ?: 0)
            hc = 31 * hc + (qName?.hashCode() ?: 0)
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + declaringTypeName.hashCode()
            return hc
        }
        init {
            this._metadata = metadata
            this._qName = qName
            this._name = updateTreeConnection(null, name)
            this._type = updateTreeConnection(null, type)
            this._declaringTypeName = updateTreeConnection(null, declaringTypeName)
        }
    }

    class SkeletalModule(
        pos: Position,
        defs: Iterable<ModuleLevelDef>,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate76
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.defs)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _defs: MutableList<ModuleLevelDef> = mutableListOf()
        var defs: List<ModuleLevelDef>
            get() = _defs
            set(newValue) { updateTreeConnections(_defs, newValue) }
        override fun deepCopy(): SkeletalModule {
            return SkeletalModule(pos, defs = this.defs.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SkeletalModule && this.defs == other.defs
        }
        override fun hashCode(): Int {
            return defs.hashCode()
        }
        init {
            updateTreeConnections(this._defs, defs)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SkeletalModule).defs },
            )
        }
    }

    data class SkeletalModuleData(
        override val sourceLibrary: DashedIdentifier,
        val defs: List<ModuleLevelDefData>,
    ) : BaseData() {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate76
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.defs)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SkeletalModuleData).defs },
            )
        }
    }

    /**
     * If we can't convert something from lispy form, it's garbage.
     * Backends should make a good effort, for production builds, to turn
     * these into something that allows some tests to proceed but spike
     * the ones that depend on garbage.
     */
    sealed interface Garbage : Tree {
        val diagnostic: Diagnostic?
        override fun deepCopy(): Garbage
    }

    class GarbageTopLevel(
        pos: Position,
        diagnostic: Diagnostic? = null,
    ) : BaseTree(pos), Garbage, TopLevel {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (diagnostic != null) {
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
                0 -> this.diagnostic ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _diagnostic: Diagnostic?
        override var diagnostic: Diagnostic?
            get() = _diagnostic
            set(newValue) { _diagnostic = updateTreeConnection(_diagnostic, newValue) }
        override fun deepCopy(): GarbageTopLevel {
            return GarbageTopLevel(pos, diagnostic = this.diagnostic?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is GarbageTopLevel && this.diagnostic == other.diagnostic
        }
        override fun hashCode(): Int {
            return (diagnostic?.hashCode() ?: 0)
        }
        init {
            this._diagnostic = updateTreeConnection(null, diagnostic)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as GarbageTopLevel).diagnostic },
            )
        }
        constructor(diagnostic: Diagnostic) : this(diagnostic.pos, diagnostic)
    }

    sealed interface Statement : Tree, StatementOrTopLevel {
        override fun deepCopy(): Statement
    }

    sealed interface MemberOrGarbage : Tree {
        override fun deepCopy(): MemberOrGarbage
    }

    class GarbageStatement(
        pos: Position,
        diagnostic: Diagnostic? = null,
    ) : BaseTree(pos), Garbage, Statement, MemberOrGarbage {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (diagnostic != null) {
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
                0 -> this.diagnostic ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _diagnostic: Diagnostic?
        override var diagnostic: Diagnostic?
            get() = _diagnostic
            set(newValue) { _diagnostic = updateTreeConnection(_diagnostic, newValue) }
        override fun deepCopy(): GarbageStatement {
            return GarbageStatement(pos, diagnostic = this.diagnostic?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is GarbageStatement && this.diagnostic == other.diagnostic
        }
        override fun hashCode(): Int {
            return (diagnostic?.hashCode() ?: 0)
        }
        init {
            this._diagnostic = updateTreeConnection(null, diagnostic)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as GarbageStatement).diagnostic },
            )
        }
        constructor(diagnostic: Diagnostic) : this(diagnostic.pos, diagnostic)
    }

    class GarbageExpression(
        pos: Position,
        diagnostic: Diagnostic? = null,
    ) : BaseTree(pos), Garbage, Expression {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (diagnostic != null) {
                    sharedCodeFormattingTemplate81
                } else {
                    sharedCodeFormattingTemplate82
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.diagnostic ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _diagnostic: Diagnostic?
        override var diagnostic: Diagnostic?
            get() = _diagnostic
            set(newValue) { _diagnostic = updateTreeConnection(_diagnostic, newValue) }
        override val type: Type2
            get() = WellKnownTypes.invalidType2
        override fun deepCopy(): GarbageExpression {
            return GarbageExpression(pos, diagnostic = this.diagnostic?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is GarbageExpression && this.diagnostic == other.diagnostic
        }
        override fun hashCode(): Int {
            return (diagnostic?.hashCode() ?: 0)
        }
        init {
            this._diagnostic = updateTreeConnection(null, diagnostic)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as GarbageExpression).diagnostic },
            )
        }
        constructor(diagnostic: Diagnostic) : this(diagnostic.pos, diagnostic)
    }

    sealed interface Callable : Tree, ExpressionOrCallable {
        val type: Signature2
        override fun deepCopy(): Callable
    }

    class GarbageCallable(
        pos: Position,
        diagnostic: Diagnostic? = null,
    ) : BaseTree(pos), Garbage, Callable {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (diagnostic != null) {
                    sharedCodeFormattingTemplate81
                } else {
                    sharedCodeFormattingTemplate82
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.diagnostic ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _diagnostic: Diagnostic?
        override var diagnostic: Diagnostic?
            get() = _diagnostic
            set(newValue) { _diagnostic = updateTreeConnection(_diagnostic, newValue) }
        override val type: Signature2
            get() = invalidSig
        override fun deepCopy(): GarbageCallable {
            return GarbageCallable(pos, diagnostic = this.diagnostic?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is GarbageCallable && this.diagnostic == other.diagnostic
        }
        override fun hashCode(): Int {
            return (diagnostic?.hashCode() ?: 0)
        }
        init {
            this._diagnostic = updateTreeConnection(null, diagnostic)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as GarbageCallable).diagnostic },
            )
        }
        constructor(diagnostic: Diagnostic) : this(diagnostic.pos, diagnostic)
    }

    class GarbageType(
        pos: Position,
        diagnostic: Diagnostic? = null,
    ) : BaseTree(pos), Garbage, Type {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (diagnostic != null) {
                    sharedCodeFormattingTemplate83
                } else {
                    sharedCodeFormattingTemplate84
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.diagnostic ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _diagnostic: Diagnostic?
        override var diagnostic: Diagnostic?
            get() = _diagnostic
            set(newValue) { _diagnostic = updateTreeConnection(_diagnostic, newValue) }
        override fun deepCopy(): GarbageType {
            return GarbageType(pos, diagnostic = this.diagnostic?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is GarbageType && this.diagnostic == other.diagnostic
        }
        override fun hashCode(): Int {
            return (diagnostic?.hashCode() ?: 0)
        }
        init {
            this._diagnostic = updateTreeConnection(null, diagnostic)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as GarbageType).diagnostic },
            )
        }
        constructor(diagnostic: Diagnostic) : this(diagnostic.pos, diagnostic)
    }

    class Diagnostic(
        pos: Position,
        var text: String,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(OutputToken(temperEscaper.escape(text), OutputTokenType.QuotedValue))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): Diagnostic {
            return Diagnostic(pos, text = this.text)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Diagnostic && this.text == other.text
        }
        override fun hashCode(): Int {
            return text.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    sealed interface TopLevelDeclarationOrNestedTypeName : Tree {
        val name: Id
        override fun deepCopy(): TopLevelDeclarationOrNestedTypeName
    }

    /** A name declaration is a node that may bind a name. */
    sealed interface NameDeclaration : Tree {
        val name: Id
        val descriptor: Descriptor?
        override fun deepCopy(): NameDeclaration
    }

    sealed interface Declaration : Tree, NameDeclaration {
        val metadata: List<DeclarationMetadata>
        val qName: QName?
            get() = qNameFor(this)
        override fun deepCopy(): Declaration
    }

    sealed interface TopLevelDeclaration : Tree, TopLevel, TopLevelDeclarationOrNestedTypeName, Declaration {
        override fun deepCopy(): TopLevelDeclaration
    }

    class ModuleInitBlock(
        pos: Position,
        metadata: Iterable<DeclarationMetadata> = emptyList(),
        body: BlockStatement,
    ) : BaseTree(pos), TopLevel {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate85
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _body: BlockStatement
        var body: BlockStatement
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): ModuleInitBlock {
            return ModuleInitBlock(pos, metadata = this.metadata, body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ModuleInitBlock && this.metadata == other.metadata && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ModuleInitBlock).body },
            )
        }
    }

    /**
     * Boilerplate code fold boundaries must occur in pairs.
     * A start and end surround regions of code that should be
     * [folded](https://en.wikipedia.org/wiki/Code_folding) away
     * because, though necessary, they should not draw the focus
     * of a person reading the code.  Typically, these are
     * used in documentation snippets.  See also [Genre.Documentation].
     *
     * Including the code in these boundaries in the compiled code
     * allows us to produce [SSCCE](https://sscce.org)
     * (Short, Self-Contained, Correct examples) in our generated
     * documentation.
     *
     * Backends may, when translating these, assume that each boundary
     * is part of a valid pair.
     *
     * Backends should, when translating documentation code, convert them
     * into comments that occur on their own lines, using the target
     * language's comment convention that contain the text
     * <code>\#region \_\_BOILERPLATE\_\_ \{\{\{</code> and
     * <code>\#endregion \}\}\}</code> respectively, and must not
     * produce those sequences in other tokens.
     * If the `#` in those sequences is preceded by a back-slash, tools
     * must not treat it as a boundary.
     * That means that it's sufficient to escape hash characters (`#`)
     * to prevent boundaries from being found in string literals.
     *
     * The <code>\{\{\{...\}\}\}</code> markers help with
     * [Emacs](https://www.emacswiki.org/emacs/FoldingMode) and
     * [Vim](https://learnvimscriptthehardway.stevelosh.com/chapters/48.html#marker).
     *
     * The end goal is to allow JavaScript to find and perform code
     * folding in HTML generated from documentation fragments in
     * a way that is agnostic to the language in the HTML code block.
     */
    sealed interface BoilerplateCodeFoldBoundary : Tree, TopLevel, Statement {
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(
                OutputToken("// #$markerText\n", OutputTokenType.Comment),
            )
        }
        val markerText: String
        override fun deepCopy(): BoilerplateCodeFoldBoundary
        companion object {
            const val START_MARKER_TEXT = "region __BOILERPLATE__ {{{"
            const val END_MARKER_TEXT = "endregion }}}"
        }
    }

    class EmbeddedComment(
        pos: Position,
        var commentText: String,
    ) : BaseTree(pos), TopLevel, Statement {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(
                OutputToken("// $commentText\n", OutputTokenType.Comment),
            )
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): EmbeddedComment {
            return EmbeddedComment(pos, commentText = this.commentText)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is EmbeddedComment && this.commentText == other.commentText
        }
        override fun hashCode(): Int {
            return commentText.hashCode()
        }
        init {
            // Many backends have only line comments, so TmpLTranslator
            // should take care to produce sequences of single line
            // comments instead of relying on each backend to do their
            // own comment splitting.
            require(anyLineBreak.find(commentText) == null)
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * A generic type
     */
    class TypeFormal(
        pos: Position,
        name: Id,
        upperBounds: Iterable<NominalType>,
        var definition: lang.temper.type.TypeFormal,
    ) : BaseTree(pos), TopLevelDeclarationOrNestedTypeName, NameDeclaration {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (upperBounds.isNotEmpty()) {
                    sharedCodeFormattingTemplate86
                } else {
                    sharedCodeFormattingTemplate26
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name
                1 -> FormattableTreeGroup(this.upperBounds)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private val _upperBounds: MutableList<NominalType> = mutableListOf()
        var upperBounds: List<NominalType>
            get() = _upperBounds
            set(newValue) { updateTreeConnections(_upperBounds, newValue) }
        override val descriptor: Descriptor?
            get() = null
        override fun deepCopy(): TypeFormal {
            return TypeFormal(pos, name = this.name.deepCopy(), upperBounds = this.upperBounds.deepCopy(), definition = this.definition)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeFormal && this.name == other.name && this.upperBounds == other.upperBounds && this.definition == other.definition
        }
        override fun hashCode(): Int {
            var hc = name.hashCode()
            hc = 31 * hc + upperBounds.hashCode()
            hc = 31 * hc + definition.hashCode()
            return hc
        }
        init {
            this._name = updateTreeConnection(null, name)
            updateTreeConnections(this._upperBounds, upperBounds)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeFormal).name },
                { n -> (n as TypeFormal).upperBounds },
            )
        }
    }

    /**
     * Represents the class with a body.
     */
    class TypeDeclaration(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        name: Id,
        typeParameters: ATypeParameters,
        superTypes: Iterable<NominalType>,
        members: Iterable<MemberOrGarbage>,
        inherited: Iterable<SuperTypeMethod>,
        var kind: TypeDeclarationKind,
        var typeShape: TypeShape,
    ) : BaseTree(pos), TopLevelDeclaration {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (kind == TypeDeclarationKind.Class && parsedTypeName != null && superTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate87
                } else if (kind == TypeDeclarationKind.Class && parsedTypeName != null) {
                    sharedCodeFormattingTemplate88
                } else if (kind == TypeDeclarationKind.Class && superTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate89
                } else if (kind == TypeDeclarationKind.Class) {
                    sharedCodeFormattingTemplate90
                } else if (kind == TypeDeclarationKind.Interface && parsedTypeName != null && superTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate91
                } else if (kind == TypeDeclarationKind.Interface && parsedTypeName != null) {
                    sharedCodeFormattingTemplate92
                } else if (kind == TypeDeclarationKind.Interface && superTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate93
                } else if (kind == TypeDeclarationKind.Interface) {
                    sharedCodeFormattingTemplate94
                } else if (parsedTypeName != null && superTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate95
                } else if (parsedTypeName != null) {
                    sharedCodeFormattingTemplate96
                } else if (superTypes.isNotEmpty()) {
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
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.name
                2 -> this.parsedTypeName ?: FormattableTreeGroup.empty
                3 -> this.typeParameters
                4 -> FormattableTreeGroup(this.superTypes)
                5 -> FormattableTreeGroup(this.members)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _typeParameters: ATypeParameters
        var typeParameters: ATypeParameters
            get() = _typeParameters
            set(newValue) { _typeParameters = updateTreeConnection(_typeParameters, newValue) }
        private val _superTypes: MutableList<NominalType> = mutableListOf()
        var superTypes: List<NominalType>
            get() = _superTypes
            set(newValue) { updateTreeConnections(_superTypes, newValue) }
        private val _members: MutableList<MemberOrGarbage> = mutableListOf()
        var members: List<MemberOrGarbage>
            get() = _members
            set(newValue) { updateTreeConnections(_members, newValue) }
        private val _inherited: MutableList<SuperTypeMethod> = mutableListOf()
        var inherited: List<SuperTypeMethod>
            get() = _inherited
            set(newValue) { updateTreeConnections(_inherited, newValue) }
        override val descriptor: Nothing?
            get() = null
        val parsedTypeName: OriginalName?
            get() = this.typeShape.word?.let { OriginalName(this.pos.leftEdge, it) }
        override fun deepCopy(): TypeDeclaration {
            return TypeDeclaration(pos, metadata = this.metadata, name = this.name.deepCopy(), typeParameters = this.typeParameters.deepCopy(), superTypes = this.superTypes.deepCopy(), members = this.members.deepCopy(), inherited = this.inherited.deepCopy(), kind = this.kind, typeShape = this.typeShape)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeDeclaration && this.metadata == other.metadata && this.name == other.name && this.typeParameters == other.typeParameters && this.superTypes == other.superTypes && this.members == other.members && this.inherited == other.inherited && this.kind == other.kind && this.typeShape == other.typeShape
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + typeParameters.hashCode()
            hc = 31 * hc + superTypes.hashCode()
            hc = 31 * hc + members.hashCode()
            hc = 31 * hc + inherited.hashCode()
            hc = 31 * hc + kind.hashCode()
            hc = 31 * hc + typeShape.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._name = updateTreeConnection(null, name)
            this._typeParameters = updateTreeConnection(null, typeParameters)
            updateTreeConnections(this._superTypes, superTypes)
            updateTreeConnections(this._members, members)
            updateTreeConnections(this._inherited, inherited)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeDeclaration).name },
                { n -> (n as TypeDeclaration).typeParameters },
                { n -> (n as TypeDeclaration).superTypes },
                { n -> (n as TypeDeclaration).members },
                { n -> (n as TypeDeclaration).inherited },
            )
        }
    }

    /**
     * Records that a type is declared in Temper but instead of
     * corresponding to a [TypeDeclaration] is connected to a
     * backend-specific type name by the support network.
     *
     * This serves to anchor the type name but most backends
     * do not need to produce code for them.
     */
    class TypeConnection(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        var kind: TypeDeclarationKind,
        name: Id,
        typeParameters: ATypeParameters,
        superTypes: Iterable<NominalType>,
        to: NominalType,
        connectedKey: ConnectedKey,
        var typeShape: TypeShape,
    ) : BaseTree(pos), TopLevelDeclaration {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (superTypes.isNotEmpty()) {
                    sharedCodeFormattingTemplate99
                } else {
                    sharedCodeFormattingTemplate100
                }
        override val formatElementCount
            get() = 6
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.kind
                2 -> this.name
                3 -> this.typeParameters
                4 -> FormattableTreeGroup(this.superTypes)
                5 -> this.to
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _typeParameters: ATypeParameters
        var typeParameters: ATypeParameters
            get() = _typeParameters
            set(newValue) { _typeParameters = updateTreeConnection(_typeParameters, newValue) }
        private val _superTypes: MutableList<NominalType> = mutableListOf()
        var superTypes: List<NominalType>
            get() = _superTypes
            set(newValue) { updateTreeConnections(_superTypes, newValue) }
        private var _to: NominalType
        var to: NominalType
            get() = _to
            set(newValue) { _to = updateTreeConnection(_to, newValue) }
        private var _connectedKey: ConnectedKey
        var connectedKey: ConnectedKey
            get() = _connectedKey
            set(newValue) { _connectedKey = updateTreeConnection(_connectedKey, newValue) }
        override val descriptor: Descriptor?
            get() = null
        override fun deepCopy(): TypeConnection {
            return TypeConnection(pos, metadata = this.metadata, kind = this.kind, name = this.name.deepCopy(), typeParameters = this.typeParameters.deepCopy(), superTypes = this.superTypes.deepCopy(), to = this.to.deepCopy(), connectedKey = this.connectedKey.deepCopy(), typeShape = this.typeShape)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeConnection && this.metadata == other.metadata && this.kind == other.kind && this.name == other.name && this.typeParameters == other.typeParameters && this.superTypes == other.superTypes && this.to == other.to && this.connectedKey == other.connectedKey && this.typeShape == other.typeShape
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + kind.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + typeParameters.hashCode()
            hc = 31 * hc + superTypes.hashCode()
            hc = 31 * hc + to.hashCode()
            hc = 31 * hc + connectedKey.hashCode()
            hc = 31 * hc + typeShape.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._name = updateTreeConnection(null, name)
            this._typeParameters = updateTreeConnection(null, typeParameters)
            updateTreeConnections(this._superTypes, superTypes)
            this._to = updateTreeConnection(null, to)
            this._connectedKey = updateTreeConnection(null, connectedKey)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeConnection).name },
                { n -> (n as TypeConnection).typeParameters },
                { n -> (n as TypeConnection).superTypes },
                { n -> (n as TypeConnection).to },
                { n -> (n as TypeConnection).connectedKey },
            )
        }
    }

    class PooledValueDeclaration(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        name: Id,
        init: Expression,
        override var descriptor: Descriptor?,
    ) : BaseTree(pos), TopLevelDeclaration {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate101
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.name
                2 -> this.init
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _init: Expression
        var init: Expression
            get() = _init
            set(newValue) { _init = updateTreeConnection(_init, newValue) }
        override fun deepCopy(): PooledValueDeclaration {
            return PooledValueDeclaration(pos, metadata = this.metadata, name = this.name.deepCopy(), init = this.init.deepCopy(), descriptor = this.descriptor)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PooledValueDeclaration && this.metadata == other.metadata && this.name == other.name && this.init == other.init && this.descriptor == other.descriptor
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + init.hashCode()
            hc = 31 * hc + (descriptor?.hashCode() ?: 0)
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._name = updateTreeConnection(null, name)
            this._init = updateTreeConnection(null, init)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as PooledValueDeclaration).name },
                { n -> (n as PooledValueDeclaration).init },
            )
        }
    }

    class SupportCodeDeclaration(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        name: Id,
        init: SupportCodeWrapper,
        override var descriptor: Descriptor?,
    ) : BaseTree(pos), TopLevelDeclaration {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate101
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.name
                2 -> this.init
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _init: SupportCodeWrapper
        var init: SupportCodeWrapper
            get() = _init
            set(newValue) { _init = updateTreeConnection(_init, newValue) }
        override fun deepCopy(): SupportCodeDeclaration {
            return SupportCodeDeclaration(pos, metadata = this.metadata, name = this.name.deepCopy(), init = this.init.deepCopy(), descriptor = this.descriptor)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SupportCodeDeclaration && this.metadata == other.metadata && this.name == other.name && this.init == other.init && this.descriptor == other.descriptor
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + init.hashCode()
            hc = 31 * hc + (descriptor?.hashCode() ?: 0)
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._name = updateTreeConnection(null, name)
            this._init = updateTreeConnection(null, init)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SupportCodeDeclaration).name },
                { n -> (n as SupportCodeDeclaration).init },
            )
        }
    }

    sealed interface FunctionLike : Tree, NameDeclaration {
        override val descriptor: Signature2?
            get() = sig
        val metadata: List<DeclarationMetadata>
        val sig: Signature2
        val parameters: Parameters
        val body: BlockStatement?
        override fun deepCopy(): FunctionLike
    }

    sealed interface FunctionDeclarationOrMethod : Tree, FunctionLike {
        val typeParameters: ATypeParameters
        val returnType: AType
        val mayYield: Boolean
        override fun deepCopy(): FunctionDeclarationOrMethod
    }

    sealed interface FunctionDeclaration : Tree, Declaration, FunctionDeclarationOrMethod {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (mayYield) {
                    sharedCodeFormattingTemplate102
                } else {
                    sharedCodeFormattingTemplate103
                }
        override val formatElementCount
            get() = 6
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.name
                2 -> this.typeParameters
                3 -> this.parameters
                4 -> this.returnType
                5 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        override val descriptor: Signature2?
            get() = sig
        override val body: BlockStatement
        override fun deepCopy(): FunctionDeclaration
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FunctionDeclaration).name },
                { n -> (n as FunctionDeclaration).typeParameters },
                { n -> (n as FunctionDeclaration).parameters },
                { n -> (n as FunctionDeclaration).returnType },
                { n -> (n as FunctionDeclaration).body },
            )
        }
    }

    /** A top level function declaration.  Might be exported or might not. */
    class ModuleFunctionDeclaration(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        name: Id,
        typeParameters: ATypeParameters,
        parameters: Parameters,
        returnType: AType,
        body: BlockStatement,
        override var sig: Signature2,
        override var mayYield: Boolean,
    ) : BaseTree(pos), TopLevelDeclaration, FunctionDeclaration {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _typeParameters: ATypeParameters
        override var typeParameters: ATypeParameters
            get() = _typeParameters
            set(newValue) { _typeParameters = updateTreeConnection(_typeParameters, newValue) }
        private var _parameters: Parameters
        override var parameters: Parameters
            get() = _parameters
            set(newValue) { _parameters = updateTreeConnection(_parameters, newValue) }
        private var _returnType: AType
        override var returnType: AType
            get() = _returnType
            set(newValue) { _returnType = updateTreeConnection(_returnType, newValue) }
        private var _body: BlockStatement
        override var body: BlockStatement
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override val descriptor: Signature2
            get() = sig
        override fun deepCopy(): ModuleFunctionDeclaration {
            return ModuleFunctionDeclaration(pos, metadata = this.metadata, name = this.name.deepCopy(), typeParameters = this.typeParameters.deepCopy(), parameters = this.parameters.deepCopy(), returnType = this.returnType.deepCopy(), body = this.body.deepCopy(), sig = this.sig, mayYield = this.mayYield)
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ModuleFunctionDeclaration && this.metadata == other.metadata && this.name == other.name && this.typeParameters == other.typeParameters && this.parameters == other.parameters && this.returnType == other.returnType && this.body == other.body && this.sig == other.sig && this.mayYield == other.mayYield
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + typeParameters.hashCode()
            hc = 31 * hc + parameters.hashCode()
            hc = 31 * hc + returnType.hashCode()
            hc = 31 * hc + body.hashCode()
            hc = 31 * hc + sig.hashCode()
            hc = 31 * hc + mayYield.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._name = updateTreeConnection(null, name)
            this._typeParameters = updateTreeConnection(null, typeParameters)
            this._parameters = updateTreeConnection(null, parameters)
            this._returnType = updateTreeConnection(null, returnType)
            this._body = updateTreeConnection(null, body)
        }
    }

    sealed interface MaybeAssignable : Tree {
        val name: Id
        val assignOnce: Boolean
        override fun deepCopy(): MaybeAssignable
    }

    sealed interface VarLike : Tree, NameDeclaration, MaybeAssignable {
        override val descriptor: Type2
        val metadata: List<DeclarationMetadata>
        override fun deepCopy(): VarLike
    }

    sealed interface ModuleOrLocalDeclaration : Tree, NameDeclaration, VarLike {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (assignOnce && init != null) {
                    sharedCodeFormattingTemplate104
                } else if (assignOnce) {
                    sharedCodeFormattingTemplate105
                } else if (init != null) {
                    sharedCodeFormattingTemplate106
                } else {
                    sharedCodeFormattingTemplate107
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.name
                2 -> this.type
                3 -> this.init ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        override val descriptor: Type2
        val type: AType
        val init: Expression?
        override fun deepCopy(): ModuleOrLocalDeclaration
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ModuleOrLocalDeclaration).name },
                { n -> (n as ModuleOrLocalDeclaration).type },
                { n -> (n as ModuleOrLocalDeclaration).init },
            )
        }
    }

    /** A declaration of a variable scoped to the containing [module][Module]. */
    class ModuleLevelDeclaration(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        name: Id,
        type: AType,
        init: Expression?,
        override var descriptor: Type2,
        override var assignOnce: Boolean,
    ) : BaseTree(pos), TopLevelDeclaration, ModuleOrLocalDeclaration {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _type: AType
        override var type: AType
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _init: Expression?
        override var init: Expression?
            get() = _init
            set(newValue) { _init = updateTreeConnection(_init, newValue) }
        override fun deepCopy(): ModuleLevelDeclaration {
            return ModuleLevelDeclaration(pos, metadata = this.metadata, name = this.name.deepCopy(), type = this.type.deepCopy(), init = this.init?.deepCopy(), descriptor = this.descriptor, assignOnce = this.assignOnce)
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ModuleLevelDeclaration && this.metadata == other.metadata && this.name == other.name && this.type == other.type && this.init == other.init && this.descriptor == other.descriptor && this.assignOnce == other.assignOnce
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + (init?.hashCode() ?: 0)
            hc = 31 * hc + descriptor.hashCode()
            hc = 31 * hc + assignOnce.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._name = updateTreeConnection(null, name)
            this._type = updateTreeConnection(null, type)
            this._init = updateTreeConnection(null, init)
        }
    }

    class Test(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        name: Id,
        parameters: Parameters,
        body: BlockStatement,
        returnType: AType,
        var rawName: String,
    ) : BaseTree(pos), TopLevelDeclaration, FunctionLike {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate108
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.name
                2 -> this.parameters
                3 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _parameters: Parameters
        override var parameters: Parameters
            get() = _parameters
            set(newValue) { _parameters = updateTreeConnection(_parameters, newValue) }
        private var _body: BlockStatement
        override var body: BlockStatement
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        private var _returnType: AType
        var returnType: AType
            get() = _returnType
            set(newValue) { _returnType = updateTreeConnection(_returnType, newValue) }
        override val descriptor: Signature2
            get() = testSig
        override val sig: Signature2
            get() = testSig
        override fun deepCopy(): Test {
            return Test(pos, metadata = this.metadata, name = this.name.deepCopy(), parameters = this.parameters.deepCopy(), body = this.body.deepCopy(), returnType = this.returnType.deepCopy(), rawName = this.rawName)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Test && this.metadata == other.metadata && this.name == other.name && this.parameters == other.parameters && this.body == other.body && this.returnType == other.returnType && this.rawName == other.rawName
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + parameters.hashCode()
            hc = 31 * hc + body.hashCode()
            hc = 31 * hc + returnType.hashCode()
            hc = 31 * hc + rawName.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._name = updateTreeConnection(null, name)
            this._parameters = updateTreeConnection(null, parameters)
            this._body = updateTreeConnection(null, body)
            this._returnType = updateTreeConnection(null, returnType)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Test).name },
                { n -> (n as Test).parameters },
                { n -> (n as Test).body },
                { n -> (n as Test).returnType },
            )
        }
    }

    /** A declaration scoped within a [block][BlockStatement]. */
    class LocalDeclaration(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        name: Id,
        type: AType,
        init: Expression?,
        override var descriptor: Type2,
        override var assignOnce: Boolean,
    ) : BaseTree(pos), Declaration, ModuleOrLocalDeclaration, Statement {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _type: AType
        override var type: AType
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _init: Expression?
        override var init: Expression?
            get() = _init
            set(newValue) { _init = updateTreeConnection(_init, newValue) }
        override fun deepCopy(): LocalDeclaration {
            return LocalDeclaration(pos, metadata = this.metadata, name = this.name.deepCopy(), type = this.type.deepCopy(), init = this.init?.deepCopy(), descriptor = this.descriptor, assignOnce = this.assignOnce)
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LocalDeclaration && this.metadata == other.metadata && this.name == other.name && this.type == other.type && this.init == other.init && this.descriptor == other.descriptor && this.assignOnce == other.assignOnce
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + (init?.hashCode() ?: 0)
            hc = 31 * hc + descriptor.hashCode()
            hc = 31 * hc + assignOnce.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._name = updateTreeConnection(null, name)
            this._type = updateTreeConnection(null, type)
            this._init = updateTreeConnection(null, init)
        }
    }

    class Formal(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        name: Id,
        type: AType,
        override var descriptor: Type2,
        override var assignOnce: Boolean = true,
        var optionalState: TriState = TriState.FALSE,
    ) : BaseTree(pos), NameDeclaration, VarLike {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (assignOnce && optional) {
                    sharedCodeFormattingTemplate109
                } else if (assignOnce) {
                    sharedCodeFormattingTemplate110
                } else if (optional) {
                    sharedCodeFormattingTemplate111
                } else {
                    sharedCodeFormattingTemplate112
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.name
                2 -> this.type
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _type: AType
        var type: AType
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        val optional: Boolean
            get() = optionalState.elseTrue
        override fun deepCopy(): Formal {
            return Formal(pos, metadata = this.metadata, name = this.name.deepCopy(), type = this.type.deepCopy(), descriptor = this.descriptor, assignOnce = this.assignOnce, optionalState = this.optionalState)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Formal && this.metadata == other.metadata && this.name == other.name && this.type == other.type && this.descriptor == other.descriptor && this.assignOnce == other.assignOnce && this.optionalState == other.optionalState
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + descriptor.hashCode()
            hc = 31 * hc + assignOnce.hashCode()
            hc = 31 * hc + optionalState.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._name = updateTreeConnection(null, name)
            this._type = updateTreeConnection(null, type)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Formal).name },
                { n -> (n as Formal).type },
            )
        }
    }

    sealed interface Member : Tree, NameDeclaration, MemberOrGarbage {
        val metadata: List<DeclarationMetadata>
        val memberShape: VisibleMemberShape
        val visibility: VisibilityModifier
        override fun deepCopy(): Member
    }

    class RestFormal(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        name: Id,
        type: AType,
        override var descriptor: Type2,
        override var assignOnce: Boolean = true,
    ) : BaseTree(pos), NameDeclaration, VarLike {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate113
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.name
                2 -> this.type
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _type: AType
        var type: AType
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        override fun deepCopy(): RestFormal {
            return RestFormal(pos, metadata = this.metadata, name = this.name.deepCopy(), type = this.type.deepCopy(), descriptor = this.descriptor, assignOnce = this.assignOnce)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is RestFormal && this.metadata == other.metadata && this.name == other.name && this.type == other.type && this.descriptor == other.descriptor && this.assignOnce == other.assignOnce
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + descriptor.hashCode()
            hc = 31 * hc + assignOnce.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._name = updateTreeConnection(null, name)
            this._type = updateTreeConnection(null, type)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as RestFormal).name },
                { n -> (n as RestFormal).type },
            )
        }
    }

    /**
     * Something that can be accessed via a dot operator.
     * Any method except constructors which are accessed, indirectly via `new`.
     */
    sealed interface DotAccessible : Tree {
        val visibility: VisibilityModifier
        val dotName: DotName
        val name: Id
        val sameDotName: Boolean
            get() = dotNameMatchesName(this.dotName, this.name)
        override fun deepCopy(): DotAccessible
    }

    sealed interface StaticMember : Tree, NameDeclaration, DotAccessible {
        override fun deepCopy(): StaticMember
    }

    sealed interface Property : Tree, MaybeAssignable, Member, DotAccessible {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (assignOnce && sameDotName) {
                    sharedCodeFormattingTemplate114
                } else if (assignOnce) {
                    sharedCodeFormattingTemplate115
                } else if (sameDotName) {
                    sharedCodeFormattingTemplate116
                } else {
                    sharedCodeFormattingTemplate117
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.dotName
                2 -> this.name
                3 -> this.type
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        override val descriptor: Type2?
        val type: AType
        override fun deepCopy(): Property
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Property).dotName },
                { n -> (n as Property).name },
                { n -> (n as Property).type },
            )
        }
    }

    /** A group of formal value parameters in the declaration of a callable thing. */
    class Parameters(
        pos: Position,
        thisName: Id?,
        parameters: Iterable<Formal>,
        restParameter: RestFormal?,
    ) : BaseTree(pos) {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.ParenGroup
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (thisName != null && needCommaAfterThis && needCommaAfterParameters && restParameter != null) {
                    sharedCodeFormattingTemplate118
                } else if (thisName != null && needCommaAfterThis && needCommaAfterParameters) {
                    sharedCodeFormattingTemplate119
                } else if (thisName != null && needCommaAfterThis && restParameter != null) {
                    sharedCodeFormattingTemplate120
                } else if (thisName != null && needCommaAfterThis) {
                    sharedCodeFormattingTemplate121
                } else if (thisName != null && needCommaAfterParameters && restParameter != null) {
                    sharedCodeFormattingTemplate122
                } else if (thisName != null && needCommaAfterParameters) {
                    sharedCodeFormattingTemplate123
                } else if (thisName != null && restParameter != null) {
                    sharedCodeFormattingTemplate124
                } else if (thisName != null) {
                    sharedCodeFormattingTemplate125
                } else if (needCommaAfterThis && needCommaAfterParameters && restParameter != null) {
                    sharedCodeFormattingTemplate126
                } else if (needCommaAfterThis && needCommaAfterParameters) {
                    sharedCodeFormattingTemplate127
                } else if (needCommaAfterThis && restParameter != null) {
                    sharedCodeFormattingTemplate128
                } else if (needCommaAfterThis) {
                    sharedCodeFormattingTemplate129
                } else if (needCommaAfterParameters && restParameter != null) {
                    sharedCodeFormattingTemplate130
                } else if (needCommaAfterParameters) {
                    sharedCodeFormattingTemplate131
                } else if (restParameter != null) {
                    sharedCodeFormattingTemplate132
                } else {
                    sharedCodeFormattingTemplate133
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.thisName ?: FormattableTreeGroup.empty
                1 -> FormattableTreeGroup(this.parameters)
                2 -> this.restParameter ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _thisName: Id?
        var thisName: Id?
            get() = _thisName
            set(newValue) { _thisName = updateTreeConnection(_thisName, newValue) }
        private val _parameters: MutableList<Formal> = mutableListOf()
        var parameters: List<Formal>
            get() = _parameters
            set(newValue) { updateTreeConnections(_parameters, newValue) }
        private var _restParameter: RestFormal?
        var restParameter: RestFormal?
            get() = _restParameter
            set(newValue) { _restParameter = updateTreeConnection(_restParameter, newValue) }
        val needCommaAfterThis: Boolean
            get() = thisName != null && (parameters.isNotEmpty() || restParameter != null)
        val needCommaAfterParameters: Boolean
            get() = restParameter != null && parameters.isNotEmpty()
        override fun deepCopy(): Parameters {
            return Parameters(pos, thisName = this.thisName?.deepCopy(), parameters = this.parameters.deepCopy(), restParameter = this.restParameter?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Parameters && this.thisName == other.thisName && this.parameters == other.parameters && this.restParameter == other.restParameter
        }
        override fun hashCode(): Int {
            var hc = (thisName?.hashCode() ?: 0)
            hc = 31 * hc + parameters.hashCode()
            hc = 31 * hc + (restParameter?.hashCode() ?: 0)
            return hc
        }
        init {
            this._thisName = updateTreeConnection(null, thisName)
            updateTreeConnections(this._parameters, parameters)
            this._restParameter = updateTreeConnection(null, restParameter)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Parameters).thisName },
                { n -> (n as Parameters).parameters },
                { n -> (n as Parameters).restParameter },
            )
        }
    }

    /** Statements like blocks and control flow that exist primarily to nest other statements. */
    sealed interface NestingStatement : Tree, Statement {
        val nestedStatements: List<Statement>
        override fun deepCopy(): NestingStatement
    }

    class BlockStatement(
        pos: Position,
        statements: Iterable<Statement>,
    ) : BaseTree(pos), NestingStatement {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate134
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
        override val nestedStatements: List<Statement>
            get() = statements
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

        /** Release and return all contained statements for reuse by other nodes. */
        fun takeBody(): List<Statement> {
            val statements = this.statements.toList()
            this.statements = emptyList()
            return statements
        }
    }

    sealed interface Method : Tree, FunctionDeclarationOrMethod, Member {
        override val descriptor: Signature2?
            get() = sig
        override val sig: Signature2
            get() = memberShape.descriptor as? Signature2 ?: invalidSig
        override fun deepCopy(): Method
    }

    /**
     * A nested function declaration which might close over names that are not defined at
     * a module top-level.
     */
    class LocalFunctionDeclaration(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        name: Id,
        typeParameters: ATypeParameters,
        parameters: Parameters,
        returnType: AType,
        body: BlockStatement,
        override var sig: Signature2,
        override var mayYield: Boolean,
    ) : BaseTree(pos), FunctionDeclaration, Statement {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _typeParameters: ATypeParameters
        override var typeParameters: ATypeParameters
            get() = _typeParameters
            set(newValue) { _typeParameters = updateTreeConnection(_typeParameters, newValue) }
        private var _parameters: Parameters
        override var parameters: Parameters
            get() = _parameters
            set(newValue) { _parameters = updateTreeConnection(_parameters, newValue) }
        private var _returnType: AType
        override var returnType: AType
            get() = _returnType
            set(newValue) { _returnType = updateTreeConnection(_returnType, newValue) }
        private var _body: BlockStatement
        override var body: BlockStatement
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): LocalFunctionDeclaration {
            return LocalFunctionDeclaration(pos, metadata = this.metadata, name = this.name.deepCopy(), typeParameters = this.typeParameters.deepCopy(), parameters = this.parameters.deepCopy(), returnType = this.returnType.deepCopy(), body = this.body.deepCopy(), sig = this.sig, mayYield = this.mayYield)
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is LocalFunctionDeclaration && this.metadata == other.metadata && this.name == other.name && this.typeParameters == other.typeParameters && this.parameters == other.parameters && this.returnType == other.returnType && this.body == other.body && this.sig == other.sig && this.mayYield == other.mayYield
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + typeParameters.hashCode()
            hc = 31 * hc + parameters.hashCode()
            hc = 31 * hc + returnType.hashCode()
            hc = 31 * hc + body.hashCode()
            hc = 31 * hc + sig.hashCode()
            hc = 31 * hc + mayYield.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._name = updateTreeConnection(null, name)
            this._typeParameters = updateTreeConnection(null, typeParameters)
            this._parameters = updateTreeConnection(null, parameters)
            this._returnType = updateTreeConnection(null, returnType)
            this._body = updateTreeConnection(null, body)
        }
    }

    /**
     * A yielding operator can cause the directly containing function body to pause.
     *
     * A [YieldStatement] it pauses until the containing generator is asked for the
     * next value.
     *
     * An [AwaitExpression] it pauses until the awaited promise resolves.
     */
    sealed interface YieldingOperator : Tree {
        override fun deepCopy(): YieldingOperator
    }

    /**
     * Yield control from the current function to its caller.
     *
     * This will only appear in the bodies (possibly in nested control flow)
     * of local or module function declarations when the support network
     * requests [CoroutineStrategy.TranslateToGenerator].
     */
    class YieldStatement(
        pos: Position,
    ) : BaseTree(pos), YieldingOperator, Statement {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate135
        override val formatElementCount
            get() = 0
        override fun deepCopy(): YieldStatement {
            return YieldStatement(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is YieldStatement
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * An await expression pauses control until the promise resolves or,
     * on some backends, the running thread is interrupted.
     */
    class AwaitExpression(
        pos: Position,
        promise: Expression,
        override var type: Type2,
    ) : BaseTree(pos), YieldingOperator, Expression {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate136
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
            return AwaitExpression(pos, promise = this.promise.deepCopy(), type = this.type)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is AwaitExpression && this.promise == other.promise && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = promise.hashCode()
            hc = 31 * hc + type.hashCode()
            return hc
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

    class OriginalName(
        pos: Position,
        var symbol: Symbol,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(ParsedName(symbol.text).toToken(inOperatorPosition = false))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): OriginalName {
            return OriginalName(pos, symbol = this.symbol)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is OriginalName && this.symbol == other.symbol
        }
        override fun hashCode(): Int {
            return symbol.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * A named type corresponding to a Temper `class` or `interface` definition.
     * These are neither special nor primarily a composition of other types.
     */
    class NominalType(
        pos: Position,
        typeName: TypeName,
        params: Iterable<AType>,
        var connectsFrom: Type2? = null,
    ) : BaseTree(pos), Type {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.Angle
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (params.isNotEmpty()) {
                    sharedCodeFormattingTemplate33
                } else {
                    sharedCodeFormattingTemplate26
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.typeName
                1 -> FormattableTreeGroup(this.params)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _typeName: TypeName
        var typeName: TypeName
            get() = _typeName
            set(newValue) { _typeName = updateTreeConnection(_typeName, newValue) }
        private val _params: MutableList<AType> = mutableListOf()
        var params: List<AType>
            get() = _params
            set(newValue) { updateTreeConnections(_params, newValue) }
        override fun deepCopy(): NominalType {
            return NominalType(pos, typeName = this.typeName.deepCopy(), params = this.params.deepCopy(), connectsFrom = this.connectsFrom)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is NominalType && this.typeName == other.typeName && this.params == other.params && this.connectsFrom == other.connectsFrom
        }
        override fun hashCode(): Int {
            var hc = typeName.hashCode()
            hc = 31 * hc + params.hashCode()
            hc = 31 * hc + (connectsFrom?.hashCode() ?: 0)
            return hc
        }
        init {
            this._typeName = updateTreeConnection(null, typeName)
            updateTreeConnections(this._params, params)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as NominalType).typeName },
                { n -> (n as NominalType).params },
            )
        }
    }

    /** Method signature information about the method declarations in super-types that an instance method overrides */
    class SuperTypeMethod(
        pos: Position,
        superType: NominalType,
        name: DotName,
        visibility: VisibilityModifier,
        typeParameters: ATypeParameters,
        parameters: ValueFormalList,
        returnType: AType,
        var memberOverride: MemberOverride2,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate137
        override val formatElementCount
            get() = 6
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.superType
                1 -> this.name
                2 -> this.visibility
                3 -> this.typeParameters
                4 -> this.parameters
                5 -> this.returnType
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _superType: NominalType
        var superType: NominalType
            get() = _superType
            set(newValue) { _superType = updateTreeConnection(_superType, newValue) }
        private var _name: DotName
        var name: DotName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _visibility: VisibilityModifier
        var visibility: VisibilityModifier
            get() = _visibility
            set(newValue) { _visibility = updateTreeConnection(_visibility, newValue) }
        private var _typeParameters: ATypeParameters
        var typeParameters: ATypeParameters
            get() = _typeParameters
            set(newValue) { _typeParameters = updateTreeConnection(_typeParameters, newValue) }
        private var _parameters: ValueFormalList
        var parameters: ValueFormalList
            get() = _parameters
            set(newValue) { _parameters = updateTreeConnection(_parameters, newValue) }
        private var _returnType: AType
        var returnType: AType
            get() = _returnType
            set(newValue) { _returnType = updateTreeConnection(_returnType, newValue) }
        override fun deepCopy(): SuperTypeMethod {
            return SuperTypeMethod(pos, superType = this.superType.deepCopy(), name = this.name.deepCopy(), visibility = this.visibility.deepCopy(), typeParameters = this.typeParameters.deepCopy(), parameters = this.parameters.deepCopy(), returnType = this.returnType.deepCopy(), memberOverride = this.memberOverride)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SuperTypeMethod && this.superType == other.superType && this.name == other.name && this.visibility == other.visibility && this.typeParameters == other.typeParameters && this.parameters == other.parameters && this.returnType == other.returnType && this.memberOverride == other.memberOverride
        }
        override fun hashCode(): Int {
            var hc = superType.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + visibility.hashCode()
            hc = 31 * hc + typeParameters.hashCode()
            hc = 31 * hc + parameters.hashCode()
            hc = 31 * hc + returnType.hashCode()
            hc = 31 * hc + memberOverride.hashCode()
            return hc
        }
        init {
            this._superType = updateTreeConnection(null, superType)
            this._name = updateTreeConnection(null, name)
            this._visibility = updateTreeConnection(null, visibility)
            this._typeParameters = updateTreeConnection(null, typeParameters)
            this._parameters = updateTreeConnection(null, parameters)
            this._returnType = updateTreeConnection(null, returnType)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SuperTypeMethod).superType },
                { n -> (n as SuperTypeMethod).name },
                { n -> (n as SuperTypeMethod).visibility },
                { n -> (n as SuperTypeMethod).typeParameters },
                { n -> (n as SuperTypeMethod).parameters },
                { n -> (n as SuperTypeMethod).returnType },
            )
        }
    }

    sealed interface SupportCodeWrapper : Tree {
        val type: Signature2
        val supportCode: SupportCode
        override fun deepCopy(): SupportCodeWrapper
    }

    class SimpleSupportCodeWrapper(
        pos: Position,
        override var type: Signature2,
        override var supportCode: SupportCode,
    ) : BaseTree(pos), SupportCodeWrapper {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            supportCode.renderTo(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): SimpleSupportCodeWrapper {
            return SimpleSupportCodeWrapper(pos, type = this.type, supportCode = this.supportCode)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SimpleSupportCodeWrapper && this.type == other.type && this.supportCode == other.supportCode
        }
        override fun hashCode(): Int {
            var hc = type.hashCode()
            hc = 31 * hc + supportCode.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class InlineSupportCodeWrapper(
        pos: Position,
        override var type: Signature2,
        override var supportCode: InlineSupportCode<*, *>,
    ) : BaseTree(pos), SupportCodeWrapper, Callable {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(OutToks.leftParen)
            tokenSink.emit(OutputToken("inline", OutputTokenType.Word))
            supportCode.renderTo(tokenSink)
            tokenSink.emit(OutToks.rightParen)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): InlineSupportCodeWrapper {
            return InlineSupportCodeWrapper(pos, type = this.type, supportCode = this.supportCode)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is InlineSupportCodeWrapper && this.type == other.type && this.supportCode == other.supportCode
        }
        override fun hashCode(): Int {
            var hc = type.hashCode()
            hc = 31 * hc + supportCode.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * Wraps an arbitrary [Expression] turning it into a [Statement]
     */
    class ExpressionStatement(
        pos: Position,
        expression: Expression,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate138
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

    class Assignment(
        pos: Position,
        left: Id,
        right: RightHandSide,
        var type: Type2,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.Eq
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate139
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
        private var _left: Id
        var left: Id
            get() = _left
            set(newValue) { _left = updateTreeConnection(_left, newValue) }
        private var _right: RightHandSide
        var right: RightHandSide
            get() = _right
            set(newValue) { _right = updateTreeConnection(_right, newValue) }
        override fun deepCopy(): Assignment {
            return Assignment(pos, left = this.left.deepCopy(), right = this.right.deepCopy(), type = this.type)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Assignment && this.left == other.left && this.right == other.right && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = left.hashCode()
            hc = 31 * hc + right.hashCode()
            hc = 31 * hc + type.hashCode()
            return hc
        }
        init {
            this._left = updateTreeConnection(null, left)
            this._right = updateTreeConnection(null, right)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Assignment).left },
                { n -> (n as Assignment).right },
            )
        }
    }

    sealed interface JumpUpStatement : Tree {
        val label: JumpLabel?
        override fun deepCopy(): JumpUpStatement
    }

    class BreakStatement(
        pos: Position,
        label: JumpLabel?,
    ) : BaseTree(pos), Statement, JumpUpStatement {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (label != null) {
                    sharedCodeFormattingTemplate140
                } else {
                    sharedCodeFormattingTemplate141
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
        private var _label: JumpLabel?
        override var label: JumpLabel?
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
        label: JumpLabel?,
    ) : BaseTree(pos), Statement, JumpUpStatement {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (label != null) {
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
                0 -> this.label ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _label: JumpLabel?
        override var label: JumpLabel?
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
     * [failed] is set to `true` when [handled] fails, or `false` otherwise.
     * The result is the result of [handled] when it passes,
     * or [BubbleSentinel] otherwise.
     */
    class HandlerScope(
        pos: Position,
        failed: Id,
        handled: Handled,
    ) : BaseTree(pos), Statement, RightHandSide {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate144
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.failed
                1 -> this.handled
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _failed: Id
        var failed: Id
            get() = _failed
            set(newValue) { _failed = updateTreeConnection(_failed, newValue) }
        private var _handled: Handled
        var handled: Handled
            get() = _handled
            set(newValue) { _handled = updateTreeConnection(_handled, newValue) }
        override fun deepCopy(): HandlerScope {
            return HandlerScope(pos, failed = this.failed.deepCopy(), handled = this.handled.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is HandlerScope && this.failed == other.failed && this.handled == other.handled
        }
        override fun hashCode(): Int {
            var hc = failed.hashCode()
            hc = 31 * hc + handled.hashCode()
            return hc
        }
        init {
            this._failed = updateTreeConnection(null, failed)
            this._handled = updateTreeConnection(null, handled)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as HandlerScope).failed },
                { n -> (n as HandlerScope).handled },
            )
        }
    }

    /**
     * May appear within a [ModuleInitBlock] to abort module initialization when
     * un-handled failure occurs during module initialization.
     */
    class ModuleInitFailed(
        pos: Position,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate145
        override val formatElementCount
            get() = 0
        override fun deepCopy(): ModuleInitFailed {
            return ModuleInitFailed(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ModuleInitFailed
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class ReturnStatement(
        pos: Position,
        expression: Expression?,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (expression != null) {
                    sharedCodeFormattingTemplate146
                } else {
                    sharedCodeFormattingTemplate147
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expression ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expression: Expression?
        var expression: Expression?
            get() = _expression
            set(newValue) { _expression = updateTreeConnection(_expression, newValue) }
        override fun deepCopy(): ReturnStatement {
            return ReturnStatement(pos, expression = this.expression?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ReturnStatement && this.expression == other.expression
        }
        override fun hashCode(): Int {
            return (expression?.hashCode() ?: 0)
        }
        init {
            this._expression = updateTreeConnection(null, expression)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ReturnStatement).expression },
            )
        }
    }

    /**
     * Assigns a value to a property of an object either via a setter or directly.
     */
    sealed interface SetProperty : Tree, Statement {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.Eq
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate139
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
        val left: PropertyLValue
        val right: Expression
        override fun deepCopy(): SetProperty
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SetProperty).left },
                { n -> (n as SetProperty).right },
            )
        }
    }

    /**
     * Used when translating using [lang.temper.be.tmpl.BubbleBranchStrategy.CatchBubble]
     * instead of using the quasi-value [BubbleSentinel].
     */
    class ThrowStatement(
        pos: Position,
    ) : BaseTree(pos), Statement {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate148
        override val formatElementCount
            get() = 0
        override fun deepCopy(): ThrowStatement {
            return ThrowStatement(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ThrowStatement
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * A `when`/`switch`-like construct but the value switched over is an
     * *Int* so backends do not need to translate complex pattern matching
     * or decomposition.
     *
     * The computed jump statement does not generate a default `break` target.
     * An unlabelled `break` inside a case jumps to the end of the closest
     * loop.  There is no `switch` `case` style fall-through.  One case is
     * executed each time so there is no need to `break` from the end of a case.
     */
    class ComputedJumpStatement(
        pos: Position,
        caseExpr: Expression,
        cases: Iterable<ComputedJumpCase>,
        elseCase: ComputedJumpElse,
    ) : BaseTree(pos), NestingStatement {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate149
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.caseExpr
                1 -> FormattableTreeGroup(this.cases)
                2 -> this.elseCase
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _caseExpr: Expression
        var caseExpr: Expression
            get() = _caseExpr
            set(newValue) { _caseExpr = updateTreeConnection(_caseExpr, newValue) }
        private val _cases: MutableList<ComputedJumpCase> = mutableListOf()
        var cases: List<ComputedJumpCase>
            get() = _cases
            set(newValue) { updateTreeConnections(_cases, newValue) }
        private var _elseCase: ComputedJumpElse
        var elseCase: ComputedJumpElse
            get() = _elseCase
            set(newValue) { _elseCase = updateTreeConnection(_elseCase, newValue) }
        override val nestedStatements: List<Statement>
            get() =
                buildList {
                    cases.forEach { add(it.body) }
                    add(elseCase.body)
                }
        override fun deepCopy(): ComputedJumpStatement {
            return ComputedJumpStatement(pos, caseExpr = this.caseExpr.deepCopy(), cases = this.cases.deepCopy(), elseCase = this.elseCase.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ComputedJumpStatement && this.caseExpr == other.caseExpr && this.cases == other.cases && this.elseCase == other.elseCase
        }
        override fun hashCode(): Int {
            var hc = caseExpr.hashCode()
            hc = 31 * hc + cases.hashCode()
            hc = 31 * hc + elseCase.hashCode()
            return hc
        }
        init {
            this._caseExpr = updateTreeConnection(null, caseExpr)
            updateTreeConnections(this._cases, cases)
            this._elseCase = updateTreeConnection(null, elseCase)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ComputedJumpStatement).caseExpr },
                { n -> (n as ComputedJumpStatement).cases },
                { n -> (n as ComputedJumpStatement).elseCase },
            )
        }
    }

    class IfStatement(
        pos: Position,
        test: Expression,
        consequent: Statement,
        alternate: Statement?,
    ) : BaseTree(pos), NestingStatement {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (isElseIf) {
                    sharedCodeFormattingTemplate150
                } else if (hasElse) {
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
        private var _consequent: Statement
        var consequent: Statement
            get() = _consequent
            set(newValue) { _consequent = updateTreeConnection(_consequent, newValue) }
        private var _alternate: Statement?
        var alternate: Statement?
            get() = _alternate
            set(newValue) { _alternate = updateTreeConnection(_alternate, newValue) }
        override val nestedStatements: List<Statement>
            get() = listOfNotNull(consequent, alternate)
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

    class LabeledStatement(
        pos: Position,
        label: JumpLabel,
        statement: Statement,
    ) : BaseTree(pos), NestingStatement {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate153
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
        private var _label: JumpLabel
        var label: JumpLabel
            get() = _label
            set(newValue) { _label = updateTreeConnection(_label, newValue) }
        private var _statement: Statement
        var statement: Statement
            get() = _statement
            set(newValue) { _statement = updateTreeConnection(_statement, newValue) }
        override val nestedStatements: List<Statement>
            get() = listOf(statement)
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

    /**
     * Used when translating using [lang.temper.be.tmpl.BubbleBranchStrategy.CatchBubble]
     * to decompile transitions to recovery from bubble via `orelse`.
     *
     * See also [ThrowStatement].
     */
    class TryStatement(
        pos: Position,
        tried: Statement,
        recover: Statement,
    ) : BaseTree(pos), NestingStatement {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate154
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.tried
                1 -> this.recover
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _tried: Statement
        var tried: Statement
            get() = _tried
            set(newValue) { _tried = updateTreeConnection(_tried, newValue) }
        private var _recover: Statement
        var recover: Statement
            get() = _recover
            set(newValue) { _recover = updateTreeConnection(_recover, newValue) }
        override val nestedStatements: List<Statement>
            get() = listOf(tried, recover)
        override fun deepCopy(): TryStatement {
            return TryStatement(pos, tried = this.tried.deepCopy(), recover = this.recover.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TryStatement && this.tried == other.tried && this.recover == other.recover
        }
        override fun hashCode(): Int {
            var hc = tried.hashCode()
            hc = 31 * hc + recover.hashCode()
            return hc
        }
        init {
            this._tried = updateTreeConnection(null, tried)
            this._recover = updateTreeConnection(null, recover)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TryStatement).tried },
                { n -> (n as TryStatement).recover },
            )
        }
    }

    class WhileStatement(
        pos: Position,
        test: Expression,
        body: Statement,
    ) : BaseTree(pos), NestingStatement {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate155
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
        override val nestedStatements: List<Statement>
            get() = listOf(body)
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

    /** An identifier used in control-flow. */
    class JumpLabel(
        pos: Position,
        id: Id,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate26
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
        override fun deepCopy(): JumpLabel {
            return JumpLabel(pos, id = this.id.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is JumpLabel && this.id == other.id
        }
        override fun hashCode(): Int {
            return id.hashCode()
        }
        init {
            this._id = updateTreeConnection(null, id)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as JumpLabel).id },
            )
        }
        constructor(id: Id) : this(id.pos, id)
    }

    /**
     * An invocation of a type's setter.
     * Unlike [SetBackedProperty] these operations can be performed either within,
     * or outside (for `public` setters) a type's definition.
     * Setter invocations may fail.
     */
    class SetAbstractProperty(
        pos: Position,
        left: PropertyLValue,
        right: Expression,
    ) : BaseTree(pos), Handled, SetProperty {
        private var _left: PropertyLValue
        override var left: PropertyLValue
            get() = _left
            set(newValue) { _left = updateTreeConnection(_left, newValue) }
        private var _right: Expression
        override var right: Expression
            get() = _right
            set(newValue) { _right = updateTreeConnection(_right, newValue) }
        override fun deepCopy(): SetAbstractProperty {
            return SetAbstractProperty(pos, left = this.left.deepCopy(), right = this.right.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SetAbstractProperty && this.left == other.left && this.right == other.right
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
    }

    class ComputedJumpCase(
        pos: Position,
        values: Iterable<ConstIndex>,
        body: BlockStatement,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate156
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.values)
                1 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _values: MutableList<ConstIndex> = mutableListOf()
        var values: List<ConstIndex>
            get() = _values
            set(newValue) { updateTreeConnections(_values, newValue) }
        private var _body: BlockStatement
        var body: BlockStatement
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        override fun deepCopy(): ComputedJumpCase {
            return ComputedJumpCase(pos, values = this.values.deepCopy(), body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ComputedJumpCase && this.values == other.values && this.body == other.body
        }
        override fun hashCode(): Int {
            var hc = values.hashCode()
            hc = 31 * hc + body.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._values, values)
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ComputedJumpCase).values },
                { n -> (n as ComputedJumpCase).body },
            )
        }
    }

    class ComputedJumpElse(
        pos: Position,
        body: BlockStatement,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate157
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
        override fun deepCopy(): ComputedJumpElse {
            return ComputedJumpElse(pos, body = this.body.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ComputedJumpElse && this.body == other.body
        }
        override fun hashCode(): Int {
            return body.hashCode()
        }
        init {
            this._body = updateTreeConnection(null, body)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ComputedJumpElse).body },
            )
        }
    }

    class ConstIndex(
        pos: Position,
        var index: Int,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(OutputToken("$index", OutputTokenType.NumericValue))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): ConstIndex {
            return ConstIndex(pos, index = this.index)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ConstIndex && this.index == other.index
        }
        override fun hashCode(): Int {
            return index.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class BoilerplateCodeFoldStart(
        pos: Position,
    ) : BaseTree(pos), BoilerplateCodeFoldBoundary {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override val markerText: String
            get() = BoilerplateCodeFoldBoundary.START_MARKER_TEXT
        override fun deepCopy(): BoilerplateCodeFoldStart {
            return BoilerplateCodeFoldStart(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BoilerplateCodeFoldStart
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class BoilerplateCodeFoldEnd(
        pos: Position,
    ) : BaseTree(pos), BoilerplateCodeFoldBoundary {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override val markerText: String
            get() = BoilerplateCodeFoldBoundary.END_MARKER_TEXT
        override fun deepCopy(): BoilerplateCodeFoldEnd {
            return BoilerplateCodeFoldEnd(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BoilerplateCodeFoldEnd
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /** A reference may be used as an expression */
    sealed interface AnyReference : Tree, ExpressionOrCallable {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate26
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
        val id: Id
        val type: Descriptor
        override fun deepCopy(): AnyReference
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as AnyReference).id },
            )
        }
    }

    /** Converts between a [Callable] and an [Expression] where the content specifies a function */
    sealed interface FunInterfaceConversion : Tree, ExpressionOrCallable {
        val type: Descriptor
        override fun deepCopy(): FunInterfaceConversion
    }

    class ValueReference(
        pos: Position,
        override var type: Type2,
        var value: Value<*>,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            value.renderTo(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): ValueReference {
            return ValueReference(pos, type = this.type, value = this.value)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ValueReference && this.type == other.type && this.value == other.value
        }
        override fun hashCode(): Int {
            var hc = type.hashCode()
            hc = 31 * hc + value.hashCode()
            return hc
        }
        companion object {
            // Convenient overloads for construction of value reference where the
            // type is obvious given the value.
            operator fun invoke(
                pos: Position,
                value: Value<Boolean>,
                // Works around JVM ban on overloads with same erasure
                @Suppress("UNUSED_PARAMETER") jvmSigWorkaround: Boolean = false,
            ) = ValueReference(pos, WellKnownTypes.booleanType2, value)
            operator fun invoke(
                pos: Position,
                value: Value<Int>,
                @Suppress("UNUSED_PARAMETER") jvmSigWorkaround: Int = 0,
            ) = ValueReference(pos, WellKnownTypes.intType2, value)
            operator fun invoke(
                pos: Position,
                value: Value<String>,
                @Suppress("UNUSED_PARAMETER") jvmSigWorkaround: String = "",
            ) = ValueReference(pos, WellKnownTypes.stringType2, value)
            private val cmr = ChildMemberRelationships()
        }
    }

    /** A sentinel value indicating failure. */
    class BubbleSentinel(
        pos: Position,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate158
        override val formatElementCount
            get() = 0
        override val type: Type2
            get() = WellKnownTypes.bubbleType2
        override fun deepCopy(): BubbleSentinel {
            return BubbleSentinel(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BubbleSentinel
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /** A read of the variable, constant, or other referent with name [id]. */
    class Reference(
        pos: Position,
        id: Id,
        override var type: Type2,
    ) : BaseTree(pos), Expression, AnyReference {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        private var _id: Id
        override var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        override fun deepCopy(): Reference {
            return Reference(pos, id = this.id.deepCopy(), type = this.type)
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Reference && this.id == other.id && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + type.hashCode()
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
        }
        constructor(id: Id, type: Type2) : this(id.pos, id, type)
    }

    /** A read of `this` as identified in the containing method by [id]. */
    class This(
        pos: Position,
        id: Id,
        override var type: DefinedType,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate159
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
        override fun deepCopy(): This {
            return This(pos, id = this.id.deepCopy(), type = this.type)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is This && this.id == other.id && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + type.hashCode()
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as This).id },
            )
        }
        constructor(id: Id, type: DefinedType) : this(id.pos, id, type)
    }

    class CallExpression(
        pos: Position,
        fn: Callable,
        typeActuals: CallTypeActuals,
        parameters: Iterable<Actual>,
        override var type: Type2,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.Paren
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate160
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.fn
                1 -> this.typeActuals
                2 -> FormattableTreeGroup(this.parameters)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _fn: Callable
        var fn: Callable
            get() = _fn
            set(newValue) { _fn = updateTreeConnection(_fn, newValue) }
        private var _typeActuals: CallTypeActuals
        var typeActuals: CallTypeActuals
            get() = _typeActuals
            set(newValue) { _typeActuals = updateTreeConnection(_typeActuals, newValue) }
        private val _parameters: MutableList<Actual> = mutableListOf()
        var parameters: List<Actual>
            get() = _parameters
            set(newValue) { updateTreeConnections(_parameters, newValue) }
        val contextualizedSig: Signature2
            get() = contextualizeSig(fn.type, typeActuals.bindings)
        override fun deepCopy(): CallExpression {
            return CallExpression(pos, fn = this.fn.deepCopy(), typeActuals = this.typeActuals.deepCopy(), parameters = this.parameters.deepCopy(), type = this.type)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is CallExpression && this.fn == other.fn && this.typeActuals == other.typeActuals && this.parameters == other.parameters && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = fn.hashCode()
            hc = 31 * hc + typeActuals.hashCode()
            hc = 31 * hc + parameters.hashCode()
            hc = 31 * hc + type.hashCode()
            return hc
        }
        init {
            this._fn = updateTreeConnection(null, fn)
            this._typeActuals = updateTreeConnection(null, typeActuals)
            updateTreeConnections(this._parameters, parameters)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as CallExpression).fn },
                { n -> (n as CallExpression).typeActuals },
                { n -> (n as CallExpression).parameters },
            )
        }
        constructor(
            pos: Position,
            fn: Callable,
            parameters: Iterable<Actual>,
            type: Type2
        ) : this(pos, fn, CallTypeActuals.empty(fn.pos.rightEdge), parameters, type)
    }

    sealed interface Operation : Tree, Expression {
        override val operatorDefinition
            get() = op.kind
        override val type: Type2
            get() = op.tmpLOperator.returnType
        val op: Operator
        override fun deepCopy(): Operation
    }

    sealed interface CheckedRttiExpression : Tree, Expression {
        val expr: Expression
        val checkedType: AType
        val checkedFrontendType: Type2
        override fun deepCopy(): CheckedRttiExpression
    }

    /** Wraps an expression to assert, based on static analysis, that the result is not null. */
    class UncheckedNotNullExpression(
        pos: Position,
        expression: Expression,
        override var type: Type2,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate161
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
        override fun deepCopy(): UncheckedNotNullExpression {
            return UncheckedNotNullExpression(pos, expression = this.expression.deepCopy(), type = this.type)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is UncheckedNotNullExpression && this.expression == other.expression && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = expression.hashCode()
            hc = 31 * hc + type.hashCode()
            return hc
        }
        init {
            this._expression = updateTreeConnection(null, expression)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as UncheckedNotNullExpression).expression },
            )
        }
    }

    /** Wraps a callable as an expression as when de-referencing a function pointer */
    class FunInterfaceExpression(
        pos: Position,
        callable: Callable,
        override var type: Type2,
    ) : BaseTree(pos), Expression, FunInterfaceConversion {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.As
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate162
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.callable
                1 -> IndexableFormattableTreeElement.wrap(this.type)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _callable: Callable
        var callable: Callable
            get() = _callable
            set(newValue) { _callable = updateTreeConnection(_callable, newValue) }
        override fun deepCopy(): FunInterfaceExpression {
            return FunInterfaceExpression(pos, callable = this.callable.deepCopy(), type = this.type)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FunInterfaceExpression && this.callable == other.callable && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = callable.hashCode()
            hc = 31 * hc + type.hashCode()
            return hc
        }
        init {
            this._callable = updateTreeConnection(null, callable)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FunInterfaceExpression).callable },
            )
        }
    }

    /**
     * A reference to a specific position within a rest argument list
     * In the body of
     *
     *     let f(...rest) {
     *         return rest[2];
     *     };
     *
     * The `...rest` is the rest formal definition and `rest[2]` is a rest
     * parameter expression that refers to index 2 of it.
     */
    class RestParameterExpression(
        pos: Position,
        parameterName: Id,
        index: ConstIndex,
        override var type: Type2,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.Square
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate163
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.parameterName
                1 -> this.index
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _parameterName: Id
        var parameterName: Id
            get() = _parameterName
            set(newValue) { _parameterName = updateTreeConnection(_parameterName, newValue) }
        private var _index: ConstIndex
        var index: ConstIndex
            get() = _index
            set(newValue) { _index = updateTreeConnection(_index, newValue) }
        override fun deepCopy(): RestParameterExpression {
            return RestParameterExpression(pos, parameterName = this.parameterName.deepCopy(), index = this.index.deepCopy(), type = this.type)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is RestParameterExpression && this.parameterName == other.parameterName && this.index == other.index && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = parameterName.hashCode()
            hc = 31 * hc + index.hashCode()
            hc = 31 * hc + type.hashCode()
            return hc
        }
        init {
            this._parameterName = updateTreeConnection(null, parameterName)
            this._index = updateTreeConnection(null, index)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as RestParameterExpression).parameterName },
                { n -> (n as RestParameterExpression).index },
            )
        }
    }

    class RestParameterCountExpression(
        pos: Position,
        parameterName: Id,
    ) : BaseTree(pos), Expression {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.Dot
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate164
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.parameterName
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _parameterName: Id
        var parameterName: Id
            get() = _parameterName
            set(newValue) { _parameterName = updateTreeConnection(_parameterName, newValue) }
        override val type: Type2
            get() = WellKnownTypes.intType2
        override fun deepCopy(): RestParameterCountExpression {
            return RestParameterCountExpression(pos, parameterName = this.parameterName.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is RestParameterCountExpression && this.parameterName == other.parameterName
        }
        override fun hashCode(): Int {
            return parameterName.hashCode()
        }
        init {
            this._parameterName = updateTreeConnection(null, parameterName)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as RestParameterCountExpression).parameterName },
            )
        }
    }

    sealed interface PropertyReference : Tree {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.Dot
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate165
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.subject
                1 -> this.property
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        val subject: Subject
        val property: PropertyId
        override fun deepCopy(): PropertyReference
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as PropertyReference).subject },
                { n -> (n as PropertyReference).property },
            )
        }
    }

    sealed interface GetProperty : Tree, Expression, PropertyReference {
        override fun deepCopy(): GetProperty
    }

    class CallTypeActuals(
        pos: Position,
        types: Iterable<AType>,
        var bindings: Map<lang.temper.type.TypeFormal, Type2>,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (types.isNotEmpty()) {
                    sharedCodeFormattingTemplate30
                } else {
                    sharedCodeFormattingTemplate31
                }
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
        private val _types: MutableList<AType> = mutableListOf()
        var types: List<AType>
            get() = _types
            set(newValue) { updateTreeConnections(_types, newValue) }
        override fun deepCopy(): CallTypeActuals {
            return CallTypeActuals(pos, types = this.types.deepCopy(), bindings = this.bindings)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is CallTypeActuals && this.types == other.types && this.bindings == other.bindings
        }
        override fun hashCode(): Int {
            var hc = types.hashCode()
            hc = 31 * hc + bindings.hashCode()
            return hc
        }
        init {
            updateTreeConnections(this._types, types)
        }
        companion object {
            fun empty(pos: Position) = CallTypeActuals(pos, emptyList(), emptyMap())
            private val cmr = ChildMemberRelationships(
                { n -> (n as CallTypeActuals).types },
            )
        }
    }

    class RestSpread(
        pos: Position,
        parameterName: Id,
    ) : BaseTree(pos), Actual {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.Ellipsis
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate166
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.parameterName
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _parameterName: Id
        var parameterName: Id
            get() = _parameterName
            set(newValue) { _parameterName = updateTreeConnection(_parameterName, newValue) }
        override fun deepCopy(): RestSpread {
            return RestSpread(pos, parameterName = this.parameterName.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is RestSpread && this.parameterName == other.parameterName
        }
        override fun hashCode(): Int {
            return parameterName.hashCode()
        }
        init {
            this._parameterName = updateTreeConnection(null, parameterName)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as RestSpread).parameterName },
            )
        }
    }

    class InfixOperation(
        pos: Position,
        left: Expression,
        op: InfixOperator,
        right: Expression,
    ) : BaseTree(pos), Operation {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate167
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
        private var _left: Expression
        var left: Expression
            get() = _left
            set(newValue) { _left = updateTreeConnection(_left, newValue) }
        private var _op: InfixOperator
        override var op: InfixOperator
            get() = _op
            set(newValue) { _op = updateTreeConnection(_op, newValue) }
        private var _right: Expression
        var right: Expression
            get() = _right
            set(newValue) { _right = updateTreeConnection(_right, newValue) }
        override fun deepCopy(): InfixOperation {
            return InfixOperation(pos, left = this.left.deepCopy(), op = this.op.deepCopy(), right = this.right.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is InfixOperation && this.left == other.left && this.op == other.op && this.right == other.right
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
                { n -> (n as InfixOperation).left },
                { n -> (n as InfixOperation).op },
                { n -> (n as InfixOperation).right },
            )
        }
    }

    class PrefixOperation(
        pos: Position,
        op: PrefixOperator,
        operand: Expression,
    ) : BaseTree(pos), Operation {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate168
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
        private var _op: PrefixOperator
        override var op: PrefixOperator
            get() = _op
            set(newValue) { _op = updateTreeConnection(_op, newValue) }
        private var _operand: Expression
        var operand: Expression
            get() = _operand
            set(newValue) { _operand = updateTreeConnection(_operand, newValue) }
        override fun deepCopy(): PrefixOperation {
            return PrefixOperation(pos, op = this.op.deepCopy(), operand = this.operand.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PrefixOperation && this.op == other.op && this.operand == other.operand
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
                { n -> (n as PrefixOperation).op },
                { n -> (n as PrefixOperation).operand },
            )
        }
    }

    sealed interface Operator : Tree {
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(kind.outputToken)
        }
        val tmpLOperator: TmpLOperator
        val kind: TmpLOperatorDefinition
        override fun deepCopy(): Operator
    }

    /**
     * A reference to a named function.
     * This is not usable as an expression, as it has no type,
     * but is usable as a [Callable].
     */
    class FnReference(
        pos: Position,
        id: Id,
        override var type: Signature2,
    ) : BaseTree(pos), Callable, AnyReference {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        private var _id: Id
        override var id: Id
            get() = _id
            set(newValue) { _id = updateTreeConnection(_id, newValue) }
        override fun deepCopy(): FnReference {
            return FnReference(pos, id = this.id.deepCopy(), type = this.type)
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FnReference && this.id == other.id && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = id.hashCode()
            hc = 31 * hc + type.hashCode()
            return hc
        }
        init {
            this._id = updateTreeConnection(null, id)
        }
        constructor(id: Id, type: Signature2) : this(id.pos, id, type)
    }

    sealed interface MethodReferenceLike : Tree, Callable {
        val method: VisibleMemberShape?
        override fun deepCopy(): MethodReferenceLike
    }

    /** Allows invoking an expression as a callable as when calling a functional interface instance's `apply` method */
    class FunInterfaceCallable(
        pos: Position,
        expr: Expression,
        override var type: Signature2,
    ) : BaseTree(pos), Callable, FunInterfaceConversion {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.As
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate169
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expr
                1 -> IndexableFormattableTreeElement.wrap(this.type)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expr: Expression
        var expr: Expression
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        override fun deepCopy(): FunInterfaceCallable {
            return FunInterfaceCallable(pos, expr = this.expr.deepCopy(), type = this.type)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FunInterfaceCallable && this.expr == other.expr && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = expr.hashCode()
            hc = 31 * hc + type.hashCode()
            return hc
        }
        init {
            this._expr = updateTreeConnection(null, expr)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FunInterfaceCallable).expr },
            )
        }
    }

    /**
     * A reference to a thing on an object, combines with [CallExpression] to represent a whole method invocation
     */
    class MethodReference(
        pos: Position,
        subject: Subject,
        methodName: DotName,
        override var type: Signature2,
        override var method: VisibleMemberShape?,
        var adjustments: SignatureAdjustments? = null,
    ) : BaseTree(pos), MethodReferenceLike {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.Dot
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate165
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.subject
                1 -> this.methodName
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _subject: Subject
        var subject: Subject
            get() = _subject
            set(newValue) { _subject = updateTreeConnection(_subject, newValue) }
        private var _methodName: DotName
        var methodName: DotName
            get() = _methodName
            set(newValue) { _methodName = updateTreeConnection(_methodName, newValue) }
        override fun deepCopy(): MethodReference {
            return MethodReference(pos, subject = this.subject.deepCopy(), methodName = this.methodName.deepCopy(), type = this.type, method = this.method, adjustments = this.adjustments)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is MethodReference && this.subject == other.subject && this.methodName == other.methodName && this.type == other.type && this.method == other.method && this.adjustments == other.adjustments
        }
        override fun hashCode(): Int {
            var hc = subject.hashCode()
            hc = 31 * hc + methodName.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + (method?.hashCode() ?: 0)
            hc = 31 * hc + (adjustments?.hashCode() ?: 0)
            return hc
        }
        init {
            this._subject = updateTreeConnection(null, subject)
            this._methodName = updateTreeConnection(null, methodName)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as MethodReference).subject },
                { n -> (n as MethodReference).methodName },
            )
        }
    }

    /** A callable reference to a type's constructors. */
    class ConstructorReference(
        pos: Position,
        typeName: TypeName,
        override var type: Signature2,
        override var method: VisibleMemberShape? = null,
    ) : BaseTree(pos), MethodReferenceLike {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate170
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
        private var _typeName: TypeName
        var typeName: TypeName
            get() = _typeName
            set(newValue) { _typeName = updateTreeConnection(_typeName, newValue) }
        val typeShape: TypeShape
            get() = typeName.sourceDefinition as TypeShape
        override fun deepCopy(): ConstructorReference {
            return ConstructorReference(pos, typeName = this.typeName.deepCopy(), type = this.type, method = this.method)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ConstructorReference && this.typeName == other.typeName && this.type == other.type && this.method == other.method
        }
        override fun hashCode(): Int {
            var hc = typeName.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + (method?.hashCode() ?: 0)
            return hc
        }
        init {
            this._typeName = updateTreeConnection(null, typeName)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ConstructorReference).typeName },
            )
        }
    }

    sealed interface TypeName : Tree, Subject {
        val sourceDefinition: TypeDefinition
        override fun deepCopy(): TypeName
    }

    class InfixOperator(
        pos: Position,
        override var tmpLOperator: TmpLOperator.Infix,
    ) : BaseTree(pos), Operator {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override val kind: TmpLOperatorDefinition.Infix
            get() = tmpLOperator.kind
        override fun deepCopy(): InfixOperator {
            return InfixOperator(pos, tmpLOperator = this.tmpLOperator)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is InfixOperator && this.tmpLOperator == other.tmpLOperator
        }
        override fun hashCode(): Int {
            return tmpLOperator.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class PrefixOperator(
        pos: Position,
        override var tmpLOperator: TmpLOperator.Prefix,
    ) : BaseTree(pos), Operator {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override val kind: TmpLOperatorDefinition.Prefix
            get() = tmpLOperator.kind
        override fun deepCopy(): PrefixOperator {
            return PrefixOperator(pos, tmpLOperator = this.tmpLOperator)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PrefixOperator && this.tmpLOperator == other.tmpLOperator
        }
        override fun hashCode(): Int {
            return tmpLOperator.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class InstanceOfExpression(
        pos: Position,
        expr: Expression,
        checkedType: AType,
        override var checkedFrontendType: Type2,
    ) : BaseTree(pos), CheckedRttiExpression {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.Instanceof
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate171
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expr
                1 -> this.checkedType
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expr: Expression
        override var expr: Expression
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        private var _checkedType: AType
        override var checkedType: AType
            get() = _checkedType
            set(newValue) { _checkedType = updateTreeConnection(_checkedType, newValue) }
        override val type: Type2
            get() = WellKnownTypes.booleanType2
        override fun deepCopy(): InstanceOfExpression {
            return InstanceOfExpression(pos, expr = this.expr.deepCopy(), checkedType = this.checkedType.deepCopy(), checkedFrontendType = this.checkedFrontendType)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is InstanceOfExpression && this.expr == other.expr && this.checkedType == other.checkedType && this.checkedFrontendType == other.checkedFrontendType
        }
        override fun hashCode(): Int {
            var hc = expr.hashCode()
            hc = 31 * hc + checkedType.hashCode()
            hc = 31 * hc + checkedFrontendType.hashCode()
            return hc
        }
        init {
            this._expr = updateTreeConnection(null, expr)
            this._checkedType = updateTreeConnection(null, checkedType)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as InstanceOfExpression).expr },
                { n -> (n as InstanceOfExpression).checkedType },
            )
        }
    }

    /** A cast based on a type tag check. */
    class CastExpression(
        pos: Position,
        expr: Expression,
        checkedType: AType,
        override var type: Type2,
        override var checkedFrontendType: Type2,
    ) : BaseTree(pos), CheckedRttiExpression {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.Paren
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate172
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.expr
                1 -> this.checkedType
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _expr: Expression
        override var expr: Expression
            get() = _expr
            set(newValue) { _expr = updateTreeConnection(_expr, newValue) }
        private var _checkedType: AType
        override var checkedType: AType
            get() = _checkedType
            set(newValue) { _checkedType = updateTreeConnection(_checkedType, newValue) }
        override fun deepCopy(): CastExpression {
            return CastExpression(pos, expr = this.expr.deepCopy(), checkedType = this.checkedType.deepCopy(), type = this.type, checkedFrontendType = this.checkedFrontendType)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is CastExpression && this.expr == other.expr && this.checkedType == other.checkedType && this.type == other.type && this.checkedFrontendType == other.checkedFrontendType
        }
        override fun hashCode(): Int {
            var hc = expr.hashCode()
            hc = 31 * hc + checkedType.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + checkedFrontendType.hashCode()
            return hc
        }
        init {
            this._expr = updateTreeConnection(null, expr)
            this._checkedType = updateTreeConnection(null, checkedType)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as CastExpression).expr },
                { n -> (n as CastExpression).checkedType },
            )
        }
    }

    /**
     * A dot name is either a reference to a backing property from within a class which
     * has a resolved name, or it's a reference to a dot name that specifies a getter/setter
     * pair to use.
     */
    sealed interface PropertyId : Tree {
        override fun deepCopy(): PropertyId
    }

    class InternalPropertyId(
        pos: Position,
        name: Id,
    ) : BaseTree(pos), PropertyId {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate26
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
        private var _name: Id
        var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override fun deepCopy(): InternalPropertyId {
            return InternalPropertyId(pos, name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is InternalPropertyId && this.name == other.name
        }
        override fun hashCode(): Int {
            return name.hashCode()
        }
        init {
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as InternalPropertyId).name },
            )
        }
        constructor(name: Id) : this(name.pos, name)
    }

    class ExternalPropertyId(
        pos: Position,
        name: DotName,
    ) : BaseTree(pos), PropertyId {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate26
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
        private var _name: DotName
        var name: DotName
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        override fun deepCopy(): ExternalPropertyId {
            return ExternalPropertyId(pos, name = this.name.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ExternalPropertyId && this.name == other.name
        }
        override fun hashCode(): Int {
            return name.hashCode()
        }
        init {
            this._name = updateTreeConnection(null, name)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ExternalPropertyId).name },
            )
        }
        constructor(name: DotName) : this(name.pos, name)
    }

    /** A property reference used as the left side of an assignment.  *L* stands for left-hand. */
    class PropertyLValue(
        pos: Position,
        subject: Subject,
        property: PropertyId,
    ) : BaseTree(pos), PropertyReference {
        private var _subject: Subject
        override var subject: Subject
            get() = _subject
            set(newValue) { _subject = updateTreeConnection(_subject, newValue) }
        private var _property: PropertyId
        override var property: PropertyId
            get() = _property
            set(newValue) { _property = updateTreeConnection(_property, newValue) }
        override fun deepCopy(): PropertyLValue {
            return PropertyLValue(pos, subject = this.subject.deepCopy(), property = this.property.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is PropertyLValue && this.subject == other.subject && this.property == other.property
        }
        override fun hashCode(): Int {
            var hc = subject.hashCode()
            hc = 31 * hc + property.hashCode()
            return hc
        }
        init {
            this._subject = updateTreeConnection(null, subject)
            this._property = updateTreeConnection(null, property)
        }
    }

    /**
     * Reads a property directly.  These operations can only be performed from within
     * a `class`'s definition and cannot fail assuming that static checks passed for
     * `const` property initialization and use before initialization in constructor
     * and methods.
     */
    class GetBackedProperty(
        pos: Position,
        subject: Subject,
        property: PropertyId,
        override var type: Type2,
    ) : BaseTree(pos), GetProperty {
        private var _subject: Subject
        override var subject: Subject
            get() = _subject
            set(newValue) { _subject = updateTreeConnection(_subject, newValue) }
        private var _property: PropertyId
        override var property: PropertyId
            get() = _property
            set(newValue) { _property = updateTreeConnection(_property, newValue) }
        override fun deepCopy(): GetBackedProperty {
            return GetBackedProperty(pos, subject = this.subject.deepCopy(), property = this.property.deepCopy(), type = this.type)
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is GetBackedProperty && this.subject == other.subject && this.property == other.property && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = subject.hashCode()
            hc = 31 * hc + property.hashCode()
            hc = 31 * hc + type.hashCode()
            return hc
        }
        init {
            this._subject = updateTreeConnection(null, subject)
            this._property = updateTreeConnection(null, property)
        }
    }

    /**
     * An invocation of a type's getter.
     * Unlike [GetBackedProperty] these operations can be performed either within,
     * or outside (for `public` setters) a type's definition.
     * Getter invocations may fail.
     */
    class GetAbstractProperty(
        pos: Position,
        subject: Expression,
        property: PropertyId,
        override var type: Type2,
    ) : BaseTree(pos), GetProperty {
        private var _subject: Expression
        override var subject: Expression
            get() = _subject
            set(newValue) { _subject = updateTreeConnection(_subject, newValue) }
        private var _property: PropertyId
        override var property: PropertyId
            get() = _property
            set(newValue) { _property = updateTreeConnection(_property, newValue) }
        override fun deepCopy(): GetAbstractProperty {
            return GetAbstractProperty(pos, subject = this.subject.deepCopy(), property = this.property.deepCopy(), type = this.type)
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is GetAbstractProperty && this.subject == other.subject && this.property == other.property && this.type == other.type
        }
        override fun hashCode(): Int {
            var hc = subject.hashCode()
            hc = 31 * hc + property.hashCode()
            hc = 31 * hc + type.hashCode()
            return hc
        }
        init {
            this._subject = updateTreeConnection(null, subject)
            this._property = updateTreeConnection(null, property)
        }
    }

    /**
     * Sets a property directly.  These operations can only be performed from within
     * a `class`'s definition and cannot fail assuming that static checks passed for
     * `const` property initialization in constructor and methods.
     */
    class SetBackedProperty(
        pos: Position,
        left: PropertyLValue,
        right: Expression,
    ) : BaseTree(pos), SetProperty {
        private var _left: PropertyLValue
        override var left: PropertyLValue
            get() = _left
            set(newValue) { _left = updateTreeConnection(_left, newValue) }
        private var _right: Expression
        override var right: Expression
            get() = _right
            set(newValue) { _right = updateTreeConnection(_right, newValue) }
        override fun deepCopy(): SetBackedProperty {
            return SetBackedProperty(pos, left = this.left.deepCopy(), right = this.right.deepCopy())
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is SetBackedProperty && this.left == other.left && this.right == other.right
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
    }

    sealed interface CompoundType : Tree, Type {
        override fun deepCopy(): CompoundType
    }

    sealed interface SpecialType : Tree, Type {
        override fun deepCopy(): SpecialType
    }

    class FunctionType(
        pos: Position,
        typeParameters: ATypeParameters,
        valueFormals: ValueFormalList,
        returnType: AType,
    ) : BaseTree(pos), CompoundType {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.Colon
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate173
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.typeParameters
                1 -> this.valueFormals
                2 -> this.returnType
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _typeParameters: ATypeParameters
        var typeParameters: ATypeParameters
            get() = _typeParameters
            set(newValue) { _typeParameters = updateTreeConnection(_typeParameters, newValue) }
        private var _valueFormals: ValueFormalList
        var valueFormals: ValueFormalList
            get() = _valueFormals
            set(newValue) { _valueFormals = updateTreeConnection(_valueFormals, newValue) }
        private var _returnType: AType
        var returnType: AType
            get() = _returnType
            set(newValue) { _returnType = updateTreeConnection(_returnType, newValue) }
        override fun deepCopy(): FunctionType {
            return FunctionType(pos, typeParameters = this.typeParameters.deepCopy(), valueFormals = this.valueFormals.deepCopy(), returnType = this.returnType.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is FunctionType && this.typeParameters == other.typeParameters && this.valueFormals == other.valueFormals && this.returnType == other.returnType
        }
        override fun hashCode(): Int {
            var hc = typeParameters.hashCode()
            hc = 31 * hc + valueFormals.hashCode()
            hc = 31 * hc + returnType.hashCode()
            return hc
        }
        init {
            this._typeParameters = updateTreeConnection(null, typeParameters)
            this._valueFormals = updateTreeConnection(null, valueFormals)
            this._returnType = updateTreeConnection(null, returnType)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FunctionType).typeParameters },
                { n -> (n as FunctionType).valueFormals },
                { n -> (n as FunctionType).returnType },
            )
        }
    }

    class TypeUnion(
        pos: Position,
        types: Iterable<Type>,
    ) : BaseTree(pos), CompoundType {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.Bar
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate174
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
        override fun deepCopy(): TypeUnion {
            return TypeUnion(pos, types = this.types.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeUnion && this.types == other.types
        }
        override fun hashCode(): Int {
            return types.hashCode()
        }
        init {
            updateTreeConnections(this._types, types)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeUnion).types },
            )
        }
    }

    class TypeIntersection(
        pos: Position,
        types: Iterable<Type>,
    ) : BaseTree(pos), CompoundType {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.Amp
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate175
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
        override fun deepCopy(): TypeIntersection {
            return TypeIntersection(pos, types = this.types.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TypeIntersection && this.types == other.types
        }
        override fun hashCode(): Int {
            return types.hashCode()
        }
        init {
            updateTreeConnections(this._types, types)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeIntersection).types },
            )
        }
    }

    class TopType(
        pos: Position,
    ) : BaseTree(pos), SpecialType {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate176
        override val formatElementCount
            get() = 0
        override fun deepCopy(): TopType {
            return TopType(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TopType
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class BubbleType(
        pos: Position,
    ) : BaseTree(pos), SpecialType {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate177
        override val formatElementCount
            get() = 0
        override fun deepCopy(): BubbleType {
            return BubbleType(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is BubbleType
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    class NeverType(
        pos: Position,
    ) : BaseTree(pos), SpecialType {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate178
        override val formatElementCount
            get() = 0
        override fun deepCopy(): NeverType {
            return NeverType(pos)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is NeverType
        }
        override fun hashCode(): Int {
            return 0
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /** A type name that is defined in Temper, not connected to a backend-specific type. */
    class TemperTypeName(
        pos: Position,
        var typeDefinition: TypeDefinition,
    ) : BaseTree(pos), TypeName {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            typeDefinition.renderName(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override val sourceDefinition: TypeDefinition
            get() = this.typeDefinition
        override fun deepCopy(): TemperTypeName {
            return TemperTypeName(pos, typeDefinition = this.typeDefinition)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is TemperTypeName && this.typeDefinition == other.typeDefinition
        }
        override fun hashCode(): Int {
            return typeDefinition.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /**
     * Stores a value created by the backend's [SupportNetwork]
     * which should be meaningful to the backend's translator.
     *
     * For example, different target languages might want to
     * connect a Temper type like *Date* to types in their language:
     *
     * - JavaScript might want to connect to a JS Temporal type
     *   by representing `globalThis.Temporal.PlainDate` as an
     *   instance of a class that the *JsTranslator* understands.
     * - The Java backend might want to connect to
     *   `java.time.LocalDate` and bundle `java.time` and
     *   `LocalDate` separately along with any Maven dependencies
     *   that requires.
     * - Python might want to pack the Python module name `datetime`
     *   and the imported name `date` into a bundle object.
     * - Some older languages might want to connect to their
     *   *String* type but assuming that the string is formatted
     *   like "YYYYMMDD".  Notating that latter fact might help
     *   when generating auto-documentation comments.
     */
    class ConnectedToTypeName(
        pos: Position,
        override var sourceDefinition: TypeShape,
        var name: TargetLanguageTypeName,
    ) : BaseTree(pos), TypeName {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            name.renderTo(tokenSink)
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): ConnectedToTypeName {
            return ConnectedToTypeName(pos, sourceDefinition = this.sourceDefinition, name = this.name)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ConnectedToTypeName && this.sourceDefinition == other.sourceDefinition && this.name == other.name
        }
        override fun hashCode(): Int {
            var hc = sourceDefinition.hashCode()
            hc = 31 * hc + name.hashCode()
            return hc
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /** Describes the types and other requirements for inputs to a function type. */
    class ValueFormalList(
        pos: Position,
        formals: Iterable<ValueFormal>,
        rest: AType?,
    ) : BaseTree(pos) {
        override val operatorDefinition
            get() = TmpLOperatorDefinition.ParenGroup
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (formals.isNotEmpty() && rest != null) {
                    sharedCodeFormattingTemplate179
                } else if (formals.isNotEmpty()) {
                    sharedCodeFormattingTemplate65
                } else if (rest != null) {
                    sharedCodeFormattingTemplate180
                } else {
                    sharedCodeFormattingTemplate181
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.formals)
                1 -> this.rest ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _formals: MutableList<ValueFormal> = mutableListOf()
        var formals: List<ValueFormal>
            get() = _formals
            set(newValue) { updateTreeConnections(_formals, newValue) }
        private var _rest: AType?
        var rest: AType?
            get() = _rest
            set(newValue) { _rest = updateTreeConnection(_rest, newValue) }
        override fun deepCopy(): ValueFormalList {
            return ValueFormalList(pos, formals = this.formals.deepCopy(), rest = this.rest?.deepCopy())
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ValueFormalList && this.formals == other.formals && this.rest == other.rest
        }
        override fun hashCode(): Int {
            var hc = formals.hashCode()
            hc = 31 * hc + (rest?.hashCode() ?: 0)
            return hc
        }
        init {
            updateTreeConnections(this._formals, formals)
            this._rest = updateTreeConnection(null, rest)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ValueFormalList).formals },
                { n -> (n as ValueFormalList).rest },
            )
        }
    }

    class ValueFormal(
        pos: Position,
        name: OriginalName?,
        type: AType,
        var isOptional: Boolean,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (isOptional && name != null) {
                    sharedCodeFormattingTemplate182
                } else if (isOptional) {
                    sharedCodeFormattingTemplate183
                } else {
                    sharedCodeFormattingTemplate184
                }
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.name ?: FormattableTreeGroup.empty
                1 -> this.type
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private var _name: OriginalName?
        var name: OriginalName?
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _type: AType
        var type: AType
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        override fun deepCopy(): ValueFormal {
            return ValueFormal(pos, name = this.name?.deepCopy(), type = this.type.deepCopy(), isOptional = this.isOptional)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is ValueFormal && this.name == other.name && this.type == other.type && this.isOptional == other.isOptional
        }
        override fun hashCode(): Int {
            var hc = (name?.hashCode() ?: 0)
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + isOptional.hashCode()
            return hc
        }
        init {
            this._name = updateTreeConnection(null, name)
            this._type = updateTreeConnection(null, type)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ValueFormal).name },
                { n -> (n as ValueFormal).type },
            )
        }
    }

    class VisibilityModifier(
        pos: Position,
        var visibility: Visibility,
    ) : BaseTree(pos) {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(OutputToken(this.visibility.name, OutputTokenType.Word))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override fun deepCopy(): VisibilityModifier {
            return VisibilityModifier(pos, visibility = this.visibility)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is VisibilityModifier && this.visibility == other.visibility
        }
        override fun hashCode(): Int {
            return visibility.hashCode()
        }
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    sealed interface InstanceMember : Tree {
        override fun deepCopy(): InstanceMember
    }

    class InstanceProperty(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        dotName: DotName,
        name: Id,
        type: AType,
        visibility: VisibilityModifier,
        override var assignOnce: Boolean,
        override var descriptor: Type2?,
        override var memberShape: PropertyShape,
    ) : BaseTree(pos), InstanceMember, Property {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _dotName: DotName
        override var dotName: DotName
            get() = _dotName
            set(newValue) { _dotName = updateTreeConnection(_dotName, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _type: AType
        override var type: AType
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _visibility: VisibilityModifier
        override var visibility: VisibilityModifier
            get() = _visibility
            set(newValue) { _visibility = updateTreeConnection(_visibility, newValue) }
        override fun deepCopy(): InstanceProperty {
            return InstanceProperty(pos, metadata = this.metadata, dotName = this.dotName.deepCopy(), name = this.name.deepCopy(), type = this.type.deepCopy(), visibility = this.visibility.deepCopy(), assignOnce = this.assignOnce, descriptor = this.descriptor, memberShape = this.memberShape)
        }
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is InstanceProperty && this.metadata == other.metadata && this.dotName == other.dotName && this.name == other.name && this.type == other.type && this.visibility == other.visibility && this.assignOnce == other.assignOnce && this.descriptor == other.descriptor && this.memberShape == other.memberShape
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + dotName.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + visibility.hashCode()
            hc = 31 * hc + assignOnce.hashCode()
            hc = 31 * hc + (descriptor?.hashCode() ?: 0)
            hc = 31 * hc + memberShape.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._dotName = updateTreeConnection(null, dotName)
            this._name = updateTreeConnection(null, name)
            this._type = updateTreeConnection(null, type)
            this._visibility = updateTreeConnection(null, visibility)
        }
    }

    sealed interface InstanceMethod : Tree, InstanceMember, Method {
        val overridden: List<SuperTypeMethod>
        val adjustments: SignatureAdjustments?
        override fun deepCopy(): InstanceMethod
    }

    class StaticProperty(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        dotName: DotName,
        name: Id,
        type: AType,
        expression: Expression,
        visibility: VisibilityModifier,
        override var descriptor: Type2?,
        override var memberShape: VisibleMemberShape,
    ) : BaseTree(pos), StaticMember, Property {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (sameDotName) {
                    sharedCodeFormattingTemplate185
                } else {
                    sharedCodeFormattingTemplate186
                }
        override val formatElementCount
            get() = 5
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.dotName
                2 -> this.name
                3 -> this.type
                4 -> this.expression
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _dotName: DotName
        override var dotName: DotName
            get() = _dotName
            set(newValue) { _dotName = updateTreeConnection(_dotName, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _type: AType
        override var type: AType
            get() = _type
            set(newValue) { _type = updateTreeConnection(_type, newValue) }
        private var _expression: Expression
        var expression: Expression
            get() = _expression
            set(newValue) { _expression = updateTreeConnection(_expression, newValue) }
        private var _visibility: VisibilityModifier
        override var visibility: VisibilityModifier
            get() = _visibility
            set(newValue) { _visibility = updateTreeConnection(_visibility, newValue) }
        override val assignOnce: Boolean
            get() = true
        override fun deepCopy(): StaticProperty {
            return StaticProperty(pos, metadata = this.metadata, dotName = this.dotName.deepCopy(), name = this.name.deepCopy(), type = this.type.deepCopy(), expression = this.expression.deepCopy(), visibility = this.visibility.deepCopy(), descriptor = this.descriptor, memberShape = this.memberShape)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StaticProperty && this.metadata == other.metadata && this.dotName == other.dotName && this.name == other.name && this.type == other.type && this.expression == other.expression && this.visibility == other.visibility && this.descriptor == other.descriptor && this.memberShape == other.memberShape
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + dotName.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + type.hashCode()
            hc = 31 * hc + expression.hashCode()
            hc = 31 * hc + visibility.hashCode()
            hc = 31 * hc + (descriptor?.hashCode() ?: 0)
            hc = 31 * hc + memberShape.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._dotName = updateTreeConnection(null, dotName)
            this._name = updateTreeConnection(null, name)
            this._type = updateTreeConnection(null, type)
            this._expression = updateTreeConnection(null, expression)
            this._visibility = updateTreeConnection(null, visibility)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as StaticProperty).dotName },
                { n -> (n as StaticProperty).name },
                { n -> (n as StaticProperty).type },
                { n -> (n as StaticProperty).expression },
                { n -> (n as StaticProperty).visibility },
            )
        }
    }

    /**
     * This is a subtype of [Method] and [DotAccessible] which is
     * redundant since its subtypes are too, but it's useful to allow named
     * methods (not constructors) to be translated by the same code path.
     */
    sealed interface DotAccessibleMethod : Tree, Method, DotAccessible {
        override fun deepCopy(): DotAccessibleMethod
    }

    class StaticMethod(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        dotName: DotName,
        name: Id,
        typeParameters: ATypeParameters,
        parameters: Parameters,
        returnType: AType,
        body: BlockStatement?,
        visibility: VisibilityModifier,
        override var mayYield: Boolean,
        override var memberShape: VisibleMemberShape,
    ) : BaseTree(pos), StaticMember, Method, DotAccessibleMethod {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (sameDotName && body != null) {
                    sharedCodeFormattingTemplate187
                } else if (sameDotName) {
                    sharedCodeFormattingTemplate188
                } else if (body != null) {
                    sharedCodeFormattingTemplate189
                } else {
                    sharedCodeFormattingTemplate190
                }
        override val formatElementCount
            get() = 7
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.dotName
                2 -> this.name
                3 -> this.typeParameters
                4 -> this.parameters
                5 -> this.returnType
                6 -> this.body ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _dotName: DotName
        override var dotName: DotName
            get() = _dotName
            set(newValue) { _dotName = updateTreeConnection(_dotName, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _typeParameters: ATypeParameters
        override var typeParameters: ATypeParameters
            get() = _typeParameters
            set(newValue) { _typeParameters = updateTreeConnection(_typeParameters, newValue) }
        private var _parameters: Parameters
        override var parameters: Parameters
            get() = _parameters
            set(newValue) { _parameters = updateTreeConnection(_parameters, newValue) }
        private var _returnType: AType
        override var returnType: AType
            get() = _returnType
            set(newValue) { _returnType = updateTreeConnection(_returnType, newValue) }
        private var _body: BlockStatement?
        override var body: BlockStatement?
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        private var _visibility: VisibilityModifier
        override var visibility: VisibilityModifier
            get() = _visibility
            set(newValue) { _visibility = updateTreeConnection(_visibility, newValue) }
        override val descriptor: Signature2
            get() = sig
        override fun deepCopy(): StaticMethod {
            return StaticMethod(pos, metadata = this.metadata, dotName = this.dotName.deepCopy(), name = this.name.deepCopy(), typeParameters = this.typeParameters.deepCopy(), parameters = this.parameters.deepCopy(), returnType = this.returnType.deepCopy(), body = this.body?.deepCopy(), visibility = this.visibility.deepCopy(), mayYield = this.mayYield, memberShape = this.memberShape)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is StaticMethod && this.metadata == other.metadata && this.dotName == other.dotName && this.name == other.name && this.typeParameters == other.typeParameters && this.parameters == other.parameters && this.returnType == other.returnType && this.body == other.body && this.visibility == other.visibility && this.mayYield == other.mayYield && this.memberShape == other.memberShape
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + dotName.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + typeParameters.hashCode()
            hc = 31 * hc + parameters.hashCode()
            hc = 31 * hc + returnType.hashCode()
            hc = 31 * hc + (body?.hashCode() ?: 0)
            hc = 31 * hc + visibility.hashCode()
            hc = 31 * hc + mayYield.hashCode()
            hc = 31 * hc + memberShape.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._dotName = updateTreeConnection(null, dotName)
            this._name = updateTreeConnection(null, name)
            this._typeParameters = updateTreeConnection(null, typeParameters)
            this._parameters = updateTreeConnection(null, parameters)
            this._returnType = updateTreeConnection(null, returnType)
            this._body = updateTreeConnection(null, body)
            this._visibility = updateTreeConnection(null, visibility)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as StaticMethod).dotName },
                { n -> (n as StaticMethod).name },
                { n -> (n as StaticMethod).typeParameters },
                { n -> (n as StaticMethod).parameters },
                { n -> (n as StaticMethod).returnType },
                { n -> (n as StaticMethod).body },
                { n -> (n as StaticMethod).visibility },
            )
        }
    }

    class Constructor(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        name: Id,
        typeParameters: ATypeParameters,
        parameters: Parameters,
        body: BlockStatement,
        returnType: AType,
        visibility: VisibilityModifier,
        override var memberShape: VisibleMemberShape,
        override var adjustments: SignatureAdjustments? = null,
    ) : BaseTree(pos), InstanceMethod {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate191
        override val formatElementCount
            get() = 5
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.name
                2 -> this.typeParameters
                3 -> this.parameters
                4 -> this.body
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _typeParameters: ATypeParameters
        override var typeParameters: ATypeParameters
            get() = _typeParameters
            set(newValue) { _typeParameters = updateTreeConnection(_typeParameters, newValue) }
        private var _parameters: Parameters
        override var parameters: Parameters
            get() = _parameters
            set(newValue) { _parameters = updateTreeConnection(_parameters, newValue) }
        private var _body: BlockStatement
        override var body: BlockStatement
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        private var _returnType: AType
        override var returnType: AType
            get() = _returnType
            set(newValue) { _returnType = updateTreeConnection(_returnType, newValue) }
        private var _visibility: VisibilityModifier
        override var visibility: VisibilityModifier
            get() = _visibility
            set(newValue) { _visibility = updateTreeConnection(_visibility, newValue) }
        override val mayYield: Boolean
            get() = false
        override val overridden: List<SuperTypeMethod>
            get() = emptyList()
        override fun deepCopy(): Constructor {
            return Constructor(pos, metadata = this.metadata, name = this.name.deepCopy(), typeParameters = this.typeParameters.deepCopy(), parameters = this.parameters.deepCopy(), body = this.body.deepCopy(), returnType = this.returnType.deepCopy(), visibility = this.visibility.deepCopy(), memberShape = this.memberShape, adjustments = this.adjustments)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Constructor && this.metadata == other.metadata && this.name == other.name && this.typeParameters == other.typeParameters && this.parameters == other.parameters && this.body == other.body && this.returnType == other.returnType && this.visibility == other.visibility && this.memberShape == other.memberShape && this.adjustments == other.adjustments
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + typeParameters.hashCode()
            hc = 31 * hc + parameters.hashCode()
            hc = 31 * hc + body.hashCode()
            hc = 31 * hc + returnType.hashCode()
            hc = 31 * hc + visibility.hashCode()
            hc = 31 * hc + memberShape.hashCode()
            hc = 31 * hc + (adjustments?.hashCode() ?: 0)
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._name = updateTreeConnection(null, name)
            this._typeParameters = updateTreeConnection(null, typeParameters)
            this._parameters = updateTreeConnection(null, parameters)
            this._body = updateTreeConnection(null, body)
            this._returnType = updateTreeConnection(null, returnType)
            this._visibility = updateTreeConnection(null, visibility)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Constructor).name },
                { n -> (n as Constructor).typeParameters },
                { n -> (n as Constructor).parameters },
                { n -> (n as Constructor).body },
                { n -> (n as Constructor).returnType },
                { n -> (n as Constructor).visibility },
            )
        }
    }

    class NormalMethod(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        dotName: DotName,
        name: Id,
        typeParameters: ATypeParameters,
        parameters: Parameters,
        returnType: AType,
        body: BlockStatement?,
        visibility: VisibilityModifier,
        overridden: Iterable<SuperTypeMethod>,
        override var mayYield: Boolean,
        override var memberShape: VisibleMemberShape,
        override var adjustments: SignatureAdjustments? = null,
    ) : BaseTree(pos), InstanceMethod, DotAccessibleMethod, DotAccessible {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (sameDotName && body != null) {
                    sharedCodeFormattingTemplate192
                } else if (sameDotName) {
                    sharedCodeFormattingTemplate193
                } else if (body != null) {
                    sharedCodeFormattingTemplate194
                } else {
                    sharedCodeFormattingTemplate195
                }
        override val formatElementCount
            get() = 7
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.dotName
                2 -> this.name
                3 -> this.typeParameters
                4 -> this.parameters
                5 -> this.returnType
                6 -> this.body ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _dotName: DotName
        override var dotName: DotName
            get() = _dotName
            set(newValue) { _dotName = updateTreeConnection(_dotName, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _typeParameters: ATypeParameters
        override var typeParameters: ATypeParameters
            get() = _typeParameters
            set(newValue) { _typeParameters = updateTreeConnection(_typeParameters, newValue) }
        private var _parameters: Parameters
        override var parameters: Parameters
            get() = _parameters
            set(newValue) { _parameters = updateTreeConnection(_parameters, newValue) }
        private var _returnType: AType
        override var returnType: AType
            get() = _returnType
            set(newValue) { _returnType = updateTreeConnection(_returnType, newValue) }
        private var _body: BlockStatement?
        override var body: BlockStatement?
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        private var _visibility: VisibilityModifier
        override var visibility: VisibilityModifier
            get() = _visibility
            set(newValue) { _visibility = updateTreeConnection(_visibility, newValue) }
        private val _overridden: MutableList<SuperTypeMethod> = mutableListOf()
        override var overridden: List<SuperTypeMethod>
            get() = _overridden
            set(newValue) { updateTreeConnections(_overridden, newValue) }
        override fun deepCopy(): NormalMethod {
            return NormalMethod(pos, metadata = this.metadata, dotName = this.dotName.deepCopy(), name = this.name.deepCopy(), typeParameters = this.typeParameters.deepCopy(), parameters = this.parameters.deepCopy(), returnType = this.returnType.deepCopy(), body = this.body?.deepCopy(), visibility = this.visibility.deepCopy(), overridden = this.overridden.deepCopy(), mayYield = this.mayYield, memberShape = this.memberShape, adjustments = this.adjustments)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is NormalMethod && this.metadata == other.metadata && this.dotName == other.dotName && this.name == other.name && this.typeParameters == other.typeParameters && this.parameters == other.parameters && this.returnType == other.returnType && this.body == other.body && this.visibility == other.visibility && this.overridden == other.overridden && this.mayYield == other.mayYield && this.memberShape == other.memberShape && this.adjustments == other.adjustments
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + dotName.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + typeParameters.hashCode()
            hc = 31 * hc + parameters.hashCode()
            hc = 31 * hc + returnType.hashCode()
            hc = 31 * hc + (body?.hashCode() ?: 0)
            hc = 31 * hc + visibility.hashCode()
            hc = 31 * hc + overridden.hashCode()
            hc = 31 * hc + mayYield.hashCode()
            hc = 31 * hc + memberShape.hashCode()
            hc = 31 * hc + (adjustments?.hashCode() ?: 0)
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._dotName = updateTreeConnection(null, dotName)
            this._name = updateTreeConnection(null, name)
            this._typeParameters = updateTreeConnection(null, typeParameters)
            this._parameters = updateTreeConnection(null, parameters)
            this._returnType = updateTreeConnection(null, returnType)
            this._body = updateTreeConnection(null, body)
            this._visibility = updateTreeConnection(null, visibility)
            updateTreeConnections(this._overridden, overridden)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as NormalMethod).dotName },
                { n -> (n as NormalMethod).name },
                { n -> (n as NormalMethod).typeParameters },
                { n -> (n as NormalMethod).parameters },
                { n -> (n as NormalMethod).returnType },
                { n -> (n as NormalMethod).body },
                { n -> (n as NormalMethod).visibility },
                { n -> (n as NormalMethod).overridden },
            )
        }
    }

    /** The shape for the property that is being set or gotten */
    sealed interface GetterOrSetter : Tree, InstanceMethod, DotAccessibleMethod, DotAccessible {
        override val mayYield: Boolean
            get() = false
        val propertyShape: PropertyShape
        override fun deepCopy(): GetterOrSetter
    }

    class Getter(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        dotName: DotName,
        name: Id,
        typeParameters: ATypeParameters,
        parameters: Parameters,
        returnType: AType,
        body: BlockStatement?,
        visibility: VisibilityModifier,
        overridden: Iterable<SuperTypeMethod>,
        override var memberShape: VisibleMemberShape,
        override var adjustments: SignatureAdjustments? = null,
        override var propertyShape: PropertyShape,
    ) : BaseTree(pos), GetterOrSetter {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (sameDotName && body != null) {
                    sharedCodeFormattingTemplate196
                } else if (sameDotName) {
                    sharedCodeFormattingTemplate197
                } else if (body != null) {
                    sharedCodeFormattingTemplate198
                } else {
                    sharedCodeFormattingTemplate199
                }
        override val formatElementCount
            get() = 7
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.dotName
                2 -> this.name
                3 -> this.typeParameters
                4 -> this.parameters
                5 -> this.returnType
                6 -> this.body ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _dotName: DotName
        override var dotName: DotName
            get() = _dotName
            set(newValue) { _dotName = updateTreeConnection(_dotName, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _typeParameters: ATypeParameters
        override var typeParameters: ATypeParameters
            get() = _typeParameters
            set(newValue) { _typeParameters = updateTreeConnection(_typeParameters, newValue) }
        private var _parameters: Parameters
        override var parameters: Parameters
            get() = _parameters
            set(newValue) { _parameters = updateTreeConnection(_parameters, newValue) }
        private var _returnType: AType
        override var returnType: AType
            get() = _returnType
            set(newValue) { _returnType = updateTreeConnection(_returnType, newValue) }
        private var _body: BlockStatement?
        override var body: BlockStatement?
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        private var _visibility: VisibilityModifier
        override var visibility: VisibilityModifier
            get() = _visibility
            set(newValue) { _visibility = updateTreeConnection(_visibility, newValue) }
        private val _overridden: MutableList<SuperTypeMethod> = mutableListOf()
        override var overridden: List<SuperTypeMethod>
            get() = _overridden
            set(newValue) { updateTreeConnections(_overridden, newValue) }
        override fun deepCopy(): Getter {
            return Getter(pos, metadata = this.metadata, dotName = this.dotName.deepCopy(), name = this.name.deepCopy(), typeParameters = this.typeParameters.deepCopy(), parameters = this.parameters.deepCopy(), returnType = this.returnType.deepCopy(), body = this.body?.deepCopy(), visibility = this.visibility.deepCopy(), overridden = this.overridden.deepCopy(), memberShape = this.memberShape, adjustments = this.adjustments, propertyShape = this.propertyShape)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Getter && this.metadata == other.metadata && this.dotName == other.dotName && this.name == other.name && this.typeParameters == other.typeParameters && this.parameters == other.parameters && this.returnType == other.returnType && this.body == other.body && this.visibility == other.visibility && this.overridden == other.overridden && this.memberShape == other.memberShape && this.adjustments == other.adjustments && this.propertyShape == other.propertyShape
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + dotName.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + typeParameters.hashCode()
            hc = 31 * hc + parameters.hashCode()
            hc = 31 * hc + returnType.hashCode()
            hc = 31 * hc + (body?.hashCode() ?: 0)
            hc = 31 * hc + visibility.hashCode()
            hc = 31 * hc + overridden.hashCode()
            hc = 31 * hc + memberShape.hashCode()
            hc = 31 * hc + (adjustments?.hashCode() ?: 0)
            hc = 31 * hc + propertyShape.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._dotName = updateTreeConnection(null, dotName)
            this._name = updateTreeConnection(null, name)
            this._typeParameters = updateTreeConnection(null, typeParameters)
            this._parameters = updateTreeConnection(null, parameters)
            this._returnType = updateTreeConnection(null, returnType)
            this._body = updateTreeConnection(null, body)
            this._visibility = updateTreeConnection(null, visibility)
            updateTreeConnections(this._overridden, overridden)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Getter).dotName },
                { n -> (n as Getter).name },
                { n -> (n as Getter).typeParameters },
                { n -> (n as Getter).parameters },
                { n -> (n as Getter).returnType },
                { n -> (n as Getter).body },
                { n -> (n as Getter).visibility },
                { n -> (n as Getter).overridden },
            )
        }
    }

    class Setter(
        pos: Position,
        metadata: Iterable<DeclarationMetadata>,
        dotName: DotName,
        name: Id,
        parameters: Parameters,
        returnType: AType,
        body: BlockStatement?,
        typeParameters: ATypeParameters,
        visibility: VisibilityModifier,
        overridden: Iterable<SuperTypeMethod>,
        override var memberShape: VisibleMemberShape,
        override var adjustments: SignatureAdjustments? = null,
        override var propertyShape: PropertyShape,
    ) : BaseTree(pos), GetterOrSetter {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (sameDotName && body != null) {
                    sharedCodeFormattingTemplate200
                } else if (sameDotName) {
                    sharedCodeFormattingTemplate201
                } else if (body != null) {
                    sharedCodeFormattingTemplate202
                } else {
                    sharedCodeFormattingTemplate203
                }
        override val formatElementCount
            get() = 6
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.metadata)
                1 -> this.dotName
                2 -> this.name
                3 -> this.parameters
                4 -> this.returnType
                5 -> this.body ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        private val _metadata: MutableList<DeclarationMetadata> = mutableListOf()
        override var metadata: List<DeclarationMetadata>
            get() = _metadata
            set(newValue) { _metadata.replaceSubList(0, _metadata.size, newValue) }
        private var _dotName: DotName
        override var dotName: DotName
            get() = _dotName
            set(newValue) { _dotName = updateTreeConnection(_dotName, newValue) }
        private var _name: Id
        override var name: Id
            get() = _name
            set(newValue) { _name = updateTreeConnection(_name, newValue) }
        private var _parameters: Parameters
        override var parameters: Parameters
            get() = _parameters
            set(newValue) { _parameters = updateTreeConnection(_parameters, newValue) }
        private var _returnType: AType
        override var returnType: AType
            get() = _returnType
            set(newValue) { _returnType = updateTreeConnection(_returnType, newValue) }
        private var _body: BlockStatement?
        override var body: BlockStatement?
            get() = _body
            set(newValue) { _body = updateTreeConnection(_body, newValue) }
        private var _typeParameters: ATypeParameters
        override var typeParameters: ATypeParameters
            get() = _typeParameters
            set(newValue) { _typeParameters = updateTreeConnection(_typeParameters, newValue) }
        private var _visibility: VisibilityModifier
        override var visibility: VisibilityModifier
            get() = _visibility
            set(newValue) { _visibility = updateTreeConnection(_visibility, newValue) }
        private val _overridden: MutableList<SuperTypeMethod> = mutableListOf()
        override var overridden: List<SuperTypeMethod>
            get() = _overridden
            set(newValue) { updateTreeConnections(_overridden, newValue) }
        override fun deepCopy(): Setter {
            return Setter(pos, metadata = this.metadata, dotName = this.dotName.deepCopy(), name = this.name.deepCopy(), parameters = this.parameters.deepCopy(), returnType = this.returnType.deepCopy(), body = this.body?.deepCopy(), typeParameters = this.typeParameters.deepCopy(), visibility = this.visibility.deepCopy(), overridden = this.overridden.deepCopy(), memberShape = this.memberShape, adjustments = this.adjustments, propertyShape = this.propertyShape)
        }
        override val childMemberRelationships
            get() = cmr
        override fun equals(
            other: Any?,
        ): Boolean {
            return other is Setter && this.metadata == other.metadata && this.dotName == other.dotName && this.name == other.name && this.parameters == other.parameters && this.returnType == other.returnType && this.body == other.body && this.typeParameters == other.typeParameters && this.visibility == other.visibility && this.overridden == other.overridden && this.memberShape == other.memberShape && this.adjustments == other.adjustments && this.propertyShape == other.propertyShape
        }
        override fun hashCode(): Int {
            var hc = metadata.hashCode()
            hc = 31 * hc + dotName.hashCode()
            hc = 31 * hc + name.hashCode()
            hc = 31 * hc + parameters.hashCode()
            hc = 31 * hc + returnType.hashCode()
            hc = 31 * hc + (body?.hashCode() ?: 0)
            hc = 31 * hc + typeParameters.hashCode()
            hc = 31 * hc + visibility.hashCode()
            hc = 31 * hc + overridden.hashCode()
            hc = 31 * hc + memberShape.hashCode()
            hc = 31 * hc + (adjustments?.hashCode() ?: 0)
            hc = 31 * hc + propertyShape.hashCode()
            return hc
        }
        init {
            this._metadata.addAll(metadata)
            this._dotName = updateTreeConnection(null, dotName)
            this._name = updateTreeConnection(null, name)
            this._parameters = updateTreeConnection(null, parameters)
            this._returnType = updateTreeConnection(null, returnType)
            this._body = updateTreeConnection(null, body)
            this._typeParameters = updateTreeConnection(null, typeParameters)
            this._visibility = updateTreeConnection(null, visibility)
            updateTreeConnections(this._overridden, overridden)
        }
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as Setter).dotName },
                { n -> (n as Setter).name },
                { n -> (n as Setter).parameters },
                { n -> (n as Setter).returnType },
                { n -> (n as Setter).body },
                { n -> (n as Setter).typeParameters },
                { n -> (n as Setter).visibility },
                { n -> (n as Setter).overridden },
            )
        }
    }

    sealed interface FunctionLikeDefData : Data, AnyDefData {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (isStatic && visibility != null && qName != null && skeletalBody != null) {
                    sharedCodeFormattingTemplate39
                } else if (isStatic && visibility != null && qName != null) {
                    sharedCodeFormattingTemplate40
                } else if (isStatic && visibility != null && skeletalBody != null) {
                    sharedCodeFormattingTemplate41
                } else if (isStatic && visibility != null) {
                    sharedCodeFormattingTemplate42
                } else if (isStatic && qName != null && skeletalBody != null) {
                    sharedCodeFormattingTemplate43
                } else if (isStatic && qName != null) {
                    sharedCodeFormattingTemplate44
                } else if (isStatic && skeletalBody != null) {
                    sharedCodeFormattingTemplate45
                } else if (isStatic) {
                    sharedCodeFormattingTemplate46
                } else if (visibility != null && qName != null && skeletalBody != null) {
                    sharedCodeFormattingTemplate47
                } else if (visibility != null && qName != null) {
                    sharedCodeFormattingTemplate48
                } else if (visibility != null && skeletalBody != null) {
                    sharedCodeFormattingTemplate49
                } else if (visibility != null) {
                    sharedCodeFormattingTemplate50
                } else if (qName != null && skeletalBody != null) {
                    sharedCodeFormattingTemplate51
                } else if (qName != null) {
                    sharedCodeFormattingTemplate52
                } else if (skeletalBody != null) {
                    sharedCodeFormattingTemplate53
                } else {
                    sharedCodeFormattingTemplate54
                }
        override val formatElementCount
            get() = 8
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.metadata
                1 -> this.visibility ?: FormattableTreeGroup.empty
                2 -> this.qName ?: FormattableTreeGroup.empty
                3 -> this.name
                4 -> this.typeParameters
                5 -> this.args
                6 -> this.retType
                7 -> this.skeletalBody ?: FormattableTreeGroup.empty
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        val typeParameters: FormalTypeDefsData
        val args: FormalArgTypesData
        val retType: RetTypeData
        val skeletalBody: SkeletalBodyData?
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FunctionLikeDefData).metadata },
                { n -> (n as FunctionLikeDefData).qName },
                { n -> (n as FunctionLikeDefData).name },
                { n -> (n as FunctionLikeDefData).typeParameters },
                { n -> (n as FunctionLikeDefData).args },
                { n -> (n as FunctionLikeDefData).retType },
                { n -> (n as FunctionLikeDefData).skeletalBody },
            )
        }
    }

    sealed interface SimpleDefData : Data, AnyDefData, ModuleLevelDefData {
        override val name: IdData
    }

    sealed interface VariableOrPropertyDefData : Data, AnyDefData {
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (isStatic && visibility != null && qName != null) {
                    sharedCodeFormattingTemplate55
                } else if (isStatic && visibility != null) {
                    sharedCodeFormattingTemplate56
                } else if (isStatic && qName != null) {
                    sharedCodeFormattingTemplate57
                } else if (isStatic) {
                    sharedCodeFormattingTemplate58
                } else if (visibility != null && qName != null) {
                    sharedCodeFormattingTemplate59
                } else if (visibility != null) {
                    sharedCodeFormattingTemplate60
                } else if (qName != null) {
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
                0 -> this.metadata
                1 -> this.visibility ?: FormattableTreeGroup.empty
                2 -> this.qName ?: FormattableTreeGroup.empty
                3 -> this.name
                4 -> this.type
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        val type: ExprTypeData
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as VariableOrPropertyDefData).metadata },
                { n -> (n as VariableOrPropertyDefData).qName },
                { n -> (n as VariableOrPropertyDefData).name },
                { n -> (n as VariableOrPropertyDefData).type },
            )
        }
    }

    sealed interface MemberDefData : Data, AnyDefData {
        override val name: MemberNameData
        val declaringTypeName: IdData

        /** Convenience for getting the dot name from the member name. */
        val dotName: DotNameData
            get() = name.dotName
    }

    data class FormalTypeDefsData(
        override val sourceLibrary: DashedIdentifier,
        val defs: List<FormalTypeDefData>,
    ) : BaseData() {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (defs.isNotEmpty()) {
                    sharedCodeFormattingTemplate30
                } else {
                    sharedCodeFormattingTemplate31
                }
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.defs)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FormalTypeDefsData).defs },
            )
        }
    }

    data class ResultTypeData(
        override val sourceLibrary: DashedIdentifier,
        val passType: PassTypeData,
        val errTypes: List<RegularTypeNotNullData>,
        override val name: IdData,
    ) : BaseData(), NonNullTypeData, RetTypeData {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate35
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.passType
                1 -> FormattableTreeGroup(this.errTypes)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        override val typeActuals: List<NewTypeData>
            get() =
                buildList {
                    add(passType)
                    addAll(errTypes)
                }
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as ResultTypeData).passType },
                { n -> (n as ResultTypeData).errTypes },
                { n -> (n as ResultTypeData).name },
            )
        }
    }

    data class VoidTypeData(
        override val sourceLibrary: DashedIdentifier,
        override val name: IdData,
    ) : BaseData(), NonNullTypeData, PassTypeData {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (canBeNull) {
                    sharedCodeFormattingTemplate36
                } else {
                    sharedCodeFormattingTemplate37
                }
        override val formatElementCount
            get() = 0
        override val typeActuals: List<NewTypeData>
            get() = emptyList()
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as VoidTypeData).name },
            )
        }
    }

    data class FormalTypeDefData(
        override val sourceLibrary: DashedIdentifier,
        override val metadata: DeclarationMetadata,
        override val qName: NameData?,
        override val name: IdData,
        override val qualifiers: TypeQualifiersData,
        override val visibility: Visibility?,
    ) : BaseData(), AnyTypeDefData {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (qName != null) {
                    sharedCodeFormattingTemplate67
                } else {
                    sharedCodeFormattingTemplate68
                }
        override val formatElementCount
            get() = 4
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.metadata
                1 -> this.qName ?: FormattableTreeGroup.empty
                2 -> this.name
                3 -> this.qualifiers
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FormalTypeDefData).metadata },
                { n -> (n as FormalTypeDefData).qName },
                { n -> (n as FormalTypeDefData).name },
                { n -> (n as FormalTypeDefData).qualifiers },
            )
        }
    }

    data class TypeQualifiersData(
        override val sourceLibrary: DashedIdentifier,
        val extendsClauses: List<NonNullExprTypeData>,
        val supportsClauses: RegularTypeNotNullData?,
        val forbidsClauses: List<NewTypeData>,
    ) : BaseData() {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() =
                if (extendsClauses.isNotEmpty() && supportsClauses != null && forbidsClauses.isNotEmpty()) {
                    sharedCodeFormattingTemplate69
                } else if (extendsClauses.isNotEmpty() && supportsClauses != null) {
                    sharedCodeFormattingTemplate70
                } else if (extendsClauses.isNotEmpty() && forbidsClauses.isNotEmpty()) {
                    sharedCodeFormattingTemplate71
                } else if (extendsClauses.isNotEmpty()) {
                    sharedCodeFormattingTemplate72
                } else if (supportsClauses != null && forbidsClauses.isNotEmpty()) {
                    sharedCodeFormattingTemplate73
                } else if (supportsClauses != null) {
                    sharedCodeFormattingTemplate74
                } else if (forbidsClauses.isNotEmpty()) {
                    sharedCodeFormattingTemplate75
                } else {
                    sharedCodeFormattingTemplate31
                }
        override val formatElementCount
            get() = 3
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.extendsClauses)
                1 -> this.supportsClauses ?: FormattableTreeGroup.empty
                2 -> FormattableTreeGroup(this.forbidsClauses)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as TypeQualifiersData).extendsClauses },
                { n -> (n as TypeQualifiersData).supportsClauses },
                { n -> (n as TypeQualifiersData).forbidsClauses },
            )
        }
    }

    sealed interface AnyMethodDefData : Data, FunctionLikeDefData, MemberDefData {
        override val name: MemberNameData
    }

    data class FunctionDefData(
        override val sourceLibrary: DashedIdentifier,
        override val metadata: DeclarationMetadata,
        override val qName: NameData?,
        override val name: IdData,
        override val typeParameters: FormalTypeDefsData,
        override val args: FormalArgTypesData,
        override val retType: RetTypeData,
        override val skeletalBody: SkeletalBodyData?,
    ) : BaseData(), FunctionLikeDefData, SimpleDefData {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val visibility: Visibility?
            get() = null
    }

    data class FormalArgTypesData(
        override val sourceLibrary: DashedIdentifier,
        val argTypes: List<ExprTypeData>,
    ) : BaseData() {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate65
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.argTypes)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as FormalArgTypesData).argTypes },
            )
        }
    }

    data class SkeletalBodyData(
        override val sourceLibrary: DashedIdentifier,
        val defs: List<SimpleDefData>,
    ) : BaseData() {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate66
        override val formatElementCount
            get() = 1
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> FormattableTreeGroup(this.defs)
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as SkeletalBodyData).defs },
            )
        }
    }

    data class VariableDefData(
        override val sourceLibrary: DashedIdentifier,
        override val metadata: DeclarationMetadata,
        override val qName: NameData?,
        override val name: IdData,
        override val type: ExprTypeData,
    ) : BaseData(), SimpleDefData, VariableOrPropertyDefData {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val visibility: Visibility?
            get() = null
    }

    sealed interface AnyPropertyDefData : Data, VariableOrPropertyDefData, MemberDefData {
        override val name: MemberNameData
    }

    sealed interface InstanceMemberDefData : Data, MemberDefData

    data class MethodDefData(
        override val sourceLibrary: DashedIdentifier,
        override val metadata: DeclarationMetadata,
        override val visibility: Visibility?,
        override val qName: NameData?,
        override val name: MemberNameData,
        override val typeParameters: FormalTypeDefsData,
        override val args: FormalArgTypesData,
        override val retType: RetTypeData,
        override val skeletalBody: SkeletalBodyData?,
        override val declaringTypeName: IdData,
    ) : BaseData(), MemberDefData, AnyMethodDefData, InstanceMemberDefData {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
    }

    sealed interface StaticMemberDefData : Data, MemberDefData

    data class StaticMethodDefData(
        override val sourceLibrary: DashedIdentifier,
        override val metadata: DeclarationMetadata,
        override val visibility: Visibility?,
        override val qName: NameData?,
        override val name: MemberNameData,
        override val typeParameters: FormalTypeDefsData,
        override val args: FormalArgTypesData,
        override val retType: RetTypeData,
        override val skeletalBody: SkeletalBodyData?,
        override val declaringTypeName: IdData,
    ) : BaseData(), MemberDefData, AnyMethodDefData, StaticMemberDefData {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
    }

    data class PropertyDefData(
        override val sourceLibrary: DashedIdentifier,
        override val metadata: DeclarationMetadata,
        override val visibility: Visibility?,
        override val qName: NameData?,
        override val name: MemberNameData,
        override val type: ExprTypeData,
        override val declaringTypeName: IdData,
    ) : BaseData(), MemberDefData, AnyPropertyDefData, InstanceMemberDefData {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
    }

    data class StaticPropertyDefData(
        override val sourceLibrary: DashedIdentifier,
        override val metadata: DeclarationMetadata,
        override val visibility: Visibility?,
        override val qName: NameData?,
        override val name: MemberNameData,
        override val type: ExprTypeData,
        override val declaringTypeName: IdData,
    ) : BaseData(), MemberDefData, AnyPropertyDefData, StaticMemberDefData {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
    }

    data class MemberNameData(
        override val sourceLibrary: DashedIdentifier,
        val containingType: IdData,
        val dotName: DotNameData,
    ) : BaseData(), DefNameData {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override val codeFormattingTemplate: CodeFormattingTemplate
            get() = sharedCodeFormattingTemplate38
        override val formatElementCount
            get() = 2
        override fun formatElement(
            index: Int,
        ): IndexableFormattableTreeElement {
            return when (index) {
                0 -> this.containingType
                1 -> this.dotName
                else -> throw IndexOutOfBoundsException("$index")
            }
        }
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships(
                { n -> (n as MemberNameData).containingType },
                { n -> (n as MemberNameData).dotName },
            )
        }
    }

    data class DotNameData(
        override val sourceLibrary: DashedIdentifier,
        val dotNameText: String,
    ) : BaseData() {
        override val operatorDefinition: TmpLOperatorDefinition?
            get() = null
        override fun renderTo(
            tokenSink: TokenSink,
        ) {
            tokenSink.emit(dotNameToOutputToken(this.dotNameText))
        }
        override val codeFormattingTemplate: CodeFormattingTemplate?
            get() = null
        override val childMemberRelationships
            get() = cmr
        companion object {
            private val cmr = ChildMemberRelationships()
        }
    }

    /** `{{0*\n \n}}` */
    private val sharedCodeFormattingTemplate0 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.Concatenation(
                listOf(
                    CodeFormattingTemplate.NewLine,
                    CodeFormattingTemplate.NewLine,
                ),
            ),
        )

    /** `{{0}} {{1}} {{2*\n}} \n {{3*\n}} \n {{4*\n}} \n export {{5}} ;` */
    private val sharedCodeFormattingTemplate1 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("export", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} {{2*\n}} \n {{3*\n}} \n {{4*\n}} \n` */
    private val sharedCodeFormattingTemplate2 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `{{0}} {{1}} {{2*\n}} \n {{3*\n}} \n {{4*\n}} export {{5}} ;` */
    private val sharedCodeFormattingTemplate3 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("export", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} {{2*\n}} \n {{3*\n}} \n {{4*\n}}` */
    private val sharedCodeFormattingTemplate4 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
            ),
        )

    /** `{{0}} {{1}} {{2*\n}} \n {{3*\n}} {{4*\n}} \n export {{5}} ;` */
    private val sharedCodeFormattingTemplate5 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("export", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} {{2*\n}} \n {{3*\n}} {{4*\n}} \n` */
    private val sharedCodeFormattingTemplate6 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `{{0}} {{1}} {{2*\n}} \n {{3*\n}} {{4*\n}} export {{5}} ;` */
    private val sharedCodeFormattingTemplate7 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("export", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} {{2*\n}} \n {{3*\n}} {{4*\n}}` */
    private val sharedCodeFormattingTemplate8 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
            ),
        )

    /** `{{0}} {{1}} {{2*\n}} {{3*\n}} \n {{4*\n}} \n export {{5}} ;` */
    private val sharedCodeFormattingTemplate9 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("export", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} {{2*\n}} {{3*\n}} \n {{4*\n}} \n` */
    private val sharedCodeFormattingTemplate10 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `{{0}} {{1}} {{2*\n}} {{3*\n}} \n {{4*\n}} export {{5}} ;` */
    private val sharedCodeFormattingTemplate11 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("export", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} {{2*\n}} {{3*\n}} \n {{4*\n}}` */
    private val sharedCodeFormattingTemplate12 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
            ),
        )

    /** `{{0}} {{1}} {{2*\n}} {{3*\n}} {{4*\n}} \n export {{5}} ;` */
    private val sharedCodeFormattingTemplate13 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("export", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} {{2*\n}} {{3*\n}} {{4*\n}} \n` */
    private val sharedCodeFormattingTemplate14 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.NewLine,
            ),
        )

    /** `{{0}} {{1}} {{2*\n}} {{3*\n}} {{4*\n}} export {{5}} ;` */
    private val sharedCodeFormattingTemplate15 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.LiteralToken("export", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} {{2*\n}} {{3*\n}} {{4*\n}}` */
    private val sharedCodeFormattingTemplate16 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    3,
                    CodeFormattingTemplate.NewLine,
                ),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.NewLine,
                ),
            ),
        )

    /** `{{0*}} let \{ {{1}} as {{2}} \} : {{3}} = import ( {{4}} ) ;` */
    private val sharedCodeFormattingTemplate17 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("as", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} let \{ {{1}} as {{2}} \} : {{3}} = import ( ) ;` */
    private val sharedCodeFormattingTemplate18 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("as", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} let \{ {{1}} as {{2}} \} = import ( {{4}} ) ;` */
    private val sharedCodeFormattingTemplate19 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("as", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} let \{ {{1}} as {{2}} \} = import ( ) ;` */
    private val sharedCodeFormattingTemplate20 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("as", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} let \{ {{1}} \} : {{3}} = import ( {{4}} ) ;` */
    private val sharedCodeFormattingTemplate21 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} let \{ {{1}} \} : {{3}} = import ( ) ;` */
    private val sharedCodeFormattingTemplate22 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} let \{ {{1}} \} = import ( {{4}} ) ;` */
    private val sharedCodeFormattingTemplate23 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} let \{ {{1}} \} = import ( ) ;` */
    private val sharedCodeFormattingTemplate24 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("import", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} ( {{1}} )` */
    private val sharedCodeFormattingTemplate25 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}}` */
    private val sharedCodeFormattingTemplate26 =
        CodeFormattingTemplate.OneSubstitution(0)

    /** `{{0*}} fn {{1}}` */
    private val sharedCodeFormattingTemplate27 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0*}} const {{1}}` */
    private val sharedCodeFormattingTemplate28 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("const", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0*}} connection {{1}}` */
    private val sharedCodeFormattingTemplate29 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("connection", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `< {{0*,}} >` */
    private val sharedCodeFormattingTemplate30 =
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
    private val sharedCodeFormattingTemplate31 =
        CodeFormattingTemplate.empty

    /** `{{0}} < {{1*,}} > ?` */
    private val sharedCodeFormattingTemplate32 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("?", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} < {{1*,}} >` */
    private val sharedCodeFormattingTemplate33 =
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

    /** `{{0}} ?` */
    private val sharedCodeFormattingTemplate34 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("?", OutputTokenType.Punctuation),
            ),
        )

    /** `result < {{0}} , {{1*|}} >` */
    private val sharedCodeFormattingTemplate35 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("result", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken("|", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `void ?` */
    private val sharedCodeFormattingTemplate36 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("void", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("?", OutputTokenType.Punctuation),
            ),
        )

    /** `void` */
    private val sharedCodeFormattingTemplate37 =
        CodeFormattingTemplate.LiteralToken("void", OutputTokenType.Word)

    /** `{{0}} :: {{1}}` */
    private val sharedCodeFormattingTemplate38 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("::", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} static {{1}} {{2}} {{3}} {{4}} {{5}} : {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate39 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} static {{1}} {{2}} {{3}} {{4}} {{5}} : {{6}} ;` */
    private val sharedCodeFormattingTemplate40 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} static {{1}} {{3}} {{4}} {{5}} : {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate41 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} static {{1}} {{3}} {{4}} {{5}} : {{6}} ;` */
    private val sharedCodeFormattingTemplate42 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} static {{2}} {{3}} {{4}} {{5}} : {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate43 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} static {{2}} {{3}} {{4}} {{5}} : {{6}} ;` */
    private val sharedCodeFormattingTemplate44 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} static {{3}} {{4}} {{5}} : {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate45 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} static {{3}} {{4}} {{5}} : {{6}} ;` */
    private val sharedCodeFormattingTemplate46 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} {{2}} {{3}} {{4}} {{5}} : {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate47 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} {{1}} {{2}} {{3}} {{4}} {{5}} : {{6}} ;` */
    private val sharedCodeFormattingTemplate48 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} {{3}} {{4}} {{5}} : {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate49 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} {{1}} {{3}} {{4}} {{5}} : {{6}} ;` */
    private val sharedCodeFormattingTemplate50 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{2}} {{3}} {{4}} {{5}} : {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate51 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} {{2}} {{3}} {{4}} {{5}} : {{6}} ;` */
    private val sharedCodeFormattingTemplate52 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{3}} {{4}} {{5}} : {{6}} {{7}}` */
    private val sharedCodeFormattingTemplate53 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.OneSubstitution(7),
            ),
        )

    /** `{{0}} {{3}} {{4}} {{5}} : {{6}} ;` */
    private val sharedCodeFormattingTemplate54 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(6),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} static {{1}} {{2}} {{3}} : {{4}}` */
    private val sharedCodeFormattingTemplate55 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(4),
            ),
        )

    /** `{{0}} static {{1}} {{3}} : {{4}}` */
    private val sharedCodeFormattingTemplate56 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(4),
            ),
        )

    /** `{{0}} static {{2}} {{3}} : {{4}}` */
    private val sharedCodeFormattingTemplate57 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(4),
            ),
        )

    /** `{{0}} static {{3}} : {{4}}` */
    private val sharedCodeFormattingTemplate58 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(4),
            ),
        )

    /** `{{0}} {{1}} {{2}} {{3}} : {{4}}` */
    private val sharedCodeFormattingTemplate59 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(4),
            ),
        )

    /** `{{0}} {{1}} {{3}} : {{4}}` */
    private val sharedCodeFormattingTemplate60 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(4),
            ),
        )

    /** `{{0}} {{2}} {{3}} : {{4}}` */
    private val sharedCodeFormattingTemplate61 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(4),
            ),
        )

    /** `{{0}} {{3}} : {{4}}` */
    private val sharedCodeFormattingTemplate62 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(4),
            ),
        )

    /** `{{0}} {{1}} {{2}} {{3}} {{4}} {{5}} \{ \n {{6*;}} \n \}` */
    private val sharedCodeFormattingTemplate63 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} {{3}} {{4}} {{5}} \{ \n {{6*;}} \n \}` */
    private val sharedCodeFormattingTemplate64 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    6,
                    CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `( {{0*,}} )` */
    private val sharedCodeFormattingTemplate65 =
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

    /** `\{ \n {{0*;}} \n \}` */
    private val sharedCodeFormattingTemplate66 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate67 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate68 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `extends {{0*,}} supports {{1}} forbids {{2*,}}` */
    private val sharedCodeFormattingTemplate69 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("supports", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("forbids", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
            ),
        )

    /** `extends {{0*,}} supports {{1}}` */
    private val sharedCodeFormattingTemplate70 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("supports", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `extends {{0*,}} forbids {{2*,}}` */
    private val sharedCodeFormattingTemplate71 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("forbids", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
            ),
        )

    /** `extends {{0*,}}` */
    private val sharedCodeFormattingTemplate72 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
            ),
        )

    /** `supports {{1}} forbids {{2*,}}` */
    private val sharedCodeFormattingTemplate73 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("supports", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("forbids", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
            ),
        )

    /** `supports {{1}}` */
    private val sharedCodeFormattingTemplate74 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("supports", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `forbids {{2*,}}` */
    private val sharedCodeFormattingTemplate75 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("forbids", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    2,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
            ),
        )

    /** `{{0*;}}` */
    private val sharedCodeFormattingTemplate76 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
        )

    /** `< GARBAGE \  {{0}} > ;` */
    private val sharedCodeFormattingTemplate77 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("GARBAGE", OutputTokenType.Word),
                CodeFormattingTemplate.Space,
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `< GARBAGE > ;` */
    private val sharedCodeFormattingTemplate78 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("GARBAGE", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `< garbage \  {{0}} > ;` */
    private val sharedCodeFormattingTemplate79 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("garbage", OutputTokenType.Word),
                CodeFormattingTemplate.Space,
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `< garbage > ;` */
    private val sharedCodeFormattingTemplate80 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("garbage", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `< garbage \  {{0}} >` */
    private val sharedCodeFormattingTemplate81 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("garbage", OutputTokenType.Word),
                CodeFormattingTemplate.Space,
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `< garbage >` */
    private val sharedCodeFormattingTemplate82 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("garbage", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `< Garbage \  {{0}} >` */
    private val sharedCodeFormattingTemplate83 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("Garbage", OutputTokenType.Word),
                CodeFormattingTemplate.Space,
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `< Garbage >` */
    private val sharedCodeFormattingTemplate84 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("\u003c", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("Garbage", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("\u003e", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0*}} module init {{1}}` */
    private val sharedCodeFormattingTemplate85 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("module", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("init", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} extends {{1*,}}` */
    private val sharedCodeFormattingTemplate86 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
            ),
        )

    /** `{{0*}} class {{1}} / {{2}} {{3}} extends {{4*&}} \{ \n {{5*\n}} \n \}` */
    private val sharedCodeFormattingTemplate87 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("/", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken("\u0026", OutputTokenType.Punctuation),
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

    /** `{{0*}} class {{1}} / {{2}} {{3}} \{ \n {{5*\n}} \n \}` */
    private val sharedCodeFormattingTemplate88 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("/", OutputTokenType.Punctuation),
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

    /** `{{0*}} class {{1}} {{3}} extends {{4*&}} \{ \n {{5*\n}} \n \}` */
    private val sharedCodeFormattingTemplate89 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken("\u0026", OutputTokenType.Punctuation),
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

    /** `{{0*}} class {{1}} {{3}} \{ \n {{5*\n}} \n \}` */
    private val sharedCodeFormattingTemplate90 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("class", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
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

    /** `{{0*}} interface {{1}} / {{2}} {{3}} extends {{4*&}} \{ \n {{5*\n}} \n \}` */
    private val sharedCodeFormattingTemplate91 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("interface", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("/", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken("\u0026", OutputTokenType.Punctuation),
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

    /** `{{0*}} interface {{1}} / {{2}} {{3}} \{ \n {{5*\n}} \n \}` */
    private val sharedCodeFormattingTemplate92 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("interface", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("/", OutputTokenType.Punctuation),
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

    /** `{{0*}} interface {{1}} {{3}} extends {{4*&}} \{ \n {{5*\n}} \n \}` */
    private val sharedCodeFormattingTemplate93 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("interface", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken("\u0026", OutputTokenType.Punctuation),
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

    /** `{{0*}} interface {{1}} {{3}} \{ \n {{5*\n}} \n \}` */
    private val sharedCodeFormattingTemplate94 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("interface", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
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

    /** `{{0*}} enum {{1}} / {{2}} {{3}} extends {{4*&}} \{ \n {{5*\n}} \n \}` */
    private val sharedCodeFormattingTemplate95 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("enum", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("/", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken("\u0026", OutputTokenType.Punctuation),
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

    /** `{{0*}} enum {{1}} / {{2}} {{3}} \{ \n {{5*\n}} \n \}` */
    private val sharedCodeFormattingTemplate96 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("enum", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("/", OutputTokenType.Punctuation),
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

    /** `{{0*}} enum {{1}} {{3}} extends {{4*&}} \{ \n {{5*\n}} \n \}` */
    private val sharedCodeFormattingTemplate97 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("enum", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken("\u0026", OutputTokenType.Punctuation),
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

    /** `{{0*}} enum {{1}} {{3}} \{ \n {{5*\n}} \n \}` */
    private val sharedCodeFormattingTemplate98 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("enum", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
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

    /** `{{0*}} {{1}} {{2}} {{3}} extends {{4*&}} connects {{5}} ;` */
    private val sharedCodeFormattingTemplate99 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("extends", OutputTokenType.Word),
                CodeFormattingTemplate.GroupSubstitution(
                    4,
                    CodeFormattingTemplate.LiteralToken("\u0026", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("connects", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} {{1}} {{2}} {{3}} connects {{5}} ;` */
    private val sharedCodeFormattingTemplate100 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("connects", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} let {{1}} = {{2}} ;` */
    private val sharedCodeFormattingTemplate101 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} let * {{1}} {{2}} {{3}} : {{4}} {{5}}` */
    private val sharedCodeFormattingTemplate102 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("*", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
            ),
        )

    /** `{{0*}} let {{1}} {{2}} {{3}} : {{4}} {{5}}` */
    private val sharedCodeFormattingTemplate103 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
            ),
        )

    /** `{{0*}} let {{1}} : {{2}} = {{3}} ;` */
    private val sharedCodeFormattingTemplate104 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
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

    /** `{{0*}} let {{1}} : {{2}} ;` */
    private val sharedCodeFormattingTemplate105 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} var {{1}} : {{2}} = {{3}} ;` */
    private val sharedCodeFormattingTemplate106 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("var", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} var {{1}} : {{2}} ;` */
    private val sharedCodeFormattingTemplate107 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("var", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} @test let {{1}} {{2}} {{3}}` */
    private val sharedCodeFormattingTemplate108 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("@test", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
            ),
        )

    /** `{{0*}} {{1}} : {{2}} = null` */
    private val sharedCodeFormattingTemplate109 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("null", OutputTokenType.Word),
            ),
        )

    /** `{{0*}} {{1}} : {{2}}` */
    private val sharedCodeFormattingTemplate110 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0*}} var {{1}} : {{2}} = null` */
    private val sharedCodeFormattingTemplate111 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("var", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("null", OutputTokenType.Word),
            ),
        )

    /** `{{0*}} var {{1}} : {{2}}` */
    private val sharedCodeFormattingTemplate112 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("var", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0*}} ... {{1}} : {{2}}` */
    private val sharedCodeFormattingTemplate113 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("...", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0*}} let {{2}} : {{3}} ;` */
    private val sharedCodeFormattingTemplate114 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} let {{1}} {{2}} : {{3}} ;` */
    private val sharedCodeFormattingTemplate115 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} var {{2}} : {{3}} ;` */
    private val sharedCodeFormattingTemplate116 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("var", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} var {{1}} {{2}} : {{3}} ;` */
    private val sharedCodeFormattingTemplate117 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("var", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `( this = {{0}} , {{1*,}} , {{2}} )` */
    private val sharedCodeFormattingTemplate118 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("this", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( this = {{0}} , {{1*,}} , )` */
    private val sharedCodeFormattingTemplate119 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("this", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( this = {{0}} , {{1*,}} {{2}} )` */
    private val sharedCodeFormattingTemplate120 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("this", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( this = {{0}} , {{1*,}} )` */
    private val sharedCodeFormattingTemplate121 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("this", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( this = {{0}} {{1*,}} , {{2}} )` */
    private val sharedCodeFormattingTemplate122 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("this", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( this = {{0}} {{1*,}} , )` */
    private val sharedCodeFormattingTemplate123 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("this", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( this = {{0}} {{1*,}} {{2}} )` */
    private val sharedCodeFormattingTemplate124 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("this", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( this = {{0}} {{1*,}} )` */
    private val sharedCodeFormattingTemplate125 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("this", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( , {{1*,}} , {{2}} )` */
    private val sharedCodeFormattingTemplate126 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( , {{1*,}} , )` */
    private val sharedCodeFormattingTemplate127 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( , {{1*,}} {{2}} )` */
    private val sharedCodeFormattingTemplate128 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( , {{1*,}} )` */
    private val sharedCodeFormattingTemplate129 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( {{1*,}} , {{2}} )` */
    private val sharedCodeFormattingTemplate130 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( {{1*,}} , )` */
    private val sharedCodeFormattingTemplate131 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( {{1*,}} {{2}} )` */
    private val sharedCodeFormattingTemplate132 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( {{1*,}} )` */
    private val sharedCodeFormattingTemplate133 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    1,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `\{ \n {{0*\n}} \n \}` */
    private val sharedCodeFormattingTemplate134 =
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

    /** `yield ;` */
    private val sharedCodeFormattingTemplate135 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("yield", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `await ( {{0}} )` */
    private val sharedCodeFormattingTemplate136 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("await", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `super {{0}} . {{1}} {{2}} {{3}} {{4}} : {{5}}` */
    private val sharedCodeFormattingTemplate137 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("super", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(5),
            ),
        )

    /** `{{0}} ;` */
    private val sharedCodeFormattingTemplate138 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0}} = {{1}} ;` */
    private val sharedCodeFormattingTemplate139 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `break {{0}} ;` */
    private val sharedCodeFormattingTemplate140 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("break", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `break ;` */
    private val sharedCodeFormattingTemplate141 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("break", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `continue {{0}} ;` */
    private val sharedCodeFormattingTemplate142 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("continue", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `continue ;` */
    private val sharedCodeFormattingTemplate143 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("continue", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `hs ( {{0}} , {{1}} )` */
    private val sharedCodeFormattingTemplate144 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("hs", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `abortLoad ;` */
    private val sharedCodeFormattingTemplate145 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("abortLoad", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `return {{0}} ;` */
    private val sharedCodeFormattingTemplate146 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("return", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `return ;` */
    private val sharedCodeFormattingTemplate147 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("return", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `throw ;` */
    private val sharedCodeFormattingTemplate148 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("throw", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `when ( {{0}} ) \{ \n {{1*\n}} \n {{2}} \n \}` */
    private val sharedCodeFormattingTemplate149 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("when", OutputTokenType.Word),
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
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `if ( {{0}} ) \{ \n {{1}} \n \} else {{2}}` */
    private val sharedCodeFormattingTemplate150 =
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
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `if ( {{0}} ) \{ \n {{1}} \n \}` */
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
            ),
        )

    /** `{{0}} : {{1}}` */
    private val sharedCodeFormattingTemplate153 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `try \{ \n {{0}} \n \} catch \{ \n {{1}} \n \}` */
    private val sharedCodeFormattingTemplate154 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("try", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("catch", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("{", OutputTokenType.Punctuation),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken("}", OutputTokenType.Punctuation),
            ),
        )

    /** `while ( {{0}} ) \{ \n {{1}} \n \}` */
    private val sharedCodeFormattingTemplate155 =
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

    /** `{{0*,}} -> do {{1}}` */
    private val sharedCodeFormattingTemplate156 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `else -> do {{0}}` */
    private val sharedCodeFormattingTemplate157 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("else", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("do", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `failure` */
    private val sharedCodeFormattingTemplate158 =
        CodeFormattingTemplate.LiteralToken("failure", OutputTokenType.Word)

    /** ``OutputToken("/\* this *\/", OutputTokenType.OtherValue)` {{0}}` */
    private val sharedCodeFormattingTemplate159 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken(OutputToken("/* this */", OutputTokenType.OtherValue)),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `{{0}} {{1}} ( {{2*,}} )` */
    private val sharedCodeFormattingTemplate160 =
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
            ),
        )

    /** `notNull ( {{0}} )` */
    private val sharedCodeFormattingTemplate161 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("notNull", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}} as @ fun {{1}}` */
    private val sharedCodeFormattingTemplate162 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("as", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("@", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("fun", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} [ {{1}} ]` */
    private val sharedCodeFormattingTemplate163 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("[", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("]", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}} . length` */
    private val sharedCodeFormattingTemplate164 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("length", OutputTokenType.Word),
            ),
        )

    /** `{{0}} . {{1}}` */
    private val sharedCodeFormattingTemplate165 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `... {{0}}` */
    private val sharedCodeFormattingTemplate166 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("...", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `{{0}} {{1}} {{2}}` */
    private val sharedCodeFormattingTemplate167 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0}} {{1}}` */
    private val sharedCodeFormattingTemplate168 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{0}} as {{1}}` */
    private val sharedCodeFormattingTemplate169 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("as", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `/\*new*\/ {{0}}` */
    private val sharedCodeFormattingTemplate170 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("/*new*/", OutputTokenType.Comment),
                CodeFormattingTemplate.OneSubstitution(0),
            ),
        )

    /** `{{0}} instanceof {{1}}` */
    private val sharedCodeFormattingTemplate171 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("instanceof", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `cast ( {{0}} , {{1}} )` */
    private val sharedCodeFormattingTemplate172 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("cast", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `fn {{0}} {{1}} : {{2}}` */
    private val sharedCodeFormattingTemplate173 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("fn", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
            ),
        )

    /** `{{0*|}}` */
    private val sharedCodeFormattingTemplate174 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.LiteralToken("|", OutputTokenType.Punctuation),
        )

    /** `{{0*&}}` */
    private val sharedCodeFormattingTemplate175 =
        CodeFormattingTemplate.GroupSubstitution(
            0,
            CodeFormattingTemplate.LiteralToken("\u0026", OutputTokenType.Punctuation),
        )

    /** `Top` */
    private val sharedCodeFormattingTemplate176 =
        CodeFormattingTemplate.LiteralToken("Top", OutputTokenType.Word)

    /** `Bubble` */
    private val sharedCodeFormattingTemplate177 =
        CodeFormattingTemplate.LiteralToken("Bubble", OutputTokenType.Word)

    /** `Never` */
    private val sharedCodeFormattingTemplate178 =
        CodeFormattingTemplate.LiteralToken("Never", OutputTokenType.Word)

    /** `( {{0*,}} , ... {{1}} )` */
    private val sharedCodeFormattingTemplate179 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.LiteralToken(",", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken("...", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( ... {{1}} )` */
    private val sharedCodeFormattingTemplate180 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken("...", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `( )` */
    private val sharedCodeFormattingTemplate181 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("(", OutputTokenType.Punctuation, TokenAssociation.Bracket),
                CodeFormattingTemplate.LiteralToken(")", OutputTokenType.Punctuation, TokenAssociation.Bracket),
            ),
        )

    /** `{{0}} ? : {{1}}` */
    private val sharedCodeFormattingTemplate182 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken("?", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `_ ? : {{1}}` */
    private val sharedCodeFormattingTemplate183 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken("_", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("?", OutputTokenType.Punctuation),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        )

    /** `{{1}}` */
    private val sharedCodeFormattingTemplate184 =
        CodeFormattingTemplate.OneSubstitution(1)

    /** `{{0*}} static let {{2}} : {{3}} = {{4}} ;` */
    private val sharedCodeFormattingTemplate185 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} static let {{1}} {{2}} : {{3}} = {{4}} ;` */
    private val sharedCodeFormattingTemplate186 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken("=", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} static let {{2}} {{3}} {{4}} : {{5}} {{6}}` */
    private val sharedCodeFormattingTemplate187 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.OneSubstitution(6),
            ),
        )

    /** `{{0*}} static let {{2}} {{3}} {{4}} : {{5}} ;` */
    private val sharedCodeFormattingTemplate188 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} static let {{1}} {{2}} {{3}} {{4}} : {{5}} {{6}}` */
    private val sharedCodeFormattingTemplate189 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.OneSubstitution(6),
            ),
        )

    /** `{{0*}} static let {{1}} {{2}} {{3}} {{4}} : {{5}} ;` */
    private val sharedCodeFormattingTemplate190 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("static", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} {{1}} {{2}} {{3}} {{4}}` */
    private val sharedCodeFormattingTemplate191 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
            ),
        )

    /** `{{0*}} let {{2}} {{3}} {{4}} : {{5}} {{6}}` */
    private val sharedCodeFormattingTemplate192 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.OneSubstitution(6),
            ),
        )

    /** `{{0*}} let {{2}} {{3}} {{4}} : {{5}} ;` */
    private val sharedCodeFormattingTemplate193 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} let {{1}} {{2}} {{3}} {{4}} : {{5}} {{6}}` */
    private val sharedCodeFormattingTemplate194 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.OneSubstitution(6),
            ),
        )

    /** `{{0*}} let {{1}} {{2}} {{3}} {{4}} : {{5}} ;` */
    private val sharedCodeFormattingTemplate195 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("let", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} get {{2}} {{3}} {{4}} : {{5}} {{6}}` */
    private val sharedCodeFormattingTemplate196 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.OneSubstitution(6),
            ),
        )

    /** `{{0*}} get {{2}} {{3}} {{4}} : {{5}} ;` */
    private val sharedCodeFormattingTemplate197 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} get . {{1}} -> {{2}} {{3}} {{4}} : {{5}} {{6}}` */
    private val sharedCodeFormattingTemplate198 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.OneSubstitution(6),
            ),
        )

    /** `{{0*}} get . {{1}} -> {{2}} {{3}} {{4}} : {{5}} ;` */
    private val sharedCodeFormattingTemplate199 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("get", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(5),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} set {{2}} {{3}} : {{4}} {{5}}` */
    private val sharedCodeFormattingTemplate200 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
            ),
        )

    /** `{{0*}} set {{2}} {{3}} : {{4}} ;` */
    private val sharedCodeFormattingTemplate201 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )

    /** `{{0*}} set . {{1}} -> {{2}} {{3}} : {{4}} {{5}}` */
    private val sharedCodeFormattingTemplate202 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.OneSubstitution(5),
            ),
        )

    /** `{{0*}} set . {{1}} -> {{2}} {{3}} : {{4}} ;` */
    private val sharedCodeFormattingTemplate203 =
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.GroupSubstitution(
                    0,
                    CodeFormattingTemplate.empty,
                ),
                CodeFormattingTemplate.LiteralToken("set", OutputTokenType.Word),
                CodeFormattingTemplate.LiteralToken(".", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.LiteralToken("-\u003e", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(2),
                CodeFormattingTemplate.OneSubstitution(3),
                CodeFormattingTemplate.LiteralToken(":", OutputTokenType.Punctuation),
                CodeFormattingTemplate.OneSubstitution(4),
                CodeFormattingTemplate.LiteralToken(";", OutputTokenType.Punctuation),
            ),
        )
}
