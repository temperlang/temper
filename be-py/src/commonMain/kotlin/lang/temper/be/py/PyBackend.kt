package lang.temper.be.py

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.SiblingData
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.names.LookupNameVisitor
import lang.temper.be.names.NameSelection
import lang.temper.be.py.PyDottedIdentifier.Companion.dotted
import lang.temper.be.py.helper.MypySpecifics
import lang.temper.be.py.helper.PythonSpecifics
import lang.temper.be.tmpl.LibraryRootContext
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
import lang.temper.common.MimeType
import lang.temper.common.console
import lang.temper.common.jsonEscaper
import lang.temper.common.partitionNotNull
import lang.temper.common.toStringViaBuilder
import lang.temper.frontend.Module
import lang.temper.fs.ResourceDescriptor
import lang.temper.fs.declareResources
import lang.temper.fs.loadResource
import lang.temper.lexer.Genre
import lang.temper.library.LibraryConfiguration
import lang.temper.library.authors
import lang.temper.library.backendLibraryName
import lang.temper.library.description
import lang.temper.library.homepage
import lang.temper.library.license
import lang.temper.library.repository
import lang.temper.library.version
import lang.temper.library.versionOrDefault
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.Position
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.unknownPos
import lang.temper.name.BackendId
import lang.temper.name.BackendMeta
import lang.temper.name.DashedIdentifier
import lang.temper.name.FileType
import lang.temper.name.LanguageLabel
import lang.temper.name.ModuleName
import lang.temper.name.OutName
import lang.temper.name.Symbol
import lang.temper.name.identifiers.IdentStyle
import lang.temper.value.DependencyCategory

/**
 * <!-- snippet: backend/py -->
 * # Python Backend
 *
 * ⎀ backend/py/id
 *
 * Translates Temper to Python3 with types and builtin *[mypy][mypy-lang]*
 * integration which helps numerics heavy code perform much better
 * than Python without type optimizations applied.
 *
 * Targets [Python 3.11][python-3.11.0].  For best support, use CPython.
 *
 * To get started with this backend, see [the tutorial](../tutorial/index.md#use-py).
 *
 * ## Translation notes
 *
 * Temper's [snippet/type/Void] type translates to a return value of
 * Python *None*.
 *
 * Temper default expressions are evaluated as needed, so do not
 * suffer the [singly evaluated default expression pitfall][default-pitfall].
 *
 * Named arguments are translated to named arguments in Python, but
 * TODO: we need to remove numeric suffixes.
 *
 * [python-3.11.0]: https://docs.python.org/release/3.11.0/
 * [mypy-lang]: https://mypy-lang.org/
 * [default-pitfall]: https://towardsdatascience.com/python-pitfall-mutable-default-arguments-9385e8265422
 *
 * <!-- snippet: backend/mypyc -->
 * # Mypyc Backend
 *
 * A variant of the [Python backend][snippet/backend/py] which invokes mypyc to generate type-optimized
 * versions of modules.
 *
 * ⎀ backend/mypyc/id
 */
