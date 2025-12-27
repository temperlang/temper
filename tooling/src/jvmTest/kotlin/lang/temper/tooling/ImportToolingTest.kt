package lang.temper.tooling

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.runBlocking
import lang.temper.be.cli.ShellPreferences
import lang.temper.common.ListBackedLogSink
import lang.temper.common.console
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.fs.copyRecursive
import lang.temper.fs.runWithTemporaryDirectory
import lang.temper.log.FilePath
import lang.temper.log.FileRelatedCodeLocation
import lang.temper.tooling.buildrun.BuildHarness
import lang.temper.tooling.buildrun.doOneBuild
import lang.temper.tooling.buildrun.prepareBuild
import java.nio.file.Path
import java.util.concurrent.ForkJoinPool
import kotlin.io.path.toPath
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ImportToolingTest {
    @Ignore // TODO: move tooling/src/commonTest/resources/tests/{importer,exporter}.temper
    // into separate sub-directories
    @DelicateCoroutinesApi
    @Test
    fun imports() = runTooling("/tests/import") { contextStore, _ ->
        val exporter = contextStore.extract("exporter.temper")
        val importer = contextStore.extract("importer.temper")
        suspend fun assertFound(refDef: Pair<String, String>) {
            val pos = LocPos(importer.path, importer.findOffsetPos(refDef.first))
            val found = contextStore.moduleDataStore.findDefPos(pos)
            assertNotNull(found)
            assertEquals(exporter.path, (found.loc as FileRelatedCodeLocation).sourceFile)
            assertEquals(exporter.findOffsetPos(refDef.second), found.pos)
        }
        runBlocking {
            // Single import.
            assertFound("/*1@+3*/" to "/*1@+0*/")
            // Multiple import.
            assertFound("/*3@-2*/" to "/*3@+0*/")
        }
    }
}

internal class ModuleDataTestContextStore(
    val moduleDataStore: ModuleDataStore,
    private val resourceDir: String,
) {
    fun extract(path: String): FileModuleDataTestContext {
        val fullPath = FilePath(BuildHarness.workDir.segments + splitFilePath(path).segments, isDir = false)
        return FileModuleDataTestContext(
            moduleSource = resourceScope.getResourceAsStream("$resourceDir/$path").use { stream ->
                stream!!.readAllBytes()
            }.decodeToString(),
            moduleData = runBlocking { moduleDataStore.read(fullPath) { it!! } },
            path = fullPath,
        )
    }
}

private fun makeSnapshotter(moduleDataStore: ModuleDataStore) = ServerBuildSnapshotter(
    launchJob = { runBlocking { it() } },
    moduleDataStore = moduleDataStore,
)

private fun resourcePath(resource: String): Path {
    return resourceScope.getResource(resource)!!.toURI().toPath()
}

@DelicateCoroutinesApi
private fun <T> runTooling(
    resourceDir: String,
    checks: suspend (ModuleDataTestContextStore, Path) -> T,
): T {
    val moduleDataStore = ModuleDataStore(console = console)
    val logSinks = mutableListOf<ListBackedLogSink>()
    return runWithTemporaryDirectory("ImportToolingTest") { tmpDir ->
        copyRecursive(resourcePath(resourceDir), tmpDir)
        val build = prepareBuild(
            executorService = ForkJoinPool.commonPool(),
            backends = listOf(),
            workRoot = tmpDir,
            ignoreFile = null,
            shellPreferences = ShellPreferences.default(console),
            moduleConfig = ModuleConfig(
                makeModuleLogSink = { _, _ -> ListBackedLogSink().also { logSinks.add(it) } },
                snapshotter = makeSnapshotter(moduleDataStore),
            ),
        )
        runBlocking {
            build?.let { doOneBuild(it) }
            checks(ModuleDataTestContextStore(moduleDataStore, resourceDir), tmpDir)
        }
    }
}

private val resourceScope = ImportToolingTest::class.java
