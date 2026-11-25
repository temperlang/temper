from temper_core import (
    Bubble as _Bubble,
    LoggingConsole as _LoggingConsole,
    int_add as _int_add,
    list_get as _list_get,
    string_builder_end as _string_builder_end,
    string_from_code_point as _string_from_code_point,
    string_get as _string_get,
    string_next as _string_next,
)

__all__ = ()


def _index_of_char(s: str, c: int, /) -> int:
    i = 0
    while len(s) > i:
        if _string_get(s, i) == c:
            return i
        i = _string_next(s, i)
    return -1


def _compare_positions(s: str, a: int, b: int, /) -> None:
    delta = _index_of_char(s, a) - _index_of_char(s, b)
    rel_pos = 'before' if delta < 0 else 'at' if delta == 0 else 'after'
    t_0: str
    try:
        t_0 = _string_from_code_point(a)
    except _Bubble:
        t_0 = '?'
    t_1: str
    try:
        t_1 = _string_from_code_point(b)
    except _Bubble:
        t_1 = '?'
    _console.log(f'\'{t_0}\' is {rel_pos} \'{t_1}\' in "{s}".')


def _has0(s: str, c: int, /) -> bool:
    return _index_of_char(s, c) >= 0


def _has1(s: str, c: int, /) -> bool:
    i = _index_of_char(s, c)
    return i >= 0


def _has2(s: str, c: int, /) -> bool:
    i = _index_of_char(s, c)
    return not i < 0


def _has3(s: str, c: int, /) -> bool:
    i = _index_of_char(s, c)
    return i >= 0


def _has4(s: str, c: int, /) -> bool:
    i = _index_of_char(s, c)
    return not i < 0


_console: _LoggingConsole = _LoggingConsole(__name__)
_compare_positions('cookie', 99, 105)
_compare_positions('cookie', 107, 111)
_compare_positions('cookie', 107, 107)
_compare_positions('cookie', 120, 99)
_compare_positions('cookie', 101, 120)
_compare_positions('cookie', 120, 121)
_s: str = 'cookie'
_list = (111, 105, 122)
_length = len(_list)
_i = 0
while _i < _length:
    _c = _list_get(_list, _i)
    _i = _int_add(_i, 1)
    _t_0: str
    try:
        _t_0 = _string_from_code_point(_c)
    except _Bubble:
        _t_0 = '?'
    _console.log(f"'{_t_0}' in \"{_s}\": {'y' if _has0(_s, _c) else 'n'}, {'y' if _has1(_s, _c) else 'n'}, {'y' if _has2(_s, _c) else 'n'}, {'y' if _has3(_s, _c) else 'n'}, {'y' if _has4(_s, _c) else 'n'}")
_string_builder = ['']
_string_builder.append('Hello')
_string_builder.append(',')
_after_comma = _string_builder_end(_string_builder)
_string_builder.append(' World!')
_str = ''.join(_string_builder)
_console.log(_str[0 : _after_comma])
_console.log(_str[_after_comma : len(_str)])
