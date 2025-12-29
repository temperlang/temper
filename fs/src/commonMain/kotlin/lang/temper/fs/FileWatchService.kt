package lang.temper.fs

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.getOrElse
import kotlinx.coroutines.withTimeoutOrNull
import lang.temper.common.OpenOrClosed
import lang.temper.log.FilePath

interface FileWatchService : AutoCloseable {
    val openOrClosed: OpenOrClosed

    val changes: ReceiveChannel<List<FileChange>>
}

/**
 * @param timeoutMillis
 *     The number of milliseconds to wait for some result or null to wait indefinitely.
 */
suspend fun FileWatchService.getChanges(timeoutMillis: Int? = null) = when (timeoutMillis) {
    null -> changes.receive()
    else -> withTimeoutOrNull(timeoutMillis.toLong()) {
        changes.receive()
    } ?: emptyList()
}

fun FileWatchService.pollChanges() = changes.tryReceive().getOrElse { emptyList() }

data class FileChange(
    val filePath: FilePath,
    val fileChangeKind: Kind,
) {
    enum class Kind {
        Created,
        Edited,
        Deleted,
    }
}
