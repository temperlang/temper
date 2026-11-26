from modules import sum_ as _sum_
from modules.core import Ratio as _Ratio, ratio_of as _ratio_of
from modules.helpers import base as _base, twice as _twice
from modules.state import Spot as _Spot
from renamed import (
    Builtins as _Builtins,
    Checked as _Checked,
    lambda_ as _lambda_,
    native as _native,
)
from temper_core import (
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    int_add as _int_add,
)

__all__ = ('both',)


def both(n: int, /) -> int:
    return _int_add(_int_add(_sum_(n), _twice(n)), _base)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log(f'both {both(2)}')
_third: _Ratio = _Ratio(1, 3)
_console.log(f'ratio {_boolean_to_string(_third == _Ratio(3, 9))} {_boolean_to_string(False if (_left := _ratio_of(2, 6)) is None else _left == _third)} {_boolean_to_string(False if (_left2 := _ratio_of(1, 0)) is None else _left2 == _third)}')
_checked: _Checked = _Checked(_lambda_(_native))
_console.log(f'renamed {_checked.delete()} {_checked.local} {_checked.end}')
_b: _Builtins = _Builtins(4)
_console.log(f'builtins {_b.hash} {_b.get(1)} {_b.set(3)} {_b.map(5)} {_b.format()} {_b.int_} {_b.later()}')
_console.log(_b.print())
_spread = _Spot(3, 4)
_far: _Spot = _Spot(9, _spread.y)
_console.log(f'spot far {_far.x} {_far.y}')
