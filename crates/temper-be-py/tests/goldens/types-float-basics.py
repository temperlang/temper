from temper_core import (
    LoggingConsole as _LoggingConsole,
    ParseError as _ParseError,
    boolean_to_string as _boolean_to_string,
    float64_div as _float64_div,
    float64_to_int as _float64_to_int,
    float64_to_int64 as _float64_to_int64,
    float64_to_int64_unsafe as _float64_to_int64_unsafe,
    float64_to_int_unsafe as _float64_to_int_unsafe,
    float64_to_string as _float64_to_string,
    float_cmp as _float_cmp,
    float_eq as _float_eq,
    int64_mul as _int64_mul,
    int64_sub as _int64_sub,
    int64_to_float64 as _int64_to_float64,
    string_to_float64 as _string_to_float64,
)

__all__ = ()


def _gt(a: float, b: float, /) -> bool:
    return _float_cmp(a, b) > 0


def _lt(a: float, b: float, /) -> bool:
    return _float_cmp(a, b) < 0


def _eq(a: float, b: float, /) -> bool:
    return _float_eq(a, b)


def _neq(a: float, b: float, /) -> bool:
    return not _float_eq(a, b)


def _add(a: float, b: float, /) -> float:
    return a + b


def _check_parse(string: str, /) -> None:
    try:
        converted = _float64_to_string(_string_to_float64(string))
    except _ParseError:
        converted = 'failed'
    _console.log(f'Parse {string} -> {converted}')


def _check_tiny(string: str, /) -> None:
    try:
        positive = _boolean_to_string(_float_cmp(_string_to_float64(string), 0.0) > 0)
    except _ParseError:
        positive = 'failed'
    _console.log(f'Parse {string} > 0 -> {positive}')


_console: _LoggingConsole = _LoggingConsole(__name__)
_negative_zero: float = -0.0
_zero: float = 0.0
_zero = _zero
_one: float = 1.0
_one = _one
_two: float = 2.0
_two = _two
_three: float = 3.0
_three = _three
if _gt(_one, _zero):
    _console.log('greater than')
if _lt(_zero, _one):
    _console.log('less than')
if _eq(_zero, _one):
    _console.log('equality is wrong')
if _neq(_zero, _one):
    _console.log('inequality')
_console.log(_float64_to_string(_zero))
if _neq(_negative_zero, _zero):
    _console.log('zeroes not equal')
if _lt(_negative_zero, _zero):
    _console.log('negZero < zero')
if _float_eq(_zero - _zero, _zero):
    _console.log('zero subtraction')
if _float_eq(_zero + _zero, _zero):
    _console.log('zero addition')
if _float_eq(_negative_zero - _zero, _negative_zero):
    _console.log('negative zero minus zero is negative')
if _float_eq(_negative_zero + _zero, _zero):
    _console.log('negative zero plus zero is positive')
if _float_eq(_zero - _negative_zero, _zero):
    _console.log('zero minus negative zero is zero')
if _float_eq(_zero + _negative_zero, _zero):
    _console.log('zero plus negative zero is zero')
if _float_eq(_negative_zero - _negative_zero, _zero):
    _console.log('negative zero minus negative zero is zero')
if _float_eq(_negative_zero + _negative_zero, _negative_zero):
    _console.log('negative zero plus negative zero is negative zero')
_console.log(_float64_to_string(_negative_zero))
_third: float = _float64_div(_one, _three)
_third = _third
_half: float = _float64_div(_one, _two)
_half = _half
_two_thirds: float = _float64_div(_two, _three)
_two_thirds = _two_thirds
if _gt(_third, _half):
    _console.log('oops third > half')
if _gt(_half, _two_thirds):
    _console.log('oops half > twoThirds')
if _neq(_third * _two, _two_thirds):
    _console.log('misaligned multiplication')
if _neq(_add(_third, _two_thirds), _one):
    _console.log("doesn't add back up")
