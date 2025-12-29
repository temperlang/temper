package lang.temper.name

import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleNameTest {
    @Test
    fun stringForm() {
        fun moduleName(vararg parts: String): ModuleName {
            val index = parts.indexOf("//")
            return ModuleName(
                FilePath(
                    buildList {
                        for (i in parts.indices) {
                            if (i != index) {
                                add(FilePathSegment(parts[i]))
                            }
                        }
                    },
                    isDir = true,
                ),
                libraryRootSegmentCount = index,
                isPreface = false,
            )
        }

        val cases = listOf(
            moduleName("//") to "//",
            moduleName("a", "//") to "a//",
            moduleName("//", "b") to "//b/",
            moduleName("a", "//", "b") to "a//b/",
            moduleName("a", "b", "//", "c", "d") to "a/b//c/d/",
        )

        fun longString(moduleName: ModuleName) =
            "ModuleName([${
                moduleName.libraryRoot().segments.joinToString(", ") { it.fullName }
            }], ${moduleName.libraryRootSegmentCount})"

        val want = cases.map { (moduleName, wantStr) ->
            longString(moduleName) to wantStr
        }

        val got = cases.map { (moduleName) ->
            longString(moduleName) to "$moduleName"
        }

        assertEquals(want, got)
    }
}
