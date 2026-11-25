from abc import ABCMeta as _ABCMeta
from collections.abc import Sequence as _Sequence
from temper_core import (
    LoggingConsole as _LoggingConsole,
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
    _console.log('yep' if isinstance(instance, _BravoClass) else 'nope')


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
