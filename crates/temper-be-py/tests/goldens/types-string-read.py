from collections.abc import Sequence as _Sequence
from temper_core import (
    InvalidCodePoint as _InvalidCodePoint,
    LoggingConsole as _LoggingConsole,
    ParseError as _ParseError,
    boolean_to_string as _boolean_to_string,
    float64_to_int as _float64_to_int,
    int_to_string as _int_to_string,
    string_count_between as _string_count_between,
    string_from_code_point as _string_from_code_point,
    string_from_code_points as _string_from_code_points,
    string_get as _string_get,
    string_has_at_least as _string_has_at_least,
    string_next as _string_next,
    string_prev as _string_prev,
    string_step as _string_step,
    string_to_int32 as _string_to_int32,
    string_to_int64 as _string_to_int64,
)

__all__ = ()


def _report_slice_info(s: str, /) -> None:
    start = 0
    end = len(s)
    count = _string_count_between(s, start, end)
    _console.log(f'Count of code-points for `{s}`: {count}')
    _console.log(f'Has at least 0: {_boolean_to_string(_string_has_at_least(s, start, end, 0))}')
    _console.log(f'Has at least 1: {_boolean_to_string(_string_has_at_least(s, start, end, 1))}')
    _console.log(f'Has at least 2: {_boolean_to_string(_string_has_at_least(s, start, end, 2))}')
    _console.log(f'Has at least 3: {_boolean_to_string(_string_has_at_least(s, start, end, 3))}')
    _console.log(f'Has at least 4: {_boolean_to_string(_string_has_at_least(s, start, end, 4))}')
    _console.log(f'Has at least 5: {_boolean_to_string(_string_has_at_least(s, start, end, 5))}')
    _console.log(f'Has at least 6: {_boolean_to_string(_string_has_at_least(s, start, end, 6))}')
    _console.log(f'Has at least 7: {_boolean_to_string(_string_has_at_least(s, start, end, 7))}')
    _console.log(f'Has at least 8: {_boolean_to_string(_string_has_at_least(s, start, end, 8))}')
    _console.log('code-points')
    i = 0
    while len(s) > i:
        cp = _string_get(s, i)
        i = _string_next(s, i)
        _console.log(f'  {_int_to_string(cp, 16)}')


def _compare_ends(message: str, /) -> None:
    start = 0
    end = len(message)
    _console.log(f'start < end? {_boolean_to_string(start - end < 0)}')
    _console.log(f'start > end? {_boolean_to_string(start - end > 0)}')
    _console.log(f'start == start? {_boolean_to_string(start == start)}')
    _console.log(f'end < end? {_boolean_to_string(end - end < 0)}')
    _console.log(f'end <= end? {_boolean_to_string(end - end <= 0)}')


def _from_code_no_inline(code: int, /) -> str:
    _console.log(f'From 0x{_int_to_string(code, 16)}')
    return _string_from_code_point(code)


def _from_codes_no_inline(codes: _Sequence[int], /) -> str:
    _console.log('From codes')
    return _string_from_code_points(codes)


def _check_parse(string: str, radix: int | None = None, /) -> None:
    try:
        converted = str(_string_to_int32(string, radix) if radix is not None else _string_to_int32(string))
    except _ParseError:
        converted = 'failed'
    _console.log(f'Parse {string} -> {converted}')


def _report_found(label: str, haystack: str, needle: str, start: int | None = None, /) -> int:
    if start is None:
        start = 0
    index = haystack.find(needle, start)
    if index >= 0:
        before = haystack[_string_prev(_hay, start) : index]
        _console.log(f'{label}: {before}...')
        return _string_next(haystack, index)
    else:
        _console.log(f'{label}: (nope)')
        return 0


def _found_at(haystack: str, needle: str, start: int | None = None, /) -> int:
    if start is None:
        start = 0
    index = haystack.find(needle, start)
    if index >= 0:
        return _string_count_between(haystack, 0, index)
    else:
        return -1


_console: _LoggingConsole = _LoggingConsole(__name__)
_report_slice_info('κόσμε𐆊')
_report_slice_info('Hi')
_compare_ends('Hi')
_console.log(_string_from_code_point(127757))
_console.log(_from_code_no_inline(127757))
_t_0: str
try:
    _t_0 = _from_code_no_inline(1114112)
except _InvalidCodePoint:
    _t_0 = 'nope'
