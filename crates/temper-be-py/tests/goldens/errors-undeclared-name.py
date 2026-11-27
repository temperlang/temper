from temper_core import (
    LoggingConsole as _LoggingConsole,
    int_add as _int_add,
    int_mul as _int_mul,
)

__all__ = ('good',)


def good(x: int, /) -> int:
    return _int_add(x, 1)


def _undeclared() -> int:
    raise BaseException('panic')


def _unknown_type(x: object, /) -> int:
    return 1


def _after_unclosed(x: int, /) -> int:
    return _int_mul(x, 3)


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
_undeclared()
_console.log('after')
