from temper_core import Bubble as _Bubble, LoggingConsole as _LoggingConsole

__all__ = ()


def _positive(n: int, /) -> int:
    if n <= 0:
        raise _Bubble()
    return n


def _checked(n: int, /) -> int:
    try:
        if n == 0:
            return 0
        _positive(n)
    except _Bubble as t_0:
        raise BaseException('panic') from t_0
    return n


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('before')
_console.log(f'after {_checked(-1)}')
