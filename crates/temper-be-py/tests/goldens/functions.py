from collections.abc import (
    Callable as _Callable,
    MutableSequence as _MutableSequence,
    Sequence as _Sequence,
)
from concurrent.futures import Future as _Future
from temper_core import (
    LoggingConsole as _LoggingConsole,
    Pair as _Pair,
    adapt_generator_factory as _adapt_generator_factory,
    boolean_to_string as _boolean_to_string,
    int_add as _int_add,
    int_mul as _int_mul,
    int_sub as _int_sub,
    list_filter as _list_filter,
    list_get as _list_get,
    list_join as _list_join,
    list_map as _list_map,
    listed_reduce_from as _listed_reduce_from,
    listed_sorted as _listed_sorted,
    map_constructor as _map_constructor,
    mapped_to_list_with as _mapped_to_list_with,
    string_get as _string_get,
    string_next as _string_next,
    string_prev as _string_prev,
)
from types import MappingProxyType as _MappingProxyType
from typing import Any as _Any, Generator as _Generator, TypeVar as _TypeVar

__all__ = ()

_T = _TypeVar('_T')
_O = _TypeVar('_O')


def _make_counter(start: int, /) -> _Callable[[], int]:
    count = start
    def fn() -> int:
        nonlocal count
        count = _int_add(count, 1)
        return count
    return fn


def _twice(f: _Callable[[int], int], /) -> _Callable[[int], int]:
    return lambda x: f(f(x))


def _apply_to(f: _Callable[[int], int], x: int, /) -> int:
    return f(x)


def _add_one(x: int, /) -> int:
    return _int_add(x, 1)


class _Scaler:
    __slots__ = ()

    @staticmethod
    def triple(x: int, /) -> int:
        return _int_mul(x, 3)


class _Button:
    __slots__ = ('_label', '_on_press')
    _label: str
    _on_press: _Callable[[], int]

    def __init__(self, /, label: str, on_press: _Callable[[], int]) -> None:
        self._label = label
        self._on_press = on_press

    @property
    def label(self, /) -> str:
        return self._label

    @property
    def on_press(self, /) -> _Callable[[], int]:
        return self._on_press

    def press(self, /) -> str:
        return f'{self._label} {self._on_press()}'


def _drive(steps: int, body: _Callable[[], _Generator[_Any, _Any, None]], /) -> None:
    g = body()
    s = 0
    while s < steps:
        next(g, None)
        s = _int_add(s, 1)


def _sum_from(xs: _Sequence[int], least: int, /) -> int:
    sum = 0
    length = len(xs)
    i = 0
    while i < length:
        x = _list_get(xs, i)
        i = _int_add(i, 1)
        if x < least:
            continue
        sum = _int_add(sum, x)
    return sum


def _doubles(s: str, /) -> int:
    n = 0
    end = len(s)
    i = 0
    prev = i
    while i - end < 0:
        if prev - i < 0 and _string_get(s, prev) == _string_get(s, i):
            n = _int_add(n, 1)
        prev = i
        i = _string_next(s, i)
    return n


def _climb(x: int, /) -> int:
    d: int
    if x == 0:
        def down(n: int, /) -> int:
            if n <= x:
                return 0
            else:
                return _int_add(down(_int_sub(n, 1)), 1)
        d = down(3)
    else:
        return -1
    d = _int_add(d, 1)
    if d == 4:
        top = _int_add(d, 1)
        def up(n: int, /) -> int:
            if n >= top:
                return n
            else:
                return up(_int_add(n, 1))
        return up(d)
    else:
        return d


class _Gate:
    __slots__ = ('_check',)
    _check: _Callable[[int], int] | None

    def __init__(self, /) -> None:
        self._check = None

    @property
    def check(self, /) -> _Callable[[int], int] | None:
        return self._check

    @check.setter
    def check(self, value: _Callable[[int], int] | None, /) -> None:
        self._check = value

    def allows(self, n: int, /) -> bool:
        c: _Callable[[int], int] | None = self._check
        if c is None:
            return True
        else:
            return c(n) > 0

    def passed(self, n: int, /) -> int:
        c: _Callable[[int], int] | None = self._check
        if c is not None:
            return c(n)
        else:
            return n


def _string_is_palindrome(s: str, /) -> bool:
    i = 0
    j = len(s)
    while i - j < 0:
        j = _string_prev(s, j)
        if _string_get(s, i) != _string_get(s, j):
            return False
        i = _string_next(s, i)
    return True


def _int_or_zero(n: int | None, /) -> int:
    if n is None:
        return 0
    else:
        return n


def _list_first_or(ls: _Sequence[_T], d: _T, /) -> _T:
    if not ls:
        return d
    else:
        return _list_get(ls, 0)


