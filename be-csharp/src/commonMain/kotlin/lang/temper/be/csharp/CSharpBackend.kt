package lang.temper.be.csharp

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.MetadataKey
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.be.names.NameSelection
import lang.temper.be.storeDescriptorsForDeclarations
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
import lang.temper.be.tmpl.injectSuperCallMethods
import lang.temper.common.MimeType
import lang.temper.frontend.Module
import lang.temper.fs.KCharsets
import lang.temper.fs.ResourceDescriptor
import lang.temper.fs.declareResources
import lang.temper.fs.loader
import lang.temper.fs.utf8
import lang.temper.interp.importExport.STANDARD_LIBRARY_NAME
import lang.temper.library.LibraryConfiguration
import lang.temper.library.authors
import lang.temper.library.description
import lang.temper.library.license
import lang.temper.library.repository
import lang.temper.library.version
import lang.temper.log.FilePath
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.last
import lang.temper.name.BackendId
import lang.temper.name.BackendMeta
import lang.temper.name.FileType
import lang.temper.name.LanguageLabel
import lang.temper.name.ModuleName
import lang.temper.name.Symbol
import lang.temper.name.rootModuleName
import lang.temper.type.MethodKind

/**
 * <!-- snippet: backend/csharp -->
 * # C# Backend
 *
 * ⎀ backend/csharp/id
 *
 * Translates Temper to C# source.
 *
 * Targets [.NET][CsDotNet] 6.0, although [.NET Framework][CsDotNetFramework] 4.8
 * might be supported in the future.
 *
 * To get started with this backend, see [the tutorial](../tutorial/index.md#use-csharp).
 *
 * ## Translation notes
 *
 * [Temper function types][CsFn], such as `fn (Int): Int` are translated into either
 * [`Func` delegates][CsFunc] for most return types or [`Action` delegates][CsAction] for `Void`
 * return type. These have an upper limit of 16 parameters, which isn't
 * accounted for in the current translation. In the future, if demand arises,
 * Temper likely will generate new delegate types in generated libraries for
 * larger numbers of parameters.
 *
 * [Temper nullable types][CsOrNull], such as `A?` translate into either nullable
 * value types or nullable reference types in C#. Nullable reference types apply
 * only to newer versions of C#, so potential .NET Framework 4.8 support would
 * need to represent these as ordinary reference types.
 *
 * For clearer representation of intention, Temper collection types translate
 * into .NET generic interfaces, including "ReadOnly" variations as appropriate.
 * For example, Temper [`Listed<T>`][CsListed] and `List<T>` both translate to
 * [`IReadOnlyList<T>`][CsIReadOnlyList], whereas `ListBuilder<T>` translates to [`IList<T>`][CsIList].
 * Similar rules apply to Temper `Mapped` types and .NET [`Dictionary`][CsDictionary] types,
 * with the additional matter that maps created in Temper always maintain
 * insertion order. Use of these interfaces in .NET is complicated by the fact
 * that `Ilist` doesn't subtype `IReadOnlyList`, even though standard .NET
 * [`List`][CsNetList] subtypes both. Similar issues apply to `Dictionary` types. To improve
 * ergonomics, usage of Temper `Listed` generate overloads that accept any of
 * `List`, `IList`, or `IReadOnlyList`. Again, Temper generates similar
 * overloads for Temper `Mapped` and .NET `Dictionary` types.
 *
 * Each Temper library is translated into a .NET [project][CsNetProject]/[assembly][CsAssembly], and Temper
 * module is translated into a [.NET namespace][CsNamespace]. Files for each subnamespace are
 * produced in respective output subdirectories. Each type defined in Temper is
 * translated to C# in separate file. Top-level Temper modules contents are
 * produced in a "Global" C# static class for the namespace.
 *
 * The Temper [logging `console`][CsConsole] translates in C# to the
 * [`Microsoft.Extensions.Logging` framework][CsMsLogging], at least for modern .NET usage.
 * This framework typically [initializes loggers via dependency injection (DI)][CsMsLoggingDi],
 * which isn't supported for static classes and static initialization. Because
 * of this, and to simplify general libraries that aren't built on DI, Temper
 * instead generates static methods for initializing an `ILogger` instance for
 * the output library. In the absence of any such configuration, generated
 * libraries fall back to using [`System.Diagnostics.Trace`][CsTrace].
 *
 * ## Tooling notes
 *
 * A [csproj file][CsCsProj] is automatically created for each Temper library such that
 * each builds to a separate .NET assembly.
 *
 * The only .NET library naming configuration available today for
 * [`config.temper.md` is `csharpRootNamespace`][CsConfig]. If specified, this string is
 * configured as the root namespace for the output project as well as the name
 * of the .NET assembly and the presumed NuGet [package ID][CsPackageId]. More fine-grained
 * configuration might be provided in the future. Microsoft has some advice for
 * [namespace selection][CsNamespaceSelection].
 *
 * Temper [`test` blocks][CsTestBlocks] are translated to use the [MSTest framework][CsMsTest]. A test class
 * is generated for each Temper module, and each test block becomes a test
 * method. Temper automatically provides infrastructure for soft assertions
 * within tests.
 *
 * [CsAction]: https://learn.microsoft.com/en-us/dotnet/api/system.action-1
 * [CsAssembly]: https://learn.microsoft.com/en-us/dotnet/standard/assembly/
 * [CsConfig]: ../tutorial/04-modlib.md#library-configuration
 * [CsConsole]: builtins.md#console
 * [CsCsProj]: https://learn.microsoft.com/en-us/aspnet/web-forms/overview/deployment/web-deployment-in-the-enterprise/understanding-the-project-file
 * [CsDictionary]: https://learn.microsoft.com/en-us/dotnet/api/system.collections.generic.dictionary-2
 * [CsDotNet]: https://dotnet.microsoft.com/en-us/platform/support/policy/dotnet-core
 * [CsDotNetFramework]: https://dotnet.microsoft.com/en-us/download/dotnet-framework
 * [CsFn]: types.md#function-types
 * [CsFunc]: https://learn.microsoft.com/en-us/dotnet/api/system.func-2
 * [CsIList]: https://learn.microsoft.com/en-us/dotnet/api/system.collections.generic.ilist-1
 * [CsIReadOnlyList]: https://learn.microsoft.com/en-us/dotnet/api/system.collections.generic.ireadonlylist-1
 * [CsListed]: types.md#interface-listed
 * [CsMsLogging]: https://learn.microsoft.com/en-us/dotnet/api/microsoft.extensions.logging
 * [CsMsLoggingDi]: https://learn.microsoft.com/en-us/aspnet/core/fundamentals/logging/#create-logs
 * [CsMsTest]: https://learn.microsoft.com/en-us/dotnet/core/testing/unit-testing-with-mstest
 * [CsNamespace]: https://learn.microsoft.com/en-us/dotnet/csharp/fundamentals/types/namespaces
 * [CsNamespaceSelection]: https://learn.microsoft.com/en-us/dotnet/standard/design-guidelines/names-of-namespaces
 * [CsNetList]: https://learn.microsoft.com/en-us/dotnet/api/system.collections.generic.list-1
 * [CsNetProject]: https://learn.microsoft.com/en-us/dotnet/core/tutorials/library-with-visual-studio-code
 * [CsOrNull]: types.md#type-relationships
 * [CsPackageId]: https://learn.microsoft.com/en-us/nuget/nuget-org/id-prefix-reservation
 * [CsTestBlocks]: ../tutorial/04-modlib.md#unit-tests
 * [CsTrace]: https://learn.microsoft.com/en-us/dotnet/api/system.diagnostics.trace
 */
