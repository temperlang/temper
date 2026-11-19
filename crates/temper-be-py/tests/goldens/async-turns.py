from collections.abc import (
    Callable as _Callable,
    MutableSequence as _MutableSequence,
    Sequence as _Sequence,
)
from concurrent.futures import Future as _Future
from temper_core import (
    Bubble as _Bubble,
    LoggingConsole as _LoggingConsole,
    TemperFuture as _TemperFuture,
    adapt_generator_factory as _adapt_generator_factory,
    arith_int_mod as _arith_int_mod,
    async_launch as _async_launch,
    complete_promise as _complete_promise,
    int_add as _int_add,
    int_div as _int_div,
    int_mul as _int_mul,
    list_join as _list_join,
    new_unbound_promise as _new_unbound_promise,
)
from typing import Any as _Any, Generator as _Generator, TypeVar as _TypeVar

__all__ = ('later', 'ticks', 'fibonacci', 'collatz', 'halve')

_T = _TypeVar('_T')


def later(n: int, /) -> _TemperFuture[int]:
    pb: _TemperFuture[int] = _new_unbound_promise()
    @_adapt_generator_factory
    def fn(do_await: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
        if False:
            yield
        _complete_promise(pb, _int_mul(n, 2))
    _async_launch(fn)
    return pb


def _generated(make: _Callable[[], _Generator[_Any, _Any, None]], /) -> _Generator[_Any, _Any, None]:
    return make()


def ticks(n: int, each: _Callable[[int], None], /) -> _Generator[_Any, _Any, None]:
    @_adapt_generator_factory
    def fn(do_await: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
        i = 0
        while i < n:
            each(i)
            yield
            i = _int_add(i, 1)
    return _generated(fn)


def _started_at(start: int, make: _Callable[[int], _Generator[_Any, _Any, None]], /) -> _Generator[_Any, _Any, None]:
    return make(start)


def fibonacci() -> _Generator[_Any, _Any, None]:
    @_adapt_generator_factory
    def fn(do_await: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
        a = 0
        b = 1
        while True:
            yield a
            next = _int_add(a, b)
            a = b
            b = next
    return _generated(fn)


def collatz(start: int, /) -> _Generator[_Any, _Any, None]:
    @_adapt_generator_factory
    def fn(do_await: _Callable[[_Future[_Any]], _Any], first: int, /) -> _Generator[_Any, _Any, None]:
        n = first
        while True:
            yield n
            if n == 1:
                break
            if _arith_int_mod(n, 2) == 0:
                n = _int_div(n, 2)
            else:
                n = _int_add(_int_mul(3, n), 1)
    return _started_at(start, fn)


def _take(g: _Generator[_Any, _Any, None], n: int, /) -> _Sequence[int]:
    taken: _MutableSequence[int] = []
    while len(taken) < n:
        r = next(map(lambda v: (v,), g), None)
        if isinstance(r, tuple):
            taken.append(r[0])
        else:
            break
    return tuple(taken)


def _shown(ints: _Sequence[int], /) -> str:
    return _list_join(ints, ' ', lambda i: str(i))


class _Counter:
    __slots__ = ('_n',)
    _n: int

    def __init__(self, /) -> None:
        self._n = 0

    @property
    def n(self, /) -> int:
        return self._n

    @n.setter
    def n(self, value: int, /) -> None:
        self._n = value

    def bump(self, tag: str, times: int, /) -> None:
        _console.log(f'{tag} start')
        i = 0
        while i < times:
            before = self._n
            self._n = _int_add(before, 1)
            i = _int_add(i, 1)
        _console.log(f'{tag} done n={self._n}')


def halve(n: int, /) -> int:
    if _arith_int_mod(n, 2) != 0:
        raise _Bubble()
    return _int_div(n, 2)


_console: _LoggingConsole = _LoggingConsole(__name__)


def _fn(i: int, /) -> None:
    _console.log(f'tick {i}')


_t: _Generator[_Any, _Any, None] = ticks(2, _fn)
next(_t, None)
next(_t, None)
next(_t, None)
_console.log(f'fibonacci {_shown(_take(fibonacci(), 10))}')
_console.log(f'collatz {_shown(_take(collatz(6), 100))}')
_fib: _Generator[_Any, _Any, None] = fibonacci()
_take(_fib, 5)
_console.log(f'then {_shown(_take(_fib, 3))}')
_c: _Counter = _Counter()
_gate_a: _TemperFuture[int] = _new_unbound_promise()
_gate_b: _TemperFuture[int] = _new_unbound_promise()


@_adapt_generator_factory
def _fn2(do_await: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
    _c.bump('A', 10000)
    try:
        k = yield do_await(_gate_a)
    except _Bubble:
        k = -1
    _c.bump(f'A{k}', 10000)


_async_launch(_fn2)


@_adapt_generator_factory
def _fn3(do_await: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
    _c.bump('B', 10000)
    try:
        k = yield do_await(_gate_b)
    except _Bubble:
        k = -1
    _c.bump(f'B{k}', 10000)


_async_launch(_fn3)


@_adapt_generator_factory
def _fn4(do_await: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
    if False:
        yield
    _console.log('opener: calling complete')
    _complete_promise(_gate_a, 1)
    _complete_promise(_gate_b, 2)
    _console.log('opener: complete returned')


_async_launch(_fn4)
