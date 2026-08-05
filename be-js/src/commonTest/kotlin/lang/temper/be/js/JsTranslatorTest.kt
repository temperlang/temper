package lang.temper.be.js

import lang.temper.be.TranslatorTests
import lang.temper.be.tmpl.TmpL
import lang.temper.format.TokenSink
import lang.temper.lexer.Genre
import kotlin.test.Ignore
import kotlin.test.Test

class JsTranslatorTest : TranslatorTests(JsBackend.Factory.backendMeta, JsSupportNetwork) {
    override fun loadExpected(testName: String): String? = testNameToExpectedCode[testName]

    private fun translator() = JsTranslator(JsNames(), Genre.Library, keepUnusedImports = true)

    override fun wrapTokenSink(tokenSink: TokenSink): TokenSink {
        return JsBackend.wrapTokenSink(tokenSink)
    }

    override fun translateTmplToBackend(ast: TmpL.Module): List<Js.Tree> =
        translator().translate(ast).map { it.program }
    override fun translateTmplToBackend(ast: TmpL.Expression): List<Js.Tree> =
        listOf(translator().translateExpression(ast))

    /** Override to allow IntelliJ to detect tests. */
    @Test
    override fun overrideThis() = Unit

    @Ignore // JS backend uses BubbleBranchStrategy.CatchBubble
    @Test
    override fun assignToHse() = Unit

