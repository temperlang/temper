@file:JvmName("UpdateGeneratedCode")

package lang.temper.kcodegen

import lang.temper.common.ignore
import lang.temper.kcodegen.functests.FunctionalTestSuitener
import lang.temper.kcodegen.lognames.LoggerNameRegistryGenerator
import lang.temper.kcodegen.vscode.syntax.VscodeGrammarGenerator
import kotlin.jvm.JvmName

fun main(argv: Array<String>) {
    ignore(argv)
    LoggerNameRegistryGenerator.bringUpToDate()
    for (subProject in OutputGrammarCodeGenerator.subProjects) {
        OutputGrammarCodeGenerator(subProject).bringUpToDate()
    }
    VscodeGrammarGenerator.bringUpToDate()
    FunctionalTestSuitener.bringUpToDate()
    object : ShellJob() {
        override val sourceTargets: Iterable<SrcTgt> = listOf(
            SrcTgt(
                Path.of("be-py", "scripts", "make_tests.py"),
                Path.of("be-py", "scripts", "ast_tests.py"),
                Path.of("be-py", "src", "commonTest", "kotlin", "lang", "temper", "be", "py", "OutGrammarTest.kt"),
            ),
        )

        @Suppress("SwallowedException")
        override val interpreter: Interpreter = try {
            which("python3")
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Try fallback for Windows.
            // Suppression above because I can't say FileNotFoundException in commonMain.
            which("python.exe")
        }
    }.run()
}
