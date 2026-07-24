@file:Suppress("MaxLineLength")

package lang.temper.frontend

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.assertStructure
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.withCapturingConsole
import lang.temper.env.InterpMode
import lang.temper.frontend.staging.ModuleAdvancer
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.log.LogSink
import lang.temper.log.Position
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.name.BuiltinName
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModuleName
import lang.temper.type2.Signature2
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.Document
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.TInt
import lang.temper.value.Value
import lang.temper.value.unholeBuiltinName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DefineStageTest {
    @Test
    fun callToMethod() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/call-to-method"),
        stagingFlags = setOf(StagingFlags.skipImportCore),
    )

    /**
     * Try out a bunch of uses of `this` and the dot operator to check
     * [lang.temper.frontend.syntax.DotOperationDesugarer].
     */
    @Test
    fun dotOperationDesugaring() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/dot-operation-desugaring"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun charTag() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/char-tag"),
        moduleResultNeeded = true,
    )

    @Test
    fun internalVersusExternalBackedPropertyAccess() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/internal-versus-external-backed-property-access"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun constantFolding() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/constant-folding"),
        stagingFlags = setOf(StagingFlags.skipImportCore),
    )

    @Test
    fun constantFoldingViaConstExpression() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/constant-folding-via-const-expression"),
        moduleResultNeeded = true,
    )

    @Test
    fun nonConstReferentNotFoldedIntoConstExpression() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/non-const-referent-not-folded-into-const-expression"),
        moduleResultNeeded = true,
    )

    @Test
    fun userDefinedPureFunctionsInlined() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/user-defined-pure-functions-inlined"),
        moduleResultNeeded = true,
    )

    // Test that type definition values get inlined.
    @Test
    fun typeAliasing() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/type-aliasing"),
    )

    @Test
    fun inheritedReassignability() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/inherited-reassignability"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun functionalInterfaceAbbreviatedSyntax() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/functional-interface-abbreviated-syntax"),
    )

    @Test
    fun functionalInterfaceGeneric() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/functional-interface-generic"),
    )

    @Test
    fun coalesce() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/coalesce"),
    )

    @Test
    fun optionalParametersNotInlined() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/optional-parameters-not-inlined"),
    )

    @Test
    fun conditionallyAssignedConstNotInlined() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/conditionally-assigned-const-not-inlined"),
    )

    @Test
    fun classesWithDisclosures() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/classes-with-disclosures"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun impliedGettersAndSetters() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/implied-getters-and-setters"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun propertyOnlyInterface() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/property-only-interface"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun exportedNamePropagates() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/exported-name-propagates"),
    )

    @Test
    fun macrosInEscapes() {
        var doNotCallWasCalled = false
        assertModuleAtStage(StageTestDir("define/macros-in-escapers")) { module: Module, _, _ ->
            // The idea for escapes is that macros that take escapes can progressively turn
            // CST elements into AST elements and then use some `eval` builtin to unescape the
            // result.
            // Fake a tree after that CST->AST conversion.
            val input = Document(module).treeFarm.grow(Position(module.loc, 0, 0)) {
                Esc {
                    Call {
                        Rn(BuiltinName("doNotCall"))
                        // This call should not be inlined as it's escaped
                        Call(BuiltinFuns.plusIntIntFn) {
                            V(Value(1, TInt))
                            V(Value(1, TInt))
                        }
                        Call {
                            Rn(unholeBuiltinName)
                            // This call is in a hole so should be inlined
                            Call(BuiltinFuns.plusIntIntFn) {
                                V(Value(1, TInt))
                                V(Value(1, TInt))
                            }
                        }
                    }
                }
            }

            module.deliverContent(input)
            module.addEnvironmentBindings(
                mapOf(
                    BuiltinName("doNotCall") to Value(
                        object : BuiltinStatelessMacroValue, NamedBuiltinFun {
                            override val name = "doNotCall"
                            override val sigs: List<Signature2>? = null
                            override fun invoke(
                                macroEnv: MacroEnvironment,
                                interpMode: InterpMode,
                            ): PartialResult {
                                doNotCallWasCalled = true
                                return NotYet
                            }
                        },
                    ),
                ),
            )
        }
        assertFalse(doNotCallWasCalled)
    }

    @Test
    fun parameterizedConstructorReference() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/parameterized-constructor-reference"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun staticRead() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/static-read"),
        moduleResultNeeded = true,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun complexTypeAliases() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/complex-type-aliases"),
    )

    @Test
    fun typeArgsKept() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/type-args-kept"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun functionTypesInline() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/function-types-inline"),
    )

    @Test
    fun nestedEmptyType() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/nested-empty-type"),
    )

    @Test
    fun whenBlock() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/when-block"),
        // Test both valid content and error content together.
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun whenGeneric() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/when-generic"),
    )

    @Test
    fun castingCall() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/casting-call"),
    )

    @Test
    fun instantiatesTestHarnesses() {
        val loc = ModuleName(
            dirPath("std", "testing"),
            libraryRootSegmentCount = 1,
            isPreface = false,
        )

        val (fakeStdTestModuleExports, consoleOutput) = withCapturingConsole(Log.Error) { console ->
            val fakeStdModule = Module(
                projectLogSink = LogSink.devNull,
                loc = loc,
                console = console,
                continueCondition = { true },
            )
            fakeStdModule.deliverContent(
                ModuleSource(
                    filePath = filePath("std", "testing"),
                    fetchedContent = """
                        |export class Test {}
                        |export let runTestCases(testCases: List<Pair<String, (fn (Test): Void  throws Bubble)>>): Void {}
                    """.trimMargin(),
                    languageConfig = StandaloneLanguageConfig,
                ),
            )

            while (fakeStdModule.canAdvance()) {
                fakeStdModule.advance()
            }

            fakeStdModule.exports!!
        }

        assertEquals("", consoleOutput)

        assertModuleAtStage(
            stageTestDir = StageTestDir("define/instantiate-test-harnesses"),
            // For `temper test` integration, we need to add instructions to
            // create instances of each concrete test fixture type when there
            // is a particular marker.
            stagingFlags = setOf(StagingFlags.defineStageHookCreateAndRunClasses),

        ) { module, moduleAdvancer, td ->
            module.addImplicitImports(fakeStdTestModuleExports)
            provisionModuleForStageTest(td, module, moduleAdvancer)
        }
    }

    @Test
    fun badTests() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/bad-tests"),
    )

    @Test
    fun goodTests() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/good-tests"),
    )

    @Test
    fun autoAssertMessage() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/auto-assert-message"),
    )

    @Test
    fun classExtendsClass() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/class-extends-class"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun missingVisibilityOnClassMembers() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/missing-visibility-on-class-members"),
    )

    @Test
    fun regexLiteral() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/regex-literal"),
    )

    @Test
    fun fullyQualifiedNamesAllocated() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/fully-qualified-names-allocated"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(
            showTypeMemberMetadata = true,
            showQNames = true,
        ),
    )

    @Test
    fun sealedTypesChecked() {
        val logSink = ListBackedLogSink()
        val (modules, consoleOutput) = withCapturingConsole { console ->
            val moduleAdvancer = ModuleAdvancer(logSink)
            moduleAdvancer.configureLibrary(
                DashedIdentifier("test-library"), dirPath("test-library"),
            )
            val moduleA = moduleAdvancer.createModule(
                ModuleName(
                    dirPath("test-library", "a"),
                    libraryRootSegmentCount = 1,
                    isPreface = false,
                ),
                console,
            )
            val moduleB = moduleAdvancer.createModule(
                ModuleName(
                    dirPath("test-library", "b"),
                    libraryRootSegmentCount = 1,
                    isPreface = false,
                ),
                console,
            )
            moduleA.deliverContent(
                ModuleSource(
                    filePath = filePath("test-library", "a", "a.temper"),
                    fetchedContent = """
                        |export sealed interface SI {}
                        |
                        |export class A extends SI {}
                        |export class B extends SI {}
                    """.trimMargin(),
                    languageConfig = StandaloneLanguageConfig,
                ),
            )
            moduleB.deliverContent(
                ModuleSource(
                    filePath = filePath("test-library", "b", "b.temper"),
                    fetchedContent = """
                        |let { SI } = import("../a/");
                        |
                        |export class C extends SI {}
                        |
                        |sealed class D {}
                    """.trimMargin(),
                    languageConfig = StandaloneLanguageConfig,
                ),
            )
            moduleAdvancer.advanceModules(null)
            moduleAdvancer.getAllModules()
        }
        assertStructure(
            """
                |{
                |  // We have a warning about the invalid sealed extension
                |  consoleOutput: ```
                |      3: export class C extends SI {}
                |                       ┗━━━━━━━━━━┛
                |      [test-library/b/b.temper:3+14-26]@D: Cannot extend sealed type SI from test-library/a/a.temper:1+7-13. C is not declared in the same module.
                |      1: export sealed interface SI {}
                |                ┗━━━━┛
                |      5: sealed class D {}
                |         ┗━━━━┛
                |      [test-library/b/b.temper:5+0-6]@D: Only interfaces can be sealed
                |      ```,
                |  "test-library//a/": [
                |    {
                |      name: "test-library//a/.SI",
                |      abstract: true,
                |      supers: ["AnyValue__0"],
                |
                |      // the sealed type list include the ones that pass the checker
                |      sealedSubTypes: [
                |        "test-library//a/.A",
                |        "test-library//a/.B"
                |      ],
                |      metadata: {
                |        sealedType: ["void: Void"],
                |      }
                |    },
                |    {
                |      name: "test-library//a/.A",
                |      abstract: false,
                |      supers: ["test-library//a/.SI"],
                |      methods: [
                |        { name: "constructor__4", visibility: "public", open: false, kind: "Constructor" },
                |      ],
                |    },
                |    {
                |      name: "test-library//a/.B",
                |      abstract: false,
                |      supers: ["test-library//a/.SI"],
                |      methods: [
                |        { name: "constructor__5", visibility: "public", open: false, kind: "Constructor" },
                |      ],
                |    },
                |  ],
                |  "test-library//b/": [
                |    {
                |      name: "test-library//b/.C",
                |      abstract: false,
                |      supers: ["test-library//a/.SI"],
                |      methods: [
                |        { name: "constructor__6", visibility: "public", open: false, kind: "Constructor" },
                |      ],
                |    },
                |    {
                |      name: "D__0",
                |      abstract: false,
                |      supers: ["AnyValue__0"],
                |      methods: [
                |        { name: "constructor__7", visibility: "public", open: false, kind: "Constructor" },
                |      ],
                |      metadata: {
                |        sealedType: ["void: Void"],
                |      },
                |    },
                |  ],
                |}
            """.trimMargin(),
            object : Structured {
                override fun destructure(structureSink: StructureSink) = structureSink.obj {
                    key("consoleOutput") {
                        value(consoleOutput.trimEnd())
                    }
                    modules.forEach {
                        key("${it.loc}") {
                            value(it.declaredTypeShapes)
                        }
                    }
                }
            },
        )
    }

    @Test
    fun sealedSubtypesRejectNewTypeParams() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/sealed-subtypes-reject-new-type-params"),
    )

    @Test
    fun resolutionsStoredWithPostponedCaseCases() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/resolutions-stored-with-postponed-case-cases"),
    )

    @Test
    fun jsonInteropMixedIn() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/json-interop-mixed-in"),
        // ## lines below are stripped, explanatory comments.
    )

    @Test
    fun nullableTypesResolved() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/nullable-types-resolved"),
    )

    @Test
    fun propertyBagsDesugarToPositionalParameters() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/property-bags-desugar-to-positional-parameters"),
    )

    @Test
    fun propertyBagsDesugaringWithOptionalParameters() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/property-bags-desugaring-with-optional-parameters"),
    )

    @Test
    fun accumulatorTypeUse() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/accumulator-type-use"),
    )

    @Test
    fun accumulatorTypeUseNoStmt() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/accumulator-type-use-no-stmt"),
    )

    @Test
    fun escapeSequenceGrouping() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/escape-sequence-grouping"),
    )

    @Test
    fun operatorDecoratorArityInference() = assertModuleAtStage(
        stageTestDir = StageTestDir("define/operator-decorator-arity-inference"),
    )
}
