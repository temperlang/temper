package lang.temper.compile.fetch

import lang.temper.common.Console
import lang.temper.common.Log
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/** Locks in process and also in file system, so singleton works well. */
object DirLocker {
    fun <T> lock(
        path: Path,
        console: Console = lang.temper.common.console,
        action: () -> T,
    ): T {
        // Double-check the dir already exists before trying to access it.
        Files.createDirectories(path)
        val realPath = path.toRealPath().toString()
        val lock = lockMap.computeIfAbsent(realPath) { ReentrantLock() }
        if (lock.isHeldByCurrentThread) {
            return action()
        }
        lock.lock()
        try {
            console.log(Log.Fine) { "Locked internal $realPath" }
            val lockFile = path.resolve(LOCK_FILE_NAME).toFile()
            FileOutputStream(lockFile).use { stream ->
                stream.channel.use { channel ->
                    val fileLock = channel.lock()
                    try {
                        console.log(Log.Fine) { "Locked external $realPath" }
                        return@lock action()
                    } finally {
                        fileLock.release()
                        console.log(Log.Fine) { "Released external $realPath" }
                    }
                }
            }
        } finally {
            lock.unlock()
            console.log(Log.Fine) { "Released internal $realPath" }
        }
    }

    private val lockMap = ConcurrentHashMap<String, ReentrantLock>()
}

interface DirLockable {
    val console: Console
    val path: Path

    fun <T> lock(action: () -> T): T = DirLocker.lock(path, console, action)
}

const val LOCK_FILE_NAME = "dir.lock"
