package lang.temper.be.cpp03

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.cpp.CppNames
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
import lang.temper.common.MimeType
import lang.temper.fs.ResourceDescriptor
import lang.temper.fs.declareResources
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.resolveDir
import lang.temper.name.BackendId
import lang.temper.name.BackendMeta
import lang.temper.name.FileType
import lang.temper.name.LanguageLabel

/**
 * <!-- snippet: backend/c -->
 * # C++ Backend
 *
 * ⎀ backend/cpp03/id
 *
 * Translates Temper to C++ source, with an eye to wide portability.
 * Generated code should work on C++03 but also support modern C++
 * features.
 */
class CppBackend(setup: BackendSetup<CppBackend>) : Backend<CppBackend>(Factory.backendId, setup) {
    private val cppNames = CppNames()

    override fun tentativeTmpL(): TmpL.ModuleSet = run {
        TmpLTranslator.translateModules(
            logSink,
            readyModules,
            CppSupportNetwork,
            libraryConfigurations,
            dependencyResolver,
            tentativeOutputPathFor = { allocateTextFile(it, FILE_EXTENSION, defaultName = "module") },
        )
    }

    override fun translate(finished: TmpL.ModuleSet): List<OutputFileSpecification> = run {
        buildList {
            for (module in finished.modules) {
                val translator = CppTranslator(module, cppNames)
                addAll(translator.translate())
            }
            if (config.makeMetaDataFile && !config.abbreviated) {
                MetadataFileSpecification(
                    path = filePath("main.cpp"),
                    mimeType = mimeType,
                    content = "int main() {}",
                ).also { add(it) }
            }
        }
    }

    override val supportNetwork = CppSupportNetwork

    companion object {
        const val FILE_EXTENSION = ".cpp"

        // val mimeType = MimeType("text", "x-csrc")
        val mimeType = MimeType.cppSource

        private val resourceBase = dirPath("lang", "temper", "be", "cpp03")
        private val coreResourceBase = resourceBase.resolveDir("temper-core")

        /**
         * <!-- snippet: backend/c/id -->
         * BackendID: `cpp03`
         */
        internal const val BACKEND_ID = "cpp03"
    }

    @PluginBackendId(BACKEND_ID)
    @BackendSupportLevel(isSupported = false, isDefaultSupported = false, isTested = true)
    object Factory : Backend.Factory<CppBackend> {
        override val backendId = BackendId(BACKEND_ID)
        override val specifics = CppSpecifics

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
                filePath("core.hpp"),
                filePath("expected.hpp"),
                filePath("int.hpp"),
                filePath("shared.hpp"),
            )

        override fun make(setup: BackendSetup<CppBackend>) = CppBackend(setup)
    }
}

enum class CppVersion {
    Cpp03,
    Cpp11,
    Cpp23,
    ;

    /** For "std" flags. Not all work on all compilers. */
    fun nameForArg(): String = when (this) {
        Cpp03 -> "c++03"
        Cpp11 -> "c++11"
        Cpp23 -> "c++23"
    }
}
