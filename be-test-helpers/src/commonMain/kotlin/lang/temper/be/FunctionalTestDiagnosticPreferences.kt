package lang.temper.be

import lang.temper.be.tmpl.TmpL
import lang.temper.frontend.Module

/**
 * Simplifies debugging of functional tests.
 *
 * When debugging you can override [FunctionalTestRunner.getDiagnosticPreferences]
 * to return preferences to dump diagnostic trace.
 *
 * This can make it easier to diff two versions of the backend or the larger TmpL
 * framework
 */
data class FunctionalTestDiagnosticPreferences(
    val shouldPrintModule: (Module) -> Boolean = doNotDoThatThing,
    val shouldPrintFinishedTmpL: (TmpL.Module) -> Boolean = doNotDoThatThing,
    val shouldPrintOutputFile: (Backend.OutputFileSpecification) -> Boolean = doNotDoThatThing,
) {
    companion object {
        val defaultPreferences = FunctionalTestDiagnosticPreferences()

        internal fun <T> mightDoSomething(predicate: (T) -> Boolean) =
            predicate !== doNotDoThatThing
    }
}

private val doNotDoThatThing: (Any?) -> Boolean = { _ -> false }
