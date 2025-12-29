package lang.temper.be.lua

import lang.temper.be.TmplGenerator
import lang.temper.be.asTmpLType
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.cli.explain
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLOperator
import lang.temper.be.tmpl.aType
import lang.temper.common.RSuccess
import lang.temper.common.console
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.log.unknownPos
import lang.temper.type.WellKnownTypes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.fail

class LuaLocalsTest {
    private fun moduleWithCountLocals(count: Int): TmpL.Module {
        val gen = TmplGenerator(".lua")
        return gen.module {
            moduleFunction(gen.makeParsedName("main")) {
                body = TmpL.BlockStatement(
                    unknownPos,
                    buildList {
                        for (i in 1..count) {
                            add(
                                gen.localDecl(
                                    gen.makeId("num${i}"),
                                    WellKnownTypes.intType2.asTmpLType().aType,
                                    WellKnownTypes.intType2,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }

    private fun moduleWithFibLocals(n: Int): TmpL.Module {
        val gen = TmplGenerator(".lua")
        return gen.module {
            initBlock(
                TmpL.BlockStatement(
                    unknownPos,
                    buildList {
                        var numVals = 0
                        fun more(n: Int): TmpL.Expression {
                            val next = if (n < 2) {
                                gen.value(n)
                            } else {
                                TmpL.InfixOperation(
                                    unknownPos,
                                    more(n - 2),
                                    TmpL.InfixOperator(
                                        unknownPos,
                                        TmpLOperator.PlusInt,
                                    ),
                                    more(n - 1),
                                )
                            }
                            val ret = gen.makeId("val${numVals++}")
                            add(
                                gen.localDecl(
                                    name = ret,
                                    type = WellKnownTypes.intType2.asTmpLType().aType,
                                    descriptor = WellKnownTypes.intType2,
                                    init = next,
                                ),
                            )
                            return TmpL.Reference(unknownPos, ret, WellKnownTypes.intType2)
                        }
                        more(n)
                    },
                ),
            )
        }
    }

    private fun generateAndCountLocals(n: Int): List<Int> {
        val tmplAst = moduleWithCountLocals(n)
        val luaTranslator = LuaTranslator(
            luaNames = LuaNames(),
            luaClosureMode = LuaClosureMode.BasicFunction,
        )
        val backendAst = luaTranslator.translateTopLevel(tmplAst).first().content as Lua.Program
        val ls = mutableListOf<Int>()
        val counter = LuaLocalCounter {
            ls.add(it)
        }
        counter.countLocals(backendAst)
        return ls
    }

    @Test
    fun localCountSmall() {
        // figure out how many locals for the Lua.Program chunk
        val n0 = generateAndCountLocals(0)
        val n1 = generateAndCountLocals(1)
        for (numRealLocals in 0 until MAX_ALLOWABLE_LOCALS) {
            val localCounts = generateAndCountLocals(numRealLocals)
            // figure out how many lua locals for position $index and $numLocals tmpl.locals
            val wantedCounts = IntRange(0, localCounts.size - 1).map {
                numRealLocals * (n1[it] - n0[it]) + n0[it]
            }
            println("$localCounts <= $numRealLocals")
            assertEquals(
                wantedCounts,
                localCounts,
                "wrong numbers of locals generated: got $localCounts, wanted: $wantedCounts",
            )
        }
    }

    @Test
    fun countLocalsLarge() {
        var numRealLocals = MAX_ALLOWABLE_LOCALS
        val wantedCounts = generateAndCountLocals(MAX_ALLOWABLE_LOCALS)
        while (numRealLocals < 10_000) {
            val localCounts = generateAndCountLocals(numRealLocals)
            for (count in localCounts) {
                assert(count < MAX_ALLOWABLE_LOCALS) {
                    "the lua backend was supposed to reduce the number of locals"
                }
            }
            assertContentEquals(
                wantedCounts,
                localCounts,
                "the lua backend was supposed to handle having too many locals consistently",
            )
            numRealLocals *= 2
        }
    }

    @Test
    fun checkValidGeneratedCode() {
        val cancelGroup = makeCancelGroupForTest()
        for (i in 1..15) {
            val mod = moduleWithFibLocals(i)
            val luat = LuaTranslator(
                luaNames = LuaNames(),
                luaClosureMode = LuaClosureMode.BasicFunction,
            )
            val tree = luat.translateTopLevel(mod)
            val code = "${tree.first().content}"
            val result = CliEnv.using(
                Lua51Specifics,
                ShellPreferences.functionalTests(console),
                cancelGroup,
            ) {
                copyLuaTemperCore(LuaBackend.Lua51, prefix = listOf())
                val luas = specifics as Lua51Specifics
                luas.runSingleSource(
                    cliEnv = this,
                    code = code,
                    env = mapOf(),
                    aux = mapOf(),
                )
            }
            if (result !is RSuccess) {
                val message = result.explain(asError = true)
                    .joinToString("\n\n") { (key, value) -> "$key: $value" }
                fail(message)
            }
        }
    }
}
