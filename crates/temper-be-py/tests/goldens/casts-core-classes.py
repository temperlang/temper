from collections import deque as _deque
from collections.abc import Sequence as _Sequence
from temper_core import (
    BitVector as _BitVector,
    LoggingConsole as _LoggingConsole,
    Pair as _Pair,
    boolean_to_string as _boolean_to_string,
    cast_by_type as _cast_by_type,
    map_constructor as _map_constructor,
)

__all__ = ()


class _Thing:
    __slots__ = ()


def _describe(x: object, /) -> str:
    if isinstance(x, _Thing):
        return 'thing'
    try:
        _cast_by_type(x, _Thing)
        return 'thing, as'
    except Exception:
        return 'other'


def _is_thing(x: object, /) -> bool:
    return isinstance(x, _Thing)


_console: _LoggingConsole = _LoggingConsole(__name__)
_bits: _BitVector = _BitVector(8)
_ints: _Sequence[int] = (1, 2)
_console.log(_describe(_Thing()))
_console.log(_describe(_Pair('a', 1)))
_console.log(_describe(_bits))
_console.log(_describe('s'))
_console.log(_describe(1))
_console.log(_describe(_ints))
_console.log(_describe(_map_constructor((_Pair('m', 2),))))
_console.log(_describe([]))
_console.log(_describe(_deque()))
_console.log(_boolean_to_string(_is_thing(_Thing())))
_console.log(_boolean_to_string(_is_thing(_bits)))
