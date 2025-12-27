package lang.temper.be.syncstaging

import lang.temper.be.Backend
import lang.temper.be.BackendInfo
import lang.temper.be.SiblingData
import lang.temper.common.Log
import lang.temper.common.currents.CancelGroup
import lang.temper.common.currents.RFuture
import lang.temper.common.currents.join
import lang.temper.log.MessageTemplate
import lang.temper.log.Position

fun <BACKEND : Backend<out BACKEND>> applyBackendsSynchronously(
    cancelGroup: CancelGroup,
    backends: List<Backend<out BACKEND>>,
    /**
     * Called occasionally to see if processing should be aborted.  May throw if caller catches.
     * If processing is about to wait on a future, passes that future which may be cancelled by
     * caller in addition or instead of throwing on cancellation.
     */
    cancelCheck: ((RFuture<*, *>?) -> Unit) = { cancelGroup.requireNotCancelled() },
): List<BackendInfo<Backend<out BACKEND>>> {
    val backendsByRoot = backends.associateBy {
        it.setup(cancelGroup)
        it.libraryConfigurations.currentLibraryConfiguration.libraryRoot
    }

    val infos = backends.map { BackendInfo(it) }

    val preAnalysisData = SiblingData(
        backendsByRoot,
        infos.associate {
            it.libraryRoot to Unit
        },
    )
    for (info in infos) {
        cancelCheck(null)
        info.backend.preAnalysis(preAnalysisData)
    }

    for (info in infos) {
        cancelCheck(null)
        info.tmpL = info.backend.tentativeTmpL()
    }

    val finishData = SiblingData(
        backendsByRoot,
        infos.associate {
            it.libraryRoot to it.tmpL
        },
    )
    for (info in infos) {
        cancelCheck(null)
        info.tmpL = info.backend.finishTmpL(info.tmpL, finishData)
    }

    val keepFileAcceptFutures = infos.map { info ->
        val backend = info.backend
        backend.loadKeepFiles().then("Accept keep file data") { keepFileResult ->
            keepFileResult.result?.let { backend.acceptKeepFiles(it) }
            keepFileResult
        }
    }
    val joinedKeepFileAcceptFutures = cancelGroup.join(keepFileAcceptFutures)
    cancelCheck(joinedKeepFileAcceptFutures)
    joinedKeepFileAcceptFutures.await()

    for (info in infos) {
        cancelCheck(null)
        // TODO How to handle one backend crashing/throwing here among several?
        info.outputFiles = info.backend.translate(info.tmpL)
    }

    for (info in infos) {
        cancelCheck(null)
        info.keepFiles = info.backend.saveKeepFiles()
    }

    val collateData = SiblingData(
        backendsByRoot,
        infos.associate {
            it.libraryRoot to it.outputFiles
        },
    )
    for (info in infos) {
        cancelCheck(null)
        info.outputFiles = info.backend.collate(info.outputFiles, collateData)
    }

    for (info in infos) {
        cancelCheck(null)
        info.outputFiles = info.backend.preWrite(info.outputFiles)
    }

    val outputFileFutures = infos.map { info ->
        info.backend.writeOutputFiles(info.outputFiles)
    }
    val joinedOutputFileFutures = cancelGroup.join(outputFileFutures)
    cancelCheck(joinedOutputFileFutures)
    joinedOutputFileFutures.await()

    val keepFileFutures = infos.map { info ->
        info.backend.writeKeepFiles(info.keepFiles)
    }
    val joinedKeepFileFutures = cancelGroup.join(keepFileFutures)
    cancelCheck(joinedKeepFileFutures)
    joinedKeepFileFutures.await()

    val postFileFutures = infos.map { info ->
        info.backend.postWrite(info.outputFiles, info.keepFiles)
    }
    val allPostFileFutures = cancelGroup.join(postFileFutures)
    cancelCheck(allPostFileFutures)
    allPostFileFutures.await()

    for (info in infos) {
        info.backend.logSink.log(
            Log.Fine,
            MessageTemplate.TranslationReady,
            Position(info.libraryRoot, 0, 0),
            listOf(
                info.backend.backendId,
                info.backend.libraryConfigurations.currentLibraryConfiguration.libraryName,
            ),
        )
    }

    return infos.toList()
}
