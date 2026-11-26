from abc import ABCMeta as _ABCMeta, abstractmethod as _abstractmethod
from collections.abc import Callable as _Callable
from concurrent.futures import Future as _Future
from temper_core import (
    LoggingConsole as _LoggingConsole,
    TemperFuture as _TemperFuture,
    adapt_generator_factory as _adapt_generator_factory,
    async_launch as _async_launch,
    boolean_to_string as _boolean_to_string,
    complete_promise as _complete_promise,
    int_add as _int_add,
    int_mul as _int_mul,
    new_unbound_promise as _new_unbound_promise,
)
from typing import (
    Any as _Any,
    Generator as _Generator,
    Generic as _Generic,
    TypeVar as _TypeVar,
)

__all__ = ()

_T = _TypeVar('_T')


class _Sized(metaclass=_ABCMeta):
    __slots__ = ()

    @_abstractmethod
    def area(self, /) -> int:
        ...


class _Box(_Sized):
    __slots__ = ('_w', '_h', '_label')
    _w: int
    _h: int
    _label: str | None

    def __init__(self, /, w: int, h: int, label: str | None = None) -> None:
        self._w = w
        self._h = h
        self._label = label

    @property
    def w(self, /) -> int:
        return self._w

    @property
    def h(self, /) -> int:
        return self._h

    @property
    def label(self, /) -> str | None:
        return self._label

    def area(self, /) -> int:
        return _int_mul(self._w, self._h)

    def to_string(self, /) -> str:
        return f"{'box' if (subject := self._label) is None else subject} {self._w}x{self._h}"

    def __str__(self, /) -> str:
        return self.to_string()


class _Two(_Generic[_T]):
    __slots__ = ('_first', '_second')
    _first: _T
    _second: _T

    def __init__(self, /, first: _T, second: _T) -> None:
        self._first = first
        self._second = second

    @property
    def first(self, /) -> _T:
        return self._first

    @property
    def second(self, /) -> _T:
        return self._second


def _grown(b: _Box, /) -> _Box:
    spread = b
    return _Box(_int_add(b.w, 1), spread.h, spread.label)


def _area_of(s: _Sized, /) -> int:
    return s.area()


def _said(s: str, n: int, /) -> int:
    _console.log(s)
    return n


def _both(x: int, b: _Box, /) -> str:
    return f'{x} {b.to_string()}'


def _widths(b: _Box, n: int, /) -> int:
    t = b
    i = 0
    while i < n:
        spread = t
        t = _Box(t.h, _int_add(t.w, 1), spread.label)
        i = _int_add(i, 1)
    while True:
        spread = t
        if not _Box(spread.w, 1, spread.label).area() < 9:
            break
        spread = t
        t = _Box(_int_add(t.w, 1), spread.h, spread.label)
    i = 0
    while i < 2:
        spread = t
        arg = _said('h', t.w)
        arg2 = _said('w', t.h)
        arg3: str | None = spread.label
        t = _Box(arg2, arg, arg3)
        i = _int_add(i, 1)
    if t.w > 0:
        spread = b
        b = _Box(spread.w, spread.h, 'inner')
        _console.log(b.to_string())
    return t.w


class _Step:
    __slots__ = ('_n', '_tag')
    _n: int
    _tag: str

    def __init__(self, /, n: int, tag: str) -> None:
        self._n = n
        self._tag = tag

    @property
    def n(self, /) -> int:
        return self._n

    @property
    def tag(self, /) -> str:
        return self._tag

    def next(self, /) -> '_Step':
        spread = self
        return _Step(_int_add(self._n, 1), spread._tag)


def _swapped(t: _Two[_T], /) -> _Two[_T]:
    spread = t  # pyright: ignore[reportUnusedVariable]
    return _Two(t.second, t.first)


class _Shape(metaclass=_ABCMeta):
    __slots__ = ()


class _Circle(_Shape):
    __slots__ = ('_r',)
    _r: int

    def __init__(self, /, r: int) -> None:
        self._r = r

    @property
    def r(self, /) -> int:
        return self._r


class _Square(_Shape):
    __slots__ = ('_side',)
    _side: int

    def __init__(self, /, side: int) -> None:
        self._side = side

    @property
    def side(self, /) -> int:
        return self._side


