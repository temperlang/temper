from collections.abc import Callable as _Callable, Sequence as _Sequence
from concurrent.futures import Future as _Future
from temper_core import (
    LoggingConsole as _LoggingConsole,
    adapt_generator_factory as _adapt_generator_factory,
    async_launch as _async_launch,
    list_get as _list_get,
)
from typing import Any as _Any, Generator as _Generator

__all__ = ()


def _third(ls: _Sequence[int], /) -> int:
    return _list_get(ls, 2)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('before')


@_adapt_generator_factory
def _fn(do_await: _Callable[[_Future[_Any]], _Any], /) -> _Generator[_Any, _Any, None]:
    if False:
        yield
    _console.log('in async')
    _third((1, 2))


_async_launch(_fn)
