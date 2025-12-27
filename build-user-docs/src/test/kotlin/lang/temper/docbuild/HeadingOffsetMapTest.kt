package lang.temper.docbuild

import kotlin.test.Test
import kotlin.test.assertEquals

class HeadingOffsetMapTest {
    @Test
    fun offsets() {
        val markdown = """
            |(preamble)
            |
            |# Foo
            |Lorem Ipsum
            |
            |## Bar
            |Dolor Sic Amet
            | 
            |### Baz
            |I forget the rest of lorem ipsum.
            |
            |## Far
            |...
            |
        """.trimMargin()

        val foo = markdown.indexOf("# Foo")
        val bar = markdown.indexOf("## Bar")
        val baz = markdown.indexOf("### Baz")
        val far = markdown.indexOf("## Far")

        val offsetMap = HeadingOffsetMap(MarkdownContent(markdown))

        @Suppress("MagicNumber") // Heading levels and safely index past last char in file.
        for (offset in 0 until (markdown.length + 10)) {
            val wanted = when {
                offset < foo -> 0
                offset < bar -> 1
                offset < baz -> 2
                offset < far -> 3
                else -> 2
            }
            val got = offsetMap[offset]
            assertEquals(wanted, got, "offset=$offset")
        }
    }
}
