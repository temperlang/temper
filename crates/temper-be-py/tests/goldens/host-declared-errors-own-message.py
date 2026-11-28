from temper_core import Failure as _Failure, LoggingConsole as _LoggingConsole

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


class _Expired(HostDeclaredErrorsError):
    __slots__ = ('_days',)
    _days: int

    def __init__(self, /, days: int) -> None:
        self._days = days

    @property
    def days(self, /) -> int:
        return self._days

    @property
    def message(self, /) -> str:
        return f'expired {self._days} days ago'


def _check(days: int, /) -> None:
    if days > 0:
        raise _Expired(days)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('before')
_check(0)
_check(3)
_console.log('after')
