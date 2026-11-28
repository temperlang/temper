from count_errors import parse_count as _parse_count
from temper_core import (
    Failure as _Failure,
    LoggingConsole as _LoggingConsole,
    int_add as _int_add,
    int_div as _int_div,
    int_sub as _int_sub,
)

__all__ = ('HostDeclaredErrorsError', 'NotFound', 'host_lookup')


# A specification: `NotFound` is the library's own error class, in the
# module this code joins, as the Python backend names it.
class _connected:
    def host_lookup(key: str) -> int:
        if key == "bug":
            raise RuntimeError("host bug")
        if key == "a":
            return 1
        raise NotFound(key)  # noqa: F821


class HostDeclaredErrorsError(_Failure):
    __slots__ = ()


class NotFound(HostDeclaredErrorsError):
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
_p: int
try:
    _n = _parse_count('two')
    _m = host_lookup('a')
    if _int_add(_n, _m) > 2:
        raise BaseException('panic')
    _p = _int_div(10, _int_sub(_n, _m))
except _Failure:
    _p = -1
_console.log(f'after {_p}')
