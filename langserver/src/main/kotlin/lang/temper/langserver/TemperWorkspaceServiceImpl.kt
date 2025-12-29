package lang.temper.langserver

import kotlinx.coroutines.DelicateCoroutinesApi
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.services.WorkspaceService

@DelicateCoroutinesApi
internal class TemperWorkspaceServiceImpl(
    private val languageServer: TemperLanguageServerImpl,
) : WorkspaceService {
    private val console = languageServer.console

    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
        if (params == null) { return }
        console.log("didChangeConfiguration settings=${params.settings}")
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {
        if (params == null) { return }
        for (change in params.changes) {
            console.log("TemperWorkspaceServiceImpl change watched files: $change")
        }
    }

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams?) {
        console.log(params?.toString() ?: "null params")
        languageServer.client!!.workspaceFolders().thenAccept {
            languageServer.workspaceManager.update(it)
        }
    }
}
