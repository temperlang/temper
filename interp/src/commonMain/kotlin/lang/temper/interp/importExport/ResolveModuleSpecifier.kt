package lang.temper.interp.importExport

import lang.temper.common.Log
import lang.temper.common.compatRemoveLast
import lang.temper.common.toStringViaBuilder
import lang.temper.common.urlDecode
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.joinPathTo
import lang.temper.log.FilePathSegment
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.ParentPseudoFilePathSegment
import lang.temper.log.Position
import lang.temper.log.SameDirPseudoFilePathSegment
import lang.temper.log.UNIX_FILE_SEGMENT_SEPARATOR

internal fun resolveModuleSpecifier(
    moduleSpecifier: String,
    basePath: FilePath,
    pos: Position,
    logSink: LogSink,
): FilePath? {
    // Decompose the module specifier string into parts
    val isAbsolute = moduleSpecifier.startsWith(UNIX_FILE_SEGMENT_SEPARATOR)
    val isDir = moduleSpecifier.endsWith(UNIX_FILE_SEGMENT_SEPARATOR)
    val splitSegments =
        moduleSpecifier.split(UNIX_FILE_SEGMENT_SEPARATOR).mapNotNull {
            when (val segmentName = urlDecode(it)) {
                null -> {
                    logSink.log(
                        level = Log.Error,
                        template = MessageTemplate.MalformedImportPathSegmentUtf8,
                        pos = pos,
                        values = listOf(it, moduleSpecifier),
                    )
                    return null
                }
                "" -> null
                ParentPseudoFilePathSegment.fullName -> ParentPseudoFilePathSegment
                SameDirPseudoFilePathSegment.fullName -> SameDirPseudoFilePathSegment
                else -> FilePathSegment(segmentName)
            }
        }
    val allSegments = when {
        isAbsolute -> splitSegments
        basePath.isDir -> basePath.segments + splitSegments
        else -> basePath.dirName().segments + splitSegments
    }

    //  '.' or '..'
    val filePathSegments = mutableListOf<FilePathSegment>()
    for (segment in allSegments) {
        when (segment) {
            ParentPseudoFilePathSegment -> {
                if (filePathSegments.isEmpty()) {
                    logSink.log(
                        level = Log.Error,
                        template = MessageTemplate.ImportPathHasTooManyParentParts,
                        pos = pos,
                        values = listOf(
                            toStringViaBuilder {
                                allSegments.joinPathTo(isDir = isDir, sb = it)
                            },
                        ),
                    )
                    return null
                }
                filePathSegments.compatRemoveLast()
            }
            SameDirPseudoFilePathSegment -> Unit
            is FilePathSegment -> {
                filePathSegments.add(segment)
            }
        }
    }

    return FilePath(filePathSegments.toList(), isDir)
}
