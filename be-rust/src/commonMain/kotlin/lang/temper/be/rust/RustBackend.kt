package lang.temper.be.rust

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.storeDescriptorsForDeclarations
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
import lang.temper.common.MimeType
import lang.temper.common.buildSetMultimap
import lang.temper.common.jsonEscaper
import lang.temper.common.putMultiSet
import lang.temper.common.subListToEnd
import lang.temper.frontend.Module
import lang.temper.fs.ResourceDescriptor
import lang.temper.fs.declareResources
import lang.temper.interp.importExport.STANDARD_LIBRARY_NAME
import lang.temper.library.LibraryConfiguration
import lang.temper.library.authors
import lang.temper.library.description
import lang.temper.library.homepage
import lang.temper.library.license
import lang.temper.library.repository
import lang.temper.library.versionOrDefault
import lang.temper.log.FilePath
import lang.temper.log.Position
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.last
import lang.temper.log.resolveDir
import lang.temper.name.BackendId
import lang.temper.name.BackendMeta
import lang.temper.name.DashedIdentifier
import lang.temper.name.FileType
import lang.temper.name.LanguageLabel

/**
 * <!-- snippet: backend/rust -->
 * # Rust Backend
 *
 * ⎀ backend/rust/id
 *
 * Translates Temper to Rust source and later to cargo crates.
 *
 * Targets the [Rust Programming Language].
 *
 * [Rust Programming Language]: https://doc.rust-lang.org/book/
 */
class RustBackend(setup: BackendSetup<RustBackend>) : Backend<RustBackend>(Factory.backendId, setup) {
    override fun tentativeTmpL(): TmpL.ModuleSet =
        TmpLTranslator.translateModules(
            logSink,
            readyModules,
            RustSupportNetwork,
            libraryConfigurations,
            dependencyResolver,
            ::tentativeOutputPathFor,
        ).also {
            storeDescriptorsForDeclarations(it, Factory)
        }

