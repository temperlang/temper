package lang.temper.tooling

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.format.ValueSimplifyingLogSink
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.FilePositions
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position

class ModuleDataStore(private val channel: SendChannel<ModuleDataUpdate>? = null, val console: Console) {
    suspend fun sendUpdate(update: ModuleDataUpdate) = channel?.send(update)

    suspend fun makeModuleLogSink(filePath: FilePath, projectLogSink: LogSink): LogSink =
        write(filePath = filePath, label = "make log sink") {
            it.logEntries.clear()
            // Write access escapes this `write` block.
            // This presumes we don't have multiple threads compiling a single module in parallel.
            // See also where this gets copied in BuildSnapshotter before being shipped out from the compiler thread.
            ValueSimplifyingLogSink(
                parent = ListLogSink(it.logEntries, projectLogSink),
                nameSimplifying = true,
            )
        }

    private val moduleDatas = mutableMapOf<FilePath, ModuleData>()

    private val mutex = Mutex()

    /** May delay on pending data or call with null if no data is expected. */
    suspend fun <Result> read(
        filePath: FilePath,
        awaitFinish: Boolean = false,
        label: String? = null,
        action: suspend (ModuleData?) -> Result,
    ): Result {
        // We rarely expect to ask about modules that aren't found. We also don't expect high throughput.
        // So just keep a map by dir names, and check for parents of files.
        fun getData(path: FilePath) = moduleDatas[path] ?: when {
            path.isFile -> moduleDatas[path.dirName()]?.let { data ->
                when {
                    path in data.treeMap -> data
                    else -> null
                }
            }
            else -> null
        }
        if (awaitFinish) {
            // Await finish outside the lock.
            labeledDebug(label) { "Get module data to await finish to read module data for $label: $filePath" }
            val moduleData = mutex.withLock { getData(filePath) }
            labeledDebug(label) { "Await finish to read module data for $label: $filePath" }
            moduleData?.awaitFinish()
            // Now it's possible that it was finished above and someone has already tweaked it again before using.
            // One option is to keep make the data objects immutable and create new ones for each round of updates.
            // This could help against cases of catch things partially recompleted, which has pros and cons vs seeing
            // new unfinished things, one con being potentially increased memory usage.
        }
        labeledDebug(label) { "Begin read module data for $label: $filePath" }
        try {
            return mutex.withLock {
                // If ready, call with data.
                // If it won't exist, call with null.
                // If it might exist later, wait.
                // TODO Can at least see if we have pending changes. Might help for actions during live edits.
                val data = getData(filePath)
                action(data)
            }
        } finally {
            labeledDebug(label) { "End read module data for $label: $filePath" }
        }
    }

    /** If missing, creates it. */
    suspend fun <T> write(filePath: FilePath, label: String? = null, action: suspend (ModuleData) -> T): T {
        labeledDebug(label) { "Begin write module data for $label: $filePath" }
        try {
            return mutex.withLock {
                val data = moduleDatas.computeIfAbsent(filePath) { ModuleData() }
                action(data)
            }
        } finally {
            labeledDebug(label) { "End write module data for $label: $filePath" }
        }
    }

    suspend fun findDefPos(
        pos: LocPos,
        awaitFinish: Boolean = false,
        label: String? = null,
    ): LocPos? {
        var def: Def? = null
        var defPos: LocPos? = null
        read(pos.loc, awaitFinish = awaitFinish, label = label) { data ->
            def = data?.findDef(pos)?.also { defPos = data.mentionPos(it) }
        }
        // TODO Loop through multiple rounds of imports if needed?
        when (val meta = def?.tree?.value) {
            is DeclMeta -> meta.imported?.let { imported ->
                read(splitFilePath(imported.path), awaitFinish = awaitFinish, label = label) { exporter ->
                    exporter?.exports?.find { it.name.baseName.nameText == imported.symbol }?.let { export ->
                        // Exports tend to keep their names, so see if we have a match.
                        def = exporter.defByName(export.name.baseName.nameText)
                        if (def == null) {
                            // Not there, so invent one as a fallback, though this probably is the full decl instead of
                            // just the name.
                            def = ToolTree.def(
                                name = imported.symbol,
                                pos = export.position,
                                text = imported.symbol,
                            ).asDef()
                        }
                        defPos = exporter.mentionPos(def!!)
                    }
                }
            }
            else -> {}
        }
        return defPos
    }

    private fun labeledDebug(label: String?, action: () -> String) {
        if (label != null) {
            console.log(action())
        }
    }
}

fun splitFilePath(path: String) = FilePath(
    path.split("/")
        .filterNot { it == "." }
        .map { FilePathSegment(it) },
    isDir = false,
)

/** Like ListBackedLogSink but focused on local needs. */
class ListLogSink(
    private val entries: MutableList<LogEntry>,
    private val projectLogSink: LogSink,
) : LogSink {
    private var maxLevel = Log.levels.first()
    override val hasFatal get() = maxLevel >= Log.Fatal
    override fun log(level: Log.Level, template: MessageTemplateI, pos: Position, values: List<Any>, fyi: Boolean) {
        if (level >= maxLevel) {
            maxLevel = level
        }
        entries.add(LogEntry(level, template, pos, values))
        projectLogSink.log(level = level, template = template, pos = pos, values = values, fyi = fyi)
    }
}

sealed interface ModuleDataUpdate
class ModuleDiagnosticsUpdate(
    val filePositionsMap: Map<FilePath, FilePositions>,
    val logEntries: List<LogEntry>,
) : ModuleDataUpdate

internal inline fun debug(action: () -> Unit) {
    if (DEBUG) {
        action()
    }
}

private const val DEBUG = false