class PyBackend private constructor(
    val pythonVersion: PythonVersion,
    setup: BackendSetup<PyBackend>,
) : Backend<PyBackend>(pythonVersion.backendId, setup) {
    override fun collate(
        outputFiles: List<OutputFileSpecification>,
        siblings: SiblingData<List<OutputFileSpecification>>,
    ): List<OutputFileSpecification> {
        var files = super.collate(outputFiles, siblings)
        if (pythonVersion == PythonVersion.MypyC) {
            files = collateMypyC(files, siblings)
        }
        return files
    }

    private fun collateMypyC(
        outputFiles: List<OutputFileSpecification>,
        siblings: SiblingData<List<OutputFileSpecification>>,
    ): List<OutputFileSpecification> {
        val allFiles = mutableSetOf<OutputFileSpecification>()
        allFiles.addAll(outputFiles)

        CliEnv.using(PythonSpecifics, ShellPreferences.mypyc(console), cancelGroup) {
            val excludes = mutableListOf<String>()

            MypyC.coreLibraryResources.forEach { resource ->
                if (resource.rsrcPath.lastOrNull()?.extension == ".py") {
                    this.write(resource.load(), resource.rsrcPath)
                    // Compile temper-core separately.
                    excludes.add("--exclude")
                    excludes.add("${resource.rsrcPath}")
                }
            }

            siblings.dataByLibraryRoot.entries.forEach { (root, files) ->
                files.forEach { file ->
                    when (file) {
                        is TranslatedFileSpecification -> {
                            val full = file.path
                            this.write("${file.content}", full)
                            // Exclude from compilation both tests and other libraries.
                            // Pytest complains when a test is compiled to native.
                            if (
                                siblings.backendsByLibraryRoot[root] != this@PyBackend ||
                                (full.segments.size > 1 && full.segments[0] == testsDir)
                            ) {
                                excludes.add("--exclude")
                                excludes.add("$full")
                            }
                        }
                        else -> {}
                    }
                }
            }

            fun registerUsedFile(path: FilePath) {
                when (path.segments.lastOrNull()) {
                    FilePathSegment(".mypy_cache") -> return
                    FilePathSegment("build") -> return
                    FilePathSegment("__pycache__") -> return
                }
                if (path.isFile) {
                    if (path.lastOrNull()?.extension == ".so" || path.lastOrNull()?.extension == ".pyd") {
                        allFiles.add(
                            ByteArrayFileSpecification(
                                path,
                                this.readBinary(path),
                            ),
                        )
                    }
                }
                if (path.isDir) {
                    this.readDir(path).forEach { file ->
                        registerUsedFile(file)
                    }
                }
            }

            registerUsedFile(FilePath.emptyPath)
        }

        return allFiles.toList()
    }

    private fun translator(names: PyNames, defaultGenre: Genre): PyTranslator =
        PyTranslator(names, defaultGenre, pythonVersion = pythonVersion, dependenciesBuilder = dependenciesBuilder)

    private fun generateProjectMetaDataFile(
        dependencies: List<Dependency>,
    ): MetadataFileSpecification {
        // generates pyproject.toml file per
        // https://peps.python.org/pep-0621/
        // see https://toml.io/en/v1.0.0 for more on TOML

        val libraryConfiguration = libraryConfigurations.currentLibraryConfiguration

        /**
         * Normalize per https://peps.python.org/pep-0503/
         */
        fun normalize(str: String): String {
            return str.replace(Regex("[-_.]+"), "-").lowercase()
        }

        fun expand(name: String, appendable: Appendable) {
            val allDeps = dependencies.sortedBy { it.name }
            val mypyDeps = if (pythonVersion == PythonVersion.MypyC) {
                listOf(Dependency("mypy-extensions", "~1.0.0"))
            } else {
                listOf()
            }
            val version = jsonEscaper.escape(libraryConfiguration.versionOrDefault())
            // TODO Make a proper list with email once we support that in config.
            val authors = libraryConfiguration.authors()?.let {
                "{ name = ${jsonEscaper.escape(it.scrubRfc822Name())} }"
            } ?: ""
            val description = jsonEscaper.escape(libraryConfiguration.description() ?: "")
            // https://peps.python.org/pep-0639/#add-license-expression-field
            // https://packaging.python.org/en/latest/specifications/core-metadata/#license-expression
            val license = jsonEscaper.escape(libraryConfiguration.license() ?: "LicenseRef-Proprietary")
            val optionalMeta = listOfNotNull(
                libraryConfiguration.homepage()?.let { "homepage = ${jsonEscaper.escape(it)}" },
                libraryConfiguration.repository()?.let { "repository = ${jsonEscaper.escape(it)}" },
            ).joinToString("\n")

            // Hatch's pyproject.toml docs: https://hatch.pypa.io/latest/config/metadata/
            // Validation tool: https://github.com/abravalheri/validate-pyproject
            // Note that as of 2024-09-23, PEP 639 is still provisional, so the tool doesn't
            // accept the `license-expression` field yet.
            // https://github.com/abravalheri/validate-pyproject/issues/70
            val text =
                """
                    |[project]
                    |name = "${normalize(name)}"
                    |version = $version
                    |description = $description
                    |readme = {text = "n/a", content-type = "text/plain"}
                    |requires-python = ">=3.11"
                    |license = { text = $license }
                    |authors = [ $authors ]
                    |$optionalMeta
                    |dependencies = [${formatDepsPep621(allDeps + mypyDeps)}]
                    |
                    |[build-system]
                    |requires = ["hatchling"]
                    |build-backend = "hatchling.build"
                    |
                """.trimMargin()
            // The python_functions entry has the [!_] bit to exclude the renamed version of the test function
            // defined in the standard lib
            appendable.append(text)
            if (backendId == MypyC.backendId) {
                appendable.append(
                    """
                    |[tool.hatch.build.targets.wheel.hooks.mypyc]
                    |dependencies = ["hatch-mypyc>=0.16.0"]
                    |
                    """.trimMargin(),
                )
            }
        }

        val libraryNameText = libraryConfiguration.pyLibraryName()
        /**
         * The setup for running pytest gets complicated here. Splitting the overall code into two packages with
         * different (but similar) pyproject.toml files.
         */
        // The real code has the config with itself but the test one goes at the base since it needs
        return MetadataFileSpecification(
            FilePath(listOf(FilePathSegment("pyproject.toml")), isDir = false),
            MimeType("application", "toml"),
            toStringViaBuilder { expand(libraryNameText, it) },
        )
    }

    override fun tentativeTmpL(): TmpL.ModuleSet {
        val pyLibraryNames = libraryConfigurations.byLibraryRoot.mapValues {
            FilePathSegment(it.value.pyLibraryName())
        }

        // TODO Do we need to choose names already in this pass so we can use them in finishTmpLImports?
        return TmpLTranslator.translateModules(
            logSink,
            readyModules,
            PySupportNetwork,
            libraryConfigurations = libraryConfigurations,
            dependencyResolver = dependencyResolver,
            tentativeOutputPathFor = {
                pyPathForModuleLocation(it.loc as ModuleName, pyLibraryNames)
            },
        )
    }

    private var pyNames: PyNames? = null

    override fun translate(finished: TmpL.ModuleSet): List<OutputFileSpecification> {
        val pyNames = PyNames(LookupNameVisitor().visit(finished), abbreviated = config.abbreviated)
        this.pyNames = pyNames

        val libraryConfigurationMap = libraryConfigurations.byLibraryName
        val pyLibraryNames = libraryConfigurationMap.mapValues { it.value.pyLibraryName() }
        val temperLibraryName = libraryConfigurations.currentLibraryConfiguration.libraryName
        val pyLibraryDir = FilePathSegment(pyLibraryNames.getValue(temperLibraryName))
        val pyLibraryName = pyDirToLibraryName(pyLibraryDir)

        val topModule = translateTmpL(finished, pyNames)
        val anyTests = topModule.any {
            it.program?.dependencyCategory == DependencyCategory.Test
        }

        // Link modules to shared definitions by export and import.
        linkModules(topModule)

        val outputFileSpecifications = mutableListOf<OutputFileSpecification>()
        if (config.makeMetaDataFile || anyTests) {
            val dependencies = buildList {
                val importDeps = dependencyNames.map { depName ->
                    Dependency(
                        pyLibraryNames.getValue(depName),
                        libraryConfigurationMap.getValue(depName).version() ?: "*",
                    )
                }
                addAll(importDeps)
                add(buildTemperCoreDependency())
            }
            outputFileSpecifications.add(generateProjectMetaDataFile(dependencies))
        }
        topModule.mapNotNullTo(outputFileSpecifications) { mod ->
            if (mod.moduleId != null) {
                mod.write().toTreeFile()
            } else {
                null
            }
        }

        // Make sure we have something that matches the project entry in `pyproject.toml`.
        // If we don't do this, when all modules are test modules, trying to run tests
        // will fail with an error message like https://stackoverflow.com/q/75397736/20394
        val mainPackageName = FilePathSegment(
            toModuleFileName(
                libraryConfigurations.currentLibraryConfiguration.pyLibraryName(),
            ),
        )
        if (
            outputFileSpecifications.none {
                it.path.segments.firstOrNull() == mainPackageName
            }
        ) {
            outputFileSpecifications.add(
                MetadataFileSpecification(
                    path = FilePath(listOf(mainPackageName, dunderInitPyFileName), isDir = false),
                    mimeType = mimeType,
                    content = "",
                ),
            )
        }

        val deps = dependenciesBuilder
        val from = libraryConfigurations.currentLibraryConfiguration.libraryName
        dependencyNames.forEach { to ->
            deps.addDependency(from = from, to = to)
        }
        deps.addMetadata(from, PyMetadataKey.PyLibraryName(pythonVersion), pyLibraryName)
        deps.addMetadata(from, PyMetadataKey.PyLibraryBaseDir(pythonVersion), pyLibraryDir)

        return outputFileSpecifications.toList()
    }

    override fun selectNames(): List<NameSelection> = pyNames!!.selectedNames()

    override fun libraryRootContext(libraryConfiguration: LibraryConfiguration) = LibraryRootContext(
        inRoot = libraryConfiguration.libraryRoot,
        outRoot = safeForImportFilePath(dirPath(libraryConfiguration.libraryName.text)),
    )

    private fun pyPathForModuleLocation(
        moduleName: ModuleName,
        pyLibraryNames: Map<FilePath, FilePathSegment>,
    ): FilePath {
        val libraryNameSegment = pyLibraryNames.getValue(moduleName.libraryRoot())
        // When someone imports translated Python, they should use the library name to
        // refer to it, so we want that as a prefix.  Then we need to put the file
        // somewhere that relates to the Temper source; the suffix is that part.
        val unsafeFilePath = FilePath(listOf(libraryNameSegment), isDir = true).resolve(
            allocateTextFile(
                moduleName, libraryConfigurations.currentLibraryConfiguration,
                outputFileExtension = fileExtension,
                defaultName = "module",
            ),
        )

        // Convert '-' to '_' so that there is a straightforward conversion from
        // the file path to the dotted identifier used to refer to the module.
        return safeForImportFilePath(unsafeFilePath)
    }

    /**
     * Laying out files is a bit more complicated in Python. We need to take account of
     * where roots exist, and special files like `__init__.py` and `__main__.py`.
     * Returns a list of top modules for each library, where the first is the main library.
     */
    private fun translateTmpL(
        finished: TmpL.ModuleSet,
        names: PyNames,
    ): PyModule {
        val modules = finished.modules
        val top = PyModule(null)

        val translations = modules.flatMap { tmpLModule ->
            val trans = this@PyBackend.translator(names, finished.genre)
            val programs = trans.translate(tmpLModule)
            for (program in programs) {
                val mod = top.setProgram(program)
                mod.saveSupport(trans, program.dependencyCategory)
                top.saveSharedSupport(trans, program.dependencyCategory)
                // We're including this module, so include its dependencies on other libraries.
                for (import in tmpLModule.imports) {
                    when (val path = import.path) {
                        null, is TmpL.SameLibraryPath -> Unit
                        is TmpL.CrossLibraryPath ->
                            dependencyNames.add(path.libraryName)
                    }
                }
            }
            programs.map { tmpLModule.codeLocation.codeLocation to it }
        }
        // Create a pseudo-program for the top-level __init__.py if it's needed.
        val pythonLibraryName = libraryConfigurations.currentLibraryConfiguration.pyLibraryName()
        val pythonLibraryBaseName = FilePathSegment(toModuleFileName(pythonLibraryName))
        val libraryNameAsPyModuleKey = safeModuleName(pythonLibraryName)
        if (top[libraryNameAsPyModuleKey].program == null && finished.genre != Genre.Documentation) {
            top.setProgram(
                Py.Program(
                    pos = unknownPos,
                    body = buildList {
                        translations.filter { (_, program) ->
                            // Exclude all tests.
                            program.outputPath.segments.first().fullName != testsDir.fullName
                        }.mapIndexedTo(this) { index, (srcLocation, program) ->
                            val fromTopModule = srcLocation.relativePath() == FilePath.emptyPath
                            if (fromTopModule) {
                                // Re-export by importing directly into module namespace
                                val relImportPath = listOf("", program.outputPath.segments.last().baseName)
                                Py.ImportWildcardFrom(
                                    unknownPos,
                                    Py.ImportDotted(unknownPos, PyDottedIdentifier.dotted(relImportPath)),
                                )
                            } else {
                                // Import each, but make them ugly. This is for init side effects, not convenience.
                                // I originally tried deleting these after import, but mypyc seemed to dislike that.
                                Py.Import(
                                    unknownPos,
                                    listOf(
                                        Py.ImportAlias(
                                            unknownPos,
                                            Py.ImportDotted(
                                                unknownPos,
                                                PyDottedIdentifier.fromPath(program.outputPath),
                                            ),
                                            Py.Identifier(unknownPos, PyIdentifierName("_$index")),
                                        ),
                                    ),
                                )
                            }
                        }
                    },
                    dependencyCategory = DependencyCategory.Production,
                    genre = Genre.Library,
                    outputPath = FilePath(
                        segments = listOf(pythonLibraryBaseName), // PyModule infers the __init__.py basename
                        isDir = true,
                    ),
                ),
            )
        }

        return top
    }

    /** The first entry in [modules] is the top module for the main library. */
    private fun linkModules(modules: PyModule) {
        // Work out exports for each module, including from other libraries.
        val providers: Map<OutName, PyModule> =
            modules.flatMap { mod ->
                // Exclude imports from exports because we don't re-export for Temper-built Python.
                // The only imports here should be TmpL imports, and some might not apply after prod vs test filtering.
                mod.analyzeImports(excludeImportsFromExports = true)
                mod.exports.map { exp -> exp to mod }
            }.toMap()

        // Any separate support code that is shared may be imported by any module as needed. Stored at top module only.
        val sharedImports = modules.support.mapNotNull { s ->
            if (s.shared && s.supportCode is PySeparateCode) { s.name to s.supportCode } else null
        }.toMap()
        // Resolve imports.
        for (mod in modules) {
            // Sort the imports as they would appear in `from foo import bar`
            mod.program?.let { program ->
                val missingImports = mutableListOf<Py.Stmt>()
                val importStatements = Imports()
                mod.support.forEach { s ->
                    if (!s.shared && s.supportCode is PySeparateCode) {
                        importStatements.add(s.supportCode, asName = s.name)
                    }
                }
                mod.imports.forEach { importName ->
                    when (val prov = providers[importName]) {
                        null -> when (val shImp = sharedImports[importName]) {
                            null -> missingImports.add(
                                garbageStmt(unknownPos, "linkModules", "Can't find import for $importName"),
                            )
                            else -> importStatements.add(shImp, asName = importName)
                        }
                        else -> importStatements.add(prov.moduleId!!, importName, importName)
                    }
                }
                // See which original imports we actually use, always keeping our obfuscated init-all imports.
                val initAllPattern = Regex("""_\d+""")
                val (currentImports, body) = program.body.partitionNotNull { stmt ->
                    when (stmt) {
                        is Py.ImportStmt -> when {
                            stmt.simpleNames().any { name ->
                                name in mod.imports || initAllPattern.matches(name.outputNameText)
                            } -> true
                            else -> null
                        }
                        else -> false
                    }
                }
                // Compose things.
                val importAst = importStatements.statements().filter {
                    it !in currentImports
                }
                if (importAst.isNotEmpty()) {
                    var beforeImports = emptyList<Py.Stmt>()
                    var afterImports = emptyList<Py.Stmt>()
                    if (program.genre == Genre.Documentation) {
                        val pos = program.pos.leftEdge
                        beforeImports = listOf(
                            PyTranslator.codeFoldCommentLine(TmpL.BoilerplateCodeFoldStart(pos)),
                        )
                        afterImports = listOf(
                            PyTranslator.codeFoldCommentLine(TmpL.BoilerplateCodeFoldEnd(pos)),
                        )
                    }
                    program.body = beforeImports + currentImports + importAst + afterImports + body
                }
            }
        }
    }

    override val supportNetwork: SupportNetwork get() = PySupportNetwork

    private val dependencyNames = mutableSetOf<DashedIdentifier>()

    @PluginBackendId(PY_BACKEND_ID)
    @BackendSupportLevel(isSupported = true, isDefaultSupported = true, isTested = true)
    object Python3 : PyFactory(PythonVersion.Python311)

    @PluginBackendId(MYPYC_BACKEND_ID)
    @BackendSupportLevel(isSupported = true, isTested = true)
    object MypyC : PyFactory(PythonVersion.MypyC)

    sealed class PyFactory(private val pythonVersion: PythonVersion) : Factory<PyBackend> {
        final override val backendId: BackendId = BackendId(pythonVersion.id)

        override val backendMeta = BackendMeta(
            backendId = backendId,
            languageLabel = LanguageLabel(pythonVersion.id),
            fileExtensionMap = mapOf(
                FileType.Module to fileExtension,
                FileType.Script to fileExtension,
            ),
            // Also seen: application/x-bytecode.python application/x-python-bytecode
            mimeTypeMap = mapOf(
                FileType.Module to mimeType,
                FileType.Script to MimeType("application", "x-python"),
            ),
        )
        override val specifics: RunnerSpecifics
            get() = when (pythonVersion) {
                PythonVersion.Python311 -> PythonSpecifics
                PythonVersion.MypyC -> MypySpecifics
            }

        final override val coreLibraryResources: List<ResourceDescriptor> =
            declareResources(
                base = dirPath("lang", "temper", "be", "py", "temper-core"),
                filePath("README-temper-core.md"),
                filePath("temper_core", "py.typed"),
                filePath("temper_core", "__init__.py"),
                filePath("temper_core", "regex.py"),
                filePath("temper_core", "testing.py"),
            ) + when (pythonVersion) {
                PythonVersion.Python311 ->
                    declareResources(
                        base = dirPath("lang", "temper", "be", "py", "temper-core"),
                        filePath("pyproject.toml"),
                    )
                PythonVersion.MypyC ->
                    declareResources(
                        base = dirPath("lang", "temper", "be", "py", "temper-core", "mypyc"),
                        filePath("pyproject.toml"),
                    )
            }

        override val processCoreLibraryResourcesNeeded get() = false

        override fun make(setup: BackendSetup<PyBackend>): PyBackend = PyBackend(pythonVersion, setup)
    }

    companion object {
        /** Config files may export a name with this text to specify the pypi library name */
        val pyNameConfigKey = Symbol("pyName")

        const val fileExtension = ".py"

        // None of the python MIME types are registered with IANA.
        val mimeType = MimeType("text", "python")

        const val exportName: String = "export"

        // Also meets pytest defaults.
        // https://docs.pytest.org/en/7.1.x/example/pythoncollection.html
        val testsDir = FilePathSegment("tests")

        const val TEST_FILE_PREFIX = "test_"

        /**
         * This obnoxious prefix was to help keep things distinct for pytest, but it doesn't hurt unittest, so meh.
         * 3 underscores is more distinct than 1, and we suffix with 2, such that something
         * named `test` might become `test__4` or whatever, so be extra unusual here.
         * Note that this still doesn't ensure prefix uniqueness for pytest, so I guess another benefit to unittest.
         */
        const val TEST_FUNCTION_PREFIX = "test___"
    }
}

