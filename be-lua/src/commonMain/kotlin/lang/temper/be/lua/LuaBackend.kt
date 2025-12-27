package lang.temper.be.lua

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.be.tmpl.DependencyGrouping
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
import lang.temper.common.Console
import lang.temper.common.MimeType
import lang.temper.common.currents.SignalRFuture
import lang.temper.common.ignore
import lang.temper.common.isNotEmpty
import lang.temper.frontend.Module
import lang.temper.fs.ResourceDescriptor
import lang.temper.fs.declareResources
import lang.temper.fs.loadResource
import lang.temper.lexer.withTemperAwareExtension
import lang.temper.library.LibraryConfiguration
import lang.temper.library.authors
import lang.temper.library.description
import lang.temper.library.homepage
import lang.temper.library.license
import lang.temper.library.version
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.last
import lang.temper.log.plus
import lang.temper.log.resolveDir
import lang.temper.log.resolveFile
import lang.temper.name.BackendId
import lang.temper.name.BackendMeta
import lang.temper.name.DashedIdentifier
import lang.temper.name.FileType
import lang.temper.name.LanguageLabel
import lang.temper.name.ModuleName
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.name
import kotlin.io.path.readBytes

/**
 * <!-- snippet: backend/lua -->
 * # Lua Backend
 *
 * ⎀ backend/lua/id
 *
 * Supported version: Lua 5.1
 */
