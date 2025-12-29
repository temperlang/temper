package lang.temper.be.rust

import lang.temper.format.CodeFormatter
import lang.temper.format.toStringViaTokenSink
import lang.temper.name.OutName
import kotlin.test.Test
import kotlin.test.assertEquals
import lang.temper.log.unknownPos as p0

class RustGrammarTest {
    private fun assertCode(expected: String, ast: Rust.Tree) {
        val actual = toStringViaTokenSink(formattingHints = RustFormattingHints, singleLine = false) {
            CodeFormatter(it).format(ast)
        }
        assertEquals(expected.trimEnd(), actual.trimEnd())
    }

    @Test
    fun hello() {
        assertCode(
            """
                |fn main() {
                |    println!("Hi!");
                |}
            """.trimMargin(),
            Rust.SourceFile(
                p0,
                attrs = listOf(),
                items = listOf(
                    Rust.Item(
                        p0,
                        attrs = listOf(),
                        pub = null,
                        item = Rust.Function(
                            p0,
                            id = Rust.Id(p0, OutName("main", null)),
                            params = listOf(),
                            block = Rust.Block(
                                p0,
                                statements = listOf(
                                    Rust.ExprStatement(
                                        p0,
                                        expr = Rust.Call(
                                            p0,
                                            callee = Rust.Id(p0, OutName("println!", null)),
                                            args = listOf(Rust.StringLiteral(p0, "Hi!")),
                                        ),
                                    ),
                                ),
                                result = null,
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}