_console.log(_t_0)
_t_1: str
try:
    _t_1 = _from_code_no_inline(55296)
except _InvalidCodePoint:
    _t_1 = 'no to surrogate'
_console.log(_t_1)
_console.log(_from_codes_no_inline((128059, 8205, 10052, 65039)))
_t_2: str
try:
    _t_2 = _from_codes_no_inline((55296, 56320))
except _InvalidCodePoint:
    _t_2 = 'no to surrogates'
_console.log(_t_2)
_t_3: str
try:
    _t_3 = _from_codes_no_inline((65, -1, 56320))
except _InvalidCodePoint as _e:
    _t_3 = _int_to_string(_e.code_point, 16)
_console.log(_t_3)
_t_4: str
try:
    _t_4 = _string_from_code_point(57343)
except _InvalidCodePoint as _e2:
    _t_4 = _e2.message
_console.log(_t_4)
_t_5: str
try:
    _t_5 = str(_string_to_int32('x'))
except _ParseError as _e3:
    _t_5 = _e3.message
_console.log(_t_5)
_t_6: str
try:
    _t_6 = str(_float64_to_int(1e300))
except _ParseError as _e4:
    _t_6 = _e4.reason
_console.log(_t_6)
_t_7: int
try:
    _t_7 = _string_to_int64('12')
except _ParseError:
    _t_7 = -1
_console.log(str(_t_7))
_check_parse('-1', None)
_check_parse('2', None)
_check_parse(' 2 ', None)
_check_parse('2.0', None)
_check_parse('2a', None)
_check_parse('0x10', None)
_check_parse('totally', None)
_check_parse('2a', 16)
_check_parse('20', 7)
_check_parse('20', 17)
_check_parse('20', 37)
_check_parse('10', 0)
_check_parse('0', 1)
_t_8: str
try:
    _t_8 = str(_string_to_int64('10', 0))
except _ParseError as _e5:
    _t_8 = _e5.reason
_console.log(_t_8)
_t_9: int
try:
    _t_9 = _string_to_int64('10', -2)
except _ParseError:
    _t_9 = -1
_console.log(str(_t_9))
_s = 'foo-bar'
_after1_2 = _string_next(_s, 0)
_after2_2 = _string_next(_s, _after1_2)
_after3 = _string_next(_s, _after2_2)
_after4 = _string_next(_s, _after3)
_after4_b = _string_step(_s, 0, 4)
_before1 = _string_prev(_s, _after4_b)
_before3 = _string_step(_s, _before1, -2)
_before3_b = _string_step(_s, _before3, 0)
_way_before = _string_step(_s, _before3, -100)
_way_after = _string_step(_s, _before3, 100)
_console.log(f's[3:]  -> {_s[_after3 : len(_s)]}')
_console.log(f's[:3]  -> {_s[0 : _after3]}')
_console.log(f's[1:4] -> {_s[_after1_2 : _after4]}')
_console.log(f's[:4]  -> {_s[0 : _after4]}')
_console.log(f's[1:4] -> {_s[_before3_b : _after4_b]}')
_console.log(f's[-many:many] -> {_s[_way_before : _way_after]}')
_hay: str = 'It was the best of times, it was the worst of times, ...'
_needle: str = 'time'
_after1: int = _report_found('found 1', _hay, _needle)
_after2: int = _report_found('found 2', _hay, _needle, _after1)
_report_found('found 3', _hay, _needle, _after2)
_report_found('hay 2', 'time comes for us all', _needle)
_report_found('hay 3', 'in the nick of time', _needle)
_report_found('hay 4', 'my name is tim', _needle)
_report_found('hay 5', 'tim', _needle)
_nul_hay: str = 'ab\x00#\x00'
_console.log('nul: ' + str(_found_at(_nul_hay, '\x00')))
_after_nul: int = _string_next(_nul_hay, _string_next(_nul_hay, _string_next(_nul_hay, 0)))
_console.log('nul again: ' + str(_found_at(_nul_hay, '\x00', _after_nul)))
_console.log('soft hyphen: ' + str(_found_at('ab\u00ad#', '\u00ad')))
_console.log('past soft hyphen: ' + str(_found_at('ab\u00ad#', 'b#')))
_console.log('zwj: ' + str(_found_at('a\u200db', 'ab')))
_console.log(f"composed: {_found_at('é', 'é')}")
