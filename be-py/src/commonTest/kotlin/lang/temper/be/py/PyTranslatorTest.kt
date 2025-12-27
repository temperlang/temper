package lang.temper.be.py

import lang.temper.ast.OutTree
import lang.temper.be.TranslatorTests
import lang.temper.be.names.LookupNameVisitor
import lang.temper.be.tmpl.TmpL
import lang.temper.lexer.Genre
import kotlin.test.Ignore
import kotlin.test.Test

@SuppressWarnings("MaxLineLength")
class PyTranslatorTest : TranslatorTests(PyBackend.Python3.backendMeta, PySupportNetwork) {
    override fun loadExpected(testName: String): String? = testNameToExpectedCode[testName]

    private fun translator(ast: TmpL.Module): PyTranslator {
        val names = PyNames(LookupNameVisitor().visit(gen.moduleSet(ast)))
        names.module = ast.codeLocation.codeLocation
        return PyTranslator(
            names,
            Genre.Library,
            pythonVersion = PythonVersion.Python311,
        )
    }

    override fun translateTmplToBackend(ast: TmpL.Module): List<Py.Tree> {
        return translator(ast).translate(ast)
    }

    override fun translateTmplToBackend(ast: TmpL.Expression): List<OutTree<*>> {
        return listOf(
            translator(
                gen.module {
                    result = ast
                },
            ).expr(ast),
        )
    }

    /** Override to allow IntelliJ to detect tests. */
    @Test
    override fun overrideThis() = Unit

    @Ignore // Py backend uses BubbleBranchStrategy.CatchBubble
    @Test
    override fun assignToHse() = Unit

    // override fun missingTest(testName: String) = Unit

