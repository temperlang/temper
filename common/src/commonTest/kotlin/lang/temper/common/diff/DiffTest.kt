package lang.temper.common.diff

import lang.temper.common.asciiLowerCase
import kotlin.test.Test
import kotlin.test.assertEquals

class DiffTest {
    @Suppress("SpellCheckingInspection")
    @Test
    fun diffOfTwoCharacterLists() {
        val a = "ABCABBA".toList()
        val b = "CBABAC".toList()
        val want = Diff.Patch(
            listOf(
                Diff.Change(Diff.ChangeType.Deletion, 0, 0, listOf('A', 'B')),
                Diff.Change(Diff.ChangeType.Unchanged, 2, 0, listOf('C')),
                Diff.Change(Diff.ChangeType.Addition, 3, 1, listOf('B')),
                Diff.Change(Diff.ChangeType.Unchanged, 3, 2, listOf('A', 'B')),
                Diff.Change(Diff.ChangeType.Deletion, 5, 4, listOf('B')),
                Diff.Change(Diff.ChangeType.Unchanged, 6, 4, listOf('A')),
                Diff.Change(Diff.ChangeType.Addition, 7, 5, listOf('C')),
            ),
        )
        val got = Diff.differencesBetween(a, b)
        assertEquals(want, got)
        assertEquals(
            """
                |@@ -0,7 +0,6 @@
                |-a
                |-b
                | c
                |+b
                | a
                | b
                |-b
                | a
                |+c
                |
            """.trimMargin(),
            Diff.formatPatch(got) {
                "${it.asciiLowerCase()}"
            },
        )
    }

    private val patchNoChangeAtStartOrEnd = Diff.differencesBetween(
        listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        listOf(0, 1, 2, 7, 4, 5, 6, 3, 8, 9),
    )

    @Test
    fun format0Context() = assertEquals(
        """
            |@@ -3 +3 @@
            |-3
            |+7
            |@@ -7 +7 @@
            |-7
            |+3
            |
        """.trimMargin(),
        Diff.formatPatch(patchNoChangeAtStartOrEnd, context = 0),
    )

    @Test
    fun format1Context() = assertEquals(
        """
            |@@ -2,3 +2,3 @@
            | 2
            |-3
            |+7
            | 4
            |@@ -6,3 +6,3 @@
            | 6
            |-7
            |+3
            | 8
            |
        """.trimMargin(),
        Diff.formatPatch(patchNoChangeAtStartOrEnd, context = 1),
    )

    @Test
    fun format2Context() = assertEquals(
        """
            |@@ -1,9 +1,9 @@
            | 1
            | 2
            |-3
            |+7
            | 4
            | 5
            | 6
            |-7
            |+3
            | 8
            | 9
            |
        """.trimMargin(),
        Diff.formatPatch(patchNoChangeAtStartOrEnd, context = 2),
    )

    @Test
    fun format3Context() = assertEquals(
        """
            |@@ -0,10 +0,10 @@
            | 0
            | 1
            | 2
            |-3
            |+7
            | 4
            | 5
            | 6
            |-7
            |+3
            | 8
            | 9
            |
        """.trimMargin(),
        Diff.formatPatch(patchNoChangeAtStartOrEnd, context = 3),
    )

    @Test
    fun formatContextChangeAtStart() {
        val patch = Diff.differencesBetween(
            listOf(0, 1, 2, 3, 4),
            listOf(5, 1, 2, 3, 4),
        )
        assertEquals(
            """
                |@@ -0,3 +0,3 @@
                |-0
                |+5
                | 1
                | 2
                |
            """.trimMargin(),
            Diff.formatPatch(
                patch,
                context = 2,
            ),
        )
    }

    @Test
    fun formatContextChangeAtEnd() {
        val patch = Diff.differencesBetween(
            listOf(0, 1, 2, 3, 4),
            listOf(0, 1, 2, 3, 5),
        )
        assertEquals(
            """
                |@@ -2,3 +2,3 @@
                | 2
                | 3
                |-4
                |+5
                |
            """.trimMargin(),
            Diff.formatPatch(
                patch,
                context = 2,
            ),
        )
    }

    @Test
    fun formatContextChangeAtEnds() {
        val patch = Diff.differencesBetween(
            listOf(0, 1, 2, 3),
            listOf(4, 1, 2, 3, 4),
        )
        assertEquals(
            """
                |@@ -0,2 +0,2 @@
                |-0
                |+4
                | 1
                |@@ -3 +3,2 @@
                | 3
                |+4
                |
            """.trimMargin(),
            Diff.formatPatch(
                patch,
                context = 1,
            ),
        )
        assertEquals(
            """
                |@@ -0,4 +0,5 @@
                |-0
                |+4
                | 1
                | 2
                | 3
                |+4
                |
            """.trimMargin(),
            Diff.formatPatch(
                patch,
                context = 2,
            ),
        )
    }

    @Test
    fun multiLineItems() {
        val got = Diff.formatPatch(
            Diff.differencesBetween(
                listOf("foo\nbar", "bar\nbaz", "baz\r\nboo"),
                listOf("foo\nbar", "far\rfaz", "baz\r\nboo"),
            ),
        )
        val want = listOf(
            "@@ -0,3 +0,3 @@\n",
            " foo\n",
            ":bar\n",
            "-bar\n",
            ":baz\n",
            "+far\r",
            ":faz\n",
            " baz\r\n",
            ":boo\n",
        ).joinToString("")
        assertEquals(want, got)
    }

    @Test
    fun formatOfHunkLongerThanContext() {
        val got = Diff.formatPatch(
            Diff.differencesBetween(
                listOf(0, 1, 2, 3, 4),
                listOf(0, 1, 2, 3, 5, 6, 7, 8, 9, 10),
            ),
            context = 2,
        )
        val want = """
            |@@ -2,3 +2,8 @@
            | 2
            | 3
            |-4
            |+5
            |+6
            |+7
            |+8
            |+9
            |+10
            |
        """.trimMargin()

        assertEquals(want, got)
    }
}