    override fun translate(finished: TmpL.ModuleSet): List<OutputFileSpecification> {
        val libraryConfiguration = libraryConfigurations.currentLibraryConfiguration
        val modules = finished.modules
        val names = makeRustNames(this)
        return buildList {
            // Figure out which modules contain subnamespaced modules, so we can declare them.
            val allModPaths = buildSet {
                for (module in modules) {
                    add(module.codeLocation.codeLocation.sourceFile)
                }
            }
            val allModKids = buildSetMultimap {
                for (module in modules) {
                    var path = module.codeLocation.codeLocation.sourceFile
                    // Add all parents, in case we have missing levels, but no need to add parent of empty path.
                    // TODO Some efficient way to stop walking upward when it's redundant? Currently semi n log n?
                    while (path.segments.size != libraryConfiguration.libraryRoot.segments.size) {
                        val parent = path.dirName()
                        putMultiSet(parent, path)
                        path = parent
                    }
                }
            }
            // Translate modules.
            val deps = mutableSetOf<Dep>()
            val featuresByDep = mutableMapOf<String, MutableSet<String>>()
            val modNames = mutableListOf<String>()
            for (module in modules) {
                // Actually translate.
                val modKids = allModKids[module.codeLocation.codeLocation.sourceFile] ?: listOf()
                val translator = RustTranslator(dependenciesBuilder, module, names, modKids)
                val mod = translator.translateModule()
                add(mod)
                // We slap mod.rs on the end of each, so exclude that.
                val segments = mod.path.dirName().segments
                val pathed = segments.subListToEnd(1).joinToString("::") { it.fullName.escapeIfNeeded() }
                val modName = when (pathed) {
                    "" -> "mod"
                    else -> pathed
                }
                modNames.add(modName)
                // Check dependencies.
                imports@ for (imp in module.imports) {
                    val path = (imp.path as? TmpL.CrossLibraryPath) ?: continue@imports
                    val depName = path.libraryName
                    val depConfig = libraryConfigurations.byLibraryName[depName]!!
                    if (depName.text == STANDARD_LIBRARY_NAME) {
                        // Track features for std to avoid unneeded dependencies.
                        // TODO Just split out std into separate libraries sometime?
                        path.to.relativePath().lastOrNull()?.fullName?.let { stdModule ->
                            // We chose feature names to match module names, but not all std modules are features.
                            if (stdModule in stdFeatures) {
                                featuresByDep.computeIfAbsent(depName.text) { mutableSetOf() }.add(stdModule)
                            }
                        }
                    }
                    Dep(
                        libraryName = depName.text,
                        naming = names.packageNamingsByRoot[depConfig.libraryRoot]!!,
                        path = "../$depName",
                        version = depConfig.versionOrDefault(),
                    ).let { deps.add(it) }
                }
            }
            // Merge lib. TODO Can we sort init in dependency order? Do we need to? Dep order maybe not hierarchical?
            linkLayers(finished.pos, allModPaths, allModKids)
            addLib(finished.pos, modules, allModKids, deps, libraryConfiguration, modNames)
            // Main program.
            MetadataFileSpecification(
                path = filePath("src", "main.rs"),
                mimeType = mimeType,
                content = """
                    |fn main() {
                    |    ${names.packageNaming.crateName}::init(None).unwrap().run_all_blocking();
                    |}
                """.trimMargin(),
            ).also { add(it) }
            // Cargo metadata.
            val isStd = libraryConfiguration.libraryName.text == STANDARD_LIBRARY_NAME
            val cargoDeps = buildString {
                for (dep in deps) {
                    val depPath = jsonEscaper.escape(dep.path)
                    val depVersion = jsonEscaper.escape(dep.version)
                    val depFeatures = when (val features = featuresByDep[dep.libraryName]) {
                        null -> ""
                        else -> ", features = [${features.joinToString { jsonEscaper.escape(it) }}]"
                    }
                    append("${dep.naming.packageName} = { path = $depPath, version = $depVersion$depFeatures }\n")
                }
                if (isStd) {
                    // TODO Provide a way to define these in config for arbitrary connected code?
                    append("regex = { version = \"=1.12.2\", optional = true }\n")
                    append("time = { version = \"=0.3.41\", optional = true }\n")
                    append("ureq = { version = \"=3.1.2\", optional = true }\n")
                    // Below aren't dependencies section anymore, but eh.
                    append("\n")
                    append("[features]\n")
                    append("net = [\"ureq\"]\n")
                    // Implied: append("regex = [\"regex\"]\n")
                    append("temporal = [\"time\"]\n")
                }
            }
            val packageFields = buildMap {
                this["name"] = names.packageNaming.packageName
                this["version"] = libraryConfiguration.versionOrDefault()
                libraryConfiguration.license()?.let { this["license"] = it }
                libraryConfiguration.description()?.let { this["description"] = it }
                libraryConfiguration.homepage()?.let { this["homepage"] = it }
                libraryConfiguration.repository()?.let { this["repository"] = it }
            }.map { (key, value) ->
                // Top-level string escape all above values.
                key to jsonEscaper.escape(value)
            }.toMap() + buildMap {
                // *Not* top-level string values here.
                libraryConfiguration.authors()?.let { this["authors"] = "[${jsonEscaper.escape(it)}]" }
            }
            val temperCoreLibraryVersion = DashedIdentifier.temperCoreLibraryVersion
            MetadataFileSpecification(
                path = filePath("Cargo.toml"),
                mimeType = MimeType("application", "toml"),
                content = """
                    |[package]
                    |${packageFields.entries.joinToString("\n") { (key, value) -> "$key = $value" }}
                    |edition = "2021"
                    |rust-version = "${RustcCommand.minVersion}"
                    |
                    |[dependencies]
                    |temper-core = { path = "../temper-core", version = "=$temperCoreLibraryVersion" }
                    |$cargoDeps
                """.trimMargin(),
            ).also { add(it) }
            // Additional template items.
            if (!config.abbreviated) {
                // TODO How to address potential namespace conflict like Logging here vs user-defined Logging?
                addResources(libraryTemplateResources)
            }
            if (isStd) {
                addResources(stdLibraryResources)
            }
        }
    }

