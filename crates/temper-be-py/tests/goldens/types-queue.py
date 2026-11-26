from collections import deque as _deque
from temper_core import (
    LoggingConsole as _LoggingConsole,
    arith_int_mod as _arith_int_mod,
    boolean_to_string as _boolean_to_string,
    deque_remove_first as _deque_remove_first,
    int_add as _int_add,
)

__all__ = ()


def _check_empty() -> None:
    _console.log('Empty' if not _queue else 'Not empty')


_console: _LoggingConsole = _LoggingConsole(__name__)
_queue: _deque[str] = _deque()
_check_empty()
if not not _queue:
    _console.log(_deque_remove_first(_queue))
_queue.append('foo')
_queue.append('bar')
_console.log(_deque_remove_first(_queue))
_queue.append('baz')
_console.log(_deque_remove_first(_queue))
_queue.append('boo')
_check_empty()
while not not _queue:
    _console.log(_deque_remove_first(_queue))
_check_empty()
_numbers: _deque[int | None] = _deque()
_sum: int = 0
_i = 0
while _i < 100:
    _numbers.append(_i)
    _numbers.append(None)
    if _arith_int_mod(_i, 3) == 0:
        _sum = _int_add(_sum, -1000 if (_subject := _deque_remove_first(_numbers)) is None else _subject)
        _sum = _int_add(_sum, 1 if (_subject2 := _deque_remove_first(_numbers)) is None else _subject2)
    _i = _int_add(_i, 1)
_order: bool = True
_expected: int = 34
while not not _numbers:
    _n: int | None = _deque_remove_first(_numbers)
    if _n is not None:
        if _n != _expected:
            _order = False
        _expected = _int_add(_expected, 1)
_console.log(f'sum {_sum}, in order {_boolean_to_string(_order)}, through {_expected}')
