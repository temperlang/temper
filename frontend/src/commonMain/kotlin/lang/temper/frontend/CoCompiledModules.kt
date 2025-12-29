package lang.temper.frontend

import lang.temper.common.WeightedAdjacencyTable
import lang.temper.common.toStringViaBuilder
import lang.temper.interp.importExport.Importer
import lang.temper.library.LibraryConfiguration
import lang.temper.log.CodeLocation
import lang.temper.log.FilePath
import lang.temper.log.plus
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModuleLocation
import lang.temper.name.ModuleName

/**
 * A group of modules that were compiled together and so which may reference
 * one another.
 *
 * # Library bundles
 *
 * [libraries] groups modules together by library.
 *
 * Typically, each group only contains modules that are under the associated
 * [library root][LibraryModuleBundle.libraryRoot].
 *
 * Imagine we have two libraries rooted at `foo/` and `bar/` and the following
 * file definitions.
 *
 * TODO: rewrite this example to use emplace instead of import with extra arguments
 *
 * ````
 * /* foo//config.temper.md */
 * ```
 * import("./foo.temper", 1 /* Pass module parameter */)
 * ```
 *
 * /* foo//foo.temper */
 * @moduleParameter export let x: Int;
 *
 * /* bar//config.temper.md */
 * ```
 * import("./bar.temper");
 * ```
 *
 * /* bar//bar.temper */
 * let { x as a } = import("../foo.temper", 1);
 * let { x as b } = import("../foo.temper", 2);
 * ````
 *
 * So the *foo* library, regardless of which other libraries
 * it's co-compiled with, will always include
 *
 * - *foo//config.temper.md*
 * - *foo//foo.temper(1)*
 *
 * but if it were not co-compiled with *bar* there would be no
 * *foo//foo.temper(2)*, so *bar*, compiled separately would not
 * have all the modules it needs to load properly.
 *
 * That last module is an implementation detail of *bar*
 * so needs to be bundled with *bar*.
 */
