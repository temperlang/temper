from collections.abc import Callable as _Callable, Sequence as _Sequence
from temper_core import (
    float64_to_string as _float64_to_string,
    int_add as _int_add,
    list_get as _list_get,
)
from temper_std.testing import Test as _Test

__all__ = ('Vec2',)


class Vec2:
    __slots__ = ('_x', '_y')
    _x: float
    _y: float

    def __init__(self, /, x: float, y: float) -> None:
        self._x = x
        self._y = y

    @property
    def x(self, /) -> float:
        return self._x

    @property
    def y(self, /) -> float:
        return self._y

    def to_string(self, /) -> str:
        return f'({_float64_to_string(self._x)}, {_float64_to_string(self._y)}) size squared {_float64_to_string(_norm_squared(self))}'

    def __str__(self, /) -> str:
        return self.to_string()


def _norm_squared(vec: Vec2, /) -> float:
    return vec.x * vec.x + vec.y * vec.y


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


def _assert_more_things(t: _Test, helper: _TestHelper, strings: _Sequence[_Sequence[str]], /) -> None:  # pyright: ignore[reportUnusedFunction]
    i = 0
    while i < len(strings):
        j = 0
        while j < len(_list_get(strings, i)):
            actual = helper.i
            expected = helper.j
            t_0 = actual == expected
            def make_fn(actual: int, expected: int, /) -> _Callable[[], str]:
                return lambda: f'expected helper.i == ({expected}) not ({actual})'
            t.assert_(t_0, make_fn(actual, expected))
            s = _list_get(_list_get(strings, i), j)
            actual2 = s
            expected2 = 'hi'
            t_1 = actual2 == expected2
            def make_fn2(actual2: str, expected2: str, /) -> _Callable[[], str]:
                return lambda: f'expected s == ({expected2}) not ({actual2})'
            t.assert_(t_1, make_fn2(actual2, expected2))
            j = _int_add(j, 1)
        i = _int_add(i, 1)


class _KeptForHost:  # pyright: ignore[reportUnusedClass]
    __slots__ = ('_n',)
    _n: int

    def __init__(self, /, n: int) -> None:
        self._n = n

    @property
    def n(self, /) -> int:
        return self._n


def _sum_of(helper: _TestHelper, /) -> int:
    return _int_add(helper.i, helper.j)


def _kept_for_tests(helper: _TestHelper, /) -> int:  # pyright: ignore[reportUnusedFunction]
    return _sum_of(helper)


_name: str = 'case'
_unit_vecs: _Sequence[Vec2] = (Vec2(1.0, 0.0), Vec2(0.0, 1.0))
