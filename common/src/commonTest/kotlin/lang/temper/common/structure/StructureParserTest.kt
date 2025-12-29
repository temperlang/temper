package lang.temper.common.structure

import lang.temper.common.assertStringsEqual
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonString
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StructureParserTest {
    @Test
    fun testParseJson() {
        assertStringsEqual(
            """
            {
                "x": [
                    123,
                    [
                        -456.7890125
                    ]
                ],
                "y\u0022": false,
                "z": {
                    "a": null
                },
                "s": "\u0022\\"
            }
            """.trimIndent(),

            FormattingStructureSink.toJsonString {
                StructureParser.parseJson(
                    """
                    { "x": [123, [-456.7890125]], "y\"": false, "z": { "a": null }, "s": "\"\\" }
                    """,
                    true,
                ).destructure(this)
            },
        )
    }

    @Test
    fun commentsAllowedInTolerantMode() {
        assertStringsEqual(
            """
            {
                "x": 42,
                "z": -1
            }
            """.trimIndent(),
            FormattingStructureSink.toJsonString(
                StructureParser.parseJson(
                    """
                    {
                        // A comment
                        x: 42,
                        /* Another
                           multiline comment
                           y: 3,
                        */
                        z: -1, // Trailing commas ok in tolerant mode
                    }
                    """,
                    tolerant = true,
                ),
            ),
        )
    }

    @Test
    fun commentsDisallowedOutsideTolerantMode() {
        assertFailsWith(
            IllegalArgumentException::class,
        ) {
            StructureParser.parseJson(
                """
                {
                    // A comment
                }
                """,
            )
        }
    }

    @Test
    fun trailingCommasDisallowedOutsideTolerantMode() {
        assertFailsWith(
            IllegalArgumentException::class,
        ) {
            StructureParser.parseJson(
                """
                {
                    "x": 42,
                }
                """,
            )
        }
    }

    @Test
    fun barePropertyNamesDisallowedOutsideTolerantMode() {
        assertFailsWith(
            IllegalArgumentException::class,
        ) {
            StructureParser.parseJson(
                """
                {
                    x: 42
                }
                """,
            )
        }
    }

    @Test
    fun escapes() {
        val tree = StructureParser.parseJson(
            """
            "\n\t\\\x\"\'"
            """,
        )
        assertTrue(tree is JsonString)
        assertStringsEqual(
            "\n\t\\x\"'",
            tree.s,
        )
    }

    @Test
    fun backticks() {
        assertFailsWith(IllegalArgumentException::class) {
            StructureParser.parseJson(
                """
                ```
                foo
                ```
                """,
            )
        }

        val tree = StructureParser.parseJson(
            """
            ```
            foo
            ```
            """,
            tolerant = true,
        )
        assertTrue(tree is JsonString)
        assertStringsEqual("foo", tree.s)
    }

    @Test
    fun emptyMultilineString() {
        val tree = StructureParser.parseJson(
            """
                ```
                ```
            """,
            tolerant = true,
        )
        assertTrue(tree is JsonString)
        assertStringsEqual("", tree.s)
    }

    @Test
    fun multilineStringWithEmptyLines() {
        val tree = StructureParser.parseJson(
            """
                ```

                ${
                "" // Some leading whitespace
            }

                ```
            """,
            tolerant = true,
        )
        assertTrue(tree is JsonString)
        assertStringsEqual("\n\n", tree.s)
    }

    @Test
    fun multilineStringStartIndentedDifferently() {
        val tree = StructureParser.parseJson(
            """
                { x: ```
                  Line 1
                    Line 2
                  Line 3
                ``` }
            """,
            tolerant = true,
        )
        assertTrue(tree is JsonObject)
        val x = tree.properties.first { it.key == "x" }.value as JsonString
        assertStringsEqual("  Line 1\n    Line 2\n  Line 3", x.s)
    }
}
