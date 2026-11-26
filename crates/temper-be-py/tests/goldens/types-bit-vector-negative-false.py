from temper_core import (
    BitVector as _BitVector,
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    dense_bit_vector_set as _dense_bit_vector_set,
)

__all__ = ()

_console: _LoggingConsole = _LoggingConsole(__name__)
_v: _BitVector = _BitVector(2)
_dense_bit_vector_set(_v, 5, False)
_console.log(f'v[5]={_boolean_to_string(_v.get(5))}')
_console.log('before')
_dense_bit_vector_set(_v, -1, False)
_console.log('not reached')
