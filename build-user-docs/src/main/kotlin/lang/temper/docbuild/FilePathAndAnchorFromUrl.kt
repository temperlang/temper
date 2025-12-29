package lang.temper.docbuild

import lang.temper.log.FilePathSegment
import lang.temper.log.FilePathSegmentOrPseudoSegment
import lang.temper.log.ParentPseudoFilePathSegment
import lang.temper.log.SameDirPseudoFilePathSegment
import lang.temper.log.bannedPathSegmentNames
import java.net.URI
import java.net.URISyntaxException

internal fun filePathAndAnchorFromUrl(urlStr: String): Pair<List<FilePathSegmentOrPseudoSegment>, String?>? {
    val uri = try {
        URI(urlStr)
    } catch (_: URISyntaxException) {
        return null
    }
    if (uri.scheme != null || uri.authority != null || uri.query != null) { return null }

    val rawPath = uri.rawPath
    val relFilePath = if (rawPath.isEmpty()) {
        emptyList()
    } else {
        uri.rawPath.split('/').map {
            when (it) {
                "." -> SameDirPseudoFilePathSegment
                ".." -> ParentPseudoFilePathSegment
                else -> {
                    val decoded = decodePercentEncodedBytes(it)
                    if (decoded in bannedPathSegmentNames) {
                        return@filePathAndAnchorFromUrl null
                    }
                    FilePathSegment(decoded)
                }
            }
        }
    }

    return relFilePath to uri.fragment
}
