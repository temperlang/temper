package lang.temper.fs

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.selects.select
import lang.temper.common.MimeType
import lang.temper.common.OpenOrClosed
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.WrappedByteArray
import lang.temper.common.currents.RFuture
import lang.temper.log.FilePath
import java.io.IOException

/**
 * Layers two file systems such that files in [front] override those in [back].
 * Currently, layer [front] can add files but can't remove any from [back].
 * This is designed for only two layers because that's the expected use case. Chain for more layers.
 */
class LayeredFileSystem(val front: FileSystem, val back: FileSystem) : FileSystem {
    override val openOrClosed: OpenOrClosed
        get() = when (val result = front.openOrClosed) {
            OpenOrClosed.Closed -> back.openOrClosed
            else -> result
        }

    override fun classify(filePath: FilePath) =
        when (val result = front.classify(filePath)) {
            FileClassification.DoesNotExist -> back.classify(filePath)
            else -> result
        }

    override fun directoryListing(dirPath: FilePath): RResult<List<FilePath>, IOException> {
        val backResult = back.directoryListing(dirPath)
        val frontResult = front.directoryListing(dirPath)
        return when {
            backResult is RSuccess && frontResult is RSuccess -> RSuccess(
                buildSet {
                    addAll(backResult.result)
                    addAll(frontResult.result)
                }.toList(),
            )
            backResult is RSuccess -> backResult
            else -> frontResult
        }
    }

    override fun textualFileContent(
        filePath: FilePath,
    ): RResult<CharSequence, IOException> =
        // This means that if Front has content that does not decode to a string,
        // but back does we will instead return the failure from front.
        // That is desired.  A file in front masks any file in back.
        fsFor(filePath).textualFileContent(filePath)

    override fun readBinaryFileContentSync(
        filePath: FilePath,
    ): RResult<WrappedByteArray, IOException> =
        fsFor(filePath).readBinaryFileContentSync(filePath)

    override fun readBinaryFileContent(filePath: FilePath): RFuture<WrappedByteArray, IOException> =
        fsFor(filePath).readBinaryFileContent(filePath)

    override fun readMimeType(filePath: FilePath): MimeType? =
        fsFor(filePath).readMimeType(filePath)

    override fun createWatchService(root: FilePath): RResult<FileWatchService, IOException> {
        // Use layered if both exist or the non-null option if any.
        val watchA = front.createWatchService(root)
        val watchB = back.createWatchService(root)
        return when {
            watchA is RSuccess && watchB is RSuccess ->
                RSuccess(LayeredWatchService(watchA.result, watchB.result))
            watchA is RSuccess -> watchA
            else -> watchB
        }
    }

    override fun close() {
        try {
            front.close()
        } finally {
            back.close()
        }
    }

    private fun fsFor(filePath: FilePath) = when (front.classify(filePath)) {
        FileClassification.DoesNotExist -> back
        else -> front
    }

    private inner class LayeredWatchService(
        val frontWatch: FileWatchService,
        val backWatch: FileWatchService,
    ) : FileWatchService {
        // Exception handler notion copied from [StitchedFileSystem].
        private val coroutineScope = CoroutineScope(Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })

        override val openOrClosed: OpenOrClosed
            get() = when (val result = frontWatch.openOrClosed) {
                OpenOrClosed.Closed -> backWatch.openOrClosed
                else -> result
            }

        @OptIn(ExperimentalCoroutinesApi::class)
        override val changes = coroutineScope.produce {
            while (true) {
                val result = select {
                    for ((watch, other) in listOf(frontWatch to back, backWatch to front)) {
                        watch.changes.onReceive { mapChanges(it, other = other) }
                    }
                }
                send(result)
            }
        }

        override fun close() {
            try {
                frontWatch.close()
            } finally {
                backWatch.close()
            }
        }

        /**
         * Translate creates and deletes to edits if the other half makes up for it.
         * Because there's no overall synchronization across layers, this might be less than ideal:
         *
         * - Delete in each might result in two delete messages.
         * - Delete in one followed by add in the other might be seen as an edit, losing the history.
         *
         * However, this should at least avoid cases where a deletion in one gets sent out while the
         * file still exists in the other.
         */
        private fun mapChanges(changes: List<FileChange>, other: FileSystem): List<FileChange> {
            return changes.map {
                when (it.fileChangeKind) {
                    FileChange.Kind.Created, FileChange.Kind.Deleted -> {
                        when (other.classify(it.filePath)) {
                            FileClassification.DoesNotExist -> it
                            else -> it.copy(fileChangeKind = FileChange.Kind.Edited)
                        }
                    }
                    else -> it
                }
            }
        }
    }
}
