@file:Suppress("MaxLineLength")

package lang.temper.frontend

import lang.temper.builtin.Types
import lang.temper.common.Freq3
import lang.temper.common.NoneShortOrLong
import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonString
import lang.temper.env.InterpMode
import lang.temper.interp.MetadataDecorator
import lang.temper.interp.importExport.STANDARD_LIBRARY_NAME
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.log.filePath
import lang.temper.name.BuiltinName
import lang.temper.name.ModuleName
import lang.temper.name.ParsedName
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.value.Document
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.Value
import lang.temper.value.initSymbol
import lang.temper.value.outTypeSymbol
import lang.temper.value.publicSymbol
import lang.temper.value.typeSymbol
import lang.temper.value.visibilitySymbol
import lang.temper.value.wordSymbol
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntaxMacroStageTest {
    @Test
    fun blockScoping() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/block-scoping"),
        moduleResultNeeded = true,
    )

    @Test
    fun useInLetInitializer() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/use-in-let-initializer"),
        moduleResultNeeded = true,
    )

    @Test
    fun backReferenceInFormalInitializer() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/back-reference-in-formal-initializer"),
    )

    /**
     * When someone does
     *
     *     let f = fn (...) {...};
     *
     * adopt a name useful for debugging.
     *
     * Similarly to if they did
     *
     *     let f = fn f(...) { ... };
     */
    @Test
    fun letOfFn() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/let-of-fn"),
    )

    @Test
    fun multiDeclarations() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/multi-declarations"),
        moduleResultNeeded = true,
    )

    @Test
    fun assignmentsInMultiDeclsResolveProperly() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/assignments-in-multi-decls-resolve-properly"),
        moduleResultNeeded = true,
    )

    @Test
    fun quotedNames() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/quoted-names"),
        moduleResultNeeded = true,
    )

    @Test
    fun thisThisIsOkButThatThisIsNot() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/this-this-is-ok-but-that-this-is-not"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun makingThisUnambiguous() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/making-this-unambiguous"),
        // TODO: IdRenumberer is not used to rewrite inlined values.
        // That affects the rendering of reified types.
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun dotsToSymbols() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/dots-to-symbols"),
        moduleResultNeeded = true,
    )

    @Test
    fun getterAndSetterInheritVisibilityFromProperty() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/getter-and-setter-inherit-visibility-from-property"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun methodWithoutBody() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/method-without-body"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun forLoopExtractsDeclarations() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/for-loop-extracts-declarations"),
    )

    @Test
    fun forOfLoopVarAvailableInBody() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/for-of-loop-var-available-in-body"),
        // for...of loop's loop variable is visible only within the body.
        // It is scoped to the body, and to allow it to be visible within the
        // expression right of `of` would lead to confusion.
        stagingFlags = setOf(StagingFlags.skipImportCore),
    )

    @Test
    fun forLoopExtractsMultipleDeclarations() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/for-loop-extracts-multiple-declarations"),
    )

    @Test
    fun forLoopKeepsLabel() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/for-loop-keeps-label"),
    )

    @Test
    fun forLoopExtractsDeclarationsMinimal() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/for-loop-extracts-declarations-minimal"),
    )

    @Test
    fun forLoopExtractsDeclarationsJustInit() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/for-loop-extracts-declarations-just-init"),
    )

    @Test
    fun forLoopLikeExtractsDeclarations() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/for-loop-like-extracts-declarations"),
    )

    @Test
    fun namesResolveToExportedNames() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/names-resolve-to-exported-names"),
        moduleResultNeeded = true,
    )

    @Test
    fun genericFn() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/generic-fn"),
        moduleResultNeeded = true,
    )

    @Test
    fun fnFormalArgsDoNotCrossScopes() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/fn-formal-args-do-not-cross-scopes"),
    )

    @Test
    fun classFormalArgsDoNotCrossScopes() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/class-formal-args-do-not-cross-scopes"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun letFunctionBodyRequiredButCheckedLater() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/let-function-body-required-but-checked-later"),
    )

    @Test
    fun letFunctionBodyRequiredWithoutName() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/let-function-body-required-without-name"),
        // Earlier, `let()` and `fn()` both hard crashed.
    )

    @Test
    fun letFunctionNameRequired() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/let-function-name-required"),
    )

    @Test
    fun objectPunning() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/object-punning"),
        moduleResultNeeded = true,
    )

    @Test
    fun whoDecoratesTheDecorators() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/who-decorates-the-decorators"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        provisionModule = { module, moduleAdvancer, td ->
            // We need some more decorators to stack.  Invent one.
            val vFoo = Value(
                MetadataDecorator(Symbol("foo"), argumentTypes = listOf(Types.string)) {
                    it.evaluate(1, interpMode = InterpMode.Partial)
                },
            )
            module.addEnvironmentBindings(
                mapOf(
                    ParsedName("@foo") to vFoo,
                    BuiltinName("@foo") to vFoo,
                ),
            )
            provisionModuleForStageTest(td, module, moduleAdvancer)
        },
    )

    @Test
    fun blockLambda() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/block-lambda"),
    )

    @Test
    fun mutuallyReferencingInterfaceTypes() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/mutually-referencing-interface-types"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun mutuallyReferencingClassTypes() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/mutually-referencing-class-types"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun mutuallyReferencingFunctionDefinition() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/mutually-referencing-function-definition"),
    )

    @Test
    fun rewriteConnectedDecorator() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/rewrite-connected-decorator"),
        // Fake std to get access to `@connected`.
        loc = ModuleName(
            sourceFile = filePath(
                STANDARD_LIBRARY_NAME,
                "fake-part-of-std.temper",
            ),
            libraryRootSegmentCount = 1,
            isPreface = false,
        ),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    @Test
    fun connectedUnsupported() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/connected-unsupported"),
        moduleResultNeeded = true,
    )

    @Test
    fun reorder() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/reorder"),
    )

    @Test
    fun genericFunctionInDocs() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/generic-function-in-docs"),
        genre = Genre.Documentation,
    )

    @Test
    fun untypedFunArgs() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/untyped-fun-args"),
        genre = Genre.Documentation,
    )

    @Test
    fun objectLiteralNoMatches() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/object-literal-no-matches"),
        moduleResultNeeded = true,
    )

    @Test
    fun objectLiteralMultipleMatches() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/object-literal-multiple-matches"),
        stage = Stage.SyntaxMacro,
        moduleResultNeeded = true,
        nameSimplifying = true,
        manualCheck = ::checkObjectLiteralMultipleMatches,
    )

    @Test
    fun staticMethods() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/static-methods"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    )

    private fun checkObjectLiteralMultipleMatches(got: JsonObject) {
        // Check that we retained the anonymous constructor call.
        val code = (((got["syntaxMacro"] as JsonObject)["body"] as JsonObject)["code"] as JsonString).content
        assertTrue("new(\\hi, 5)" in code)
        // Check that we got the expected error, without relying on specific numbering.
        val errors = (got["errors"] as JsonArray).map { (((it as JsonObject)["formatted"]) as JsonString).content }
        assertEquals(1, errors.size)
        assertEquals("Multiple types have matching constructors: Apple, Banana!", errors[0])
    }

    @Test
    fun objectLiteralMultipleMatchesNested() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/object-literal-multiple-matches-nested"),
        stage = Stage.SyntaxMacro,
        // The ObjectLiterals functional test checks non-ambiguous cases for nested scopes, so check an ambiguous case
        // here to prove we still do that.
        nameSimplifying = true,
        manualCheck = ::checkObjectLiteralMultipleMatches,
    )

    @Test
    fun objectLiteralOverloads() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/object-literal-overloads"),
        stage = Stage.SyntaxMacro,
        // At time of writing, these overloads fail at later stages but work correctly here.
        // Also, check usage both before and after type definition.
        manualCheck = { got ->
            // Check that we transformed both calls.
            val code = (got.lookup("syntaxMacro", "body", "code") as? JsonString)!!.content
            assertContains(code, Regex("""new Thing\w+\(\\hi, 5\)"""))
            assertContains(code, Regex("""new Thing\w+\(\\lo, 5\)"""))
        },
    )

    @Suppress("MaxLineLength")
    @Test
    fun storingDocStringWithFn() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/storing-doc-string-with-fn"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(metadataValueDetail = NoneShortOrLong.Short),
    )

    @Test
    fun docStringsFromMarkdown() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/doc-strings-from-markdown"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(metadataValueDetail = NoneShortOrLong.Long),
    )

    @Test
    fun storingDocStringWithType() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/storing-doc-string-with-type"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(metadataValueDetail = NoneShortOrLong.Long),
    )

    @Test
    fun storingDocStringWithExportedType() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/storing-doc-string-with-exported-type"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(metadataValueDetail = NoneShortOrLong.Short),
    )

    @Test
    fun commentsOnSettersAndGetters() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/comments-on-setters-and-getters"),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(
            metadataValueDetail = NoneShortOrLong.Short,
            showTypeMemberMetadata = true,
        ),
    )

    @Test
    fun consoleBound() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/console-bound"),
    )

    @Test
    fun chainNull() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/chain-null"),
        // Note that we currently can't properly infer `a != null` for `a.string.end` yet. TODO Infer such.
    )

    @Test
    fun nullChainingDesugaring() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/null-chaining-desugaring"),
    )

    @Test
    fun consoleUnbound() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/console-unbound"),
    )

    @Test
    fun referencedToPreResolvedPropertyNamesRecognizedAsThisReferences() = assertModuleAtStage(
        stageTestDir = StageTestDir(
            "syntax-macro/referenced-to-pre-resolved-property-names-recognized-as-this-references",
        ),
        // Ensure that when a mixin uses a generated, resolved property name
        // that we infer the `this.` on it.
        // The generated code looks like the below:
        //
        // class C(public let i__0: Int) {
        //   public let f(): Int {
        //     i__0
        //   }
        // }
    ) { module, _, _ ->
        val document = Document(module)
        val pos = Position(module.loc, 0, 0)
        val i = document.nameMaker.unusedSourceName(ParsedName("i"))
        module.deliverContent(
            document.treeFarm.grow(pos) {
                Block {
                    Call(ClassDefinitionMacro) {
                        V(wordSymbol)
                        Rn(ParsedName("C"))
                        Decl(i) {
                            V(typeSymbol)
                            V(Value(Types.int))
                            V(visibilitySymbol)
                            V(publicSymbol)
                        }
                        Fn {
                            Block {
                                Decl(ParsedName("f")) {
                                    V(initSymbol)
                                    Fn {
                                        V(outTypeSymbol)
                                        V(Value(Types.int))
                                        Block {
                                            Rn(i)
                                        }
                                    }
                                    V(visibilitySymbol)
                                    V(publicSymbol)
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    @Test
    fun noPropertyConstructorPropertiesInPropertyBag() {
        assertModuleAtStage(
            stageTestDir = StageTestDir("syntax-macro/no-property-constructor-properties-in-property-bag"),
        )
    }

    @Test
    fun setterInvocationUsedInExpressionContext() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/setter-invocation-used-in-expression-context"),
    )

    @Test
    fun malformedNumericLiteralErrors() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/malformed-numeric-literal-errors"),
    )

    @Test
    fun desugarCompoundOp() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/desugar-compound-op"),
        pseudoCodeDetail = PseudoCodeDetail(resugarDotHelpers = Freq3.Never),
        stagingFlags = setOf(StagingFlags.skipImportCore),
    )

    @Test
    fun compoundOpsWithGetterAndSetter() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/compound-ops-with-getter-and-setter"),
        pseudoCodeDetail = PseudoCodeDetail(resugarDotHelpers = Freq3.Never),
        stagingFlags = setOf(StagingFlags.skipImportCore),
    )

    @Test
    fun compoundOpsWithIndexedGetAndSet() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/compound-ops-with-indexed-get-and-set"),
        pseudoCodeDetail = PseudoCodeDetail(resugarDotHelpers = Freq3.Never),
        stagingFlags = setOf(StagingFlags.skipImportCore),
    )

    @Test
    fun nestedArithmetic() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/nested-arithmetic"),
    )

    @Test
    fun desugarPrefixOp() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/desugar-prefix-op"),
        stagingFlags = setOf(StagingFlags.skipImportCore),
    )

    @Test
    fun desugarPrefixOpWithComplexOperand() = assertModuleAtStage(
        stageTestDir = StageTestDir("syntax-macro/desugar-prefix-op-with-complex-operand"),
        stagingFlags = setOf(StagingFlags.skipImportCore),
    )
}
