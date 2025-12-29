package lang.temper.compile.fetch

import lang.temper.common.Console
import lang.temper.common.currents.CancelGroup
import lang.temper.common.currents.CompletableRFuture
import lang.temper.common.currents.runLater
import lang.temper.common.subListToEnd
import lang.temper.log.FilePath
import lang.temper.log.dirPath
import org.eclipse.jgit.lib.Repository
import java.net.URI
import java.nio.file.Path

fun fetchGitHub(
    spec: GitHubSpec,
    cacheDir: Path,
    cancelGroup: CancelGroup,
    treeFuture: CompletableRFuture<GitCacheTree, IllegalArgumentException>,
    cliConsole: Console,
) {
    // TODO Do we need to url encode or escape anything?
    // TODO Store by some hash rather than readable names?
    // TODO Synchronize on access for some higher up dir? Right now done at git cache root level.
    val remoteCore = "github.com/${spec.owner}/${spec.repo}"
    cancelGroup.runLater("Fetch $remoteCore") {
        runCatching {
            val root = GitCacheRoot(cacheDir.resolve("imports/${remoteCore}"), cliConsole)
            root.initIfMissing("https://$remoteCore.git")
            when (val tree = root.tree(spec.ref, updateOnMissing = true)) {
                null -> treeFuture.completeFail(IllegalArgumentException("Invalid git request"))
                else -> treeFuture.completeOk(tree)
            }
        }.onFailure { treeFuture.completeError(it) }
    }
}

fun parseGitHubUri(uri: String): GitHubSpec? =
    runCatching { URI(uri) }.getOrNull()?.let { parseGitHubUri(it) }

fun parseGitHubUri(uri: URI): GitHubSpec? {
    uri.host == "github.com" || return null
    // Always starts with a "/" and might end with one. We don't care either way.
    // Current addresses I'm seeing in browser don't end with "/", but be flexible anyway.
    val parts = uri.path.trimStart { it == '/' }.trimEnd { it == '/' }.split('/')
    parts.size >= 2 || return null
    val (owner, repo) = parts.subList(0, 2)
    val (ref, path) = interpretParts(parts) ?: return null
    return GitHubSpec(owner = owner, repo = repo, ref = ref, path = path)
}

@Suppress("MagicNumber")
private fun interpretParts(parts: List<String>): Pair<GitRef, FilePath>? {
    // TODO Actual git URLs. Recognize and handle them elsewhere.
    val (ref, pathBegin) = when (parts.getOrNull(2)) {
        null -> GitRef.defaultBranch to parts.size
        "commit" -> parts.getOrNull(3)?.let { GitRef(it, GitRefKind.Commit) to -4 }
        "tree" -> parts.getOrNull(3)?.let { GitRef(it, kind = null) to 4 }
        "releases" -> when (parts.getOrNull(3)) {
            // TODO Instead download the zip for this case?
            "tag" -> parts.getOrNull(4)?.let { GitRef(it, GitRefKind.Tag) to -5 }
            else -> null
        }
        else -> null
    } ?: return null
    val pathBeginActual = when {
        // Use negatives to validate max size of parts array, so we don't accept unexpected things without using them.
        pathBegin < 0 -> when {
            -pathBegin < parts.size -> return null
            else -> parts.size
        }
        else -> pathBegin
    }
    return ref to dirPath(parts.subListToEnd(pathBeginActual))
}

data class GitHubSpec(
    val owner: String,
    val repo: String,
    val ref: GitRef = GitRef.defaultBranch,
    val path: FilePath = dirPath(),
)

data class GitRef(
    val name: String,
    val kind: GitRefKind? = null,
) {
    /**
     * Returns a ref with a full name and a non-null kind, or else null if not found.
     * Throws for specified but mismatched found kind.
     */
    fun fullRef(repository: Repository): GitRef? {
        val found = when (name) {
            "" -> when (kind) {
                GitRefKind.Branch, null -> {
                    // Special case for default branch.
                    repository.exactRef("HEAD")?.target?.name?.let { GitRef(it, GitRefKind.Branch) }
                }
                else -> null
            }
            else -> when (val ref = repository.findRef(name)) {
                null -> repository.resolve(name)?.name?.let { GitRef(it, GitRefKind.Commit) }
                else -> when {
                    "/tags/" in ref.name -> GitRef(ref.name, GitRefKind.Tag)
                    else -> GitRef(ref.name, GitRefKind.Branch)
                }
            }
        } ?: return null
        check(kind == null || found.kind == kind)
        return found
    }

    companion object {
        val defaultBranch = GitRef("", GitRefKind.Branch)
    }
}

enum class GitRefKind {
    Branch,
    Commit,
    Tag,
}

fun chooseAccessName(spec: GitHubSpec): String {
    return chooseAccessName(dirPath(spec.repo).resolve(spec.path))
}

/**
 * Choose a default name for shorthand access to this remote resource. Typically,
 * this is name of the last segment unless it's named "src", in which case, use
 * the parent dir name if there is one. If not, just go with "src".
 *
 * @param path the relevant portions of the remote path, which must not be empty
 */
fun chooseAccessName(path: FilePath): String {
    return when (val last = path.segments.last().fullName) {
        "src" -> when (path.segments.size) {
            1 -> last
            else -> path.segments[path.segments.size - 2].fullName
        }
        else -> last
    }
}
