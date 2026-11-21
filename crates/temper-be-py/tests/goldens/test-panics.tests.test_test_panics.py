from collections.abc import Sequence
from temper_core import Bubble, finish_async_work, int_add, list_get
from temper_std.testing import Test
from unittest import TestCase


def _at(ls: Sequence[int], i: int, /) -> int:
    if i == 99:
        raise Bubble()
    return list_get(ls, i)


class TestTestPanics(TestCase):
    def test_passes_before(self, /) -> None:
        'passes before'
        test: Test = Test()
        try:
            actual = int_add(1, 1)
            expected = 2
            test.assert_(actual == expected, lambda: f'expected 1 + 1 == ({expected}) not ({actual})')
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_panics_reading_past_the_end(self, /) -> None:
        'panics reading past the end'
        test: Test = Test()
        try:
            try:
                n = _at((1, 2), 5)
            except Bubble:
                n = -1
            actual = n
            expected = -1
            test.assert_(actual == expected, lambda: f'expected n == ({expected}) not ({actual})')
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_panics_outright(self, /) -> None:
        'panics outright'
        test: Test = Test()
        try:
            raise BaseException('panic')
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_fails_an_assertion(self, /) -> None:
        'fails an assertion'
        test: Test = Test()
        try:
            test.assert_(int_add(1, 1) == 3, lambda: 'wrong on purpose')
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_passes_after(self, /) -> None:
        'passes after'
        test: Test = Test()
        try:
            actual = _at((1, 2), 1)
            expected = 2
            test.assert_(actual == expected, lambda: f'expected at([1, 2], 1) == ({expected}) not ({actual})')
        finally:
            finish_async_work()
            test.soft_fail_to_hard()
