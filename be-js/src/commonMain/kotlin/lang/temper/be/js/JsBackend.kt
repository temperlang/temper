package lang.temper.be.js

import lang.temper.ast.VisitCue
import lang.temper.be.Backend
import lang.temper.be.BackendHelpTopicKey
import lang.temper.be.BackendHelpTopicKeys
import lang.temper.be.BackendSetup
import lang.temper.be.globalPathSegment
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TESTING_BASENAME
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
import lang.temper.be.tmpl.findCommonTopLevels
import lang.temper.be.tmpl.matchesStdTesting
import lang.temper.common.MimeType
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValueBuilder
import lang.temper.common.putMultiSet
import lang.temper.common.structure.PropertySink
import lang.temper.common.structure.StructureParser
import lang.temper.format.TokenSink
import lang.temper.frontend.Module
import lang.temper.fs.declareResources
import lang.temper.fs.loadResource
import lang.temper.library.LibraryConfiguration
import lang.temper.library.authors
import lang.temper.library.backendLibraryName
import lang.temper.library.description
import lang.temper.library.homepage
import lang.temper.library.license
import lang.temper.library.version
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.FilePathSegment
import lang.temper.log.FilePathSegmentOrPseudoSegment
import lang.temper.log.ParentPseudoFilePathSegment
import lang.temper.log.Position
import lang.temper.log.SameDirPseudoFilePathSegment
import lang.temper.log.UNIX_FILE_SEGMENT_SEPARATOR
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.last
import lang.temper.log.unknownPos
import lang.temper.name.BackendId
import lang.temper.name.BackendMeta
import lang.temper.name.DashedIdentifier
import lang.temper.name.ExportedName
import lang.temper.name.FileType
import lang.temper.name.LanguageLabel
import lang.temper.name.Symbol
import lang.temper.value.DependencyCategory
import lang.temper.value.Helpful
import lang.temper.value.OccasionallyHelpful

/**
 * <!-- snippet: backend/js -->
 * # JavaScript Backend
 *
 * ⎀ backend/js/id
 *
 * Translates Temper to JavaScript with types in documentation comments for
 * [compatibility with TypeScript][TS-compat].
 *
 * Targets [ES2018 / ECMA-262, 9<sup>th</sup> edition][ES2018].
 *
 * To get started with this backend, see [the tutorial](../tutorial/index.md#use-js).
 *
 * ## Translation notes
 *
 * Temper [`interface` type declarations] are translated to names in JavaScript
 * that work with JavaScript's `instanceof` operator.
 * Your JavaScript code may use [*InterfaceType.implementedBy*][temperlang-core-code]
 * to create JavaScript `class`es that implement Temper interfaces.
 *
 * The `temper.out/js` output directory will contain a [source map] for each
 * generated JavaScript source file so that, in a JS debugger, you can see the
 * corresponding Temper code.
 *
 * ## Tooling notes
 *
 * Temper's JavaScript backend translates tests to [Mocha] tests and generates a
 * [*package.json* file][package.json] so that running `npm test` from the command line will run
 * the translated tests for a Temper built JavaScript library.
 *
 * [ES2018]: https://www.ecma-international.org/publications-and-standards/standards/ecma-262/
 * [TS-compat]: https://www.typescriptlang.org/docs/handbook/jsdoc-supported-types.html#types-1
 * [temperlang-core-code]: https://www.npmjs.com/package/@temperlang/core?activeTab=code
 * [source map]: https://web.dev/source-maps/
 * [Mocha]: https://mochajs.org/
 * [package.json]: https://docs.npmjs.com/cli/v9/configuring-npm/package-json
 */
