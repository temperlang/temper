import builtins as _builtins
from abc import ABCMeta as _ABCMeta, abstractmethod as _abstractmethod
from collections.abc import (
    MutableSequence as _MutableSequence,
    Sequence as _Sequence,
)
from temper_core import (
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    int_add as _int_add,
    int_mul as _int_mul,
    int_sub as _int_sub,
    list_join as _list_join,
    listed_reduce_from as _listed_reduce_from,
)

__all__ = (
    'Described',
    'Option',
    'total',
    'widest',
    'limited',
    'doubled',
    'shown',
    'is_option',
    'counted',
    'span',
)


class Described(metaclass=_ABCMeta):
    __slots__ = ()

    @_abstractmethod
    def describe(self, /) -> str:
        ...


class Option(Described):
    __slots__ = ('_help', '_min', '_max', '_type_')
    _help: str
    _min: int
    _max: int
    _type_: str

    def __init__(self, /, help: str, min: int, max: int, type: str) -> None:
        self._help = help
        self._min = min
        self._max = max
        self._type_ = type

    @property
    def help(self, /) -> str:
        return self._help

    @property
    def min(self, /) -> int:
        return self._min

    @property
    def max(self, /) -> int:
        return self._max

    @property
    def type_(self, /) -> str:
        return self._type_

    def clamp(self, len: int, /) -> int:
        return min(max(len, self._min), self._max)

    def describe(self, /) -> str:
        return f'{self._type_}: {self._help}'


class _Plain(Described):
    __slots__ = ()

    def describe(self, /) -> str:
        return 'plain'


def total(len: int, items: _Sequence[str], /) -> int:
    return _int_add(_builtins.len(items), len)


def widest(min: int, max: int, sizes: _Sequence[int], /) -> int:
    return _builtins.min(_listed_reduce_from(sizes, min, lambda a, b: _builtins.max(a, b)), max)


def limited(n: int, max: int | None = None, /) -> int:
    if max is None:
        max = 10
    return min(n, max)


def doubled(int: int, /) -> _Sequence[int]:
    twice: _MutableSequence[_builtins.int] = []
    twice.append(_int_mul(int, 2))
    return tuple(twice)


def shown(str: int, /) -> str:
    return _builtins.str(str)


def is_option(isinstance: Described, /) -> bool:
    return _builtins.isinstance(isinstance, Option)


def counted(items: _Sequence[str], /) -> int:
    len = _builtins.len(items)
    return _int_mul(len, 2)


def span(from_: int, lambda_: int, class_: int, /) -> int:
    return _int_add(_int_sub(lambda_, from_), class_)


_console: _LoggingConsole = _LoggingConsole(__name__)
_o: Option = Option('how', 1, 9, 'flag')
_console.log(f'{_o.clamp(12)} {_o.clamp(0)} {_o.describe()}')
_console.log(str(total(2, ('a', 'b', 'c'))))
_console.log(str(widest(0, 10, (3, 12, 5))))
_console.log(f'{limited(12)} {limited(12, 4)}')
_console.log(_list_join(doubled(21), ',', lambda i: str(i)))
_console.log(shown(7))
_console.log(f'{_boolean_to_string(is_option(_o))} {_boolean_to_string(is_option(_Plain()))}')
_console.log(str(counted(('a', 'b'))))
_console.log(str(span(3, 10, 0)))
