package lang.temper.interp.importExport

import lang.temper.common.subListToEnd
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.bannedPathSegmentNames
import lang.temper.value.PartialResult
import lang.temper.value.TList
import lang.temper.value.TString
import lang.temper.value.Value

internal const val FILE_PATH_ISDIR_TAG = "d"
private const val FILE_PATH_ISFILE_TAG = "f"

fun (FilePath).toValue(): Value<*> {
    return Value(
        buildList {
            add(Value(if (isDir) FILE_PATH_ISDIR_TAG else FILE_PATH_ISFILE_TAG, TString))
            segments.mapTo(this) {
                Value(it.fullName, TString)
            }
        },
        TList,
    )
}

/** Reverse of [FilePath.toValue] */
internal fun (PartialResult).toFilePath(): FilePath? {
    val parts = TList.unpackOrNull(this as? Value<*>)
    if (parts.isNullOrEmpty()) { return null }
    val isDir = (TString.unpackOrNull(parts[0]) ?: return null) == FILE_PATH_ISDIR_TAG
    val segmentList = parts.subListToEnd(1)
    val segments = segmentList.map {
        val segmentText = TString.unpackOrNull(it) ?: return@toFilePath null
        if (segmentText in bannedPathSegmentNames) {
            return@toFilePath null
        }
        FilePathSegment(segmentText)
    }
    return FilePath(segments, isDir)
}