class JsBackend private constructor(
    setup: BackendSetup<JsBackend>,
    val extension: String = EXTENSION,
) : Backend<JsBackend>(Factory.backendId, setup) {
    init {
        require(extension.startsWith(".")) { "$extension must start with dot" }
    }

    /** Reserve index.js as the name of the main entry point. */
    private val mainFilePath: FilePath = allocateTextFile(filePath(INDEX_NAME), extension)

    private var jsDependencies = JsDependencies(emptyList(), emptyList())

    private fun generateMainJsForFileModules(
        outPath: FilePath,
        exportingTranslations: List<Translation>,
    ): OutputFileSpecification {
        // Given source files foo.js and bar.js generates an export merging file like
        //    import {} from "./foo.js";
        //    import {} from "./bar.js";
        val reExports = Js.Program(
            unknownPos,
            buildList {
                translations@ for (translation in exportingTranslations) {
                    val relPathText = outPath.relativePathTo(translation.outPath)
                        .importReadyPath(isDir = translation.outPath.isDir)
                    val specifier = Js.StringLiteral(unknownPos, relPathText)
                    if (".internal." in specifier.value) {
                        // Require manual reaching for internal.
                        continue@translations
                    }
                    val isTopLevel = FilePath.emptyPath ==
                        translation.tmpLModule.codeLocation.codeLocation.relativePath()
                    if (isTopLevel) { // export * from "..."
                        Js.ExportAllDeclaration(unknownPos, specifier)
                    } else { // import "..."
                        Js.ImportDeclaration(unknownPos, emptyList(), specifier)
                    }.also { add(it) }
                }
            },
        ).toString(singleLine = false)
        return MetadataFileSpecification(outPath, MimeType.javascript, reExports)
    }

    private fun generatePackageJson(
        exports: Map<String, FilePath>,
    ): MetadataFileSpecification {
        val libraryConfiguration = libraryConfigurations.currentLibraryConfiguration
        val scripts = buildMap {
            if (jsDependencies.testDependencies.isNotEmpty()) {
                put("test", "mocha test --recursive --reporter mocha-junit-reporter")
            }
        }
        val jsonContent =
            // The name and version are required for a minimal file. The test script is for executing generated tests.
            JsonValueBuilder.build {
                obj {
                    key("name") { value(libraryConfiguration.jsLibraryName()) }
                    libraryConfiguration.version()?.let { key("version") { value(it) } }
                    // TODO If we change authors to a list, then use "contributors" array instead.
                    libraryConfiguration.authors()?.let { key("author") { value(it) } }
                    libraryConfiguration.description()?.let { key("description") { value(it) } }
                    libraryConfiguration.homepage()?.let { key("homepage") { value(it) } }
                    // Prefers SPDX: https://docs.npmjs.com/cli/v6/configuring-npm/package-json#license
                    libraryConfiguration.license()?.let { key("license") { value(it) } }
                    // TODO We currently don't ask for a formal repo url, which is what npm wants.
                    // TODO It also wants a repo type, such as "git".
                    // libraryConfiguration.repository()?.let { key("repository") { key("url") { value(it) } } }
                    key("type") { value("module") }
                    key("exports") {
                        // Here, we generate imports for the library's modules.
                        //
                        // It will end up looking like
                        //    "exports": {
                        //      ".": "./index.js", // or "./main.js" if no top-level dir module
                        //      // ... other modules ...
                        //    }
                        //
                        // See also https://nodejs.org/api/packages.html#subpath-exports
                        obj {
                            for (export in exports) {
                                key(export.key) { value(export.value.importReadyPath()) }
                            }
                        }
                    }
                    maybeBuildObj("scripts", scripts)
                    buildDependencies("dependencies", jsDependencies.runtimeDependencies)
                    buildDependencies("devDependencies", jsDependencies.testDependencies)
                }
            }.toJsonString()

        return MetadataFileSpecification(
            FilePath(listOf(FilePathSegment("package.json")), isDir = false),
            MimeType.json,
            jsonContent,
        )
    }

    override fun tentativeTmpL(): TmpL.ModuleSet = TmpLTranslator.translateModules(
        logSink,
        readyModules,
        JsSupportNetwork,
        libraryConfigurations = libraryConfigurations,
        dependencyResolver = dependencyResolver,
        tentativeOutputPathFor = { module ->
            allocateTextFile(module, extension, defaultName = INDEX_NAME)
        },
    )

    override fun translate(finished: TmpL.ModuleSet): List<OutputFileSpecification> {
        val jsNames = JsNames()
        val jsLibraryNames = libraryConfigurations.byLibraryName.mapValues { it.value.jsLibraryName() }

        // Prep for test identification.
        var stdTestingPath: FilePath? = null
        val testPaths = mutableSetOf<FilePath>()

        var translations: List<Translation> =
            finished.modules.flatMap { tmpLModule ->
                val (supportCodes) = tmpLModule.findCommonTopLevels()
                val translator = JsTranslator(
                    jsNames,
                    defaultGenre = finished.genre,
                    dependenciesBuilder = dependenciesBuilder,
                    jsLibraryNames = jsLibraryNames,
                    supportCodes = supportCodes,
                )
                translator.translate(tmpLModule)
            }
        // Extract some info.
        for (translation in translations) {
            val codeLocation = translation.tmpLModule.codeLocation.codeLocation
            when (translation.dependencyCategory) {
                DependencyCategory.Production -> {
                    if (matchesStdTesting(codeLocation, libraryConfigurations)) {
                        stdTestingPath = translation.outPath
                    }
                }
                DependencyCategory.Test -> {
                    testPaths.add(translation.outPath)
                }
            }
        }
        // Link modules to shared definitions by export and import.
        translations = linkModules(translations)

        val allOutputFiles = if (config.makeMetaDataFile) {
            val dependencyNames = mutableSetOf<DashedIdentifier>()
            val updatedTranslations = translations
                .map translations@{ (outPath, program, tmpLModule) ->
                    if (outPath in testPaths) {
                        // Functional tests still uses renamed std imports.
                        // TODO Remove this if we standardize funtests to same imports as elsewhere.
                        val stdTestingRelativePath = stdTestingPath?.let {
                            outPath.relativePathTo(stdTestingPath).joinToString("/")
                        }
                        prepareTesting(program, stdTestingRelativePath)
                    }

                    val outFile = TranslatedFileSpecification(
                        outPath,
                        MimeType.javascript,
                        program,
                    )
                    // Add all dependencies but not to ourselves.
                    for (import in tmpLModule.imports) {
                        when (val path = import.path) {
                            null, is TmpL.SameLibraryPath -> Unit
                            // TODO If test-only, should go to dev dependencies.
                            is TmpL.CrossLibraryPath -> dependencyNames.add(path.libraryName)
                        }
                    }
                    outFile
                }
            // Update dependencies.
            jsDependencies = jsDependencies.copy(
                runtimeDependencies = jsDependencies.runtimeDependencies + dependencyNames.map { depName ->
                    // Our build process should provide library configs for all imports.
                    JsDependency(
                        jsLibraryNames.getValue(depName),
                        libraryConfigurations.byLibraryName.getValue(depName).version() ?: "*",
                        depName,
                    )
                },
            )
            jsDependencies = jsDependencies.withDependency(buildTemperCoreDependency())

            val exportingTranslations = translations.filter {
                // This is true if it contains any non-test content, or if it's empty of tests as well.
                it.dependencyCategory == DependencyCategory.Production
            }
            val exports = mutableMapOf<String, FilePath>()
            // Export all modules.
            for (translation in exportingTranslations) {
                exports[translation.outPath.exportPath()] = translation.outPath
            }
            // Also a main to init everything, and just call it "index.js".
            // It's responsible for loading the submodules and re-exporting
            // the interface of any top-level module.
            exports["."] = mainFilePath

            buildList<OutputFileSpecification> {
                addAll(updatedTranslations)
                add(generateMainJsForFileModules(mainFilePath, exportingTranslations))
                add(generatePackageJson(exports = exports))
            }
        } else {
            translations.map translations@{ (outPath, program) ->
                TranslatedFileSpecification(
                    outPath,
                    MimeType.javascript,
                    program,
                )
            }
        }

        val deps = dependenciesBuilder
        val from = libraryConfigurations.currentLibraryConfiguration.libraryName
        jsDependencies.allDependencies.forEach { jsDep ->
            val to = jsDep.temperLibraryName
            if (to != null) { deps.addDependency(from = from, to = to) }
        }
        jsLibraryNames[from]?.let { jsLibraryName ->
            deps.addMetadata(from, JsMetadataKey.JsLibraryName, jsLibraryName)
        }
        deps.addMetadata(from, JsMetadataKey.MainPath, mainFilePath)

        return allOutputFiles
    }

    private fun prepareTesting(program: Js.Program, stdTestingRelativePath: String?) {
        // If we have a relative path to std/testing, it must be because we're not using the standard name.
        // So default to the optional relative path.
        // TODO Once we standardize imports for funtests, always just use the global name.
        val stdTestingPrefix = stdTestingRelativePath ?: run {
            val std = libraryConfigurations.byLibraryName.getValue(DashedIdentifier.temperStandardLibraryIdentifier)
            "${std.jsLibraryName()}/$TESTING_BASENAME"
        }
        this.jsDependencies = this.jsDependencies
            .withTestDependency(JsDependency("mocha", "^10.0.0", null))
            .withTestDependency(JsDependency("mocha-junit-reporter", "^2.0.2", null))
        // The overall goal is to find code that imports from the std/testing lib, move them to the test
        // directory and convert the import to something like
        // const test_17 = it;
        // import assert  from 'assert';
        // const assert_35 = assert;
        // The mixed import style is apparently required.
        data class DeclarationInfo(val pos: Position, val name: Js.Identifier)
        program.topLevel = program.topLevel.flatMap { topLevel ->
            var itName: DeclarationInfo? = null
            when (topLevel) {
                is Js.ImportDeclaration ->
                    // use the prefix to abstract over .temper.md and .md
                    if (topLevel.source.value.startsWith(stdTestingPrefix)) {
                        topLevel.specifiers = topLevel.specifiers.flatMap { imported: Js.Imported ->
                            when (imported) {
                                is Js.ImportDefaultSpecifier ->
                                    listOf(imported)

                                is Js.ImportNamespaceSpecifier -> listOf(imported)
                                is Js.ImportSpecifiers -> {
                                    val updatedSpecifiers = imported.specifiers.flatMap { specifier ->
                                        with(specifier.local.name.text) {
                                            when {
                                                startsWith("test_") -> {
                                                    itName = DeclarationInfo(specifier.pos, specifier.local)
                                                    listOf()
                                                }

                                                else -> listOf(specifier)
                                            }
                                        }
                                    }
                                    if (updatedSpecifiers.isEmpty()) {
                                        emptyList()
                                    } else {
                                        imported.specifiers = updatedSpecifiers
                                        listOf(imported)
                                    }
                                }
                            }
                        }
                        // Need this to enable the smart cast since otherwise you have a var in a closure
                        val itInfo = itName
                        val itTopLevels: List<Js.TopLevel> = if (itInfo != null) {
                            listOf(
                                Js.VariableDeclaration(
                                    itInfo.pos,
                                    listOf(
                                        Js.VariableDeclarator(
                                            itInfo.pos,
                                            itInfo.name,
                                            init = Js.Identifier(
                                                itInfo.pos,
                                                JsIdentifierName("it"),
                                                null,
                                            ),
                                        ),
                                    ),
                                    Js.DeclarationKind.Const,
                                ),
                            )
                        } else {
                            emptyList()
                        }
                        itTopLevels +
                            if (topLevel.specifiers.isNotEmpty()) listOf(topLevel) else emptyList()
                    } else {
                        listOf(topLevel)
                    }

                else -> listOf(topLevel)
            }
        }
    }

    private fun linkModules(translations: List<Translation>): List<Translation> {
        val declaring = mutableMapOf<JsIdentifierName, MutableSet<FilePath>>()
        val exportedNames = mutableMapOf<ExportedName, Pair<FilePath, JsIdentifierName>>()
        // Find declarations.
        for ((textFile, program) in translations) {
            for (topLevel in program.topLevel) {
                val declaredNames = findDeclaredNames(topLevel, exportedNames, textFile)
                for (declaredName in declaredNames) {
                    declaring.putMultiSet(declaredName, textFile)
                }
            }
        }
        // Import into each according to their needs.
        val importedBySomeOtherFile = mutableMapOf<FilePath, MutableSet<JsIdentifierName>>()
        for ((path, program) in translations) {
            val needs = mutableMapOf<FilePath, MutableSet<JsIdentifierName>>()
            val localNameToExportedName = mutableMapOf<JsIdentifierName, JsIdentifierName>()
            walkDepthFirst(program) {
                when (it) {
                    is Js.Identifier -> {
                        val name = it.name
                        val declarers = declaring[name]
                        if (declarers != null) {
                            if (path !in declarers) {
                                val declarer = declarers.first()
                                needs.putMultiSet(declarer, name)
                                importedBySomeOtherFile.putMultiSet(declarer, name)
                            }
                        } else {
                            it.sourceIdentifier?.let exported@{ id ->
                                val export = exportedNames[id] ?: return@exported
                                if (export.first != path) {
                                    // This is expected only for split modules such as test extraction.
                                    // If imported explicitly from another module, we expect to see a local name.
                                    // The effect of directly importing and using the ExportedName is somewhat the same
                                    // as if it had been directly in this module in terms of the name we use.
                                    needs.putMultiSet(export.first, export.second)
                                    // Keep out of importedBySomeOtherFile because already exported.
                                }
                            }
                        }
                        VisitCue.Continue
                    }
                    is Js.ImportSpecifier ->
                        // The exported name is not an ID that we need.
                        // We don't need the local name because we've already got it.
                        VisitCue.SkipOne
                    else -> VisitCue.Continue
                }
            }
            val importsNeeded = needs.entries
            val importPos = program.pos.leftEdge
            val importDeclarations = importsNeeded.mapNotNull imports@{ (fileToImportFrom, namesToImport) ->
                val pathParts = if (fileToImportFrom.segments.firstOrNull() == globalPathSegment) {
                    // Global references are handled through tmpl imports.
                    return@imports null
                } else {
                    // Relative import.
                    val pathParts = path.relativePathTo(fileToImportFrom).toMutableList()
                    if (pathParts.getOrNull(0) !in relativeModulePathStarts) {
                        pathParts.add(index = 0, element = SameDirPseudoFilePathSegment)
                    }
                    pathParts
                }
                if (fileToImportFrom in importedBySomeOtherFile) {
                    // An internal version will be generated because someone is using non-publics.
                    // So just go to internals, whether the specifiers here are internal or not.
                    // Simplifies the bookkeeping.
                    pathParts[pathParts.lastIndex] =
                        (pathParts.last() as FilePathSegment).withExtension(INTERNAL_EXTENSION)
                }
                Js.ImportDeclaration(
                    importPos,
                    specifiers = listOf(
                        Js.ImportSpecifiers(
                            importPos,
                            namesToImport.sortedBy { it.text }.map {
                                Js.ImportSpecifier(
                                    importPos,
                                    imported = Js.Identifier(
                                        importPos,
                                        localNameToExportedName[it] ?: it,
                                        null,
                                    ),
                                    local = Js.Identifier(importPos, it, null),
                                )
                            },
                        ),
                    ),
                    source = Js.StringLiteral(
                        importPos,
                        // TODO: this is probably not right.  What is the actual rule for escaping
                        // path segments in the web-platform / Node / Deno worlds?
                        pathParts.join(separator = UNIX_FILE_SEGMENT_SEPARATOR, isDir = false),
                    ),
                )
            }
            program.topLevel = importDeclarations + program.topLevel
        }
        // Export from each according to their ability.
        return translations.flatMap { translation ->
            val program = translation.program
            val exported = importedBySomeOtherFile[translation.outPath]
            if (exported?.isNotEmpty() == true) {
                val exportsInOrder = exported.sortedBy { it.text }
                val exportPos = program.pos.rightEdge
                val exportDeclaration = Js.ExportNamedDeclaration(
                    exportPos,
                    doc = Js.MaybeJsDocComment(exportPos, null),
                    declaration = null,
                    specifiers = exportsInOrder.map { exportedName ->
                        Js.ExportSpecifier(
                            exportPos,
                            local = Js.Identifier(exportPos, exportedName, null),
                            exported = Js.Identifier(exportPos, exportedName, null),
                        )
                    },
                    source = null,
                )
                // Find already exported things to re-export from public face.
                val exporteds = findTopLevelExportedIds(translation.program)
                // Add new exports to internal, rename to internal, and add exporting public face.
                program.topLevel += exportDeclaration
                val internalOutPath = translation.outPath.withExtension(INTERNAL_EXTENSION)!!
                listOf(
                    translation.copy(outPath = internalOutPath),
                    translation.copy(program = buildPublicFace(exportPos, exporteds, internalOutPath)),
                )
            } else {
                listOf(translation)
            }
        }
    }

    override val supportNetwork: SupportNetwork get() = JsSupportNetwork

    override fun wrapTokenSink(tokenSink: TokenSink): TokenSink = Companion.wrapTokenSink(tokenSink)

    companion object {
        internal const val BACKEND_ID = "js"

        internal fun wrapTokenSink(tokenSink: TokenSink) =
            CommentGroupingTokenSink(tokenSink, JsFormattingHints)

        /** The default file extension for output files. Node also supports `".js"` with proper package settings. */
        const val EXTENSION = ".js"

        /** The file extension for internal-use-only output files. */
        const val INTERNAL_EXTENSION = ".internal$EXTENSION"

        /** Config files may export a name with this text to specify the JS library name */
        val jsNameConfigKey = Symbol("jsName")

        const val INDEX_NAME = "index.js"
    }

    @PluginBackendId(BACKEND_ID)
    @BackendSupportLevel(isSupported = true, isDefaultSupported = true, isTested = true)
    object Factory : Backend.Factory<JsBackend> {

        /**
         * <!-- snippet: backend/js/id -->
         * Backend ID: `js`
         */
        override val backendId = BackendId(uniqueId = BACKEND_ID)

        override val backendMeta = BackendMeta(
            languageLabel = LanguageLabel("js"),
            backendId = backendId,
            fileExtensionMap = mapOf(
                FileType.Module to EXTENSION,
                FileType.JsModule to ".mjs",
                FileType.Script to EXTENSION,
            ),
            mimeTypeMap = mapOf(
                FileType.Module to MimeType.javascript,
                FileType.Script to MimeType.javascriptApp,
            ),
        )

        override val extraHelpTopics: Map<BackendHelpTopicKey, OccasionallyHelpful> = mapOf(
            BackendHelpTopicKeys.ABOUT to Helpful.of(
                "About the JavaScript backend (-b js)",
                // TODO(mike, docs): How can we sync this with our docs or easily embed a
                // URL prefix to our docs?
                """
                    |Translates Temper to JavaScript with types in documentation comments for
                    |[compatibility with TypeScript][TS-compat].
                """.trimMargin(),
            ),
            BackendHelpTopicKeys.REPL to Helpful.of(
                "Running a JavaScript REPL with libraries loaded",
                """
                    |Runs `node --interactive` in a temporary directory that has the
                    |built modules `npm install`ed and `--require`ed.
                """.trimMargin(),
            ),
        )

        override val coreLibraryResources = declareResources(
            dirPath("lang", "temper", "be", "js", "temper-core"),
            filePath("package.json"),
            filePath("tsconfig.json"),
            filePath(INDEX_NAME),
            filePath("async.js"),
            filePath("bitvector.js"),
            filePath("check-type.js"),
            filePath("core.js"),
            filePath("date.js"),
            filePath("deque.js"),
            filePath("float.js"),
            filePath("interface.js"),
            filePath("listed.js"),
            filePath("mapped.js"),
            filePath("net.js"),
            filePath("pair.js"),
            filePath("regex.js"),
            filePath("string.js"),
        )

        override val specifics: NodeSpecifics get() = NodeSpecifics

        /**
         * A label used to identify the language, for example in highlighted Markdown code blocks.
         */
        override fun make(setup: BackendSetup<JsBackend>) =
            JsBackend(setup, extension = EXTENSION)
    }
}