class LuaBackend private constructor(
    val lang: LuaLang,
    setup: BackendSetup<LuaBackend>,
) : Backend<LuaBackend>(backendId = lang.id, setup) {
    private val luaNames = LuaNames()
    private val outRoot = dirPath(libraryConfigurations.currentLibraryConfiguration.libraryName.text)

    override fun tentativeTmpL(): TmpL.ModuleSet = TmpLTranslator.translateModules(
        logSink,
        readyModules,
        supportNetwork,
        tentativeOutputPathFor = { outRoot },
        libraryConfigurations = libraryConfigurations,
        dependencyResolver = dependencyResolver,
    )

    override fun translate(finished: TmpL.ModuleSet): List<OutputFileSpecification> {
        val luaLibraryName = libraryConfigurations.currentLibraryConfiguration.libraryName.text

        val translations = finished.modules.flatMap { mod ->
            val translator = LuaTranslator(
                luaNames,
                luaLibraryName = luaLibraryName,
                dependenciesBuilder = dependenciesBuilder,
            )
            translator.translateTopLevel(mod)
        }

        val initPath = filePath(INIT_NAME)
        val metadataFiles = if (translations.any { it.path == initPath }) {
            // We already have a top module, so nothing more to add.
            listOf()
        } else {
            // We don't already have a top module, so compose one that inits but doesn't export all the others.
            val mainFilePath = allocateTextFile(initPath, lang.ext)
            // Complicated logic in this case ought to give us just what we started with.
            check(mainFilePath == initPath)
            listOf(
                MetadataFileSpecification(
                    mainFilePath,
                    MimeType.luaSource,
                    buildString {
                        translations.forEach translation@{ translation ->
                            translation.path.segments.first().fullName == LUA_TESTS_DIR && return@translation
                            // Not a test module, so require it.
                            append("require(")
                            append(
                                stringTokenText(
                                    // Stripping off .lua allows a `LUA_PATH="?.lua"` to load the library.
                                    "$luaLibraryName/${translation.path.withExtension(null)}",
                                ),
                            )
                            append(")\n")
                        }
                    },
                ),
            )
        } + run {
            val dependencies = DependencyGrouping.fromModuleImports(finished.modules)
            listOf(makeRockspec(dependencies, libraryConfigurations.byLibraryName))
        }

        dependenciesBuilder.addMetadata(
            libraryConfigurations.currentLibraryConfiguration.libraryName,
            LuaMetadataKey.MainFilePath,
            FilePath(listOf(FilePathSegment(luaLibraryName)), isDir = true) + initPath,
        )
        return translations + metadataFiles
    }

    private fun makeRockspec(
        dependencies: DependencyGrouping,
        libraries: Map<DashedIdentifier, LibraryConfiguration>,
    ): MetadataFileSpecification {
        val config = libraryConfigurations.currentLibraryConfiguration
        val buffer = StringWriter()
        val writer = PrintWriter(buffer)
        writer.println("package = ${stringTokenText(config.libraryName.text)}")
        // I think you're supposed to increment this if you change the rockspec without changing the library itself.
        val rockspecRevision = 1
        val version = "${config.version() ?: "dev"}-${rockspecRevision}"
        writer.println("version = ${stringTokenText(version)}")
        writer.println("rockspec_format = '3.0'")
        // Description.
        run {
            writer.println("description = {")
            config.description()?.let { writer.println("    summary = ${stringTokenText(it)},") }
            // Abuse `detailed` for authors. TODO Any better ideas?
            config.authors()?.let { writer.println("    detailed = ${stringTokenText("Authors: $it")},") }
            config.homepage()?.let { writer.println("    homepage = ${stringTokenText(it)},") }
            config.license()?.let { writer.println("    license = ${stringTokenText(it)},") }
            writer.println("}")
        }
        // Build.
        writer.println(
            """
                |build = {
                |    type = 'builtin',
                |    copy_directories = {'.'},
                |    modules = {},
                |}
            """.trimMargin(),
        )
        // Dependencies.
        run {
            fun formatDependencyEntry(dependency: DashedIdentifier): String {
                // The "==" is optional for exact version, but use it anyway for clarity.
                // And blank versions for unspecified work properly in manual testing so far.
                val depVersion = libraries[dependency]?.version()?.let { " == $it" } ?: ""
                return "    ${stringTokenText("$dependency$depVersion")},"
            }
            run {
                writer.println("dependencies = {")
                writer.println("    'lua >= 5.1',")
                writer.println("    'temper-core == $temperCoreVersion',")
                for (dependency in dependencies.productionNames) {
                    writer.println(formatDependencyEntry(dependency))
                }
                writer.println("}")
            }
            if (dependencies.testNames.isNotEmpty()) {
                writer.println("test_dependencies = {")
                for (dependency in dependencies.testNames) {
                    writer.println(formatDependencyEntry(dependency))
                }
                writer.println("}")
            }
        }
        // Source.
        writer.println(
            """
                |source = {
                |    url = ${stringTokenText("file://${fullZipPath()}")},
                |}
            """.trimMargin(),
        )
        // Done.
        return MetadataFileSpecification(
            path = filePath("${config.libraryName}-$version.rockspec"),
            mimeType = MimeType.luaSource, // because rockspecs are also lua
            content = "$buffer",
        )
    }

    private fun fullZipPath(): String {
        val zipName = "${libraryConfigurations.currentLibraryConfiguration.libraryName.text}.zip"
        // If we don't have a real path, we'll end up with a relative path, which luarocks can't pack, but eh.
        return buildFileCreator.envPath(filePath(zipName))?.ensureSlash() ?: zipName
    }

    override fun postWrite(
        outputFiles: List<OutputFileSpecification>,
        keepFiles: List<MetadataFileSpecification>,
    ): SignalRFuture {
        // Zip when raw access is available.
        if (buildFileCreator.envPath(FilePath.emptyPath) != null) {
            // Can't use outputFiles: doesn't represent the actual output files.
            // TODO Do we care to expose a way to make this async? Would it gain anything?
            zipLibrary(fullZipPath())
        }
        return super.postWrite(outputFiles, keepFiles)
    }

    override val supportNetwork: SupportNetwork = LuaSupportNetwork

    @PluginBackendId("lua")
    @BackendSupportLevel(isSupported = true, isDefaultSupported = true, isTested = true)
    data object Lua51 : LuaFactory(LuaLang.Lua51)

    sealed class LuaFactory(val lang: LuaLang) : Factory<LuaBackend> {
        override val backendId = lang.id
        override val backendMeta: BackendMeta
            get() = BackendMeta(
                languageLabel = lang.languageLabel,
                backendId = backendId,
                fileExtensionMap = mapOf(
                    FileType.Module to lang.ext,
                    FileType.Script to lang.ext,
                ),
                mimeTypeMap = mapOf(
                    FileType.Module to MimeType.luaSource,
                    FileType.Script to MimeType.luaSource,
                ),
            )

        override val specifics: RunnerSpecifics get() = when (lang) {
            LuaLang.Lua51 -> Lua51Specifics
        }

        override val coreLibraryResources: List<ResourceDescriptor>
            get() {
                return declareResources(
                    dirPath("lang", "temper", "be", "lua", "temper-core"),
                    filePath("init.lua"),
                    filePath("intnew.lua"),
                    filePath("intold.lua"),
                    filePath("powersort", "README.md"),
                    filePath("powersort", "init.lua"),
                    filePath("powersort", "init-internal.lua"),
                    filePath("regex", "runtime.lua"),
                    filePath(CORE_ROCKSPEC_NAME),
                )
            }

        override fun processCoreLibraryResources(cliEnv: CliEnv, console: Console) {
            ignore(console)
            val luaDir = dirPath(lang.id.uniqueId)
            val coreDir = luaDir.resolveDir("temper-core")
            val devSpecPath = coreDir.resolveFile(CORE_ROCKSPEC_NAME)
            val specText = cliEnv.readFile(devSpecPath)
            // Rename the spec file based on the internal version number, because luarocks requires matching.
            val version = Regex("^version = \"([^\"]+)\"", RegexOption.MULTILINE).find(specText)!!.groups[1]!!.value
            val versionedSpecPath = coreDir.resolveFile(CORE_ROCKSPEC_NAME.replace("dev-1", version))
            // And change the source "file:" url to point to the archive we'll make, because luarocks can't do relative.
            // Note that this file url will be in the packed rock. TODO Is a temp dir less likely to be sensitive?
            // Ideally this is some git repo, but that's not handy.
            val archiveName = "temper-core.zip"
            val archivePath = coreDir.resolveFile(archiveName)
            val absoluteArchivePath = cliEnv.envPath(archivePath)
            // TODO Use stringTokenText, except different quote handling?
            val escapedPath = absoluteArchivePath.ensureSlash().replace("\"", "\\\"")
            val changedSpecText = specText.replace(archiveName, escapedPath)
            cliEnv.write(changedSpecText, versionedSpecPath)
            cliEnv.remove(devSpecPath)
            // And actually create the zip file.
            zipLibrary(absoluteArchivePath)
        }

        override val processCoreLibraryResourcesNeeded get() = true

        override fun make(setup: BackendSetup<LuaBackend>): Backend<LuaBackend> = LuaBackend(lang, setup)
    }
}