_console.log(_float64_to_string(_half))
_console.log(_float64_to_string(4.0 ** -_half))
_console.log(_float64_to_string(_third))
_console.log(_float64_to_string(_two_thirds))
_console.log(_float64_to_string(_one))
_almost_big: float = 1e25 * _one
_console.log(_float64_to_string(_almost_big))
_big: float = 99999999999999.0 * 999999999999.0
_big = _big
_console.log(_float64_to_string(_big))
_bigger: float = _big * _big * _big * _big * _big * _big * _big * _big
_bigger = _bigger
_console.log(_float64_to_string(_bigger))
_middling: float = -1500.0 * _one
_console.log(f'{_float64_to_string(_middling)} {_float64_to_string(1000000.0 * _one)} {_float64_to_string(9999999.0 * _one)} {_float64_to_string(0.001 * _one)}')
_console.log(f'{_float64_to_string(1e21 * _one)} {_float64_to_string(1.5e-10 * _one)} {_float64_to_string(2e23 * _one)}')
_biggest: float = _bigger * _bigger * _bigger * _bigger * _bigger * _bigger
_biggest = _biggest
_console.log(f'biggest {_float64_to_string(_biggest)}')
_tiniest: float = _float64_div(_one, _biggest)
_tiniest = _tiniest
_console.log(f'tiniest {_float64_to_string(_tiniest)}')
_negative_infinity: float = _biggest * -1.0
_negative_infinity = _negative_infinity
_console.log(f'negative infinity {_float64_to_string(_negative_infinity)}')
_infinity: float = float('inf')
_infinity = _infinity
_console.log(_float64_to_string(_infinity))
_console.log('processed an infinity')
_nan: float = float('nan')
_nan = _nan
_console.log(_float64_to_string(_nan))
if _float_cmp(_nan, _infinity) > 0:
    _console.log('NaN is biggest')
else:
    _console.log('Infinity is biggest')
_console.log(f"NaN == NaN? {_boolean_to_string(_float_eq(_nan, float('nan')))}")
_console.log(f'Int to float: {_float64_to_string(float(_float64_to_int_unsafe(_two)))}')
_console.log(f'Positive to int: {_float64_to_int_unsafe(4.9 * _one)}')
_console.log(f'Negative to int: {_float64_to_int_unsafe(-4.9 * _one)}')
_t_0: str
try:
    _t_0 = str(_float64_to_int(_infinity))
except _ParseError:
    _t_0 = 'no'
_console.log(f'Infinity to int: {_t_0}')
_t_1: str
try:
    _t_1 = str(_float64_to_int(float('nan')))
except _ParseError:
    _t_1 = 'no'
_console.log(f'NaN to int: {_t_1}')
_one_int64: int = _float64_to_int64_unsafe(_one)
_not_so_big: int = _int64_mul(2147483647, _one_int64)
_not_so_small: int = _int64_mul(-2147483648, _one_int64)
_slightly_bigger: int = _float64_to_int64_unsafe(float(_not_so_big) + 1.5)
_slightly_smaller: int = _float64_to_int64_unsafe(float(_not_so_small) - 1.5)
_too_big_for_float64: int = _int64_mul(9007199254740992, _one_int64)
_ok_to_convert64: float = float(_int64_sub(_too_big_for_float64, 1))
_sneaky_big: float = _ok_to_convert64 + 1.0
_console.log(f'Bigger to Int64: {str(_slightly_bigger)}')
_console.log(f'Smaller to Int64: {str(_slightly_smaller)}')
_console.log(f'Barely ok to Int64: {str(_float64_to_int64_unsafe(_ok_to_convert64))}')
_t_2: str
try:
    _t_2 = str(_float64_to_int64(_sneaky_big))
except _ParseError:
    _t_2 = 'no'
_console.log(f'Sneaky big to Int64: {_t_2}')
_t_3: str
try:
    _t_3 = _float64_to_string(_int64_to_float64(_too_big_for_float64))
except _ParseError:
    _t_3 = 'no'
_console.log(f'Too big Int64 to Float64: {_t_3}')
_t_4: str
try:
    _t_4 = str(_float64_to_int64(_infinity))
except _ParseError:
    _t_4 = 'no'
_console.log(f'Infinity to Int64: {_t_4}')
_t_5: str
try:
    _t_5 = str(_float64_to_int64(float('nan')))
except _ParseError:
    _t_5 = 'no'
_console.log(f'NaN to Int64: {_t_5}')
_check_parse('-1.5')
_check_parse('-0')
_check_parse('2.0')
_check_parse('2')
_check_parse(' 2 ')
_check_parse('5e-1')
_check_parse('NaN')
_check_parse('-Infinity')
_check_tiny('2.225e-308')
_check_tiny('5e-324')
_check_parse('1e-400')
_check_parse('-1e-400')
_check_parse('2.')
_check_parse('.2')
_check_parse('2.0.0')
_check_parse('-inf')
_check_parse('totally')
