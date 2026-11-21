from temper_core import Bubble as _Bubble, LoggingConsole as _LoggingConsole

__all__ = ()


def _positive(n: int, /) -> int:
    if n <= 0:
        raise _Bubble()
    return n


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('before')
_positive(-1)
_console.log('after')
