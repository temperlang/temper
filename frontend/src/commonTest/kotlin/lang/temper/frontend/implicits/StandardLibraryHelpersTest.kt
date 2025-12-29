package lang.temper.frontend.implicits

import lang.temper.fs.MemoryFileSystem
import lang.temper.log.dirPath
import lang.temper.log.filePath
import kotlin.test.Test
import kotlin.test.assertEquals

class StandardLibraryHelpersTest {
    @Test
    fun automatedTemperFileExtensions() {
        val fs = MemoryFileSystem.fromJson(
            // TODO Some dirs, both src and direct.
            """
            {
                foo: {
                    "other.txt": "Hi!",
                    "other.temper": "Hi!",
                    "plain": "Hi!",
                    "plain.temper": "Hi!",
                    "something.temper": "Hi!",
                    "whatever.temper.md": "Hi!",
                    more: {
                        "config.temper.md": "Hi!",
                        src: {},
                    },
                    src: {},
                }
            }
            """,
        )
        val cases = listOf(
            filePath("foo") to dirPath("foo", "src"), // no config but yes src
            filePath("foo", "more") to dirPath("foo", "more"), // yes config despite src
            filePath("foo", "missing") to filePath("foo", "missing"),
            filePath("foo", "other") to filePath("foo", "other.temper"),
            filePath("foo", "other.txt") to filePath("foo", "other.txt"),
            filePath("foo", "other.temper") to filePath("foo", "other.temper"),
            filePath("foo", "plain") to filePath("foo", "plain"),
            filePath("foo", "something") to filePath("foo", "something.temper"),
            filePath("foo", "whatever.temper") to filePath("foo", "whatever.temper"),
            filePath("foo", "whatever") to filePath("foo", "whatever.temper.md"),
        )
        for (case in cases) {
            assertEquals(case.second, findMatchingTemperFile(case.first, fs))
        }
    }
}
