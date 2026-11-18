from math import (
    acos as _acos,
    asin as _asin,
    atan as _atan,
    atan2 as _atan2,
    ceil as _ceil,
    cos as _cos,
    cosh as _cosh,
    e as _e_,
    exp as _exp,
    expm1 as _expm1,
    floor as _floor,
    log as _log,
    log10 as _log10,
    log1p as _log1p,
    pi as _pi_,
    sin as _sin,
    sinh as _sinh,
    sqrt as _sqrt,
    tan as _tan,
    tanh as _tanh,
)
from temper_core import (
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    float64_div as _float64_div,
    float64_max as _float64_max,
    float64_min as _float64_min,
    float64_mod as _float64_mod,
    float64_near as _float64_near,
    float64_sign as _float64_sign,
    float64_to_string as _float64_to_string,
    list_join as _list_join,
)

__all__ = ()


def _near(label: str, actual: float, expected: float, /) -> None:
    subject = _float64_near(actual, expected)
    result = '✅' if subject == True else f'❌ wanted {_float64_to_string(expected)}, was {_float64_to_string(actual)}'
    _console.log(f'{label}: {result}')


_console: _LoggingConsole = _LoggingConsole(__name__)
_one: float = 1.0
_one = _one
_two: float = _one + _one
_half: float = _float64_div(_one, _two)
_near('half.abs()', abs(_half), _half)
_near('(-half).abs()', abs(-_half), _half)
_three: float = _one + _two
_near('half.acos()', _acos(_half), _float64_div(_pi_, _three))
_pi: float = _pi_
_six: float = _two * _three
_near('half.asin()', _asin(_half), _float64_div(_pi, _six))
_four: float = _two * _two
_near('(-one).atan()', _atan(-_one), _float64_div(-_pi, _four))
_near('one.atan2(-one)', _atan2(_one, -_one), _float64_div(_three * _pi, _four))
_near('(four / three).ceil()', _ceil(_float64_div(_four, _three)), _two)
_near('(-four / three).ceil()', _ceil(_float64_div(-_four, _three)), -_one)
_near('(pi / three).cos()', _cos(_float64_div(_pi, _three)), _half)
_e: float = _e_
_near('one.cosh()', _cosh(_one), _float64_div(_e + _float64_div(_one, _e), _two))
_near('half.exp() == e.sqrt()', _exp(_half), _sqrt(_e))
_near('half.expm1()', _expm1(_half), _sqrt(_e) - _one)
_near('(four / three).floor()', _floor(_float64_div(_four, _three)), _one)
_near('(-four / three).floor()', _floor(_float64_div(-_four, _three)), -_two)
_near('e.log()', _log(_e_), _one)
_five: float = _one + _four
_ten: float = _two * _five
_near('ten.log10()', _log10(_ten), _one)
_near('(e - one).log1p()', _log1p(_e - _one), _one)
_t_0 = (float('nan'), _one)


def _fn(x: float, /) -> str:
    t_0 = (float('nan'), _two)
    def fn2(y: float, /) -> str:
        pair = _list_join((_float64_max(x, y), _float64_min(x, y)), ', ', lambda z: _float64_to_string(z))
        return f'({pair})'
    return _list_join(t_0, ', ', fn2)


_max_min_combos: str = _list_join(_t_0, ', ', _fn)
_console.log(f'maxMinCombos: {_max_min_combos}')
_quarter: float = _float64_div(_half, _two)
_near('2.25 % 2.0', _float64_mod(_two + _quarter, _two), 0.25)
_near('2.25 % -2.0', _float64_mod(_two + _quarter, -_two), 0.25)
_near('-2.25 % 2.0', _float64_mod(-(_two + _quarter), _two), -0.25)
_near('-2.25 % -2.0', _float64_mod(-(_two + _quarter), -_two), -0.25)
_near('pi.round()', round(_pi), _three)
_near('(-pi).round()', round(-_pi), -_three)
_near('pi.sign()', _float64_sign(_pi), _one)
_near('(-pi).sign()', _float64_sign(-_pi), -_one)
_zero: float = _one - _one
_console.log(f'zero.sign(): {_float64_to_string(_float64_sign(_zero))}')
_console.log(f'(-zero).sign(): {_float64_to_string(_float64_sign(-_zero))}')
_near('(pi / six).sin()', _sin(_float64_div(_pi, _six)), _half)
_near('one.sinh()', _sinh(_one), _float64_div(_e - _float64_div(_one, _e), _two))
_near('(-pi / four).tan()', _tan(_float64_div(-_pi, _four)), -_one)
_near('(three * pi / four).tan()', _tan(_float64_div(_three * _pi, _four)), -_one)
_near('one.tanh()', _tanh(_one), _float64_div(_e ** 2.0 - 1.0, _e ** 2.0 + 1.0))
_console.log(f'one.near(one + 0.1): {_boolean_to_string(_float64_near(_one, _one + 0.1))}')
_console.log(f'one.near(one + 0.25, absTol = 0.25): {_boolean_to_string(_float64_near(_one, _one + 0.25, None, 0.25))}')
_console.log(f'one.near(one + 0.1 + 1e-9, absTol = 0.1): {_boolean_to_string(_float64_near(_one, _one + 0.1 + 1e-9, None, 0.1))}')
_console.log(f'one.near(one - 0.1, absTol = 0.11): {_boolean_to_string(_float64_near(_one, _one - 0.1, None, 0.11))}')
_console.log(f'ten.near(ten + 0.1, absTol = 0.011): {_boolean_to_string(_float64_near(_ten, _ten + 0.1, None, 0.011))}')
_console.log(f'ten.near(ten + 0.1, relTol = 0.011): {_boolean_to_string(_float64_near(_ten, _ten + 0.1, 0.011))}')
