package lang.temper.tooling

import com.sleepycat.je.Database
import com.sleepycat.je.DatabaseConfig
import com.sleepycat.je.DatabaseEntry
import com.sleepycat.je.Environment
import com.sleepycat.je.EnvironmentConfig
import com.sleepycat.je.LockMode
import com.sleepycat.je.OperationStatus
import lang.temper.common.Console
import lang.temper.fs.getDirectories
import lang.temper.log.CodeLocation
import java.nio.file.Files
import java.nio.file.Path

data class StoreKey(
    val codeLocation: CodeLocation,
    val aspect: String,
    val stepId: String,
) {
    fun toByteArray() = toString().toByteArray()
    override fun toString() = listOf(codeLocation, aspect, stepId).joinToString("#")
}

class BuildSnapshotStore(
    /** Parent for a directory that is used as a workspace for temporary database files. */
    baseDirectory: Path = getDirectories().userCacheDir,
    /** For logging things. */
    console: Console,
    /** Just for diagnostics. */
    context: String,
    /** For subdir, database name, and/or other identification. */
    name: String,
) : AutoCloseable {
    private val environment: Environment
    private val snapshotDatabase: Database

    init {
        val storeDir = baseDirectory.resolve(name)
        console.log("For context: $context")
        console.log("... using snapshot dir: $storeDir")
        // Instead of new each time, reuse old if that caching helps.
        // TODO Use hashes to validate caches.
        // val envDir = Files.createTempDirectory(storeDir, "dbEnv")
        val envDir = storeDir.resolve("dbEnv")
        Files.createDirectories(envDir)
        // And up in storeDir, write the context for diagnostic back reference.
        Files.writeString(baseDirectory.resolve("context.txt"), context)

        val configuration = EnvironmentConfig()
        configuration.allowCreate = true
        environment = Environment(envDir.toFile(), configuration)

        val snapshotDatabaseConfig = DatabaseConfig()
        snapshotDatabaseConfig.allowCreate = true
        // snapshotDatabaseConfig.temporary = true
        snapshotDatabase = environment.openDatabase(null, name, snapshotDatabaseConfig)
    }

    override fun close() {
        snapshotDatabase.close()
        environment.close()
    }

    fun get(key: StoreKey): String? {
        val keyEntry = DatabaseEntry(key.toByteArray())
        val value = DatabaseEntry()
        return when (snapshotDatabase.get(null, keyEntry, value, LockMode.DEFAULT)) {
            OperationStatus.SUCCESS -> value.data.toString(Charsets.UTF_8)
            OperationStatus.KEYEXIST,
            OperationStatus.KEYEMPTY,
            OperationStatus.NOTFOUND,
            null,
            -> null
        }
    }

    fun set(key: StoreKey, value: String) {
        val keyEntry = DatabaseEntry(key.toByteArray())
        val valueEntry = DatabaseEntry(value.toByteArray())
        snapshotDatabase.put(null, keyEntry, valueEntry)
    }
}