enum class LuaLang(val id: BackendId, val languageLabel: LanguageLabel, val ext: String) {
    /**
     * <!-- snippet: backend/lua/id -->
     * BackendID: `lua`
     */
    Lua51(BackendId(uniqueId = "lua"), LanguageLabel("lua"), LUA_EXT),
}

internal fun pathForModule(moduleName: ModuleName, nameSuffix: String = ""): FilePath {
    val ext = LuaLang.Lua51.ext
    val fileOrDir = moduleName.relativePath()
    val mainPath = when {
        fileOrDir.isFile -> fileOrDir.withTemperAwareExtension(ext)
        fileOrDir.segments.isEmpty() -> filePath(INIT_NAME)
        // Reinterpret as a file if not at the top level, so we can avoid nesting where possible.
        else -> fileOrDir.dirName().resolveFile(fileOrDir.last().fullName).withTemperAwareExtension(ext)
    }
    return when {
        nameSuffix.isEmpty() -> mainPath
        else -> mainPath.dirName().resolveFile("${mainPath.last().baseName}$nameSuffix$ext")
    }
}

internal const val CORE_ROCKSPEC_NAME = "temper-core-dev-1.rockspec"
internal const val LUA_EXT = ".lua"
internal const val INIT_NAME = "init$LUA_EXT"
internal const val LUA_TESTS_DIR = "tests"

/** Typically works as either "." or "/". */
internal const val LUA_MODULE_SEP = "/"

internal fun CliEnv.copyLuaTemperCore(factory: Backend.Factory<LuaBackend>, prefix: List<String>? = null) {
    val dir = dirPath((prefix ?: listOf(factory.backendId.uniqueId)) + listOf("temper-core"))
    copyResources(factory.coreLibraryResources, dir)
}

private fun String.ensureSlash() = replace('\\', '/')

private fun loadTemperCoreRockspecText(): String {
    return loadResource(LuaBackend.Lua51, "lang/temper/be/lua/temper-core/temper-core-dev-1.rockspec")
}

private fun loadTemperCoreVersion(): String {
    val text = loadTemperCoreRockspecText()
    return Regex("""\nversion = "([^"]+)-\d+"\n""").find(text)!!.groupValues[1]
}

private val temperCoreVersion = loadTemperCoreVersion()

/**
 * TODO Move this to somewhere generally available.
 * Recursively zip up a directory.
 * @param prefix preprended to all entry paths, and end the prefix
 *     with an explicit "/" if you want to treat it as a dir
 */
private fun zipDir(dir: Path, zip: Path, prefix: String = "", include: (Path) -> Boolean = { true }) {
    ZipOutputStream(FileOutputStream(zip.toFile())).use { zipOut ->
        Files.walkFileTree(
            dir,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (!include(file)) {
                        return FileVisitResult.CONTINUE
                    }
                    val relPath = dir.relativize(file).toString().ensureSlash()
                    val path = "$prefix$relPath"
                    zipOut.putNextEntry(ZipEntry(path))
                    zipOut.write(file.readBytes())
                    zipOut.closeEntry()
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }
}

/** Zip up the library to meet luarocks expectations. */
private fun zipLibrary(absoluteArchivePath: String) {
    val zip = Path.of(absoluteArchivePath)
    val dir = zip.parent
    zipDir(dir = dir, zip = zip, prefix = "${dir.name}/") { path ->
        val name = path.name
        !(name.endsWith(".rockspec") || name.endsWith(".zip"))
    }
}