    companion object {
        private val testNameToExpectedCode = mapOf(
            "moduleMinimal" to """
                |
                |export {} from "./implement.internal.js";
            """.trimMargin(),
            "moduleWithImport" to
                """
                    |import {
                    |  frobnicate as frobnicate_0, lunarWayneshaft as lunarWayneshaft_1
                    |} from "./other.js";
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "moduleWithTopLevel" to
                """
                    |/** @type {string} */
                    |export const exampleName_0 = "example assigned value";
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "moduleWithResult" to """
                |export default "example module result";
                |
                |export {} from "./implement.internal.js";
            """.trimMargin(),
            "importNoLocalName" to """
                |
                |export {} from "./implement.internal.js";
            """.trimMargin(),
            "importOne" to
                """
                    |import {
                    |  pi as pi_0
                    |} from "./other.js";
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "importThree" to
                """
                    |import {
                    |  math as math_0, pieCharts as pieCharts_1, magic as magic_2
                    |} from "./other.js";
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "expressionStatement" to
                """
                    |42;
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "assignmentToValue" to """
                |maybeValue_0 = false;
                |
                |export {} from "./implement.internal.js";
            """.trimMargin(),
            "blockStatementEmpty" to
                """
                    |if (true) {
                    |}
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "blockStatementOne" to
                """
                    |if (true) {
                    |  doThing_0("one");
                    |}
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "blockStatementThree" to
                """
                    |if (true) {
                    |  doThing_0("one");
                    |  doThing_0("two");
                    |  doThing_0("three");
                    |}
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "blockStatementBreaking" to
                """
                    |label_0: {
                    |  doThing_1("one");
                    |  doThing_1("two");
                    |  break label_0;
                    |  doThing_1("three");
                    |}
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "whileReturnEarly" to
                """
                    |{
                    |  beforeLoop_0();
                    |  while (loopPredicate_1()) {
                    |    beforeTest_2();
                    |    if (earlyPredicate_3()) {
                    |      beforeReturn_4();
                    |      return 49;
                    |    }
                    |    afterTest_5();
                    |  }
                    |  afterLoop_6();
                    |  return 42;
                    |}
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "whileReturnEarlySimple" to
                """
                    |{
                    |  while (loopPredicate_0()) {
                    |    if (earlyPredicate_1()) {
                    |      return 49;
                    |    }
                    |  }
                    |  return 42;
                    |}
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "whileBreakEarly" to
                """
                    |{
                    |  beforeLoop_0();
                    |  while (loopPredicate_1()) {
                    |    beforeTest_2();
                    |    if (earlyPredicate_3()) {
                    |      beforeBreak_4();
                    |      break;
                    |    }
                    |    afterTest_5();
                    |  }
                    |  afterLoop_6();
                    |  return 42;
                    |}
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "whileBreakEarlySimple" to
                """
                    |{
                    |  while (loopPredicate_0()) {
                    |    if (earlyPredicate_1()) {
                    |      break;
                    |    }
                    |  }
                    |  return 42;
                    |}
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "whileBreakNested" to
                """
                    |{
                    |  beforeOuter_0();
                    |  outer_1: while (outerPredicate_2()) {
                    |    beforeInner_3();
                    |    while (innerPredicate_4()) {
                    |      beforeInner_5();
                    |      if (earlyPredicate_6()) {
                    |        beforeBreak_7();
                    |        break outer_1;
                    |      }
                    |      afterTest_8();
                    |    }
                    |    afterInner_9();
                    |  }
                    |  afterOuter_10();
                    |  return 42;
                    |}
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "whileBreakNestedSimple" to
                """
                    |{
                    |  outer_0: while (outerPredicate_1()) {
                    |    while (innerPredicate_2()) {
                    |      if (earlyPredicate_3()) {
                    |        break outer_0;
                    |      }
                    |    }
                    |  }
                    |  return 42;
                    |}
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "whileContinueSkip" to
                """
                    |{
                    |  beforeLoop_0();
                    |  while (loopPredicate_1()) {
                    |    beforeTest_2();
                    |    if (skipPredicate_3()) {
                    |      beforeContinue_4();
                    |      continue;
                    |    }
                    |    afterTest_5();
                    |  }
                    |  afterLoop_6();
                    |  return 42;
                    |}
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "whileContinueSkipSimple" to
                """
                    |{
                    |  while (loopPredicate_0()) {
                    |    if (skipPredicate_1()) {
                    |      continue;
                    |    }
                    |  }
                    |  return 42;
                    |}
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "whileContinueNested" to
                """
                    |{
                    |  beforeOuter_0();
                    |  outer_1: while (outerPredicate_2()) {
                    |    beforeInner_3();
                    |    while (innerPredicate_4()) {
                    |      beforeInner_5();
                    |      if (skipPredicate_6()) {
                    |        beforeContinue_7();
                    |        continue outer_1;
                    |      }
                    |      afterTest_8();
                    |    }
                    |    afterInner_9();
                    |  }
                    |  afterOuter_10();
                    |  return 42;
                    |}
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "whileContinueNestedSimple" to
                """
                    |{
                    |  outer_0: while (outerPredicate_1()) {
                    |    while (innerPredicate_2()) {
                    |      if (skipPredicate_3()) {
                    |        continue outer_0;
                    |      }
                    |    }
                    |  }
                    |  return 42;
                    |}
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "whileNestedBreakContinue" to
                """
                    |{
                    |  outer_0: while (outerPredicate_1()) {
                    |    while (innerPredicate_2()) {
                    |      if (skipPredicate_3()) {
                    |        continue outer_0;
                    |      }
                    |      if (earlyPredicate_4()) {
                    |        break outer_0;
                    |      }
                    |    }
                    |  }
                    |  return 42;
                    |}
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "exprStatementHse" to
                """failed_0 = false, "dummy" ||(failed_0 = true, null);""",
            "expressionAssociativityLeftAmp" to "a_0 && b_1 && c_2",
            "expressionAssociativityRightAmp" to "a_0 &&(b_1 && c_2)",
            "expressionAssociativityLeftPlus" to "a_0 + b_1 + c_2",
            "expressionAssociativityRightPlus" to "a_0 +(b_1 + c_2)",
            "unexportedClass" to
                """
                    |import {
                    |  type as type__8
                    |} from "@temperlang/core";
                    |export class Thing extends type__8() {
                    |  /** @param {string} blah_0 */
                    |  constructor(blah_0) {
                    |    super ();
                    |  }
                    |  /**
                    |   * @param {string} requiredArg_2
                    |   * @param {number | null} [optionalArg_3]
                    |   * @returns {number}
                    |   */
                    |  funName(requiredArg_2, optionalArg_3) {
                    |    let return_4;
                    |    if (true) {
                    |      optionalArg_3 = 1;
                    |    }
                    |    return_4 = optionalArg_3;
                    |    return return_4;
                    |  }
                    |  /** @type {string} */
                    |  #propName_7;
                    |};
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "exportedFun" to
                """
                    |/**
                    | * @param {string} requiredArg_0
                    | * @param {number | null} [optionalArg_1]
                    | * @returns {number}
                    | */
                    |export function funName(requiredArg_0, optionalArg_1) {
                    |  let return_2;
                    |  if (true) {
                    |    optionalArg_1 = 1;
                    |  }
                    |  return_2 = optionalArg_1;
                    |  return return_2;
                    |};
                    |
                    |export {
                    |  funName
                    |} from "./implement.internal.js";
                """.trimMargin(),
            "funLambdaArgs" to
                """
                    |/**
                    | * @template X_4
                    | * @param {string} alpha_1
                    | * @param {number} beta_2
                    | * @param {(arg0: string, arg1: string) => X_4} gamma_3
                    | * @returns {string}
                    | */
                    |export function function_0(alpha_1, beta_2, gamma_3) {
                    |};
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "trailingRequiredArgs" to
                """
                    |/**
                    | * @param {number} alpha_1
                    | * @param {number | null} [beta_2]
                    | * @param {number} gamma_3
                    | * @returns {number}
                    | */
                    |export function function_0(alpha_1, beta_2, gamma_3) {
                    |  let return_4;
                    |  if (true) {
                    |    beta_2 = 77;
                    |  }
                    |  return_4 = alpha_1 + beta_2 + gamma_3;
                    |  return return_4;
                    |};
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
            "simpleGenerator" to
                """
                    |import {
                    |  adaptAwaiter as adaptAwaiter__2
                    |} from "@temperlang/core";
                    |/** @returns {Generator<{}>} */
                    |export const simpleGenerator_0 = adaptAwaiter__2(function* simpleGenerator_0(await_1) {
                    |    yield null;
                    |    return empty_3;
                    |});
                    |
                    |export {} from "./implement.internal.js";
                """.trimMargin(),
        )
    }
}
