from temper_core import Failure as _Failure, LoggingConsole as _LoggingConsole

__all__ = ('HostFailuresCauseError', 'NotFound', 'host_lookup')


# A specification: `NotFound` is the library's own failure class, in the
# module this code joins, as the Python backend names it.
class _connected:
    def host_lookup(key: str) -> int:
        if key == "bug":
            raise RuntimeError("host bug")
        if key == "a":
            return 1
        raise NotFound(key)  # noqa: F821


class HostFailuresCauseError(_Failure, Exception):
    __slots__ = ()


class NotFound(HostFailuresCauseError):
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


class _LookupFailed(HostFailuresCauseError):
    __slots__ = ('_name',)
    _name: str

    def __init__(self, /, name: str) -> None:
        self._name = name

    @property
    def name(self, /) -> str:
        return self._name

    @property
    def message(self, /) -> str:
        return f'lookup failed (name: {self._name})'


def _setting(name: str, /) -> int:
    try:
        return host_lookup(name)
    except NotFound as failure:
        raise _LookupFailed(name) from failure


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('before')
_setting('port')
_console.log('after')
