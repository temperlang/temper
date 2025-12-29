package lang.temper.tests

import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.console
import lang.temper.common.emptyByteArray
import lang.temper.common.printStackTraceBestEffort
import lang.temper.frontend.Module
import lang.temper.frontend.staging.ModuleAdvancer
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.frontend.staging.ModuleCustomizeHook
import lang.temper.frontend.staging.partitionSourceFilesIntoModules
import lang.temper.fs.FileFilterRules
import lang.temper.fs.FilteringFileSystemSnapshot
import lang.temper.fs.MemoryFileSystem
import lang.temper.lexer.defaultClassifyTemperSource
import lang.temper.library.LibraryConfiguration
import lang.temper.log.FilePath
import lang.temper.log.LogEntry
import lang.temper.stage.Stage
import lang.temper.value.Abort
import lang.temper.value.Panic
import lang.temper.value.toPseudoCode
import kotlin.test.fail

data class PreparedFunctionalTest(
    val modules: List<Module>,
    /** Partitioned by library root */
    val partitionedModules: Map<FilePath, Pair<LibraryConfiguration, List<Module>>>,
    val libraryConfigurations: List<LibraryConfiguration>,
    val mainModule: Module,
    val projectLogSink: ListBackedLogSink,
    val stdout: StringBuilder,
)

fun prepareModulesForFunctionalTest(
    t: FunctionalTestBase,
    projectLogSink: ListBackedLogSink = ListBackedLogSink(),
    enforceStaticErrors: Boolean = false,
    customizeModule: ModuleCustomizeHook = ModuleCustomizeHook.CustomizeNothing,
): PreparedFunctionalTest {
    val moduleConfig = ModuleConfig(moduleCustomizeHook = customizeModule)
    // Added in order they finish processing.
    val moduleAdvancer = ModuleAdvancer(
        projectLogSink = projectLogSink,
        moduleConfig = moduleConfig,
    )
    moduleAdvancer.configureLibrary(
        libraryName = t.libraryName,
        libraryRoot = t.projectRoot,
    )

    val sourceTree = MemoryFileSystem()
    for (temperFile in t.temperFiles) {
        sourceTree.write(temperFile.key, temperFile.value.toByteArray(Charsets.UTF_8))
    }
    val expectedConfigFile = t.projectRoot.resolve(LibraryConfiguration.fileName, isDir = false)
    if (expectedConfigFile !in t.temperFiles) {
        sourceTree.write(expectedConfigFile, emptyByteArray)
    }
    val sourceTreeSnapshot = FilteringFileSystemSnapshot(
        sourceTree,
        FileFilterRules.Allow,
    )
    partitionSourceFilesIntoModules(
        sourceTreeSnapshot, moduleAdvancer, projectLogSink, console, mayRun = true,
        makeTentativeLibraryConfiguration = { name, root ->
            LibraryConfiguration(name, root, emptyList(), ::defaultClassifyTemperSource)
        },
    )

    val modules = moduleAdvancer.getAllModules()

    val mainLoc = t.mainFile
    val mainModule = modules.find { module ->
        // Look for the directory module containing the source.
        module.sources.any { it.filePath == mainLoc }
    } ?: fail(
        "Missing main file `$mainLoc` from among [${
            modules.joinToString(",") { "`${it.loc}`" }
        }]",
    )

    val stdout = StringBuilder()
    try {
        moduleAdvancer.advanceModules(stopBefore = Stage.Run)
        val allModules = moduleAdvancer.getAllModules()
        val allOk = allModules.all { it.ok }
        if (!allOk) {
            for (module in allModules) {
                if (!module.ok) {
                    module.failLog.logReasonForFailure(projectLogSink)
                }
            }
            fail("Failed before runtime")
        }
        if (enforceStaticErrors) {
            checkStaticErrors(t, projectLogSink)
        }
        if (projectLogSink.hasFatal) {
            console.error("Reached runtime but with fatal errors")
            mainModule.generatedCode?.toPseudoCode(console.textOutput)
        }
    } catch (e: Panic) {
        stdout.append("Panic!!!\n")
        e.printStackTraceBestEffort()
    } catch (e: Abort) {
        stdout.append("Abort!!!\n")
        e.printStackTraceBestEffort()
    }
    return PreparedFunctionalTest(
        modules = moduleAdvancer.getAllModules(),
        partitionedModules = moduleAdvancer.getPartitionedModules(),
        libraryConfigurations = moduleAdvancer.getAllLibraryConfigurations(),
        mainModule = mainModule,
        projectLogSink = projectLogSink,
        stdout = stdout,
    )
}

fun checkStaticErrors(test: FunctionalTestBase, logSink: ListBackedLogSink) {
    val allowedErrors = test.allowedErrors
    val disallowedStaticErrors = mutableListOf<LogEntry>()
    logSink.allEntries.forEach {
        if (it.level >= Log.Error && it.template.name !in allowedErrors) {
            disallowedStaticErrors.add(it)
        }
    }
    if (disallowedStaticErrors.isNotEmpty()) {
        console.group("Unexpected static errors") {
            disallowedStaticErrors.forEach {
                console.error("- ${it.template.name}: ${it.pos}:${it.messageText}")
            }
        }
        fail("${disallowedStaticErrors.size} unexpected static errors")
    }
}
