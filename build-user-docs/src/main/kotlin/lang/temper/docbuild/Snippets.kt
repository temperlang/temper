package lang.temper.docbuild

import kotlinx.coroutines.runBlocking
import lang.temper.common.HEX_RADIX
import lang.temper.common.MimeType
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.ignore
import lang.temper.common.isMarkdown
import lang.temper.common.putMultiList
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.toStringViaBuilder
import lang.temper.lexer.isTemperFile
import lang.temper.lexer.languageConfigForExtension
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.FilePathSegment
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory

/**
 * A compiled list of the results of running [snippetExtractors] over all source files.
 */
internal object Snippets : Structured {
    val snippetList: List<Snippet>
    internal val unreadable: List<FilePath>
    init {
        val snippetsMutList = mutableListOf<Snippet>()
        val unreadableMutList = mutableListOf<FilePath>()
        UserDocFilesAndDirectories.run {
            for (sourceFile in SourceFiles.files) {
                if (sourceFile.isDir) {
                    continue
                }
                val path = run {
                    var p = projectRoot
                    for (segment in sourceFile.segments) {
                        p = p.resolve(segment.fullName)
                    }
                    p
                }
                if (isPathAncestor(mkdocsRoot, path)) {
                    // Do not look for snippets in docs/for-users.  That's where we write out
                    // files with snippet insertions.
                    continue
                }
                // Can happen when the user has unstaged directories that need to be `git add`ed.
                if (path.isDirectory()) {
                    unreadableMutList.add(sourceFile)
                    continue
                }

                val extension = sourceFile.segments.lastOrNull()?.extension
                val mimeType = path.mimeType

                val byteContent = try {
                    Files.readAllBytes(path)
                } catch (ex: NoSuchFileException) { // Legit for files deleted from git
                    ignore(ex)
                    null
                }
                val content = when {
                    byteContent == null -> MissingContent
                    sourceFile.isTemperFile -> TemperContent(
                        sourceFile,
                        byteContent.decodeToString(),
                        languageConfigForExtension(extension),
                    )

                    mimeType.isMarkdown -> MarkdownContent(byteContent.decodeToString())
                    extension == ".kt" -> KotlinContent(byteContent.decodeToString())
                    mimeType.major == "text" -> TextSourceContent(byteContent.decodeToString())
                    else -> continue
                }
                for (extractor in snippetExtractors) {
                    extractor.extractSnippets(
                        from = sourceFile,
                        mimeType = mimeType,
                        content = content,
                        onto = snippetsMutList,
                    )
                }
            }
            var snippetsExtractedFrom = 0
            while (true) {
                val start = snippetsExtractedFrom
                val end = snippetsMutList.size
                if (start >= end) {
                    break
                }
                snippetsExtractedFrom = end

                for (i in start until end) {
                    val snippet = snippetsMutList[i]
                    for (extractor in snippetExtractors) {
                        extractor.extractNestedSnippets(snippet, snippetsMutList)
                    }
                }
            }
        }
        snippetList = snippetsMutList.toList()
        unreadable = unreadableMutList.toList()
    }

    private val snippetSlashStringToSnippets: Map<String, List<Snippet>>
    init {
        val snippetSlashStringToSnippets = mutableMapOf<String, MutableList<Snippet>>()
        snippetList.forEach { snippet ->
            if (!snippet.isIntermediate) {
                val id = snippet.id
                snippetSlashStringToSnippets.putMultiList(
                    id.shortCanonString(withExtension = false),
                    snippet,
                )
                snippetSlashStringToSnippets.putMultiList(
                    id.shortCanonString(withExtension = true),
                    snippet,
                )
            }
        }
        this.snippetSlashStringToSnippets =
            snippetSlashStringToSnippets.mapValues { it.value.toList() }
    }

    /** Lookup a snippet ID. */
    fun resolveShortId(
        /** A string like [SnippetId.shortCanonString] */
        slashString: String,
        /** Source of reference, used in failure message. */
        from: FilePath,
        /** The text of the reference, used in failure message. */
        referenceText: CharSequence,
    ): RResult<Snippet, IllegalArgumentException> {
        val slashStringNormalized =
            slashString.split("/").map {
                FilePathSegment(decodePercentEncodedBytes(it))
            }.join(isDir = false)
        require('\uFFFD' !in slashStringNormalized) {
            "%-encoded UTF-8 sequence decoded to replacement character in $referenceText"
        }
        val snippets = snippetSlashStringToSnippets[slashStringNormalized]
        if (snippets.isNullOrEmpty()) {
            return RFailure(
                IllegalArgumentException(
                    "No snippets correspond to `$referenceText` referenced in $from",
                ),
            )
        }
        if (snippets.size == 1) {
            return RSuccess(snippets[0])
        }
        val markdown = snippets.firstOrNull { it.mimeType.isMarkdown }
        if (markdown != null) {
            return RSuccess(markdown)
        }
        return RFailure(
            IllegalArgumentException(
                "Snippet reference `$referenceText` in $from is ambiguous:${
                    snippets.joinToString("") {
                        "\n- ${it.id} from ${it.source}+${it.sourceStartOffset}"
                    }
                }\nMaybe add a file extension to the reference.",
            ),
        )
    }

    /**
     * This destructures to the file content of `docs/.snippet-hashes.json` which lets the
     * *DocsUpToDateTest* check whether the snippets used to generate markdown files are
     * up-to-date with source code without running any shell commands.
     */
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        // Sort them so that we can
        val pathsToHashes = runBlocking {
            snippetList.map {
                it.id.filePath to it.content.hash()
            }
        }.sortedBy { it.first }
        pathsToHashes.forEach { (snippetIdPath, snippetContentHash) ->
            key("$snippetIdPath") {
                value(
                    toStringViaBuilder { snippetContentHash.appendHex(it) },
                )
            }
        }
    }
}

private val Path.mimeType: MimeType
    get() = when (this.extension) {
        "md" -> MimeType.markdown
        "kt" -> MimeType.kotlinSource
        else -> {
            val mimeTypeString: String? = Files.probeContentType(this)
            val slash = mimeTypeString?.indexOf('/') ?: -1
            if (slash >= 0) {
                check(mimeTypeString != null)
                MimeType(mimeTypeString.substring(0, slash), mimeTypeString.substring(slash + 1))
            } else {
                MimeType.textPlain
            }
        }
    }

/**
 * Decodes runs of `%` *hex* *hex* as described in https://url.spec.whatwg.org/#percent-encoded-byte
 */
internal fun decodePercentEncodedBytes(s: String) = percentEsc.replace(s) { matchResult ->
    val bytes = ByteArrayOutputStream()
    // We need to decode adjacent bytes using UTF-8
    val encoded = matchResult.value
    var i = 0
    while (i in encoded.indices) {
        val next = encoded[i + 1]
        i = if (next == '%') {
            // "%%" -> '%'
            bytes.write('%'.code)
            i + 2
        } else {
            val endOfSequence = i +
                1 + // %
                2 // hex characters
            val byteValue = encoded.substring(i + 1, endOfSequence).toInt(radix = HEX_RADIX)
            bytes.write(byteValue)
            endOfSequence
        }
    }
    bytes.toByteArray().decodeToString()
}

private val percentEsc = Regex("""(?:%(?:%|[\dA-Fa-f]{2}))+""")
