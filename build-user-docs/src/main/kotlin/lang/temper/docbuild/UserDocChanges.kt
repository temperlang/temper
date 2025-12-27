package lang.temper.docbuild

import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.structure.arrAsKeyedObj
import lang.temper.log.FilePath

internal data class UserDocChanges(
    val snippetChanges: List<SnippetChange>,
    val fileChanges: List<FileChange>,
) : Structured {
    data class SnippetChange(
        val snippet: Snippet,
        val newContent: MarkdownContent,
        val from: FilePath,
    ) {
        override fun hashCode(): Int =
            snippet.id.hashCode() + 31 * newContent.hashCode()

        override fun equals(other: Any?): Boolean =
            other is SnippetChange && this.snippet.id == other.snippet.id &&
                this.newContent == other.newContent
    }

    data class FileChange(
        val relFilePath: FilePath,
        val newContent: MarkdownContent,
    )

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("snippetChanges", isDefault = snippetChanges.isEmpty()) {
            arrAsKeyedObj(
                snippetChanges,
                emitValueFor = { value(it.newContent.fileContent) },
            ) {
                it.snippet.id.shortCanonString(false)
            }
        }
        key("fileChanges", isDefault = fileChanges.isEmpty()) {
            arrAsKeyedObj(
                fileChanges,
                emitValueFor = { value(it.newContent.fileContent) },
            ) {
                "${it.relFilePath}"
            }
        }
    }
}
