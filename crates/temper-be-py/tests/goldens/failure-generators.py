from collections.abc import (
    Callable as _Callable,
    MutableSequence as _MutableSequence,
)
from concurrent.futures import Future as _Future
from temper_core import (
    BrokenGenerator as _BrokenGenerator,
    Failure as _Failure,
    LoggingConsole as _LoggingConsole,
    adapt_generator_factory as _adapt_generator_factory,
    failure_cause as _failure_cause,
    generator_next as _generator_next,
    int_add as _int_add,
    int_mul as _int_mul,
    list_join as _list_join,
)
from typing import Any as _Any, Generator as _Generator, TypeVar as _TypeVar

__all__ = ()

_T = _TypeVar('_T')


class _FailureGeneratorsError(_Failure):
    __slots__ = ()


class _TooBig(_FailureGeneratorsError):
    __slots__ = ('_n',)
    _n: int

    def __init__(self, /, n: int) -> None:
        self._n = n

    @property
    def n(self, /) -> int:
        return self._n

    @property
    def message(self, /) -> str:
        return f'too big (n: {self._n})'


def _generated(make: _Callable[[], _Generator[_Any, _Any, None]], /) -> _Generator[_Any, _Any, None]:
    return make()


def _safely(make: _Callable[[], _Generator[_Any, _Any, None]], /) -> _Generator[_Any, _Any, None]:
    return make()


def _why(e: _Failure, /) -> str:
    mine = f'too big {e.n}' if isinstance(e, _TooBig) else e.message
    cause: _Failure | None = _failure_cause(e)
    if cause is None:
        return mine
    else:
        return f'{mine}: {_why(cause)}'


def _take(g: _Generator[_Any, _Any, None], n: int, /) -> str:
    taken: _MutableSequence[str] = []
    while len(taken) < n:
        r: tuple[int] | None
        failed = False
        caught: _BrokenGenerator | None = None
        held: tuple[int] | None | None = None
        try:
            held = _generator_next(g)
        except _BrokenGenerator as failure:
            caught = failure
            failed = True
        if failed:
            assert caught is not None
            e = caught
            return f"{_list_join(taken, ' ', lambda s: s)} {_why(e)}"
        else:
            r = held
        if isinstance(r, tuple):
            taken.append(str(r[0]))
        else:
            taken.append('done')
            break
    return _list_join(taken, ' ', lambda s: s)


def _up_to(limit: int, /) -> _Generator[_Any, _Any, None]:
    @_adapt_generator_factory
    def fn(do_await: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
        i = 1
        while True:
            if i > limit:
                raise _TooBig(i)
            yield i
            i = _int_add(i, 1)
    return _generated(fn)


def _checked(n: int, limit: int, /) -> int:
    if n > limit:
        raise _TooBig(n)
    return n


def _squares(limit: int, /) -> _Generator[_Any, _Any, None]:
    @_adapt_generator_factory
    def fn(do_await: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
        t_0: int
        try:
            t_0 = _checked(50, limit)
        except _TooBig:
            t_0 = 0
        yield t_0
        i = 1
        while True:
            yield _checked(_int_mul(i, i), limit)
            i = _int_add(i, 1)
    return _generated(fn)


def _count(n: int, /) -> _Generator[_Any, _Any, None]:
    @_adapt_generator_factory
    def fn(do_await: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
        i = 1
        while i <= n:
            yield i
            i = _int_add(i, 1)
    return _safely(fn)


def _shown(r: tuple[int] | None, /) -> str:
    if isinstance(r, tuple):
        return str(r[0])
    else:
        return 'done'


def _first_two(g: _Generator[_Any, _Any, None], /) -> str:
    a = _shown(next(map(lambda v: (v,), g), None))
    try:
        b = _shown(_generator_next(g))
    except _BrokenGenerator:
        b = 'failed'
    return f'{a} {b}'


def _doubled(inner: _Generator[_Any, _Any, None], /) -> _Generator[_Any, _Any, None]:
    @_adapt_generator_factory
    def fn(do_await: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
        while True:
            r = _generator_next(inner)
            if isinstance(r, tuple):
                yield _int_mul(r[0], 2)
            else:
                break
    return _generated(fn)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log(_take(_up_to(3), 9))
_one: _Generator[_Any, _Any, None] = _up_to(1)
_console.log(_take(_one, 9))
_console.log(_take(_one, 9))
_console.log(_take(_squares(10), 9))
_console.log(_take(_count(2), 9))
_console.log(_first_two(_count(1)))
_console.log(_take(_doubled(_up_to(2)), 9))
_console.log(_take(_doubled(_count(2)), 9))
