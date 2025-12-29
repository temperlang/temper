package lang.temper.fs

import lang.temper.common.Flushable
import lang.temper.common.MimeType
import lang.temper.common.RFailure
import lang.temper.common.RSuccess
import lang.temper.common.WrappedByteArray
import lang.temper.common.currents.CancelGroup
import lang.temper.common.ignore
import lang.temper.common.isNotEmpty
import lang.temper.common.unmodifiableView
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.asFilePath
import lang.temper.log.last
import lang.temper.log.plus
import java.io.IOException
import java.nio.charset.Charset

/** A tree representation of built output files. */
sealed interface OutFile {
    val root: OutputRoot

    /** null for the root */
    val name: FilePathSegment?
    val parent: OutFile?

    /** The path for this outfile from the root node. */
    val path: FilePath

    /**
     * Like path, except this is helpful if you aren't using the root. Null will find the root.
     */
    fun pathFrom(dir: OutDir?): FilePath = FilePath(
        ancestorsOf(this).takeWhile { it != dir }.mapNotNull { it.name }.toListReversed(),
        isDir = this is OutDir,
    )

    /** whether the directory is an ancestor of this */
    fun hasAncestor(dir: OutDir): Boolean = ancestorsOf(this).any { it == dir }
}

/** An output file that is not the root */
sealed interface OutSubFile : OutFile {
    override val name: FilePathSegment
    override val parent: OutFile
}

private fun <T> Sequence<T>.toListReversed(): List<T> = this.toMutableList().also { it.reverse() }

/** An output file that can contain other files: a directory or root. */
sealed class OutDir : OutFile {
    private val _files = mutableListOf<OutSubFile>()

    val files: List<OutSubFile> get() {
        updateFilesList() // Sync with file system on fetch
        return _files.unmodifiableView()
    }

    /** Copy the child files into a container. */
    @Synchronized
    fun listFilesTo(target: MutableCollection<OutSubFile>) {
        updateFilesList()
        target.addAll(_files)
    }

    /**
     * Look up a path to a file or directory.
     * Returns null if the path is missing or if the path's `isDir` attribute does not match the class found.
     */
    @Synchronized
    fun find(path: FilePath): OutFile? {
        var node: OutFile = this
        for (seg in path.segments) {
            node = (node as? OutDir)?.entryNamed(seg) ?: return null
        }
        return if (path.isDir == node is OutDir) { node } else { null }
    }

    @Synchronized
    private fun <T : OutSubFile> asChild(newFile: T): T {
        _files.add(newFile)
        return newFile
    }

    @Synchronized
    fun makeDir(name: FilePathSegment): OutSubDir {
        return existingFile { it.name == name && it is OutSubDir } as OutSubDir?
            ?: run {
                root.fs.ensureDir(path.resolve(name, isDir = true))
                makeDirNotPresent(name)
            }
    }

    private fun makeDirNotPresent(name: FilePathSegment): OutSubDir =
        asChild(OutSubDir(this, name))

    @Synchronized
    fun makeDirs(name: FilePath): OutDir {
        if (name.segments.isEmpty()) return this
        return makeDir(name.segments.first()).makeDirs(name.copy(name.segments.drop(1)))
    }

    @Synchronized
    fun makeRegularFile(name: FilePathSegment, mimeType: MimeType? = null) =
        // TODO: Fail gracefully when there is an existing directory with that name
        existingFile { it.name == name && it is OutRegularFile } as OutRegularFile?
            ?: makeRegularNotPresent(name, mimeType)

    private fun makeRegularNotPresent(name: FilePathSegment, mimeType: MimeType? = null) =
        asChild(OutRegularFile(this, name, mimeType))

    fun entryNamed(name: FilePathSegment): OutSubFile? = existingFile { it.name == name }

    fun hasEntryNamed(name: FilePathSegment): Boolean = entryNamed(name) != null

    @Synchronized
    private fun existingFile(predicate: (OutSubFile) -> Boolean): OutSubFile? =
        _files.find(predicate) ?: updateFilesList(predicate)

    /** Queries the underlying file system to add any missing entries to the file list. */
    @Synchronized
    private fun updateFilesList(
        /** The first newly added entry that matches the predicate is returned or null if none */
        predicate: (OutSubFile) -> Boolean = { false },
    ): OutSubFile? {
        var result: OutSubFile? = null
        val fs = root.fs
        when (val ls = fs.directoryListing(this.path)) {
            is RFailure -> when (fs.classify(this.path)) {
                FileClassification.DoesNotExist -> {}
                FileClassification.File,
                FileClassification.Directory,
                ->
                    fs.reportInternalIoException(ls.failure)
            }
            is RSuccess -> {
                val namesBefore = buildSet { _files.mapTo(this) { it.name } }
                val namesAfter = mutableSetOf<FilePathSegment>()
                for (pathFromLs in ls.result) {
                    val name = pathFromLs.last()
                    namesAfter.add(name)
                    if (name !in namesBefore) {
                        // Create a new file
                        val newSubFile = when (fs.classify(pathFromLs)) {
                            FileClassification.DoesNotExist -> null
                            FileClassification.File ->
                                makeRegularNotPresent(name, fs.readMimeType(pathFromLs))
                            FileClassification.Directory ->
                                makeDirNotPresent(name)
                        }
                        if (newSubFile != null && result == null && predicate(newSubFile)) {
                            result = newSubFile
                        }
                    }
                }
                if (namesAfter.size < _files.size) {
                    // Some got deleted
                    for (i in namesBefore.indices.reversed()) {
                        if (_files[i].name !in namesAfter) {
                            _files.removeAt(i)
                        }
                    }
                }
            }
        }

        return result
    }

