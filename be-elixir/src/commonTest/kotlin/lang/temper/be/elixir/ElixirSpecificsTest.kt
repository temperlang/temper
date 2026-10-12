package lang.temper.be.elixir

import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.EffortSuccess
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.cli.explain
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.console
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.log.FilePath
import lang.temper.log.filePath
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * [ElixirSpecifics.runSingleSource], the way `RegexMatchTest` and
 * `LuaLocalsTest` use it for their backends: one source string, run with
 * the runtime available, judged by its stdout.
 */
class ElixirSpecificsTest {
    private fun run(
        code: String,
        env: Map<String, String> = mapOf(),
        aux: Map<Aux, FilePath> = mapOf(),
    ): RResult<EffortSuccess, CliFailure> =
        CliEnv.using(ElixirSpecifics, ShellPreferences.functionalTests(console), makeCancelGroupForTest()) {
            ElixirSpecifics.runSingleSource(this, code, env = env, aux = aux)
        }

    private fun stdout(result: RResult<EffortSuccess, CliFailure>): String = when (result) {
        is RSuccess -> result.result.stdout
        is RFailure -> fail(result.explain(asError = true).joinToString("\n\n") { (key, value) -> "$key: $value" })
    }

    @Test
    fun runsWithTemperCoreAndItsApplication() {
        // TemperCore.int32 is the runtime; :temper_globals is the ETS table
        // TemperCore.Application makes, so the application has started too.
        // Nothing of Mix's compile reaches stdout.
        val result = run(
            """
            IO.puts(TemperCore.int32(2_147_483_648))
            IO.puts(:ets.info(:temper_globals, :name))
            IO.puts(System.get_env("PATTERN_MATCHES"))
            """.trimIndent(),
            env = mapOf("PATTERN_MATCHES" to "[\"a\",\"b\"]"),
        )
        assertEquals("-2147483648\ntemper_globals\n[\"a\",\"b\"]\n", stdout(result))
    }

    @Test
    fun aRaiseIsAFailureWithItsMessage() {
        val result = run(
            "raise \"single source fails\"",
            aux = mapOf(Aux.Stderr to filePath("stderr.txt")),
        )
        if (result !is RFailure) fail("expected a failure, got $result")
        assertContains((result.failure.effort as? EffortSuccess)?.auxOut?.get(Aux.Stderr) ?: "", "single source fails")
    }
}
