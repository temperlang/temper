package lang.temper.be.java

import lang.temper.be.TranslatorTests
import lang.temper.be.tmpl.TmpL
import kotlin.test.Ignore
import kotlin.test.Test

class JavaTranslatorTest : TranslatorTests(
    JavaBackend.Java17.backendMeta,
    JavaSupportNetwork.supportFor(JavaLang.Java17),
) {
    private val jLibConfigs = JavaLibraryConfigs(libraryConfigs)

    private fun javaTranslator() =
        JavaTranslator(
            JavaNames(
                JavaLang.Java17,
                jLibConfigs,
            ),
        )

    override fun translateTmplToBackend(ast: TmpL.Module): List<Java.Tree> =
        javaTranslator().translateSnippet(ast).map(::simplifyNames)

    override fun translateTmplToBackend(ast: TmpL.Expression): List<Java.Tree> =
        listOf(javaTranslator().translateSnippet(ast)).map(::simplifyNames)

    override fun translateTmplToBackend(ast: TmpL.Statement): List<Java.Tree> =
        listOf(javaTranslator().translateSnippet(ast)).map(::simplifyNames)

    override fun loadExpected(testName: String): String? = testNameToExpectedCode[testName]

    @Test
    override fun overrideThis() {
        // Dummy to help junit recognize inherited tests.
    }

    @Ignore
    @Test
    override fun simpleGenerator() {
        // We opt out of translating to generator functions.
    }

    companion object {
        @SuppressWarnings("MaxLineLength")
        private val testNameToExpectedCode = mapOf(
            "moduleMinimal" to
                """
                    |package test;
                    |public final class TestGlobal {
                    |    private TestGlobal() {
                    |    }
                    |}
                """.trimMargin(),
            "moduleWithImport" to
                """
                    |package test;
                    |public final class TestGlobal {
                    |    private TestGlobal() {
                    |    }
                    |}
                """.trimMargin(),
            "moduleWithTopLevel" to
                """
                    |package test;
                    |public final class TestGlobal {
                    |    private TestGlobal() {
                    |    }
                    |    static final String exampleName_7;
                    |    static {
                    |        exampleName_7 = "example assigned value";
                    |    }
                    |}
                """.trimMargin(),
            "moduleWithResult" to
                """
                    |package test;
                    |public final class TestGlobal {
                    |    private TestGlobal() {
                    |    }
                    |    public static String export;
                    |    static {
                    |        export = "example module result";
                    |    }
                    |}
                """.trimMargin(),
            "importNoLocalName" to
                """
                    |package test;
                    |public final class TestGlobal {
                    |    private TestGlobal() {
                    |    }
                    |}
                """.trimMargin(),
            "importOne" to
                """
                    |package test;
                    |public final class TestGlobal {
                    |    private TestGlobal() {
                    |    }
                    |}
                """.trimMargin(),
            "importThree" to
                """
                    |package test;
                    |public final class TestGlobal {
                    |    private TestGlobal() {
                    |    }
                    |}
                """.trimMargin(),
            "expressionStatement" to "/* 42: Literal expression statement */",
            "assignmentToValue" to "maybeValue_7 = false;",
            "assignToHse" to """failed_8 = val_7 = "dummy" == null;""",
            "blockStatementEmpty" to
                """
                    |if (true) {
                    |}
                """.trimMargin(),
            "blockStatementOne" to
                """
                    |if (true) {
                    |    TestGlobal.doThing_7("one");
                    |}
                """.trimMargin(),
            "blockStatementThree" to
                """
                    |if (true) {
                    |    TestGlobal.doThing_7("one");
                    |    TestGlobal.doThing_7("two");
                    |    TestGlobal.doThing_7("three");
                    |}
                """.trimMargin(),
            "blockStatementBreaking" to
                """
                    |label_8: {
                    |    TestGlobal.doThing_7("one");
                    |    TestGlobal.doThing_7("two");
                    |    break label_8;
                    |    TestGlobal.doThing_7("three");
                    |}
                """.trimMargin(),
            "expressionAssociativityLeftAmp" to "a && b && c",
            "expressionAssociativityRightAmp" to "a && (b && c)",
            "expressionAssociativityLeftPlus" to "a + b + c",
            "expressionAssociativityRightPlus" to "a + (b + c)",
            "unexportedClass" to
                """
                    |package test;
                    |import temper.core.Nullable;
                    |final class Thing {
                    |    public Thing(String blah_12) {
                    |    }
                    |    public int funName(String requiredArg_17, @Nullable Integer optionalArg_15) {
                    |        int return_16;
                    |        if (true) {
                    |            optionalArg_15 = 1;
                    |        }
                    |        return_16 = optionalArg_15;
                    |        return return_16;
                    |    }
                    |    public int funName(String requiredArg_17) {
                    |        return funName(requiredArg_17, null);
                    |    }
                    |    void privateMethod() {
                    |    }
                    |    public final String propName;
                    |}
                    |
                    |package test;
                    |public final class TestGlobal {
                    |    private TestGlobal() {
                    |    }
                    |}
                """.trimMargin(),
            "exportedFun" to
                """
                    |package test;
                    |import temper.core.Nullable;
                    |public final class TestGlobal {
                    |    private TestGlobal() {
                    |    }
                    |    public static int funName(String requiredArg_9, @Nullable Integer optionalArg_7) {
                    |        int return_8;
                    |        if (true) {
                    |            optionalArg_7 = 1;
                    |        }
                    |        return_8 = optionalArg_7;
                    |        return return_8;
                    |    }
                    |    public static int funName(String requiredArg_9) {
                    |        return funName(requiredArg_9, null);
                    |    }
                    |}
                """.trimMargin(),
            "funLambdaArgs" to
                """
                    |package test;
                    |import java.util.function.BiFunction;
                    |public final class TestGlobal {
                    |    private TestGlobal() {
                    |    }
                    |    static<X_7> String function_8(String alpha_9, int beta_10, BiFunction<String, String, X_7> gamma_11) {
                    |    }
                    |}
                """.trimMargin(),
            "trailingRequiredArgs" to
                """
                    |package test;
                    |import temper.core.Nullable;
                    |public final class TestGlobal {
                    |    private TestGlobal() {
                    |    }
                    |    static int function_7(int alpha_8, @Nullable Integer beta_9, int gamma_10) {
                    |        int return_11;
                    |        if (true) {
                    |            beta_9 = 77;
                    |        }
                    |        return_11 = alpha_8 + beta_9 + gamma_10;
                    |        return return_11;
                    |    }
                    |}
                """.trimMargin(),
            "whileNestedBreakContinue" to
                """
                    |outer_7: while (TestGlobal.outerPredicate_8()) {
                    |    while (TestGlobal.innerPredicate_9()) {
                    |        if (TestGlobal.skipPredicate_10()) {
                    |            continue outer_7;
                    |        }
                    |        if (TestGlobal.earlyPredicate_11()) {
                    |            break outer_7;
                    |        }
                    |    }
                    |}
                    |return 42;
                """.trimMargin(),
            "whileBreakEarlySimple" to
                """
                    |while (TestGlobal.loopPredicate_7()) {
                    |    if (TestGlobal.earlyPredicate_8()) {
                    |        break;
                    |    }
                    |}
                    |return 42;
                """.trimMargin(),
            "whileBreakEarly" to
                """
                    |TestGlobal.beforeLoop_7();
                    |while (TestGlobal.loopPredicate_8()) {
                    |    TestGlobal.beforeTest_9();
                    |    if (TestGlobal.earlyPredicate_10()) {
                    |        TestGlobal.beforeBreak_11();
                    |        break;
                    |    }
                    |    TestGlobal.afterTest_12();
                    |}
                    |TestGlobal.afterLoop_13();
                    |return 42;
                """.trimMargin(),
            "whileBreakNested" to
                """
                    |TestGlobal.beforeOuter_8();
                    |outer_7: while (TestGlobal.outerPredicate_9()) {
                    |    TestGlobal.beforeInner_10();
                    |    while (TestGlobal.innerPredicate_11()) {
                    |        TestGlobal.beforeInner_12();
                    |        if (TestGlobal.earlyPredicate_13()) {
                    |            TestGlobal.beforeBreak_14();
                    |            break outer_7;
                    |        }
                    |        TestGlobal.afterTest_15();
                    |    }
                    |    TestGlobal.afterInner_16();
                    |}
                    |TestGlobal.afterOuter_17();
                    |return 42;
                """.trimMargin(),
            "whileBreakNestedSimple" to
                """
                    |outer_7: while (TestGlobal.outerPredicate_8()) {
                    |    while (TestGlobal.innerPredicate_9()) {
                    |        if (TestGlobal.earlyPredicate_10()) {
                    |            break outer_7;
                    |        }
                    |    }
                    |}
                    |return 42;
                """.trimMargin(),
            "whileContinueSkipSimple" to
                """
                    |while (TestGlobal.loopPredicate_7()) {
                    |    if (TestGlobal.skipPredicate_8()) {
                    |        continue;
                    |    }
                    |}
                    |return 42;
                """.trimMargin(),
            "whileContinueSkip" to
                """
                    |TestGlobal.beforeLoop_7();
                    |while (TestGlobal.loopPredicate_8()) {
                    |    TestGlobal.beforeTest_9();
                    |    if (TestGlobal.skipPredicate_10()) {
                    |        TestGlobal.beforeContinue_11();
                    |        continue;
                    |    }
                    |    TestGlobal.afterTest_12();
                    |}
                    |TestGlobal.afterLoop_13();
                    |return 42;
                """.trimMargin(),
            "whileContinueNestedSimple" to
                """
                    |outer_7: while (TestGlobal.outerPredicate_8()) {
                    |    while (TestGlobal.innerPredicate_9()) {
                    |        if (TestGlobal.skipPredicate_10()) {
                    |            continue outer_7;
                    |        }
                    |    }
                    |}
                    |return 42;
                """.trimMargin(),
            "whileContinueNested" to
                """
                    |TestGlobal.beforeOuter_8();
                    |outer_7: while (TestGlobal.outerPredicate_9()) {
                    |    TestGlobal.beforeInner_10();
                    |    while (TestGlobal.innerPredicate_11()) {
                    |        TestGlobal.beforeInner_12();
                    |        if (TestGlobal.skipPredicate_13()) {
                    |            TestGlobal.beforeContinue_14();
                    |            continue outer_7;
                    |        }
                    |        TestGlobal.afterTest_15();
                    |    }
                    |    TestGlobal.afterInner_16();
                    |}
                    |TestGlobal.afterOuter_17();
                    |return 42;
                """.trimMargin(),
            "whileReturnEarly" to
                """
                    |TestGlobal.beforeLoop_7();
                    |while (TestGlobal.loopPredicate_8()) {
                    |    TestGlobal.beforeTest_9();
                    |    if (TestGlobal.earlyPredicate_10()) {
                    |        TestGlobal.beforeReturn_11();
                    |        return 49;
                    |    }
                    |    TestGlobal.afterTest_12();
                    |}
                    |TestGlobal.afterLoop_13();
                    |return 42;
                """.trimMargin(),
            "whileReturnEarlySimple" to
                """
                    |while (TestGlobal.loopPredicate_7()) {
                    |    if (TestGlobal.earlyPredicate_8()) {
                    |        return 49;
                    |    }
                    |}
                    |return 42;
                """.trimMargin(),
        )
    }
}
