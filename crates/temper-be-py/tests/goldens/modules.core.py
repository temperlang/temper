from temper_core import int_div as _int_div, int_mul as _int_mul

__all__ = ('share', 'Ratio', 'ratio_of')


def share(n: int, d: int, /) -> int:
    try:
        return _int_div(n, d)
    except Exception:
        return 0


class Ratio:
    __slots__ = ('_n', '_d')
    _n: int
    _d: int

    def __init__(self, /, n: int, d: int) -> None:
        self._n = n
        self._d = d

    @property
    def n(self, /) -> int:
        return self._n

    @property
    def d(self, /) -> int:
        return self._d

    def eq(self, other: 'Ratio', /) -> bool:
        return _int_mul(self._n, other._d) == _int_mul(other._n, self._d)

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, Ratio) and self.eq(other)


def ratio_of(n: int, d: int, /) -> Ratio | None:
    if d == 0:
        return None
    else:
        return Ratio(n, d)
