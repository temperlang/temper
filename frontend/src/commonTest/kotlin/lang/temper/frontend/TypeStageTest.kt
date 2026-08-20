@file:Suppress("MaxLineLength")

package lang.temper.frontend

import lang.temper.builtin.PureCallableValue
import lang.temper.builtin.Types
import lang.temper.env.InterpMode
import lang.temper.lexer.Genre
import lang.temper.name.BuiltinName
import lang.temper.stage.Stage
import lang.temper.type.MkType
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.InterpreterCallback
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.StaySink
import lang.temper.value.Value
import lang.temper.value.void
import kotlin.test.Test

class TypeStageTest {
    @Test
    fun emptyFile() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/empty-file"),
    )

    @Test
    fun emptyReplChunk() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/empty-repl-chunk"),
        moduleResultNeeded = true,
    )

    @Test
    fun ifTransformed() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/if-transformed"),
        moduleResultNeeded = true,
    )

    @Test
    fun whileTransformed() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/while-transformed"),
    )

    @Test
    fun whileTransformedInReplContext() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/while-transformed-in-repl-context"),
        moduleResultNeeded = true,
    )

    @Test
    fun doWhileTransformed() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/do-while-transformed"),
        moduleResultNeeded = true,
    )

    @Test
    fun doOnceTransformed() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/do-once-transformed"),
        moduleResultNeeded = true,
    )

    @Test
    fun nestedFn() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/nested-fn"),
        moduleResultNeeded = true,
    )

    @Test
    fun bareReferenceToOperator() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/bare-reference-to-operator"),
        moduleResultNeeded = true,
    )

    @Test
    fun minimalForTransformed() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/minimal-for-transformed"),
    )

    @Test
    fun forWithExpressionParts() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/for-with-expression-parts"),
    )

    @Test
    fun asCheckWithIncompleteTypeCompleted() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/as-check-with-incomplete-type-completed"),
        stagingFlags = setOf(StagingFlags.skipImportCore),
    )

    @Test
    fun jumpToLabel() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/jump-to-label"),
        moduleResultNeeded = true,
        // TODO: Why does the assignment `return = void` not happen before the jump?
    )

    @Test
    fun jumpDefaultLabel() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/jump-default-label"),
        moduleResultNeeded = true,
    )

    @Test
    fun forWithIfsAndJumpsTransformed() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/for-with-ifs-and-jumps-transformed"),

    )

    @Test
    fun minimalForOfTransformed() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/minimal-for-of-transformed"),
    )

    @Test
    fun breakInForOf() = repeat(2) {
        assertModuleAtStage(
            stageTestDir = StageTestDir("type/break-in-for-of"),
            // Showing extra detail helps clarify that `List.forEach`'s <T> gets rebound to String.
            pseudoCodeDetail = PseudoCodeDetail(showInferredTypes = true),
        )
    }

    @Test
    fun breakFromInnerLoopToOuter() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/break-from-inner-loop-to-outer"),
        // The reason the loops show up below is that
        // simplifyControlFlow does not try to determine
        // whether every body path breaks and therefore
        // the condition is never re-checked.
    )

    @Test
    fun continueFromInnerLoopToOuter() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/continue-from-inner-loop-to-outer"),
        moduleResultNeeded = true,
        stagingFlags = setOf(StagingFlags.skipImportCore),
    )

    @Test
    fun blockPulledThroughDecl() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/block-pulled-through-decl"),
    )

    @Test
    fun desugarPrefixOperators() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/desugar-prefix-operators"),
        moduleResultNeeded = true,
    )

    @Test
    fun desugarPostfixOperators() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/desugar-postfix-operators"),
        // The increment from the last one intentionally doesn't show in the result
        moduleResultNeeded = true,
    )

    @Test
    fun desugarCompoundAssignments() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/desugar-compound-assignments"),
        //               10       1       5      15       3  3
        moduleResultNeeded = true,
    )

    @Test
    fun desugarCompoundAssignmentsComplexRHS() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/desugar-compound-assignments-complex-r-h-s"),
        moduleResultNeeded = true,
    )

    @Test
    @Suppress("SpellCheckingInspection") // Fixing "Brahmagupta's" triggers other lint rules
    fun brahmaguptasRevenge() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/brahmaguptas-revenge"),
        moduleResultNeeded = true,
    )

    @Test
    fun returningUntyped() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/returning-untyped"),
        moduleResultNeeded = true,
    )

    @Test
    fun returningWithReturnTypeMetadata() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/returning-with-return-type-metadata"),
        moduleResultNeeded = true,
    )

    @Test
    fun returnThatViolatesReturnType() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/return-that-violates-return-type"),
        moduleResultNeeded = true,
    )

    @Test
    fun deepStringToString() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/deep-string-to-string"),
        // Only one of these toStrings is actually needed.
    )

    @Test
    fun fnWithMixedReturnAndImpliedResultPaths() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/fn-with-mixed-return-and-implied-result-paths"),
        moduleResultNeeded = true,
    )

    @Test
    fun yieldsSeparated() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/yields-separated"),
        provisionModule = { module: Module, moduleAdvancer, td ->
            module.addEnvironmentBindings(
                mapOf(BuiltinName(ImpureIgnoreFn.name) to Value(ImpureIgnoreFn)),
            )
            provisionModuleForStageTest(td, module, moduleAdvancer)
        },
    )

    @Test
    fun functionWithArgumentsAndReturnType() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/function-with-arguments-and-return-type"),
        moduleResultNeeded = true,
    )

    @Test
    fun typeMismatchInCall() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/type-mismatch-in-call"),
        moduleResultNeeded = true,
    )

    @Test
    fun ifNotNullMulti() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/if-not-null-multi"),
        // TODO Support auto-not-null on multiple conditions. Or after blocks with early exit. Or ...
        // TODO Meanwhile, this provides some exploration fodder for such work in the future.
        moduleResultNeeded = true,
    )

    @Test
    fun ifVsNestedIf() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/if-vs-nested-if"),
    )

    @Test
    fun ifElseResultNeeded() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/if-else-result-needed"),
        moduleResultNeeded = true,
    )

    @Test
    fun ifIsNullResultNeeded() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/if-is-null-result-needed"),
        moduleResultNeeded = true,
    )

    @Test
    fun staticAccess() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/static-access"),
        moduleResultNeeded = true,
    )

    @Test
    fun amazingEvaporatingClasses() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/amazing-evaporating-classes"),
        moduleResultNeeded = true,
    )

    @Test
    fun explicitTypeArgumentsRemainInTree() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/explicit-type-arguments-remain-in-tree"),
        moduleResultNeeded = true,
    ) { module, moduleAdvancer, td ->
        module.addEnvironmentBindings(
            mapOf(
                BuiltinName("echo") to Value(
                    object : PureCallableValue, NamedBuiltinFun {
                        override val name: String = "echo"

                        override fun invoke(
                            args: ActualValues,
                            cb: InterpreterCallback,
                            interpMode: InterpMode,
                        ): PartialResult =
                            if (cb.stage == Stage.Run) {
                                if (args.size >= 1) {
                                    args[0]
                                } else {
                                    void
                                }
                            } else {
                                NotYet
                            }

                        override val sigs: List<Signature2>? = null

                        override fun addStays(s: StaySink) = Unit

                        override val callMayFailPerSe: Boolean = false
                    },
                ),
            ),
        )
        provisionModuleForStageTest(td, module, moduleAdvancer)
    }

    @Test
    fun implicitReturnForDocGenre() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/implicit-return-for-doc-genre"),
        genre = Genre.Documentation,
    )

    @Test
    fun skippedAndSwappedArgs() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/skipped-and-swapped-args"),
        // Purposely include named args with side effects to show it's ok because temporaries.
    )

    @Test // See issue#1305
    fun deepDefaultMethod() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/deep-default-method"),
        moduleResultNeeded = true,
    )

    @Test
    fun bareReturnVoid() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/bare-return-void"),
    )

    @Test
    fun makeEmptyExplicitVoid() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/make-empty-explicit-void"),
    )

    @Test
    fun issue1828MissingReturn() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/issue1828-missing-return"),
    )

    @Test
    fun extensionHintsResolved() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/extension-hints-resolved"),
        moduleResultNeeded = true,
    )

    @Test
    fun staticExtensionHintsResolved() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/static-extension-hints-resolved"),
        moduleResultNeeded = true,
    )

    @Test
    fun bindingCalleesNotPulledOut() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/binding-callees-not-pulled-out"),
    )

    @Test
    fun importedExtensionsUsable() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/imported-extensions-usable"),
    )

    @Test
    fun unaryPlusWashesOut() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/unary-plus-washes-out"),
    )

    @Test
    fun orElsePanic() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/or-else-panic"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showInferredTypes = true),
        moduleResultNeeded = true,
    )

    @Test
    fun taggedString() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/tagged-string"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showInferredTypes = true),
    )

    /** Test overload decorations. */
    @Test
    fun overloadedMethods() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/overloaded-methods"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showInferredTypes = true),
    )

    /** Test overload decorations. */
    @Test
    fun overriddenAndUnoverriddenOverloadedMethods() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/overridden-and-unoverridden-overloaded-methods"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showInferredTypes = true),
    )

    @Test
    fun overloadOnGenerics() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/overload-on-generics"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showInferredTypes = true),
    )

    /**
     * We currently have some direct overloading support, so this helps fore reviewing that behavior.
     * TODO Drop support for direct overloading.
     */
    @Test
    fun overloadedMethodsWrong() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/overloaded-methods-wrong"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showInferredTypes = true),
    )

    @Test
    fun iteratorLoop() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/iterator-loop"),
        stagingFlags = setOf(StagingFlags.skipImportCore),
    )

    @Test
    fun halfReturn() = assertModuleAtStage(
        stageTestDir = StageTestDir("type/half-return"),
        stagingFlags = setOf(StagingFlags.skipImportCore),
    )
}

private object ImpureIgnoreFn : NamedBuiltinFun, CallableValue {
    override val name: String = "ignore"
    override val sigs: List<Signature2> = listOf(
        Signature2(
            returnType2 = WellKnownTypes.voidType2,
            hasThisFormal = false,
            requiredInputTypes = listOf(
                hackMapOldStyleToNew(
                    MkType.fn(
                        typeFormals = emptyList(),
                        valueFormals = emptyList(),
                        restValuesFormal = null,
                        returnType = Types.void.type,
                    ),
                ),
            ),
        ),
    )
    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): PartialResult = void

    override val callMayFailPerSe: Boolean = false
}
