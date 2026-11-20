from temper_core import (
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    float64_near as _float64_near,
    int64_add as _int64_add,
    int64_mul as _int64_mul,
    int64_negate as _int64_negate,
    int64_sub as _int64_sub,
    int_add as _int_add,
    int_mul as _int_mul,
    int_negate as _int_negate,
    int_sub as _int_sub,
)

__all__ = ()

_console: _LoggingConsole = _LoggingConsole(__name__)
_one: int = 1
_big: int = _int_mul(2147483647, _one)
_small: int = _int_mul(_int_negate(-2147483648), _one)
_console.log(f'{_int_add(_big, 1)} {_int_sub(_small, 1)} {_int_add(_one, 1)} {_int_sub(_one, 1)} {_int_add(_small, 1)} {_int_sub(_big, 1)}')
_one64: int = _one
_big64: int = _int64_mul(9223372036854775807, _one64)
_small64: int = _int64_sub(_int64_negate(_big64), _one64)
_console.log(f'{str(_int64_add(_big64, 1))} {str(_int64_sub(_small64, 1))} {str(_int64_sub(_one64, 1))}')
_inf: float = 1e308 * 10.0
_nan: float = _inf - _inf
_ninf: float = 0.0 - _inf
_console.log(f'{_boolean_to_string(_float64_near(_nan, _nan))} {_boolean_to_string(_float64_near(_inf, _inf))} {_boolean_to_string(_float64_near(_ninf, _ninf))} {_boolean_to_string(_float64_near(_nan, 1.0))} {_boolean_to_string(_float64_near(1.0, _nan))}')
_console.log(f'{_boolean_to_string(_float64_near(_inf, 1.0))} {_boolean_to_string(_float64_near(_inf, _ninf))} {_boolean_to_string(_float64_near(_inf, 1.0, 0.0, 0.0))} {_boolean_to_string(_float64_near(_inf, _inf, 0.0, 0.0))} {_boolean_to_string(_float64_near(_inf, 1.0, 1.0, _inf))} {_boolean_to_string(_float64_near(1.0, _ninf, 1.0, _inf))}')
_console.log(f'{_boolean_to_string(_float64_near(1.0, 1.5, _nan, 1.0))} {_boolean_to_string(_float64_near(1.0, 1.5, 1.0, _nan))} {_boolean_to_string(_float64_near(1.0, 1.0, _nan, _nan))} {_boolean_to_string(_float64_near(-0.0, 0.0, 0.0, 0.0))}')
_console.log(f'{_boolean_to_string(_float64_near(1.0, 1.0 + 1e-10))} {_boolean_to_string(_float64_near(1.0, 1.1))} {_boolean_to_string(_float64_near(1.0, 1.1, 0.2))} {_boolean_to_string(_float64_near(0.0, 1e-300, 1e-9, 1e-12))}')
