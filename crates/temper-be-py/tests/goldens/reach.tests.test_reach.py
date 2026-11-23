import reach.reach as _m_reach_reach
from abc import ABCMeta, abstractmethod
from reach.reach import _base, _checked, shown
from temper_core import end_test, int_add, int_mul
from temper_std.testing import Test
from unittest import TestCase


class _TestShape(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def area(self, /) -> int:
        ...


class _TestSquare(_TestShape):
    __slots__ = ('_side',)
    _side: int

    def __init__(self, /, side: int) -> None:
        self._side = side

    @property
    def side(self, /) -> int:
        return self._side

    def area(self, /) -> int:
        return int_mul(self._side, self._side)


def _for_tests(n: int, /) -> int:
    return int_mul(n, _test_factor)


def _add_area(shape: _TestShape, /) -> None:
    global _test_count
    _test_count = int_add(_test_count, shape.area())


_test_factor: int = int_mul(_base, 2)
_test_count: int = 0


class TestReach(TestCase):
    def test_test_parts(self, /) -> None:
        'test parts'
        global _test_count
        test: Test = Test()
        with end_test(test):
            shape = _TestSquare(_for_tests(2))
            _add_area(shape)
            _test_count = int_add(_test_count, shape.area())
            actual = _test_count
            expected = 32
            test.assert_(actual == expected, lambda: f'expected testCount == ({expected}) not ({actual})')
            actual2 = _checked
            expected2 = 3
            test.assert_(actual2 == expected2, lambda: f'expected checked == ({expected2}) not ({actual2})')
            actual3 = _m_reach_reach._shown_count
            expected3 = 2
            test.assert_(actual3 == expected3, lambda: f'expected shownCount == ({expected3}) not ({actual3})')
            _m_reach_reach._shown_count = 0
            shown(0)
            actual4 = _m_reach_reach._shown_count
            expected4 = 1
            test.assert_(actual4 == expected4, lambda: f'expected shownCount == ({expected4}) not ({actual4})')