private fun declaredInPattern(pattern: Js.Pattern, ids: MutableList<JsIdentifierName>) {
    when (pattern) {
        is Js.Identifier -> ids.add(pattern.name)
        is Js.AssignmentPattern -> declaredInPattern(pattern.left, ids)
        is Js.RestElement -> declaredInPattern(pattern.argument, ids)
        is Js.MemberExpression -> Unit
        is Js.ArrayPattern -> pattern.elements.forEach { element ->
            when (element) {
                is Js.Pattern -> declaredInPattern(element, ids)
                is Js.ArrayHole -> Unit
            }
        }
        is Js.ObjectPattern -> pattern.properties.forEach {
            when (it) {
                is Js.ObjectPropertyPattern -> declaredInPattern(it.pattern, ids)
                is Js.RestElement -> declaredInPattern(it, ids)
            }
        }
    }
}

internal fun walkDepthFirst(t: Js.Tree, action: (Js.Tree) -> VisitCue): VisitCue {
    when (action(t)) {
        VisitCue.Continue -> Unit
        VisitCue.SkipOne -> return VisitCue.Continue
        VisitCue.AllDone -> return VisitCue.AllDone
    }
    val n = t.childCount
    for (i in 0 until n) {
        val cue = walkDepthFirst(t.child(i), action)
        if (cue == VisitCue.AllDone) { return VisitCue.AllDone }
    }
    return VisitCue.Continue
}

