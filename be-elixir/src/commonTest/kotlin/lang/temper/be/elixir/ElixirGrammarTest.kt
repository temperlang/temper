package lang.temper.be.elixir

import lang.temper.format.CodeFormatter
import lang.temper.format.toStringViaTokenSink
import lang.temper.name.OutName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import lang.temper.be.elixir.ElixirOperator as O
import lang.temper.log.unknownPos as p0

/**
 * Checks that the out-grammar renders Elixir that Elixir's parser accepts and
 * that means what the tree says.
 *
 * Every expected string here was run through `elixir` 1.19.5 on OTP 28
 * (journal/probes/05_grammar_samples.exs evaluates each one), so these are not
 * guesses about the target language.
 */
class ElixirGrammarTest {
    private fun assertCode(expected: String, ast: Elixir.Tree) {
        val actual = toStringViaTokenSink(formattingHints = ElixirFormattingHints, singleLine = false) {
            CodeFormatter(it).format(ast)
        }
        assertEquals(expected.trimEnd(), actual.trimEnd())
    }

    private fun id(text: String) = Elixir.Id(p0, OutName(text, null))
    private fun mod(vararg segments: String) = Elixir.ModuleName(p0, segments.map { id(it) })
    private fun num(n: Number) = Elixir.NumberLit(p0, n)
    private fun str(s: String) = Elixir.StringLit(p0, s)
    private fun atom(s: String) = Elixir.Atom(p0, s)
    private fun op(left: Elixir.Expr?, o: O, right: Elixir.Expr?) =
        Elixir.Operation(p0, left = left, operator = Elixir.Operator(p0, o), right = right)
    private fun block(vararg exprs: Elixir.BlockItem) = Elixir.Block(p0, exprs.toList())

    @Test
    fun hello() {
        assertCode(
            """IO.puts("Hello, World!")""",
            Elixir.RemoteCall(p0, module = mod("IO"), fn = id("puts"), args = listOf(str("Hello, World!"))),
        )
    }

