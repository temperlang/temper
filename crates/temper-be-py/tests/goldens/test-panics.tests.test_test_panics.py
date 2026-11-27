from collections.abc import Sequence
from temper_core import Failure, end_test, int_add, list_get
from temper_std.testing import Test
from unittest import TestCase


class _Problem(Failure, Exception):
    __slots__ = ()


class _Oops(_Problem):
    __slots__ = ()

    @property
    def message(self, /) -> str:
        return 'oops'


def _at(ls: Sequence[int], i: int, /) -> int:
    if i == 99:
        raise _Oops()
    return list_get(ls, i)


class TestTestPanics(TestCase):
    def test_passes_before(self, /) -> None:
        'passes before'
        test: Test = Test()
        with end_test(test):
            actual = int_add(1, 1)
            expected = 2
            test.assert_(actual == expected, lambda: f'expected 1 + 1 == ({expected}) not ({actual})')

    def test_panics_reading_past_the_end(self, /) -> None:
        'panics reading past the end'
        test: Test = Test()
        with end_test(test):
            try:
                n = _at((1, 2), 5)
            except _Oops:
                n = -1
            actual = n
            expected = -1
            test.assert_(actual == expected, lambda: f'expected n == ({expected}) not ({actual})')

    def test_dies_outright(self, /) -> None:
        'dies outright'
        test: Test = Test()
        with end_test(test):
            raise BaseException('panic')

    def test_fails_an_assertion(self, /) -> None:
        'fails an assertion'
        test: Test = Test()
        with end_test(test):
            test.assert_(int_add(1, 1) == 3, lambda: 'wrong on purpose')

    def test_passes_after(self, /) -> None:
        'passes after'
        test: Test = Test()
        with end_test(test):
            actual = _at((1, 2), 1)
            expected = 2
            test.assert_(actual == expected, lambda: f'expected at([1, 2], 1) == ({expected}) not ({actual})')

    def test_dies_after_failing(self, /) -> None:
        'dies after failing'
        test: Test = Test()
        with end_test(test):
            test.assert_(int_add(1, 1) == 3, lambda: 'failed before')
            raise BaseException('panic')
