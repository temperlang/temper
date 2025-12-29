package lang.temper.tooling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import lang.temper.fs.MemoryFileSystem
import lang.temper.log.FilePath
import kotlin.jvm.Synchronized
import kotlin.time.Duration

interface FileState {
    /** Return null for deleted. */
    fun toByteArray(): ByteArray?
}

class LiveFileHandler(val coroutineScope: CoroutineScope, val fileSystem: MemoryFileSystem, val path: FilePath) {
    private var hasChangesMutable = MutableStateFlow(false)
    var hasChanges = hasChangesMutable.asStateFlow()

    @Synchronized
    private fun updateHasChanges(update: Boolean): Boolean {
        return hasChangesMutable.getAndUpdate { update }
    }

    fun flush(prepareAction: () -> Unit): Boolean {
        if (updateHasChanges(false)) {
            prepareAction()
            timer.restart(Duration.ZERO)
            return true
        }
        return false
    }

    fun trigger(delay: Duration) {
        hasChangesMutable.value = true
        timer.restart(delay)
    }

    fun start(state: FileState) = coroutineScope.launch { run(state = state) }

    val closed: Boolean
        get() = timer.closed

    private suspend fun run(state: FileState) {
        coroutineScope.launch { timer.run() }
        while (true) {
            channel.receive()
            updateHasChanges(false)
            when (val bytes = state.toByteArray()) {
                null -> {
                    fileSystem.unlink(path)
                    timer.close()
                    channel.close()
                    break
                }
                else -> {
                    if (fileSystem.write(path, bytes, checkEqual = true)) {
                        // It would be nice to mark pending changes, but we don't track here if there are actual
                        // content changes, and it won't get processed by BuildConductor if not.
                        // TODO Should we also or instead have a restart timer on flush in case of multiple files?
                        fileSystem.flushPendingChanges()
                    }
                }
            }
        }
    }

    private val channel = Channel<Unit>(Channel.UNLIMITED)

    private val timer = RestartTimer(channel = channel, coroutineScope = coroutineScope)
}