    fun byteContentOf(path: FilePath): WrappedByteArray? {
        val fullPath = this.path.resolve(path.segments, isDir = path.isDir)
        return root.fs.readBinaryFileContent(fullPath).await().result
    }

    fun textContentOf(path: FilePath): String? = byteContentOf(path)?.decodeToString()

    fun hasContent(path: FilePath): Boolean {
        return when (root.fs.classify(this.path + path)) {
            FileClassification.DoesNotExist -> false
            FileClassification.File -> true
            FileClassification.Directory -> false
        }
    }

    fun systemAccess(cancelGroup: CancelGroup): AsyncSystemAccess =
        root.fs.systemAccess(path, cancelGroup)
    fun systemReadAccess(cancelGroup: CancelGroup): AsyncSystemReadAccess =
        root.fs.systemReadAccess(path, cancelGroup)
}

/** An output root can contain directories and specifies how content is stored. */
class OutputRoot(
    val fs: WritableFileSystem,
) : OutDir() {
    override val name: FilePathSegment? get() = null
    override val parent get() = null
    override val path: FilePath get() = FilePath.emptyPath

    override val root: OutputRoot get() = this

    internal fun write(
        path: FilePath,
        mimeType: MimeType?,
        contentSupplier: (ByteSink) -> Unit,
        onCompletion: (ok: Boolean) -> Unit,
    ) {
        val ok = try {
            fs.writeAsync(path, mimeType).use { byteSink ->
                contentSupplier(byteSink)
            }
            true
        } catch (ex: IOException) {
            ignore(ex)
            false
        }
        onCompletion(ok)
    }
}

fun OutDir.leaves(): List<OutSubFile> = buildList {
    val deque = ArrayDeque(files)
    while (deque.isNotEmpty()) {
        when (val file = deque.removeFirst()) {
            is OutDir -> deque.addAll(file.files)
            else -> add(file)
        }
    }
}

/** An output directory may contain directories and files. */
class OutSubDir(
    override val parent: OutFile,
    override val name: FilePathSegment,
) : OutDir(), OutSubFile {
    override val root: OutputRoot = parent.root

    private val _path = lazy { pathFor(this) }
    override val path: FilePath get() = _path.value
}

/** A regular file does not contain or reference other files and has binary&|textual content. */
class OutRegularFile(
    override val parent: OutFile,
    override val name: FilePathSegment,
    val mimeType: MimeType?,
) : OutSubFile {
    override val root: OutputRoot = parent.root
    val dir: OutDir get() = parent as OutDir

    private val _path = lazy { pathFor(this) }
    override val path: FilePath get() = _path.value

    /** read the byte content of this file */
    fun byteContent(): WrappedByteArray = (parent as OutDir).byteContentOf(name.asFilePath())!!

    /** read the byte content of this file */
    fun textContent(): String? = (parent as OutDir).textContentOf(name.asFilePath())!!

    /**
     * @param contentSupplier receives a byte sink which is open for the duration of the call.
     * @param onCompletion called on finish of writing passing false if there was an error.
     */
    fun supplyByteContent(
        contentSupplier: (ByteSink) -> Unit,
        onCompletion: (ok: Boolean) -> Unit,
    ) {
        root.write(
            path = path,
            mimeType = mimeType,
            contentSupplier = contentSupplier,
            onCompletion = onCompletion,
        )
    }

    /**
     * @param contentSupplier receives a byte sink which is open for the duration of the call.
     * @param onCompletion called on finish of writing passing false if there was an error.
     */
    fun supplyTextContent(
        contentSupplier: (Appendable) -> Unit,
        onCompletion: (ok: Boolean) -> Unit,
    ) {
        fun byteSupplier(out: ByteSink) {
            val charOut = out.asAppendable()
            contentSupplier(charOut)
            charOut.flush()
        }
        root.write(
            path = path,
            mimeType = mimeType,
            contentSupplier = ::byteSupplier,
            onCompletion = onCompletion,
        )
    }

    override fun toString(): String {
        return "$path $mimeType"
    }
}

/** A sequence starting at this node and walking up through the parent nodes. */
private fun ancestorsOf(f: OutFile): Sequence<OutFile> = generateSequence(f) { it.parent }

private fun pathFor(f: OutFile) =
    FilePath(ancestorsOf(f).mapNotNull { it.name }.toListReversed(), isDir = f is OutDir)

@Suppress(
    "EmptyDefaultConstructor", // Implicit constructors are not expected.
)
expect abstract class ByteSink() : AutoCloseable, Flushable {
    open fun write(bytes: ByteArray, off: Int, len: Int)
    open fun write(bytes: ByteArray)
    fun write(p0: Int)
}

expect class CharSink : Appendable, AutoCloseable, Flushable

expect fun ByteSink.asAppendable(charset: Charset = Charsets.UTF_8): CharSink
