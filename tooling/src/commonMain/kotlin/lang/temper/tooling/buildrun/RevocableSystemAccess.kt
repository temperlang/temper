package lang.temper.tooling.buildrun

import lang.temper.common.MimeType
import lang.temper.common.WrappedByteArray
import lang.temper.common.currents.CancelGroup
import lang.temper.common.currents.Cancellable
import lang.temper.common.currents.ExecutorService
import lang.temper.common.currents.JoiningCancellable
import lang.temper.common.currents.RFuture
import lang.temper.common.currents.SignalRFuture
import lang.temper.common.currents.UnmanagedFuture
import lang.temper.fs.AsyncSystemAccess
import lang.temper.fs.AsyncSystemReadAccess
import lang.temper.fs.ByteSink
import lang.temper.fs.ChildProcessBuilder
import lang.temper.fs.FileBuilder
import lang.temper.fs.FileReader
import lang.temper.fs.PendingChildProcess
import lang.temper.fs.RunningChildProcess
import lang.temper.log.FilePath
import java.io.IOException
import java.nio.charset.Charset
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

internal open class RevocableSystemAccess(
    protected val underlying: AsyncSystemAccess,
    override val cancelGroup: CancelGroup,
) : AsyncSystemAccess {
    override val pathToFileCreatorRoot: FilePath get() = underlying.pathToFileCreatorRoot

    override fun buildFile(filePath: FilePath, mimeType: MimeType?): FileBuilder {
        cancelGroup.requireNotCancelled()
        val fileBuilder = underlying.buildFile(filePath, mimeType)
        cancelGroup.add(RevocableFileBuilder(fileBuilder, cancelGroup))
        return fileBuilder
    }

    override fun buildChildProcess(command: String, build: ChildProcessBuilder.() -> Unit): PendingChildProcess? {
        if (cancelGroup.isCancelled) {
            return null
        }
        val childProcess = underlying.buildChildProcess(command, build)
            ?: return null
        return RevocablePendingChildProcess(childProcess, cancelGroup)
    }

    override fun envPath(path: FilePath): String? {
        if (cancelGroup.isCancelled) {
            return null
        }
        return underlying.envPath(path)
    }
}

internal class RevocableSystemReadAccess(
    underlying: AsyncSystemReadAccess,
    cancelGroup: CancelGroup,
) : RevocableSystemAccess(underlying, cancelGroup), AsyncSystemReadAccess {
    override fun fileReader(filePath: FilePath): FileReader {
        cancelGroup.requireNotCancelled()
        val reader = (underlying as AsyncSystemReadAccess).fileReader(filePath)
        return RevocableFileReader(reader, cancelGroup)
    }
}

private class RevocableFileBuilder(
    private val underlying: FileBuilder,
    private val cancelGroup: CancelGroup,
) : FileBuilder, Cancellable {
    override fun executable(): FileBuilder {
        cancelGroup.requireNotCancelled()
        underlying.executable()
        return this
    }

    override fun content(textContent: String, charset: Charset): RFuture<Unit, IOException> {
        cancelGroup.requireNotCancelled()
        return underlying.content(textContent, charset).also {
            cancelGroup.add(it)
        }
    }

    override fun content(bytes: ByteArray): RFuture<Unit, IOException> {
        cancelGroup.requireNotCancelled()
        return underlying.content(bytes).also {
            cancelGroup.add(it)
        }
    }

    private var byteSink: ByteSink? = null
    override fun write(doWrite: (ByteSink) -> Unit): RFuture<Unit, IOException> {
        cancelGroup.requireNotCancelled()
        return underlying.write { newByteSink ->
            synchronized(this@RevocableFileBuilder) {
                if (byteSink != null) {
                    throw IOException("Concurrents write to file")
                }
                this@RevocableFileBuilder.byteSink = newByteSink
            }
            doWrite(newByteSink)
        }.also {
            cancelGroup.add(it)
        }
    }

    override fun cancel(mayInterruptIfRunning: Boolean) {
        val byteSink = synchronized(this) {
            val byteSink = this.byteSink
            this.byteSink = null
            byteSink
        }
        byteSink?.close()
    }
}

private class RevocableFileReader(
    private val underlying: FileReader,
    private val cancelGroup: CancelGroup,
) : FileReader {
    override fun textContent(charset: Charset): RFuture<String, IOException> {
        cancelGroup.requireNotCancelled()
        return underlying.textContent(charset).also {
            cancelGroup.add(it)
        }
    }

    override fun binaryContent(): RFuture<WrappedByteArray, IOException> {
        cancelGroup.requireNotCancelled()
        return underlying.binaryContent().also {
            cancelGroup.add(it)
        }
    }
}

private class RevocablePendingChildProcess(
    private val underlying: PendingChildProcess,
    private val cancelGroup: CancelGroup,
) : PendingChildProcess(
    defaultCwd = underlying.defaultCwd,
    cwd = underlying.cwd,
    env = underlying.env,
    command = underlying.command,
    args = underlying.args,
) {
    override fun execute(): RunningChildProcess<Int> {
        cancelGroup.requireNotCancelled()
        return underlying.execute().also { cancelGroup.add(it) }
    }
    override fun executeCapturing(): RunningChildProcess<Pair<Int, String>> {
        cancelGroup.requireNotCancelled()
        return underlying.executeCapturing().also { cancelGroup.add(it) }
    }
}

class BuildRunCancelGroup(override val executorService: ExecutorService) : CancelGroup {
    private val isCancelledBit = AtomicBoolean(false)
    private val cancellableThings = Collections.synchronizedList(mutableListOf<Cancellable>())

    override val isCancelled: Boolean
        get() = isCancelledBit.get()

    override fun requireNotCancelled() {
        if (isCancelled) {
            throw AbortCurrentBuild()
        }
    }

    override fun add(c: Cancellable) {
        synchronized(this) {
            if (isCancelled) {
                c.cancel()
            } else {
                cancellableThings.add(c)
            }
        }
    }

    override fun cancelAll(mayInterruptIfRunning: Boolean): SignalRFuture {
        synchronized(this) {
            isCancelledBit.set(true)
        }
        val futures = mutableListOf<SignalRFuture>()
        while (true) {
            val toCancel: List<Cancellable>
            synchronized(this) {
                toCancel = cancellableThings.toList()
                cancellableThings.clear()
            }
            if (toCancel.isEmpty()) {
                break
            }
            for (c in toCancel) {
                if (c is JoiningCancellable) {
                    futures.add(c.cancelSignalling(mayInterruptIfRunning = mayInterruptIfRunning))
                } else {
                    c.cancel(mayInterruptIfRunning = mayInterruptIfRunning)
                }
            }
        }
        return UnmanagedFuture.join(futures.toList(), executorService)
    }
}
