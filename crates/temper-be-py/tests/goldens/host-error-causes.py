from temper_core import Failure as _Failure, LoggingConsole as _LoggingConsole

__all__ = ('OopsError', 'Oops', 'host_fails')


class _connected:
    def host_fails(n: int) -> int:
        raise RuntimeError(f"host bug {n}")


class OopsError(_Failure):
    __slots__ = ()


class Oops(OopsError):
    __slots__ = ()

    @property
    def message(self, /) -> str:
        return 'oops'


def host_fails(n: int, /) -> int:
    return _connected.host_fails(n)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('before')
_h: int
try:
    _h = host_fails(1)
except Oops as _t_0:
    raise BaseException('panic') from _t_0
_console.log(f'after {_h}')
