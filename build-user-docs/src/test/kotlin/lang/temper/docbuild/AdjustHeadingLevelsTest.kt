package lang.temper.docbuild

import kotlin.test.Test
import kotlin.test.assertEquals

class AdjustHeadingLevelsTest {
    @Test
    fun adjust() {
        assertEquals(
            """
                    |### Foo
                    |Not heading text
                    |
                    |#### Bar
                    |More not heading text.
                    |
                    |##### Baz
                    |Yet another not heading text.
                    |
                    |###### H6
                    |Who uses `<h6>`?
                    |
                    |### Bar Bar
                    |
                    |Multi-line heading precedes.
                    |
            """.trimMargin(),
            adjustHeadingLevels(
                MarkdownContent(
                    """
                    |## Foo
                    |Not heading text
                    |
                    |### Bar
                    |More not heading text.
                    |
                    |#### Baz
                    |Yet another not heading text.
                    |
                    |###### H6
                    |Who uses `<h6>`?
                    |
                    |Bar Bar
                    |-------
                    |
                    |Multi-line heading precedes.
                    |
                    """.trimMargin(),
                ),
                contextLevel = 2,
            ),
        )
    }
}