    companion object {
        private val testNameToExpectedCode = mapOf(
            "moduleMinimal" to "",
            "moduleWithImport" to
                "from other import frobnicate as frobnicate_7, lunar_wayneshaft as lunar_wayneshaft_8",
            "moduleWithTopLevel" to "example_name_7: 'str0' = 'example assigned value'",
            "moduleWithResult" to "export = 'example module result'",
            "importNoLocalName" to "",
            "importOne" to "from other import pi as pi_7",
            "importThree" to
                "from other import math as math_7, pie_charts as pie_charts_8, magic as magic_9",
            "expressionStatement" to "42",
            "assignmentToValue" to "maybe_value_7 = False",
            "blockStatementEmpty" to
                """
                    |if True:
                    |    pass
                """.trimMargin(),
            "blockStatementOne" to
                """
                    |if True:
                    |    do_thing_7('one')
                """.trimMargin(),
            "blockStatementThree" to
                """
                    |if True:
                    |    do_thing_7('one')
                    |    do_thing_7('two')
                    |    do_thing_7('three')
                """.trimMargin(),
            "blockStatementBreaking" to
                """
                    |with Label0() as label_8:
                    |    do_thing_7('one')
                    |    do_thing_7('two')
                    |    label_8.break_()
                    |    do_thing_7('three')
                """.trimMargin(),
            "whileReturnEarly" to
                """
                    |before_loop_7()
                    |while loop_predicate_8():
                    |    before_test_9()
                    |    if early_predicate_10():
                    |        before_return_11()
                    |        return 49
                    |    after_test_12()
                    |after_loop_13()
                    |return 42
                """.trimMargin(),
            "whileReturnEarlySimple" to
                """
                    |while loop_predicate_7():
                    |    if early_predicate_8():
                    |        return 49
                    |return 42
                """.trimMargin(),
            "whileBreakEarly" to
                """
                    |before_loop_7()
                    |while loop_predicate_8():
                    |    before_test_9()
                    |    if early_predicate_10():
                    |        before_break_11()
                    |        break
                    |    after_test_12()
                    |after_loop_13()
                    |return 42
                """.trimMargin(),
            "whileBreakEarlySimple" to
                """
                    |while loop_predicate_7():
                    |    if early_predicate_8():
                    |        break
                    |return 42
                    |
                """.trimMargin(),
            "whileBreakNested" to
                """
                    |before_outer_8()
                    |with Label0() as outer_7:
                    |    while outer_predicate_9():
                    |        before_inner_10()
                    |        while inner_predicate_11():
                    |            before_inner_12()
                    |            if early_predicate_13():
                    |                before_break_14()
                    |                outer_7.break_()
                    |            after_test_15()
                    |        after_inner_16()
                    |after_outer_17()
                    |return 42
                """.trimMargin(),
            "whileBreakNestedSimple" to
                """
                    |with Label0() as outer_7:
                    |    while outer_predicate_8():
                    |        while inner_predicate_9():
                    |            if early_predicate_10():
                    |                outer_7.break_()
                    |return 42
                """.trimMargin(),
            "whileContinueSkip" to
                """
                    |before_loop_7()
                    |while loop_predicate_8():
                    |    before_test_9()
                    |    if skip_predicate_10():
                    |        before_continue_11()
                    |        continue
                    |    after_test_12()
                    |after_loop_13()
                    |return 42
                """.trimMargin(),
            "whileContinueSkipSimple" to
                """
                    |while loop_predicate_7():
                    |    if skip_predicate_8():
                    |        continue
                    |return 42
                """.trimMargin(),
            "whileContinueNested" to
                """
                    |before_outer_8()
                    |outer_7 = Label0()
                    |while outer_predicate_9():
                    |    with outer_7:
                    |        before_inner_10()
                    |        while inner_predicate_11():
                    |            before_inner_12()
                    |            if skip_predicate_13():
                    |                before_continue_14()
                    |                outer_7.continue_()
                    |            after_test_15()
                    |        after_inner_16()
                    |after_outer_17()
                    |return 42
                """.trimMargin(),
            "whileContinueNestedSimple" to
                """
                    |outer_7 = Label0()
                    |while outer_predicate_8():
                    |    with outer_7:
                    |        while inner_predicate_9():
                    |            if skip_predicate_10():
                    |                outer_7.continue_()
                    |return 42
                """.trimMargin(),
            "whileNestedBreakContinue" to
                """
                    |with LabelPair0() as outer_7:
                    |    while outer_predicate_8():
                    |        with outer_7.continuing:
                    |            while inner_predicate_9():
                    |                if skip_predicate_10():
                    |                    outer_7.continue_()
                    |                if early_predicate_11():
                    |                    outer_7.break_()
                    |return 42
                """.trimMargin(),
            "exprStatementHse" to
                """
                    |failed_7 = 'dummy' is NO_RESULT0
                """.trimMargin(),
            "expressionAssociativityLeftAmp" to "a and b and c",
            "expressionAssociativityRightAmp" to "a and (b and c)",
            "expressionAssociativityLeftPlus" to "a + b + c",
            "expressionAssociativityRightPlus" to "a + (b + c)",
            "unexportedClass" to
                """
                    |class Thing_7:
                    |    prop_name_9: 'str0'
                    |    __slots__ = ('prop_name_9',)
                    |    def __init__(blah_12: 'str0') -> None:
                    |        pass
                    |    def fun_name(required_arg_17: 'str0', optional_arg_15: 'Union1[int2, None]' = None) -> 'int2':
                    |        _optional_arg_15: 'Union1[int2, None]' = optional_arg_15
                    |        return_16: 'int2'
                    |        if True:
                    |            _optional_arg_15 = 1
                    |        return_16 = _optional_arg_15
                    |        return return_16
                    |    def private_method_10() -> 'None':
                    |        raise NotImplementedError3
                """.trimMargin(),
            "exportedFun" to
                """
                    |def fun_name(required_arg_9: 'str0', optional_arg_7: 'Union1[int2, None]' = None) -> 'int2':
                    |    _optional_arg_7: 'Union1[int2, None]' = optional_arg_7
                    |    return_8: 'int2'
                    |    if True:
                    |        _optional_arg_7 = 1
                    |    return_8 = _optional_arg_7
                    |    return return_8
                """.trimMargin(),
            "funLambdaArgs" to
                """
                    |X_7 = TypeVar0('X_7')
                    |def function_8(alpha_9: 'str1', beta_10: 'int2', gamma_11: 'Callable3[[str1, str1], X_7]') -> 'str1':
                    |    pass
                """.trimMargin(),
            "simpleGenerator" to
                """
                    |@adapt_generator_factory0
                    |def simple_generator_7(do_await_1) -> 'Generator2[empty, None, None]':
                    |    yield
                    |    return ()
                """.trimMargin(),
            "trailingRequiredArgs" to
                """
                    |def function_7(alpha_8: 'int0', beta_9: 'Union1[int0, None]' = None, gamma_10: Optional2['int0'] = None) -> 'int0':
                    |    _beta_9: 'Union1[int0, None]' = beta_9
                    |    _gamma_10: Optional2['int0'] = gamma_10
                    |    return_11: 'int0'
                    |    if True:
                    |        _beta_9 = 77
                    |    return_11 = alpha_8 + _beta_9 + _gamma_10
                    |    return return_11
                """.trimMargin(),
        )
    }
}