def _string_padded(s: str, n: int, fill: str | None = None, /) -> str:
    if fill is None:
        fill = '.'
    out = s
    i = 0
    while i < n:
        out = out + fill
        i = _int_add(i, 1)
    return out


def _gate_passed(g: _Gate, s: str, /) -> str:
    return f'{s} {_boolean_to_string(g.allows(1))}'


def _list_first_through(ls: _Sequence[_T], f: _Callable[[_T], _O], /) -> _O:
    return f(_list_get(ls, 0))


_console: _LoggingConsole = _LoggingConsole(__name__)
_counter: _Callable[[], int] = _make_counter(10)
_counter()
_console.log(f'count {_counter()}')
_other: _Callable[[], int] = _make_counter(0)
_console.log(f'other {_other()} count {_counter()}')
_add_two: _Callable[[int], int] = _twice(_add_one)
_console.log(f'twice {_add_two(5)} {_apply_to(_add_one, 1)}')
_console.log(f'triple {_apply_to(_Scaler.triple, 4)} {_twice(_Scaler.triple)(2)}')
_ns: _Sequence[int] = (5, 2, 8, 1, 4)
_doubled: _Sequence[int] = _list_map(_ns, lambda n: _int_mul(n, 2))
_big: _Sequence[int] = _list_filter(_doubled, lambda n: n > 4)
_console.log(_list_join(_big, ', ', lambda n: str(n)))
_order: _Sequence[int] = _listed_sorted(_ns, lambda a, b: _int_sub(a, b))
_console.log(_list_join(_order, ' ', lambda n: str(n)))
_total: int = 0
_length = len(_ns)
_i2 = 0
while _i2 < _length:
    _n = _list_get(_ns, _i2)
    _i2 = _int_add(_i2, 1)
    _total = _int_add(_total, _n)
_console.log(f'total {_total}')
_button: _Button = _Button('hits', _make_counter(0))
_button.press()
_console.log(_button.press())
_makers: _MutableSequence[_Callable[[], int]] = []
_i = 0
while _i < 3:
    _k = _int_mul(_i, 10)
    def _make_fn6(_k: int, /) -> _Callable[[], int]:
        return lambda: _k
    _makers.append(_make_fn6(_k))
    _i = _int_add(_i, 1)
_console.log(_list_join(_makers, ' ', lambda c: str(c())))


@_adapt_generator_factory
def _fn8(do_await: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
    fs: _MutableSequence[_Callable[[], int]] = []
    i = 0
    while i < 3:
        j = _int_mul(i, 10)
        def make_fn9(j: int, /) -> _Callable[[], int]:
            return lambda: j
        fs.append(make_fn9(j))
        yield
        def make_fn10(j: int, /) -> _Callable[[], int]:
            return lambda: _int_add(j, 1)
        fs.append(make_fn10(j))
        i = _int_add(i, 1)
    _console.log(_list_join(fs, ' ', lambda c: str(c())))
    yield


_drive(5, _fn8)
_console.log(f"sum {_sum_from(_ns, 4)} doubles {_doubles('bookkeeper')}")
_console.log(f'climb {_climb(0)} {_climb(1)}')
_gate: _Gate = _Gate()
_console.log(f'gate {_boolean_to_string(_gate.allows(0))} {_gate.passed(4)}')
_gate.check = lambda x: _int_sub(x, 1)
_console.log(f'gate {_boolean_to_string(_gate.allows(1))} {_boolean_to_string(_gate.allows(2))} {_gate.passed(4)}')
_none: int | None = 3 if _gate.allows(0) else None
_console.log(f"{_boolean_to_string(_string_is_palindrome('step on no pets'))} {_boolean_to_string(_string_is_palindrome('steps'))}")
_console.log(f"{_int_or_zero(_none)} {_list_first_or((4, 5), 0)} {_list_first_or(('x',), '-')}")
_console.log(f"{_string_padded('ab', 2)} {_string_padded('ab', 1, '*')}")
_console.log(f"{_gate.passed(4)} {_gate_passed(_gate, 'gate')}")
_tags: _Sequence[str] = _list_map(_ns, lambda n: f'<{n}>')
_digits: str = _listed_reduce_from(_ns, '', lambda s, n: s + str(n))
_named: _MappingProxyType[str, int] = _map_constructor((_Pair('a', 1), _Pair('b', 2)))
_entries: _Sequence[str] = _mapped_to_list_with(_named, lambda k, v: f'{k}={v}')
_console.log(f"{_list_get(_tags, 0)}{_list_get(_tags, 4)} {_digits} {_list_join(_entries, ',', lambda e: e)}")
_hashed: str = _list_first_through((3,), lambda n: f'#{n}')
_bumped: int = _list_first_through((3,), lambda n: _int_add(n, 1))
_console.log(f'{_hashed} {_bumped} {_list_first_or((4, 5), 0)}')
