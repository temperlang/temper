package lang.temper.fs

import net.rubygrapefruit.platform.NativeException
import net.rubygrapefruit.platform.file.FileEvents
import net.rubygrapefruit.platform.file.FileWatchEvent
import net.rubygrapefruit.platform.file.FileWatchEvent.Handler
import net.rubygrapefruit.platform.internal.jni.AbstractFileEventFunctions
import net.rubygrapefruit.platform.internal.jni.NativeLogger
import java.io.File
import java.io.IOException
import java.nio.file.ClosedWatchServiceException
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.Watchable
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

class FileEventsWatchService(type: Class<out AbstractFileEventFunctions<*>>) : WatchService {
    private val eventFunctions = FileEvents.get(type)
    private val events = LinkedBlockingDeque<FileWatchEvent>()
    private val watcherBuilder = eventFunctions.newWatcher(events)
    private val watcher = watcherBuilder.start()
    private var pending: WatchKey? = null

    @Volatile
    private var closed = false

    init {
        // We get useless logs even at "severe" level from here, such as:
        // SEVERE: Caught exception: Couldn't add watch, stat failed, error = 2: ...
        //     .../temper.out/js/.deleting9434140723220599686
        Logger.getLogger(NativeLogger::class.qualifiedName).level = Level.OFF
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            watcher.shutdown()
            // And interrupt any waiters.
            for (thread in waitingThreads) {
                thread.interrupt()
            }
            waitingThreads.clear()
        }
    }

    override fun poll(): WatchKey? = process {
        events.poll()
    }

    override fun poll(timeout: Long, unit: TimeUnit): WatchKey? = process {
        events.poll(timeout, unit)
    }

    override fun take(): WatchKey = process {
        events.take()
    }!!

    @Synchronized
    fun watchable(path: Path): Watchable {
        val file = path.toFile().canonicalFile
        return watchables.getOrPut(file) { FileEventsWatchable(file) }
    }

    @Synchronized
    private fun cancel(watchKey: FileEventsWatchKey) {
        val old = watchKeysRegistered.remove(watchKey.watchable.file)
        if (old != null) {
            watcher.stopWatching(listOf(watchKey.watchable.file))
            // And invalidate the cache for anything referecing this.
            // If inefficient, we at least don't expect to cancel watches often.
            val invalidCacheFiles = watchKeysCached.entries.mapNotNull { (file, cachedKey) ->
                when (cachedKey) {
                    watchKey -> file
                    else -> null
                }
            }
            for (file in invalidCacheFiles) {
                watchKeysCached.remove(file)
            }
        }
    }

    @Synchronized
    private fun isValid(watchKey: FileEventsWatchKey): Boolean {
        return watchKey.watchable.file in watchKeysRegistered && !closed
    }

    private val waitingThreads = mutableSetOf<Thread>()

    private fun process(watch: () -> FileWatchEvent?): WatchKey? {
        if (closed) {
            throw ClosedWatchServiceException()
        }
        synchronized(this) {
            waitingThreads.add(Thread.currentThread())
        }
        try {
            // Get a new event only if we don't have one pending.
            val watchKey = when (pending) {
                // Don't synchronize on watch, since we also need to be able to interrupt it.
                null -> handleEvent(watch())
                else -> synchronized(this) { pending.also { pending = null } }
            }
            // However we got one, see if we have other events already available.
            // This has turned out followup events in unit tests despite no extra delay.
            while (true) {
                if (closed) {
                    throw ClosedWatchServiceException()
                }
                val nextEvent = events.poll() ?: break
                val nextKey = handleEvent(nextEvent)
                if (nextKey !== watchKey) {
                    pending = nextKey
                    break
                }
            }
            return watchKey
        } finally {
            synchronized(this) {
                waitingThreads.remove(Thread.currentThread())
            }
        }
    }

    @Synchronized
    private fun handleEvent(event: FileWatchEvent?): WatchKey? {
        // See https://github.com/gradle/native-platform/blob/015924cd57d19370b062bd3e957b6721f4b5a8a6/file-events/src/main/java/net/rubygrapefruit/platform/file/FileWatchEvent.java
        var result: WatchKey? = null
        event?.handleEvent(object : Handler {
            override fun handleChangeEvent(type: FileWatchEvent.ChangeType, absolutePath: String) {
                val (file, watchKey) = watchKeyFor(absolutePath)
                when (type) {
                    FileWatchEvent.ChangeType.CREATED ->
                        watchKey.addPathEvent(StandardWatchEventKinds.ENTRY_CREATE, file)

                    FileWatchEvent.ChangeType.REMOVED ->
                        watchKey.addPathEvent(StandardWatchEventKinds.ENTRY_DELETE, file)

                    FileWatchEvent.ChangeType.MODIFIED,
                    FileWatchEvent.ChangeType.INVALIDATED, // TODO Or delete for this???
                    -> watchKey.addPathEvent(StandardWatchEventKinds.ENTRY_MODIFY, file)
                }
                result = watchKey
            }

            override fun handleUnknownEvent(absolutePath: String) {
                val (file, watchKey) = watchKeyFor(absolutePath)
                watchKey.addPathEvent(StandardWatchEventKinds.ENTRY_MODIFY, file)
                result = watchKey
            }

            override fun handleOverflow(type: FileWatchEvent.OverflowType, absolutePath: String?) {
                when (absolutePath) {
                    null -> {
                        // TODO Log?
                    }

                    else -> {
                        val (file, watchKey) = watchKeyFor(absolutePath)
                        watchKey.addEvent(StandardWatchEventKinds.OVERFLOW, file)
                        result = watchKey
                    }
                }
            }

            override fun handleFailure(failure: Throwable) {
                // TODO Log? Stop watching???
                // See https://github.com/gradle/gradle/blob/c9ffdb524e1316f9f9a6a994c6e299f4d43745f5/subprojects/file-watching/src/main/java/org/gradle/internal/watch/registry/impl/DefaultFileWatcherRegistry.java#L115
            }

            override fun handleTerminated() {
                // TODO Log? Stop watching?
                // See https://github.com/gradle/gradle/blob/c9ffdb524e1316f9f9a6a994c6e299f4d43745f5/subprojects/file-watching/src/main/java/org/gradle/internal/watch/registry/impl/DefaultFileWatcherRegistry.java#L120C43-L120C43
            }
        })
        return result
    }

    @Synchronized
    private fun register(watchable: FileEventsWatchable): WatchKey {
        try {
            return watchKeysRegistered.getOrPut(watchable.file) {
                watcher.startWatching(listOf(watchable.file))
                // Also put this in the cached map.
                val watchKey = FileEventsWatchKey(watchable)
                watchKeysCached[watchable.file] = watchKey
                watchKey
            }
        } catch (exception: NativeException) {
            throw IOException(exception)
        }
    }

    @Synchronized
    private fun watchKeyFor(absolutePath: String): Pair<File, FileEventsWatchKey> {
        val file = File(absolutePath).canonicalFile
        var next = file
        while (true) {
            watchKeysCached[next]?.let { watchKey ->
                if (next !== file) {
                    // Cache for next time in case we had to go up for something that supports recursive watch.
                    // And to reduce the cache size somewhat, only cache for dirs.
                    val dir = when (file.isFile) {
                        true -> file.parentFile
                        false -> file
                    }
                    watchKeysCached[dir] = watchKey
                }
                return@watchKeyFor file to watchKey
            }
            next = next.parentFile ?: break
        }
        // Can this ever happen?
        error("No watch key for $file")
    }

    private val watchables = mutableMapOf<File, FileEventsWatchable>()
    private val watchKeysCached = mutableMapOf<File, FileEventsWatchKey>()
    private val watchKeysRegistered = mutableMapOf<File, FileEventsWatchKey>()

    private data class FileEventsWatchable(val file: File) : Watchable {
        override fun register(
            watcher: WatchService,
            events: Array<out WatchEvent.Kind<*>>,
            vararg modifiers: WatchEvent.Modifier,
        ): WatchKey {
            return (watcher as FileEventsWatchService).register(this)
        }

        override fun register(watcher: WatchService, vararg events: WatchEvent.Kind<*>): WatchKey {
            return register(watcher, events)
        }
    }

    private inner class FileEventsWatchKey(val watchable: FileEventsWatchable) : WatchKey {
        private val events = mutableListOf<SimpleWatchEvent<*>>()

        override fun isValid() = this@FileEventsWatchService.isValid(this)

        @Synchronized
        override fun pollEvents(): List<WatchEvent<*>> {
            val result = events.toList()
            events.clear()
            return result
        }

        override fun reset(): Boolean {
            // TODO Do we care about resetting anything?
            return isValid
        }

        @Synchronized
        override fun cancel() {
            this@FileEventsWatchService.cancel(this)
        }

        override fun watchable() = watchable

        @Synchronized
        fun addEvent(kind: WatchEvent.Kind<Any>, file: File) {
            val path = file.toPath()
            events.lastOrNull()?.let { last ->
                if (last.kind == kind && last.context == path) {
                    last.increment()
                    return@addEvent
                }
            }
            // Last doesn't match, so add a new event.
            events.add(SimpleWatchEvent(kind, path))
        }

        /** Just a convenience for casting. */
        fun addPathEvent(kind: WatchEvent.Kind<Path>, file: File) {
            @Suppress("UNCHECKED_CAST")
            addEvent(kind as WatchEvent.Kind<Any>, file)
        }
    }
}

private class SimpleWatchEvent<T>(val kind: WatchEvent.Kind<T>, val context: T) : WatchEvent<T> {
    override fun kind() = kind

    override fun context() = context

    private var count = 1
    override fun count() = count
    fun increment() {
        count += 1
    }
}
