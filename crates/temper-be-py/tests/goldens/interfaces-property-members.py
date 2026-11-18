from abc import ABCMeta as _ABCMeta, abstractmethod as _abstractmethod
from collections.abc import Sequence as _Sequence
from temper_core import (
    LoggingConsole as _LoggingConsole,
    Pair as _Pair,
    boolean_to_string as _boolean_to_string,
    int_add as _int_add,
    list_get as _list_get,
)
from typing import Generic as _Generic, TypeVar as _TypeVar

__all__ = ('I', 'J', 'SI')

_IT = _TypeVar('_IT', bound='I')
_T = _TypeVar('_T', bound='I')
_T_2 = _TypeVar('_T_2')
_L = _TypeVar('_L')


class I(metaclass=_ABCMeta):
    __slots__ = ()

    @property
    @_abstractmethod
    def x(self, /) -> str:
        ...


class J(I):
    __slots__ = ()


def _least_x(a: _IT, b: _IT, /) -> _IT:
    if a.x < b.x:
        return a
    else:
        return b


class _C(J):
    __slots__ = ('_x',)
    _x: str

    def __init__(self, /, x: str) -> None:
        self._x = x

    @property
    def x(self, /) -> str:
        return self._x

    @x.setter
    def x(self, value: str, /) -> None:
        self._x = value


class _D(_Generic[_T]):
    __slots__ = ('_thing',)
    _thing: _T

    def __init__(self, /, thing: _T) -> None:
        self._thing = thing

    @property
    def thing(self, /) -> _T:
        return self._thing


class SI(_Generic[_T], metaclass=_ABCMeta):
    __slots__ = ()

    @property
    @_abstractmethod
    def thing(self, /) -> _T:
        ...


class _SSub(SI[_T], _Generic[_T]):
    __slots__ = ('_thing',)
    _thing: _T

    def __init__(self, /, thing: _T) -> None:
        self._thing = thing

    @property
    def thing(self, /) -> _T:
        return self._thing


class _Holder(_Generic[_T_2], metaclass=_ABCMeta):
    __slots__ = ()

    @property
    @_abstractmethod
    def held(self, /) -> _T_2:
        ...

    def held_and(self, other: _T_2, /) -> _Sequence[_T_2]:
        return (self.held, other)


class _Labeled(_Holder[_T_2], _Generic[_L, _T_2]):
    __slots__ = ()

    @_abstractmethod
    def label(self, l: _L, /) -> str:
        ...


class _LabeledSub(_Labeled[str, _C]):
    __slots__ = ('_held',)
    _held: _C

    def __init__(self, /, held: _C) -> None:
        self._held = held

    @property
    def held(self, /) -> _C:
        return self._held

    def label(self, l: str, /) -> str:
        return f'{l}: {self._held.x}'


class _High(metaclass=_ABCMeta):
    __slots__ = ()

    @_abstractmethod
    def to_string(self, /) -> str:
        ...

    @_abstractmethod
    def eq(self, other: '_High', /) -> bool:
        ...


class _Low1(_High):
    __slots__ = ('_i',)
    _i: int

    def __init__(self, /, i: int) -> None:
        self._i = i

    @property
    def i(self, /) -> int:
        return self._i

    def to_string(self, /) -> str:
        return f'Low1({self._i})'

    def __str__(self, /) -> str:
        return self.to_string()

    def eq(self, other: _High, /) -> bool:
        if isinstance(other, _Low1):
            return self._i == other._i
        else:
            return False

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, _High) and self.eq(other)


class _Mid(_High):
    __slots__ = ()


class _Low2(_Mid):
    __slots__ = ('_s',)
    _s: str

    def __init__(self, /, s: str) -> None:
        self._s = s

    @property
    def s(self, /) -> str:
        return self._s

    def to_string(self, /) -> str:
        return f'Low2({self._s})'

    def __str__(self, /) -> str:
        return self.to_string()

    def eq(self, other: _High, /) -> bool:
        if isinstance(other, _Low2):
            return self._s == other._s
        else:
            return False

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, _High) and self.eq(other)


class _Low3(_Mid):
    __slots__ = ()

    def to_string(self, /) -> str:
        return 'Low3'

    def __str__(self, /) -> str:
        return self.to_string()

    def eq(self, other: _High, /) -> bool:
        return isinstance(other, _Low3)

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, _High) and self.eq(other)


_console: _LoggingConsole = _LoggingConsole(__name__)
_a: _C = _C('foo')
_b: _C = _C('bar')
_console.log(f'leastX of {_a.x} and {_b.x} is {_least_x(_a, _b).x}.')
_i: I = _a
_console.log(f'As an I, a.x is {_i.x}.')
_di: _D[I] = _D(_a)
_dj: _D[J] = _D(_a)
_console.log(f'Generically, x is {_di.thing.x}.')
_console.log(f'Genericallier, x is still {_dj.thing.x}.')
_si: SI[_C] = _SSub(_a)
_console.log(f'Sealed thing is {_si.thing.x}.')
_labeled: _Labeled[str, _C] = _LabeledSub(_a)
_held_and: _Sequence[_C] = _labeled.held_and(_b)
_console.log(f"Labeled thing is {_labeled.label('first')}.")
_console.log(f'Held are {_labeled.held.x} and {_list_get(_held_and, 1).x}.')
_high1: _High = _Low1(11)
_high1_b: _High = _Low1(12)
_high1_c: _High = _Low1(12)
_high2: _High = _Low2('two')
_high2_b: _High = _Low2('to be')
_high2_c: _High = _Low2('to be')
_high3: _High = _Low3()
_high3_b: _High = _Low3()
_pairs: _Sequence[_Pair[_High, _High]] = (_Pair(_high1, _high1_b), _Pair(_high1_b, _high1_c), _Pair(_high1, _high2), _Pair(_high2, _high2_b), _Pair(_high2_b, _high2_c), _Pair(_high1, _high3), _Pair(_high3, _high3_b))
_length = len(_pairs)
_i2 = 0
while _i2 < _length:
    _pair = _list_get(_pairs, _i2)
    _i2 = _int_add(_i2, 1)
    _console.log(f'{_pair.key.to_string()} == {_pair.value.to_string()}: {_boolean_to_string(_pair.key.eq(_pair.value))}')
