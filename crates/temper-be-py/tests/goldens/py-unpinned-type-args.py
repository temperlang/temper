from collections import deque as _deque
from collections.abc import Sequence as _Sequence
from temper_core import (
    LoggingConsole as _LoggingConsole,
    deque_remove_first as _deque_remove_first,
)
from typing import Generic as _Generic, TypeVar as _TypeVar, cast as _cast

__all__ = ()

_T = _TypeVar('_T')
_U = _TypeVar('_U')
_V = _TypeVar('_V')
_W = _TypeVar('_W')


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


def _mk(first: _T, /) -> _deque[_T]:
    q: _deque[_T] = _deque()
    q.append(first)
    return q


class _Maker(_Generic[_T]):
    __slots__ = ()

    def make(self, /) -> _Holder[_T]:
        return _cast('_Holder[_T]', _Holder())

    def describe(self, /) -> str:
        return _kind(_cast('_Holder[_T]', _Holder()))


class _Later:
    __slots__ = ()


def _early() -> str:
    return _kind(_cast('_Holder[_Later]', _Holder()))


def _empty() -> _deque[_T]:  # pyright: ignore[reportInvalidTypeVarUse]
    return _cast('_deque[_T]', _deque())


class _Factory:
    __slots__ = ()
    @staticmethod
    def fresh() -> _Holder[_U]:  # pyright: ignore[reportInvalidTypeVarUse]
        return _cast('_Holder[_U]', _Holder())
    def made(self, /) -> _Sequence[_V]:  # pyright: ignore[reportInvalidTypeVarUse]
        return ()


def _relayed() -> _deque[_W]:  # pyright: ignore[reportInvalidTypeVarUse]
    return _cast('_deque[_W]', _empty())


_console: _LoggingConsole = _LoggingConsole(__name__)
_filled: _deque[int] = _cast('_deque[int]', _empty())
_filled.append(2)
_console.log(_kind(_cast('_deque[int]', _empty())))
_console.log(_kind(_cast('_Holder[str]', _Factory.fresh())))
_console.log(str(len(_cast('_Sequence[int]', _Factory().made()))))
_console.log(_kind(_cast('_deque[bool]', _relayed())))
_console.log(str(_deque_remove_first(_filled)))
_console.log(_kind(_cast('_Holder[int]', _Holder())))
_console.log(_kind(_cast('_deque[str]', _deque())))
_console.log(_kind({}))
_console.log(_kind(_same(_cast('_deque[int]', _deque()))))
_console.log(str(len(_same(_cast('_Holder[str]', _Holder())).items)))
_console.log(_kind(_mk(1)))
_console.log(_kind(_cast('_Maker[str]', _Maker()).make()))
_console.log(_cast('_Maker[int]', _Maker()).describe())
_console.log(_early())
