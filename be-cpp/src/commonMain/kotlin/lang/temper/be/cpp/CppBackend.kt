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

        val translations = finished.modules.flatMap { mod ->
            val translator = CppTranslator(
                cppNames,
                cppLibraryName = cppLibraryName,
                dependenciesBuilder = dependenciesBuilder,
            )
            translator.translateModule(mod)
        }

        val initPath = filePath(INIT_NAME)

        dependenciesBuilder.addMetadata(
            libraryConfigurations.currentLibraryConfiguration.libraryName,
            CppMetadataKey.MainFilePath,
            FilePath(listOf(FilePathSegment(cppLibraryName)), isDir = true) + initPath,
        )

        return translations
    }

    override val supportNetwork: SupportNetwork = CppSupportNetwork

    @PluginBackendId("cpp")
    @BackendSupportLevel(isTested = true)
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
