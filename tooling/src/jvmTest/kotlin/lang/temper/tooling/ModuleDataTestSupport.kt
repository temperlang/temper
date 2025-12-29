package lang.temper.tooling

import kotlinx.coroutines.runBlocking
import lang.temper.common.Console
import lang.temper.common.console
import lang.temper.common.mapFirst
import lang.temper.frontend.Module
import lang.temper.frontend.ModuleSource
import lang.temper.frontend.implicits.builtinEnvironment
import lang.temper.interp.EmptyEnvironment
import lang.temper.lexer.Genre
import lang.temper.lexer.MarkdownLanguageConfig
import lang.temper.lexer.languageConfigForExtension
import lang.temper.log.Debug
import lang.temper.log.FilePath
import lang.temper.log.FilePosition
import lang.temper.log.LogSink
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.last
import lang.temper.name.BuiltinName
import lang.temper.name.ModuleName
import lang.temper.stage.Stage
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

interface ModuleDataTestContext {
    fun assertCompletions(
        spot: String,
        expected: List<String>,
        includeBuiltins: Boolean = false,
    )

    fun assertFound(refDef: Pair<String, String?>)

    fun findOffsetLocPos(spot: String): LocPos

    val moduleData: ModuleData
}

internal open class DirModuleDataTestContext(
    sources: List<ModuleSource>,
) : ModuleDataTestContext {
    /** Sources maybe with other details inferred. */
    val sources = sources.mapIndexed { index, source ->
        val filePath = source.filePath ?: run {
            val ext = when (source.languageConfig) {
                is MarkdownLanguageConfig -> ".temper.md"
                else -> ".temper"
            }
            filePath("source$index$ext")
        }
        source.copy(
            filePath = filePath,
            languageConfig = languageConfigForExtension(filePath.last().extension),
        )
    }

    override val moduleData = buildModuleData(sourceFile = dirPath(), sources = this.sources)

    fun mergedFileContext() = FileModuleDataTestContext(mergedSourceText())

    fun mergedSourceText() = sources.joinToString("\n") { it.fetchedContent!! }

    override fun assertCompletions(spot: String, expected: List<String>, includeBuiltins: Boolean) {
        assertTrue(moduleData.finished, "Expected module data to be finished.")
        val pos = findOffsetLocPos(spot)
        var defs = moduleData.findCompletions(pos)
        // Leave out builtins for tests by default, so we can focus.
        if (!includeBuiltins) {
            val builtins = builtinEnvironment(EmptyEnvironment, Genre.Library)
            defs = defs.filter { builtins.constness(BuiltinName(it.name)) == null }
        }
        assertEquals(expected.sorted(), defs.map { it.text }.sorted())
    }

    override fun assertFound(refDef: Pair<String, String?>) {
        assertTrue(moduleData.finished, "Expected module data to be finished.")
        val expectedDef = refDef.second?.let { findOffsetLocPos(spot = it) }
        val refPos = findOffsetLocPos(refDef.first)
        val foundDef = moduleData.findDefPos(refPos)
        assertEquals(expectedDef, foundDef)
    }

    /** Find the [LocPos] of [spot], including any "@offset" in the spot comment pattern within the containing file. */
    override fun findOffsetLocPos(spot: String) = sources.mapFirst sources@{ source ->
        val pos = findOffsetPosMaybe(moduleSource = source.fetchedContent!!, spot = spot) ?: return@sources null
        LocPos(source.filePath!!, pos)
    } ?: error("Spot $spot missing")
}

