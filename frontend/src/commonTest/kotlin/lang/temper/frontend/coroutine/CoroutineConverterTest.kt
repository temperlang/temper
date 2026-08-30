package lang.temper.frontend.coroutine

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.BuiltinFun
import lang.temper.builtin.BuiltinLogicalOperators
import lang.temper.builtin.makeTypeFormal
import lang.temper.common.Either
import lang.temper.common.ListBackedLogSink
import lang.temper.common.RFailure
import lang.temper.common.RSuccess
import lang.temper.common.assertStructure
import lang.temper.common.console
import lang.temper.common.ignore
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValue
import lang.temper.common.json.JsonValueBuilder
import lang.temper.common.json.buildJsonNestedObject
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.structure.reconcileStructure
import lang.temper.common.testModuleName
import lang.temper.env.InterpMode
import lang.temper.format.ValueSimplifyingLogSink
import lang.temper.frontend.DumpStackTracesForThoseErrors
import lang.temper.frontend.Module
import lang.temper.frontend.StageTestDir
import lang.temper.frontend.provisionModuleForStageTest
import lang.temper.frontend.stageTestDirFileRoot
import lang.temper.frontend.stageTestDirFileSourceRoot
import lang.temper.frontend.staging.ModuleAdvancer
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.frontend.structureBlock
import lang.temper.frontend.testLibraryName
import lang.temper.fs.Url
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.filePath
import lang.temper.name.BuiltinName
import lang.temper.name.PseudoCodeNameRenumberer
import lang.temper.name.ResolvedName
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.testdir.DataFileConverter
import lang.temper.testdir.FileContentStringConverter
import lang.temper.testdir.ParseJsonTolerantConverter
import lang.temper.testdir.TestResourceFileRelationship
import lang.temper.testdir.readTestDir
import lang.temper.testdir.regenerateFiles
import lang.temper.type.MkType
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.ActualValues
import lang.temper.value.BlockTree
import lang.temper.value.FunTree
import lang.temper.value.InterpreterCallback
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Value
import lang.temper.value.simplifyControlFlow
import lang.temper.value.toPseudoCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

private fun shouldRegenerateCoroConvertTest(
    stageTestDir: StageTestDir,
    isEmpty: Boolean,
): Boolean {
    // ignore() to suppress unused parameter warnings because in git this should just return false.
    ignore(stageTestDir)
    ignore(isEmpty)

    // Set to true temporarily if you want to regenerate the test output, which you should then check
    // with `git diff`.
    return false
}

class CoroutineConverterTest {
    @Test
    fun simpleCoro() = assertConvertedCoroutine(
        StageTestDir("convert-coro/simple-coro"),
    )

    @Test
    fun branchingCoroWithNestedNonYieldingIf() = assertConvertedCoroutine(
        StageTestDir("convert-coro/branching-coro-with-nested-non-yielding-if"),
        args = listOf(WellKnownTypes.booleanType2, WellKnownTypes.booleanType2),
    )

    @Test
    fun awaiting() = assertConvertedCoroutine(
        StageTestDir("convert-coro/awaiting"),
        args = listOf(
            MkType2(WellKnownTypes.promiseTypeDefinition)
                .actuals(listOf(WellKnownTypes.stringType2))
                .get(),
        ),
    )

    @Test
    fun hoistedLocals() = assertConvertedCoroutine(
        StageTestDir("convert-coro/hoisted-locals"),
        args = listOf(WellKnownTypes.intType2, WellKnownTypes.intType2),
    )

    @Test
    fun nestingOrElse() = assertConvertedCoroutine(
        StageTestDir("convert-coro/nesting-orelse"),
        args = listOf(WellKnownTypes.intType2, WellKnownTypes.intType2),
    )

    @Test
    fun yieldInLoop() = assertConvertedCoroutine(
        StageTestDir("convert-coro/yield-in-loop"),
    )

    @Test
    fun nestedFunctionHoisting() = assertConvertedCoroutine(
        StageTestDir("convert-coro/nested-function-hoisting"),
        args = listOf(WellKnownTypes.intType2, WellKnownTypes.intType2),
    )
}

/**
 * Stages the `word` directory under [stageTestDir] the same as [lang.temper.frontend.assertModuleAtStage]
 * but always advances to generate code, then searches for a coroutine function, converts its body, and
 * compares the pseudo-code to `expect/coro.temper`.
 *
 * To regenerate output, temporarily tweak the `shouldRegenerateCoroConvertTest` tweak.
 */
