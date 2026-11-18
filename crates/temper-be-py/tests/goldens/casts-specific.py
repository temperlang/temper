from abc import ABCMeta as _ABCMeta
from collections.abc import Sequence as _Sequence
from temper_core import (
    Bubble as _Bubble,
    LoggingConsole as _LoggingConsole,
    cast_by_type as _cast_by_type,
    list_get as _list_get,
)

__all__ = ()


class _AlphaInterface(metaclass=_ABCMeta):
    __slots__ = ()


class _BravoClass(_AlphaInterface):
    __slots__ = ()


class _CharlieClass(_AlphaInterface):
    __slots__ = ()


def _check_b(instance: _AlphaInterface, /) -> None:
    t_0: str
    try:
        _cast_by_type(instance, _BravoClass)
        t_0 = 'yep'
    except _Bubble:
        t_0 = 'nope'
    _console.log(t_0)


def _check_if_b(instance: _AlphaInterface, /) -> None:
    _console.log('yep' if isinstance(instance, _BravoClass) else 'nope')


_console: _LoggingConsole = _LoggingConsole(__name__)
_check_b(_BravoClass())
_check_b(_CharlieClass())
_items: _Sequence[_AlphaInterface] = (_BravoClass(), _CharlieClass())
_check_b(_list_get(_items, 0))
_check_b(_list_get(_items, 1))
_check_if_b(_list_get(_items, 0))
_check_if_b(_list_get(_items, 1))
