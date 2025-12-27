package lang.temper.cli.repl

import com.sleepycat.je.Database
import com.sleepycat.je.DatabaseConfig
import com.sleepycat.je.DatabaseEntry
import com.sleepycat.je.Environment
import com.sleepycat.je.EnvironmentConfig
import com.sleepycat.je.LockMode
import com.sleepycat.je.OperationStatus
import lang.temper.common.AppendingTextOutput
import lang.temper.common.SnapshotKey
import lang.temper.common.Snapshotter
import lang.temper.common.structure.Structured
import lang.temper.common.toStringViaBuilder
import lang.temper.cst.CstInner
import lang.temper.format.OutToks
import lang.temper.format.toStringViaTokenSink
import lang.temper.frontend.AstSnapshotKey
import lang.temper.frontend.CstSnapshotKey
import lang.temper.frontend.ExportList
import lang.temper.frontend.ExportsSnapshotKey
import lang.temper.value.BlockTree
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.toLispy
import lang.temper.value.toPseudoCode
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * A store of diagnostic snapshots of the processing of chunks of Temper code.
 */
internal class SnapshotStore(
    /**
     * Parent for a directory that is used as a workspace for temporary database files.
     */
    userCacheDirectory: Path,
    /**
     * Whether diagnostic snapshots that might be dumped to output should include TTY codes.
     */
    val storeTtyLikeSnapshots: Boolean,
) : AutoCloseable {
    private val environment: Environment
    private val snapshotDatabase: Database

    init {
        val envDir = Files.createDirectories(
            userCacheDirectory.resolve(Path.of(".replSnapshotStore", "dbEnv")),
        )

        val configuration = EnvironmentConfig()
        configuration.allowCreate = true
        environment = Environment(envDir.toFile(), configuration)

        val snapshotDatabaseConfig = DatabaseConfig()
        snapshotDatabaseConfig.allowCreate = true
        snapshotDatabaseConfig.temporary = true
        snapshotDatabase = environment.openDatabase(null, "replSnapshots", snapshotDatabaseConfig)
    }

    override fun close() {
        if (!environment.isClosed) {
            snapshotDatabase.close()
            environment.close()
        }
    }

    fun snapshotterFor(loc: ReplChunkIndex): Snapshotter = SnapshotterImpl(loc)

    fun retrieveSnapshot(loc: ReplChunkIndex, aspect: Aspect, stepId: String): String? {
        val key = DatabaseEntry(keyFor(loc, aspect, stepId))
        val value = DatabaseEntry()
        return when (snapshotDatabase.get(null, key, value, LockMode.DEFAULT)) {
            OperationStatus.SUCCESS -> value.data.toString(Charsets.UTF_8)
            OperationStatus.KEYEXIST,
            OperationStatus.KEYEMPTY,
            OperationStatus.NOTFOUND,
            null,
            -> null
        }
    }

    private inner class SnapshotterImpl(val loc: ReplChunkIndex) : Snapshotter {
        override fun <IR : Structured> snapshot(key: SnapshotKey<IR>, stepId: String, state: IR) {
            val entries =
                AstSnapshotKey.useIfSame(key, state) { block ->
                    snapshotAst(stepId, block)
                }
                    ?: CstSnapshotKey.useIfSame(key, state) { cstInner ->
                        snapshotCst(stepId, cstInner)
                    }
                    ?: ExportsSnapshotKey.useIfSame(key, state) { exports ->
                        snapshotExports(stepId, exports)
                    }
                    ?: emptyList()
            for ((dbKey, dbValue) in entries) {
                snapshotDatabase.put(null, dbKey, dbValue)
            }
        }

        fun snapshotAst(stepId: String, ast: BlockTree): List<Pair<DatabaseEntry, DatabaseEntry>> {
            val lispy = ast.toLispy(multiline = true)
            val pseudoCode = toStringViaBuilder {
                val bufferedTextOutput = AppendingTextOutput(it, isTtyLike = storeTtyLikeSnapshots)
                ast.toPseudoCode(bufferedTextOutput, detail = describeDetailLevel)
                bufferedTextOutput.flush()
            }
            return listOf(
                DatabaseEntry(keyFor(loc, AstAspect.Lispy, stepId)) to
                    lispy.toDatabaseEntry(),
                DatabaseEntry(keyFor(loc, AstAspect.PseudoCode, stepId)) to
                    pseudoCode.toDatabaseEntry(),
            )
        }

        fun snapshotCst(stepId: String, cst: CstInner): List<Pair<DatabaseEntry, DatabaseEntry>> {
            return listOf(
                DatabaseEntry(keyFor(loc, CstAspect, stepId)) to
                    toStringViaTokenSink(
                        isTtyLike = storeTtyLikeSnapshots,
                        singleLine = false,
                    ) {
                        cst.renderTo(it)
                    }.toDatabaseEntry(),
            )
        }

        fun snapshotExports(
            stepId: String,
            exports: ExportList,
        ): List<Pair<DatabaseEntry, DatabaseEntry>> = listOf(
            DatabaseEntry(keyFor(loc, ExportsAspect, stepId)) to
                toStringViaTokenSink(isTtyLike = storeTtyLikeSnapshots) { out ->
                    exports.forEachIndexed { index, export ->
                        if (index != 0) { out.emit(OutToks.semi) }
                        out.emit(export.name.baseName.toToken(inOperatorPosition = false))
                        val type = export.typeInferences?.type
                        if (type != null) {
                            out.emit(OutToks.colon)
                            type.renderTo(out)
                        }
                        val value = export.value
                        if (value != null) {
                            out.emit(OutToks.eq)
                            value.renderTo(
                                out,
                                typeInfoIsRedundant = type != null,
                            )
                        }
                    }
                }.toDatabaseEntry(),
        )
    }

    private fun keyFor(loc: ReplChunkIndex, aspect: Aspect, stepId: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        val objectOutputStream = ObjectOutputStream(bytes)
        objectOutputStream.writeInt(loc.index)
        objectOutputStream.writeInt(aspect.ordinal)
        objectOutputStream.writeUTF(stepId)
        objectOutputStream.flush()
        return bytes.toByteArray()
    }
}

internal interface Aspect { val ordinal: Int }

/**
 * What are we storing about the state at the [AstSnapshotKey] snapshot.
 */
internal enum class AstAspect : Aspect {
    Lispy,
    PseudoCode,
}

internal object CstAspect : Aspect { override val ordinal = 0 }

internal object ExportsAspect : Aspect { override val ordinal = 0 }

private var describeDetailLevel = PseudoCodeDetail(
    showInferredTypes = true,
) // TODO: allow configuring this

private fun (String).toDatabaseEntry(): DatabaseEntry = DatabaseEntry(toByteArray(Charsets.UTF_8))