    private fun MutableList<OutputFileSpecification>.addResources(resources: List<ResourceDescriptor>) {
        val baseDir = dirPath(SRC_PROJECT_DIR)
        for (resource in resources) {
            add(
                MetadataFileSpecification(
                    path = baseDir.resolve(resource.rsrcPath),
                    mimeType = resource.mimeType(),
                    content = resource.load(),
                ),
            )
        }
    }

    override val supportNetwork = RustSupportNetwork

    private fun tentativeOutputPathFor(module: Module): FilePath {
        return allocateTextFile(module, FILE_EXTENSION, defaultName = "module")
    }

    companion object {
        const val FILE_EXTENSION = ".rs"
        val mimeType = MimeType("text", "rust")
        private val resourceBase = dirPath("lang", "temper", "be", "rust")
        private val coreResourceBase = resourceBase.resolveDir("temper-core")
        private val stdResourceBase = resourceBase.resolveDir("std")
        val stdSupportNeeders = setOf("net", "regex", "temporal")
        val stdFeatures = stdSupportNeeders // same set today but maybe not guaranteed
        private val templateResourceBase = resourceBase.resolveDir("library-template")

        private val libraryTemplateResources: List<ResourceDescriptor> =
            declareResources(
                base = templateResourceBase,
                filePath("support", "mod.rs"),
            )

        private val stdLibraryResources: List<ResourceDescriptor> =
            declareResources(
                base = stdResourceBase,
                rsrcs = stdSupportNeeders.map { name -> filePath(name, "support.rs") },
            )

        /**
         * <!-- snippet: backend/rust/id -->
         * BackendID: `rust`
         */
        internal const val BACKEND_ID = "rust"
    }

    @PluginBackendId(BACKEND_ID)
    @BackendSupportLevel(isSupported = true, isDefaultSupported = true, isTested = true)
    object Factory : Backend.Factory<RustBackend> {
        override val backendId = BackendId(BACKEND_ID)
        override val specifics = RustSpecifics

        override val backendMeta: BackendMeta
            get() = BackendMeta(
                backendId = backendId,
                languageLabel = LanguageLabel(backendId.uniqueId),
                fileExtensionMap = mapOf(
                    FileType.Module to FILE_EXTENSION,
                    FileType.Script to FILE_EXTENSION,
                ),
                mimeTypeMap = mapOf(
                    FileType.Module to mimeType,
                    FileType.Script to mimeType,
                ),
            )

        override val coreLibraryResources: List<ResourceDescriptor> =
            declareResources(
                base = coreResourceBase,
                filePath("Cargo.lock"),
                filePath("Cargo.toml"),
                filePath("src", "float64.rs"),
                filePath("src", "generator.rs"),
                filePath("src", "lib.rs"),
                filePath("src", "listed.rs"),
                filePath("src", "mapped.rs"),
                filePath("src", "promise.rs"),
                filePath("src", "string.rs"),
            )

        override fun make(setup: BackendSetup<RustBackend>) = RustBackend(setup)
    }
}

private data class Dep(val libraryName: String, val naming: PackageNaming, val path: String, val version: String)

internal const val SRC_PROJECT_DIR = "src"

private val mimeTypes = mapOf(
    RustBackend.FILE_EXTENSION to RustBackend.mimeType,
)

private fun FilePath.mimeType() = mimeTypes.getOrDefault(last().extension, MimeType.textPlain)

private fun ResourceDescriptor.mimeType() = rsrcPath.mimeType()

