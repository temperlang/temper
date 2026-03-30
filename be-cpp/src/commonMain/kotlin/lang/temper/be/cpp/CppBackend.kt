package lang.temper.be.cpp

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
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

    override fun translate(finished: TmpL.ModuleSet): List<OutputFileSpecification> {
        val cppLibraryName = libraryConfigurations.currentLibraryConfiguration.libraryName.text

        // Build a mapping from library source roots to output directory names
        // so that include paths and namespaces use the library name (e.g. "tempercc")
        // instead of raw source paths (e.g. "-work/src").
        val libraryRootToOutputDir = libraryConfigurations.byLibraryRoot.entries.associate { (root, config) ->
            root to config.libraryName.text
        }

        val allTestInfos = mutableListOf<Pair<String, String>>()
        val allInitFuncs = mutableListOf<String>()
        val allInitIncludes = mutableSetOf<String>()
        val translations = finished.modules.flatMap { mod ->
            val translator = CppTranslator(
                cppNames,
                cppLibraryName = cppLibraryName,
                dependenciesBuilder = dependenciesBuilder,
                libraryRootToOutputDir = libraryRootToOutputDir,
            )
            val result = translator.translateModule(mod)
            allTestInfos.addAll(translator.testInfos)
            translator.moduleInitFuncName?.let { name ->
                val libNs = cppNames.library(cppLibraryName).text.let { base ->
                    if (base == "std" || base == "chrono" || base == "filesystem") "temper_$base" else base
                }
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
        val testNs = cppNames.library("std").text.let { base ->
            val safe = if (base == "std" || base == "chrono" || base == "filesystem") "temper_$base" else base
            "temper::$safe"
        }

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
                    appendLine("    try { $funcName(t); } catch (...) {}")
                    appendLine("    auto mc = t->messagesCombined();")
                    appendLine(
                        "    results.push_back({\"$escapedName\"," +
                            " (bool)t->get_passing()," +
                            " temper::core::is_null(mc)" +
                            " ? \"\" : (std::string)mc});",
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
                        " << r.name << \"\\\" classname=\\\"\"" +
                        " << r.name << \"\\\" time=\\\"0\\\">\\n\";",
                )
                appendLine("    if (!r.passed) {")
                appendLine(
                    "      xml << \"      <failure message=\\\"\"" +
                        " << r.messages" +
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