/** An ES-modules file-like module path is one that starts with "/", "./", "../". */
private val relativeModulePathStarts =
    setOf(SameDirPseudoFilePathSegment, ParentPseudoFilePathSegment)

private data class JsDependency(
    val name: String,
    val versionString: String,
    val temperLibraryName: DashedIdentifier?,
)
private data class JsDependencies(
    val runtimeDependencies: List<JsDependency>,
    val testDependencies: List<JsDependency>,
) {
    val allDependencies get() = runtimeDependencies + testDependencies
}

@Suppress("UnusedPrivateMember")
private fun JsDependencies.withDependency(dep: JsDependency): JsDependencies = //
    JsDependencies(this.runtimeDependencies + dep, this.testDependencies)
private fun JsDependencies.withTestDependency(dep: JsDependency): JsDependencies =
    JsDependencies(this.runtimeDependencies, this.testDependencies + dep)

fun LibraryConfiguration.jsLibraryName() = backendLibraryName(JsBackend.jsNameConfigKey)

private fun PropertySink.buildDependencies(key: String, deps: List<JsDependency>) {
    maybeBuildObj(key, deps.sortedBy { it.name }.associate { it.name to it.versionString })
}

private fun PropertySink.maybeBuildObj(key: String, pairs: Map<String, String>) {
    if (pairs.isNotEmpty()) {
        key(key) { value(pairs) }
    }
}

