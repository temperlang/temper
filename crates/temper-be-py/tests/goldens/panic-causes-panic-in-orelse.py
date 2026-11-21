from temper_core import Bubble as _Bubble, LoggingConsole as _LoggingConsole

__all__ = ()


def _positive(n: int, /) -> int:
    if n <= 0:
        raise _Bubble()
    return n


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('before')
_p: int
try:
    _p = _positive(-1)
except _Bubble as _t_0:
    raise BaseException('panic') from _t_0
_console.log(f'after {_p}')
