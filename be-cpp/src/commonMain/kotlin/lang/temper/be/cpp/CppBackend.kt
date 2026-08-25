package lang.temper.be.cpp

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
import lang.temper.be.tmpl.asReceiverMember
import lang.temper.be.tmpl.injectSuperCallMethods
import lang.temper.be.tmpl.mutatingMemberNames
import lang.temper.common.MimeType
import lang.temper.common.subListToEnd
import lang.temper.fs.ResourceDescriptor
import lang.temper.fs.declareResources
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.asFilePath
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.plus
import lang.temper.name.BackendId
import lang.temper.name.BackendMeta
import lang.temper.name.FileType
import lang.temper.name.LanguageLabel

/**
 * # C++ Backend
 *
 * Translates Temper to C++14 source (.cpp and .hpp file pairs).
 *
 * ## Translation strategy
 *
 * Each Temper module produces one .hpp (declarations) and one .cpp (definitions) file.
 * Temper classes and interfaces become C++ structs with `shared_ptr` wrapping via `Object<T>`.
 * Value types (Int, Int64, Float64, Boolean, String) are unwrapped to native C++ types.
 *
 * Inheritance uses `virtual public` for single-supertype classes to support diamond
 * hierarchies safely. Multi-inheritance uses plain `public` since the bases already
 * provide virtual paths to shared ancestors.
 *
 * ## Initialization
 *
 * All module-level variable initializations and init blocks are deferred to an explicit
 * `global_init_<module>()` function, called from `main()`, to avoid the Static
 * Initialization Order Fiasco (SIOF). Cross-module dependency init functions are
 * called first within each module's init function.
 *
 * ## Memory model
 *
 * Reference types use `shared_ptr<T>`. Value types use stack allocation.
 * `NullableParam<T>` wraps value types that may be null.
 * `borrow_this` creates a non-owning `shared_ptr` for passing `this` to
 * functions that expect `Object<T>`.
 */
