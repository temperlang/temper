package lang.temper.docbuild

import lang.temper.fs.NativeConvention
import lang.temper.fs.NativePath
import lang.temper.fs.nativeConvention
import lang.temper.fs.resolve
import lang.temper.fs.resolveEntry
import lang.temper.fs.temperRoot
import lang.temper.log.dirPath

/**
 * You can use this to [run] code or [inContext] to get file and directory paths in scope.
 */
internal object UserDocFilesAndDirectories {
    val projectRoot = temperRoot
    val userDocRootPathPrefix = dirPath("docs", "for-users")
    val userDocRoot = temperRoot.resolve(userDocRootPathPrefix)
    val skeletalDocRoot = temperRoot.resolve(dirPath("build-user-docs", "skeletal-docs"))
    val preserveUnderDocRoot = setOf(
        userDocRoot.resolve(dirPath("temper-docs", "docs", "blog")),
    )
    val scriptsDir = temperRoot.resolve(dirPath("build-user-docs", "snippet-factory-scripts"))
    val snippetsRoot = temperRoot.resolve(snippetPathPrefix)
    val snippetsJsonFile: NativePath = userDocRoot.resolveEntry(".snippet-hashes.json")
    val mkdocsRoot: NativePath = userDocRoot.resolve(dirPath("temper-docs", "docs"))
    val unInlinedSnippetsRoot: NativePath = mkdocsRoot.resolveEntry("snippet")

    val isWindows = nativeConvention == NativeConvention.Windows
}

internal inline fun UserDocFilesAndDirectories.inContext(f: UserDocFilesAndDirectories.() -> Unit) {
    f()
}

internal fun isPathAncestor(
    ancestor: NativePath,
    possibleDescendant: NativePath,
): Boolean {
    val n = ancestor.nameCount
    return possibleDescendant.nameCount >= n && (0 until n).all { i ->
        ancestor.getName(i) == possibleDescendant.getName(i)
    }
}