class CSharpBackend(setup: BackendSetup<CSharpBackend>) : Backend<CSharpBackend>(Factory.backendId, setup) {
    override fun tentativeTmpL(): TmpL.ModuleSet =
        TmpLTranslator.translateModules(
            logSink,
            readyModules,
            CSharpSupportNetwork,
            libraryConfigurations,
            dependencyResolver,
            tentativeOutputPathFor = ::tentativeOutputPathFor,
            withTentative = {
                injectSuperCallMethods(
                    it,
                    injectInto = { decl ->
                        // For default interface methods, you can't use them directly on classes unless you redefine them.
                        // But this only applies to classes, not sub-interfaces.
                        decl.kind != TmpL.TypeDeclarationKind.Interface
                    },
                    chooseSuperName = { methodKind, name ->
                        // Currently this will need to translate from camel to pascal again later, sadly.
                        when (methodKind) {
                            MethodKind.Normal -> "${name}Default"
                            MethodKind.Getter -> "get${name.camelToPascal()}Default"
                            MethodKind.Setter -> "set${name.camelToPascal()}Default"
                            MethodKind.Constructor -> error("unexpected")
                        }
                    },
                )
            },
        ).also {
            storeDescriptorsForDeclarations(it, Factory)
        }

    override fun translate(finished: TmpL.ModuleSet): List<OutputFileSpecification> {
        return buildList {
            val names = makeCSharpNames(backend = this@CSharpBackend, moduleSet = finished)
            this@CSharpBackend.names = names
            val libraryConfig = finished.libraryConfiguration
            val libraryName = libraryConfig.libraryName
            val isStd = libraryName.text == STANDARD_LIBRARY_NAME
            // Translate.
            val moduleNameToGlobalClassName = mutableMapOf<ModuleName, QualifiedName>()
            val dependencies = mutableSetOf<String>()
            for (module in finished.modules) {
                val translator = CSharpTranslator(
                    dependenciesBuilder = dependenciesBuilder,
                    module = module,
                    names = names,
                    libraryConfigurations = libraryConfigurations.withCurrentLibrary(libraryConfig),
                    genre = finished.genre,
                )
                addAll(translator.translateModule())
                moduleNameToGlobalClassName[module.codeLocation.codeLocation] =
                    translator.qualifiedGlobalClassName
                // Track dependencies.
                module.deps.mapTo(dependencies) { it.libraryName.text }
                for (imp in module.imports) {
                    when (val path = imp.path) {
                        is TmpL.CrossLibraryPath -> dependencies.add(path.libraryName.text)
                        else -> {}
                    }
                }
            }
            // Proj.
            val rootNamespaceByName = names.rootNamespaces.associate { it.first.libraryName.text to it.second }
            val proj = CsProj(
                authors = libraryConfig.authors(),
                description = libraryConfig.description(),
                internalsVisibleTo = listOf("${names.rootNamespace}$TEST_SUFFIX"),
                packageLicenseExpression = libraryConfig.license(),
                packageProjectUrl = libraryConfig.repository(),
                packageReferences = when {
                    isStd -> listOf(PackageReference.microsoftNetTestSdk, PackageReference.msTestTestFramework)
                    else -> listOf()
                },
                projectReferences = buildList {
                    // Use relative references because they'll be transformed to global ones when packed for nuget.
                    // TODO Use direct global references if we know something is published?
                    // TODO Any way to tell dotnet to look locally for something not primarily relative?
                    add("../../temper-core/TemperLang.Core.csproj")
                    for (dependency in dependencies) {
                        val dependencyRootNamespace = rootNamespaceByName[dependency]!!
                        add("../../$dependency/$SRC_PROJECT_DIR/${dependencyRootNamespace}.csproj")
                    }
                },
                rootNamespace = names.rootNamespace,
                version = libraryConfig.version(),
            ).toFileSpec(baseDir = SRC_PROJECT_DIR)
            if (config.makeMetaDataFile && !config.abbreviated) {
                add(proj)
                // Main program project for library.
                val rootGlobalsName = moduleNameToGlobalClassName[libraryConfig.libraryRoot.rootModuleName()] ?: run {
                    // No top module, so add merge-all for library.
                    addMergeAll(
                        globals = moduleNameToGlobalClassName.values.toSet(),
                        rootNamespace = names.rootNamespace,
                    )
                }
                addMainProgram(
                    globalName = rootGlobalsName,
                    libraryConfig = libraryConfig,
                    projPath = proj.path,
                    rootNamespace = names.rootNamespace,
                )
            }
            // Test proj if any tests.
            if (this.any { it.path.segments.first().fullName == TEST_PROJECT_DIR }) {
                val testProj = CsProj(
                    // Exe also builds a dll, though a bit larger. TODO What are all the differences?
                    packageReferences = listOf(
                        PackageReference.junitXmlTestLogger,
                        PackageReference.microsoftNetTestSdk,
                        PackageReference.msTestTestAdapter,
                        PackageReference.msTestTestFramework,
                    ),
                    projectReferences = listOf(
                        // TODO Any other references needed?
                        "../${proj.path}",
                    ),
                    rootNamespace = names.rootNamespace,
                    version = libraryConfig.version(),
                ).toFileSpec(baseDir = TEST_PROJECT_DIR, nameSuffix = TEST_SUFFIX)
                add(testProj)
            }
            // Additional template items.
            val baseDir = dirPath(SRC_PROJECT_DIR)
            if (!config.abbreviated) {
                for (resource in libraryTemplateResources) {
                    // TODO How to address potential namespace conflict like Logging here vs user-defined Logging?
                    val content = resource.load().replaceTemplateSpots(names.rootNamespace)
                    val mimeType = resource.mimeType()
                    add(
                        MetadataFileSpecification(
                            path = baseDir.resolve(resource.rsrcPath),
                            mimeType = mimeType,
                            content = content,
                        ),
                    )
                }
            }
            if (isStd) {
                for (resource in stdLibraryResources) {
                    add(
                        MetadataFileSpecification(
                            path = baseDir.resolve(resource.rsrcPath),
                            mimeType = resource.mimeType(),
                            content = resource.load(),
                        ),
                    )
                }
            }
            // Metadata.
            dependenciesBuilder.addMetadata(
                libraryName,
                CSharpMetadataKey,
                CSharpMetadata(
                    globals = moduleNameToGlobalClassName,
                    projName = proj.path.toString(),
                ),
            )
        }
    }

