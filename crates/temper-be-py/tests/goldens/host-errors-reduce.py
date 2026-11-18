from collections.abc import Sequence as _Sequence
from temper_core import (
    Bubble as _Bubble,
    LoggingConsole as _LoggingConsole,
    int_add as _int_add,
    listed_reduce as _listed_reduce,
)

__all__ = ()


def _at(i: int, /) -> int:
    if i == 99:
        raise _Bubble()
    return i


def _sum(ls: _Sequence[int], i: int, /) -> int:
    _at(i)
    def fn(a: int, b: int, /) -> int:
        return _int_add(a, b)
    return _listed_reduce(ls, fn)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('reading')
_r: int
try:
    _r = _sum((), 0)
except _Bubble:
    _r = -1
_console.log(f'reduced empty {_r}')