internal fun assertConvertedCoroutine(
    stageTestDir: StageTestDir,
    args: List<Type2> = listOf(),
    verboseDebug: Boolean = false,
    skipSimplifyControlFlow: Boolean = true,
) {
    val testDir = readTestDir(stageTestDirFileRoot.resolve(stageTestDir.url))
    val debugConsole = if (verboseDebug) {
        console
    } else {
        null
    }

    val stageNeeded = Stage.GenerateCode
    // Inspect the expect/... data files to assemble a bundle of JSON to
    // diff against the got bundle.
    val originals = mutableMapOf<TestResourceFileRelationship, JsonValue>()
    val wantJson = buildJsonNestedObject {
        for ((relPath, content) in testDir.files) {
            if (relPath.segments.firstOrNull()?.fullName != "expect") {
                // Not relevant to expectations
                continue
            }
            // The file relationship knows how to process the file into requirements in the
            // "wanted" JSON bundle.
            val rel = testResourceFileRelationships[relPath]
                ?: fail("Unrecognized test data file `${stageTestDir.url}//$relPath`")
            when (val jsonResult = rel.converter.fromFileContent(content)) {
                is RFailure<*> -> throw IllegalArgumentException(
                    "Malformed test data file `${stageTestDir.url}//$relPath`",
                    jsonResult.failure,
                )
                is RSuccess<*, *> -> {
                    originals[rel] = JsonString(content)
                    property(rel.jsonProperties, jsonResult.result)
                }
            }
        }
    }

    val listBackedLogSink = ListBackedLogSink()
    val projectLogSink = ValueSimplifyingLogSink(listBackedLogSink, nameSimplifying = true).let {
        if (verboseDebug) {
            DumpStackTracesForThoseErrors(it)
        } else {
            it
        }
    }

    val moduleAdvancer = ModuleAdvancer(projectLogSink, moduleConfig = ModuleConfig())
    val moduleLoc = testModuleName
    moduleAdvancer.configureLibrary(testLibraryName, moduleLoc.libraryRoot())

    val module = moduleAdvancer.createModule(
        loc = moduleLoc,
        console = console,
        allowDuplicateLogPositions = true,
    )
    module.addEnvironmentBindings(fakeBlockReceiverBuiltins(args))

    provisionModuleForStageTest(testDir, module, moduleAdvancer)

    val stopBeforeForMainModule = Stage.after(stageNeeded)
    val stopBefore = { m: Module ->
        when {
            stopBeforeForMainModule == null -> null
            m === module -> stopBeforeForMainModule
            // Any other modules that the main module might import
            // need to advance to at least Export to unblock the
            // main module and each other
            stopBeforeForMainModule <= Stage.Export -> Stage.after(Stage.Export)
            else -> stopBeforeForMainModule
        }
    }
    moduleAdvancer.advanceModules(stopBefore = stopBefore)

    assertEquals(Stage.GenerateCode, module.stageCompleted)

    data class CoroDetails(
        val wrapperFn: FunTree,
        val unwrappedCoroutine: UnwrappedCoroutine,
    )
    var coroDetails: CoroDetails? = null
    TreeVisit.startingAt(module.treeForDebug!!)
        .forEach { t ->
            if (t is FunTree) {
                val parts = t.parts
                val body = parts?.body as? BlockTree
                if (body != null) {
                    maybeUnwrapCoroutine(body, parts.returnDecl!!)
                        ?.let {
                            coroDetails = CoroDetails(wrapperFn = t, unwrappedCoroutine = it)
                            return@forEach VisitCue.AllDone
                        }
                }
            }
            VisitCue.Continue
        }.visitPreOrder()
    val funTree = coroDetails?.unwrappedCoroutine?.funTree
    fun snapshotCoroPseudocode() = funTree?.toPseudoCode(singleLine = false)
    val unconvertedCoroPseudocodeBefore = snapshotCoroPseudocode()
    var coroPseudocode: String? = null
    var simplifiedPseudocode: String? = null
    coroDetails?.let { (outerFn, unwrappedCoroutine) ->
        val innerFn = unwrappedCoroutine.funTree
        val parts = innerFn.parts!!
        val body = parts.body as BlockTree
        val converted = convertCoroutineFunctionBodyToRegularFunctionBody(
            body,
            body.document.nameMaker,
            outerFnOutputName = outerFn.parts!!.returnDecl!!.parts!!.name.content as ResolvedName,
            outputDecl = parts.returnDecl!!,
            adapterFn = unwrappedCoroutine.adapter,
            generatorType = unwrappedCoroutine.generatorType,
            generatorSig = unwrappedCoroutine.generatorSig,
            debugConsole = debugConsole,
            skipSimplifyControlFlow = skipSimplifyControlFlow,
        )
        coroPseudocode = converted.toPseudoCode(singleLine = false)
        run {
            val blocks = buildList {
                TreeVisit.startingAt(converted)
                    .forEachContinuing {
                        if (it is BlockTree) {
                            add(it to structureBlock(it).copy())
                        }
                    }
                    .visitPreOrder()
            }
            for ((block, originalFlow) in blocks) {
                val simplerFlow = simplifyControlFlow(
                    block,
                    originalFlow.controlFlow,
                    assumeAllJumpsResolved = true,
                    assumeResultsCaptured = true,
                    assumeUseBeforeInitChecked = true,
                    logicalOperators = BuiltinLogicalOperators,
                )
                block.replaceFlow(simplerFlow)
            }
            simplifiedPseudocode = converted.toPseudoCode(singleLine = false)
            for ((block, originalFlow) in blocks) {
                block.replaceFlow(originalFlow)
            }
        }
    }

    val unconvertedCoroPseudocodeAfter = snapshotCoroPseudocode()

    val got = listBackedLogSink.wrapErrorsAround(
        object : Structured {
            override fun destructure(structureSink: StructureSink) = structureSink.obj {
                key("coro") {
                    obj {
                        key("code") {
                            value(coroPseudocode)
                        }
                        key("simplified", Hints.u) {
                            value(simplifiedPseudocode)
                        }
                    }
                }
            }
        },
    )

    var passed = false
    val (wantReconciled, gotReconciled) = reconcileStructure(wantJson, got)
    try {
        assertStructure(
            PseudoCodeNameRenumberer.newStructurePostProcessor()(wantReconciled),
            PseudoCodeNameRenumberer.newStructurePostProcessor()(gotReconciled),
        )
        passed = true
    } finally {
        if (!passed && shouldRegenerateCoroConvertTest(stageTestDir, isEmpty = testDir.isEmpty())) {
            val gotJson = run {
                val b = JsonValueBuilder()
                gotReconciled.destructure(b)
                b.getRoot()
            }

            val regeneratedFiles = testDir.files.mapNotNull { (relPath) ->
                testResourceFileRelationships[relPath]?.let { rel ->
                    var value: JsonValue? = gotJson
                    for (prop in rel.jsonProperties) {
                        value = (value as? JsonObject)?.getOrNull(prop)
                    }
                    value?.let { value ->
                        val oldValue = originals[rel]
                        rel.converter.toFileContent(value = value, oldValue = oldValue)
                            .result?.let { content ->
                                Url(rel.relFilePath.join()) to Either.Left(content)
                            }
                    }
                }
            }
            regenerateFiles(
                stageTestDirFileSourceRoot.resolve("${stageTestDir.url}/"),
                regeneratedFiles,
            )
        }
    }

    assertEquals(
        unconvertedCoroPseudocodeAfter, unconvertedCoroPseudocodeBefore,
        "Conversion does not modify the original",
    )
}

