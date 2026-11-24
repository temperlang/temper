from collections.abc import Sequence as _Sequence
from temper_core import (
    LoggingConsole as _LoggingConsole,
    int_add as _int_add,
    listed_reduce as _listed_reduce,
)

__all__ = ()


def _sum(values: _Sequence[int], /) -> int:
    def fn(a: int, b: int, /) -> int:
        return _int_add(a, b)
    return _listed_reduce(values, fn)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log(str(_sum((1, 2, 3))))
_console.log('before')
_console.log(str(_sum(())))
_console.log('not reached')
