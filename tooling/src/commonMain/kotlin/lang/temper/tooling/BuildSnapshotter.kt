package lang.temper.tooling

import lang.temper.common.SnapshotKey
import lang.temper.common.Snapshotter
import lang.temper.common.console
import lang.temper.common.structure.Structured
import lang.temper.frontend.AstSnapshotKey
import lang.temper.frontend.Module
import lang.temper.log.Debug
import lang.temper.log.FilePath
import lang.temper.log.FilePositions
import lang.temper.log.LogEntry
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModuleName
import lang.temper.value.BlockTree

abstract class BuildSnapshotter : Snapshotter {
    override fun <IR : Structured> snapshot(key: SnapshotKey<IR>, stepId: String, state: IR) {
        AstSnapshotKey.useIfSame(key, state) { block ->
            snapshotAst(stepId, block)
        }
    }

    abstract fun snapshotAst(stepId: String, ast: BlockTree)
}

class ServerBuildSnapshotter(
    val moduleDataStore: ModuleDataStore,
    val launchJob: (suspend () -> Unit) -> Unit,
) : BuildSnapshotter() {
    override fun snapshotAst(stepId: String, ast: BlockTree) {
        // Not sure if we can ever get a context that isn't a module here, but just back out if so.
        val module = (ast.document.context as? Module) ?: return
        val sources = module.sources
        val filePath = when (val loc = module.loc) {
            is ModuleName -> if (loc.isPreface) {
                // Skip preface for now. TODO(tjp, tooling): Merge preface data into other module data.
                return
            } else {
                loc.sourceFile
            }
            is ImplicitsCodeLocation -> return
        }
        when {
            // Expect the precomputed string object because of our usage.
            stepId === Debug.Frontend.ParseStage.After.loggerName -> {
                val toolTrees = sources.map { source ->
                    val toolTreeWithoutComments = convertTree(source.tree!!)
                    weaveComments(tree = toolTreeWithoutComments, comments = source.comments!!)
                }
                debug {
                    console.info("Block for parse stage job for: $filePath")
                }
                launchJob {
                    moduleDataStore.write(filePath, label = "snapshot parse") { moduleData ->
                        moduleData.resetTrees(toolTrees)
                        // We expect filePositions or other kept into to be non-mutated, so just pass it along.
                        moduleData.setSources(sources)
                    }
                }
            }
            stepId === Debug.Frontend.SyntaxMacroStage.After.loggerName -> {
                // Convert the tree before going async.
                val infoMap = extractDecls(ast)
                debug {
                    console.info("Block for syntax macro stage job for: $filePath")
                }
                launchJob {
                    moduleDataStore.write(filePath, label = "snapshot syntax macro") { moduleData ->
                        val declTrees = moduleData.trees.map { correlateDecls(tree = it, infoMap = infoMap) }
                        moduleData.resetTrees(declTrees)
                    }
                }
            }
            stepId === Debug.Frontend.GenerateCodeStage.After.loggerName -> {
                val infoMap = extractTypes(ast)
                val exports = module.exports ?: emptyList()
                // On JVM, this `launchJob` is actually a `runBlocking`, so everything below is synchronous with the
                // compiler thread that sends this snapshot.
                // On JS, things are single-threaded in the first place.
                debug {
                    console.info("Block for generate stage job for: $filePath")
                }
                launchJob {
                    var filePositionsMap: Map<FilePath, FilePositions> = mapOf()
                    val logEntries: MutableList<LogEntry> = mutableListOf()
                    moduleDataStore.write(filePath, label = "snapshot generate") { moduleData ->
                        val typedTrees = moduleData.trees.map { correlateTypes(tree = it, infoMap = infoMap) }
                        moduleData.resetTrees(typedTrees)
                        moduleData.exports = exports
                        moduleData.finish()
                        // These technically are reads, but go ahead and grab them while we can.
                        // We could pull file positions from sources, but we presumably already have them handy here.
                        filePositionsMap = moduleData.filePositionsMap.toMap()
                        // Updates to log entries happen from the compiler thread outside the mutex, but for the
                        // moment, we also are synced with the compiler thread for this module.
                        logEntries.addAll(moduleData.logEntries)
                    }
                    // Send the safe copy after we exit the mutex block.
                    moduleDataStore.sendUpdate(
                        ModuleDiagnosticsUpdate(
                            filePositionsMap = filePositionsMap,
                            logEntries = logEntries,
                        ),
                    )
                }
            }
        }
    }
}
