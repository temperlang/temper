from collections.abc import Callable as _Callable, Sequence as _Sequence
from temper_core import (
    LoggingConsole as _LoggingConsole,
    arith_int_mod as _arith_int_mod,
    boolean_to_string as _boolean_to_string,
    int_add as _int_add,
    listed_reduce as _listed_reduce,
    listed_reduce_from as _listed_reduce_from,
)
from typing import TypeVar as _TypeVar

__all__ = ()

_T = _TypeVar('_T')


def _all(items: _Sequence[_T], check: _Callable[[_T], bool], /) -> bool:
    def fn(result: bool, it: _T, /) -> bool:
        if result:
            return check(it)
        else:
            return False
    return _listed_reduce_from(items, True, fn)


_console: _LoggingConsole = _LoggingConsole(__name__)
_vals: _Sequence[int] = (2, 3, 4)


def _fn(sum: int, n: int, /) -> int:
    return _int_add(sum, n)


_sum: int = _listed_reduce(_vals, _fn)
_poor_reverse_join: str = _listed_reduce_from(_vals, '', lambda text, n: str(n) + text)
_console.log(f'Reductions: {_sum} {_poor_reverse_join}')
_console.log(f'All even: {_boolean_to_string(_all(_vals, lambda n: _arith_int_mod(n, 2) == 0))}')
_console.log(f'All positive: {_boolean_to_string(_all(_vals, lambda n: n > 0))}')