internal data class Translation(
    val outPath: FilePath,
    val program: Js.Program,
    val tmpLModule: TmpL.Module,
    val dependencyCategory: DependencyCategory,
)

private fun buildTemperCoreDependency(): JsDependency {
    val json = loadTemperCorePackageJson()
    // The name here has to match DashedIdentifier.temperCoreLibraryIdentifier, but just use the json value.
    return JsDependency((json["name"] as JsonString).s, (json["version"] as JsonString).s, null)
}

private fun loadTemperCorePackageJson(): JsonObject {
    val text = loadResource(JsBackend, "lang/temper/be/js/temper-core/package.json")
    return StructureParser.parseJson(text) as JsonObject
}

/** convention of mocha that all tests are in the test directory */
internal val testDir = dirPath("test")

private fun findDeclaredNames(
    topLevel: Js.TopLevel,
    exportedNames: MutableMap<ExportedName, Pair<FilePath, JsIdentifierName>>,
    textFile: FilePath,
): List<JsIdentifierName> = when (topLevel) {
    is Js.FunctionDeclaration -> listOf(topLevel.id.name)
    is Js.ClassDeclaration -> listOf(topLevel.id.name)
    is Js.VariableDeclaration -> {
        val ids = mutableListOf<JsIdentifierName>()
        topLevel.declarations.forEach { declaredInPattern(it.id, ids) }
        ids.toList()
    }

    is Js.DocumentedDeclaration -> findDeclaredNames(topLevel.decl, exportedNames, textFile)

    is Js.Statement -> emptyList()
    is Js.ImportDeclaration ->
        topLevel.specifiers.flatMap { imported ->
            when (imported) {
                is Js.ImportSpecifiers -> imported.specifiers.map { it.local.name }
                is Js.ImportDefaultSpecifier -> emptyList()
                is Js.ImportNamespaceSpecifier -> emptyList()
            }
        }

    is Js.ExportNamedDeclaration -> {
        val id = when (val declaration = topLevel.declaration) {
            is Js.ClassDeclaration -> declaration.id
            is Js.ExceptionDeclaration -> null
            is Js.FunctionDeclaration -> declaration.id
            is Js.VariableDeclaration -> declaration.declarations.first().id as? Js.Identifier
            null -> null
        }
        val sourceId = id?.sourceIdentifier
        val exported = id?.name
        if (exported == null) {
            listOf()
        } else if (sourceId is ExportedName) {
            // TODO Conjure missing imports to these.
            check(sourceId !in exportedNames)
            exportedNames[sourceId] = textFile to exported
            emptyList()
        } else {
            listOf(exported)
        }
    }

    is Js.ExportDefaultDeclaration,
    is Js.ExportAllDeclaration,
    -> emptyList()
}

