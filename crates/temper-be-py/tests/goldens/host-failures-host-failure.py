from temper_core import Failure as _Failure, LoggingConsole as _LoggingConsole

__all__ = ('HostFailuresError', 'NotFound', 'host_lookup')


# A specification: `NotFound` is the library's own failure class, in the
# module this code joins, as the Python backend names it.
class _connected:
    def host_lookup(key: str) -> int:
        if key == "bug":
            raise RuntimeError("host bug")
        if key == "a":
            return 1
        raise NotFound(key)  # noqa: F821


class HostFailuresError(_Failure):
    __slots__ = ()


class NotFound(HostFailuresError):
    __slots__ = ('_key',)
    _key: str

    def __init__(self, /, key: str) -> None:
        self._key = key

    @property
    def key(self, /) -> str:
        return self._key

    @property
    def message(self, /) -> str:
        return f'not found (key: {self._key})'


def host_lookup(key: str, /) -> int:
    return _connected.host_lookup(key)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('before')
_n: int
try:
    _n = host_lookup('z')
except NotFound as _e:
    _console.log(f'caught {_e.key}')
    _n = -1
_t_0: int
try:
    _t_0 = host_lookup('a')
except NotFound:
    _t_0 = 0
_console.log(f'got {_n} {_t_0}')
host_lookup('y')
_console.log('after')
