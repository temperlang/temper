from collections.abc import Sequence as _Sequence
from temper_core import (
    Bubble as _Bubble,
    LoggingConsole as _LoggingConsole,
    int_add as _int_add,
    list_get as _list_get,
)

__all__ = ()


def _positive(n: int, /) -> int:
    if n <= 0:
        raise _Bubble()
    return n


def _read(ls: _Sequence[int], i: int, /) -> int:
    _positive(_int_add(i, 1))
    return _list_get(ls, i)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('before')
_r: int
try:
    _r = _read((1, 2), 5)
except _Bubble as _t_0:
    raise BaseException('panic') from _t_0
_console.log(f'after {_r}')