/**
 * <!-- snippet: backend/py/id -->
 * Backend ID: `py`
 */
internal const val PY_BACKEND_ID = "py"

/**
 * <!-- snippet: backend/mypyc/id -->
 * Backend ID: `mypyc`
 */
internal const val MYPYC_BACKEND_ID = "mypyc"

enum class PythonVersion(val id: String) {
    // If you change the default supported version of Python, also check choosePython() in be-py/build.gradle.
    Python311(PY_BACKEND_ID),
    MypyC(MYPYC_BACKEND_ID),
    ;

    val backendId = BackendId(id)
}

class Imports {
    private val importStatements = mutableMapOf<PyDottedIdentifier, MutableSet<ImportAlias>>()

    fun add(module: PyDottedIdentifier, name: OutName, asName: OutName) {
        importStatements.getOrPut(module) { mutableSetOf() }.add(ImportAlias(name = name, asName = asName))
    }

    fun add(sc: PySeparateCode, asName: OutName) {
        add(sc.module, name = OutName(sc.baseName.nameText, sc.baseName), asName = asName)
    }

    fun statements(pos: Position = unknownPos): List<Py.ImportStmt> {
        return importStatements.map { (mod, aliases) ->
            Py.ImportFrom(
                pos,
                module = Py.ImportDotted(pos, mod),
                names = aliases.map { (name, asName) ->
                    Py.ImportAlias(
                        pos,
                        name = Py.ImportDotted(pos, dotted(name)),
                        asname = if (name != asName) asName.asPyId(pos) else null,
                    )
                },
            )
        }
    }
}

