package lang.temper.frontend.implicits

import lang.temper.ast.TreeVisit
import lang.temper.common.console
import lang.temper.common.runAsyncTest
import lang.temper.frontend.Module
import lang.temper.frontend.staging.getSharedStdModules
import lang.temper.fs.resolve
import lang.temper.fs.temperRoot
import lang.temper.interp.importExport.STANDARD_LIBRARY_NAME
import lang.temper.log.filePath
import lang.temper.value.DeclTree
import lang.temper.value.TString
import lang.temper.value.connectedSymbol
import lang.temper.value.importedSymbol
import lang.temper.value.toPseudoCode
import lang.temper.value.valueContained
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class ConnectedMethodsDocumentationTest {
    @Test
    fun documentationIsUpToDate() {
        // The file specified below has a list of keys.
        val documentationFile = temperRoot.resolve(
            filePath(
                "be",
                "src",
                "commonMain",
                "kotlin",
                "lang",
                "temper",
                "be",
                "README.md",
            ),
        )
        val documentationContent = Files.readString(documentationFile, Charsets.UTF_8)
        var adjustedContent = extractKeysAndAdjustContent(
            documentationFile = documentationFile,
            documentationContent = documentationContent,
            libraryName = "ImplicitsModule",
            modules = listOf(ImplicitsModule.module),
        )
        runAsyncTest {
            adjustedContent = extractKeysAndAdjustContent(
                documentationFile = documentationFile,
                documentationContent = adjustedContent,
                libraryName = STANDARD_LIBRARY_NAME,
                modules = getSharedStdModules(),
            )
        }
        if (documentationContent != adjustedContent) {
            console.log("Writing adjusted list to $documentationFile")
            Files.writeString(documentationFile, adjustedContent, Charsets.UTF_8)
        }
    }

    private fun extractKeysAndAdjustContent(
        documentationFile: Path,
        documentationContent: String,
        libraryName: String,
        modules: List<Module>,
    ): String {
        val connectedMethodKeys = mutableListOf<String>()
        for (module in modules) {
            extractConnectedMethodKeys(module, connectedMethodKeys)
        }

        return buildAdjustedContent(
            connectedMethodKeys,
            documentationFile,
            documentationContent,
            libraryName,
        )
    }
}

private fun extractConnectedMethodKeys(module: Module, connectedMethodKeys: MutableList<String>) {
    TreeVisit
        .startingAt(
            module.treeForDebug ?: fail("Implicits module broken"),
        )
        .forEachContinuing { t ->
            val metadata = (t as? DeclTree)?.parts?.metadataSymbolMultimap
            if (metadata != null && importedSymbol !in metadata) { // If it's imported, the metadata is inherited
                val connectedMetadata = metadata[connectedSymbol] ?: emptyList()
                for (connectedMetadataEdge in connectedMetadata) {
                    val arg = connectedMetadataEdge.target.valueContained(TString)
                        ?: fail("Malformed metadata in ${t.toPseudoCode()} at ${t.pos}")
                    connectedMethodKeys.add(arg)
                }
            }
        }
        .visitPostOrder()
    assertEquals(
        connectedMethodKeys.toSet().size,
        connectedMethodKeys.size,
        message = "Duplicates in $connectedMethodKeys",
    )
}

private fun buildAdjustedContent(
    connectedMethodKeys: MutableList<String>,
    documentationFile: Path,
    documentationContent: String,
    libraryName: String,
): String {
    val generatedList = connectedMethodKeys.sorted().joinToString("\n") { "- `$it`" }
    val lineBeforeList = "\n<!-- start $libraryName-connected -->\n"
    val lineAfterList = "\n<!-- end $libraryName-connected -->\n"
    val regionStart = documentationContent.indexOf(lineBeforeList)
    if (regionStart < 0) {
        fail("$lineBeforeList not in $documentationFile")
    }
    val insertionStart = regionStart + lineBeforeList.length
    val regionEnd = documentationContent.indexOf(lineAfterList, insertionStart)
    if (regionEnd < 0) {
        fail("$lineAfterList not in $documentationFile")
    }
    return documentationContent.substring(0, insertionStart) +
        generatedList +
        documentationContent.substring(regionEnd)
}
