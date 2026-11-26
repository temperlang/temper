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
        b: _TemperFuture[str] = _new_unbound_promise()
        _complete_promise(b, 'Completed synchronously')
        _console.log((yield do_await(b)))
    except _BrokenPromise as t_0:
        raise BaseException('panic') from t_0
    b = _new_unbound_promise()
    _break_promise(b)
    t_1: str
    try:
        t_1 = yield do_await(b)
    except _BrokenPromise:
        t_1 = 'Broken synchronously'
    _console.log(t_1)
    b = _new_unbound_promise()
    _break_promise(b)
    t_2: str
    try:
        t_2 = yield do_await(b)
    except _BrokenPromise as e:
        t_2 = f'Caught {e.message}'
    _console.log(t_2)
    b = _new_unbound_promise()
    @_adapt_generator_factory
    def fn2(do_await_1: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
        if False:
            yield
        _break_promise(b)
    _async_launch(fn2)
    t_3: str
    try:
        t_3 = yield do_await(b)
    except _BrokenPromise as e:
        t_3 = f'Caught {e.message} later'
    _console.log(t_3)
    try:
        b2: _TemperFuture[str] = _new_unbound_promise()
        @_adapt_generator_factory
        def fn3(do_await_1: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
            if False:
                yield
            _complete_promise(b2, 'Completed asynchronously')
        _async_launch(fn3)
        _console.log((yield do_await(b2)))
    except _BrokenPromise as t_4:
        raise BaseException('panic') from t_4
    try:
        b3: _TemperFuture[str] = _new_unbound_promise()
        _complete_promise(b3, 'Worth doing twice')
        _console.log((yield do_await(b3)))
        _console.log((yield do_await(b3)))
    except _BrokenPromise as t_5:
        raise BaseException('panic') from t_5
    try:
        a: _TemperFuture[tuple[()]] = _new_unbound_promise()
        b4: _TemperFuture[tuple[()]] = _new_unbound_promise()
        @_adapt_generator_factory
        def fn4(do_await_1: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
            try:
                yield do_await_1(b4)
            except _BrokenPromise as t_0:
                raise BaseException('panic') from t_0
            _console.log('After')
            _complete_promise(a, ())
        _async_launch(fn4)
        @_adapt_generator_factory
        def fn5(do_await_1: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
            if False:
                yield
            _console.log('Before')
            _complete_promise(b4, ())
        _async_launch(fn5)
        yield do_await(a)
    except _BrokenPromise as t_6:
        raise BaseException('panic') from t_6
    try:
        _console.log('What is your favourite colour?')
        b3 = _new_unbound_promise()
        _complete_promise(b3, 'Blue')
        _complete_promise(b3, 'No, yell...')
        _console.log((yield do_await(b3)))
    except _BrokenPromise:
        pass


_async_launch(_fn)
