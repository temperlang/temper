from collections.abc import MutableSequence as _MutableSequence
from temper_core import (
    Bubble as _Bubble,
    LoggingConsole as _LoggingConsole,
    list_builder_remove_last as _list_builder_remove_last,
)

__all__ = ()


def _at(i: int, /) -> int:
    if i == 99:
        raise _Bubble()
    return i


def _remove_last(lb: _MutableSequence[int], i: int, /) -> int:
    _at(i)
    return _list_builder_remove_last(lb)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('reading')
_r: int
try:
    _r = _remove_last([], 0)
except _Bubble:
    _r = -1
_console.log(f'removed from empty {_r}')
