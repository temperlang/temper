from collections.abc import Sequence as _Sequence
from temper_core import (
    Bubble as _Bubble,
    LoggingConsole as _LoggingConsole,
    list_get as _list_get,
)

__all__ = ()


def _at(i: int, /) -> int:
    if i == 99:
        raise _Bubble()
    return i


def _read(ls: _Sequence[int], i: int, /) -> int:
    return _list_get(ls, _at(i))


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('reading')
_r: int
try:
    _r = _read((1, 2), 5)
except _Bubble:
    _r = -1
_console.log(f'read past the end {_r}')
