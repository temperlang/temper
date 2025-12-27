package lang.temper.tests

import lang.temper.common.putMulti
import lang.temper.name.BackendId

typealias IssueCheckList = MutableList<IssueCheck>

/**
 * Tracks tests that have unresolved issues.
 * See the [issue], [onlyPasses], [onlyFails] helpers in this file.
 * See also `QuickTests.kt` to create a new function to represent issues.
 */
@Suppress("MagicNumber") // Issue numbers to skip
val functionalTestStatus: Map<Ft, List<IssueCheck>> = buildMap {
    // Make sure all entries are populated.
    Ft.entries.forEach { this[it] = mutableListOf() }

    // Add issues here. See [issue], [onlyPasses] and [onlyFails].
    issue(
        Ft.SemanticsTypeCheckedLocals,
        kotlinMultiplatformIssue,
        staticallyTypeds(58),
    )
    issue(Ft.ControlFlowAsync, lua(144))
    issue(Ft.RegexZeroAdvance, lua(166))
    issue(Ft.NamesNonascii, lua(228))
    214.let { issue(Ft.TypesNetresponse, cpp(it), interp(it), lua(it)) }
    onlyPasses(
        cpp(198),
    )
    onlyPasses(
        cpp03(198),
        Ft.AlgosFibonacci,
        Ft.AlgosHelloWorld,
        Ft.TypesIntBasics,
        Ft.TypesIntLimits,
    )
}.mapValues { it.value.toList() }

/**
 * Add issues to a particular test. Usage:
 *
 *     val functionalTestStatus: blah blah {
 *        issue(Ft.SomeTest, py(1234), js(1234))
 *     }.blah blah
 */
fun MutableMap<Ft, IssueCheckList>.issue(test: Ft, vararg issues: IssueCheck) {
    issues.forEach { this.putMulti(test, it, ::mutableListOf) }
}

/**
 * When creating a new backend it will only pass a few tests. To indicate this:
 *
 *     val functionalTestStatus: blah blah {
 *        onlyPasses(intercal(1234), Ft.AlgosHelloWorld, ft.TypeString)
 *     }.blah blah
 */
fun MutableMap<Ft, IssueCheckList>.onlyPasses(issue: IssueCheck, vararg passes: Ft) {
    val skip = passes.toSet()
    for ((t, i) in this.entries) {
        if (t !in skip) {
            i.add(issue)
        }
    }
}

/**
 * Once a backend hits the halfway mark, you may want to specify the remaining issues to fix.
 * To indicate this:
 *
 *     val functionalTestStatus: blah blah {
 *        onlyFail(intercal(1234), Ft.AlgosTravellingSalesman, Ft.AlgosHaltingProblem)
 *     }.blah blah
 */
fun MutableMap<Ft, IssueCheckList>.onlyFails(issue: IssueCheck, vararg fails: Ft) {
    for (t in fails) {
        putMulti(t, issue, ::mutableListOf)
    }
}

/** Check if this test should be run on this backend. */
internal fun dispo(test: Ft, backend: BackendId): Disposition {
    var runTest = true
    val issues: MutableSet<Int> = mutableSetOf()

    fun handle(check: IssueCheck) {
        when (val disposition = check(backend)) {
            null, is Disposition.Run -> {}
            is Disposition.Skip -> {
                runTest = false
                issues.addAll(disposition.issues)
            }
        }
    }

    for (check in functionalTestStatus.getOrElse(test, ::listOf)) {
        handle(check)
    }
    handle(defaultToRun)
    return if (runTest) {
        Disposition.Run
    } else {
        Disposition.Skip(issues.sorted())
    }
}
