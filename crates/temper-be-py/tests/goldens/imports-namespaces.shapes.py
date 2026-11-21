from abc import ABCMeta as _ABCMeta, abstractmethod as _abstractmethod
from temper_core import int_mul as _int_mul
from typing import Generic as _Generic, TypeVar as _TypeVar

__all__ = ('unit', 'twice', 'Box', 'Shape', 'Square')

_T = _TypeVar('_T')


def twice(x: int, /) -> int:
    return _int_mul(x, 2)


class Box(_Generic[_T]):
    __slots__ = ('_value',)
    _value: _T

    def __init__(self, /, value: _T) -> None:
        self._value = value

    @property
    def value(self, /) -> _T:
        return self._value


class Shape(metaclass=_ABCMeta):
    __slots__ = ()

    @_abstractmethod
    def area(self, /) -> int:
        ...


class Square(Shape):
    __slots__ = ('_side',)
    _side: int

    def __init__(self, /, side: int) -> None:
        self._side = side

    @property
    def side(self, /) -> int:
        return self._side

    def area(self, /) -> int:
        return _int_mul(self._side, self._side)

    @staticmethod
    def sized(side: int, /) -> 'Square':
        return Square(side)


unit: int = 1
