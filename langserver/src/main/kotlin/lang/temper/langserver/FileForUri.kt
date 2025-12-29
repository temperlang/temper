package lang.temper.langserver

import lang.temper.common.ignore
import java.lang.IllegalArgumentException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.FileSystemNotFoundException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * LSP4J receives URIs as strings.
 * Since our extension is registered specifically for "file", the URIs for TextDocumentIdentifiers
 * should all be `file:` URIs, so this just unpacks them to paths optimistically, returning null
 * on problems so that we can reliably complete futures.
 */
internal fun pathForUri(documentUri: DocumentUri): Path? {
    val uri = try {
        URI(documentUri.uri)
    } catch (ex: URISyntaxException) {
        ignore(ex)
        return null
    }
    return try {
        Paths.get(uri)
    } catch (ex: IllegalArgumentException) {
        ignore(ex)
        return null
    } catch (ex: FileSystemNotFoundException) {
        ignore(ex)
        return null
    }
}
