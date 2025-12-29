@file:Suppress("SpellCheckingInspection")

package lang.temper.be

import lang.temper.ast.OutTree
import lang.temper.be.tmpl.FunctionTypeStrategy
import lang.temper.be.tmpl.InlineSupportCode
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLOperator
import lang.temper.be.tmpl.aType
import lang.temper.be.tmpl.operatorJoin
import lang.temper.common.AtomicCounter
import lang.temper.common.OpenOrClosed
import lang.temper.common.TriState
import lang.temper.format.CodeFormatter
import lang.temper.format.FormattingHints
import lang.temper.format.TokenSink
import lang.temper.format.toStringViaTokenSink
import lang.temper.lexer.Associativity
import lang.temper.lexer.Genre
import lang.temper.library.LibraryConfiguration
import lang.temper.library.LibraryConfigurationsBundle
import lang.temper.log.dirPath
import lang.temper.name.BackendMeta
import lang.temper.name.BuiltinName
import lang.temper.name.DashedIdentifier
import lang.temper.name.FileType
import lang.temper.name.ResolvedName
import lang.temper.name.Symbol
import lang.temper.type.Abstractness
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.Variance
import lang.temper.type.withTypeTestHarness
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.value.TBoolean
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import lang.temper.log.unknownPos as p0
import lang.temper.type.WellKnownTypes as WKT

/**
 * A base class for backends to run a suite of unit tests of translated TmpL.
 *
 * @see [lang.temper.be.tmpl.TmplRenderSuite]
 */
