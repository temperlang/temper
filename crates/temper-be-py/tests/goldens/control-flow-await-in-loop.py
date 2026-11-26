from collections.abc import Callable as _Callable
from concurrent.futures import Future as _Future
from temper_core import (
    BrokenPromise as _BrokenPromise,
    LoggingConsole as _LoggingConsole,
    TemperFuture as _TemperFuture,
    adapt_generator_factory as _adapt_generator_factory,
    async_launch as _async_launch,
    complete_promise as _complete_promise,
    int_add as _int_add,
    new_unbound_promise as _new_unbound_promise,
)
from typing import Any as _Any, Generator as _Generator

__all__ = ()

_console: _LoggingConsole = _LoggingConsole(__name__)
_once: _TemperFuture[str] = _new_unbound_promise()
_complete_promise(_once, 'same')
_later: _TemperFuture[str] = _new_unbound_promise()


@_adapt_generator_factory
def _fn(do_await: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
    i = 0
    while i < 2:
        try:
            s = yield do_await(_once)
        except _BrokenPromise:
            s = '?'
        _console.log(f'{s} {i}')
        i = _int_add(i, 1)
    j = 0
    while j < 2:
        try:
            s = yield do_await(_later)
        except _BrokenPromise:
            s = '?'
        _console.log(f'{s} {j}')
        j = _int_add(j, 1)
    try:
        t = yield do_await(_once)
    except _BrokenPromise:
        t = '?'
    _console.log(f'{t} after the loops')


_async_launch(_fn)
_complete_promise(_later, 'later')
