package lang.temper.log

import lang.temper.common.assertStringsEqual
import lang.temper.common.toStringViaBuilder
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.FilePath.Companion.joinPathTo
import kotlin.test.Test
import kotlin.test.assertEquals

class FilePathTest {
    @Test
    fun relativize() {
        val target = filePath("foo", "bar", "baz", "boo.x")
        assertEquals(
            "foo/bar/baz/boo.x",
            target.join(),
        )
        fun assertRelativePathToPath(want: String, start: FilePath) {
            assertStringsEqual(
                want,
                toStringViaBuilder { sb ->
                    start.relativePathTo(target).joinPathTo(isDir = target.isDir, sb = sb)
                },
            )
        }
        assertRelativePathToPath(
            "boo.x",
            filePath("foo", "bar", "baz", "other.y"),
        )
        assertRelativePathToPath(
            "boo.x",
            filePath("foo", "bar", "baz", "boo.x"),
        )
        assertRelativePathToPath(
            "../baz/boo.x",
            filePath("foo", "bar", "different", "boo.x"),
        )
        assertRelativePathToPath(
            "../baz/boo.x",
            dirPath("foo", "bar", "different"),
        )
        assertRelativePathToPath(
            "../boo.x",
            dirPath("foo", "bar", "baz", "sub"),
        )
        assertRelativePathToPath(
            "baz/boo.x",
            dirPath("foo", "bar"),
        )
        assertRelativePathToPath(
            "bar/baz/boo.x",
            dirPath("foo"),
        )
        assertRelativePathToPath(
            "../bar/baz/boo.x",
            dirPath("foo", "different"),
        )
        assertRelativePathToPath(
            "../../bar/baz/boo.x",
            dirPath("foo", "different", "different"),
        )
        assertRelativePathToPath(
            "../../../foo/bar/baz/boo.x",
            dirPath("different", "different", "different"),
        )
    }
}
