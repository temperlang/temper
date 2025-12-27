package lang.temper.be.cli

import lang.temper.common.ClosedOpenRange
import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.console
import lang.temper.common.flatMap
import lang.temper.common.invoke
import lang.temper.name.PartialSemVer
import lang.temper.name.SemVer
import java.io.PrintWriter
import java.io.StringWriter

/** Check that a parsed result is within a given range. */
fun RResult<SemVer, IllegalArgumentException>.check(
    run: EffortSuccess,
    range: ClosedOpenRange<PartialSemVer>,
): RResult<Unit, CliFailure> =
    this.mapFailure<CliFailure> { SemVerParseError(run, cause = it) }
        .flatMap { semVer ->
            if (semVer in range) {
                RSuccess(Unit)
            } else {
                RFailure(CommandIncorrectVersion(semVer, range, run))
            }
        }

/** Check that a parsed result fulfills a given minimum. */
fun RResult<SemVer, IllegalArgumentException>.checkMin(
    run: EffortSuccess,
    min: PartialSemVer,
): RResult<Unit, CliFailure> =
    this.mapFailure<CliFailure> { SemVerParseError(run, cause = it) }
        .flatMap { semVer ->
            if (semVer >= min) {
                RSuccess(Unit)
            } else {
                RFailure(CommandOldVersion(semVer, min, run))
            }
        }

/** For dependent tools where we're not enforcing a version range. */
fun RResult<SemVer, IllegalArgumentException>.noCheck(
    run: EffortSuccess,
): RResult<Unit, CliFailure> = this.map(
    mapResult = {},
    mapFailure = { SemVerParseError(run, it) },
)

fun <E : EffortBase, F : CliFailure> RResult<E, F>.mapEffort(func: (EffortBase) -> E): RResult<E, F> =
    this.map({ effort ->
        func(effort)
    }, { err ->
        err.effort = err.effort?.let { func(it) }
        err
    })

/** Dump the standard result type. */
fun <E : EffortBase, F : CliFailure> RResult<E, F>.print(
    con: Console = console,
    successLevel: Log.Level = Log.Info,
    failureLevel: Log.Level = Log.Warn,
    asError: Boolean = false,
) {
    con.log(level = if (failure != null || asError) failureLevel else successLevel) {
        this@print.explain(true).joinToString("\n") { (header, advice) ->
            "## $header: $advice"
        }
    }
}

fun CliFailure.print(
    con: Console = console,
    level: Log.Level = Log.Warn,
) {
    con.log(level = level) {
        this@print.explain().joinToString("\n") { (header, advice) ->
            "## $header: $advice"
        }
    }
}

fun CliFailure.explain() = buildList {
    cause?.let { cause ->
        cause.message?.let { add("exception" to it) }
        StringWriter().also { sw ->
            PrintWriter(sw).also { pw ->
                cause.printStackTrace(pw)
            }.close()
            add("stackTrace" to sw.toString())
        }
    }
}

fun <E : EffortBase, F : CliFailure> RResult<E, F>.explain(asError: Boolean): List<Pair<String, String>> =
    buildList {
        this@explain { (effort, fail) ->
            when {
                effort != null -> addAll(effort.explain(advice = asError))
                fail != null -> {
                    addAll(explain("exception.", fail))
                    addAll(explain("exception.cause.", fail.cause))
                    fail.effort?.let {
                        addAll(it.explain(advice = true))
                    }
                }
            }
        }
    }

private fun explain(prefix: String, t: Throwable?): List<Pair<String, String>> = buildList {
    if (t != null) {
        add("${prefix}class" to t::class.java.canonicalName)
        t.message?.let { add("${prefix}msg" to it) }
        StringWriter().also { sw ->
            PrintWriter(sw).also { pw ->
                t.printStackTrace(pw)
            }.close()
            add("${prefix}trace" to sw.toString())
        }
    } else {
        add("${prefix}null" to "true")
    }
}

/** Get some kind of effort object. */
fun <E : EffortBase, F : CliFailure> RResult<E, F>.effort(): EffortBase? =
    when (this) {
        is RSuccess -> result
        is RFailure -> failure.effort
    }

/** the effort object should track a [CliEnv] and be able to clean it up. */
fun <E : EffortBase, F : CliFailure> RResult<E, F>.cleanup() {
    effort().cleanup()
}
