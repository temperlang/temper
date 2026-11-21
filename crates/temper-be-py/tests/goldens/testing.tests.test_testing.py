from temper_core import finish_async_work, int_add
from temper_std.testing import Test
from testing.testing import twice
from unittest import TestCase

_runs: int = 0
_base: int = 10


class TestTesting(TestCase):
    def test_twice_works(self, /) -> None:
        'twice works'
        global _runs
        test: Test = Test()
        try:
            x = twice(2)
            _runs = int_add(int_add(_runs, x), _base)
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_names_punctuation_quotes_and_ünïcode(self, /) -> None:
        "names: punctuation, 'quotes', and ünïcode"
        t: Test = Test()
        try:
            twice(_runs)
        finally:
            finish_async_work()
            t.soft_fail_to_hard()

    def test_locals_a_test_assigns(self, /) -> None:
        'locals a test assigns'
        global _runs
        test: Test = Test()
        try:
            n = 0
            i = 0
            while i < 3:
                n = int_add(n, twice(i))
                i = int_add(i, 1)
            _runs = n
        finally:
            finish_async_work()
            test.soft_fail_to_hard()
