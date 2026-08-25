package lang.temper.be.js

import lang.temper.ast.VisitCue
import lang.temper.be.Backend
import lang.temper.be.BackendHelpTopicKey
import lang.temper.be.BackendHelpTopicKeys
import lang.temper.be.BackendSetup
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
import lang.temper.be.tmpl.findCommonTopLevels
import lang.temper.be.tmpl.injectSuperCallMethods
import lang.temper.common.MimeType
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValueBuilder
import lang.temper.common.structure.PropertySink
import lang.temper.common.structure.StructureParser
import lang.temper.common.subListToEnd
import lang.temper.format.TokenSink
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
import lang.temper.log.SameDirPseudoFilePathSegment
import lang.temper.log.UNIX_FILE_SEGMENT_SEPARATOR
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.last
import lang.temper.log.unknownPos
import lang.temper.name.BackendId
import lang.temper.name.BackendMeta
import lang.temper.name.DashedIdentifier
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
        withTentative = { injectSuperCallMethods(it) },
    )

    override fun translate(finished: TmpL.ModuleSet): List<OutputFileSpecification> {
        val jsNames = JsNames()
        val jsLibraryNames = libraryConfigurations.byLibraryName.mapValues { it.value.jsLibraryName() }

        // Prep for test identification.
        val testPaths = mutableSetOf<FilePath>()

        val translations: List<Translation> =
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
        // Extract test paths.
        for (translation in translations) {
            if (translation.dependencyCategory == DependencyCategory.Test) {
                testPaths.add(translation.outPath)
            }
        }
        // Connected files.
        val rootSize = libraryConfigurations.currentLibraryConfiguration.libraryRoot.segments.size
        val connectedFiles = rawBackendFiles.map { file ->
            MetadataFileSpecification(
                path = FilePath(file.key.segments.subListToEnd(rootSize), isDir = false),
                mimeType = MimeType.javascript,
                content = file.value,
            )
        }

        val allOutputFiles = if (config.makeMetaDataFile) {
            val dependencyNames = mutableSetOf<DashedIdentifier>()
            val updatedTranslations = translations
                .map translations@{ (outPath, program, tmpLModule) ->
                    if (outPath in testPaths) {
                        jsDependencies = jsDependencies
                            .withTestDependency(JsDependency("mocha", "^10.0.0", null))
                            .withTestDependency(JsDependency("mocha-junit-reporter", "^2.0.2", null))
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
            // Export all public modules.
            translations@ for (translation in exportingTranslations) {
                val outPath = translation.outPath
                val outName = outPath.last().fullName
                // Skip internal modules.
                outName.endsWith(INTERNAL_EXTENSION) && continue@translations
                outName.startsWith("_") && continue@translations
                // Export others without js extension.
                exports[outPath.exportPath()] = outPath
            }
            // Also a main to init everything, and just call it "index.js".
            // It's responsible for loading the submodules and re-exporting
            // the interface of any top-level module.
            exports["."] = mainFilePath

            buildList {
                addAll(updatedTranslations)
                addAll(connectedFiles)
                add(generateMainJsForFileModules(mainFilePath, exportingTranslations))
                add(generatePackageJson(exports = exports))
            }
        } else {
            buildList {
                for ((outPath, program) in translations) {
                    TranslatedFileSpecification(
                        outPath,
                        MimeType.javascript,
                        program,
                    ).also { add(it) }
                }
                addAll(connectedFiles)
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
            filePath("int.js"),
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

private data class JsDependency(
    val name: String,
    val versionString: String,
    val temperLibraryName: DashedIdentifier?,
)
private data class JsDependencies(
    // We'll associate these by name later, which will collapse any redundancies in the lists.
    val runtimeDependencies: List<JsDependency>,
    val testDependencies: List<JsDependency>,
) {
    val allDependencies get() = runtimeDependencies + testDependencies
}

@Suppress("UnusedPrivateMember")
private fun JsDependencies.withDependency(dep: JsDependency): JsDependencies =
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
