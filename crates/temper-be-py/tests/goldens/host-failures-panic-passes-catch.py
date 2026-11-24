from temper_core import Failure as _Failure, LoggingConsole as _LoggingConsole

__all__ = ('HostFailuresPanicPassesCatchError', 'NotFound', 'host_lookup')


# A specification: `NotFound` is the library's own failure class, in the
# module this code joins, as the Python backend names it.
class _connected:
    def host_lookup(key: str) -> int:
        if key == "bug":
            raise RuntimeError("host bug")
        if key == "a":
            return 1
        raise NotFound(key)  # noqa: F821


class HostFailuresPanicPassesCatchError(_Failure, Exception):
    __slots__ = ()


class NotFound(HostFailuresPanicPassesCatchError):
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


def _risky(n: int, /) -> int:
    if n > 1:
        raise BaseException('panic')
    if n > 0:
        raise NotFound('n')
    return n


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('before')
_r: int
try:
    _r = _risky(2)
except NotFound:
    _r = -1
_console.log(f'after {_r}')
