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


def _misspelled(s: str, /) -> int:
    raise BaseException('panic')


def _unknown_type(x: object, /) -> int:
    return 1


def _after_unclosed(x: int, /) -> int:
    return _int_mul(x, 3)


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
_box: _Box = _Box(3)
_console.log(f'{good(1)} {_unknown_type(1)} {_after_unclosed(2)} {_box.get()}')
_console.log(f"{_mismatched(1)} {_stored(1)} {_returned()} {_passed_on('in')} {_box.bad()}")
_misspelled('a')
_console.log('after')
