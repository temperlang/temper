package lang.temper.be.tmpl

import lang.temper.be.TranslatorTests
import kotlin.test.Test

class TmplRenderSuite : TranslatorTests(
    meta = TestBackend.Factory.backendMeta,
    supportNetwork = defaultTestSupportNetwork,
) {

    // We're just testing TmpL's own rendering here, so "translation" is pass-through.
    override fun translateTmplToBackend(ast: TmpL.Module): List<TmpL.Tree> = listOf(ast)
    override fun translateTmplToBackend(ast: TmpL.Expression): List<TmpL.Tree> = listOf(ast)
    override fun translateTmplToBackend(ast: TmpL.Import): List<TmpL.Tree> = listOf(ast)
    override fun translateTmplToBackend(ast: TmpL.Statement): List<TmpL.Tree> = listOf(ast)

    @Test
    override fun overrideThis() {
        // Helps IntelliJ recognize this is a test suite.
    }

    /** Just look up results from a map. */
    override fun loadExpected(testName: String): String? = testNameToExpectedCode[testName]

    companion object {
        @SuppressWarnings("MaxLineLength")
        private val testNameToExpectedCode = mapOf(
            "moduleMinimal" to "//// project//module/implement.temper => implement.tmpl\n",
            "moduleWithImport" to
                """
                    |//// project//module/implement.temper => implement.tmpl
                    |let {
                    |  frobnicate as frobnicate__7
                    |}: const String = import ("./other.tmpl");
                    |let {
                    |  lunarWayneshaft as lunarWayneshaft__8
                    |}: const String = import ("./other.tmpl");
                """.trimMargin(),
            "moduleWithTopLevel" to
                """
                    |//// project//module/implement.temper => implement.tmpl
                    |let exampleName#7: String = "example assigned value";
                """.trimMargin(),
            "moduleWithResult" to
                """
                    |//// project//module/implement.temper => implement.tmpl
                    |export "example module result";
                """.trimMargin(),
            "importNoLocalName" to """
                |let {
                |  Deque
                |}: type = import ("./other.tmpl");
            """.trimMargin(),
            "importOne" to """
                |let {
                |  pi as pi__7
                |}: const Float64 = import ("./other.tmpl");
            """.trimMargin(),
            "importThree" to """
                |//// project//module/implement.temper => implement.tmpl
                |let {
                |  math as math__7
                |}: const Float64 = import ("./other.tmpl");
                |let {
                |  pieCharts as pieCharts__8
                |}: const Float64 = import ("./other.tmpl");
                |let {
                |  magic as magic__9
                |}: const Float64 = import ("./other.tmpl");
            """.trimMargin(),
            "expressionStatement" to "42;",
            "assignmentToValue" to """maybeValue#7 = false;""",
            "blockStatementEmpty" to
                """
                    |if (true) {
                    |}
                """.trimMargin(),
            "blockStatementOne" to
                """
                    |if (true) {
                    |  doThing#7("one");
                    |}
                """.trimMargin(),
            "blockStatementThree" to
                """
                    |if (true) {
                    |  doThing#7("one");
                    |  doThing#7("two");
                    |  doThing#7("three");
                    |}
                """.trimMargin(),
            "blockStatementBreaking" to
                """
                    |label#8: {
                    |  doThing#7("one");
                    |  doThing#7("two");
                    |  break label#8;
                    |  doThing#7("three");
                    |}
                """.trimMargin(),
            "whileReturnEarly" to
                """
                    |beforeLoop#7();
                    |while (loopPredicate#8()) {
                    |  beforeTest#9();
                    |  if (earlyPredicate#10()) {
                    |    beforeReturn#11();
                    |    return 49;
                    |  }
                    |  afterTest#12();
                    |}
                    |afterLoop#13();
                    |return 42;
                """.trimMargin(),
            "whileReturnEarlySimple" to
                """
                    |while (loopPredicate#7()) {
                    |  if (earlyPredicate#8()) {
                    |    return 49;
                    |  }
                    |}
                    |return 42;
                """.trimMargin(),
            "whileBreakEarly" to
                """
                    |beforeLoop#7();
                    |while (loopPredicate#8()) {
                    |  beforeTest#9();
                    |  if (earlyPredicate#10()) {
                    |    beforeBreak#11();
                    |    break;
                    |  }
                    |  afterTest#12();
                    |}
                    |afterLoop#13();
                    |return 42;
                """.trimMargin(),
            "whileBreakEarlySimple" to
                """
                    |while (loopPredicate#7()) {
                    |  if (earlyPredicate#8()) {
                    |    break;
                    |  }
                    |}
                    |return 42;
                """.trimMargin(),
            "whileBreakNested" to
                """
                    |beforeOuter#8();
                    |outer#7: while (outerPredicate#9()) {
                    |  beforeInner#10();
                    |  while (innerPredicate#11()) {
                    |    beforeInner#12();
                    |    if (earlyPredicate#13()) {
                    |      beforeBreak#14();
                    |      break outer#7;
                    |    }
                    |    afterTest#15();
                    |  }
                    |  afterInner#16();
                    |}
                    |afterOuter#17();
                    |return 42;
                """.trimMargin(),
            "whileBreakNestedSimple" to
                """
                    |outer#7: while (outerPredicate#8()) {
                    |  while (innerPredicate#9()) {
                    |    if (earlyPredicate#10()) {
                    |      break outer#7;
                    |    }
                    |  }
                    |}
                    |return 42;
                """.trimMargin(),
            "whileContinueSkip" to
                """
                    |beforeLoop#7();
                    |while (loopPredicate#8()) {
                    |  beforeTest#9();
                    |  if (skipPredicate#10()) {
                    |    beforeContinue#11();
                    |    continue;
                    |  }
                    |  afterTest#12();
                    |}
                    |afterLoop#13();
                    |return 42;
                """.trimMargin(),
            "whileContinueSkipSimple" to
                """
                    |while (loopPredicate#7()) {
                    |  if (skipPredicate#8()) {
                    |    continue;
                    |  }
                    |}
                    |return 42;
                """.trimMargin(),
            "whileContinueNested" to
                """
                    |beforeOuter#8();
                    |outer#7: while (outerPredicate#9()) {
                    |  beforeInner#10();
                    |  while (innerPredicate#11()) {
                    |    beforeInner#12();
                    |    if (skipPredicate#13()) {
                    |      beforeContinue#14();
                    |      continue outer#7;
                    |    }
                    |    afterTest#15();
                    |  }
                    |  afterInner#16();
                    |}
                    |afterOuter#17();
                    |return 42;
                """.trimMargin(),
            "whileContinueNestedSimple" to
                """
                    |outer#7: while (outerPredicate#8()) {
                    |  while (innerPredicate#9()) {
                    |    if (skipPredicate#10()) {
                    |      continue outer#7;
                    |    }
                    |  }
                    |}
                    |return 42;
                """.trimMargin(),
            "whileNestedBreakContinue" to
                """
                    |outer#7: while (outerPredicate#8()) {
                    |  while (innerPredicate#9()) {
                    |    if (skipPredicate#10()) {
                    |      continue outer#7;
                    |    }
                    |    if (earlyPredicate#11()) {
                    |      break outer#7;
                    |    }
                    |  }
                    |}
                    |return 42;
                """.trimMargin(),
            "assignToHse" to """val#7 = hs (failed#8, "dummy");""",
            "exprStatementHse" to """hs (failed#7, "dummy");""",
            "expressionAssociativityLeftAmp" to "(a && b) && c",
            "expressionAssociativityRightAmp" to "a && b && c",
            "expressionAssociativityLeftPlus" to "a + b + c",
            "expressionAssociativityRightPlus" to "a + (b + c)",
            "unexportedClass" to
                """
                    |//// project//module/implement.temper => implement.tmpl
                    |class Thing#7 {
                    |  constructor#8(this = this#11, blah#12: String) {
                    |  }
                    |  let funName__14(requiredArg#17: String, optionalArg#15: Int32 | Null = null): Int32 {
                    |    let return#16: Int32;
                    |    if (true) {
                    |      optionalArg#15 = 1;
                    |    }
                    |    return#16 = optionalArg#15;
                    |    return return#16;
                    |  }
                    |  let privateMethod__10(): Void;
                    |  let propName__9: String;
                    |}
                """.trimMargin(),
            "exportedFun" to
                """
                    |//// project//module/implement.temper => implement.tmpl
                    |let funName(requiredArg#9: String, optionalArg#7: Int32 | Null = null): Int32 {
                    |  let return#8: Int32;
                    |  if (true) {
                    |    optionalArg#7 = 1;
                    |  }
                    |  return#8 = optionalArg#7;
                    |  return return#8;
                    |}
                """.trimMargin(),
            "funLambdaArgs" to
                """
                    |//// project//module/implement.temper => implement.tmpl
                    |let function#8<X#7>(alpha#9: String, beta#10: Int32, gamma#11: fn (String, String): X#7): String {
                    |}
                """.trimMargin(),
            "trailingRequiredArgs" to
                """
                    |//// project//module/implement.temper => implement.tmpl
                    |let function#7(alpha#8: Int32, beta#9: Int32 | Null = null, gamma#10: Int32): Int32 {
                    |  let return#11: Int32;
                    |  if (true) {
                    |    beta#9 = 77;
                    |  }
                    |  return#11 = alpha#8 + beta#9 + gamma#10;
                    |  return return#11;
                    |}
                """.trimMargin(),
            "simpleGenerator" to
                """
                    |//// project//module/implement.temper => implement.tmpl
                    |let * simpleGenerator#7(): SafeGenerator<Empty> {
                    |  yield;
                    |  return empty;
                    |}
                """.trimMargin(),
        )
    }
}