abstract class TranslatorTests(
    private val meta: BackendMeta,
    private val supportNetwork: SupportNetwork,
) {
    protected var gen: TmplGenerator = newGen()

    private fun newGen() = TmplGenerator(meta.fileExtensionMap.getValue(FileType.Module))

    @BeforeTest
    fun resetGenerator() {
        gen = newGen()
    }

    /** Convenience for translators that need a library configuration. */
    protected val libraryConfigs =
        LibraryConfiguration(
            libraryName = DashedIdentifier("translator-test"),
            classifyTemperSource = { error("unsupported operation") },
            libraryRoot = dirPath("example"),
            supportedBackendList = listOf(meta.backendId),
        ).let { lc ->
            LibraryConfigurationsBundle.from(listOf(lc)).withCurrentLibrary(lc)
        }

    /** Loads the expected output for a given test. */
    abstract fun loadExpected(testName: String): String?

    /** Translate a module. */
    abstract fun translateTmplToBackend(ast: TmpL.Module): List<OutTree<*>>

    /** Translate a top level statement. */
    open fun translateTmplToBackend(ast: TmpL.TopLevel): List<OutTree<*>> = translateTmplToBackend(
        gen.module {
            topLevels.add(ast)
        },
    )

    /** Translate an import. */
    open fun translateTmplToBackend(ast: TmpL.Import): List<OutTree<*>> = translateTmplToBackend(
        gen.module {
            imports.add(ast)
        },
    )

    /** Translate a regular statement. */
    open fun translateTmplToBackend(ast: TmpL.Statement): List<OutTree<*>> = translateTmplToBackend(
        gen.module {
            initBlock(ast)
        },
    )

    /** Translate an expression. */
    open fun translateTmplToBackend(ast: TmpL.Expression): List<OutTree<*>> = translateTmplToBackend(
        gen.module {
            result = ast
        },
    )

    open fun translateTmplToBackend(ast: TmpL.Tree): List<OutTree<*>> = when (ast) {
        is TmpL.Module -> translateTmplToBackend(ast)
        is TmpL.Import -> translateTmplToBackend(ast)
        is TmpL.TopLevel -> translateTmplToBackend(ast)
        is TmpL.Statement -> translateTmplToBackend(ast)
        is TmpL.Expression -> translateTmplToBackend(ast)
        else -> throw IllegalStateException("Tests shouldn't construct this type directly")
    }

    /** Override this to provide formatting hints. */
    open val formattingHints: FormattingHints?
        get() = null

    open fun wrapTokenSink(tokenSink: TokenSink) = tokenSink

    open fun renderBackend(ast: List<OutTree<*>>): String =
        ast.joinToString(separator = "\n") {
            genericRender(it, wrapTokenSink = ::wrapTokenSink, hints = formattingHints, singleLine = false)
        }

    open fun gradeTest(expected: String, actual: String, testName: String, tmplText: String) {
        assertEquals(expected.trimEnd(), actual.trimEnd(), "$testName\n$tmplText")
    }

    /** By default, fail missing tests. You can override unimplemented translations with [kotlin.test.Ignore]s. */
    open fun missingTest(testName: String, backendText: String) {
        fail("Didn't define or ignore $testName but expected\n$backendText")
    }

    /**
     * Don't expect this would often be overridden because all the functions it calls are open.
     * However, if you need to
     */
    open fun doTest(testName: String, generateTmpl: TmplGenerator.() -> TmpL.Tree) {
        val backendExpected = loadExpected(testName)
        val tmplAst = generateTmpl(gen)
        val tmplText = genericRender(tmplAst, wrapTokenSink = ::wrapTokenSink, singleLine = true)
        val backendAst = translateTmplToBackend(tmplAst)
        val backendText = renderBackend(backendAst)
        if (backendExpected != null) {
            gradeTest(expected = backendExpected, actual = backendText, testName = testName, tmplText = tmplText)
        } else {
            missingTest(testName, backendText)
        }
    }

    @Test
    open fun overrideThis() {
        fail("Override this test to do nothing and annotate it @Test; this helps IntelliJ recognize your test suite.")
    }

    @Test
    open fun moduleMinimal() {
        doTest("moduleMinimal") {
            module {}
        }
    }

    @Test
    open fun moduleWithImport() {
        doTest("moduleWithImport") {
            module {
                listOf("frobnicate", "lunarWayneshaft").forEach { builtinKey ->
                    import(builtinKey) {
                        type = WKT.stringType2
                        exampleSameLibrary()
                    }
                }
            }
        }
    }

    @Test
    open fun moduleWithTopLevel() {
        doTest("moduleWithTopLevel") {
            module {
                decl(
                    makeParsedName("exampleName"),
                    type = WKT.stringType2,
                    init = value("example assigned value"),
                    assignOnce = true,
                )
            }
        }
    }

    @Test
    open fun moduleWithResult() {
        doTest("moduleWithResult") {
            module {
                result = value("example module result")
            }
        }
    }

    @Test
    open fun importNoLocalName() {
        doTest("importNoLocalName") {
            import("Deque") {
                localName = null
                type = WKT.stringType2
                importType()
                exampleSameLibrary()
            }
        }
    }

    @Test
    open fun importOne() {
        doTest("importOne") {
            import("pi") {
                example()
            }
        }
    }

    @Test
    open fun importThree() {
        doTest("importThree") {
            module {
                listOf("math", "pieCharts", "magic").forEach {
                    import(it) {
                        example()
                    }
                }
            }
        }
    }

    @Test
    open fun expressionStatement() {
        doTest("expressionStatement") {
            value(FORTY_TWO).stmt()
        }
    }

    @Test
    open fun assignmentToValue() {
        doTest("assignmentToValue") {
            makeId("maybeValue") assignTo value(false)
        }
    }

    @Test
    open fun blockStatementEmpty() {
        // Wrap in an if statement because translators may simply unwrap blocks internally.
        doTest("blockStatementEmpty") {
            ifThen(
                value(true),
                block(),
            )
        }
    }

    @Test
    open fun blockStatementOne() {
        doTest("blockStatementOne") {
            val doThing = makeParsedName("doThing")
            // Wrap in an if statement because translators may simply unwrap blocks internally.
            ifThen(
                value(true),
                block(call(doThing, stringToVoid, value("one")).stmt()),
            )
        }
    }

    @Test
    open fun blockStatementThree() {
        doTest("blockStatementThree") {
            val doThing = makeParsedName("doThing")
            // Wrap in an if statement because translators may simply unwrap blocks internally.
            ifThen(
                value(true),
                block(
                    call(doThing, stringToVoid, value("one")).stmt(),
                    call(doThing, stringToVoid, value("two")).stmt(),
                    call(doThing, stringToVoid, value("three")).stmt(),
                ),
            )
        }
    }

    @Test
    open fun blockStatementBreaking() {
        doTest("blockStatementBreaking") {
            val doThing = makeParsedName("doThing")
            val blockLabel = makeParsedName("label")
            // Wrap in an if statement because translators may simply unwrap blocks internally.
            label(
                blockLabel,
                block(
                    call(doThing, stringToVoid, value("one")).stmt(),
                    call(doThing, stringToVoid, value("two")).stmt(),
                    breakStmt(blockLabel),
                    call(doThing, stringToVoid, value("three")).stmt(),
                ),
            )
        }
    }

    @Test
    open fun whileReturnEarly() {
        doTest("whileReturnEarly") {
            block(
                call("beforeLoop", noneToVoid).stmt(),
                whileLoop(
                    call("loopPredicate", noneToBoolean),
                    call("beforeTest", noneToVoid).stmt(),
                    ifThen(
                        call("earlyPredicate", noneToBoolean),
                        call("beforeReturn", noneToVoid).stmt(),
                        returnStmt(value(FORTY_NINE)),
                    ),
                    call("afterTest", noneToVoid).stmt(),
                ),
                call("afterLoop", noneToVoid).stmt(),
                returnStmt(value(FORTY_TWO)),
            )
        }
    }

    @Test
    open fun whileReturnEarlySimple() {
        doTest("whileReturnEarlySimple") {
            block(
                whileLoop(
                    call("loopPredicate", noneToBoolean),
                    ifThen(
                        call("earlyPredicate", noneToBoolean),
                        returnStmt(value(FORTY_NINE)),
                    ),
                ),
                returnStmt(value(FORTY_TWO)),
            )
        }
    }

    @Test
    open fun whileBreakEarly() {
        doTest("whileBreakEarly") {
            block(
                call("beforeLoop", noneToVoid).stmt(),
                whileLoop(
                    call("loopPredicate", noneToBoolean),
                    call("beforeTest", noneToVoid).stmt(),
                    ifThen(
                        call("earlyPredicate", noneToBoolean),
                        call("beforeBreak", noneToVoid).stmt(),
                        breakStmt(),
                    ),
                    call("afterTest", noneToVoid).stmt(),
                ),
                call("afterLoop", noneToVoid).stmt(),
                returnStmt(value(FORTY_TWO)),
            )
        }
    }

    @Test
    open fun whileBreakEarlySimple() {
        doTest("whileBreakEarlySimple") {
            block(
                whileLoop(
                    call("loopPredicate", noneToBoolean),
                    ifThen(
                        call("earlyPredicate", noneToBoolean),
                        breakStmt(),
                    ),
                ),
                returnStmt(value(FORTY_TWO)),
            )
        }
    }

    @Test
    open fun whileBreakNested() {
        doTest("whileBreakNested") {
            val outer = makeLabel("outer")
            block(
                call("beforeOuter", noneToVoid).stmt(),
                label(
                    outer,
                    whileLoop(
                        call("outerPredicate", noneToBoolean),
                        call("beforeInner", noneToVoid).stmt(),
                        whileLoop(
                            call("innerPredicate", noneToBoolean),
                            call("beforeInner", noneToVoid).stmt(),
                            ifThen(
                                call("earlyPredicate", noneToBoolean),
                                call("beforeBreak", noneToVoid).stmt(),
                                breakStmt(outer),
                            ),
                            call("afterTest", noneToVoid).stmt(),
                        ),
                        call("afterInner", noneToVoid).stmt(),
                    ),
                ),
                call("afterOuter", noneToVoid).stmt(),
                returnStmt(value(FORTY_TWO)),
            )
        }
    }

    @Test
    open fun whileBreakNestedSimple() {
        doTest("whileBreakNestedSimple") {
            val outer = makeLabel("outer")
            block(
                label(
                    outer,
                    whileLoop(
                        call("outerPredicate", noneToBoolean),
                        whileLoop(
                            call("innerPredicate", noneToBoolean),
                            ifThen(
                                call("earlyPredicate", noneToBoolean),
                                breakStmt(outer),
                            ),
                        ),
                    ),
                ),
                returnStmt(value(FORTY_TWO)),
            )
        }
    }

    @Test
    open fun whileContinueSkip() {
        doTest("whileContinueSkip") {
            block(
                call("beforeLoop", noneToVoid).stmt(),
                whileLoop(
                    call("loopPredicate", noneToBoolean),
                    call("beforeTest", noneToVoid).stmt(),
                    ifThen(
                        call("skipPredicate", noneToBoolean),
                        call("beforeContinue", noneToVoid).stmt(),
                        continueStmt(),
                    ),
                    call("afterTest", noneToVoid).stmt(),
                ),
                call("afterLoop", noneToVoid).stmt(),
                returnStmt(value(FORTY_TWO)),
            )
        }
    }

    @Test
    open fun whileContinueSkipSimple() {
        doTest("whileContinueSkipSimple") {
            block(
                whileLoop(
                    call("loopPredicate", noneToBoolean),
                    ifThen(
                        call("skipPredicate", noneToBoolean),
                        continueStmt(),
                    ),
                ),
                returnStmt(value(FORTY_TWO)),
            )
        }
    }

    @Test
    open fun whileContinueNested() {
        doTest("whileContinueNested") {
            val outer = makeLabel("outer")
            block(
                call("beforeOuter", noneToVoid).stmt(),
                label(
                    outer,
                    whileLoop(
                        call("outerPredicate", noneToBoolean),
                        call("beforeInner", noneToVoid).stmt(),
                        whileLoop(
                            call("innerPredicate", noneToBoolean),
                            call("beforeInner", noneToVoid).stmt(),
                            ifThen(
                                call("skipPredicate", noneToBoolean),
                                call("beforeContinue", noneToVoid).stmt(),
                                continueStmt(outer),
                            ),
                            call("afterTest", noneToVoid).stmt(),
                        ),
                        call("afterInner", noneToVoid).stmt(),
                    ),
                ),
                call("afterOuter", noneToVoid).stmt(),
                returnStmt(value(FORTY_TWO)),
            )
        }
    }

    @Test
    open fun whileContinueNestedSimple() {
        doTest("whileContinueNestedSimple") {
            val outer = makeLabel("outer")
            block(
                label(
                    outer,
                    whileLoop(
                        call("outerPredicate", noneToBoolean),
                        whileLoop(
                            call("innerPredicate", noneToBoolean),
                            ifThen(
                                call("skipPredicate", noneToBoolean),
                                continueStmt(outer),
                            ),
                        ),
                    ),
                ),
                returnStmt(value(FORTY_TWO)),
            )
        }
    }

    @Test
    open fun whileNestedBreakContinue() {
        doTest("whileNestedBreakContinue") {
            val outer = makeLabel("outer")
            block(
                label(
                    outer,
                    whileLoop(
                        call("outerPredicate", noneToBoolean),
                        whileLoop(
                            call("innerPredicate", noneToBoolean),
                            ifThen(
                                call("skipPredicate", noneToBoolean),
                                continueStmt(outer),
                            ),
                            ifThen(
                                call("earlyPredicate", noneToBoolean),
                                breakStmt(outer),
                            ),
                        ),
                    ),
                ),
                returnStmt(value(FORTY_TWO)),
            )
        }
    }

    // TODO(mikesamuel, specialization): this testcase is overridden in {Js,Py}TranslatorTest
    // since those backends use BubbleBranchStrategy.ThrowBubble so their invocations of
    // TmpLTranslator do not produce TmpL.HandlerScope.
    @Test
    open fun assignToHse() {
        doTest("assignToHse") {
            makeId("val") assignTo TmpL.HandlerScope(
                p0,
                makeId("failed"),
                value("dummy"),
            )
        }
    }

    private fun abcOp(op: TmpLOperator.Infix, assoc: Associativity): TmpL.Expression =
        operatorJoin(
            gen.nullValue(WKT.booleanType2),
            listOf("a", "b", "c").map {
                gen.makeRef(gen.makeBuiltin(it), WKT.booleanType2)
            },
            assoc,
        ) { left, right ->
            TmpL.InfixOperation(
                p0,
                left,
                TmpL.InfixOperator(p0, op),
                right,
            )
        }

    @Test
    open fun expressionAssociativityLeftAmp() {
        doTest("expressionAssociativityLeftAmp") {
            abcOp(TmpLOperator.AmpAmp, Associativity.Left)
        }
    }

    @Test
    open fun expressionAssociativityRightAmp() {
        doTest("expressionAssociativityRightAmp") {
            abcOp(TmpLOperator.AmpAmp, Associativity.Right)
        }
    }

    @Test
    open fun expressionAssociativityLeftPlus() {
        doTest("expressionAssociativityLeftPlus") {
            abcOp(TmpLOperator.PlusInt, Associativity.Left)
        }
    }

    @Test
    open fun expressionAssociativityRightPlus() {
        doTest("expressionAssociativityRightPlus") {
            abcOp(TmpLOperator.PlusInt, Associativity.Right)
        }
    }

    @Test
    open fun unexportedClass() {
        doTest("unexportedClass") {
            val classNameText = makeParsedName("Thing")
            val constructorName = makeParsedName("constructor")
            val propertyName = makeSourceName("propName")
            val privateMethodName = makeSourceName("privateMethod")

            module {
                typeDecl(classNameText) {
                    kind = TmpL.TypeDeclarationKind.Class
                    abstractness = Abstractness.Concrete
                    constructor(constructorName) {
                        thisName = makeParsedName("this")
                        addFormal(makeParsedName("blah"), WKT.stringType2)
                    }
                    method(makeSourceName("funName")) {
                        exampleMethod()
                    }
                    method(privateMethodName) {
                        visibility = TmpL.Visibility.Private
                        openness = OpenOrClosed.Closed
                        body = null
                    }
                    property(propertyName) {
                        type = WKT.stringType2
                        visibility = TmpL.Visibility.Public
                        abstractness = Abstractness.Concrete
                        assignOnce = true
                    }
                }
            }
        }
    }

    @Test
    open fun funLambdaArgs(): Unit = withTypeTestHarness(
        """
            |@fun interface Gamma<T>(a: String, b: String): T;
        """.trimMargin(),
    ) {
        doTest("funLambdaArgs") {
            val typeParamWord = Symbol("X")
            val typeParamX = makeParsedName(typeParamWord.text)
            val typeDefX = TypeFormal(
                pos = p0,
                name = typeParamX,
                symbol = typeParamWord,
                variance = Variance.Invariant,
                mutationCount = AtomicCounter(),
                upperBounds = emptyList(),
            )
            val typeParamRefX = MkType2(typeDefX).get()

            module {
                moduleFunction(makeParsedName("function")) {
                    addTypeFormal(typeDefX)
                    addFormal(makeParsedName("alpha"), WKT.stringType2)
                    addFormal(makeParsedName("beta"), WKT.intType2)
                    val gammaType = MkType2(getDefinition("Gamma") as TypeShape)
                        .actuals(listOf(typeParamRefX))
                        .get()
                    val gammaName = makeParsedName("gamma")
                    when (supportNetwork.functionTypeStrategy) {
                        FunctionTypeStrategy.ToFunctionalInterface -> addFormal(gammaName, gammaType)
                        FunctionTypeStrategy.ToFunctionType -> addFormal(
                            gammaName,
                            gammaType,
                            TmpL.FunctionType(
                                p0,
                                typeParameters = TmpL.ATypeParameters(
                                    TmpL.TypeParameters(p0, listOf()),
                                ),
                                valueFormals = TmpL.ValueFormalList(
                                    p0,
                                    listOf(
                                        TmpL.ValueFormal(p0, null, WKT.stringType2.asTmpLType().aType, false),
                                        TmpL.ValueFormal(p0, null, WKT.stringType2.asTmpLType().aType, false),
                                    ),
                                    null,
                                ),
                                returnType = typeParamRefX.asTmpLType().aType,
                            ).aType,
                        )
                    }
                    returnType = WKT.stringType2
                    body = block()
                    mayYield = false
                }
            }
        }
    }

    /**
     * Translates:
     * {@code let function(alpha: Int, beta: Int = 77, gamma: Int) { alpha + beta + gamma } }
     */
    @Suppress("MagicNumber") // need a value for test
    @Test
    open fun trailingRequiredArgs() {
        doTest("trailingRequiredArgs") {
            module {
                moduleFunction(makeParsedName("function")) {
                    val alphaName = makeParsedName("alpha")
                    val betaName = makeParsedName("beta")
                    val gammaName = makeParsedName("gamma")
                    val typ = WKT.intType2

                    addFormal(alphaName, typ)
                    addFormal(betaName, typ, optionalState = TriState.TRUE)
                    addFormal(gammaName, typ)
                    val returnId = tmplGen.makeId("return")
                    returnType = typ

                    fun ResolvedName.ref() =
                        TmpL.Reference(tmplGen.makeId(this), typ)

                    operator fun TmpL.Expression.plus(o: TmpL.Expression) =
                        TmpL.InfixOperation(p0, this, TmpL.InfixOperator(p0, TmpLOperator.PlusInt), o)

                    body = tmplGen.block(
                        tmplGen.localDecl(
                            returnId,
                            returnType.asTmpLType().aType,
                            returnType,
                        ),
                        // Just pretend to do optional coalescing because correct code is awkward and unneeded.
                        tmplGen.ifThen(
                            TmpL.ValueReference(p0, TBoolean.valueTrue),
                            TmpL.Assignment(
                                p0, tmplGen.makeId(betaName), tmplGen.value(77),
                                WKT.intType2,
                            ),
                        ),
                        returnId assignTo alphaName.ref() + betaName.ref() + gammaName.ref(),
                        tmplGen.returnStmt(TmpL.Reference(p0, returnId, typ)),
                    )
                }
            }
        }
    }

    @Test
    open fun exportedFun() {
        doTest("exportedFun") {
            module {
                moduleFunction(makeExportedName("funName")) {
                    exampleFunction()
                }
            }
        }
    }

    /** Skip this one if not using [lang.temper.be.tmpl.CoroutineStrategy.TranslateToGenerator] */
    @Test
    open fun simpleGenerator() {
        doTest("simpleGenerator") {
            module {
                topLevels.add(
                    // A simple lambda like
                    //
                    //     { (): GeneratorResult<Empty> extends GeneratorFn =>
                    //       yield;
                    //       return empty();
                    //     }
                    TmpL.ModuleFunctionDeclaration(
                        p0,
                        metadata = emptyList(),
                        name = makeId("simpleGenerator"),
                        typeParameters = TmpL.ATypeParameters(
                            TmpL.TypeParameters(p0, emptyList()),
                        ),
                        parameters = TmpL.Parameters(p0, null, emptyList(), null),
                        returnType = TmpL.NominalType(
                            p0,
                            TmpL.TemperTypeName(p0, WKT.safeGeneratorTypeDefinition),
                            listOf(
                                TmpL.NominalType(
                                    p0,
                                    TmpL.TemperTypeName(p0, WKT.emptyTypeDefinition),
                                    emptyList(),
                                ).aType,
                            ),
                        ).aType,
                        body = block(
                            TmpL.YieldStatement(p0),
                            TmpL.ReturnStatement(
                                p0,
                                (
                                    supportNetwork.translateConnectedReference(p0, "empty", Genre.Library)
                                        as? InlineSupportCode<*, *>
                                    )
                                    ?.let {
                                        TmpL.CallExpression(
                                            p0,
                                            TmpL.InlineSupportCodeWrapper(
                                                p0,
                                                type = Signature2(WKT.emptyType2, hasThisFormal = false, listOf()),
                                                it,
                                            ),
                                            listOf(),
                                            WKT.emptyType2,
                                        )
                                    }
                                    ?: TmpL.Reference(
                                        TmpL.Id(p0, BuiltinName("empty"), null),
                                        WKT.emptyType2,
                                    ),
                            ),
                        ),
                        mayYield = true,
                        sig = Signature2(
                            MkType2(WKT.safeGeneratorTypeDefinition).actuals(listOf(WKT.emptyType2)).get(),
                            hasThisFormal = false,
                            listOf(),
                        ),
                    ),
                )
            }
        }
    }
}

private fun genericRender(
    ast: OutTree<*>,
    singleLine: Boolean,
    wrapTokenSink: (TokenSink) -> TokenSink,
    hints: FormattingHints? = null,
): String =
    toStringViaTokenSink(
        formattingHints = hints ?: ast.formattingHints(),
        singleLine = singleLine,
    ) {
        CodeFormatter(wrapTokenSink(it)).format(ast, ast.childCount != 0)
    }
