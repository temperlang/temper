from host_error_tests.host_error_tests import host_fails, host_fails_test
from temper_core import Bubble, end_test
from temper_std.testing import Test
from unittest import TestCase


class TestHostErrorTests(TestCase):
    def test_host_errors_are_panics(self, /) -> None:
        'host errors are panics'
        test: Test = Test()
        with end_test(test):
            def fn() -> None:
                try:
                    r = host_fails(1)
                except Bubble:
                    r = -1
                test.assert_(r != -1, lambda: 'expected r != -1')
            test.assert_panics(fn, 'host error 1')
            def fn3() -> None:
                try:
                    r = host_fails(2)
                except Bubble:
                    r = -1
                test.assert_(r != -1, lambda: 'expected r != -1')
            test.assert_panics(fn3, 'host error 2')
            def fn5() -> None:
                try:
                    r = host_fails(3)
                except Bubble:
                    r = -1
                test.assert_(r != -1, lambda: 'expected r != -1')
            test.assert_panics(fn5, 'host error 3')

    def test_host_test_failure_is_no_panic(self, /) -> None:
        'host test failure is no panic'
        test: Test = Test()
        with end_test(test):
            def fn() -> None:
                host_fails_test()
            test.assert_panics(fn)