class CoCompiledModules private constructor(
    /** The modules in the set in topological order. */
    val modules: List<Module>,
    /**
     * For each library, the modules that need to be translated with it.
     * This is roughly a partition of [modules] by library, but if a
     * non-default parameterization of a module is needed cross-library,
     * and that parameterization is not guaranteed to be bundled with
     * the defining library then it has to be bundled as an implementation
     * detail in the importing library.
     */
    val libraries: List<LibraryModuleBundle>,
) {

    /** Information about a single library. */
    class LibraryModuleBundle internal constructor(
        val libraryRoot: FilePath,
        /**
         * A configuration for the library rooted at [libraryRoot] if available.
         * If non-null, [libraryRoot] == [configuration.libraryRoot][LibraryConfiguration.libraryRoot].
         */
        val configuration: LibraryConfiguration,
        /**
         * The modules associated that need to be bundled with [libraryRoot].
         * These modules are a subset of [CoCompiledModules.modules] and are
         * similarly topologically ordered.
         * Normally, all of these modules are derived from source files under
         * [libraryRoot], but there are corner-cases related to parameterized
         * modules where that is not the case.  See the caveat above.
         */
        val modules: List<Module>,
    )

    override fun toString(): String = toStringViaBuilder { sb ->
        // Outputs a debugging dump like the below
        //
        // modules:
        //   module-name-1
        //   module-name-2
        //   module-name-2
        // libraries:
        //   * root-1 : library-name-1
        //       module-name-1
        //   * root-2 : library-name-2
        //       module-name-2
        //       module-name-3

        // This format is used in unit-tests.
        sb.append("modules:")
        modules.forEach {
            sb.append("\n  ").append(it.loc)
        }
        sb.append("\nlibraries:")
        libraries.forEach { b ->
            sb.append("\n  * ${b.libraryRoot} : ${b.configuration.libraryName}")
            b.modules.forEach {
                sb.append("\n      ").append(it.loc)
            }
        }
    }

    companion object {
        operator fun invoke(
            modules: List<Module>,
            libraryRootList: Iterable<FilePath>,
            configurationForLibraryAtRoot: (FilePath) -> LibraryConfiguration,
        ): CoCompiledModules {
            val moduleList = modulesInDependencyOrder(modules)

            // Collect the library roots.
            val libraryRoots = mutableSetOf<FilePath>()
            libraryRoots.addAll(libraryRootList)
            for (module in moduleList) {
                when (val loc = module.loc) {
                    is ModuleName -> libraryRoots.add(loc.libraryRoot())
                    is ImplicitsCodeLocation -> Unit
                }
            }
            libraryRoots.sortedBy { it }

            val configurations = libraryRoots.associateWith { configurationForLibraryAtRoot(it) }

            // For each library, produce a partition.
            // We do this in three steps:
            // 1. First find starting points for each library root.
            //    - If the library set includes a config file, that's the starting point.
            //    - Otherwise, it's all the files in that library, which is the right thing
            //      to do for our test harnesses that create files without config files.
            // 2. Walk import records transitively from those roots to find same-library sets.
            //    These are the files that are guaranteed to be bundled with the library.
            // 3. Walk from those sets to include any non-bundled cross-library imports.
            val moduleByLocation = moduleList.associateBy { it.loc }
            val modulesByLibraryRoot = moduleList.groupBy {
                when (val loc = it.loc) {
                    is ModuleName -> loc.libraryRoot()
                    is ImplicitsCodeLocation -> null
                }
            }

            // Step 1
            val startingPoints = libraryRoots.associateWith { root ->
                val modulesForRoot = modulesByLibraryRoot[root] ?: emptyList()

                val configName = ModuleName(
                    root + LibraryConfiguration.fileName,
                    libraryRootSegmentCount = root.segments.size,
                    isPreface = false,
                )
                val configModule = modulesForRoot.firstOrNull { it.loc == configName }
                if (configModule != null) {
                    listOf(configModule)
                } else {
                    modulesForRoot
                }
            }

            // Step 2
            val sameLibraryModules =
                startingPoints.mapValues { (root, starts) ->
                    val moduleLocations = mutableSetOf<ModuleLocation>()
                    val deque = ArrayDeque<ModuleLocation>()
                    starts.mapTo(deque) { it.loc }
                    while (deque.isNotEmpty()) {
                        val loc = deque.removeFirst()
                        if (loc !in moduleLocations) {
                            moduleLocations.add(loc)
                            val module = moduleByLocation[loc]
                            module?.importRecords?.forEach { importRecord ->
                                if (importRecord is Importer.OkImportRecord) {
                                    val exporterLocation = importRecord.exporterLocation
                                    if ((exporterLocation as? ModuleName)?.libraryRoot() == root) {
                                        deque.add(exporterLocation)
                                    }
                                }
                            }
                        }
                    }
                    moduleLocations.toSet()
                }

            // Step 3
            val libraries = sameLibraryModules.mapNotNull { (root, initial) ->
                val expanded = mutableSetOf<CodeLocation>()
                val deque = ArrayDeque(initial)
                while (deque.isNotEmpty()) {
                    val loc = deque.removeFirst()
                    if (loc !in expanded) {
                        expanded.add(loc)
                        val module = moduleByLocation[loc]
                        module?.importRecords?.forEach { importRecord ->
                            if (importRecord is Importer.OkImportRecord) {
                                val exporterLocation = importRecord.exporterLocation as? ModuleName
                                if (exporterLocation != null) {
                                    val inSameLibrary =
                                        sameLibraryModules[exporterLocation.libraryRoot()] ?: emptySet()
                                    if (exporterLocation !in inSameLibrary) {
                                        deque.add(exporterLocation)
                                    }
                                }
                            }
                        }
                    }
                }
                // Present the partition
                LibraryModuleBundle(
                    libraryRoot = root,
                    configuration = configurations.getValue(root),
                    // Present modules in the library in the same order as in the topological sort.
                    modules = moduleList.filter { it.loc in expanded },
                )
            }

            return CoCompiledModules(
                modules = moduleList.toList(),
                libraries = libraries,
            )
        }
    }
}

fun modulesInDependencyOrder(modules: List<Module>): List<Module> {
    val modulesByLocation = modules.associateBy { it.loc }
    val moduleGraph = WeightedAdjacencyTable(
        // First, we sort by module location.  This allows our
        // stable topological-sort to partially reorder from a
        // deterministic foundation.
        modules.sortedBy { it.loc },
    )
    // Build a map relating imports so that we can order importers
    // after exporters.
    modules.forEach { importer ->
        importer.importRecords.forEach { importRecord ->
            if (importRecord is Importer.OkImportRecord) {
                val exporterLocation = importRecord.exporterLocation
                val exporter = modulesByLocation[exporterLocation]
                if (exporter != null) {
                    moduleGraph[importer, exporter] = if (importRecord.isBlockingImport) {
                        2 // Give blocking imports priority
                    } else {
                        1
                    }
                }
            }
        }
    }

    moduleGraph.breakCycles()
    return moduleGraph.partiallyOrder()
}
