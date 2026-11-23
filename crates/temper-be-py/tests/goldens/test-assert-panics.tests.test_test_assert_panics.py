from collections.abc import Sequence
from temper_core import Bubble, end_test, int_add, list_get
from temper_std.testing import Test
from unittest import TestCase


def _at(ls: Sequence[int], i: int, /) -> int:
    if i == 99:
        raise Bubble()
    return list_get(ls, i)


class TestTestAssertPanics(TestCase):
    def test_passes_before(self, /) -> None:
        'passes before'
        test: Test = Test()
        with end_test(test):
            actual = _at((1, 2), 1)
            expected = 2
            test.assert_(actual == expected, lambda: f'expected at([1, 2], 1) == ({expected}) not ({actual})')

    def test_passes_when_the_block_panics(self, /) -> None:
        'passes when the block panics'
        test: Test = Test()
        with end_test(test):
            def fn() -> None:
                _at((1, 2), 5)
            test.assert_panics(fn)
            def fn2() -> None:
                raise BaseException('panic')
            test.assert_panics(fn2)

    def test_passes_past_an_orelse(self, /) -> None:
        'passes past an orelse'
        test: Test = Test()
        with end_test(test):
            def fn() -> None:
                try:
                    n = _at((1, 2), 5)
                except Bubble:
                    n = -1
                test.assert_(n == -1, lambda: 'caught by an orelse')
            test.assert_panics(fn)

    def test_fails_when_the_block_returns(self, /) -> None:
        'fails when the block returns'
        test: Test = Test()
        with end_test(test):
            def fn() -> None:
                _at((1, 2), 0)
            test.assert_panics(fn)

    def test_fails_with_its_message(self, /) -> None:
        'fails with its message'
        test: Test = Test()
        with end_test(test):
            def fn() -> None:
                _at((1, 2), 1)
            test.assert_panics(fn, 'reading index 1 panics')

    def test_fails_when_the_block_bubbles(self, /) -> None:
        'fails when the block bubbles'
        test: Test = Test()
        with end_test(test):
            def fn() -> None:
                _at((1, 2), 99)
            test.assert_panics(fn)

    def test_fails_with_its_message_when_the_block_bubbles(self, /) -> None:
        'fails with its message when the block bubbles'
        test: Test = Test()
        with end_test(test):
            def fn() -> None:
                _at((1, 2), 99)
            test.assert_panics(fn, 'reading index 99 panics')

    def test_goes_on_after_failing(self, /) -> None:
        'goes on after failing'
        test: Test = Test()
        with end_test(test):
            def fn() -> None:
                _at((1, 2), 0)
            test.assert_panics(fn)
            test.assert_(False, lambda: 'went on')

    def test_nests(self, /) -> None:
        'nests'
        test: Test = Test()
        with end_test(test):
            def fn() -> None:
                def fn2() -> None:
                    raise BaseException('panic')
                test.assert_panics(fn2)
                def fn3() -> None:
                    _at((1, 2), 0)
                test.assert_panics(fn3, 'inner returns')
                raise BaseException('panic')
            test.assert_panics(fn)

    def test_keeps_what_the_block_changed(self, /) -> None:
        'keeps what the block changed'
        test: Test = Test()
        with end_test(test):
            n = 0
            def fn() -> None:
                nonlocal n
                n = int_add(n, 1)
                raise BaseException('panic')
            test.assert_panics(fn)
            test.assert_(n == 1, lambda: 'lost the change')

    def test_fails_on_a_panic_after_the_block(self, /) -> None:
        'fails on a panic after the block'
        test: Test = Test()
        with end_test(test):
            def fn() -> None:
                raise BaseException('panic')
            test.assert_panics(fn)
            _at((1, 2), 7)

    def test_ends_on_a_fail_in_the_block(self, /) -> None:
        'ends on a fail in the block'
        test: Test = Test()
        with end_test(test):
            def fn() -> None:
                test.fail('failed inside')
            test.assert_panics(fn)
            test.fail('swallowed')

    def test_passes_after(self, /) -> None:
        'passes after'
        test: Test = Test()
        with end_test(test):
            actual = _at((1, 2), 0)
            expected = 1
            test.assert_(actual == expected, lambda: f'expected at([1, 2], 0) == ({expected}) not ({actual})')
