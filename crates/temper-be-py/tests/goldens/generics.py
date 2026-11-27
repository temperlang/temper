from abc import ABCMeta as _ABCMeta, abstractmethod as _abstractmethod
from collections.abc import (
    MutableSequence as _MutableSequence,
    Sequence as _Sequence,
)
from temper_core import (
    LoggingConsole as _LoggingConsole,
    Pair as _Pair,
    boolean_to_string as _boolean_to_string,
    int_add as _int_add,
    int_mul as _int_mul,
    int_sub as _int_sub,
    list_builder_add as _list_builder_add,
    list_builder_set as _list_builder_set,
    list_get as _list_get,
    list_get_or as _list_get_or,
    list_slice as _list_slice,
    map_builder_set as _map_builder_set,
    map_constructor as _map_constructor,
    mapped_has as _mapped_has,
)
from types import MappingProxyType as _MappingProxyType
from typing import Generic as _Generic, TypeVar as _TypeVar

__all__ = (
    'Holder',
    'Box',
    'Named',
    'Counter',
    'Shelf',
    'Scene',
    'Stage',
    'first',
)

_T = _TypeVar('_T')
_T_2 = _TypeVar('_T_2', bound='Named')
_TName = _TypeVar('_TName', bound='Named')
_S = _TypeVar('_S', bound='Scene')
_A = _TypeVar('_A')
_B = _TypeVar('_B')


class Holder(_Generic[_T], metaclass=_ABCMeta):
    __slots__ = ()

    @property
    @_abstractmethod
    def held(self, /) -> _T:
        ...


class Box(Holder[_T], _Generic[_T]):
    __slots__ = ('_value',)
    _value: _T

    def __init__(self, /, value: _T) -> None:
        self._value = value

    @property
    def value(self, /) -> _T:
        return self._value

    @property
    def held(self, /) -> _T:
        return self._value

    def pair_with(self, other: int, /) -> _Pair[_T, int]:
        return _Pair(self._value, other)


class Named:
    __slots__ = ('_name',)
    _name: str

    def __init__(self, /, name: str) -> None:
        self._name = name

    @property
    def name(self, /) -> str:
        return self._name


class Counter:
    __slots__ = ('_n',)
    _n: int

    def __init__(self, /, n: int) -> None:
        self._n = n

    @property
    def n(self, /) -> int:
        return self._n

    @n.setter
    def n(self, value: int, /) -> None:
        self._n = value


class Shelf(_Generic[_T_2]):
    __slots__ = ('_items',)
    _items: _Sequence[_T_2]

    def __init__(self, /, items: _Sequence[_T_2]) -> None:
        self._items = items

    @property
    def items(self, /) -> _Sequence[_T_2]:
        return self._items

    def first_name(self, /) -> str | None:
        if not self._items:
            return None
        else:
            return _list_get(self._items, 0).name

    def tally(self, extra: int, /) -> int:
        return _int_add(len(self._items), extra)


class Scene(metaclass=_ABCMeta):
    __slots__ = ()


class Stage(Scene):
    __slots__ = ()


def _tagged(x: _T, /) -> _T:
    return x


def _count_of(n: int, /) -> int:
    b: _MutableSequence[object] = []
    return _int_add(len(b), n)


def _relayed(x: _T, /) -> _T:
    return _tagged(x)


def _half_pair(a: _A, /) -> _Pair[_A, _Sequence[_B]]:
    return _Pair(a, ())


def _opt(x: _T | None, /) -> _T | None:
    return x


def _none_in(length: int, /) -> bool:
    return length == 0


def first(items: _Sequence[_T], fallback: _T, /) -> _T:
    if _none_in(len(items)):
        return fallback
    else:
        return _list_get(items, 0)


def _held(h: Holder[_T], /) -> _T:
    return h.held


def _total(ns: _Sequence[int], /) -> int:
    sum = 0
    i = 0
    while i < len(ns):
        sum = _int_add(sum, _list_get(ns, i))
        i = _int_add(i, 1)
    return sum


def _indexed() -> None:
    counts: _MutableSequence[int] = []
    counts.append(1)
    counts.append(10)
    at = 0
    k = at
    _list_builder_set(counts, k, _int_add(_list_get(counts, k), 4))
    _list_builder_set(counts, 1, _int_mul(_list_get(counts, 1), 3))
    t = at
    at = _int_add(t, 1)
    k = t
    t = _list_get(counts, k)
    _list_builder_set(counts, k, _int_add(t, 1))
    was = t
    k = at
    t = _int_add(_list_get(counts, k), 1)
    _list_builder_set(counts, k, t)
    pre = t
    t = _list_get(counts, 0)
    _list_builder_set(counts, 0, _int_sub(t, 1))
    post = t
    t = _int_sub(_list_get(counts, 0), 1)
    _list_builder_set(counts, 0, t)
    down = t
    _console.log(f'{was} {pre} {post} {down} {at}')


