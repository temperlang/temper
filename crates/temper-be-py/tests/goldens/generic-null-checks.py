from temper_core import (
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
)
from typing import TypeVar as _TypeVar

__all__ = ('Named', 'is_null', 'or_else')

_T = _TypeVar('_T')


class Named:
    __slots__ = ('_name',)
    _name: str

    def __init__(self, /, name: str) -> None:
        self._name = name

    @property
    def name(self, /) -> str:
        return self._name


def is_null(x: object, /) -> bool:
    return x is None


def or_else(x: _T, y: _T, /) -> _T:
    if x is None:
        return y
    else:
        return x


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log(f"{_boolean_to_string(is_null(None))} {_boolean_to_string(is_null('a'))} {_boolean_to_string(is_null('a'))}")
_console.log(f'{_boolean_to_string(is_null(None))} {_boolean_to_string(is_null(1))} {_boolean_to_string(is_null(1))}')
_console.log(f"{_boolean_to_string(is_null(None))} {_boolean_to_string(is_null(Named('n')))}")
_console.log('null' if (_subject := or_else(None, 'y')) is None else _subject)
_console.log(f"{or_else('x', 'y')} {-1 if (_subject2 := or_else(None, 2)) is None else _subject2}")
