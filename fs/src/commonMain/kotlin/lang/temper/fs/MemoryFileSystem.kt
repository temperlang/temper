package lang.temper.fs

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.produce
import lang.temper.common.Either
import lang.temper.common.MimeType
import lang.temper.common.OpenOrClosed
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.WrappedByteArray
import lang.temper.common.commonPrefixLength
import lang.temper.common.currents.CancelGroup
import lang.temper.common.currents.RFuture
import lang.temper.common.currents.UnmanagedFuture
import lang.temper.common.currents.preComputedFuture
import lang.temper.common.currents.preComputedResultFuture
import lang.temper.common.currents.runLater
import lang.temper.common.structure.Hints
import lang.temper.common.structure.PropertySink
import lang.temper.common.structure.StructureContextKey
import lang.temper.common.structure.StructureHint
import lang.temper.common.structure.StructureParser
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.subListToEnd
import lang.temper.common.unmodifiableView
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.last
import lang.temper.log.plus
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException

/**
 * A memory [FileSystem] implementation.
 */
class MemoryFileSystem private constructor(
    rootFromParent: DirectoryOrRoot?,
    mutexFromParent: Any?,
) : Structured, WritableFileSystem {
    val root: DirectoryOrRoot = rootFromParent ?: RootDirectory(this)
    private val mutex: Any = mutexFromParent ?: this
    override var openOrClosed = OpenOrClosed.Open
        private set

    constructor() : this(null, null)

    constructor(parent: MemoryFileSystem, pathToViewRoot: FilePath) : this(
        rootFromParent = parent.ensureDirectory(pathToViewRoot),
        mutexFromParent = parent.mutex,
    )

    // All synchronization goes through this to avoid deadlocks where root and directories
    // under root each have their own locks that are locked in non-obvious orders
    internal inline fun <T> fsSynchronized(block: () -> T): T = synchronized(this@MemoryFileSystem.mutex) {
        block()
    }

    /** Gets a file or directory node given a path relative to the root. */
    fun lookup(path: FilePath): FileOrDirectoryOrRoot? = fsSynchronized {
        var fileOrDirectory: FileOrDirectoryOrRoot = root
        for (name in path.segments) {
            fileOrDirectory = (fileOrDirectory as? DirectoryOrRoot)?.get(name) ?: return@fsSynchronized null
        }
        fileOrDirectory
    }

    override fun ensureDir(path: FilePath): RFuture<Unit, IOException> = preComputedResultFuture(
        RResult.of(IOException::class) {
            ensureDirectory(path)
            Unit
        },
    )

    /** Similar to `mkdir -p`, creates intermediate as needed. Throws on failure. */
    fun ensureDirectory(path: FilePath): DirectoryOrRoot = fsSynchronized {
        check(path.isDir)
        var dir: DirectoryOrRoot = root
        for (name in path.segments) {
            dir = dir.mkdir(name) ?: throw IOException("Can't create dir over file")
        }
        dir
    }

    override fun deleteDirRecursively(path: FilePath): Unit = fsSynchronized {
        val dir = lookup(path) ?: return@fsSynchronized
        fun rmRf(file: FileOrDirectoryOrRoot) {
            if (file is DirectoryOrRoot) {
                file.ls().toList().forEach { rmRf(it) }
            }
            if (file !is RootDirectory) {
                unlink(file.absolutePath)
            }
        }
        rmRf(dir)
    }

    override fun ensureCleanDir(path: FilePath): RFuture<Unit, IOException> =
        fsSynchronized {
            val dir = ensureDirectory(path)
            for (child in dir.ls().toList()) {
                dir.unlink(child.entry.name)
            }
            preComputedFuture(Unit)
        }

    /** Similar to `rm -rf`. Fails when attempting to delete root itself (empty path). */
    fun unlink(path: FilePath): Unit = fsSynchronized {
        when (val dir = lookup(path.dirName())) {
            is File -> throw IOException("Dir is file?")
            null -> Unit
            else -> (dir as DirectoryOrRoot).unlink(path.lastOrNull()!!)
        }
    }

    override fun write(path: FilePath, bytes: ByteArray) {
        write(path, bytes, checkEqual = false)
    }

    fun write(
        path: FilePath,
        bytes: ByteArray,
        checkEqual: Boolean = false,
    ): Boolean = fsSynchronized {
        val file = when (val item = lookup(path)) {
            is File -> item
            null -> {
                val dir = ensureDirectory(path.dirName())
                dir.touch(path.lastOrNull()!!)!!
            }
            else -> throw IOException("Can't write to directory $path")
        }
        file.edit(bytes, checkEqual = checkEqual)
    }

    private fun writeAsyncInternal(
        path: FilePath,
        mimeType: MimeType?,
    ): CommittingByteSink {
        check(path.isFile && path.segments.isNotEmpty())
        val file = when (val item = lookup(path)) {
            is File -> item
            null -> {
                val dir = ensureDirectory(path.dirName())
                dir.touch(path.last())
                    ?: throw IOException("Could not create file $path")
            }
            else -> {
                throw IOException("Can't write to directory $path")
            }
        }
        return CommittingByteSink(file, mimeType)
    }

    override fun writeAsync(
        path: FilePath,
        mimeType: MimeType?,
    ): ByteSink = writeAsyncInternal(path, mimeType)

    private class CommittingByteSink(
        private val file: File,
        private val mimeType: MimeType?,
        private val byteCollector: ByteArrayOutputStream = ByteArrayOutputStream(),
    ) : ByteSink() {
        private var openOrClosed = OpenOrClosed.Open
        var future = UnmanagedFuture.newCompletableFuture<Unit, IOException>("writing to $file")

        override fun write(bytes: ByteArray, off: Int, len: Int) {
            byteCollector.write(bytes, off, len)
        }
        override fun write(bytes: ByteArray) {
            write(bytes, 0, bytes.size)
        }

        override fun flush() {
            file.edit(byteCollector.toByteArray(), mimeType)
        }

        override fun close() {
            flush()
            byteCollector.close()
            openOrClosed = OpenOrClosed.Closed
            future.completeOk(Unit)
        }
    }

    /** Recursively walks under [start] to make a list of all files */
    fun files(start: FileOrDirectoryOrRoot = root): List<File> {
        val files = mutableListOf<File>()
        fun walk(f: FileOrDirectoryOrRoot) {
            when (f) {
                is File -> { files.add(f) }
                is DirectoryOrRoot -> f.ls().forEach { walk(it) }
            }
        }
        walk(start)
        return files.toList()
    }

    override fun systemAccess(
        defaultCwd: FilePath,
        cancelGroup: CancelGroup,
    ): AsyncSystemAccess = MemoryFileSystemAccess(defaultCwd, cancelGroup)
    override fun systemReadAccess(
        defaultCwd: FilePath,
        cancelGroup: CancelGroup,
    ): AsyncSystemReadAccess = MemoryFileSystemReadAccess(defaultCwd, cancelGroup)

    private open inner class MemoryFileSystemAccess(
        override val pathToFileCreatorRoot: FilePath,
        override val cancelGroup: CancelGroup,
    ) : AsyncSystemAccess {
        override fun buildFile(filePath: FilePath, mimeType: MimeType?): FileBuilder = object : FileBuilder {
            private val fullFilePath get() = pathToFileCreatorRoot + filePath

            override fun executable(): FileBuilder {
                // Do nothing since we don't expose perm bits for read
                return this
            }

            override fun write(doWrite: (ByteSink) -> Unit): RFuture<Unit, IOException> {
                val fullFilePath = this.fullFilePath
                val byteSink =
                    writeAsyncInternal(fullFilePath, mimeType)
                cancelGroup.runLater("Invoking doWrite for $fullFilePath") {
                    byteSink.use {
                        try {
                            doWrite(it)
                        } catch (ex: IOException) {
                            byteSink.future.completeFail(ex)
                        }
                    }
                }
                return byteSink.future
            }
        }

        override fun buildChildProcess(command: String, build: ChildProcessBuilder.() -> Unit): PendingChildProcess? =
            null
    }

    private inner class MemoryFileSystemReadAccess(
        pathToFileCreatorRoot: FilePath,
        cancelGroup: CancelGroup,
    ) :
        MemoryFileSystemAccess(pathToFileCreatorRoot, cancelGroup), AsyncSystemReadAccess {
        override fun fileReader(filePath: FilePath): FileReader = object : FileReader {
            private val fullFilePath get() = pathToFileCreatorRoot + filePath
            override fun binaryContent(): RFuture<WrappedByteArray, IOException> =
                readBinaryFileContent(fullFilePath)
        }
    }

    companion object {
        /**
         * `{ dir: { file: "Hello, World!" } }` specifies a memory file system whose root contains
         * a directory named `dir` which contains a file named `file` with the textual content
         * "Hello, World!".
         */
        fun fromJson(
            jsonText: String,
            fs: MemoryFileSystem,
        ) = fromStructure(
            StructureParser.parseJson(jsonText, tolerant = true),
            fs = fs,
        )

        fun fromStructure(
            fileSystemStructure: Structured,
            fs: MemoryFileSystem,
        ) {
            fileSystemStructure.destructure(FsBuilder(fs.root, null))
        }

        /**
         * `{ dir: { file: "Hello, World!" } }` specifies a memory file system whose root contains
         * a directory named `dir` which contains a file named `file` with the textual content
         * "Hello, World!".
         */
        fun fromJson(
            jsonText: String,
        ): MemoryFileSystem {
            val fs = MemoryFileSystem()
            fromJson(jsonText, fs)
            return fs
        }

        fun fromStructure(
            fileSystemStructure: Structured,
        ): MemoryFileSystem {
            val fs = MemoryFileSystem()
            fromStructure(fileSystemStructure, fs)
            return fs
        }
    }

    override fun classify(filePath: FilePath): FileClassification = when (lookup(filePath)) {
        is File -> FileClassification.File
        is RootDirectory, is SubDirectory -> FileClassification.Directory
        null -> FileClassification.DoesNotExist
    }

    override fun directoryListing(
        dirPath: FilePath,
    ): RResult<List<FilePath>, IOException> = fsSynchronized {
        RSuccess(
            (lookup(dirPath) as? DirectoryOrRoot)?.ls()?.map {
                FilePath(
                    dirPath.segments + it.entry.name, // safe since child cannot be root
                    isDir = it is DirectoryOrRoot,
                )
            } ?: emptyList(),
        )
    }

    override fun textualFileContent(filePath: FilePath): RResult<CharSequence, IOException> {
        val file = lookup(filePath) as? File
            ?: return RFailure(FileNotFoundException("$filePath"))
        return RSuccess(file.textContent)
    }

    private fun getByteContentOrNull(
        filePath: FilePath,
    ): WrappedByteArray? = fsSynchronized {
        val file = lookup(filePath) as? File
            ?: return@fsSynchronized null
        val size = file.size
        val bytes = ByteArray(size)
        file.copyInto(bytes, 0, 0, size)
        WrappedByteArray.build(size) { append(bytes) }
    }

    override fun readBinaryFileContent(
        filePath: FilePath,
    ): RFuture<WrappedByteArray, IOException> =
        preComputedResultFuture(readBinaryFileContentSync(filePath))

    override fun readBinaryFileContentSync(
        filePath: FilePath,
    ): RResult<WrappedByteArray, IOException> =
        when (val bytes = getByteContentOrNull(filePath)) {
            // FileNotFoundException is documented as below, so it is the
            // appropriate response when a file is known to exist but may
            // not be read, even if due to missing read permissions.
            //
            // > Signals that an attempt to open the file denoted by a
            // > specified pathname has failed.
            null -> RFailure(FileNotFoundException("$filePath is not readable"))
            else -> RSuccess(bytes)
        }

    override fun readMimeType(filePath: FilePath): MimeType? = (lookup(filePath) as? File)?.mimeType

    override fun createWatchService(
        root: FilePath,
    ): RSuccess<MemoryFileWatchService, Nothing> = fsSynchronized {
        val watchService = MemoryFileWatchService(root)
        openWatchers.add(watchService) // Removes self on .close()
        RSuccess(watchService)
    }

    override fun close(): Unit = fsSynchronized {
        openOrClosed = OpenOrClosed.Closed
        for (watcher in openWatchers.toList()) {
            watcher.close()
        }
    }

    private fun MemoryFileWatchService.closeWatcher(): Unit = fsSynchronized {
        openWatchers.remove(this)
    }

    /**
     * An entry in a directory comprising a file name and the [FileOrDirectory] named.
     */
    data class DirectoryEntry(
        val parent: DirectoryOrRoot,
        val child: FileOrDirectory,
        val name: FilePathSegment,
    )

    /**
     * Files and directories have names, so can be in a [DirectoryEntry].
     * The [RootDirectory] is also a "directory" but has no parent.
     * It's convenient to treat the root as a different kind of thing rather than having nullable
     * fields.
     */
    sealed interface FileOrDirectoryOrRoot : Structured {
        val absolutePath: FilePath
    }

    /**
     * A file system part that is contained in a directory and which has a name.
     */
    sealed interface FileOrDirectory : FileOrDirectoryOrRoot {
        val fileSystem: MemoryFileSystem
        val entry: DirectoryEntry

        override val absolutePath: FilePath
            get() {
                val namesReversed = mutableListOf<FilePathSegment>()
                var entry: DirectoryEntry? = this.entry
                while (entry != null) {
                    namesReversed.add(entry.name)
                    entry = (entry.parent as? FileOrDirectory)?.entry
                }
                namesReversed.reverse()
                return FilePath(
                    namesReversed.toList(),
                    isDir = this is DirectoryOrRoot,
                )
            }
    }

    /**
     * Super type for [RootDirectory] and [SubDirectory].
     */
    sealed class DirectoryOrRoot : FileOrDirectoryOrRoot, Structured {
        private val children = mutableMapOf<FilePathSegment, FileOrDirectory>()
        abstract val fileSystem: MemoryFileSystem

        operator fun get(name: FilePathSegment) = fileSystem.fsSynchronized { children[name] }

        operator fun contains(name: FilePathSegment) = fileSystem.fsSynchronized { children.containsKey(name) }

        fun ls(): Collection<FileOrDirectory> = fileSystem.fsSynchronized { children.values.unmodifiableView() }

        fun touch(name: FilePathSegment): File? = fileSystem.fsSynchronized {
            when (val child = children[name]) {
                is File -> child
                null -> {
                    val file = File(fileSystem) {
                        DirectoryEntry(this@DirectoryOrRoot, it, name)
                    }
                    children[name] = file
                    fileSystem.notifyCreated(file.entry)
                    file
                }

                else -> null
            }
        }

        /** Return existing subdir or creates it new or returns null if a file is already there. */
        fun mkdir(name: FilePathSegment): SubDirectory? = fileSystem.fsSynchronized {
            when (val child = children[name]) {
                is SubDirectory -> child
                null -> {
                    val file = SubDirectory(fileSystem) {
                        DirectoryEntry(this@DirectoryOrRoot, it, name)
                    }
                    children[name] = file
                    fileSystem.notifyCreated(file.entry)
                    file
                }
                else -> null
            }
        }

        fun unlink(name: FilePathSegment): Boolean = fileSystem.fsSynchronized {
            when (val child = children[name]) {
                null -> false
                is File -> {
                    children.remove(name)
                    fileSystem.notifyDeleted(child.entry)
                    true
                }
                is SubDirectory -> {
                    val dir = child as DirectoryOrRoot
                    for (childName in dir.children.keys.toList()) {
                        child.unlink(childName)
                    }
                    if (dir.children.isEmpty()) {
                        children.remove(name)
                        fileSystem.notifyDeleted(child.entry)
                        true
                    } else {
                        false
                    }
                }
            }
        }

        override fun destructure(structureSink: StructureSink) = fileSystem.fsSynchronized {
            structureSink.obj {
                val sortedEntries = children.entries.sortedBy { it.key.fullName }
                for ((name, child) in sortedEntries) {
                    key(name.fullName) {
                        value(child)
                    }
                }
            }
        }
    }

    /**
     * The directory `/`.
     */
    class RootDirectory internal constructor(
        override val fileSystem: MemoryFileSystem,
    ) : DirectoryOrRoot(), FileOrDirectoryOrRoot {
        override val absolutePath: FilePath = FilePath(emptyList(), isDir = true)
    }

    /**
     * A subdirectory under the [RootDirectory].
     */
    class SubDirectory internal constructor(
        override val fileSystem: MemoryFileSystem,
        makeEntry: (FileOrDirectory) -> DirectoryEntry,
    ) : DirectoryOrRoot(), FileOrDirectory {
        override val entry: DirectoryEntry = makeEntry(this)
        init {
            require(entry.child == this)
        }
        override val absolutePath: FilePath
            get() = super.absolutePath
    }

    /**
     * A regular file.
     */
    class File internal constructor(
        override val fileSystem: MemoryFileSystem,
        makeEntry: (FileOrDirectory) -> DirectoryEntry,
    ) : FileOrDirectory {
        override val entry: DirectoryEntry = makeEntry(this)
        init {
            require(entry.child == this)
        }

        var mimeType: MimeType? = null
            private set
        private var content = WrappedByteArray.empty

        fun edit(
            bytes: ByteArray,
            mimeType: MimeType? = null,
            checkEqual: Boolean = false,
        ): Boolean = fileSystem.fsSynchronized {
            val wrappedBytes = WrappedByteArray.build { append(bytes) }
            val needsUpdate = !(checkEqual && wrappedBytes == content && this.mimeType == mimeType)
            if (needsUpdate) {
                this.content = wrappedBytes
                this.mimeType = mimeType
                fileSystem.notifyEdited(entry)
            }
            needsUpdate
        }

        /** Textual content.  Assumes the underlying bytes are valid UTF-8. */
        val textContent get() = content.decodeToString(throwOnInvalidSequence = false)

        val textOrBinaryContent: Either<String, WrappedByteArray>
            get() = try {
                Either.Left(content.decodeToString(throwOnInvalidSequence = true))
            } catch (_: CharacterCodingException) {
                Either.Right(content)
            }

        /** Byte count. */
        val size get() = content.size

        /**
         * Copies byte content into the given buffer
         * without exposing the underlying bytes to mutation.
         */
        fun copyInto(
            destination: ByteArray,
            destinationOffset: Int,
            startIndex: Int,
            endIndex: Int,
        ) = fileSystem.fsSynchronized {
            content.copyInto(
                destination = destination,
                destinationOffset = destinationOffset,
                startIndex = startIndex,
                endIndex = endIndex,
            )
        }

        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("mimeType", Hints.u) { value(mimeType) }
            key("content", Hints.n) { value(textContent) }
            key("__DO_NOT_CARE__", Hints.su) { value("__DO_NOT_CARE__") }
        }
    }

    override fun destructure(structureSink: StructureSink) = structureSink.value(root)

    /** Watchers that we need to deliver notifications to */
    private val openWatchers = mutableListOf<MemoryFileWatchService>()

    private fun notifyCreated(entry: DirectoryEntry) =
        notify(entry, FileChange.Kind.Created)
    private fun notifyDeleted(entry: DirectoryEntry) =
        notify(entry, FileChange.Kind.Deleted)
    private fun notifyEdited(entry: DirectoryEntry) =
        notify(entry, FileChange.Kind.Edited)

    private fun notify(entry: DirectoryEntry, kind: FileChange.Kind): Unit = fsSynchronized {
        val change = FileChange(entry.child.absolutePath, kind)
        for (watcher in openWatchers) {
            watcher.queueChange(change)
        }
    }

    /** Flushes changes for all open watchers. */
    fun flushPendingChanges(): Unit = fsSynchronized {
        for (watcher in openWatchers) {
            watcher.flushPendingChanges()
        }
    }

    /**
     * Responsible for filtering changes that are not relevant and contextualizing the changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    inner class MemoryFileWatchService(
        private val watchedRoot: FilePath,
    ) : FileWatchService {
        init {
            require(watchedRoot.isDir)
        }

        override var openOrClosed = OpenOrClosed.Open
            private set

        /**
         * Call to deliver change reports.
         * This gives explicit control over when changes are delivered so behavior can be
         * more repeatable, for tests or otherwise.
         */
        fun flushPendingChanges(): Unit = fsSynchronized {
            withUnconsumedChanges {
                if (it.isActive && queuedChanges.isNotEmpty()) {
                    it.complete(queuedChanges.toList())
                    queuedChanges.clear()
                }
            }
        }

        /**
         * Changes that have not been consumed by any waiting coroutine.
         */
        private var unconsumedChanges = CompletableDeferred<List<FileChange>>()

        private fun <T> withUnconsumedChanges(
            action: (CompletableDeferred<List<FileChange>>) -> T,
        ): T = fsSynchronized {
            action(unconsumedChanges)
        }

        private suspend fun <T> withUnconsumedChangesAsync(
            action: suspend (CompletableDeferred<List<FileChange>>) -> T,
        ): T {
            return action(unconsumedChanges)
        }

        override val changes = CoroutineScope(Dispatchers.Default).produce {
            while (true) {
                withUnconsumedChangesAsync { deferred ->
                    val changes = deferred.await()
                    unconsumedChanges = CompletableDeferred()
                    send(changes)
                }
            }
        }

        override fun close() = fsSynchronized {
            openOrClosed = OpenOrClosed.Closed
            closeWatcher()
            queuedChanges.clear()
            withUnconsumedChanges { deferred ->
                if (!deferred.isCompleted) {
                    deferred.complete(mutableListOf())
                }
            }
        }

        private val queuedChanges = mutableListOf<FileChange>()

        internal fun queueChange(change: FileChange) {
            val absPath = change.filePath
            val nCommon = commonPrefixLength(watchedRoot.segments, absPath.segments)
            if (nCommon == watchedRoot.segments.size) {
                // absPath is under watchedRoot.  Translate it to be relative.
                addToPending(
                    change.copy(
                        filePath = absPath.copy(segments = absPath.segments.subListToEnd(nCommon)),
                    ),
                )
            }
        }

        private fun addToPending(change: FileChange): Unit = fsSynchronized {
            if (openOrClosed == OpenOrClosed.Open) {
                queuedChanges.add(change)
            }
        }
    }
}

