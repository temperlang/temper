package lang.temper.be.py

import kotlin.test.Test
import kotlin.test.assertEquals
import lang.temper.log.unknownPos as p0

class ExtraGrammarTests {
    private fun assertRenders(tree: Py.Tree, expected: String) {
        assertEquals(expected.trimIndent() + "\n", formatAst(tree))
    }

    // Use getters because nodes aren't reusable.
    private val foo get() = Py.Name(p0, PyIdentifierName("foo"))
    private val bar get() = Py.Name(p0, PyIdentifierName("bar"))
    private val qux get() = Py.Identifier(p0, PyIdentifierName("qux"))
    private val comment get() = Py.CommentLine(p0, "test")
    private val pass get() = Py.Pass(p0)

    @Test
    fun forLoop1() {
        assertRenders(
            Py.For(p0, foo, bar, listOf(), listOf()),
            """
            for foo in bar:
                pass
            """,
        )
    }

    @Test
    fun forLoop2() {
        assertRenders(
            Py.For(p0, foo, bar, listOf(pass), listOf(pass)),
            """
            for foo in bar:
                pass
            else:
                pass
            """,
        )
    }

    @Test
    fun forLoop3() {
        assertRenders(
            Py.For(p0, foo, bar, listOf(comment), listOf(comment)),
            """
            for foo in bar:
                # test
                pass
            else:
                # test
                pass
            """,
        )
    }

    @Test
    fun forLoop4() {
        assertRenders(
            Py.For(p0, foo, bar, listOf(pass, comment), listOf(pass, comment)),
            """
            for foo in bar:
                pass
                # test
            else:
                pass
                # test
            """,
        )
    }

    @Test
    fun forLoop5() {
        assertRenders(
            Py.For(p0, foo, bar, listOf(comment, comment), listOf(comment, comment)),
            """
            for foo in bar:
                # test
                # test
                pass
            else:
                # test
                # test
                pass
            """,
        )
    }

    @Test
    fun whileLoop1() {
        assertRenders(
            Py.While(p0, foo, listOf(), listOf()),
            """
            while foo:
                pass
            """,
        )
    }

    @Test
    fun whileLoop2() {
        assertRenders(
            Py.While(p0, foo, listOf(pass), listOf(pass)),
            """
            while foo:
                pass
            else:
                pass
            """,
        )
    }

    @Test
    fun whileLoop3() {
        assertRenders(
            Py.While(p0, foo, listOf(comment), listOf(comment)),
            """
            while foo:
                # test
                pass
            else:
                # test
                pass
            """,
        )
    }

    @Test
    fun whileLoop4() {
        assertRenders(
            Py.While(p0, foo, listOf(pass, comment), listOf(pass, comment)),
            """
            while foo:
                pass
                # test
            else:
                pass
                # test
            """,
        )
    }

    @Test
    fun whileLoop5() {
        assertRenders(
            Py.While(p0, foo, listOf(comment, comment), listOf(comment, comment)),
            """
            while foo:
                # test
                # test
                pass
            else:
                # test
                # test
                pass
            """,
        )
    }

    @Test
    fun ifThen1() {
        assertRenders(
            Py.If(p0, foo, listOf(), listOf(Py.Elif(p0, bar, listOf())), listOf()),
            """
            if foo:
                pass
            elif bar:
                pass
            """,
        )
    }

    @Test
    fun ifThen2() {
        assertRenders(
            Py.If(p0, foo, listOf(pass), listOf(Py.Elif(p0, bar, listOf(pass))), listOf(pass)),
            """
            if foo:
                pass
            elif bar:
                pass
            else:
                pass
            """,
        )
    }

    @Test
    fun ifThen3() {
        assertRenders(
            Py.If(p0, foo, listOf(comment), listOf(Py.Elif(p0, bar, listOf(comment))), listOf(comment)),
            """
            if foo:
                # test
                pass
            elif bar:
                # test
                pass
            else:
                # test
                pass
            """,
        )
    }

    @Test
    fun ifThen4() {
        assertRenders(
            Py.If(
                p0,
                foo,
                listOf(pass, comment),
                listOf(Py.Elif(p0, bar, listOf(pass, comment))),
                listOf(pass, comment),
            ),
            """
            if foo:
                pass
                # test
            elif bar:
                pass
                # test
            else:
                pass
                # test
            """,
        )
    }

