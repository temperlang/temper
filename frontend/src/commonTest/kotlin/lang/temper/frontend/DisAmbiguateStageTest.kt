@file:Suppress("MaxLineLength")

package lang.temper.frontend

import lang.temper.common.Freq3
import lang.temper.interp.MetadataDecorator
import lang.temper.lexer.Genre
import lang.temper.name.BuiltinName
import lang.temper.name.Symbol
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.Value
import lang.temper.value.void
import kotlin.test.Test

class DisAmbiguateStageTest {
    @Test
    fun unknownFunctionWithFormalGetsError() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/unknown-function-with-formal-gets-error"),
    )

    @Test
    fun formalsFormalizedAndActualsActualized() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/formals-formalized-and-actuals-actualized"),
    )

    @Test
    fun formalsAndActualsWithEmbeddedComments() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/formals-and-actuals-with-embedded-comments"),
    )

    @Test
    fun annotatedFormal() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/annotated-formal"),
    )

    @Test
    fun stagingAnnotation() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/staging-annotation"),
        stagingFlags = setOf(StagingFlags.skipImportImplicits),
    )

    @Test
    fun bunchOfStuff() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/bunch-of-stuff"),
    )

    @Test
    fun blockFormals() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/block-formals"),
    )

    @Test
    fun classBodyAmbiguityReduction() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/class-body-ambiguity-reduction"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun typeFormalsOnClassDeclaration() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/type-formals-on-class-declaration"),
        stagingFlags = setOf(StagingFlags.skipImportImplicits),
    )

    @Test
    fun genericFnWithComplexTypeFormal() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/generic-fn-with-complex-type-formal"),
        stagingFlags = setOf(StagingFlags.skipImportImplicits),
    )

    @Test
    fun moreDecoratedTypeFormals() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/more-decorated-type-formals"),
        // No, `@partialImu` doesn't make sense here, but it allows for testing multiple decorators.
    )

    @Test
    fun genericMethodsDisallowedInInterface() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/generic-methods-disallowed-in-interface"),
        stagingFlags = setOf(StagingFlags.skipImportImplicits),
        // Generic instance methods should be reported. Variety here is just to be sure about internal forms.
        // Static methods in interfaces can be generic if they want.
    )

    @Test
    fun multipleKeywordAnnotationsAllFire() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/multiple-keyword-annotations-all-fire"),
    )

    @Test
    fun unrecognizedDecorationsPreservedForLater() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/unrecognized-decorations-preserved-for-later"),
    )

    @Test
    fun decoratedArgument() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/decorated-argument"),
    )

    @Test
    fun everyTypeButImplicitsHasASuperType() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/every-type-but-implicits-has-a-super-type"),
    )

    @Test
    fun annotationsOnFormals() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/annotations-on-formals"),
        // annotations on x do not apply to y as would be the case if `@foo var x = 0, y` were
        // to appear as a top-level, not a function formal parameter
    )

    @Test
    fun genericMethod() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/generic-method"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun typeDecoratorCanAccessTypeAndDeclaration() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/type-decorator-can-access-type-and-declaration"),
    ) { module, moduleAdvancer, td ->
        module.addEnvironmentBindings(
            mapOf(
                BuiltinName("@foo") to Value(
                    MetadataDecorator(symbolKey = Symbol("TypeDecoratedByFoo")) { void },
                ),
            ),
        )
        provisionModuleForStageTest(td, module, moduleAdvancer)
    }

    @Test
    fun enumDesugaring() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/enum-desugaring"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun squareBracketDesugaring() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/square-bracket-desugaring"),
    )

    @Test
    fun multiDeclDecoratorApplication() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/multi-decl-decorator-application"),
    )

    @Test
    fun multiInit() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/multi-init"),
    )

    @Test
    fun multiInitMultiRenameError() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/multi-init-multi-rename-error"),
    )

    @Test
    fun wildcardDestructureError() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/wildcard-destructure-error"),
    )

    @Test
    fun multiInitErrorInClass() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/multi-init-error-in-class"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun commentInDocTypeDefinition() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/comment-in-doc-type-definition"),
        genre = Genre.Documentation,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun exportedClassesHaveExportedNames() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/exported-classes-have-exported-names"),
    )

    @Test
    fun exportedClassesWithExtraDecoratorsHaveExportedNames() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/exported-classes-with-extra-decorators-have-exported-names"),
    )

    @Test
    fun classesCanDeclarePropertiesInParenthetical() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/classes-can-declare-properties-in-parenthetical"),
    )

    @Test
    fun incrementInDoBlock() = assertModuleAtStage(
        stageTestDir = StageTestDir("dis-ambiguate/increment-in-do-block"),
        pseudoCodeDetail = PseudoCodeDetail(resugarDotHelpers = Freq3.Never),
        stagingFlags = setOf(StagingFlags.skipImportImplicits),
    )
}
