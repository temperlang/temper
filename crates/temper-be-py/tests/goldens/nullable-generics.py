from collections.abc import Sequence as _Sequence
from temper_core import (
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    list_get as _list_get,
    list_slice as _list_slice,
)
from typing import Generic as _Generic, TypeVar as _TypeVar, cast as _cast

__all__ = ('first_or_null', 'Slot', 'Named', 'with_default')

_T = _TypeVar('_T')


def first_or_null(ls: _Sequence[_T], /) -> _T | None:
    if not ls:
        return None
    else:
        return _list_get(ls, 0)


class Slot(_Generic[_T]):
    __slots__ = ('_value',)
    _value: _T

    def __init__(self, /, value: _T) -> None:
        self._value = value

    @property
    def value(self, /) -> _T:
        return self._value

    def kept(self, keep: bool, /) -> _T | None:
        if keep:
            return self._value
        else:
            return None


class Named:
    __slots__ = ('_name',)
    _name: str

    def __init__(self, /, name: str) -> None:
        self._name = name

    @property
    def name(self, /) -> str:
        return self._name


def with_default(x: _T, y: _T | None = None, /) -> _T:
    if y is None:
        y = x
    return y


_console: _LoggingConsole = _LoggingConsole(__name__)
_strings: _Sequence[str | None] = (None, 'b')
_no_strings: _Sequence[str | None] = ()
_ints: _Sequence[int | None] = (None, 2)
_a: str | None = first_or_null(_strings)
_b: str | None = first_or_null(_no_strings)
_c: int | None = first_or_null(_ints)
_console.log(f'{_boolean_to_string(_a is None)} {_boolean_to_string(_b is None)} {_boolean_to_string(_c is None)}')
_console.log('none' if (_subject := first_or_null(('x',))) is None else _subject)
_console.log(f'{-1 if (_subject2 := first_or_null(_list_slice(_ints, 1, 2))) is None else _subject2} {-1 if (_subject3 := first_or_null((3,))) is None else _subject3}')
_slot: Slot[str | None] = Slot(None)
_console.log(f'{_boolean_to_string(_slot.kept(True) is None)} {_boolean_to_string(_slot.kept(False) is None)}')
_console.log('none' if (_subject4 := Slot(_cast('str | None', 's')).kept(True)) is None else _subject4)
_nameds: _Sequence[Named | None] = (None,)
_no_nameds: _Sequence[Named] = ()
_n: Named | None = first_or_null((Named('n'),))
_console.log(f"{_boolean_to_string(first_or_null(_nameds) is None)} {_boolean_to_string(first_or_null(_no_nameds) is None)} {'none' if (_subject5 := None if _n is None else _n.name) is None else _subject5}")
_console.log('none' if (_subject6 := None if (_subject7 := first_or_null(_no_nameds)) is None else _subject7.name) is None else _subject6)
_none: str | None = None
_console.log('null' if (_subject8 := with_default(_cast('str | None', 'a'), _none)) is None else _subject8)
_console.log('null' if (_subject9 := with_default(_cast('str | None', 'a'), 'b')) is None else _subject9)
_console.log('null' if (_subject10 := with_default(_cast('str | None', 'a'))) is None else _subject10)
_console.log(str(-1 if (_subject11 := with_default(_cast('int | None', 1), None)) is None else _subject11))
_console.log(f"{with_default('c')} {with_default(4, 5)}")