class CppBackend private constructor(
    val lang: CppLang,
    setup: BackendSetup<CppBackend>,
) : Backend<CppBackend>(lang.id, setup) {
    private val cppNames = CppNames()
    private val outRoot = dirPath(libraryConfigurations.currentLibraryConfiguration.libraryName.text)

    override fun tentativeTmpL(): TmpL.ModuleSet = TmpLTranslator.translateModules(
        logSink,
        readyModules,
        supportNetwork,
        tentativeOutputPathFor = { outRoot },
        libraryConfigurations = libraryConfigurations,
        dependencyResolver = dependencyResolver,
        withTentative = { injectSuperCallMethods(it) },
    )

    /**
     * Member dotNames that the C++ backend cannot emit as `const` regardless of mutation:
     * `mayYield` generator methods, and methods with optional parameters (whose generated
     * forwarding overloads are left non-const). Seeded into the mutation analysis so a
     * const method calling one of these on `this` is itself made non-const.
     */
    private fun forcedNonConstMembers(modules: List<TmpL.Module>): Set<String> = buildSet {
        for (mod in modules) {
            for (top in mod.topLevels) {
                if (top !is TmpL.TypeDeclaration) continue
                for (member in top.members) {
                    val rm = member.asReceiverMember() ?: continue
                    val hasOptional = rm.parameters.parameters.drop(1).any { it.optional }
                    if (rm.mayYield || hasOptional) add(rm.dotName)
                }
            }
        }
    }

    override fun translate(finished: TmpL.ModuleSet): List<OutputFileSpecification> {
        val cppLibraryName = libraryConfigurations.currentLibraryConfiguration.libraryName.text

        // Build a mapping from library source roots to output directory names
        // so that include paths and namespaces use the library name (e.g. "tempercc")
        // instead of raw source paths (e.g. "-work/src").
        val libraryRootToOutputDir = libraryConfigurations.byLibraryRoot.entries.associate { (root, config) ->
            root to config.libraryName.text
        }

        // Determine, across the whole library, which property getters may mutate the
        // receiver, so non-mutating getters can be emitted `const` consistently across
        // every override of each property. See [CppTranslator.mutatingGetterProperties].
        // Cross-member mutation analysis: methods and getters whose receiver is never
        // mutated are emitted `const`. Members the C++ representation can't const-qualify
        // — generators (mayYield) and methods with optional params (whose forwarding
        // overloads stay non-const) — are seeded as "forced mutating" so the constraint
        // propagates to any const caller. See [lang.temper.be.tmpl.mutatingMemberNames].
        val forcedNonConst = forcedNonConstMembers(finished.modules)
        val mutatingMethodNames = mutatingMemberNames(finished.modules, forcedNonConst)

        val allTestInfos = mutableListOf<Pair<String, String>>()
        val allInitFuncs = mutableListOf<String>()
        val allInitIncludes = mutableSetOf<String>()
        val translations = finished.modules.flatMap { mod ->
            val translator = CppTranslator(
                cppNames,
                cppLibraryName = cppLibraryName,
                libraryRootToOutputDir = libraryRootToOutputDir,
                mutatingMethodNames = mutatingMethodNames,
            )
            val result = translator.translateModule(mod)
            allTestInfos.addAll(translator.testInfos)
            translator.moduleInitFuncName?.let { name ->
                val libNs = safeCppNamespace(cppNames.library(cppLibraryName).text)
                allInitFuncs.add("$libNs::$name")
                // Track include path for this module's header so main.cpp can see all init decls
                val loc = mod.codeLocation.codeLocation
                allInitIncludes.add(translator.cpp.includePathForModule(loc))
            }
            result
        }

        // Connected code.
        val connectedFiles = buildList {
            val rootSize = libraryConfigurations.currentLibraryConfiguration.libraryRoot.segments.size
            for (file in rawBackendFiles) {
                MetadataFileSpecification(
                    path = file.key.segments.subListToEnd(rootSize).asFilePath(),
                    mimeType = MimeType.cppSource,
                    content = file.value,
                ).also { add(it) }
            }
        }

        val initPath = filePath(INIT_NAME)

        dependenciesBuilder.addMetadata(
            libraryConfigurations.currentLibraryConfiguration.libraryName,
            CppMetadataKey.MainFilePath,
            FilePath(listOf(FilePathSegment(cppLibraryName)), isDir = true) + initPath,
        )

        // Compute the C++ namespace for the std library's Test type
        val testNs = safeCppNamespace(cppNames.library("std").text)

        val mainContent = generateMainCpp(
            initIncludes = allInitIncludes.sorted(),
            initFuncs = allInitFuncs,
            testInfos = allTestInfos,
            testNs = testNs,
        )

        return translations + connectedFiles + listOf(
            MetadataFileSpecification(
                path = filePath(MAIN_CPP_FILE),
                mimeType = MimeType.cppSource,
                content = mainContent,
            ),
        )
    }

    /**
     * Build the contents of `main.cpp`: include each module's header, call its init
     * function (each guarded so dependency order is handled), and — when the library
     * contains tests — hand the test harness one closure per test.
     *
     * [initIncludes] should already be sorted for deterministic output. [testInfos] pairs
     * each test's generated function name with its raw (display) name. [testNs] is the
     * C++ namespace of the std library's `Test` type.
     */
    private fun generateMainCpp(
        initIncludes: List<String>,
        initFuncs: List<String>,
        testInfos: List<Pair<String, String>>,
        testNs: String,
    ): String = buildString {
        for (inc in initIncludes) {
            appendLine("""#include "$inc"""")
        }
        if (testInfos.isNotEmpty()) {
            appendLine("""#include "std/testing.hpp"""")
            appendLine("""#include "temper-core/test_main.hpp"""")
        }
        appendLine("int main() {")
        for (initFunc in initFuncs) {
            appendLine("  $initFunc();")
        }
        if (testInfos.isNotEmpty()) {
            // Hand the harness one closure per test. Each closure runs the test and
            // reports its outcome; run_tests (in temper-core/test_main.hpp) owns the
            // exception handling, JUnit-XML serialization, and file writing.
            appendLine("  return temper::core::run_tests({")
            for ((funcName, rawName) in testInfos) {
                val escapedName = rawName
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                appendLine("    { \"$escapedName\", []() -> temper::core::TestOutcome {")
                appendLine("      auto t = $testNs::Test::make();")
                appendLine("      $funcName(t);")
                appendLine("      auto mc = t->messagesCombined();")
                appendLine(
                    "      std::string messages = temper::core::is_null(mc)" +
                        " ? std::string() : (std::string)mc;",
                )
                appendLine("      return { (bool)t->get_passing(), messages };")
                appendLine("    } },")
            }
            appendLine("  });")
        }
        append("}")
    }

    override val supportNetwork: SupportNetwork = CppSupportNetwork

    @PluginBackendId("cpp")
    @BackendSupportLevel(isSupported = true, isDefaultSupported = false, isTested = true)
    data object Cpp : CppFactory(CppLang.Cpp)

    sealed class CppFactory(val lang: CppLang) : Factory<CppBackend> {
        override val backendId = lang.id
        override val backendMeta: BackendMeta
            get() = BackendMeta(
                languageLabel = lang.languageLabel,
                backendId = backendId,
                fileExtensionMap = mapOf(
                    FileType.Module to lang.ext,
                    FileType.Script to lang.ext,
                    FileType.Header to HPP_EXT,
                ),
                mimeTypeMap = mapOf(
                    FileType.Module to MimeType.cppSource,
                    FileType.Script to MimeType.cppSource,
                    FileType.Header to MimeType.cppSource,
                ),
            )

        override val specifics: RunnerSpecifics get() = when (lang) {
            CppLang.Cpp -> CppSpecifics
        }

        override val coreLibraryResources: List<ResourceDescriptor>
            get() {
                return declareResources(
                    dirPath("lang", "temper", "be", "cpp", "core"),
                    filePath("temper_bubble.hpp"),
                    filePath("any_value.hpp"),
                    filePath("nullable_param.hpp"),
                    filePath("pair.hpp"),
                    filePath("base_types.hpp"),
                    filePath("casting.hpp"),
                    filePath("compare.hpp"),
                    filePath("boolean.hpp"),
                    filePath("int.hpp"),
                    filePath("int64.hpp"),
                    filePath("float64.hpp"),
                    filePath("console.hpp"),
                    filePath("string.hpp"),
                    filePath("string_builder.hpp"),
                    filePath("list.hpp"),
                    filePath("list_builder.hpp"),
                    filePath("mapped.hpp"),
                    filePath("map_builder.hpp"),
                    filePath("map.hpp"),
                    filePath("deque.hpp"),
                    filePath("dense_bit_vector.hpp"),
                    filePath("date.hpp"),
                    filePath("regex.hpp"),
                    filePath("generator.hpp"),
                    filePath("promise.hpp"),
                    filePath("core.hpp"),
                    // Test harness used only by the generated `main.cpp`; intentionally not
                    // pulled into core.hpp so its <fstream>/<sstream> stay out of every TU.
                    filePath("test_main.hpp"),
                )
            }

        override val processCoreLibraryResourcesNeeded get() = true

        override fun make(setup: BackendSetup<CppBackend>) = CppBackend(lang, setup)
    }
}

enum class CppLang(val id: BackendId, val languageLabel: LanguageLabel, val ext: String) {
    Cpp(BackendId(uniqueId = "cpp"), LanguageLabel("cpp"), CPP_EXT),
}

internal const val CPP_EXT = ".cpp"
internal const val HPP_EXT = ".hpp"
internal const val INIT_NAME = "init"

/** The per-library entry-point file holding `int main()`. */
internal const val MAIN_CPP_FILE = "main.cpp"

internal fun CliEnv.copyCppTemperCore(factory: Backend.Factory<CppBackend>, prefix: List<String>? = null) {
    val dir = dirPath((prefix ?: listOf(factory.backendId.uniqueId)) + listOf("temper-core"))
    copyResources(factory.coreLibraryResources, dir)
}