    private val withItem = listOf(Py.WithItem(p0, foo))

    @Test
    fun withBlock1() {
        assertRenders(
            Py.With(p0, withItem, listOf()),
            """
            with foo:
                pass
            """,
        )
    }

    @Test
    fun withBlock2() {
        assertRenders(
            Py.With(p0, withItem, listOf(pass)),
            """
            with foo:
                pass
            """,
        )
    }

    @Test
    fun withBlock3() {
        assertRenders(
            Py.With(p0, withItem, listOf(comment)),
            """
            with foo:
                # test
                pass
            """,
        )
    }

    @Test
    fun withBlock4() {
        assertRenders(
            Py.With(p0, withItem, listOf(pass, comment)),
            """
            with foo:
                pass
                # test
            """,
        )
    }

    private fun except(vararg stmt: Py.Stmt) =
        listOf(Py.ExceptHandler(p0, body = stmt.toList()))

    @Test
    fun tryBlock1() {
        assertRenders(
            Py.Try(p0, listOf(), except(), listOf(), listOf()),
            """
            try:
                pass
            except:
                pass
            """,
        )
    }

    @Test
    fun tryBlock2() {
        assertRenders(
            Py.Try(p0, listOf(pass), except(pass), listOf(pass), listOf(pass)),
            """
            try:
                pass
            except:
                pass
            else:
                pass
            finally:
                pass
            """,
        )
    }

    @Test
    fun tryBlock3() {
        assertRenders(
            Py.Try(p0, listOf(comment), except(comment), listOf(comment), listOf(comment)),
            """
            try:
                # test
                pass
            except:
                # test
                pass
            else:
                # test
                pass
            finally:
                # test
                pass
            """,
        )
    }

    @Test
    fun tryBlock4() {
        assertRenders(
            Py.Try(p0, listOf(pass, comment), except(pass, comment), listOf(pass, comment), listOf(pass, comment)),
            """
            try:
                pass
                # test
            except:
                pass
                # test
            else:
                pass
                # test
            finally:
                pass
                # test
            """,
        )
    }

    @Test
    fun classDef1() {
        assertRenders(
            Py.ClassDef(p0, name = qux, args = listOf(), body = listOf()),
            """
            class qux:
                pass
            """,
        )
    }

    @Test
    fun classDef2() {
        assertRenders(
            Py.ClassDef(p0, name = qux, args = listOf(), body = listOf(pass)),
            """
            class qux:
                pass
            """,
        )
    }

    @Test
    fun classDef3() {
        assertRenders(
            Py.ClassDef(p0, name = qux, args = listOf(), body = listOf(comment)),
            """
            class qux:
                # test
                pass
            """,
        )
    }

    @Test
    fun classDef4() {
        assertRenders(
            Py.ClassDef(p0, name = qux, args = listOf(), body = listOf(pass, comment)),
            """
            class qux:
                pass
                # test
            """,
        )
    }

    @Test
    fun classDef5() {
        assertRenders(
            Py.ClassDef(p0, name = qux, args = listOf(), body = listOf(comment, comment)),
            """
            class qux:
                # test
                # test
                pass
            """,
        )
    }

    private val noArgs get() = Py.Arguments(p0, listOf())

    @Test
    fun funDef1() {
        assertRenders(
            Py.FunctionDef(p0, name = qux, args = noArgs, body = listOf()),
            """
            def qux():
                pass
            """,
        )
    }

    @Test
    fun funDef2() {
        assertRenders(
            Py.FunctionDef(p0, name = qux, args = noArgs, body = listOf(pass)),
            """
            def qux():
                pass
            """,
        )
    }

    @Test
    fun funDef3() {
        assertRenders(
            Py.FunctionDef(p0, name = qux, args = noArgs, body = listOf(comment)),
            """
            def qux():
                # test
                pass
            """,
        )
    }

    @Test
    fun funDef4() {
        assertRenders(
            Py.FunctionDef(p0, name = qux, args = noArgs, body = listOf(pass, comment)),
            """
            def qux():
                pass
                # test
            """,
        )
    }

    @Test
    fun funDef5() {
        assertRenders(
            Py.FunctionDef(p0, name = qux, args = noArgs, body = listOf(comment, comment)),
            """
            def qux():
                # test
                # test
                pass
            """,
        )
    }
}
