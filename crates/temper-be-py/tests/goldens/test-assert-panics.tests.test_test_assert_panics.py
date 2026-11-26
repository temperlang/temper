from abc import ABCMeta
from collections.abc import Sequence
from temper_core import (
    Failure,
    arith_int_mod,
    cast_by_type,
    end_test,
    int_add,
    int_div,
    list_get,
)
from temper_std.testing import Test
from unittest import TestCase


class _AtError(Failure, Exception):
    __slots__ = ()


class _Unlucky(_AtError):
    __slots__ = ()

    @property
    def message(self, /) -> str:
        return 'unlucky'


def _at(ls: Sequence[int], i: int, /) -> int:
    if i == 99:
        raise _Unlucky()
    return list_get(ls, i)


class _Shape(metaclass=ABCMeta):
    __slots__ = ()


class _Square(_Shape):
    __slots__ = ()


class _Circle(_Shape):
    __slots__ = ()


def _divide(a: int, b: int, /) -> int:
    return int_div(a, b)


def _remainder(a: int, b: int, /) -> int:
    return arith_int_mod(a, b)


def _squared(s: _Shape, /) -> _Square:
    return cast_by_type(s, _Square)


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

    def test_passes_when_division_by_zero_or_a_cast_panics(self, /) -> None:
        'passes when division by zero or a cast panics'
        test: Test = Test()
        with end_test(test):
            def fn() -> None:
                _divide(1, 0)
            test.assert_panics(fn)
            def fn2() -> None:
                _remainder(1, 0)
            test.assert_panics(fn2)
            def fn3() -> None:
                _squared(_Circle())
            test.assert_panics(fn3)
            _squared(_Square())
            actual = _divide(-7, 2)
            expected = -3
            test.assert_(actual == expected, lambda: f'expected divide(-7, 2) == ({expected}) not ({actual})')

    def test_passes_past_an_orelse(self, /) -> None:
        'passes past an orelse'
        test: Test = Test()
        with end_test(test):
            def fn() -> None:
                try:
                    n = _at((1, 2), 5)
                except _Unlucky:
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

    def test_fails_when_the_block_throws(self, /) -> None:
        'fails when the block throws'
        test: Test = Test()
        with end_test(test):
            def fn() -> None:
                _at((1, 2), 99)
            test.assert_panics(fn)

    def test_fails_with_its_message_when_the_block_throws(self, /) -> None:
        'fails with its message when the block throws'
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

    def test_fails_when_its_body_throws(self, /) -> None:
        'fails when its body throws'
        test: Test = Test()
        with end_test(test):
            _at((1, 2), 99)

    def test_passes_after(self, /) -> None:
        'passes after'
        test: Test = Test()
        with end_test(test):
            actual = _at((1, 2), 0)
            expected = 1
            test.assert_(actual == expected, lambda: f'expected at([1, 2], 0) == ({expected}) not ({actual})')
