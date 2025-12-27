package lang.temper.compile.fetch

import lang.temper.common.Console
import lang.temper.common.Log
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists

/** A cache of some remote repo for also caching multiple work trees for different refs. */
class GitCacheRoot(
    path: Path,
    override val console: Console = lang.temper.common.console,
) : DirLockable {
    /** The absolute root path above the bare git clone and the separate work tree clones. */
    override val path = path.absolute()

    /** The absolute path to the bare repo that serves as the central cache source. */
    val barePath: Path = this.path.resolve("bare.git")

    /** Initializes by cloning from the given uri. Does nothing if already initialized. */
    fun initIfMissing(uri: String): Unit = lock {
        barePath.exists() && return@lock
        console.log(Log.Fine) { "GitCacheRoot init $path $uri" }
        Git.cloneRepository().setBare(true).setDirectory(barePath.toFile()).setURI(uri).call().close()
        // Keep things writable because this just cache, not actual user data.
        makeWritable(barePath)
    }

    /**
     * Returns a dependent tree for a specific ref, which can't have "/" in its name.
     * If ref is for an empty named ("") branch or unspecified kind, get the default branch.
     * If missing and [updateOnMissing], update the root cache, and try once more to find the ref.
     * If a new tree is needed, it will be populated, but existing trees are left untouched.
     * Throws for specified but mismatched found kind.
     */
    fun tree(ref: GitRef, updateOnMissing: Boolean = false): GitCacheTree? = lock {
        console.log(Log.Fine) { "GitCacheRoot tree $path $ref" }
        val fullRef = FileRepository(barePath.toFile()).use { repository ->
            ref.fullRef(repository) ?: when {
                updateOnMissing -> {
                    update()
                    // Take 2.
                    ref.fullRef(repository)
                }
                else -> null
            }
        } ?: return@lock null
        val name = when (ref.name) {
            "" -> fullRef.name.split("/").last()
            else -> ref.name
        }
        val tree = GitCacheTree(root = this, path = path.resolve("tree").resolve(name), ref = fullRef)
        if (!tree.path.exists()) {
            tree.update()
        }
        return@lock tree
    }

    /** Fetch changes to the root from the remote. */
    fun update(): Unit = lock {
        console.log(Log.Fine) { "GitCacheRoot update $path" }
        Git.open(barePath.toFile()).use { it.fetch().call() }
    }
}

class GitCacheTree(
    /** The general cache root including for other trees. */
    val root: GitCacheRoot,
    /** The path of the top of the work tree. */
    val path: Path,
    /** The ref for this work tree. */
    val ref: GitRef,
) {
    /**
     * Pull changes to tree from the root if it's either missing or for a branch.
     * @param force if updates should be made even for tags.
     */
    fun update(force: Boolean = false): Unit = root.lock {
        root.console.log(Log.Fine) { "GitCacheTree update $path force $force" }
        // Could lock on just this tree, but we'd want to lock a different dir so we don't add contents.
        // Meanwhile, we lock above on root because we don't really gain much by parallel git requests, anyway.
        when {
            path.exists() -> if (ref.kind == GitRefKind.Branch || (force && ref.kind == GitRefKind.Tag)) {
                root.console.log(Log.Fine) { "GitCacheTree pull $path" }
                Git.open(path.toFile()).use { it.pull().call() }
            }
            else -> {
                // Prep common clone
                val command = Git.cloneRepository().setDirectory(path.toFile()).setURI(root.barePath.toString())
                root.console.log(Log.Fine) { "GitCacheTree clone $path" }
                when (ref.kind) {
                    // Shallow clone works for ref names but not commit ids unless I missed something.
                    GitRefKind.Commit -> command.setNoCheckout(false) // but still don't waste time
                    else -> command.setBranch(ref.name).setDepth(1)
                }
                command.call().use { git ->
                    if (ref.kind == GitRefKind.Commit) {
                        // Handle commit ids after the fact.
                        git.checkout().setName(ref.name).call()
                    }
                }
            }
        }
        // Keep things writable because this is just cache, not actual user data.
        makeWritable(path)
    }
}

internal fun makeWritable(root: Path) {
    // `Files.walk` is fragile in the face of ongoing changes, which we've seen from jgit, such as also seen by others
    // here: https://github.com/gitflow-incremental-builder/gitflow-incremental-builder/issues/64
    // For now, just try twice. If it keeps changing, let the sad failure through.
    var exception: Throwable? = null
    repeat(2) {
        runCatching {
            Files.walk(root).forEach { it.toFile().setWritable(true, true) }
            // It worked, so get out of here.
            return@makeWritable
        }.onFailure { exception = it }
    }
    throw exception!!
}
