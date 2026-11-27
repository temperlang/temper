from abc import ABCMeta as _ABCMeta
from collections.abc import Callable as _Callable, Sequence as _Sequence
from temper_core import (
    Failure as _Failure,
    InvalidCodePoint as _InvalidCodePoint,
    LoggingConsole as _LoggingConsole,
    MissingKey as _MissingKey,
    ParseError as _ParseError,
    boolean_to_string as _boolean_to_string,
    date_from_iso_string as _date_from_iso_string,
    date_of as _date_of,
    date_to_string as _date_to_string,
    float64_to_int as _float64_to_int,
    float_eq as _float_eq,
    int64_to_int32 as _int64_to_int32,
    int_to_string as _int_to_string,
    map_builder_remove as _map_builder_remove,
    mapped_get as _mapped_get,
    string_from_code_point as _string_from_code_point,
    string_from_code_points as _string_from_code_points,
    string_to_float64 as _string_to_float64,
    string_to_int32 as _string_to_int32,
    string_to_int64 as _string_to_int64,
)
from temper_std.json import (
    JsonError as _JsonError,
    JsonSyntaxError as _JsonSyntaxError,
    JsonTextProducer as _JsonTextProducer,
    parse_json as _parse_json,
)
from temper_std.regex import (
    CodeRange as _CodeRange,
    CodeSet as _CodeSet,
    Regex as _Regex,
    Repeat as _Repeat,
)
from temper_std.temporal import TemporalError as _TemporalError

__all__ = ()


class _Shape(metaclass=_ABCMeta):
    __slots__ = ()


class _Square(_Shape):
    __slots__ = ('_side',)
    _side: int

    def __init__(self, /, side: int) -> None:
        self._side = side

    @property
    def side(self, /) -> int:
        return self._side


class _Circle(_Shape):
    __slots__ = ()


class _Problem(_Failure):
    __slots__ = ()


class _Positive(_Problem):
    __slots__ = ('_n',)
    _n: int

    def __init__(self, /, n: int) -> None:
        self._n = n

    @property
    def n(self, /) -> int:
        return self._n

    @property
    def message(self, /) -> str:
        return f'positive (n: {self._n})'


class _NotSquare(_Problem):
    __slots__ = ()

    @property
    def message(self, /) -> str:
        return 'not square'


def _failing(n: int, /) -> int:
    if n > 0:
        raise _Positive(n)
    return n


def _downcast(s: _Shape, /) -> _Square:
    if isinstance(s, _Square):
        return s
    else:
        raise _NotSquare()


def _calling(f: _Callable[[], int], /) -> int:
    try:
        return f()
    except _Problem:
        return -9


def _appending(code_point: int, /) -> str:
    sb = ['']
    sb.append(_string_from_code_point(code_point))
    return ''.join(sb)


def _parsing(text: str, /) -> str:
    _parse_json(text)
    return 'parsed'


def _explode(n: int, /) -> int:
    if n > 0:
        raise BaseException('panic')
    raise _Positive(n)


def _noted(name: str, /) -> int:
    _console.log(f'{name} fallback')
    return -2


def _inner() -> int:
    try:
        return _explode(1)
    except _Positive:
        return _noted('inner')


_console: _LoggingConsole = _LoggingConsole(__name__)
_zero: int
try:
    _zero = _failing(0)
except _Positive:
    _zero = -1
_a: int
try:
    _a = _failing(1)
except _Positive:
    _a = -1
_b: int
try:
    _b = _downcast(_Circle()).side
except _NotSquare:
    _b = -2
_e: int
try:
    _e = _string_to_int32('nope')
except _ParseError:
    _e = -5
_f: float
try:
    _f = _string_to_float64('x')
except _ParseError:
    _f = -6.0
_g: str
try:
    _g = _string_from_code_point(1114112)
except _InvalidCodePoint:
    _g = '?'
_digits: _Regex = _Repeat(_CodeSet((_CodeRange(48, 57),), False), 1, None, False).compiled()
_h: str = 'no digits' if (_subject := None if (_subject2 := None if (_subject3 := _digits.find('none')) is None else _subject3.full) is None else _subject2.value) is None else _subject
_i: int = _calling(lambda: _failing(2))
_console.log(f'{_a} {_b} {_e} {_boolean_to_string(_float_eq(_f, -6.0))} {_g} {_h} {_i}')
_k: int
try:
    _k = _string_to_int64('zz')
except _ParseError:
    _k = -7
_l: int
try:
    _l = _string_to_int32('99999999999')
except _ParseError:
    _l = -8
_m: int
try:
    _m = _float64_to_int(1e20)
except _ParseError:
    _m = -9
_n: int
try:
    _n = _int64_to_int32(5000000000)
except _ParseError:
    _n = -10
_points: _Sequence[int] = (65, 1114112)
_o: str
try:
    _o = _string_from_code_points(_points)
except _InvalidCodePoint as _e2:
    _o = _int_to_string(_e2.code_point, 16)
_q: str
try:
    _q = _appending(-1)
except _InvalidCodePoint as _e3:
    _q = str(_e3.code_point)
_builder: dict[str, int] = {}
_r: int
try:
    _r = _mapped_get(_builder, 'x')
except _MissingKey:
    _r = -11
_r2: str
try:
    _r2 = str(_map_builder_remove(_builder, 'y'))
except _MissingKey as _e4:
    _r2 = _e4.message
_s: str
try:
    _s = _date_to_string(_date_of(2023, 2, 30))
except _TemporalError:
    _s = 'no date'
_t: str
try:
    _t = _date_to_string(_date_from_iso_string('2023-13-01'))
except _TemporalError:
    _t = 'bad date'
_u: str
try:
    _u = _parsing('[1,')
except _JsonError:
    _u = 'bad json'
_partial: _JsonTextProducer = _JsonTextProducer()
_partial.start_object()
_v: str
try:
    _v = _partial.to_json_string()
except _JsonSyntaxError as _e5:
    _v = _e5.problem
_console.log(f'{str(_k)} {_l} {_m} {_n} {_o} {_q} {_r} {_r2} {_s} {_t} {_u}')
_console.log(_v)
_console.log('panicking')
_p: int
try:
    _p = _inner()
except _Positive:
    _p = -1
_console.log(f'after the panic {_p}')
