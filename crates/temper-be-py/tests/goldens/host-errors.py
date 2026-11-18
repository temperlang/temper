from collections.abc import Sequence as _Sequence
from temper_core import (
    Bubble as _Bubble,
    LoggingConsole as _LoggingConsole,
    list_get as _list_get,
)

__all__ = ()


def _read(ls: _Sequence[int], i: int, /) -> int:
    if i < 0:
        raise _Bubble()
    return _list_get(ls, i)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('reading')
_r: int
try:
    _r = _read((1, 2), 5)
except _Bubble:
    _r = -1
_console.log(f'read past the end {_r}')
raise BaseException('panic')
