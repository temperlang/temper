package lang.temper.common.structure

import lang.temper.common.assertStringsEqual
import kotlin.test.Test

class FormattingStructureSinkTest {
    @Test
    fun strings() {
        assertStringsEqual(
            "\"foo\"",
            FormattingStructureSink.toJsonString {
                value("foo")
            },
        )
        assertStringsEqual(
            "\"\\u0008o\\u0022o\\u0022\\n\"",
            FormattingStructureSink.toJsonString {
                value("\bo\"o\"\n")
            },
        )
        assertStringsEqual(
            "\"\"",
            FormattingStructureSink.toJsonString {
                value("")
            },
        )
        assertStringsEqual(
            "\"\\u0022\"",
            FormattingStructureSink.toJsonString {
                value("\"")
            },
        )
    }

    @Test
    fun number() {
        assertStringsEqual(
            "-1",
            FormattingStructureSink.toJsonString {
                value(-1)
            },
        )
        assertStringsEqual(
            "1",
            FormattingStructureSink.toJsonString {
                value(1L)
            },
        )
        assertStringsEqual(
            "0.00125",
            FormattingStructureSink.toJsonString {
                value(0.00125)
            },
        )
    }

    @Test
    fun boolean() {
        assertStringsEqual(
            "true",
            FormattingStructureSink.toJsonString {
                value(true)
            },
        )
        assertStringsEqual(
            "false",
            FormattingStructureSink.toJsonString {
                value(false)
            },
        )
    }

    @Test
    fun `null`() {
        assertStringsEqual(
            "null",
            FormattingStructureSink.toJsonString {
                nil()
            },
        )
    }

    @Test
    fun obj() {
        assertStringsEqual(
            """
            {
                "foo": "bar",
                "baz": [
                    123,
                    456
                ]
            }
            """.trimIndent(),

            FormattingStructureSink.toJsonString {
                obj {
                    key("foo") { value("bar") }
                    key("baz") {
                        arr {
                            value(123)
                            value(456)
                        }
                    }
                }
            },
        )
    }

    @Test
    fun multilineStringExtension() {
        fun format(extensions: Boolean) =
            FormattingStructureSink.toJsonString(extensions = extensions) {
                obj {
                    key("a") {
                        value(
                            """
                            line1
                            line2
                            ```line3```
                            line4
                            line5
                            """.trimIndent(),
                        )
                    }
                    key("b") { value("foo\nbar") }
                }
            }

        assertStringsEqual(
            """
            {
                "a": 
                ````
                line1
                line2
                ```line3```
                line4
                line5
                ````,
                "b": "foo\nbar"
            }
            """.trimIndent(),
            format(extensions = true),
        )

        assertStringsEqual(
            """
            {
                "a": "line1\nline2\n```line3```\nline4\nline5",
                "b": "foo\nbar"
            }
            """.trimIndent(),
            format(extensions = false),
        )
    }
}
