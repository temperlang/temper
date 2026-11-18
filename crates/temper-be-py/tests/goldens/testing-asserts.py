from collections.abc import Sequence as _Sequence
from temper_core import float64_to_string as _float64_to_string

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


class _KeptForHost:  # pyright: ignore[reportUnusedClass]
    __slots__ = ('_n',)
    _n: int

    def __init__(self, /, n: int) -> None:
        self._n = n

    @property
    def n(self, /) -> int:
        return self._n


_unit_vecs: _Sequence[Vec2] = (Vec2(1.0, 0.0), Vec2(0.0, 1.0))
