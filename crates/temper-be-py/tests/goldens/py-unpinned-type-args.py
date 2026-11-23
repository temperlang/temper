from collections import deque as _deque
from collections.abc import Sequence as _Sequence
from temper_core import LoggingConsole as _LoggingConsole
from typing import Generic as _Generic, TypeVar as _TypeVar

__all__ = ()

_T = _TypeVar('_T')


class _Holder(_Generic[_T]):
    __slots__ = ('_items',)
    _items: _Sequence[_T]

    def __init__(self, /) -> None:
        self._items = ()

    @property
    def items(self, /) -> _Sequence[_T]:
        return self._items

    @items.setter
    def items(self, value: _Sequence[_T], /) -> None:
        self._items = value


class _Marker:
    __slots__ = ()


def _kind(x: object, /) -> str:
    if isinstance(x, _Marker):
        return 'marker'
    else:
        return 'other'


def _same(x: _T, /) -> _T:
    return x


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log(_kind(_Holder[int]()))
_console.log(_kind(_deque[str]()))
_console.log(_kind({}))
_console.log(_kind(_same(_deque[int]())))
_console.log(str(len(_same(_Holder[str]()).items)))
