from collections.abc import Callable as _Callable
from concurrent.futures import Future as _Future
from temper_core import (
    BrokenPromise as _BrokenPromise,
    LoggingConsole as _LoggingConsole,
    TemperFuture as _TemperFuture,
    adapt_generator_factory as _adapt_generator_factory,
    async_launch as _async_launch,
    break_promise as _break_promise,
    complete_promise as _complete_promise,
    new_unbound_promise as _new_unbound_promise,
)
from typing import Any as _Any, Generator as _Generator

__all__ = ()

_console: _LoggingConsole = _LoggingConsole(__name__)


@_adapt_generator_factory
def _fn(do_await: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
    try:
        b: _TemperFuture[int] = _new_unbound_promise()
        first_done: _TemperFuture[int] = _new_unbound_promise()
        second_done: _TemperFuture[int] = _new_unbound_promise()
        @_adapt_generator_factory
        def fn2(do_await_1: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
            try:
                v = yield do_await_1(b)
            except _BrokenPromise:
                v = -1
            _complete_promise(first_done, v)
        _async_launch(fn2)
        @_adapt_generator_factory
        def fn3(do_await_1: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
            try:
                v = yield do_await_1(b)
            except _BrokenPromise:
                v = -1
            _complete_promise(second_done, v)
        _async_launch(fn3)
        @_adapt_generator_factory
        def fn4(do_await_1: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
            if False:
                yield
            _complete_promise(b, 7)
        _async_launch(fn4)
        first = yield do_await(first_done)
        second = yield do_await(second_done)
        _console.log(f'after complete: first got {first}, second got {second}')
    except _BrokenPromise as t_0:
        raise BaseException('panic') from t_0
    try:
        b2: _TemperFuture[int] = _new_unbound_promise()
        first_done2: _TemperFuture[int] = _new_unbound_promise()
        second_done2: _TemperFuture[int] = _new_unbound_promise()
        @_adapt_generator_factory
        def fn5(do_await_1: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
            try:
                v = yield do_await_1(b2)
            except _BrokenPromise:
                v = -1
            _complete_promise(first_done2, v)
        _async_launch(fn5)
        @_adapt_generator_factory
        def fn6(do_await_1: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
            try:
                v = yield do_await_1(b2)
            except _BrokenPromise:
                v = -1
            _complete_promise(second_done2, v)
        _async_launch(fn6)
        @_adapt_generator_factory
        def fn7(do_await_1: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
            if False:
                yield
            _break_promise(b2)
        _async_launch(fn7)
        first = yield do_await(first_done2)
        second = yield do_await(second_done2)
        _console.log(f'after breakPromise: first got {first}, second got {second}')
    except _BrokenPromise as t_1:
        raise BaseException('panic') from t_1


_async_launch(_fn)
