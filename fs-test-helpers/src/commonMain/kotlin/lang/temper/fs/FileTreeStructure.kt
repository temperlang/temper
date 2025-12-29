package lang.temper.fs

import lang.temper.common.Console
import lang.temper.common.Either
import lang.temper.common.console
import lang.temper.common.isJson
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureParser
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.log.FilePath
import lang.temper.log.last

fun OutFile.fileTreeStructure(): Structured = root.fs.fileTreeStructure(path)

/**
 * Dumps a description of a file tree below `this` like the below to the given console.
 *
 * ```
 * temper.keep
 * └─ py
 *    ├─ apple
 *    │  └─ name-selection.json 1883B
 *    ├─ banana
 *    │  └─ name-selection.json 1309B
 *    └─ std
 *       └─ name-selection.json 67130B
 * ```
 */
expect fun NativePath.fileTree(out: Console = console)

fun FileSystem.fileTreeStructure(from: FilePath = FilePath.emptyPath): Structured = object : Structured {
    override fun destructure(structureSink: StructureSink) {
        destructureFilesOnto(from, structureSink)
    }

    private fun destructureFilesOnto(path: FilePath, sink: StructureSink): Unit = sink.obj {
        key("_name", Hints.u) { value(path.lastOrNull()?.fullName) }
        when (classify(path)) {
            FileClassification.DoesNotExist -> {}
            FileClassification.File -> {
                val fs = this@fileTreeStructure
                val mimeType = fs.readMimeType(path)
                key("mimeType", Hints.u) { value(mimeType) }

                val f = (fs as? MemoryFileSystem)?.lookup(path) as? MemoryFileSystem.File
                val content = f?.textOrBinaryContent
                    ?: fs.readBinaryFileContentSync(path).result?.let {
                        try {
                            Either.Left(it.decodeToString(throwOnInvalidSequence = true))
                        } catch (_: CharacterCodingException) {
                            Either.Right(it)
                        }
                    }

                var textContentHints = Hints.empty
                if (mimeType?.isJson == true && content is Either.Left) {
                    key("jsonContent") {
                        StructureParser.parseJsonTo(content.item, this, tolerant = false)
                    }
                    textContentHints = Hints.u
                }

                val (type, contentString) = when (content) {
                    null -> null to null
                    is Either.Left -> "txt" to content.item
                    is Either.Right -> "bin" to content.item.base64Encode()
                }

                key("_type", Hints.u) { value(type) }
                key("content", textContentHints) { value(contentString) }
                key("__DO_NOT_CARE__", Hints.su) { value("__DO_NOT_CARE__") }
            }
            FileClassification.Directory -> {
                key("_type", Hints.u) { value("dir") }
                val ls = directoryListing(path)
                ls.result?.forEach { childPath ->
                    val name = childPath.last()
                    key(name.fullName) childEntryValue@{
                        destructureFilesOnto(childPath, this@childEntryValue)
                    }
                }
            }
        }
    }
}
