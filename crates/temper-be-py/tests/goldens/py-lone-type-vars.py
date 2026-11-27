from collections.abc import Sequence as _Sequence
from temper_core import LoggingConsole as _LoggingConsole, Pair as _Pair
from typing import TypeVar as _TypeVar

__all__ = ('empty_of', 'nothing', 'failing', 'Maker')

_T = _TypeVar('_T')
_A = _TypeVar('_A')
_B = _TypeVar('_B')


def empty_of() -> _Sequence[_T]:  # pyright: ignore[reportInvalidTypeVarUse]
    return ()


def nothing() -> _T | None:  # pyright: ignore[reportInvalidTypeVarUse]
    return None


def failing() -> _T:  # type: ignore[type-var]
    raise RuntimeError()


def _halved(a: _A, /) -> _Pair[_A, _Sequence[_B]]:  # pyright: ignore[reportInvalidTypeVarUse]
    return _Pair(a, ())


class Maker:
    __slots__ = ()

    def pair_of(self, n: int, /) -> _Pair[int, _Sequence[_B]]:  # pyright: ignore[reportInvalidTypeVarUse]
        return _Pair(n, ())

    @staticmethod
    def none() -> _Sequence[_B]:  # pyright: ignore[reportInvalidTypeVarUse]
        return ()


_console: _LoggingConsole = _LoggingConsole(__name__)
_ints: _Sequence[int] = empty_of()
_none: str | None = nothing()
_half: _Pair[int, _Sequence[str]] = _halved(2)
_pair: _Pair[int, _Sequence[str]] = Maker().pair_of(3)
_made: _Sequence[int] = Maker.none()
_t_2 = len(_ints)
_t_1 = '-' if _none is None else _none
_t_0: int
try:
    _t_0 = failing()
except Exception:
    _t_0 = 4
_console.log(f'{_t_2} {_t_1} {_t_0}')
_console.log(f'{_half.key} {_pair.key} {len(_pair.value)} {len(_made)}')
