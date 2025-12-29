package lang.temper.langserver

import lang.temper.log.CodeLocation

internal class TextDocumentCodeLocation(
    val uri: DocumentUri,
) : CodeLocation {
    // TODO: if the context is a code location related to a workspace, relativize using its root.
    // Also, create context code locations from workspaces.
    override val diagnostic: String get() = uri.uri
}
