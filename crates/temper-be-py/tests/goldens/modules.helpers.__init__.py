from modules.helpers.deep import greet as _greet
from temper_core import (
    LoggingConsole as _LoggingConsole,
    int_add as _int_add,
    int_mul as _int_mul,
)

__all__ = ('twice', 'int_doubled', 'base', 'calls', 'bump')


def twice(x: int, /) -> int:
    return _int_mul(x, 2)


def int_doubled(x: int, /) -> int:
    return twice(x)


def bump() -> int:
    global calls
    calls = _int_add(calls, 1)
    return calls


_console: _LoggingConsole = _LoggingConsole(__name__)
base: int = 10
calls: int = 0
_console.log(_greet('helpers'))