    private var names: CSharpNames? = null

    override fun selectNames(): List<NameSelection> {
        return names!!.nameSelection.map { NameSelection(it.key, it.value) }
    }

    override val supportNetwork: SupportNetwork
        get() = CSharpSupportNetwork

    private fun tentativeOutputPathFor(module: Module): FilePath =
        allocateTextFile(module, FILE_EXTENSION)

    private val libraryTemplateResources: List<ResourceDescriptor> =
        declareResources(
            base = templateResourceBase,
            // *Don't* include Program.cs here because it needs more serious template substitution.
            filePath("Logging.cs"),
        )

    private val stdLibraryResources: List<ResourceDescriptor> =
        declareResources(
            base = dirPath("lang", "temper", "be", "csharp", "std"),
            filePath("Regex", "IntRangeSet.cs"),
            filePath("Regex", "RegexSupport.cs"),
            filePath("Temporal", "TemporalSupport.cs"),
            filePath("Net", "NetSupport.cs"),
        )

    companion object {
        const val FILE_EXTENSION = ".cs"
        val mimeType = MimeType("text", "x-csharp")
        val templateResourceBase = dirPath("lang", "temper", "be", "csharp", "library-template")

        internal const val BACKEND_ID = "csharp"
    }

    @PluginBackendId(BACKEND_ID)
    @BackendSupportLevel(isSupported = true, isDefaultSupported = true, isTested = true)
    object Factory : Backend.Factory<CSharpBackend> {
        /**
         * <!-- snippet: backend/csharp/id -->
         * BackendID: `csharp`
         */
        override val backendId: BackendId
            get() = BackendId(BACKEND_ID)