private fun MutableList<Backend.OutputFileSpecification>.addLib(
    pos: Position,
    modules: List<TmpL.Module>,
    allModKids: Map<FilePath, Set<FilePath>>,
    deps: Set<Dep>,
    libraryConfiguration: LibraryConfiguration,
    modNames: List<String>,
) {
    val hasTopLevel = modules.any { it.codeLocation.codeLocation.relativePath().segments.isEmpty() }
    Backend.TranslatedFileSpecification(
        path = filePath("src", "lib.rs"),
        content = Rust.SourceFile(
            pos,
            attrs = listOf(
                Rust.AttrInner(
                    pos,
                    Rust.Call(
                        pos,
                        "allow".toId(pos),
                        // We generate these warnings and more. Some we could try to clean up, but it's awkward.
                        // Ignoring these warnings means we have to pay manual attention to generated public names.
                        listOf("nonstandard_style", "unused_imports", "unused_mut", "unused_variables").map { name ->
                            name.toId(pos)
                        },
                    ),
                ),
            ),
            items = buildList {
                // Separate mods.
                declareSubmods(pos, allModKids[libraryConfiguration.libraryRoot] ?: setOf())
                // Top mod.
                val pub = Rust.VisibilityPub(pos)
                if (hasTopLevel) {
                    val modId = "mod".toId(pos)
                    add(Rust.Module(pos, id = modId).toItem())
                    add(Rust.Use(pos, modId.deepCopy().extendWith("*")).toItem(pub = pub))
                }
                // Support mod.
                val supportId = "support".toId(pos)
                add(Rust.Module(pos, id = supportId).toItem())
                val configType = listOf("temper_core", "Config").toPath(pos)
                val crateId = "crate".toKeyId(pos)
                val crateConfig = crateId.deepCopy().extendWith("config")
                val crateScope = Rust.VisibilityScope(pos, Rust.VisibilityScopeOption.Crate)
                val pubCrate = Rust.VisibilityPub(pos, scope = crateScope)
                add(Rust.Use(pos, supportId.deepCopy().extendWith("*")).toItem(pub = pubCrate))
                // Library init function.
                Rust.Function(
                    pos,
                    id = "init".toId(pos),
                    params = listOf(Rust.FunctionParam(pos, "config".toId(pos), configType.deepCopy().option())),
                    returnType = listOf("temper_core", "AsyncRunner").toPath(pos).wrapResult(),
                    block = Rust.Block(
                        pos,
                        statements = buildList {
                            // crate::CONFIG.get_or_init(|| config.unwrap_or_else(|| temper_core::Config::default()));
                            crateId.deepCopy().extendWith("CONFIG").methodCall(
                                key = "get_or_init",
                                args = Rust.Closure(
                                    pos,
                                    params = listOf(),
                                    value = "config".toId(pos).methodCall(
                                        key = "unwrap_or_else",
                                        args = Rust.Closure(
                                            pos,
                                            params = listOf(),
                                            value = configType.deepCopy().extendWith("default").call(),
                                        ).let { listOf(it) },
                                    ),
                                ).let { listOf(it) },
                            ).let { add(Rust.ExprStatement(pos, it)) }
                            // Init external dependencies.
                            for (dep in deps) {
                                // ${dep.naming.crateName}::init(Some(crate::config().clone()))?;
                                val config = crateConfig.deepCopy().call().wrapClone().wrapSome()
                                val init =
                                    listOf(dep.naming.crateName, "init").toPath(pos).call(listOf(config)).propagate()
                                add(Rust.ExprStatement(pos, init))
                            }
                            // Init internal modules.
                            for (modName in modNames) {
                                // $modName::init()?;
                                val init = listOf(modName, "init").toPath(pos).call().propagate()
                                add(Rust.ExprStatement(pos, init))
                            }
                        },
                        result = run {
                            // Ok(crate::config().runner().clone())
                            crateConfig.call().methodCall("runner").methodCall("clone").wrapOk()
                        },
                    ),
                ).toItem(pub = pub.deepCopy()).let { add(it) }
            },
        ),
        mimeType = RustBackend.mimeType,
    ).also { add(it) }
}

private fun MutableList<Backend.OutputFileSpecification>.linkLayers(
    pos: Position,
    /** The paths to actual modules. */
    allModPaths: Set<FilePath>,
    /** Parent-child relationships including when modules don't exist. */
    allModKids: Map<FilePath, Set<FilePath>>,
) {
    val rootSegmentCount = allModKids.keys.minOfOrNull { it.segments.size } ?: return
    for (parent in allModKids.keys) {
        if (parent !in allModPaths && parent.segments.size > rootSegmentCount) {
            val kids = allModKids.getValue(parent)
            Backend.TranslatedFileSpecification(
                path = makeSrcFilePath(parent.segments.subListToEnd(rootSegmentCount)),
                content = Rust.SourceFile(
                    pos,
                    attrs = listOf(),
                    items = buildList { declareSubmods(pos, kids) },
                ),
                mimeType = RustBackend.mimeType,
            ).also { add(it) }
        }
    }
}