def _both() -> None:
    x = 1
    y = 10
    t = x
    x = _int_add(t, 1)
    t2 = y
    y = _int_add(t2, 1)
    locals = _int_add(t, t2)
    p = Counter(100)
    q = Counter(1000)
    t = p.n
    p.n = _int_add(t, 1)
    t2 = q.n
    q.n = _int_add(t2, 1)
    fields = _int_add(t, t2)
    t = p.n
    p.n = _int_add(t, 1)
    t2 = p.n
    p.n = _int_add(t2, 1)
    same = _int_add(t, t2)
    a: _MutableSequence[int] = []
    a.append(3)
    b: _MutableSequence[int] = []
    b.append(30)
    i = 0
    k = i
    t = _list_get(a, k)
    _list_builder_set(a, k, _int_add(t, 1))
    k = i
    t2 = _list_get(b, k)
    _list_builder_set(b, k, _int_add(t2, 1))
    items = _int_add(t, t2)
    _console.log(f'{locals} {x} {y} {fields} {same} {p.n} {q.n} {items} {_list_get(a, 0)} {_list_get(b, 0)}')


def _bump(key: str, /) -> int:
    _map_builder_set(_tally, key, _int_sub(_tally[key], 1))
    t = _tally[key]
    _map_builder_set(_tally, key, _int_add(t, 1))
    before = t
    t = _int_add(_tally[key], 1)
    _map_builder_set(_tally, key, t)
    after = t
    return _int_add(before, after)


def _first_same(a: _Sequence[int], b: _Sequence[int], /) -> bool:
    return _list_get(a, 0) == _list_get(b, 0)


_console: _LoggingConsole = _LoggingConsole(__name__)
_ns: _Sequence[int] = (1, 2, 3)
_none: _Sequence[int] = ()
_console.log(f'{_total(_ns)} {first(_ns, 0)} {first(_none, -1)}')
_console.log(first(('a', 'b'), 'z'))
_box: Box[str] = Box('boxed')
_console.log(_held(_box))
_pair: _Pair[str, int] = _box.pair_with(7)
_console.log(f'{_pair.key} {_pair.value}')
_shelf: Shelf[Named] = Shelf((Named('ann'), Named('bo')))
_console.log('none' if (_subject := _shelf.first_name()) is None else _subject)
_no_names: _Sequence[Named] = ()
_empty: Shelf[Named] = Shelf(_no_names)
_console.log('none' if (_subject2 := _empty.first_name()) is None else _subject2)
_n: int = _int_add(_shelf.tally(1), _shelf.tally(2))
_console.log(f"{_tagged(4)} {_tagged('t')} {_count_of(3)} {_relayed(5)} {_n}")
_half: _Pair[int, _Sequence[object]] = _half_pair(8)
_half_too: _Pair[str, _Sequence[object]] = _half_pair('h')
_console.log(f'{_half.key} {len(_half.value)} {_half_too.key}')
_opt_none: object | None = _opt(None)
_opt_some: int | None = _opt(6)
_console.log(f"{_boolean_to_string(_opt_none is None)} {0 if _opt_some is None else _opt_some} {'-' if (_subject3 := _opt(None)) is None else _subject3}")
_builder: _MutableSequence[str] = []
_builder.append('x')
_builder.append('y')
_list_builder_add(_builder, 'w', 0)
_console.log(f'{len(_builder)} {_list_get(_builder, 0)} {_builder.pop()}')
_built: _Sequence[str] = tuple(_builder)
_console.log(f"{len(_built)} {_list_get_or(_built, 5, '?')} {_list_get(_list_slice(_built, 1, 2), 0)}")
_m: _MappingProxyType[str, int] = _map_constructor((_Pair('a', 1), _Pair('b', 2)))
_t_0: int
try:
    _t_0 = _m['b']
except Exception:
    _t_0 = -1
_t_1: int
try:
    _t_1 = _m['c']
except Exception:
    _t_1 = -1
_console.log(f"{_t_0} {_t_1} {_boolean_to_string(_mapped_has(_m, 'a'))} {len(_m)}")
_mb: dict[str, int | None] = {}
_map_builder_set(_mb, 'q', 5)
_map_builder_set(_mb, 'n', None)
_console.log(f"{-2 if (_subject4 := _mb.get('q', 0)) is None else _subject4} {-2 if (_subject5 := _mb.get('n', 0)) is None else _subject5} {_boolean_to_string(_mapped_has(_mb, 'n'))}")
_t_2: int | None
try:
    _t_2 = _mb.pop('q')
except Exception:
    _t_2 = 0
_console.log('null' if (_subject6 := None if (_subject7 := _t_2) is None else str(_subject7)) is None else _subject6)
_console.log(str(len(list(_mb.keys()))))
_indexed()
_both()
_tally: dict[str, int] = {}
_map_builder_set(_tally, 'k', 2)
_t_3: int
try:
    _t_3 = _bump('k')
except Exception:
    _t_3 = -1
_t_4: int
try:
    _t_4 = _bump('z')
except Exception:
    _t_4 = -1
_t_5: int
try:
    _t_5 = _tally['k']
except Exception:
    _t_5 = -1
_console.log(f'{_t_3} {_t_4} {_t_5}')
_console.log(f'{_boolean_to_string(_first_same((1000,), (1000,)))} {_boolean_to_string(_first_same((1000,), (7,)))}')
