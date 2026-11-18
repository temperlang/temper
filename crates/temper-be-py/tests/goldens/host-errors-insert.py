from collections.abc import MutableSequence as _MutableSequence
from temper_core import (
    Bubble as _Bubble,
    LoggingConsole as _LoggingConsole,
    list_builder_add as _list_builder_add,
)

__all__ = ()


def _at(i: int, /) -> int:
    if i == 99:
        raise _Bubble()
    return i


def _built() -> _MutableSequence[int]:
    lb: _MutableSequence[int] = []
    lb.append(1)
    return lb


def _insert(lb: _MutableSequence[int], i: int, /) -> int:
    _list_builder_add(lb, 2, _at(i))
    return len(lb)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('reading')
_r: int
try:
    _r = _insert(_built(), 5)
except _Bubble:
    _r = -1
_console.log(f'added past the end {_r}')
