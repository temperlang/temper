package lang.temper.be.cli

import lang.temper.be.Dependencies
import lang.temper.be.cli.CliEnv.Companion.using
import lang.temper.common.RResult
import lang.temper.common.WrappedByteArray
import lang.temper.common.console
import lang.temper.common.currents.CancelGroup
import lang.temper.common.orThrow
import lang.temper.common.putMultiList
import lang.temper.fs.NativePath
import lang.temper.fs.OutDir
import lang.temper.fs.OutRegularFile
import lang.temper.fs.ResourceDescriptor
import lang.temper.fs.WalkSignal
import lang.temper.fs.encodeToBytes
import lang.temper.fs.leaves
import lang.temper.fs.list
import lang.temper.fs.resolve
import lang.temper.fs.stat
import lang.temper.fs.temperRoot
import lang.temper.fs.walk
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.chompFirst
import lang.temper.log.last
import lang.temper.log.plus
import lang.temper.name.DashedIdentifier

/**
 * An environment to run one or more specific cli tools with its own directory structure.
 * A CliEnv may be created on the local machine without any protection, so code running should be
 * well-behaved.
 *
 * See [CliEnv.using] to obtain an instance and run actions within it.
 */
abstract class CliEnv protected constructor(
    val specifics: Specifics,
    val shellPreferences: ShellPreferences,
) {
    val charset get() = specifics.preferredEncoding

    /** within the environment, separates paths in e.g. PATH, e.g. `:` on Unix */
    abstract val pathSeparator: String

    /** the underlying implementation. */
    abstract val implementation: EnvImpl

    /** identify when Windows conventions are needed */
    abstract val isWindows: Boolean

    /** for constructing environment variables, find a string form of an absolute path in the environment */
    abstract fun envPath(path: FilePath): String

    /** also for constructing environment variables, generate a relative path */
    abstract fun relativePath(path: FilePath): String

    /** for access to user-level space for Temper outside the local cli env area */
    abstract fun userCachePath(path: FilePath): String

    /**
     * After being minimally configured, confirm that the environment is valid by asking the specifics
     */
    open fun validate(): RResult<Unit, CliFailure> = specifics.validate(this)

    /** Used during initialization; afterward prefer the get operator. */
    abstract fun which(tool: ToolSpecifics): RResult<CliTool, CliFailure>

    /** Get the cli tool within an initialized environment. */
    open operator fun get(tool: ToolSpecifics): CliTool = which(tool).result!!

    /** Copy a local file into the environment; creates directories as needed. */
    abstract fun writeLocalFile(sourceFile: NativePath, destination: FilePath)

    /** Write a binary blob into the environment at the file path; creates directories as needed. */
    abstract fun write(source: ByteArray, destination: FilePath)

    /** Write a binary blob into the environment at the file path; creates directories as needed. */
    open fun write(source: WrappedByteArray, destination: FilePath) {
        write(source.copyOf(), destination)
    }

    /** Write a string into the environment at the file path using the preferred encoding. */
    fun write(source: String, destinationFile: FilePath) {
        write(charset.encodeToBytes(source), destinationFile)
    }

    /** Make a directory, requires destination is a directory. Creates intermediate directories. */
    abstract fun makeDir(target: FilePath)

    /** Will attempt to create a symlink at destination pointing to source. Returns false if this fails. */
    abstract fun symlink(source: FilePath, destination: FilePath): Boolean

    /** Use sparingly; test if a file or dir is present in the environment. */
    abstract fun fileExists(path: FilePath): Boolean

    /**Test if a file or dir is present in the user cache. */
    abstract fun fileExistsInUserCache(path: FilePath): Boolean

    /** Use sparingly; list the files in [path] if it is a readable directory or else the empty list. */
    abstract fun readDir(
        path: FilePath,
        /** If false, behaves like `ls`.  If true, behaves like `find`. */
        recursively: Boolean = false,
    ): List<FilePath>

    /** Read a file using the standard encoding. */
    abstract fun readFile(path: FilePath): String

    /** Read a binary file to a byte array. */
    abstract fun readBinary(path: FilePath): ByteArray

    /**
     * Reads files matching a `$prefix*$suffix` glob.
     * Caveat: implementations may not escape glob characters, so don't use `*?[]`.
     */
    open fun readGlob(dir: FilePath, prefix: String, suffix: String): Map<FilePath, String> =
        readDir(dir).filter {
            if (it.isFile) {
                val name = it.last().fullName
                name.startsWith(prefix) && name.endsWith(suffix)
            } else {
                false
            }
        }.associateWith(::readFile)

    /** For now, removes only files, not dirs. */
    abstract fun remove(path: FilePath, errIfMissing: Boolean = false)

    /**
     * Copy file trees selectively from an output root into the environment.
     */
    fun copyOutputRoot(
        /** an output directory, which may be an output root, to recursively copy into the environment */
        source: OutDir,
        /** path of the directory within the environment to receive files */
        destinationDir: FilePath,
        mode: CopyMode = CopyMode.PlaceTop,
        /** Which libraries' files to copy.   If we need x, then we also need its transitive dependencies. */
        needed: Pair<Dependencies<*>, List<DashedIdentifier>>? = null,
    ) {
        val sourceRoots = if (needed == null) {
            // Copy all the libraries.
            setOf(source)
        } else {
            val (deps, libNames) = needed
            val transitiveDependencies = deps.transitiveDependencies
            buildSet {
                fun addRootFor(libraryName: DashedIdentifier) {
                    val root = source.entryNamed(FilePathSegment(libraryName.text)) as? OutDir
                        ?: return
                    add(root)
                }
                for (libName in libNames) {
                    addRootFor(libName)
                    transitiveDependencies[libName]?.forEach {
                        addRootFor(it)
                    }
                }
            }
        }
        for (sourceRoot in sourceRoots) {
            for (file in sourceRoot.leaves()) {
                val path = file.path
                val modPath = when (mode) {
                    CopyMode.UnpackTop -> path.chompFirst()
                    CopyMode.PlaceTop -> path
                }
                val target = destinationDir + modPath
                when (file) {
                    is OutRegularFile -> write(file.byteContent(), target)
                    else -> {}
                }
            }
        }
    }

    /**
     * Copy file trees from our source tree into the environment.
     *
     * @param source an output root to recursively copy into the environment
     * @param destinationDir path of the directory within the environment to receive files
     */
    fun copyOutputDir(source: OutDir, destinationDir: FilePath, mode: CopyMode = CopyMode.PlaceTop) {
        for (file in source.leaves()) {
            val path = file.pathFrom(source)
            val modPath = when (mode) {
                CopyMode.UnpackTop -> path.chompFirst()
                CopyMode.PlaceTop -> path
            }
            val target = destinationDir + modPath
            when (file) {
                is OutRegularFile -> write(file.byteContent(), target)
                else -> {}
            }
        }
    }

    /**
     * Copy file trees from within the project root into the environment.
     *
     * @param sourceDir a source directory relative to the project root
     * @param destinationDir path of the directory within the environment to receive files
     */
    open fun copySourceFileTree(
        sourceDir: FilePath,
        destinationDir: FilePath,
        mode: CopyMode = CopyMode.PlaceTop,
    ) {
        val sourceNative = temperRoot.resolve(sourceDir)
        for (
        path in when (mode) {
            CopyMode.UnpackTop -> sourceNative.list()
            CopyMode.PlaceTop -> listOf(sourceNative)
        }
        ) {
            if (path.stat().isFile) {
                writeLocalFile(path, destinationDir)
            } else {
                path.walk { source, relative ->
                    if (relative.isFile) {
                        writeLocalFile(source, destinationDir + relative)
                    }
                    WalkSignal.Continue
                }
            }
        }
    }

    /**
     * Copy resources into the environment.
     *
     * @param resources a list of resource descriptors to copy into the environment
     * @param destinationDir path of the directory within the environment to receive files
     */
    fun copyResources(resources: List<ResourceDescriptor>, destinationDir: FilePath) {
        resources.forEach { resource ->
            write(
                resource.load(),
                destinationDir + resource.rsrcPath,
            )
        }
    }

    val checkpoints = Checkpoints()

    /** Called by a [Specifics] to invoke any callbacks registered via [checkpoints].[on][Checkpoints.on]. */
    fun announce(checkpoint: Checkpoint) =
        this.checkpoints.announce(this, checkpoint)

    data class Checkpoint(val name: String) {
        companion object {
            // Some shared checkpoint definitions.

            /**
             * Checkpoint invoked after temper build products are available but
             * before installing them as defined by [postInstall]. Note that the
             * build typically occurs before the [CliEnv] exists, so this is
             * typically announced after copying the build into the environment.
             */
            val postBuild = Checkpoint("postBuild")

            /**
             * Checkpoint invoked after all the Temper generated artifacts have
             * been moved into the right place.
             */
            val postInstall = Checkpoint("postInstall")
        }
    }

    class Checkpoints {
        private val actions = mutableMapOf<Checkpoint, MutableList<CheckpointAction>>()

        /**
         * Allows communication between backend-specific tool running code and the runner.
         * This allows introspection over a run in progress so that test harnesses can,
         * for example, check the state of the file tree after putting all the Temper
         * generated files in the (hopefully) right places but before invoking the
         * tool to run one of those files.
         *
         * It is the [RunnerSpecifics] implementation's responsible to call the
         * checkpoints at appropriate places.
         */
        @Synchronized
        fun on(checkpoint: Checkpoint, action: CheckpointAction) {
            actions.putMultiList(checkpoint, action)
        }

        /** Called by a [Specifics] to invoke any callbacks registered via [on]. */
        internal fun announce(cliEnv: CliEnv, checkpoint: Checkpoint) {
            val toCall: List<CheckpointAction>? =
                synchronized(this@Checkpoints) { actions.remove(checkpoint) }

            toCall?.forEach {
                // dispatching to one shouldn't prevent dispatching to the next
                @Suppress("TooGenericExceptionCaught")
                try {
                    it(cliEnv, checkpoint)
                } catch (e: Exception) {
                    // TODO: should we use the cliConsole?
                    console.error(e)
                }
            }
        }

        @Synchronized
        fun addAll(checkpoints: Checkpoints) {
            for ((cp, actions) in checkpoints.actions) {
                this.actions.getOrPut(cp) { mutableListOf() }.addAll(actions)
            }
        }
    }

    companion object {
        /**
         * Obtains a CliEnv, runs the action in the block and releases it.
         *
         * The `using` method will not freeze the environment, and
         * outside the using block it may be too late.
         *
         * To preserve the test result, [maybeFreeze] should be called when it's
         * known that there's an error.
         * Methods like [print] and [explain] will implicitly call `maybeFreeze`,
         * so if errors are printed within the block, this is generally sufficient.
         *
         */
        inline fun <T> using(
            specifics: Specifics,
            shellPreferences: ShellPreferences,
            cancelGroup: CancelGroup,
            outDir: OutDir? = null,
            block: CliEnv.() -> T,
        ): T {
            val cliEnv = obtainCliEnv(specifics, shellPreferences, outDir, cancelGroup)
            try {
                cliEnv.init()
                cliEnv.validate().orThrow()
                // intercept effort
                return block(cliEnv)
            } finally {
                cliEnv.release()
            }
        }
    }

    /** Creates a sub env with no-op release for adjusted root. */
    abstract fun subRoot(sub: FilePath): CliEnv

    /** After validation, initializes real resources. See [using]. */
    abstract fun init()

    /**
     * Freeze the environment to reconstruct an error. This should be an advice map containing only [Advice.CliEnv].
     *
     * See [using] and [composing] for a discussion on usage.
     */
    abstract fun maybeFreeze(): Map<Advice, String>

    /** Releases real resources acquired by the CliEnv. Prefer [using]. */
    abstract fun release()
}