private fun findTopLevelExportedIds(program: Js.Program): List<Js.Identifier> {
    // And the dig here only goes through top levels rather than arbitrarily deep in the tree.
    fun digId(tree: Js.Tree): List<Js.Identifier> {
        // TODO Dig out type aliases in comments?
        return when (tree) {
            is Js.ClassDeclaration -> listOf(tree.id)
            is Js.DocumentedDeclaration -> digId(tree.decl)
            is Js.ExportNamedDeclaration -> when (val declaration = tree.declaration) {
                null -> tree.specifiers.map { it.exported }
                else -> digId(declaration)
            }

            is Js.FunctionDeclaration -> listOf(tree.id)
            // Presume we don't generate destructuring for top levels.
            is Js.VariableDeclaration -> tree.declarations.mapNotNull { it.id as? Js.Identifier }
            else -> emptyList()
        }
    }
    val exporteds = program.topLevel.flatMap { tree ->
        val ids = digId(tree)
        ids.mapNotNull { id ->
            when (id.sourceIdentifier) {
                is ExportedName -> id
                else -> null
            }
        }
    }
    return exporteds
}

private fun buildPublicFace(pos: Position, exporteds: List<Js.Identifier>, internalOutPath: FilePath) = Js.Program(
    pos,
    listOf(
        Js.ExportNamedDeclaration(
            pos,
            doc = Js.MaybeJsDocComment(pos, null),
            declaration = null,
            specifiers = exporteds.map { Js.ExportSpecifier(pos, it.deepCopy(), it.deepCopy()) },
            source = Js.StringLiteral(pos, "./${internalOutPath.last().fullName}"),
        ),
    ),
)

private fun FilePath.importReadyPath(): String =
    this.segments.importReadyPath(isDir = this.isDir)

private fun FilePath.exportPath(): String {
    // Unlike for importReadyPath, we always have a simple FilePath instance here.
    // The logic here presumes no dot extension except for the last segment at most.
    val segments = listOf(".") + segments.filter { it.fullName != JsBackend.INDEX_NAME }.map { it.baseName }
    return segments.joinToString(UNIX_FILE_SEGMENT_SEPARATOR)
}

private fun List<FilePathSegmentOrPseudoSegment>.importReadyPath(isDir: Boolean): String {
    var segments = this
    when (segments.firstOrNull()) {
        // Fine JS relative import path starts with "." or ".."
        SameDirPseudoFilePathSegment, ParentPseudoFilePathSegment -> {}
        else -> segments = listOf(SameDirPseudoFilePathSegment) + segments
    }
    return segments
        .join(UNIX_FILE_SEGMENT_SEPARATOR, isDir = isDir)
}
