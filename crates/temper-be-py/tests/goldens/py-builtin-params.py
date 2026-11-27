import builtins as _builtins
from abc import ABCMeta as _ABCMeta, abstractmethod as _abstractmethod
from collections.abc import (
    MutableSequence as _MutableSequence,
    Sequence as _Sequence,
)
from temper_core import (
    Failure as _Failure,
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    int_add as _int_add,
    int_mul as _int_mul,
    int_sub as _int_sub,
    list_builder_set as _list_builder_set,
    list_get as _list_get,
    list_join as _list_join,
    list_slice as _list_slice,
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
    'joined',
    'repeated',
    'found',
    'last',
    'resized',
    'OptionError',
    'NotAnOption',
    'help_of',
    'pieces',
    'either',
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


def _maybe(s: str, /) -> str | None:
    if not s:
        return None
    else:
        return s


def joined(firsts: _MutableSequence[str], seconds: _Sequence[str], /) -> str:
    out = ['']
    list_: _Sequence[str] = tuple(firsts)
    length = len(list_)
    i = 0
    while i < length:
        s = _list_get(list_, i)
        i = _int_add(i, 1)
        out.append(s)
    list_ = _list_slice(seconds, 0, len(seconds))
    length = len(list_)
    i = 0
    while i < length:
        s = _list_get(list_, i)
        i = _int_add(i, 1)
        out.append(s)
    return ''.join(out)


def repeated(text: str, times: int, /) -> str:
    sb_or_null: list[str] | None = None
    i = 0
    while i < times:
        sb_now: list[str] | None = sb_or_null
        sb: list[str] | None
        sb = [''] if sb_now is None else sb_now
        sb_or_null = sb
        sb.append(text)
        i = _int_add(i, 1)
    sb = sb_or_null
    if sb is not None:
        return ''.join(sb)
    else:
        return '-'


def found(a: str, b: str, /) -> str:
    first: str | None = _maybe(b) if (subject := _maybe(a)) is None else subject
    if first is not None:
        return first
    if a == b:
        return 'same'
    else:
        subject = _maybe(b)
        if subject is None:
            return 'none'
        else:
            return subject


def last(sides: _Sequence[int], /) -> Described:
    at: Described = _Plain()
    length = len(sides)
    i = 0
    while i < length:
        n = _list_get(sides, i)
        i = _int_add(i, 1)
        at = Option('sides', n, n, 'shape')
    return at


def resized(more: bool, /) -> int:
    sizes: _Sequence[int] = (1, 2)
    if more:
        sizes = (3,)
    return len(sizes)


class OptionError(_Failure):
    __slots__ = ()


class NotAnOption(OptionError):
    __slots__ = ()

    @property
    def message(self, /) -> str:
        return 'not an option'


def help_of(d: Described, /) -> str:
    t_0: Option
    if isinstance(d, Option):
        t_0 = d
    else:
        raise NotAnOption()
    option = t_0
    return option.help


def pieces(n: int, /) -> _Sequence[int]:
    pieces: _MutableSequence[int] = list((0, 0))
    _list_builder_set(pieces, 1, n)
    return tuple(pieces)


def either(subject: str, /) -> str:
    _console.log(subject)
    r = 'empty' if (subject2 := _maybe(subject[0 : 0])) is None else subject2
    return r


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
_more: _MutableSequence[str] = []
_more.append('a')
_more.append('b')
_console.log(joined(_more, ('c',)))
_console.log(f"{repeated('ab', 2)} {repeated('ab', 0)}")
_console.log(f"{found('', 'x')} {found('', '')} {found('y', '')}")
_console.log(f'{last(()).describe()} {last((4,)).describe()}')
_console.log(f'{resized(False)} {resized(True)}')
_t_0: str
try:
    _t_0 = help_of(_o)
except NotAnOption:
    _t_0 = '-'
_t_1: str
try:
    _t_1 = help_of(_Plain())
except NotAnOption:
    _t_1 = '-'
_console.log(f'{_t_0} {_t_1}')
_console.log(_list_join(pieces(7), ',', lambda i: str(i)))
_console.log(either('e'))
