package lang.temper.compile.fetch

import lang.temper.common.console
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.common.currents.newCompletableFuture
import lang.temper.fs.runWithTemporaryDirectory
import lang.temper.log.dirPath
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.toPath
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GitHubTest {
    @Test
    fun accessName() {
        // Ordinarily, it's the last dir name.
        assertEquals("c", chooseAccessName(GitHubSpec(owner = "a", repo = "b", path = dirPath("c"))))
        // But not if that name is "src".
        assertEquals("b", chooseAccessName(GitHubSpec(owner = "a", repo = "b", path = dirPath("src"))))
        assertEquals("c", chooseAccessName(GitHubSpec(owner = "a", repo = "b", path = dirPath("c", "src"))))
        // Unless it's the repo name itself, because repo name is the last stop, whether "src" or not.
        assertEquals("src", chooseAccessName(GitHubSpec(owner = "a", repo = "src")))
        assertEquals("b", chooseAccessName(GitHubSpec(owner = "a", repo = "b")))
    }

    @Ignore("Relies on internet resources but should work for manual testing")
    @Test
    fun fetch() {
        runWithTemporaryDirectory("GitHubFetchTest") { root ->
            val commit = "4a35aa7337854f64cc483375a0f65984fc8528ce"
            val spec = parseGitHubUri("https://github.com/temperlang/prismora/tree/$commit/src")!!
            val cancelGroup = makeCancelGroupForTest()
            val treeFuture = cancelGroup.newCompletableFuture<GitCacheTree, IllegalArgumentException>("Tree future")
            fetchGitHub(spec, root, cancelGroup, treeFuture, console)
            val result = treeFuture.await()
            assertNotNull(result.result)
        }
    }

    @Test
    fun parseUri() {
        // Soft asserts would be nicer, but we'll survive hard here.
        val owner = "temperlang"
        val repo = "temper-regex-parser"
        val path = dirPath("whatever", "there")
        // Default
        assertEquals(
            GitHubSpec(owner = owner, repo = repo, ref = GitRef.defaultBranch, path = dirPath()),
            parseGitHubUri("https://github.com/$owner/$repo"),
        )
        assertNull(parseGitHubUri("https://github.com/$owner/$repo/$path"))
        assertNull(parseGitHubUri("https://github.com/$owner"))
        // Branch
        val branch = "rename-regex"
        assertEquals(
            GitHubSpec(owner = owner, repo = repo, ref = GitRef(branch), path = dirPath()),
            parseGitHubUri("https://github.com/$owner/$repo/tree/$branch"),
        )
        assertEquals(
            GitHubSpec(owner = owner, repo = repo, ref = GitRef(branch), path = path),
            parseGitHubUri("https://github.com/$owner/$repo/tree/$branch/$path"),
        )
        // Commit
        val commit = "aa1da387f57bb6943d12e982e7dd086d19086618"
        assertEquals(
            GitHubSpec(owner = owner, repo = repo, ref = GitRef(commit), path = dirPath()),
            parseGitHubUri("https://github.com/$owner/$repo/tree/$commit"),
        )
        assertEquals(
            GitHubSpec(owner = owner, repo = repo, ref = GitRef(commit, GitRefKind.Commit), path = dirPath()),
            parseGitHubUri("https://github.com/$owner/$repo/commit/$commit"),
        )
        assertNull(parseGitHubUri("https://github.com/$owner/$repo/commit/$commit/$path"))
        // Tag
        val tag = "v0.3.0"
        assertEquals(
            GitHubSpec(owner = owner, repo = repo, ref = GitRef(tag), path = dirPath()),
            parseGitHubUri("https://github.com/$owner/$repo/tree/$tag"),
        )
        assertEquals(
            GitHubSpec(owner = owner, repo = repo, ref = GitRef(tag, GitRefKind.Tag), path = dirPath()),
            parseGitHubUri("https://github.com/$owner/$repo/releases/tag/$tag"),
        )
        assertNull(parseGitHubUri("https://github.com/$owner/$repo/releases/tag/$tag/$path"))
    }
}

internal fun resourcePath(resource: String): Path {
    return GitHubTest::class.java.getResource(resource)!!.toURI().toPath()
}
