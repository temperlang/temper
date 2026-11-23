from collections.abc import Callable as _Callable
from concurrent.futures import Future as _Future
from temper_core import (
    Bubble as _Bubble,
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
    except _Bubble as t_0:
        raise BaseException('panic') from t_0
    b = _new_unbound_promise()
    _break_promise(b)
    t_1: str
    try:
        t_1 = yield do_await(b)
    except _Bubble:
        t_1 = 'Broken synchronously'
    _console.log(t_1)
    try:
        b = _new_unbound_promise()
        @_adapt_generator_factory
        def fn2(do_await_1: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
            if False:
                yield
            _complete_promise(b, 'Completed asynchronously')
        _async_launch(fn2)
        _console.log((yield do_await(b)))
    except _Bubble as t_2:
        raise BaseException('panic') from t_2
    try:
        b2: _TemperFuture[str] = _new_unbound_promise()
        _complete_promise(b2, 'Worth doing twice')
        _console.log((yield do_await(b2)))
        _console.log((yield do_await(b2)))
    except _Bubble as t_3:
        raise BaseException('panic') from t_3
    try:
        a: _TemperFuture[tuple[()]] = _new_unbound_promise()
        b3: _TemperFuture[tuple[()]] = _new_unbound_promise()
        @_adapt_generator_factory
        def fn3(do_await_1: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
            try:
                yield do_await_1(b3)
            except _Bubble as t_0:
                raise BaseException('panic') from t_0
            _console.log('After')
            _complete_promise(a, ())
        _async_launch(fn3)
        @_adapt_generator_factory
        def fn4(do_await_1: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
            if False:
                yield
            _console.log('Before')
            _complete_promise(b3, ())
        _async_launch(fn4)
        yield do_await(a)
    except _Bubble as t_4:
        raise BaseException('panic') from t_4
    try:
        _console.log('What is your favourite colour?')
        b2 = _new_unbound_promise()
        _complete_promise(b2, 'Blue')
        _complete_promise(b2, 'No, yell...')
        _console.log((yield do_await(b2)))
    except _Bubble:
        pass


_async_launch(_fn)
