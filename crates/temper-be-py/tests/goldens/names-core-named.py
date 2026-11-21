from core_named.core import greet as _greet, share as _share
from temper_core import (
    LoggingConsole as _LoggingConsole,
    int_add as _int_add,
    int_mul as _int_mul,
)

__all__ = ()


class _String:
    __slots__ = ('_n',)
    _n: int

    def __init__(self, /, n: int) -> None:
        self._n = n

    @property
    def n(self, /) -> int:
        return self._n


def _twice(core: int, /) -> int:
    return _int_mul(core, 2)


_console: _LoggingConsole = _LoggingConsole(__name__)
_core: str = _greet('a module named core')
_console.log(_core)
_console.log(f'shares {_share(7, 2)} {_share(1, 0)}')
_builtins: int = 1
_builtins = _int_add(_builtins, 1)
_console.log(f'builtins is {_builtins}')
_s: str = "core's String"
_mine: _String = _String(3)
_console.log(f'{_s}, and mine holds {_mine.n}')
_console.log(str(_twice(21)))