        override val backendMeta: BackendMeta
            get() {
                return BackendMeta(
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
            }

        override val specifics: RunnerSpecifics
            get() = CSharpSpecifics

        override val coreLibraryResources: List<ResourceDescriptor> =
            declareResources(
                base = dirPath("lang", "temper", "be", "csharp", "temper-core"),
                filePath("Async.cs"),
                filePath("Core.cs"),
                filePath("Float64.cs"),
                filePath("Generator.cs"),
                filePath("Listed.cs"),
                filePath("Logging.cs"),
                filePath("Optional.cs"),
                filePath("OrderedDictionary.cs"),
                filePath("StringUtil.cs"),
                filePath("TemperLang.Core.csproj"),
            )

        override fun make(setup: BackendSetup<CSharpBackend>): Backend<CSharpBackend> = CSharpBackend(setup)
    }
}

object CSharpMetadataKey : MetadataKey<CSharpBackend, CSharpMetadata>() {
    override val backendId: BackendId
        get() = CSharpBackend.Factory.backendId
}

class CSharpMetadata(
    /** The components of the qualified name for the Global class for each module with a Global class. */
    val globals: Map<ModuleName, QualifiedName>,

    /** The name of the csproj file. */
    val projName: String,
)

const val DEFAULT_CSPROJ_BASENAME = "TemperBuilt"
const val STD_ROOT_NAMESPACE = "TemperLang.Std"

internal val csharpRootNamespaceKey = Symbol("csharpRootNamespace")

private val mimeTypes = mapOf(
    CSharpBackend.FILE_EXTENSION to CSharpBackend.mimeType,
)

private fun FilePath.mimeType() = mimeTypes.getOrDefault(last().extension, MimeType.textPlain)
private fun ResourceDescriptor.mimeType() = rsrcPath.mimeType()

private fun String.replaceTemplateSpots(rootNamespace: String) =
    replace("RootNamespaceSpot", rootNamespace).replace("SupportNamespaceSpot", SUPPORT_NAMESPACE)

// We invert src/tests vs standard layout because that fits our expectations better. For layout reference, see:
// https://github.com/dotnet/eShop/tree/0b07251070a94f21453e8139f6107f87c8329771/src
// https://github.com/dotnet/eShop/tree/0b07251070a94f21453e8139f6107f87c8329771/tests
internal const val PROGRAM_PROJECT_DIR = "program"
internal const val SRC_PROJECT_DIR = "src"
internal const val SUPPORT_NAMESPACE = "Support"
internal const val TEST_PROJECT_DIR = "tests"
internal const val TEST_SUFFIX = "Test"

data class PackageReference(
    val name: String,
    val version: String,
) {
    companion object {
        val junitXmlTestLogger = PackageReference(name = "JunitXml.TestLogger", version = "3.0.134")
        val microsoftNetTestSdk = PackageReference(name = "Microsoft.NET.Test.Sdk", version = "17.8.0")
        val msTestTestAdapter = PackageReference(name = "MSTest.TestAdapter", version = "3.1.1")
        val msTestTestFramework = PackageReference(name = "MSTest.TestFramework", version = "3.1.1")
    }
}

fun MutableList<Backend.OutputFileSpecification>.addMainProgram(
    globalName: QualifiedName,
    libraryConfig: LibraryConfiguration,
    projPath: FilePath,
    rootNamespace: String,
) {
    // Program file.
    val filePath = filePath("Program.cs")
    val replacedContent = replaceInitContent(filePath, rootNamespace) { line, lineRegex ->
        line.replace(lineRegex, "$1${globalName.joinToString(".")}$2")
    }
    val programFile = Backend.MetadataFileSpecification(
        path = dirPath(PROGRAM_PROJECT_DIR).resolve(filePath),
        mimeType = filePath.mimeType(),
        content = replacedContent,
    )
    add(programFile)
    // Proj file.
    val programProj = CsProj(
        outputType = "Exe",
        projectReferences = listOf("../${projPath}"),
        rootNamespace = rootNamespace,
        version = libraryConfig.version(),
    ).toFileSpec(baseDir = PROGRAM_PROJECT_DIR, nameSuffix = "Program")
    add(programProj)
}

fun MutableList<Backend.OutputFileSpecification>.addMergeAll(
    globals: Iterable<QualifiedName>,
    rootNamespace: String,
): QualifiedName {
    val filePath = filePath("Global.cs")
    val fullNamespace = rootNamespace.split(".")
    val name = makeGlobalName(fullNamespace)
    val rootNamespaceRegex = Regex("""^$rootNamespace\.""")
    val replacedContent = replaceInitContent(filePath, rootNamespace) { line, lineRegex ->
        // One line per module global.
        globals.joinToString("") { global ->
            // Replace each `GlobalNameSpot` placeholder with the module's qualifiedGlobalName value.
            // But, to avoid ambiguity, leave the prefix `R::` in place of spelled out root namespace.
            val qualifiedGlobalName = global.joinToString(".").replace(rootNamespaceRegex, "")
            line.replace(lineRegex, "$1$qualifiedGlobalName$2")
        }
    }.replace("GlobalNameSpot", name)
    val globalFile = Backend.MetadataFileSpecification(
        path = dirPath(SRC_PROJECT_DIR).resolve(filePath("$name${CSharpBackend.FILE_EXTENSION}")),
        mimeType = filePath.mimeType(),
        content = replacedContent,
    )
    add(globalFile)
    return fullNamespace + listOf(SUPPORT_NAMESPACE, name)
}

private fun replaceInitContent(
    filePath: FilePath,
    rootNamespace: String,
    replaceLine: (String, Regex) -> String,
): String {
    // Access template resource.
    val loader = CSharpBackend::class.loader()
    val resource = ResourceDescriptor(
        loader = loader,
        basePath = CSharpBackend.templateResourceBase,
        rsrcPath = filePath,
        charset = KCharsets.utf8,
    )
    val content = resource.load()
    // Build replacement content.
    val lineRegex = Regex(
        """^(\s+RuntimeHelpers[.]RunClassConstructor\(typeof\((?:\w+::)?)[^)]+(.*\n)""",
        RegexOption.MULTILINE,
    )
    val line = lineRegex.find(content)!!.groupValues.first()
    val replacedLine = replaceLine(line, lineRegex)
    return content.replace(lineRegex, replacedLine).replaceTemplateSpots(rootNamespace)
}

typealias QualifiedName = List<String>