data class ImportAlias(val name: OutName, val asName: OutName)

private fun LibraryConfiguration.pyLibraryName() = backendLibraryName(PyBackend.pyNameConfigKey)

private data class Dependency(val name: String, val version: String)

private fun buildTemperCoreDependency(): Dependency {
    // One way or another, these resources have to have correct info in them.
    // The name here has to match DashedIdentifier.temperCoreLibraryIdentifier.
    val text = loadResource(PyBackend.Python3, "lang/temper/be/py/temper-core/pyproject.toml")
    // TODO Actual toml parsing might be nice sometime, but we can control the format here well enough for now.
    val version = Regex("""\nversion = "([^"]+)"\n""").find(text)!!.groupValues[1]
    return Dependency(DashedIdentifier.temperCoreLibraryIdentifier.text, version)
}

private fun Dependency.formatDepPep508(): String {
    // TODO Other variations? What constraints do we want by default on api stability?
    val text = when {
        version == "*" -> name
        version.startsWith('~') -> "$name ~= ${version.substring(1)}"
        else -> "$name == $version"
    }
    return jsonEscaper.escape(text)
}

private fun formatDepsPep621(dependencies: List<Dependency>): String {
    // Include pipes for trimMargin.
    val listText = dependencies.joinToString(",\n|    ") { it.formatDepPep508() }
    return "\n|    $listText\n|"
}

fun pyDirToLibraryName(libraryDirectoryName: FilePathSegment): PyIdentifierName =
    PyIdentifierName(
        // convert directory name like foo-bar to Py ident like foo_bar
        IdentStyle.Dash.convertTo(IdentStyle.Snake, libraryDirectoryName.fullName),
    )

private fun String.scrubRfc822Name(): String {
    // See https://packaging.python.org/en/latest/specifications/pyproject-toml/#authors-maintainers
    // > The name value MUST be a valid email name (i.e. whatever can be put as a name, before an email, in RFC 822) and
    // > not contain commas.
    // For now, just focus on the commas and be sloppy about it.
    // TODO Improve this processing.
    return replace(",", "")
}
