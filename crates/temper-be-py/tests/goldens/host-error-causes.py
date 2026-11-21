from temper_core import Bubble as _Bubble, LoggingConsole as _LoggingConsole

__all__ = ('host_fails',)


class _connected:
    def host_fails(n: int) -> int:
        raise ValueError(f"host bug {n}")


def host_fails(n: int, /) -> int:
    return _connected.host_fails(n)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('before')
_h: int
try:
    _h = host_fails(1)
except _Bubble as _t_0:
    raise BaseException('panic') from _t_0
_console.log(f'after {_h}')
