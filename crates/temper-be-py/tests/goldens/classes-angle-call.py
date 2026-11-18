from collections.abc import Sequence as _Sequence
from temper_core import (
    LoggingConsole as _LoggingConsole,
    int_sub as _int_sub,
    list_get as _list_get,
)
from typing import TypeVar as _TypeVar

__all__ = ()

_T = _TypeVar('_T')


def _whatever(n: int, /) -> type:
    if n == 0:
        _console.log('0')
    return type(None)


class _Classy:
    __slots__ = ('_index',)
    _index: int

    def __init__(self, /, index: int) -> None:
        self._index = index

    @property
    def index(self, /) -> int:
        return self._index

    @staticmethod
    def first(things: _Sequence[_T], /) -> _T:
        thing = _list_get(things, 0)
        return thing

    def indexed(self, things: _Sequence[_T], /) -> _T:
        thing = _list_get(things, self._index)
        return thing


def _last_of(things: _Sequence[_T], /) -> _T:
    thing = _list_get(things, _int_sub(len(things), 1))
    return thing


class _Picker:
    __slots__ = ()

    def pick(self, things: _Sequence[_T], /) -> _T:
        return _last_of(things)


_console: _LoggingConsole = _LoggingConsole(__name__)
_whatever(1)
_some_things: _Sequence[str] = ('a', 'b')
_first: str = _Classy.first(_some_things)
_second: str = _Classy(1).indexed(_some_things)
_console.log(f'Some things: {_first}, {_second}')
_console.log(f'Picked: {_Picker().pick(_some_things)}')
