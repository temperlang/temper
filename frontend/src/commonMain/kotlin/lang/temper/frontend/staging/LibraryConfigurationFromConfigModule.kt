package lang.temper.frontend.staging

import lang.temper.frontend.Module
import lang.temper.lexer.defaultClassifyTemperSource
import lang.temper.library.LibraryConfiguration
import lang.temper.name.ModuleName

fun libraryConfigurationFromConfigModule(
    module: Module,
    guessLibraryConfiguration: LibraryConfiguration?,
): LibraryConfiguration {
    val libraryNameGuess = guessLibraryConfiguration?.libraryName
    val libraryName = libraryNameForModule(module) {
        libraryNameGuess
    }
    val moduleName = module.loc as ModuleName
    return LibraryConfiguration(
        libraryName = libraryName,
        libraryRoot = moduleName.libraryRoot(),
        supportedBackendList = guessLibraryConfiguration?.supportedBackendList ?: emptyList(),
        classifyTemperSource = guessLibraryConfiguration?.classifyTemperSource ?: ::defaultClassifyTemperSource,
        configExports = module.exports?.filter { it.value != null }?.associate {
            it.name.baseName.toSymbol() to it.value!!
        } ?: emptyMap(),
    )
}