internal open class FileModuleDataTestContext(
    val moduleSource: String,
    extension: String? = null,
    override val moduleData: ModuleData = buildModuleData(moduleSource, extension = extension),
    val path: FilePath = moduleData.treeMap.keys.first(),
) : ModuleDataTestContext {
    fun findOffsetPos(spot: String) = findOffsetPos(moduleSource, spot)

    override fun assertCompletions(spot: String, expected: List<String>, includeBuiltins: Boolean) {
        assertTrue(moduleData.finished, "Expected module data to be finished.")
        val pos = findOffsetPos(moduleSource = moduleSource, spot = spot)
        var defs = moduleData.findCompletions(LocPos(path, pos))
        // Leave out builtins for tests by default, so we can focus.
        if (!includeBuiltins) {
            val builtins = builtinEnvironment(EmptyEnvironment, Genre.Library)
            defs = defs.filter { builtins.constness(BuiltinName(it.name)) == null }
        }
        assertEquals(expected.sorted(), defs.map { it.text }.sorted())
    }

    override fun assertFound(refDef: Pair<String, String?>) {
        assertTrue(moduleData.finished, "Expected module data to be finished.")
        val expectedDef = refDef.second?.let { findOffsetPos(moduleSource = moduleSource, spot = it) }
        val refPos = findOffsetPos(moduleSource = moduleSource, refDef.first)
        val foundDef = moduleData.findDefPos(LocPos(path, pos = refPos))?.pos
        assertEquals(expectedDef, foundDef)
    }

    override fun findOffsetLocPos(spot: String): LocPos =
        LocPos(path, findOffsetPos(moduleSource, spot = spot))
}

internal fun buildModuleData(input: String, extension: String? = null): ModuleData {
    val ext = extension ?: ".temper"
    val sourceFile = filePath("test${Random.nextInt()}$ext")
    val sources = listOf(
        ModuleSource(
            fetchedContent = input,
            filePath = sourceFile,
            languageConfig = languageConfigForExtension(ext.substring(ext.lastIndexOf('.'))),
        ),
    )
    return buildModuleData(sourceFile = sourceFile, sources = sources)
}

internal fun buildModuleData(sourceFile: FilePath, sources: List<ModuleSource>): ModuleData {
    // Module
    val moduleDataStore = ModuleDataStore(console = console)
    val name = ModuleName(
        sourceFile = sourceFile,
        libraryRootSegmentCount = 0,
        isPreface = false,
    )
    val module = Module(
        projectLogSink = runBlocking { moduleDataStore.makeModuleLogSink(name.sourceFile, LogSink.devNull) },
        loc = name,
        console = console,
        continueCondition = { true },
    )
    module.deliverContent(sources)
    // Snapshotter
    val snapshotter =
        ServerBuildSnapshotter(
            moduleDataStore = moduleDataStore,
            launchJob = { action ->
                runBlocking {
                    action()
                }
            },
        )
    val snapshottingConsole = Console(console.textOutput, logLevel = console.level, snapshotter = snapshotter)
    Debug.Frontend.configure(key = module, consoleForKey = snapshottingConsole)
    // Advance
    while (module.stageCompleted != Stage.GenerateCode) {
        module.advance()
    }
    // Module data
    // This test requires jvm for `runBlocking`. TODO Is there an alternative?
    return runBlocking { moduleDataStore.read(name.sourceFile) { it!! } }
}

private val offsetSpotPattern = Regex("""/\*(.*)@(\+?)(.*)\*/""")

/** Find the [FilePosition] of [spot], including any "@offset" in the spot comment pattern. */
internal fun findOffsetPos(moduleSource: String, spot: String): FilePosition =
    findOffsetPosMaybe(moduleSource = moduleSource, spot = spot) ?: error("Spot $spot missing")

internal fun findOffsetPosMaybe(moduleSource: CharSequence, spot: String): FilePosition? {
    val charOffset = when (val match = offsetSpotPattern.matchEntire(spot)) {
        null -> 0
        else -> {
            val offset = match.groupValues[3].toInt()
            offset + when (offset > 0 || match.groupValues[2] == "+") {
                true -> spot.length
                else -> 0
            }
        }
    }
    return findPos(moduleSource = moduleSource, text = spot)?.let {
        it.copy(charInLine = it.charInLine + charOffset)
    }
}

/** Find the first [FilePosition] of [text], returning null if missing. */
internal fun findPos(moduleSource: CharSequence, text: String): FilePosition? {
    for ((line, lineText) in moduleSource.splitToSequence("\n").withIndex()) {
        val charInLine = lineText.indexOf(text)
        if (charInLine >= 0) {
            return FilePosition(line = line + 1, charInLine = charInLine)
        }
    }
    return null
}
