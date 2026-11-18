from collections.abc import Callable as _Callable
from concurrent.futures import Future as _Future
from temper_core import (
    Bubble as _Bubble,
    LoggingConsole as _LoggingConsole,
    adapt_generator_factory as _adapt_generator_factory,
    async_launch as _async_launch,
)
from temper_std.net import NetRequest as _NetRequest
from typing import Any as _Any, Generator as _Generator

__all__ = ()

_console: _LoggingConsole = _LoggingConsole(__name__)
_url: str = f'http://127.0.0.1:{28461}/missing'


@_adapt_generator_factory
def _fn(do_await: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
    try:
        resp = yield do_await(_NetRequest(_url).send())
        _console.log(f'status {resp.status}')
        body = 'missing' if (subject := (yield do_await(resp.text))) is None else subject
        _console.log(f'body {body}')
    except _Bubble:
        _console.log('broken promise')
    try:
        yield do_await(_NetRequest('http://127.0.0.1:1/').send())
        _console.log('port 1 answered')
    except _Bubble:
        _console.log('no response from port 1')


_async_launch(_fn)