private val testResourceFileRelationships: Map<FilePath, TestResourceFileRelationship> =
    buildMap {
        fun put(jsonProperties: List<String>, relFilePath: FilePath, converter: DataFileConverter) {
            this[relFilePath] = TestResourceFileRelationship(jsonProperties, relFilePath, converter)
        }
        put(
            listOf("coro", "code"),
            filePath("expect", "coro.temper"),
            FileContentStringConverter,
        )

        put(
            listOf("coro", "simplified"),
            filePath("expect", "simplified.temper"),
            FileContentStringConverter,
        )

        put(
            listOf("errors"),
            filePath("expect", "errors.json"),
            ParseJsonTolerantConverter,
        )
    }

private fun fakeBlockReceiverBuiltins(args: List<Type2>): Map<TemperName, Value<*>> {
    val argsOld = args.map(::hackMapNewStyleToOld)
    return mapOf(
        BuiltinName("f") to Value(
            object : BuiltinFun(
                BuiltinName("f"),
                run {
                    val (tF, tT) = makeTypeFormal("f", "T")

                    Signature2(
                        returnType2 = WellKnownTypes.voidType2,
                        hasThisFormal = false,
                        requiredInputTypes = listOf(
                            hackMapOldStyleToNew(
                                MkType.fn(
                                    listOf(), argsOld, null,
                                    returnType = MkType.nominal(
                                        WellKnownTypes.safeGeneratorTypeDefinition,
                                        listOf(hackMapNewStyleToOld(tT)),
                                    ),
                                ),
                            ),
                        ),
                        typeFormals = listOf(tF),
                    )
                },
            ) {
                override fun invoke(
                    args: ActualValues,
                    cb: InterpreterCallback,
                    interpMode: InterpMode,
                ): PartialResult =
                    NotYet
            },
        ),
        BuiltinName("fBubbly") to Value(
            object : BuiltinFun(
                BuiltinName("fBubbly"),
                run {
                    val (tF, tT) = makeTypeFormal("fBubbly", "T")

                    Signature2(
                        returnType2 = WellKnownTypes.voidType2,
                        hasThisFormal = false,
                        requiredInputTypes = listOf(
                            hackMapOldStyleToNew(
                                MkType.fn(
                                    listOf(), argsOld, null,
                                    returnType = MkType.nominal(
                                        WellKnownTypes.generatorTypeDefinition,
                                        listOf(hackMapNewStyleToOld(tT)),
                                    ),
                                ),
                            ),
                        ),
                        typeFormals = listOf(tF),
                    )
                },
            ) {
                override fun invoke(
                    args: ActualValues,
                    cb: InterpreterCallback,
                    interpMode: InterpMode,
                ): PartialResult =
                    NotYet
            },
        ),
    )
}
