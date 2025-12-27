package lang.temper.compile.fetch

import lang.temper.fs.runWithTemporaryDirCopyOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitCacheTest {
    @Test
    fun cache() {
        runWithTemporaryDirCopyOf("GitFetchTest", resourcePath("git")) { root ->
            val bundle = root.resolve("simple-repo.bundle").toAbsolutePath().toString()
            val cacheRoot = GitCacheRoot(root)
            cacheRoot.initIfMissing(bundle)
            // Branches
            cacheRoot.tree(GitRef.defaultBranch)!!.let { default ->
                assertEquals("main", default.path.name)
                assertTrue(default.path.resolve("config.temper.md").exists())
            }
            cacheRoot.tree(GitRef("my-branch"))!!.let { branch ->
                assertEquals(GitRefKind.Branch, branch.ref.kind)
                assertTrue(branch.path.resolve("here.txt").exists())
            }
            // Missing or mismatch
            assertNull(cacheRoot.tree(GitRef("totally-doesnt-exist")))
            assertThrows<Exception> { cacheRoot.tree(GitRef("my-branch", GitRefKind.Tag)) }
            // Tag
            cacheRoot.tree(GitRef("v0.1.0"))!!.let { branch ->
                assertEquals(GitRefKind.Tag, branch.ref.kind)
                assertTrue(branch.path.resolve("here.txt").exists())
            }
            // Commit
            cacheRoot.tree(GitRef("aeb8542098adba97ddf7e9537db876ac7f3230f3"))!!.let { branch ->
                assertEquals(GitRefKind.Commit, branch.ref.kind)
                assertTrue(branch.path.resolve("here.txt").exists())
            }
        }
    }
}
