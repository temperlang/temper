from collections.abc import Callable as _Callable
from concurrent.futures import Future as _Future
from temper_core import (
    BrokenPromise as _BrokenPromise,
    LoggingConsole as _LoggingConsole,
    adapt_generator_factory as _adapt_generator_factory,
    async_launch as _async_launch,
)
from temper_std.net import NetRequest as _NetRequest
from typing import Any as _Any, Generator as _Generator

__all__ = ()

_console: _LoggingConsole = _LoggingConsole(__name__)
_url: str = f'http://127.0.0.1:{28461}'


@_adapt_generator_factory
def _fn(do_await: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
    try:
        req = _NetRequest(_url)
        req.post('[]', 'application/json')
        resp = yield do_await(req.send())
        if resp.status == 200:
            body = 'missing' if (subject := (yield do_await(resp.text))) is None else subject
            _console.log(f'Thanks, server.  I got {body}.')
        else:
            _console.log(f'HTTP status was not 200: {resp.status}!')
    except _BrokenPromise:
        _console.log('failed')


_async_launch(_fn)
