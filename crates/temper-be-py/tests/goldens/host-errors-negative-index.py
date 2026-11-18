from collections.abc import (
    MutableSequence as _MutableSequence,
    Sequence as _Sequence,
)
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


def _built() -> _MutableSequence[int]:
    lb: _MutableSequence[int] = []
    lb.append(1)
    return lb


def _read(ls: _Sequence[int], i: int, /) -> int:
    return _list_get(ls, _at(i))


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('reading')
_r: int
try:
    _r = _read(_built(), -1)
except _Bubble:
    _r = -1
_console.log(f'read before the start {_r}')
