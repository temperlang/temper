from collections.abc import Sequence as _Sequence
from temper_core import (
    Failure as _Failure,
    LoggingConsole as _LoggingConsole,
    int_add as _int_add,
    list_get as _list_get,
    list_join as _list_join,
)

__all__ = ()


class _FailuresError(_Failure):
    __slots__ = ()


class _NotFound(_FailuresError):
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


def _lookup(key: str, /) -> int:
    if key == 'a':
        return 1
    if key == 'b':
        return 2
    raise _NotFound(key)


def _sum(a: str, b: str, /) -> int:
    x = _lookup(a)
    y = _lookup(b)
    return _int_add(x, y)


def _shown(a: str, b: str, /) -> str:
    total: int
    caught: _NotFound | None = None
    held: int | None = None
    try:
        held = _sum(a, b)
    except _NotFound as failure:
        caught = failure
    if held is None:  # pyright: ignore[reportUnnecessaryComparison]
        assert caught is not None
        e = caught
        return f'no {e.key}'
    else:
        total = held
    return str(total)


class _SignError(_Failure):
    __slots__ = ()


class _EmptyText(_SignError):
    __slots__ = ()

    @property
    def message(self, /) -> str:
        return 'empty text'


class _BadSign(_SignError):
    __slots__ = ('_text',)
    _text: str

    def __init__(self, /, text: str) -> None:
        self._text = text

    @property
    def text(self, /) -> str:
        return self._text

    @property
    def message(self, /) -> str:
        return f'bad sign (text: {self._text})'


def _sign(text: str, /) -> int:
    if text == '+':
        return 1
    elif text == '-':
        return -1
    else:
        raise _BadSign(text)


def _parse_sign(text: str, /) -> int:
    if text == '':
        raise _EmptyText()
    return _sign(text)


def _describe(e: _SignError, /) -> str:
    if isinstance(e, _EmptyText):
        return 'empty'
    elif isinstance(e, _BadSign):
        return f'bad sign {e.text}'
    else:
        raise BaseException('panic')


def _rethrown(p: _NotFound, /) -> int:
    raise p


class _ConfigError(_FailuresError):
    __slots__ = ('_setting',)
    _setting: str

    def __init__(self, /, setting: str) -> None:
        self._setting = setting

    @property
    def setting(self, /) -> str:
        return self._setting

    @property
    def message(self, /) -> str:
        return f'config error (setting: {self._setting})'


def _setting(name: str, /) -> int:
    try:
        return _lookup(name)
    except _NotFound as e:
        raise _ConfigError(f'{name}: missing {e.key}') from e


def _pair(a: str, b: str, /) -> str:
    try:
        x = _lookup(a)
        try:
            y = _lookup(b)
        except _NotFound:
            y = 0
        return f'{x}+{y}'
    except _NotFound as e:
        return f'outer {e.key}'


_console: _LoggingConsole = _LoggingConsole(__name__)
_found: int
try:
    _found = _lookup('a')
except _NotFound:
    _found = -1
_missing: int
try:
    _missing = _lookup('z')
except _NotFound:
    _missing = -1
_console.log(f'{_found} {_missing}')
_n: int
try:
    _n = _lookup('q')
except _NotFound as _e:
    _console.log(f'not found: {_e.key}')
    _n = 0
_m: int
try:
    _m = _lookup('b')
except _NotFound:
    _m = -1
_console.log(f'{_n} {_m}')
_console.log(f"{_shown('a', 'b')} {_shown('a', 'x')} {_shown('y', 'z')}")
_list = ('+', '-', '', 'x')
_length = len(_list)
_i = 0
while _i < _length:
    _t = _list_get(_list, _i)
    _i = _int_add(_i, 1)
    _s: int
    _caught: _SignError | None = None
    _held: int | None = None
    try:
        _held = _parse_sign(_t)
    except _SignError as _failure:
        _caught = _failure
    if _held is None:  # pyright: ignore[reportUnnecessaryComparison]
        assert _caught is not None
        _e2 = _caught
        _console.log(_describe(_e2))
        continue
    else:
        _s = _held
    _console.log(str(_s))
_code: int
try:
    _code = _parse_sign('?')
except _SignError as _e3:
    _t_1: int
    if isinstance(_e3, _EmptyText):
        _t_1 = 0
    else:
        _t_0: int
        if isinstance(_e3, _BadSign):
            _t_0 = 9
        else:
            raise BaseException('panic')
        _t_1 = _t_0
    _code = _t_1
_console.log(str(_code))
_kept: _NotFound = _NotFound('none')
_v: int
try:
    _v = _lookup('k')
except _NotFound as _e4:
    _kept = _e4
    _v = -1
_problems: _Sequence[_NotFound] = (_NotFound('x'), _kept)
_console.log(f"{_v} {_list_join(_problems, ',', lambda p: p.key)}")
_w: int
try:
    _w = _rethrown(_kept)
except _NotFound as _e5:
    _console.log(f'again {_e5.key}')
    _w = 0
_port: int
try:
    _port = _setting('port')
except _ConfigError as _e6:
    _console.log(_e6.setting)
    _port = -1
_first: int
try:
    _first = _setting('a')
except _ConfigError:
    _first = -1
_console.log(f'{_port} {_first}')
_console.log(f"{_pair('a', 'z')} {_pair('z', 'a')}")
_total: int = 0
_list2 = ('a', 'b', 'c', 'a')
_length2 = len(_list2)
_i2 = 0
while _i2 < _length2:
    _k = _list_get(_list2, _i2)
    _i2 = _int_add(_i2, 1)
    _t2: int
    _held2: int | None = None
    try:
        _held2 = _lookup(_k)
    except _NotFound:
        pass
    if _held2 is None:  # pyright: ignore[reportUnnecessaryComparison]
        break
    else:
        _t2 = _held2
    _total = _int_add(_total, _t2)
_console.log(str(_total))
