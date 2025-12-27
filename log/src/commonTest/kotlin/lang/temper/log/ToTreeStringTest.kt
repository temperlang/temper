package lang.temper.log

import lang.temper.common.assertStringsEqual
import kotlin.test.Test

class ToTreeStringTest {
    @Test
    fun testTreeString() {
        assertStringsEqual(
            """
                |_f
                |┣━a
                |┃ ┗━r.
                |┣━l
                |┃ ┗━a
                |┃   ┗━t.
                |┗━o
                |  ┣━o.
                |  ┃ ┣━d.
                |  ┃ ┣━l.
                |  ┃ ┗━t.
                |  ┗━u
                |    ┗━n
                |      ┗━d.
            """.trimMargin(),

            listOf("_foo", "_food", "_foot", "_far", "_fool", "_flat", "_found").toTreeString(
                unpack = { it.toCharArray().toList() },
                render = { el, segments ->
                    buildString {
                        segments.forEach { append(it) }
                        if (el != null) { append('.') }
                    }
                },
            ),
        )
    }

    @Test
    fun testFileTree() {
        assertStringsEqual(
            """
                |foo/
                |┣━bar/
                |┃ ┣━baz.txt
                |┃ ┗━boo.txt
                |┣━baz/
                |┃ ┗━far.txt
                |┗━foo/
            """.trimMargin(),
            listOf(
                filePath("foo", "bar", "baz.txt"),
                filePath("foo", "bar", "boo.txt"),
                dirPath("foo", "foo"),
                filePath("foo", "baz", "far.txt"),
            ).toFileTreeString(),
        )
    }

    @Test
    fun testMultiline() {
        assertStringsEqual(
            """
                |One
                |┣━Four
                |┃ ┗━Five
                |┣━More${
                "" // this item is split over multiple lines without interrupting the connecting bar
            }
                |┃  and
                |┃ More
                |┣━Three
                |┗━Two
            """.trimMargin(),
            listOf(
                listOf("One", "Two"),
                listOf("One", "Three"),
                listOf("One", "Four"),
                listOf("One", "Four", "Five"),
                listOf("One", "More\n and\nMore"),
            ).toTreeString(
                unpack = { it },
                render = { _, segments ->
                    segments.joinToString("/")
                },
            ),
        )
    }
}
