from collections.abc import Sequence as _Sequence
from namespaces.shapes import (
    Box as _Box,
    Shape as _Shape,
    Square as _Square,
    twice as _twice,
    unit as _unit,
)
from temper_core import (
    LoggingConsole as _LoggingConsole,
    int_add as _int_add,
    int_mul as _int_mul,
    list_get as _list_get,
)

__all__ = ()


class _Circle(_Shape):
    __slots__ = ('_r',)
    _r: int

    def __init__(self, /, r: int) -> None:
        self._r = r

    @property
    def r(self, /) -> int:
        return self._r

    def area(self, /) -> int:
        return _int_mul(_int_mul(3, self._r), self._r)


def _bump(shapes: int, /) -> int:
    return _int_add(shapes, 1)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log(str(_unit))
_console.log(str(_twice(21)))
_box: _Box[int] = _Box(3)
_squares: _Sequence[_Shape] = (_Square(4), _Square(_unit), _Square.sized(5))
_length = len(_squares)
_i = 0
while _i < _length:
    _s = _list_get(_squares, _i)
    _i = _int_add(_i, 1)
    _console.log(f'area {_s.area()}')
_mixed: _Sequence[_Shape] = (_Circle(2), _Square(2))
_length2 = len(_mixed)
_i2 = 0
while _i2 < _length2:
    _s2 = _list_get(_mixed, _i2)
    _i2 = _int_add(_i2, 1)
    if isinstance(_s2, _Square):
        _console.log(f'a square of {_s2.area()}')
    else:
        _console.log(f'something else of {_s2.area()}')
_console.log(str(_bump(_box.value)))
