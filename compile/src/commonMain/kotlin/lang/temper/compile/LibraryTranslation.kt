package lang.temper.compile

import lang.temper.be.Dependencies
import lang.temper.log.FilePath
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier

data class LibraryTranslation(
    val backendId: BackendId,
    /** Null when [libraryIds] is empty. */
    val dependencies: Dependencies<*>?,
    val libraryIds: List<Pair<DashedIdentifier, FilePath>>,
)
