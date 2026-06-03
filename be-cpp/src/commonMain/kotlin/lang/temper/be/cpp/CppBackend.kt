package lang.temper.be.cpp

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
import lang.temper.be.tmpl.mutatingMemberNames
import lang.temper.common.MimeType
import lang.temper.fs.ResourceDescriptor
import lang.temper.fs.declareResources
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
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
 * `temper_init_<module>()` function, called from `main()`, to avoid the Static
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
                    val (dotName, parameters, mayYield) = when (member) {
                        is TmpL.NormalMethod -> Triple(member.dotName.dotNameText, member.parameters, member.mayYield)
                        is TmpL.Getter -> Triple(member.dotName.dotNameText, member.parameters, false)
                        is TmpL.Setter -> Triple(member.dotName.dotNameText, member.parameters, false)
                        else -> continue
                    }
                    val hasOptional = parameters.parameters.drop(1).any { it.optional }
                    if (mayYield || hasOptional) add(dotName)
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
                dependenciesBuilder = dependenciesBuilder,
                libraryRootToOutputDir = libraryRootToOutputDir,
                mutatingMethodNames = mutatingMethodNames,
            )
            val result = translator.translateModule(mod)
            allTestInfos.addAll(translator.testInfos)
            translator.moduleInitFuncName?.let { name ->
                val libNs = safeCppNamespace(cppNames.library(cppLibraryName).text)
                allInitFuncs.add("temper::${libNs}::$name")
                // Track include path for this module's header so main.cpp can see all init decls
                val loc = mod.codeLocation.codeLocation
                allInitIncludes.add(translator.cpp.includePathForModule(loc))
            }
            result
        }

        val initPath = filePath(INIT_NAME)

        dependenciesBuilder.addMetadata(
            libraryConfigurations.currentLibraryConfiguration.libraryName,
            CppMetadataKey.MainFilePath,
            FilePath(listOf(FilePathSegment(cppLibraryName)), isDir = true) + initPath,
        )

        // Compute the C++ namespace for the std library's Test type
        val testNs = "temper::${safeCppNamespace(cppNames.library("std").text)}"

        val mainContent = if (allTestInfos.isNotEmpty()) {
            buildString {
                appendLine("#include <fstream>")
                appendLine("#include <sstream>")
                appendLine("#include <string>")
                appendLine("#include <vector>")
                for (inc in allInitIncludes.sorted()) {
                    appendLine("""#include "$inc"""")
                }
                appendLine("""#include "std/testing.hpp"""")
                appendLine("std::string xmlEscape(const std::string& s) {")
                appendLine("  std::string out;")
                appendLine("  for (char c : s) {")
                appendLine("    switch (c) {")
                appendLine("""      case '&': out += "&amp;"; break;""")
                appendLine("""      case '<': out += "&lt;"; break;""")
                appendLine("""      case '>': out += "&gt;"; break;""")
                appendLine("""      case '"': out += "&quot;"; break;""")
                appendLine("      default: out += c;")
                appendLine("    }")
                appendLine("  }")
                appendLine("  return out;")
                appendLine("}")
                appendLine("int main() {")
                // Call module init functions in order (each has a
                // static guard so dependency order is handled).
                for (initFunc in allInitFuncs) {
                    appendLine("  ${initFunc}();")
                }
                appendLine("  struct TestResult {")
                appendLine("    std::string name;")
                appendLine("    bool passed;")
                appendLine("    std::string messages;")
                appendLine("  };")
                appendLine("  std::vector<TestResult> results;")
                for ((funcName, rawName) in allTestInfos) {
                    val escapedName = rawName
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                    appendLine("  {")
                    appendLine("    auto t = $testNs::Test::make();")
                    // An uncaught exception means the generated code is broken: record it as a
                    // failure with its message rather than swallowing it (which would let a
                    // crashing test masquerade as whatever get_passing() happened to return).
                    appendLine("    bool threw = false;")
                    appendLine("    std::string thrownMessage;")
                    appendLine("    try { $funcName(t); }")
                    appendLine(
                        "    catch (const std::exception& e) { threw = true; thrownMessage = e.what(); }",
                    )
                    appendLine(
                        "    catch (...) { threw = true; thrownMessage = \"unknown C++ exception\"; }",
                    )
                    appendLine("    auto mc = t->messagesCombined();")
                    appendLine(
                        "    std::string combinedMessages = temper::core::is_null(mc)" +
                            " ? std::string() : (std::string)mc;",
                    )
                    appendLine("    if (threw) {")
                    appendLine("""      if (!combinedMessages.empty()) { combinedMessages += "\n"; }""")
                    appendLine("""      combinedMessages += "uncaught exception: " + thrownMessage;""")
                    appendLine("    }")
                    appendLine(
                        "    results.push_back({\"$escapedName\"," +
                            " threw ? false : (bool)t->get_passing()," +
                            " combinedMessages});",
                    )
                    appendLine("  }")
                }
                appendLine("  int failures = 0;")
                appendLine(
                    "  for (auto& r : results)" +
                        " if (!r.passed) failures++;",
                )
                appendLine("  std::ostringstream xml;")
                appendLine(
                    "  xml << \"<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?>\\n\"" +
                        " << \"<testsuites>\\n\"" +
                        " << \"  <testsuite name=\\\"suite\\\"" +
                        " tests=\\\"\" << results.size()" +
                        " << \"\\\" failures=\\\"\"" +
                        " << failures << \"\\\" time=\\\"0\\\">\\n\";",
                )
                appendLine("  for (auto& r : results) {")
                appendLine(
                    "    xml << \"    <testcase name=\\\"\"" +
                        " << xmlEscape(r.name) << \"\\\" classname=\\\"\"" +
                        " << xmlEscape(r.name) << \"\\\" time=\\\"0\\\">\\n\";",
                )
                appendLine("    if (!r.passed) {")
                appendLine(
                    "      xml << \"      <failure message=\\\"\"" +
                        " << xmlEscape(r.messages)" +
                        " << \"\\\"><![CDATA[\" << r.messages" +
                        " << \"]]></failure>\\n\";",
                )
                appendLine("    }")
                appendLine(
                    "    xml << \"    </testcase>\\n\";",
                )
                appendLine("  }")
                appendLine(
                    "  xml << \"  </testsuite>\\n\"" +
                        " << \"</testsuites>\\n\";",
                )
                appendLine("""  std::ofstream out("test-results.xml");""")
                appendLine("  if (out.is_open()) {")
                appendLine("    out << xml.str();")
                appendLine("    out.close();")
                appendLine("  }")
                appendLine("  return 0;")
                append("}")
            }
        } else {
            if (allInitFuncs.isNotEmpty()) {
                buildString {
                    for (inc in allInitIncludes.sorted()) {
                        appendLine("""#include "$inc"""")
                    }
                    appendLine("int main() {")
                    for (initFunc in allInitFuncs) {
                        appendLine("  ${initFunc}();")
                    }
                    append("}")
                }
            } else {
                "int main() {}"
            }
        }

        return translations + listOf(
            MetadataFileSpecification(
                path = filePath("main.cpp"),
                mimeType = MimeType.cppSource,
                content = mainContent,
            ),
        )
    }

    override val supportNetwork: SupportNetwork = CppSupportNetwork

    @PluginBackendId("cpp")
    @BackendSupportLevel(isSupported = true, isDefaultSupported = false, isTested = true)
    data object Cpp11 : CppFactory(CppLang.Cpp11)

    sealed class CppFactory(val lang: CppLang) : Factory<CppBackend> {
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
                    FileType.Module to MimeType.cppSource,
                    FileType.Script to MimeType.cppSource,
                ),
            )

        override val specifics: RunnerSpecifics get() = when (lang) {
            CppLang.Cpp11 -> Cpp11Specifics
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
                )
            }

        override val processCoreLibraryResourcesNeeded get() = true

        override fun make(setup: BackendSetup<CppBackend>) = CppBackend(lang, setup)
    }
}

enum class CppLang(val id: BackendId, val languageLabel: LanguageLabel, val ext: String) {
    Cpp11(BackendId(uniqueId = "cpp"), LanguageLabel("cpp"), CPP_EXT),
}

internal const val CPP_EXT = ".cpp"
internal const val HPP_EXT = ".hpp"
internal const val INIT_NAME = "init"

internal fun CliEnv.copyCppTemperCore(factory: Backend.Factory<CppBackend>, prefix: List<String>? = null) {
    val dir = dirPath((prefix ?: listOf(factory.backendId.uniqueId)) + listOf("temper-core"))
    copyResources(factory.coreLibraryResources, dir)
}