/**
 * Converts JSON parsed structures into edits to a memory file system.
 */
private class FsBuilder(
    val dir: MemoryFileSystem.DirectoryOrRoot,
    val pendingName: FilePathSegment?,
) : StructureSink {
    override fun obj(emitProperties: PropertySink.() -> Unit) {
        val dirToFill = if (pendingName == null) {
            // At the top level `{ ... }`, we're filling the root dir, and pendingName is null
            dir
        } else {
            // For a nested dir, we see the name before the curly bracket
            //     pendingName: { ... }
            // Now we've seen the curly bracket we can create the directory, and get ready to fill
            // it.
            dir.mkdir(pendingName)
                // Maybe there's already a regular file by that name.
                ?: error("Cannot convert ${dir.absolutePath}/$pendingName to a directory")
        }
        val filler = object : PropertySink {
            override fun key(
                key: String,
                hints: Set<StructureHint>,
                emitValue: StructureSink.() -> Unit,
            ) {
                FsBuilder(dirToFill, FilePathSegment(key)).emitValue()
            }

            override fun <T : Any> context(key: StructureContextKey<T>): T? =
                this@FsBuilder.context(key)
        }
        filler.emitProperties()
    }

    override fun arr(emitElements: StructureSink.() -> Unit) =
        error("Cannot convert array to a file or directory")

    override fun value(s: String) {
        if (pendingName != null) {
            val file = dir.touch(pendingName)
                // Maybe there's already a directory by that name
                ?: error("Cannot convert ${dir.absolutePath}/$pendingName to a file")
            file.edit(s.encodeToByteArray(), null)
        } else {
            error("Expected object with file names as keys for root directory, not string")
        }
    }

    override fun value(n: Int) = error("Cannot convert number $n to a file or directory")

    override fun value(n: Long) = error("Cannot convert number $n to a file or directory")

    override fun value(n: Double) = error("Cannot convert number $n to a file or directory")

    override fun value(b: Boolean) = error("Cannot convert boolean $b to a file or directory")

    override fun nil() = error("Cannot convert null to a file or directory")

    override fun <T : Any> context(key: StructureContextKey<T>): T? = null
}

fun MemoryFileSystem.forEachLeaf(
    f: (FilePath, MemoryFileSystem.File) -> Unit,
) {
    fun walk(filePath: FilePath, file: MemoryFileSystem.FileOrDirectoryOrRoot) {
        when (file) {
            is MemoryFileSystem.File -> f(filePath, file)
            is MemoryFileSystem.DirectoryOrRoot -> {
                for (child in file.ls()) {
                    walk(
                        filePath.resolve(
                            child.entry.name,
                            isDir = child is MemoryFileSystem.DirectoryOrRoot,
                        ),
                        child,
                    )
                }
            }
        }
    }
    walk(FilePath.emptyPath, this.root)
}
