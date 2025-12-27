package lang.temper.frontend

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.common.AppendingTextOutput
import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.common.TestWebServer
import lang.temper.common.assertStringsEqual
import lang.temper.common.console
import lang.temper.common.currents.CancelGroup
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.common.printStackTraceBestEffort
import lang.temper.frontend.define.testReportExportName
import lang.temper.log.CodeLocationKey
import lang.temper.log.FailLog
import lang.temper.log.FileRelatedCodeLocation
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.SharedLocationContext
import lang.temper.log.excerpt
import lang.temper.log.toReadablePosition
import lang.temper.name.BackendId
import lang.temper.name.ExportedName
import lang.temper.name.interpBackendId
import lang.temper.stage.Stage
import lang.temper.tests.FunctionalTestBase
import lang.temper.tests.FunctionalTestSuiteI
import lang.temper.tests.assertTestingTestFromJunit
import lang.temper.tests.prepareModulesForFunctionalTest
import lang.temper.type.mentionsInvalid
import lang.temper.value.Abort
import lang.temper.value.DependencyCategory
import lang.temper.value.Fail
import lang.temper.value.NotYet
import lang.temper.value.Panic
import lang.temper.value.TBoolean
import lang.temper.value.TString
import lang.temper.value.Tree
import lang.temper.value.toLispy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

class InterpreterFunctionalTests : FunctionalTestSuiteI {
    override val backendId: BackendId get() = interpBackendId
    override val cancelGroup: CancelGroup get() = _cancelGroup!!

    private var _cancelGroup: CancelGroup? = null

    @BeforeTest
    fun makeCancelGroup() {
        _cancelGroup = makeCancelGroupForTest()
    }

    @AfterTest
    fun tearDown() {
        _cancelGroup = null
    }

    @Test
    override fun algosHelloWorld() {
        super.algosHelloWorld()
    }

    override fun runFunctionalTest(
        test: FunctionalTestBase,
        verbose: Boolean,
    ) {
        val preparedTest = prepareModulesForFunctionalTest(
            test,
            enforceStaticErrors = true,
            customizeModule = { module, _ ->
                module.addEnvironmentBindings(server.makeBindings())
                if (
                    test.runAsTest &&
                    module.dependencyCategory == DependencyCategory.Test &&
                    module.nextStage == Stage.Define
                ) {
                    module.addEnvironmentBindings(
                        mapOf(StagingFlags.defineStageHookCreateAndRunClasses to TBoolean.valueTrue),
                    )
                }
            },
        )
        val modules = preparedTest.modules
        val mainModule = preparedTest.mainModule
        val projectLogSink = preparedTest.projectLogSink
        val stdout = preparedTest.stdout

        try {
            for (module in modules) {
                if (module.stageCompleted == Stage.Run) {
                    // already done
                } else if (module.canAdvance() && module.nextStage == Stage.Run) {
                    module.advance()
                } else {
                    stdout.append("${module.loc} NOT RUN\n")
                }
                if (!module.ok) {
                    stdout.append("${module.loc} IS NOT OK\n")
                } else if (module.generatedCode?.mentionsInvalid == true && test.allowedErrors.isEmpty()) {
                    Console(AppendingTextOutput(stdout)).run {
                        group("${module.loc} MENTIONS INVALID TYPES") {
                            module.generatedCode?.toLispy(
                                multiline = true,
                                includeTypeInfo = true,
                            )?.let { log(it) }
                        }
                    }
                }
            }
            for (e in projectLogSink.allEntries) {
                if (e.template == MessageTemplate.StandardOut) {
                    stdout.append(e.values.joinToString(" ; "))
                    stdout.append('\n')
                }
            }
        } catch (e: Panic) {
            stdout.append("Panic!!!\n")
            e.printStackTraceBestEffort()
        } catch (e: Abort) {
            stdout.append("Abort!!!\n")
            e.printStackTraceBestEffort()
        }

        if (test.expectRunFailure) {
            // Test and exit early if we expect failure.
            assertIs<Fail>(mainModule.runResult)
            return
        }

        // Common case checking for expected success.
        val (dumpResults, resultText) =
            when (mainModule.runResult) {
                is Fail -> true to "Fail"
                is NotYet -> true to "NotYet"
                else -> false to null
            }

        if (dumpResults) {
            stdout.append("----------------\n")
            stdout.append(resultText)
            stdout.append('\n')
            dump(mainModule.failLog, mainModule.sharedLocationContext)
        }

        when {
            test.runAsTest -> {
                val reportName = ExportedName(mainModule.namingContext, testReportExportName)
                // Expect these things for any runAsTest funtest.
                val reportText = TString.unpack(mainModule.exports!!.find { it.name == reportName }!!.value!!)
                assertTestingTestFromJunit(
                    test = test,
                    junitOutput = reportText,
                    stdout = null,
                )
            }
            else -> assertStringsEqual(
                test.expectedOutput,
                "$stdout",
            )
        }
    }

    companion object {
        private val server = TestWebServer()

        @BeforeAll
        @JvmStatic
        fun startServer() = server.start()

        @AfterAll
        @JvmStatic
        fun stopServer() = server.stop()
    }
}

fun dump(failLog: FailLog, sharedLocationContext: SharedLocationContext) {
    replayLessSpammy(
        failLog,
        object : LogSink {
            override var hasFatal = false
                private set

            override fun log(
                level: Log.Level,
                template: MessageTemplateI,
                pos: Position,
                values: List<Any>,
                fyi: Boolean,
            ) {
                if (level >= Log.Fatal) {
                    hasFatal = true
                }
                val loc = pos.loc
                val inputText =
                    sharedLocationContext[loc, CodeLocationKey.SourceCodeKey]
                if (inputText != null) {
                    excerpt(pos, inputText, console.textOutput)
                }
                val filePositions =
                    sharedLocationContext[loc, CodeLocationKey.FilePositionsKey]
                val fileName = (loc as? FileRelatedCodeLocation)?.sourceFile?.toString()
                val posStr = if (filePositions != null && fileName != null) {
                    filePositions.filePositionAtOffset(pos.left).toReadablePosition(fileName)
                } else {
                    "$pos"
                }
                console.log("$posStr: ${template.format(values)}", level = level)
            }
        },
    )
}

private fun replayLessSpammy(failLog: FailLog, logSink: LogSink) {
    failLog.logReasonForFailure(
        object : LogSink {
            private var lastInterpretingPos: Position? = null

            override val hasFatal: Boolean
                get() = throw IllegalStateException("Why are you asking me?")

            override fun log(
                level: Log.Level,
                template: MessageTemplateI,
                pos: Position,
                values: List<Any>,
                fyi: Boolean,
            ) {
                lastInterpretingPos =
                    if (template == MessageTemplate.Interpreting && values.isEmpty()) {
                        // Filter out adjacent messages about interpreting trees with
                        // the same position.  This visual clutter happens due to
                        // wrapper trees with the same position as their sole child.
                        val last = lastInterpretingPos
                        if (last == pos) {
                            return
                        }
                        pos
                    } else {
                        null
                    }
                logSink.log(level, template, pos, values, fyi = fyi)
            }
        },
    )
}

private val Tree.mentionsInvalid: Boolean
    get() {
        var invalid = false
        TreeVisit.startingAt(this)
            .forEach { t ->
                if (t.typeInferences?.type?.mentionsInvalid == true) {
                    invalid = true
                    VisitCue.AllDone
                } else {
                    VisitCue.Continue
                }
            }
            .visitPreOrder()
        return invalid
    }
