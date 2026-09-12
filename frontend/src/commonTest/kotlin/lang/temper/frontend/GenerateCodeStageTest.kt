@file:Suppress("MaxLineLength")

package lang.temper.frontend

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.interp.MetadataDecorator
import lang.temper.log.MessageTemplate
import lang.temper.name.BuiltinName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.BuiltinStatelessCallableValue
import lang.temper.value.FunctionSpecies
import lang.temper.value.InterpreterCallback
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.TInt
import lang.temper.value.Value
import lang.temper.value.void
import kotlin.test.Ignore
import kotlin.test.Test

class GenerateCodeStageTest {
    @Test
    fun simpleDoNothingLoop() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/simple-do-nothing-loop"),
    )

    @Test
    fun sealedWhen() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/sealed-when"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showInferredTypes = true),
    )

    @Test
    fun assignmentsToTypedReturnAreChecked() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/assignments-to-typed-return-are-checked"),
        moduleResultNeeded = true,
    )

    @Test
    fun docCommentInData() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/doc-comment-in-data"),
    )

    @Test
    fun doWhileContinuesToFalseCondition() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/do-while-continues-to-false-condition"),
        moduleResultNeeded = true,
    )

    @Test
    fun exportedNames() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/exported-names"),
        moduleResultNeeded = true,
    )

    @Test
    fun simpleMethodCall() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/simple-method-call"),
        moduleResultNeeded = true,
    )

    @Test
    fun getterSettersFinal() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/getter-setters-final"),
        moduleResultNeeded = true,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun getterSettersVarOrNot() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/getter-setters-var-or-not"),
    )

    @Test
    fun fnType() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/fn-type"),
        moduleResultNeeded = true,
    )

    @Test
    fun catsAreNice() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/cats-are-nice"),
        pseudoCodeDetail = PseudoCodeDetail(showInferredTypes = true),
    )

    @Test
    fun catsAreRadActually() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/cats-are-rad-actually"),
    )

    @Test
    fun catsPlayWithStringAndNull() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/cats-play-with-string-and-null"),
    )

    /** No cats were harmed in the making of this test. */
    @Test
    fun rawCatsGetCooked() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/raw-cats-get-cooked"),
    )

    @Ignore // TODO(mikesamuel): Fix typing of generic methods with explicit actuals
    @Test
    fun mapTypeArg() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/map-type-arg"),
        pseudoCodeDetail = PseudoCodeDetail(showInferredTypes = true),
    )

    @Ignore
    @Test
    fun banExportNotAtTopLevel() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/ban-export-not-at-top-level"),
    )

    @Ignore
    @Test
    fun banExportsThatAreReAssignable() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/ban-exports-that-are-re-assignable"),
    )

    @Ignore
    @Test
    fun banExportInLoops() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/ban-export-in-loops"),
    )

    @Test
    fun banExportsExposingNonExported() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/ban-exports-exposing-non-exported"),
    )

    @Test
    fun banMixedExportsJustFunctionType() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/ban-mixed-exports-just-function-type"),
    )

    @Test
    fun unalignedNamedArgs() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/unaligned-named-args"),
        // TODO This only matters for constructors/factories going forward.
        // TODO And maybe we'll manage those positioned, so this test might be best removed sometime.
    )

    @Test
    fun nestedAssignmentInResultPosition() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/nested-assignment-in-result-position"),
        moduleResultNeeded = true,
    ) { module, moduleAdvancer, td ->
        module.addEnvironmentBindings(oneToThreeBindings)
        provisionModuleForStageTest(td, module, moduleAdvancer)
    }

    @Test
    fun autoCastIs() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/auto-cast-is"),
    )

    @Test
    fun autoCastWhen() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/auto-cast-when"),
    )

    @Test
    fun nestedSetpInResultPosition() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/nested-setp-in-result-position"),
        moduleResultNeeded = true,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    ) { module, moduleAdvancer, td ->
        module.addEnvironmentBindings(oneToThreeBindings)
        provisionModuleForStageTest(td, module, moduleAdvancer)
    }

    @Test
    fun nestedSetterInvocationsInResultPosition() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/nested-setter-invocations-in-result-position"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        moduleResultNeeded = true,
    ) { module, moduleAdvancer, td ->
        module.addEnvironmentBindings(oneToThreeBindings)
        provisionModuleForStageTest(td, module, moduleAdvancer)
    }

    /**
     * Not having argument or return types causes the errors here.
     * See also `TyperTest.assignedFnWithInferredSigTypes`.
     */
    @Test
    fun assignedFnWithInferredSigTypes() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/assigned-fn-with-inferred-sig-types"),
    )

    @Test
    fun booleanTypeError() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/boolean-type-error"),
        moduleResultNeeded = true,
    )

    @Test
    fun lotsaLets() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/lotsa-lets"),
    )

    @Ignore
    @Test
    fun enumConstants() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/enum-constants"),
    )

    @Test
    fun emptyInterface() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/empty-interface"),
        moduleResultNeeded = true,
    )

    @Test
    fun hideOverrideProperty() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/hide-override-property"),
        moduleResultNeeded = true,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun hideOverrideMethod() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/hide-override-method"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun hideOverrideMethodGeneric() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/hide-override-method-generic"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    /**
     * No [lang.temper.log.MessageTemplate.CannotExtendConcrete] because of `<S extends String>`.
     * *S* can validly bind to *String* or *Never*.
     */
    @Test
    fun typeParameterCanExtendConcreteType() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/type-parameter-can-extend-concrete-type"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun returnTypeRequired() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/return-type-required"),
    )

    @Test
    fun optionalArgumentPassing() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/optional-argument-passing"),
        moduleResultNeeded = true,
    )

    @Test
    fun returnTypeOptionalForSomeCases() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/return-type-optional-for-some-cases"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun typeMetadata() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/type-metadata"),
    ) { module, moduleAdvancer, td ->
        module.addEnvironmentBindings(
            mapOf(
                BuiltinName("@foo") to Value(MetadataDecorator(Symbol("foo")) { void }),
            ),
        )

        provisionModuleForStageTest(td, module, moduleAdvancer)
    }

    @Test
    fun voidNotAValue() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/void-not-a-value"),
        // Implied and explicit void returns should be fine, but others should be errors.
    )

    @Test
    fun voidVsValue() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/void-vs-value"),
    )

    @Test
    fun impliedLambdaReturnType() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/implied-lambda-return-type"),
    )

    @Test
    fun deadCode() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/dead-code"),
    )

    @Test
    fun staticMethods() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/static-methods"),
        moduleResultNeeded = true,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun staticAccessGoodAndBad() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/static-access-good-and-bad"),
    )

    @Test
    fun noInstantiateInterface() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/no-instantiate-interface"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun exportSome() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/export-some"),
        // Includes examples of different kinds of roots and entities as well as transitive reachability and such.
        // Also includes an example of something reachable from both export and test roots.
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun initAssignmentReachability() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/init-assignment-reachability"),
    )

    @Test
    fun blockLambdaEndToEnd() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/block-lambda-end-to-end"),
        moduleResultNeeded = true,

    )

    @Test
    fun generatorInterpreted() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/generator-interpreted"),
    )

    @Test
    fun generatorInterpretedInLoop() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/generator-interpreted-in-loop"),
    )

    @Ignore
    @Test
    fun generatorResultsUsed() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/generator-results-used"),
    )

    @Test
    fun forOfExample() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/for-of-example"),
    )

    @Test
    fun awaiting() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/awaiting"),
        stagingFlags = setOf(StagingFlags.allowTopLevelAwait),
    )

    @Test
    fun invalidRtti() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/invalid-rtti"),
        // Check that is T and as T only operate
        // on types that can be distinguished at runtime.
        logEntryWanted = {
            it.level >= Log.Warn ||
                // This is a low-level message, but it's specific to these checks.
                it.template == MessageTemplate.UnnecessaryRttiCheck
        },
    )

    @Test
    fun invalidRttiTypeArgs() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/invalid-rtti-type-args"),
    )

    @Test
    fun invalidRttiNotInlined() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/invalid-rtti-not-inlined"),
        // Check that is T and as T that would be invalid
        // if translated aren't inlined.
        moduleResultNeeded = true,
        logEntryWanted = {
            // UnnecessaryRttiCheck is low level, but relevant inside a REPL.
            it.level >= Log.Warn || it.template == MessageTemplate.UnnecessaryRttiCheck
        },
    )

    @Test
    fun upcastOk() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/upcast-ok"),
        // Use Map here because that was the original motivating example, even though it's not vital to the test.
        logEntryWanted = {
            // UnnecessaryRttiCheck is low level, but relevant inside a REPL.
            it.level >= Log.Warn || it.template == MessageTemplate.UnnecessaryRttiCheck
        },
        // Key focus being no errors here.
    )

    @Test
    fun castAwayNullWorksAtRuntime() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/cast-away-null-works-at-runtime"),
    )

    @Test
    fun matchWithCharExprCases() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/match-with-char-expr-cases"),
    )

    @Test
    fun sealedConnectedCasts() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/sealed-connected-casts"),
        // comments in the cast checker describe why this is the way it is.
        // In short, a sealed, connected type must be able to distinguish
        // its subtypes, so the static expression type matters when casting.
    )

    @Test
    fun stringNullEquality() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/string-null-equality"),
        moduleResultNeeded = true,
    )

    @Test
    fun asAndIsSimplification1() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/as-and-is-simplification1"),
    )

    @Test
    fun asAndIsSimplification2() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/as-and-is-simplification2"),
    )

    @Test
    fun asAndIsSimplification3() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/as-and-is-simplification3"),
    )

    @Test
    fun asAndIsSimplification4() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/as-and-is-simplification4"),
    )

    // Complex expressions caught in temporary
    @Test
    fun asAndIsSimplification5() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/as-and-is-simplification5"),
    )

    @Test
    fun nullSimplification() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/null-simplification"),
    )

    @Test
    fun sneakyBubble() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/sneaky-bubble"),
    )

    @Test
    fun bubbleOrElseNot() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/bubble-or-else-not"),
        // Explore bubbles both escaping and captured, both explicit and implicit, both builtin and user functions.
        // Just making sure to explore the space of how we handle things.
    )

    @Test // TODO: get this working without the explicit type on x
    fun orElseNull() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/or-else-null"),
        pseudoCodeDetail = PseudoCodeDetail(showInferredTypes = true),
    )

    @Test
    fun extensionMethodUse() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/extension-method-use"),
        moduleResultNeeded = true,
    )

    @Test
    fun jsonAdapterWorks() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/json-adapter-works"),
        moduleResultNeeded = true,
    )

    @Test
    fun jsonAdapterEncodesSealedTypes() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/json-adapter-encodes-sealed-types"),
        moduleResultNeeded = true,
    )

    @Test
    fun jsonAdapterDecodesSealedTypes() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/json-adapter-decodes-sealed-types"),
        moduleResultNeeded = true,
    )

    @Test
    fun nullableJsonField() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/nullable-json-field"),
        moduleResultNeeded = true,
    )

    @Test
    fun jsonInteropForwardsTypeInfoForNullableProps() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/json-interop-forwards-type-info-for-nullable-props"),
        moduleResultNeeded = true,
    )

    @Test
    fun rgxMacro() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/rgx-macro"),
        moduleResultNeeded = true,
    )

    @Test
    fun complexStringExpr() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/complex-string-expr"),
        moduleResultNeeded = true,
    )

    @Test
    fun complexStringExprWithFormattingHole() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/complex-string-expr-with-formatting-hole"),
        moduleResultNeeded = true,
    )

    @Test
    fun complexStringExprWithFormattingHoleAndMore() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/complex-string-expr-with-formatting-hole-and-more"),
        moduleResultNeeded = true,
    )

    @Test
    fun explicitBoundedTypeParametersInInterpreter() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/explicit-bounded-type-parameters-in-interpreter"),
    )

    @Test
    fun invalidNonNullCheck() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/invalid-non-null-check"),
    )

    @Test
    fun multiImport() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/multi-import"),
    )

    @Test
    fun nullInTestingAssert() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/null-in-testing-assert"),
    )

    @Test
    fun longNullChain() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/long-null-chain"),
        moduleResultNeeded = true,
    )

    @Test
    fun nonNullInference() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/non-null-inference"),
    )

    @Test
    fun complexAssignmentOfVarProperty() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/complex-assignment-of-var-property"),

    )

    @Test
    fun complexAssignmentOfGetExpr() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/complex-assignment-of-get-expr"),

    )

    @Test
    fun whenElseBubble() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/when-else-bubble"),
        pseudoCodeDetail = PseudoCodeDetail(showInferredTypes = true),
    )

    @Test
    fun veryBigMapConstructor() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/very-big-map-constructor"),
    )

    @Test
    fun doPureRuns() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/do-pure-runs"),
        moduleResultNeeded = true,
    )

    @Test
    fun errorMessageOnMissingOverride() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/error-message-on-missing-override"),
    )

    @Test
    fun errorMessageOnBadOverride() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/error-message-on-bad-override"),
    )

    @Test
    fun nullAssignedToNonNullVarDevl() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/null-assigned-to-non-null-var-devl"),
        moduleResultNeeded = true,
    )

    @Test
    fun stringCoercionOfRttiCheck() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/string-coercion-of-rtti-check"),
    )

    @Test
    fun isAppliedToParameterizedType() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/is-applied-to-parameterized-type"),
        moduleResultNeeded = true,
    )

    @Test
    fun staticWithUnusedExtension() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/static-with-unused-extension"),
    )

    @Test
    fun declaringADataFile() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/declaring-a-data-file"),
    )

    @Test
    fun missingFunctionBody() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/missing-function-body"),
    )

    @Test
    fun varGetP() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/var-get-p"),
        stagingFlags = setOf(StagingFlags.skipImportCore, StagingFlags.moduleResultNeeded),
    )

    @Test
    fun orelseInStringInterpolation() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/orelse-in-string-interpolation"),
    )

    @Test
    fun asVsAssertAs() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/as-vs-assertas"),
    )
}

// Provide an extra binding to a function whose call does not inline so does not trigger any
// we-don't-need-to-capture-this-in-a-temporary paths in the Weaver.
private val oneToThreeBindings = mapOf<TemperName, Value<*>>(
    BuiltinName("oneTwoThree") to Value(
        object : BuiltinStatelessCallableValue, NamedBuiltinFun {
            override val name: String = "oneTwoThree"
            override val sigs = listOf(
                Signature2(
                    returnType2 = WellKnownTypes.intType2,
                    hasThisFormal = false,
                    requiredInputTypes = emptyList(),
                ),
            )

            override fun invoke(
                args: ActualValues,
                cb: InterpreterCallback,
                interpMode: InterpMode,
            ) = Value(123, TInt)

            override val functionSpecies = FunctionSpecies.Normal
            override val callMayFailPerSe: Boolean = false
        },
    ),
)
