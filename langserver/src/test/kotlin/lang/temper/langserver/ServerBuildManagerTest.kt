package lang.temper.langserver

import kotlinx.coroutines.DelicateCoroutinesApi
import lang.temper.common.OpenOrClosed
import lang.temper.common.console
import lang.temper.common.consoleOutput
import lang.temper.common.jsonEscaper
import lang.temper.common.prefixTimestamp
import lang.temper.fs.RealFileSystem
import lang.temper.fs.runWithTemporaryDirectory
import lang.temper.library.LibraryConfiguration
import lang.temper.log.filePath
import lang.temper.tooling.buildrun.BuildHarness
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.WorkspaceFolder
import java.util.concurrent.CompletableFuture
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DelicateCoroutinesApi
class ServerBuildManagerTest {
    @Test
    fun basics() {
        // Also include timestamps on logs to match "temper serve" behavior for informal observation.
        consoleOutput.emitPrefix = ::prefixTimestamp
        val languageServer = TemperLanguageServerImpl(console = console, onExit = {})
        val client = TestClient()
        languageServer.connect(client)
        runWithTemporaryDirectory("ServerBuildManagerTestBasics") { workRoot ->
            languageServer.workspaceManager.update(listOf(WorkspaceFolder(workRoot.toUri().toString())))
            val manager = languageServer.workspaceManager.builds.values.first()
            assertEquals(manager.contextHash, manager.buildBase.name)
            // Check session root in build file system.
            val hiFilePath = filePath("hi.temper")
            manager.liveFileSystem.write(hiFilePath, """console.log("Hi!");""".toByteArray())
            val subFilePath = BuildHarness.workDir.resolve(hiFilePath)
            assertTrue(manager.watcher.harness.workFileSystem.isFile(subFilePath))
            // Check locations of output roots.
            val (buildOut, keepOut) = manager.buildServerOutputRoots()
            val buildOutFs = buildOut.fs as RealFileSystem
            val buildRootDir = buildOutFs.javaRoot
            val keepOutFs = keepOut.fs as RealFileSystem
            val keepRootDir = keepOutFs.javaRoot
            // Output should be under base.
            listOf(buildRootDir to manager.buildBase, keepRootDir to manager.keepBase).forEach {
                    (dir, base) ->
                assertTrue(
                    "$dir".startsWith("$base"),
                    "manager.buildBase=${
                        jsonEscaper.escape("$base")
                    } should be prefix of outputRootDir=${
                        jsonEscaper.escape("$dir")
                    }",
                )
                // And not under projects dir area.
                assertFalse(
                    "$dir".startsWith("$workRoot"),
                    "workRoot=${
                        jsonEscaper.escape("$workRoot")
                    } should not be a prefix of outputRootDir=${
                        jsonEscaper.escape("$dir")
                    }",
                )
            }
            // Check requests to create config, using immediate to avoid tracking delays.
            manager.maybeSuggestLibraryConfig(SuggestionDelay.Immediate, subFilePath)
            manager.maybeSuggestLibraryConfig(SuggestionDelay.Immediate, subFilePath)
            // Make a config then check again.
            manager.liveFileSystem.write(filePath(LibraryConfiguration.fileName.fullName), ByteArray(0))
            manager.maybeSuggestLibraryConfig(SuggestionDelay.Immediate, subFilePath)
            manager.watcher.awaitCurrentBuild() // TODO: do we need to wait for the manager to dispatch to a watcher?
            // We should have gotten only 2 requests, not 3.
            assertEquals(2, client.requestParamsList.size, "${client.requestParamsList}")
            assertFalse(BuildHarness.workDir.toString() in client.requestParamsList.first()!!.message)
            assertContains(client.requestParamsList.first()!!.message, "$hiFilePath")

            // Close down the build.
            languageServer.workspaceManager.update(listOf())
            manager.joinWatchThread()
            assertEquals(OpenOrClosed.Closed, manager.openOrClosed)
            // This ought to kick off currents shutdown, but don't bother to wait for it.
            languageServer.shutdown()
            // These aren't under the temp dir, so delete manually.
            // But this should be based on a hash of the temp dir, so it should be safe to delete as well.
            assertTrue(manager.buildBase.toFile().deleteRecursively())
            assertTrue(manager.keepBase.toFile().deleteRecursively())
        }
    }
}

/** Implements only those necessary for the test to work. */
private class TestClient : TemperLanguageClient {
    val messageList = mutableListOf<MessageParams?>()
    val requestParamsList = mutableListOf<ShowMessageRequestParams?>()

    override fun docGen(message: DocGenUpdate) {
        TODO("Not yet implemented")
    }

    override fun telemetryEvent(`object`: Any?) {
        TODO("Not yet implemented")
    }

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?) {
        TODO("Not yet implemented")
    }

    override fun showMessage(messageParams: MessageParams?) {
        TODO("Not yet implemented")
    }

    override fun showMessageRequest(requestParams: ShowMessageRequestParams?): CompletableFuture<MessageActionItem?> {
        requestParamsList.add(requestParams)
        return CompletableFuture.completedFuture(null)
    }

    override fun logMessage(message: MessageParams?) {
        // We don't actually check this at present, but save it in case we care to sometime.
        messageList.add(message)
    }
}