def _doubled(x: _Shape, /) -> _Shape:
    t_1: _Shape
    if isinstance(x, _Circle):
        spread = x  # pyright: ignore[reportUnusedVariable]
        t_1 = _Circle(_int_mul(x.r, 2))
    else:
        t_0: _Square
        if isinstance(x, _Square):
            spread2 = x  # pyright: ignore[reportUnusedVariable]
            t_0 = _Square(_int_mul(x.side, 2))
        else:
            raise BaseException('panic')
        t_1 = t_0
    made = t_1
    return made


def _size(x: _Shape, /) -> int:
    t_1: int
    if isinstance(x, _Circle):
        t_1 = x.r
    else:
        t_0: int
        if isinstance(x, _Square):
            t_0 = x.side
        else:
            raise BaseException('panic')
        t_1 = t_0
    n = t_1
    return n


def _picked(c: bool, /) -> _Box:
    if c:
        spread = _a
        return _Box(9, spread.h, spread.label)
    else:
        return _a


def _applied(f: _Callable[[int], _Box], /) -> _Box:
    return f(4)


def _positive(n: int, /) -> int:
    if n < 0:
        raise RuntimeError()
    return n


def _sized(b: _Box, n: int, /) -> _Box:
    spread = b
    return _Box(_positive(n), spread.h, spread.label)


_console: _LoggingConsole = _LoggingConsole(__name__)
_a: _Box = _Box(2, 3)
_b: _Box = _grown(_a)
_console.log(f'{_a.to_string()} {_b.to_string()} {_area_of(_b)}')
_spread = _b
_console.log(_Box(_spread.w, _spread.h, 'named').to_string())
_t_0: bool
_t_0 = _a.w > 1
if _t_0:
    _spread2 = _a
    _t_0 = _Box(10, _spread2.h, _spread2.label).area() > 20
_wide: bool = _t_0
_p: _Two[int] = _Two(1, 2)
_spread3 = _p
_q: _Two[int] = _Two(_spread3.first, 5)
_console.log(f'{_boolean_to_string(_wide)} {_q.first} {_q.second}')
_spread5 = _a
_spread4 = _Box(7, _spread5.h, _spread5.label)
_nested: _Box = _Box(_spread4.w, 8, _spread4.label)
_t_1 = _said('one', 1)
_spread6 = _a
_console.log(_both(_t_1, _Box(_spread6.w, _said('two', 2), _spread6.label)))
_t_3 = _nested.to_string()
_t_2: bool
_t_2 = _a.w > 5
if not _t_2:
    _spread7 = _a
    _t_2 = _Box(_said('three', 3), _spread7.h, _spread7.label).w == 3
_console.log(f'{_t_3} {_boolean_to_string(_t_2)}')
_console.log(str(_widths(_a, 3)))
_s: _Step = _Step(1, 's').next().next()
_spread8 = _q
_same: _Two[int] = _Two(_spread8.first, _spread8.second)
_words: _Two[str] = _Two('a', 'b')
_flipped: _Two[str] = _swapped(_words)
_console.log(f'{_s.n} {_s.tag} {_same.first}{_same.second} {_flipped.first}{_flipped.second}')
_console.log(f'{_size(_doubled(_Circle(3)))} {_size(_doubled(_Square(5)))}')


def _fn(n: int, /) -> _Box:
    spread = _a
    return _Box(spread.w, n, spread.label)


_made: _Box = _applied(_fn)
_console.log(f'{_picked(True).to_string()} {_picked(False).to_string()} {_made.to_string()}')
_spread9 = _a
_t_4: int
try:
    _t_4 = _positive(-1)
except Exception:
    _t_4 = 0
_t_5: int
try:
    _t_5 = _positive(5)
except Exception:
    _t_5 = -1
_caught: _Box = _Box(_t_4, _t_5, _spread9.label)
_t_7 = _caught.to_string()
_t_6: str
try:
    _t_6 = _sized(_a, 4).to_string()
except Exception:
    _t_6 = '-'
_console.log(f'{_t_7} {_t_6}')
_t_8: str
try:
    _sized(_a, -1)
    _t_8 = 'sized'
except Exception:
    _t_8 = 'bubbled'
_console.log(_t_8)
_gate: _TemperFuture[int] = _new_unbound_promise()


@_adapt_generator_factory
def _fn2(do_await: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
    spread = _a
    arg = spread.w
    try:
        arg2 = yield do_await(_gate)
    except Exception:
        arg2 = -1
    later = _Box(arg, arg2, 'later')
    _console.log(later.to_string())


_async_launch(_fn2)
_complete_promise(_gate, 6)
