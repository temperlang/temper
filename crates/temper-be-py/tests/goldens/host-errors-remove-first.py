from collections import deque as _deque
from temper_core import (
    Bubble as _Bubble,
    LoggingConsole as _LoggingConsole,
    deque_remove_first as _deque_remove_first,
)

__all__ = ()


def _at(i: int, /) -> int:
    if i == 99:
        raise _Bubble()
    return i


def _remove_first(d: _deque[int], i: int, /) -> int:
    _at(i)
    return _deque_remove_first(d)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('reading')
_r: int
try:
    _r = _remove_first(_deque(), 0)
except _Bubble:
    _r = -1
_console.log(f'removed from empty {_r}')
