package lang.temper.fs

import lang.temper.common.MimeType
import lang.temper.common.WrappedByteArray
import lang.temper.common.currents.CancelGroup
import lang.temper.common.currents.RFuture
import lang.temper.log.FilePath
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/** An abstraction that allows limited access to write files and run shell commands. */
interface AsyncSystemAccess {
    val cancelGroup: CancelGroup

    /**
     * The directory where this will situate files.
     * If this is `path/to/temper.out/<library-name>/<backend-id>/` then
     * a call to [buildFile][AsyncSystemAccess.buildFile]\("foo.bar", ...\)
     * will build `path/to/temper.out/<library-name>/<backend-id>/foo.bar`.
     */
    val pathToFileCreatorRoot: FilePath

    /** Prepares a file builder for the specified file. */
    fun buildFile(
        filePath: FilePath,
        mimeType: MimeType?,
    ): FileBuilder

    /**
     * Invokes [build] to initialize a child process via
     * [arg][ChildProcessBuilder.arg],
     * [arg][ChildProcessBuilder.env],
     * [arg][ChildProcessBuilder.cd] and related methods
     * to initializes the child process state.
     *
     * If the system access does not support child process
     * creation, returns `null` without calling [build].
     */
    fun buildChildProcess(
        command: String,
        build: ChildProcessBuilder.() -> Unit,
    ): PendingChildProcess?

    /**
     * If applicable, provide an absolute file system path, else null.
     * Similar to [lang.temper.be.cli.CliEnv.envPath].
     */
    fun envPath(path: FilePath): String? = null
}

/** Extends [AsyncSystemAccess] to read existing files. */
interface AsyncSystemReadAccess : AsyncSystemAccess {
    /** Prepares a file reader for the specified file. */
    fun fileReader(
        filePath: FilePath,
    ): FileReader
}

interface FileBuilder {
    /** Marks the file executable */
    fun executable(): FileBuilder

    /**
     * Starts building the file with the given textual content.
     *
     * @return completes when the content is written or an error prevents that.
     */
    fun content(textContent: String, charset: Charset = Charsets.UTF_8): RFuture<Unit, IOException> =
        content(textContent.toByteArray(charset = charset))

    /**
     * Starts building the file with the given byte content.
     *
     * @return completes when the content is written or an error prevents that.
     */
    fun content(bytes: ByteArray): RFuture<Unit, IOException> = write { sink ->
        sink.write(bytes)
    }

    /**
     * Starts building the file with content to be supplies by [doWrite]
     * via a [ByteSink].  After calling [doWrite], closes the sink so
     * all writing must be done within [doWrite].
     */
    fun write(doWrite: (ByteSink) -> Unit): RFuture<Unit, IOException>
}

interface FileReader {
    /**
     * Starts reading the file to obtain the textual content.
     *
     * @return completes when the content is read or an error prevents that.
     */
    fun textContent(charset: Charset = StandardCharsets.UTF_8): RFuture<String, IOException> =
        binaryContent().then("decode to ${charset.name()}") { bytes ->
            bytes.mapResult { it.decodeToStringWithCharset(charset) }
        }

    /**
     * Starts reading the file to obtain the byte content.
     *
     * @return completes when the content is read or an error prevents that.
     */
    fun binaryContent(): RFuture<WrappedByteArray, IOException>
}
