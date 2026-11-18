from temper_core import (
    Bubble as _Bubble,
    LoggingConsole as _LoggingConsole,
    string_get as _string_get,
)

__all__ = ()


def _at(i: int, /) -> int:
    if i == 99:
        raise _Bubble()
    return i


def _code_at(s: str, i: int, /) -> int:
    _at(i)
    return _string_get(s, len(s))


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('reading')
_r: int
try:
    _r = _code_at('ab', 0)
except _Bubble:
    _r = -1
_console.log(f'read the end {_r}')
