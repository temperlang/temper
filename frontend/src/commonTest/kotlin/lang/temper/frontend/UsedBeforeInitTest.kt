package lang.temper.frontend

import lang.temper.common.Console
import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.SnapshotKey
import lang.temper.common.Snapshotter
import lang.temper.common.assertStructure
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.testCodeLocation
import lang.temper.common.testModuleName
import lang.temper.common.withCapturingConsole
import lang.temper.frontend.staging.ModuleAdvancer
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.frontend.staging.partitionSourceFilesIntoModules
import lang.temper.fs.FileFilterRules
import lang.temper.fs.FilteringFileSystemSnapshot
import lang.temper.fs.MemoryFileSystem
import lang.temper.fs.loadResource
import lang.temper.lexer.withTemperAwareExtension
import lang.temper.log.Debug
import lang.temper.name.BuiltinName
import lang.temper.name.ModuleName
import lang.temper.name.PseudoCodeNameRenumberer
import lang.temper.stage.Stage
import lang.temper.tests.FunctionalTests
import lang.temper.value.TBoolean
import lang.temper.value.Value
import lang.temper.value.toPseudoCode
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class UsedBeforeInitTest {

    @Test
    fun ok() {
        val r = doCheck(
            """
                |do {
                |  let x = randomInt(1, 10);
                |  x
                |}
            """.trimMargin(),
        )
        assertStructure(
            """
                |{}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun assignInBranch() {
        val r = doCheck(
            """
                |do {
                |  let x;
                |  if (randomBool()) {
                |    x = 1;
                |  } else {
                |    x = 2;
                |  }
                |  x
                |}
            """.trimMargin(),
        )
        assertStructure(
            """
                |{}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun notAssignedAlongHalfOfIf() {
        val r = doCheck(
            """
                |do {
                |  let a, b;
                |  if (randomBool()) {
                |    a = randomInt(0, 10);
                |  } else {
                |    b = randomInt(-10, 0);
                |  }
                |  a + b
                |}
            """.trimMargin(),
        )
        assertStructure(
            """
                |{
                |  consoleOutput: ```
                |      8: a + b
                |         ⇧
                |      [test/test.temper:8+2-3]@T: a__0 is not initialized along branches at [:5+9 - 6+25]
                |        ┏━━━━━━━┓
                |      5:┃} else {
                |      6:┃  b = randomInt(-10, 0);
                |        ┗━━━━━━━━━━━━━━━━━━━━━━┛
                |      8: a + b
                |             ⇧
                |      [test/test.temper:8+6-7]@T: b__0 is not initialized along branches at [:3+20 - 4+24]
                |        ┏━━━━━━━━━━━━━━━━━━┓
                |      3:┃if (randomBool()) {
                |      4:┃  a = randomInt(0, 10);
                |        ┗━━━━━━━━━━━━━━━━━━━━━┛
                |
                |      ```
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun notAssignedAlongMissingElse() {
        val r = doCheck(
            """
                |do {
                |  let x;
                |  if (randomBool()) {
                |    x = randomInt(0, 10);
                |  } // nothing else
                |  console.log(x.toString());
                |}
            """.trimMargin(),
        )
        assertStructure(
            """
                |{
                |  consoleOutput: ```
                |      6: console.log(x.toString());
                |                     ⇧
                |      [test/test.temper:6+14-15]@T: x__0 is not initialized along branches at [:5+3]
                |      5: } // nothing else
                |          ⇧
                |
                |      ```
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun type() {
        val r = doCheck(
            """
                |class C {}
                |new C()
            """.trimMargin(),
        )
        assertStructure(
            """
                |{}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun fn() {
        val r = doCheck(
            """
                |// It's ok for f to depend on g as long
                |// as f is not read until after g is initialized.
                |do {
                |  let f(): Int { g() + 1 }
                |  let g(): Int { randomInt(0, 4) }
                |  f() + g()
                |}
            """.trimMargin(),
        )
        assertStructure(
            """
                |{}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun genericTypeDeclaration() {
        val r = doCheck(
            """
                |interface I<T> {}
            """.trimMargin(),
        )
        assertStructure(
            """
                |{}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun genericFn() {
        val r = doCheck(
            """
                |export let identity<T>(x: T) { x }
            """.trimMargin(),
        )
        assertStructure(
            """
                |{}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun nestedClass() {
        val r = doCheck(
            """
                |let f(): AnyValue {
                |  class C(public x: Int) {}
                |  return new C(42);
                |}
            """.trimMargin(),
        )
        assertStructure(
            """
                |{}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun orElseALot() {
        val r = doCheck(
            """
                |do {
                |  let d: Deque<String?> = new Deque<String?>();
                |  while (true) {
                |    let s: String? = d.removeFirst() orelse "orelse";
                |  }
                |}
            """.trimMargin(),
        )
        assertStructure(
            """
                |{
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun twoIfsInTandem() {
        val r = doCheck(
            """
                |do {
                |  var t1 = randomBool();
                |  let toLogOrNotToLog = t1;
                |  if (toLogOrNotToLog) {
                |    console.log("j");
                |  }
                |  if (toLogOrNotToLog) {
                |    console.log("k");
                |  }
                |}
            """.trimMargin(),
        )

        assertStructure(
            """
                |{}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun multipleExitingBranches() {
        val r = doCheck(
            """
                |(randomInt(0, 100) / randomInt(0, 10)) orelse -1
                |
            """.trimMargin(),
            moduleResultNeeded = true,
        )
        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    let return__0;
                |    var t#0, t#1, fail#0;
                |    orelse#0: {
                |      t#0 = hs(fail#0, randomInt(0, 100) / randomInt(0, 10));
                |      if (fail#0) {
                |        break orelse#0;
                |      };
                |      t#1 = t#0
                |    } orelse {
                |      t#1 = -1
                |    };
                |    return__0 = t#1
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun isFastEnough() {
        val r = doCheck(
            loadResource(
                FunctionalTests.AlgosMyersDiff,
                "algos/myers-diff/myers-diff.temper.md",
            ),
            extension = ".temper.md",
        )
        assertStructure("{}", r)
        assertTrue(r.timeTaken < 10.seconds, "Took ${r.timeTaken}")
    }

    @Test
    fun lotsOfLoops() {
        val r = doCheck(
            """
                |let f(): Void {
                |  var data = 0;
                |  for (var x: Int = 0; x <= 1; x += 1) {
                |    console.log("a");
                |    for (var y: Int = 75; y <= 77; y += 1) {
                |      console.log("b");
                |      if ((data++) >= 76) {
                |        console.log("c");
                |        break;
                |      } else {
                |        continue;
                |      }
                |    }
                |    console.log("d");
                |  }
                |}
                |
            """.trimMargin(),
        )
        assertStructure(
            "{}",
            r,
        )
    }

    private fun renamer() =
        PseudoCodeNameRenumberer.newStructurePostProcessor()

    // Use the renumberer for identifiers in source code
    private fun assertStructure(want: String, got: Structured) {
        assertStructure(want, got, postProcessor = { renamer()(it) })
    }

    private fun doCheck(
        input: String,
        extension: String = ".temper",
        moduleResultNeeded: Boolean = false,
    ): UseBeforeInitResult {
        val logSink = ListBackedLogSink()
        val moduleConfig = ModuleConfig(
            moduleCustomizeHook = { module, isNew ->
                if (isNew) {
                    module.addEnvironmentBindings(
                        mapOf(
                            // Expose a well-typed builtin that we can use for conditions in test code
                            // which does not collapse any control flow.
                            BuiltinName(RandomBool.name) to Value(RandomBool),
                            BuiltinName(RandomInt.name) to Value(RandomInt),
                            StagingFlags.moduleResultNeeded to TBoolean.value(moduleResultNeeded),
                        ),
                    )
                }
            },
        )
        val moduleAdvancer = ModuleAdvancer(logSink, moduleConfig = moduleConfig)
        var t0 = TimeSource.Monotonic.markNow()
        var t1 = t0
        var pseudoCodeBefore: String? = null
        var pseudoCodeAfter: String? = null
        val captureAfterCleanupTemporaries = object : Snapshotter {
            override fun <IR : Structured> snapshot(key: SnapshotKey<IR>, stepId: String, state: IR) {
                when (stepId) {
                    Debug.Frontend.TypeStage.AfterTyper.loggerName -> {
                        AstSnapshotKey.useIfSame(key, state) {
                            t0 = TimeSource.Monotonic.markNow()
                            pseudoCodeBefore = it.toPseudoCode(singleLine = false)
                        }
                    }
                    Debug.Frontend.TypeStage.AfterUseBeforeInit.loggerName ->
                        AstSnapshotKey.useIfSame(key, state) {
                            t1 = TimeSource.Monotonic.markNow()
                            pseudoCodeAfter = it.toPseudoCode(singleLine = false)
                        }
                }
            }
        }

        val (makeResult, consoleOutput) = withCapturingConsole { cConsole ->
            val moduleConsole = Console(
                textOutput = cConsole.textOutput,
                logLevel = Log.Warn,
                snapshotter = captureAfterCleanupTemporaries,
            )

            val sourceTree = MemoryFileSystem()
            sourceTree.write(testCodeLocation.withTemperAwareExtension(extension), input.toByteArray())
            partitionSourceFilesIntoModules(
                FilteringFileSystemSnapshot(sourceTree, FileFilterRules.Allow),
                moduleAdvancer,
                logSink,
                cConsole,
                root = testModuleName.libraryRoot(),
            )

            val module = moduleAdvancer.getAllModules().first {
                (it.loc as ModuleName).sourceFile == testModuleName.libraryRoot()
            }

            Debug.configure(module, moduleConsole)
            moduleAdvancer.advanceModules(stopBefore = Stage.after(Stage.Type))
            Debug.configure(module, consoleForKey = null)

            val makeResult = { consoleOutput: String ->
                UseBeforeInitResult(
                    pseudoCodeBefore = pseudoCodeBefore ?: MISSING_PSEUDO_CODE_PLACEHOLDER,
                    pseudoCodeAfter = pseudoCodeAfter ?: MISSING_PSEUDO_CODE_PLACEHOLDER,
                    consoleOutput = consoleOutput,
                    ok = module.ok,
                    timeTaken = t1 - t0,
                )
            }
            makeResult
        }
        return makeResult(consoleOutput)
    }
}

private data class UseBeforeInitResult(
    val pseudoCodeBefore: String,
    val pseudoCodeAfter: String,
    val consoleOutput: String,
    val ok: Boolean,
    val timeTaken: Duration,
) : Structured {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("pseudoCodeBefore", Hints.u) { value(pseudoCodeBefore) }
        key("pseudoCodeAfter", Hints.u) { value(pseudoCodeAfter) }
        key("consoleOutput", isDefault = consoleOutput.isEmpty()) { value(consoleOutput) }
        key("ok", isDefault = ok) { value(ok) }
    }
}
