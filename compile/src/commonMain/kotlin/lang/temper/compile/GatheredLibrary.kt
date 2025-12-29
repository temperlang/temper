package lang.temper.compile

import lang.temper.frontend.Module
import lang.temper.library.LibraryConfigurations

data class GatheredLibrary(
    val libraryConfigurations: LibraryConfigurations,
    val modules: List<Module>,
    val hasMissingModules: Boolean,
)
