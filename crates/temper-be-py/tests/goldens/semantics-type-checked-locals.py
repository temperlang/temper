from temper_core import Bubble as _Bubble, LoggingConsole as _LoggingConsole

__all__ = ()


def _yay() -> None:
    _console.log('yay')


def _meh() -> None:
    _console.log('meh')


def _boo() -> None:
    _console.log('boo')


def _f(x: int, /) -> int:
    return x


def _g(x: int, /) -> int:
    return x


def _i(x: int, /) -> int:
    return x


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('Checkpoint 0')
try:
    _a: int
    _a = '1'
    raise _Bubble()
except _Bubble:
    _yay()
_console.log('Checkpoint 1')
_a2 = 1  # pyright: ignore[reportUnusedVariable]
_yay()
_console.log('Checkpoint 2')
_a3 = 1
try:
    _a3 = '2'
    raise _Bubble()
    _boo()
except _Bubble:
    _meh()
if _a3 == 1:
    _meh()
else:
    _meh()
_console.log('Checkpoint 3')
_a4 = 1  # pyright: ignore[reportUnusedVariable]
_a4 = 2
_yay()
_console.log('Checkpoint 4')
try:
    _f(0)
    _yay()
except _Bubble:
    _boo()
try:
    _f('0')
    _meh()
except _Bubble:
    _meh()
_console.log('Checkpoint 5')
try:
    _g(0)
    _yay()
except _Bubble:
    _boo()
try:
    _g('0')
    _meh()
except _Bubble:
    _meh()
_console.log('Checkpoint 6')
try:
    _i(0)
    _yay()
except _Bubble:
    _boo()
try:
    _i('0')
    _meh()
except _Bubble:
    _meh()
