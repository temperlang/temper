from count_failures import parse_count as _parse_count
from temper_core import (
    Bubble as _Bubble,
    Failure as _Failure,
    LoggingConsole as _LoggingConsole,
    failure_cause as _failure_cause,
    int_add as _int_add,
    int_div as _int_div,
)

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


class HostFailuresError(_Failure, Exception):
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


def _total(key: str, count: str, /) -> int:
    try:
        return _int_add(host_lookup(key), _parse_count(count))
    except Exception as e:
        if not isinstance(e, _Failure):
            raise e
        _console.log(f'caught {e.message}')
        return -1 if isinstance(e, NotFound) else -2


class _Unreadable(HostFailuresError):
    __slots__ = ('_key',)
    _key: str

    def __init__(self, /, key: str) -> None:
        self._key = key

    @property
    def key(self, /) -> str:
        return self._key

    @property
    def message(self, /) -> str:
        return f'unreadable (key: {self._key})'


def _reading(key: str, count: str, /) -> int:
    try:
        return _int_add(host_lookup(key), _parse_count(count))
    except Exception as failure:
        if not isinstance(failure, _Failure):
            raise failure
        raise _Unreadable(key) from failure


def _nonzero(n: int, /) -> int:
    if n == 0:
        raise _Bubble()
    else:
        return n


def _ratio(key: str, count: str, /) -> int:
    try:
        return _int_div(host_lookup(key), _nonzero(_parse_count(count)))
    except Exception as t_0:
        if not isinstance(t_0, (_Bubble, _Failure)):
            raise t_0
        return -9


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('before')
_console.log(f"{_total('a', 'two')} {_total('z', 'two')} {_total('a', 'x')}")
_r: int
try:
    _r = _reading('a', 'x')
except _Unreadable as _e:
    _console.log(f"{_e.message}, caused by {'nothing' if (_subject := None if (_subject2 := _failure_cause(_e)) is None else _subject2.message) is None else _subject}")
    _r = 0
_console.log(f"{_r} {_ratio('a', 'two')} {_ratio('a', 'zero')} {_ratio('a', 'x')} {_ratio('z', 'two')}")
_parse_count('x')
_console.log('after')
