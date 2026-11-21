from asserts.asserts import Vec2, _norm_squared, _unit_vecs
from collections.abc import Callable, Sequence
from temper_core import (
    finish_async_work,
    float64_to_string,
    float_eq,
    int_add,
    list_get,
)
from temper_std.testing import Test
from unittest import TestCase


class _TestHelper:
    __slots__ = ('_i', '_j')
    _i: int
    _j: int

    def __init__(self, /, i: int, j: int) -> None:
        self._i = i
        self._j = j

    @property
    def i(self, /) -> int:
        return self._i

    @property
    def j(self, /) -> int:
        return self._j


def _assert_more_things(t: Test, helper: _TestHelper, strings: Sequence[Sequence[str]], /) -> None:
    i = 0
    while i < len(strings):
        j = 0
        while j < len(list_get(strings, i)):
            actual = helper.i
            expected = helper.j
            t_0 = actual == expected
            def make_fn(actual: int, expected: int, /) -> Callable[[], str]:
                return lambda: f'expected helper.i == ({expected}) not ({actual})'
            t.assert_(t_0, make_fn(actual, expected))
            s = list_get(list_get(strings, i), j)
            actual2 = s
            expected2 = 'hi'
            t_1 = actual2 == expected2
            def make_fn2(actual2: str, expected2: str, /) -> Callable[[], str]:
                return lambda: f'expected s == ({expected2}) not ({actual2})'
            t.assert_(t_1, make_fn2(actual2, expected2))
            j = int_add(j, 1)
        i = int_add(i, 1)


def _sum_of(helper: _TestHelper, /) -> int:
    return int_add(helper.i, helper.j)


def _kept_for_tests(helper: _TestHelper, /) -> int:  # pyright: ignore[reportUnusedFunction]
    return _sum_of(helper)


class TestAsserts(TestCase):
    def test_a_test_case(self, /) -> None:
        'a test case'
        test: Test = Test()
        try:
            test.assert_(float_eq(_norm_squared(Vec2(0.0, 0.0)), 0.0), lambda: 'Oh noes')
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_a_failing_test(self, /) -> None:
        'a failing test'
        test: Test = Test()
        try:
            test.assert_(False, lambda: "because it's wrong")
            helper = _TestHelper(3, 2)
            _assert_more_things(test, helper, (('bye',),))
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_exported(self, /) -> None:
        'exported'
        test: Test = Test()
        try:
            vec = Vec2(1.5, 2.5)
            actual = vec.to_string()
            expected = '(1.5, 2.5) size squared 8.5'
            test.assert_(actual == expected, lambda: f'expected vec.toString() == ({expected}) not ({actual})')
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_module_values(self, /) -> None:
        'module values'
        test: Test = Test()
        try:
            length = len(_unit_vecs)
            i = 0
            while i < length:
                vec = list_get(_unit_vecs, i)
                i = int_add(i, 1)
                actual = _norm_squared(vec)
                expected = 1.0
                t_0 = float_eq(actual, expected)
                def make_fn(actual: float, expected: float, /) -> Callable[[], str]:
                    return lambda: f'expected normSquared(vec) == ({float64_to_string(expected)}) not ({float64_to_string(actual)})'
                test.assert_(t_0, make_fn(actual, expected))
        finally:
            finish_async_work()
            test.soft_fail_to_hard()
