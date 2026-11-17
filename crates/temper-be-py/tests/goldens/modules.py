import modules.helpers as _m_modules_helpers
from modules.core import (
    Ratio as _Ratio,
    ratio_of as _ratio_of,
    share as _share,
)
from modules.helpers import (
    base as _base_,
    bump as _bump,
    int_doubled as _int_doubled,
    twice as _twice,
)
from modules.helpers.deep import greet as _greet
from modules.machine import describe as _describe
from temper_core import (
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    int_add as _int_add,
)

__all__ = ('sum_',)


class _Engine:
    __slots__ = ('_name',)
    _name: str

    def __init__(self, /, name: str) -> None:
        self._name = name

    @property
    def name(self, /) -> str:
        return self._name


def sum_(n: int, /) -> int:
    return _int_add(_int_add(_twice(n), _base_), _base)


_console: _LoggingConsole = _LoggingConsole(__name__)
_calls: int = _m_modules_helpers.calls
_base: int = 1
_console.log(_greet('modules'))
_console.log(f'sum {sum_(3)}')
_console.log(f'share {_share(7, 2)} {_int_doubled(21)}')
_console.log(f"{_Engine('own').name}, {_describe()}")
_bump()
_bump()
_console.log(f'calls {_calls}')
_console.log(f'calls now {_bump()}')
_half: _Ratio = _Ratio(1, 2)
_t_1 = _boolean_to_string(_half == _Ratio(2, 4))
_t_0 = _boolean_to_string(False if (_left := _ratio_of(3, 6)) is None else _left == _half)
_left2 = _half
_right: _Ratio | None = _ratio_of(1, 0)
_console.log(f'{_t_1} {_t_0} {_boolean_to_string(not (False if _right is None else _left2 == _right))}')
