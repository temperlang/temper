from temper_core import (
    LoggingConsole as _LoggingConsole,
    int_add as _int_add,
    int_mul as _int_mul,
)

__all__ = ('good',)


def good(x: int, /) -> int:
    return _int_add(x, 1)


def _mismatched(x: int, /) -> int:
    s = x  # pyright: ignore[reportUnusedVariable]
    return 1


def _undeclared() -> int:
    raise RuntimeError()


def _misspelled(s: str, /) -> int:
    raise RuntimeError()


def _misspelled_method(s: str, /) -> int:
    raise RuntimeError()


def _wrong_arity() -> int:
    raise RuntimeError()


def _unknown_type(x: object, /) -> int:
    return 1


def _after_unclosed(x: int, /) -> int:
    return _int_mul(x, 3)


def _dropped(cents: int, /) -> str:
    raise RuntimeError()


def _stored(x: int, /) -> str:
    s = x
    return f'{s}!'


def _returned() -> int:
    return 'text'


def _passed_on(x: int, /) -> int:
    return x


class _Box:
    __slots__ = ('_value',)
    _value: int

    def __init__(self, /, value: int) -> None:
        self._value = value

    @property
    def value(self, /) -> int:
        return self._value

    def get(self, /) -> int:
        return self._value

    def bad(self, /) -> str:
        return self._value


_console: _LoggingConsole = _LoggingConsole(__name__)
_a: int
try:
    _a = _mismatched(1)
except Exception:
    _a = -1
_b: int
try:
    _b = _undeclared()
except Exception:
    _b = -1
_c: int
try:
    _c = _misspelled('a')
except Exception:
    _c = -1
_d: int
try:
    _d = _misspelled_method('a')
except Exception:
    _d = -1
_e: int
try:
    _e = _wrong_arity()
except Exception:
    _e = -1
_box: _Box = _Box(3)
_f: str
try:
    _f = _box.bad()
except Exception:
    _f = 'caught'
_console.log(f'{good(1)} {_a} {_b} {_c} {_d} {_e}')
_console.log(f'{_unknown_type(1)} {_after_unclosed(2)} {_box.get()} {_f}')
_g: str
try:
    _g = _stored(1)
except Exception:
    _g = 'caught'
_h: int
try:
    _h = _returned()
except Exception:
    _h = -1
_i: int
try:
    _i = _passed_on('in')
except Exception:
    _i = -1
_j: str
try:
    _j = _dropped(1)
except Exception:
    _j = 'dropped'
_console.log(f'{_g} {_h} {_i} {_j}')
