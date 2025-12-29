package lang.temper.be.csharp

import lang.temper.format.toStringViaTokenSink
import lang.temper.log.Position
import lang.temper.log.UnknownCodeLocation
import lang.temper.name.OutName
import kotlin.test.Test
import kotlin.test.assertEquals

class YieldOrDoNotYieldThereIsNoTryTest {
    fun assertConverted(
        csharp: CSharp.BlockStatement,
        want: String,
    ) {
        var n = 0
        val yordnytisnt = YieldOrDoNotYieldThereIsNoTry { p, h ->
            CSharp.Identifier(p, OutName("${h}__${n++}", null))
        }
        val converted = yordnytisnt.convertFnBody(csharp)
        val got = toStringViaTokenSink(singleLine = false) {
            converted.formatTo(it)
        }
        assertEquals(want.trimEnd(), got.trimEnd())
    }

    private val p = Position(UnknownCodeLocation, 0, 0)

    @Test
    fun block() = assertConverted(
        block(
            call("f"),
            call("g"),
        ),
        """
            |f();
            |g();
        """.trimMargin(),
    )

    @Test
    fun yieldOutsideTry() = assertConverted(
        block(
            call("f"),
            yieldReturn(),
            call("g"),
        ),
        """
            |f();
            |yield return 0;
            |g();
        """.trimMargin(),
    )

    @Test
    fun yieldInsideTry() = assertConverted(
        CSharp.BlockStatement(
            p,
            listOf(
                CSharp.TryStatement(
                    p,
                    block(
                        call("f"),
                        yieldReturn(),
                        call("g"),
                    ),
                    catchBlock = block(call("h")),
                ),
            ),
        ),
        """
            |try {
            |  f();
            |} catch {
            |  goto CATCH__0;
            |}
            |yield return 0;
            |try {
            |  g();
            |} catch {
            |  goto CATCH__0;
            |}
            |goto OK__1;
            |CATCH__0: {
            |  h();
            |}
            |OK__1: {
            |}
        """.trimMargin(),
    )

    @Test
    fun nestedYieldsInIf() = assertConverted(
        CSharp.BlockStatement(
            p,
            listOf(
                CSharp.TryStatement(
                    p,
                    block(
                        CSharp.IfStatement(
                            p,
                            call("c").expr,
                            block(
                                call("f"),
                                yieldReturn(),
                            ),
                            block(
                                call("g"),
                                yieldReturn(),
                            ),
                        ),
                    ),
                    catchBlock = block(call("h")),
                ),
            ),
        ),
        """
            |bool cond__2;
            |try {
            |  cond__2 = c();
            |} catch {
            |  goto CATCH__0;
            |}
            |if (cond__2) {
            |  try {
            |    f();
            |  } catch {
            |    goto CATCH__0;
            |  }
            |  yield return 0;
            |} else {
            |  try {
            |    g();
            |  } catch {
            |    goto CATCH__0;
            |  }
            |  yield return 0;
            |}
            |goto OK__1;
            |CATCH__0: {
            |  h();
            |}
            |OK__1: {
            |}
        """.trimMargin(),
    )

    @Test
    fun nestedYieldInLoop() = assertConverted(
        block(
            CSharp.TryStatement(
                p,
                block(
                    CSharp.WhileStatement(
                        p,
                        call("keepRunning").expr,
                        block(
                            call("repeatedlyBeforeYield"),
                            yieldReturn(),
                            call("repeatedlyAfterYield"),
                        ),
                    ),
                ),
                block(
                    call("caught"),
                ),
                null,
            ),
        ),
        """
            |while (true) {
            |  bool cond__2;
            |  try {
            |    cond__2 = keepRunning();
            |    if (!cond__2) {
            |      break;
            |    }
            |    repeatedlyBeforeYield();
            |  } catch {
            |    goto CATCH__0;
            |  }
            |  yield return 0;
            |  try {
            |    repeatedlyAfterYield();
            |  } catch {
            |    goto CATCH__0;
            |  }
            |}
            |goto OK__1;
            |CATCH__0: {
            |  caught();
            |}
            |OK__1: {
            |}
        """.trimMargin(),
    )

    @Test
    fun exceptionInCatchGoToOuter() = assertConverted(
        block(
            CSharp.TryStatement(
                p,
                block(
                    localInt("i", call("f").expr),
                    call("a", id("i")),
                    yieldReturn(),
                    call("b", id("i")),
                    CSharp.TryStatement(
                        p,
                        block(
                            call("c"),
                            yieldReturn(),
                            call("d"),
                        ),
                        catchBlock = block(
                            call("innerFailed"),
                        ),
                        finallyBlock = block(
                            call("afterInner"),
                        ),
                    ),
                ),
                catchBlock = block(
                    call("outerFailed"),
                ),
                finallyBlock = block(
                    call("afterOuter"),
                ),
            ),
        ),
        """
            |try {
            |  int i;
            |  try {
            |    i = f();
            |    a(i);
            |  } catch {
            |    goto CATCH__0;
            |  }
            |  yield return 0;
            |  try {
            |    b(i);
            |  } catch {
            |    goto CATCH__0;
            |  }
            |  try {
            |    try {
            |      c();
            |    } catch {
            |      goto CATCH__2;
            |    }
            |    yield return 0;
            |    try {
            |      d();
            |    } catch {
            |      goto CATCH__2;
            |    }
            |    goto OK__3;
            |    CATCH__2: {
            |      innerFailed();
            |    }
            |    OK__3: {
            |    }
            |  } finally {
            |    afterInner();
            |  }
            |  goto OK__1;
            |  CATCH__0: {
            |    outerFailed();
            |  }
            |  OK__1: {
            |  }
            |} finally {
            |  afterOuter();
            |}
        """.trimMargin(),
    )

    private fun id(idNameText: String) = CSharp.Identifier(p, OutName(idNameText, null))

    private fun localInt(idNameText: String, init: CSharp.Expression? = null) =
        local("int", idNameText, init)

    private fun local(
        primTypeName: String,
        idNameText: String,
        init: CSharp.Expression? = null,
    ) = CSharp.LocalVariableDecl(
        p,
        CSharp.UnboundType(id(primTypeName)),
        listOf(
            CSharp.VariableDeclarator(
                p,
                id(idNameText),
                init,
            ),
        ),
    )

    private fun call(idNameText: String, vararg args: CSharp.Expression) = CSharp.ExpressionStatement(
        p,
        CSharp.InvocationExpression(
            p,
            id(idNameText),
            emptyList(),
            args.toList(),
        ),
    )

    private fun yieldReturn() = CSharp.YieldReturn(
        p,
        CSharp.NumberLiteral(p, 0),
    )

    private fun block(vararg s: CSharp.Statement) =
        CSharp.BlockStatement(p, s.toList())
}
