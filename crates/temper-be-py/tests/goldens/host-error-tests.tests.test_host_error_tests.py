from host_error_tests.host_error_tests import host_fails
from temper_core import Bubble, finish_async_work
from temper_std.testing import Test
from unittest import TestCase


class TestHostErrorTests(TestCase):
    def test_host_error_is_no_panic(self, /) -> None:
        'host error is no panic'
        test: Test = Test()
        try:
            def fn() -> None:
                try:
                    r = host_fails(1)
                except Bubble:
                    r = -1
                test.assert_(r != -1, lambda: 'caught')
            test.assert_panics(fn)
        finally:
            finish_async_work()
            test.soft_fail_to_hard()
