from temper_core import (
    BitVector as _BitVector,
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    dense_bit_vector_set as _dense_bit_vector_set,
)

__all__ = ()

_console: _LoggingConsole = _LoggingConsole(__name__)
_v: _BitVector = _BitVector(16)
_console.log(f'v[2]={_boolean_to_string(_v.get(2))}')
_console.log(f'v[3]={_boolean_to_string(_v.get(3))}')
_console.log('v[3] <- true')
_dense_bit_vector_set(_v, 3, True)
_console.log(f'v[2]={_boolean_to_string(_v.get(2))}')
_console.log(f'v[3]={_boolean_to_string(_v.get(3))}')
_console.log('v[3] <- false')
_dense_bit_vector_set(_v, 3, False)
_console.log(f'v[2]={_boolean_to_string(_v.get(2))}')
_console.log(f'v[3]={_boolean_to_string(_v.get(3))}')
_dense_bit_vector_set(_v, 100, True)
_dense_bit_vector_set(_v, 200, False)
_console.log(f'v[99]={_boolean_to_string(_v.get(99))} v[100]={_boolean_to_string(_v.get(100))} v[101]={_boolean_to_string(_v.get(101))} v[200]={_boolean_to_string(_v.get(200))} v[-1]={_boolean_to_string(_v.get(-1))}')
_dense_bit_vector_set(_v, 1000, True)
_console.log(f'v[999]={_boolean_to_string(_v.get(999))} v[1000]={_boolean_to_string(_v.get(1000))} v[100]={_boolean_to_string(_v.get(100))}')
_u: _BitVector = _BitVector(0)
_dense_bit_vector_set(_u, 8, True)
_dense_bit_vector_set(_u, 32, True)
_dense_bit_vector_set(_u, 64, True)
_console.log(f'{_boolean_to_string(_u.get(7))} {_boolean_to_string(_u.get(8))} {_boolean_to_string(_u.get(9))} {_boolean_to_string(_u.get(31))} {_boolean_to_string(_u.get(32))} {_boolean_to_string(_u.get(33))} {_boolean_to_string(_u.get(63))} {_boolean_to_string(_u.get(64))} {_boolean_to_string(_u.get(65))}')
_dense_bit_vector_set(_v, 1000, False)
_console.log(f'v[1000]={_boolean_to_string(_v.get(1000))} v[999]={_boolean_to_string(_v.get(999))}')
_w: _BitVector = _BitVector(-9)
_dense_bit_vector_set(_w, 1, True)
_console.log(f'w[0]={_boolean_to_string(_w.get(0))} w[1]={_boolean_to_string(_w.get(1))}')
_console.log('before')
_dense_bit_vector_set(_v, -1, True)
_console.log('not reached')
