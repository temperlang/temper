from temper_core import (
    LoggingConsole as _LoggingConsole,
    int_add as _int_add,
    int_mul as _int_mul,
    string_count_between as _string_count_between,
)

__all__ = ('sum_', 'length', 'twice', 'Hider')


class _connected:
    from ._support import Support

    def sum(i: int, j: int, bonus: int) -> int:
        # Separately, here's an example of where overriding name choices might
        # sometimes be handy.
        return _sum_of3(i, j, bonus)

    def prod(hidden: "_Hidden", j: int) -> int:
        return _connected.Support().prod(hidden.i, j)

    def length(string: str | None) -> int:
        return -1 if string is None else len(string)

    def twice(i: int) -> int:
        return i * 2


def _sum_of3(a: int, b: int, c: int, /) -> int:
    return _int_add(_int_add(a, b), c)


def sum_(i: int, j: int, bonus: int | None = None, /) -> int:
    if bonus is None:
        bonus = 0
    return _int_add(_sum_of3(i, j, bonus), 1000)


def length(string: str | None = None, /) -> int:
    if string is None:
        return 999
    else:
        return _int_add(_string_count_between(string, 0, len(string)), 1000)


def twice(i: int, /) -> int:
    return _int_add(_int_mul(i, 2), 1000)


class _Hidden:
    __slots__ = ('_i',)
    _i: int

    def __init__(self, /, i: int) -> None:
        self._i = i

    @property
    def i(self, /) -> int:
        return self._i


def _prod(hidden: _Hidden, j: int, /) -> int:
    return _connected.prod(hidden, j)


class Hider:
    __slots__ = ('_hidden',)
    _hidden: _Hidden

    def __init__(self, /, i: int) -> None:
        self._hidden = _Hidden(i)

    def plus(self, j: int, /) -> int:
        return sum_(self._hidden.i, j)

    def times(self, j: int, /) -> int:
        return _prod(self._hidden, j)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log(f'sum(1, 2): {sum_(1, 2)}')
_console.log(f"length \"\" vs null vs (): {length('')} vs {length(None)} vs {length()}")
_console.log(f'twice(5): {twice(5)}')
_is_connected: bool = sum_(1, 2) == 3
if _is_connected:
    _console.log(f'prod(new Hidden(1), 2): {_prod(_Hidden(1), 2)}')
_hider: Hider = Hider(3)
_console.log(f'new Hider(3).plus(4): {_hider.plus(4)}')
if _is_connected:
    _console.log(f'new Hider(3).times(4): {_hider.times(4)}')