/**
 * If the receiver is null, behaves like using, otherwise reuses the environment.
 *
 * The convention for freezing is that the "owner" of the environment decides what constitutes a failure
 * that ought to be frozen for diagnosis. For instance, if `runJsBestEffort` detects a failure, it will only freeze
 * if it created the environment.
 */
inline fun <T> CliEnv.composing(
    specifics: Specifics,
    block: CliEnv.() -> T,
): T {
    require(this.specifics == specifics) { "Can't compose environments with different specifics!" }
    return this.block()
}

enum class CopyMode {
    /** Unpack the contents of the source directory into the destination directory, preserving subdirectories. */
    UnpackTop,

    /** Place the source element (directory) as a new object within the destination directory. */
    PlaceTop,
}

enum class EnvImpl {
    Local,
}

typealias CheckpointAction = (CliEnv, CliEnv.Checkpoint) -> Unit

/** True if the CliEnv is implemented for this language. */
expect val cliEnvImplemented: Boolean

/** Language specific function to obtain a CliEnv. See [CliEnv.using]. */
expect fun obtainCliEnv(
    specifics: Specifics,
    shellPreferences: ShellPreferences,
    outDir: OutDir?,
    cancelGroup: CancelGroup,
): CliEnv

/**
 * Install operations like `pip install` or `npm install` should fail fast during CI.
 *
 * Enabled manually with `-Dtemper.install-fail-fast=yes` and disable with `-Dtemper.install-fail-fast=no`.
 */
expect val installShouldFailFast: Boolean

/** System property to configure installer fail fast behavior; see root build.gradle */
const val INSTALL_FF_NAME = "temper.install-fail-fast"

/** How many times should an installer retry a fetch. */
const val INSTALL_FF_FETCH_RETRIES = 2

/** How long an individual attempt to fetch waits before timing out. */
const val INSTALL_FF_TIMEOUT_MILLIS = 4_000

/** How long an individual attempt to fetch waits before timing out. */
const val INSTALL_FF_TIMEOUT_SECONDS = INSTALL_FF_TIMEOUT_MILLIS / 1000.0

/** Pause between fetch retries. */
const val INSTALL_FF_RETRY_PAUSE_MILLIS = 1_000
