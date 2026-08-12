package lang.temper.testdir

import lang.temper.common.json.JsonString
import kotlin.test.Test
import kotlin.test.assertEquals

class DataFileConverterTest {
    @Test
    fun stringContentConverterNoComments() {
        val oldFile = """
            |  foo
            |
            |  bar1
            |## Comment about bar
            |  bar2
            |
            |  baz1
            |  boo
            |## Comment about boo
            |  baz2
            |## Comment at end
            |
        """.trimMargin()

        val newFile = """
            |food
            |
            |bar1
            |bar2
            |
            |baz1
            |boo
            |baz3
            |
        """.trimMargin()

        assertEquals(
            JsonString(
                """
                    |foo
                    |
                    |bar1
                    |bar2
                    |
                    |baz1
                    |boo
                    |baz2
                    |
                """.trimMargin(),
            ),
            FileContentStringConverter.fromFileContent(oldFile).result,
        )

        val regenerated = FileContentStringConverter.toFileContent(
            value = JsonString(newFile),
            oldValue = JsonString(oldFile),
        ).result

        assertEquals(
            """
                |  food
                |
                |  bar1
                |## Comment about bar
                |  bar2
                |
                |  baz1
                |  boo
                |## Comment about boo
                |  baz3
                |## Comment at end
                |
            """.trimMargin(),
            regenerated,
        )
    }
}
