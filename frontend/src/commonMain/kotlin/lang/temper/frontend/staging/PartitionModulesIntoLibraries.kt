package lang.temper.frontend.staging

import lang.temper.common.putMultiList
import lang.temper.frontend.Module
import lang.temper.frontend.modulesInDependencyOrder
import lang.temper.library.LibraryConfiguration
import lang.temper.log.FilePath
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModuleName

/**
 * From a set of modules, produce a set of module bundles
 * with their corresponding library configurations.
 */
fun partitionModulesIntoLibraries(
    /** The modules to partition */
    modules: List<Module>,
    /**
     * Library roots for which there must be entries in the output
     * even if they would have an empty module list.
     */
    libraryRoots: Iterable<FilePath>,
    /**
     * Returns a library configuration given a library root.
     */
    configurationForLibraryRoot: (FilePath) -> LibraryConfiguration,
): List<Pair<LibraryConfiguration, List<Module>>> {
    val modulesByRoot = mutableMapOf<FilePath, MutableList<Module>>()
    libraryRoots.forEach { libraryRoot ->
        modulesByRoot[libraryRoot] = mutableListOf()
    }

    val orderedModules = modulesInDependencyOrder(modules)
    for (module in orderedModules) {
        when (val loc = module.loc) {
            is ImplicitsCodeLocation -> {}
            is ModuleName -> {
                modulesByRoot.putMultiList(loc.libraryRoot(), module)
            }
        }
    }

    return modulesByRoot.map { (libraryRoot, modules) ->
        configurationForLibraryRoot(libraryRoot) to modules
    }
}