    @Test
    fun moduleWithStructAttributeAndFunctions() {
        assertCode(
            """
                |defmodule Shapes.Point do
                |  @moduledoc false
                |  defstruct [:x, :y]
                |  def norm1(p) when p.x >= 0 do
                |    abs_sum(p.x, p.y)
                |  end
                |  defp abs_sum(a, b) do
                |    abs(a) + abs(b)
                |  end
                |end
            """.trimMargin(),
            Elixir.ModuleDef(
                p0,
                name = mod("Shapes", "Point"),
                items = listOf(
                    Elixir.ModuleAttr(p0, id = id("moduledoc"), value = Elixir.BoolLit(p0, false)),
                    Elixir.StructDef(p0, fields = listOf(atom("x"), atom("y"))),
                    Elixir.FunDef(
                        p0,
                        id = id("norm1"),
                        params = listOf(id("p")),
                        guard = op(field("p", "x"), O.GreaterEquals, num(0)),
                        body = block(
                            Elixir.Call(p0, callee = id("abs_sum"), args = listOf(field("p", "x"), field("p", "y"))),
                        ),
                    ),
                    Elixir.FunDef(
                        p0,
                        isPrivate = true,
                        id = id("abs_sum"),
                        params = listOf(id("a"), id("b")),
                        body = block(
                            op(
                                Elixir.Call(p0, callee = id("abs"), args = listOf(id("a"))),
                                O.Addition,
                                Elixir.Call(p0, callee = id("abs"), args = listOf(id("b"))),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun field(obj: String, f: String) = Elixir.Field(p0, obj = id(obj), id = id(f))

    @Test
    fun caseWithGuardAndMultiLineBody() {
        assertCode(
            """
                |r = case {a, b} do
                |  {1, _} ->
                |    :one
                |  {_, y} when y > 1 ->
                |    z = y + 1
                |    {:big, z}
                |  _ ->
                |    :other
                |end
            """.trimMargin(),
            Elixir.Match(
                p0,
                left = id("r"),
                right = Elixir.Case(
                    p0,
                    subject = Elixir.TupleLit(p0, listOf(id("a"), id("b"))),
                    clauses = listOf(
                        Elixir.Clause(
                            p0,
                            pattern = Elixir.TuplePattern(p0, listOf(num(1), Elixir.Wildcard(p0))),
                            body = block(atom("one")),
                        ),
                        Elixir.Clause(
                            p0,
                            pattern = Elixir.TuplePattern(p0, listOf(Elixir.Wildcard(p0), id("y"))),
                            guard = op(id("y"), O.GreaterThan, num(1)),
                            body = block(
                                Elixir.Match(p0, left = id("z"), right = op(id("y"), O.Addition, num(1))),
                                Elixir.TupleLit(p0, listOf(atom("big"), id("z"))),
                            ),
                        ),
                        Elixir.Clause(p0, pattern = Elixir.Wildcard(p0), body = block(atom("other"))),
                    ),
                ),
            ),
        )
    }

    @Test
    fun anonymousFunctionAndItsCall() {
        assertCode(
            """
                |f = fn c, d ->
                |  c + d
                |end
                |f.(1, 2)
            """.trimMargin(),
            Elixir.SourceFile(
                p0,
                listOf(
                    Elixir.Match(
                        p0,
                        left = id("f"),
                        right = Elixir.Fn(
                            p0,
                            params = listOf(id("c"), id("d")),
                            body = block(op(id("c"), O.Addition, id("d"))),
                        ),
                    ),
                    Elixir.AnonCall(p0, fn = id("f"), args = listOf(num(1), num(2))),
                ),
            ),
        )
    }

    @Test
    fun mapsStructsAndUpdates() {
        assertCode(
            """{%{:x => 1, "k" => 2}, %{m | :x => 3}, %Shapes.Point{x: 1, y: 2}}""",
            Elixir.TupleLit(
                p0,
                listOf(
                    Elixir.MapLit(
                        p0,
                        listOf(Elixir.MapEntry(p0, atom("x"), num(1)), Elixir.MapEntry(p0, str("k"), num(2))),
                    ),
                    Elixir.MapUpdate(p0, base = id("m"), entries = listOf(Elixir.MapEntry(p0, atom("x"), num(3)))),
                    Elixir.StructLit(
                        p0,
                        name = mod("Shapes", "Point"),
                        fields = listOf(
                            Elixir.KeywordEntry(p0, key = id("x"), value = num(1)),
                            Elixir.KeywordEntry(p0, key = id("y"), value = num(2)),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun chainedComparisonsAreParenthesised() {
        // `1 < 2 < 3` would parse, and be false
        assertCode(
            "(a < b) < c",
            op(op(id("a"), O.LessThan, id("b")), O.LessThan, id("c")),
        )
        // comparison binds tighter than equality, so (a < b) == c needs none
        assertCode(
            "a < b == c",
            op(op(id("a"), O.LessThan, id("b")), O.Equals, id("c")),
        )
        assertCode(
            "(a == b) == c",
            op(op(id("a"), O.Equals, id("b")), O.Equals, id("c")),
        )
    }

    @Test
    fun associativity() {
        assertCode("a - (b - c)", op(id("a"), O.Subtraction, op(id("b"), O.Subtraction, id("c"))))
        assertCode("a - b - c", op(op(id("a"), O.Subtraction, id("b")), O.Subtraction, id("c")))
        // `++` is right-associative, so the left-nested tree needs parentheses
        assertCode("(a ++ b) ++ c", op(op(id("a"), O.ListConcat, id("b")), O.ListConcat, id("c")))
        assertCode("a <> b <> c", op(id("a"), O.StringConcat, op(id("b"), O.StringConcat, id("c"))))
    }

    @Test
    fun prefixOperatorsBindTight() {
        // `not a == b` would be `(not a) == b`
        assertCode("not (a == b)", op(null, O.Not, op(id("a"), O.Equals, id("b"))))
        // `-2 ** 2` is 4: unary minus binds tighter than `**`
        assertCode("-(a ** 2)", op(null, O.Negate, op(id("a"), O.Power, num(2))))
        assertCode("-a ** 2", op(op(null, O.Negate, id("a")), O.Power, num(2)))
    }

    @Test
    fun ifElseCondAndTry() {
        assertCode(
            """
                |if ok do
                |  1
                |else
                |  2
                |end
            """.trimMargin(),
            Elixir.If(p0, test = id("ok"), then = block(num(1)), otherwise = block(num(2))),
        )
        assertCode(
            """
                |cond do
                |  x > 1 ->
                |    :big
                |  true ->
                |    :small
                |end
            """.trimMargin(),
            Elixir.Cond(
                p0,
                listOf(
                    Elixir.CondArm(p0, test = op(id("x"), O.GreaterThan, num(1)), body = block(atom("big"))),
                    Elixir.CondArm(p0, test = Elixir.BoolLit(p0, true), body = block(atom("small"))),
                ),
            ),
        )
        assertCode(
            """
                |try do
                |  risky()
                |rescue
                |  e ->
                |    {:error, e}
                |end
            """.trimMargin(),
            Elixir.Try(
                p0,
                body = block(Elixir.Call(p0, callee = id("risky"))),
                rescues = listOf(
                    Elixir.Clause(
                        p0,
                        pattern = id("e"),
                        body = block(Elixir.TupleLit(p0, listOf(atom("error"), id("e")))),
                    ),
                ),
            ),
        )
    }

    @Test
    fun listsAndPatterns() {
        assertCode(
            "[h | t] = [0 | [1, 2]]",
            Elixir.Match(
                p0,
                left = Elixir.ConsPattern(p0, head = id("h"), tail = id("t")),
                right = Elixir.ConsList(p0, items = listOf(num(0)), tail = Elixir.ListLit(p0, listOf(num(1), num(2)))),
            ),
        )
        assertCode(
            "%{:k => ^v} = m",
            Elixir.Match(
                p0,
                left = Elixir.MapPattern(
                    p0,
                    listOf(Elixir.MapPatternEntry(p0, key = atom("k"), value = Elixir.Pin(p0, id("v")))),
                ),
                right = id("m"),
            ),
        )
    }

    @Test
    fun literals() {
        assertCode(""""a\#{b}c \"q\" \\ \n\t\r \u{1}"""", str("a#{b}c \"q\" \\ \n\t\r \u0001"))
        assertCode(""":ok""", atom("ok"))
        assertCode(""":empty?""", atom("empty?"))
        assertCode(""":"with spaces"""", atom("with spaces"))
        assertCode(""":"Elixir.Foo"""", atom("Elixir.Foo"))
        assertCode("1.0", num(1.0))
        assertCode("-0.0", num(-0.0))
        assertCode("1.0E10", num(1.0e10))
        assertCode("1.5E-7", num(1.5e-7))
        assertCode("nil", Elixir.NilLit(p0))
    }

    @Test
    fun valuesTheBeamCannotHold() {
        assertFailsWith<IllegalStateException> { elixirNumberText(Double.NaN) }
        assertFailsWith<IllegalStateException> { elixirNumberText(Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalStateException> { elixirStringText("lone \uD800 surrogate") }
        assertEquals("\"😀\"", elixirStringText("😀"))
    }

    @Test
    fun comment() {
        assertCode("# one\n#\n# two", Elixir.Comment(p0, "one\n\ntwo"))
    }
}
