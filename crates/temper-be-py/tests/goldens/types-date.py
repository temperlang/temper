from collections.abc import Sequence as _Sequence
from datetime import date as _date
from temper_core import (
    LoggingConsole as _LoggingConsole,
    date_from_iso_string as _date_from_iso_string,
    date_of as _date_of,
    date_to_string as _date_to_string,
    date_today as _date_today,
    int_add as _int_add,
    list_get as _list_get,
    list_join as _list_join,
    list_map as _list_map,
    years_between as _years_between,
)
from temper_std.temporal import TemporalError as _TemporalError

__all__ = ()


def _check_date(year: int, month: int, day: int, /) -> str:
    try:
        return _date_to_string(_date_of(year, month, day))
    except _TemporalError as e:
        return e.message


_console: _LoggingConsole = _LoggingConsole(__name__)
_d: _date = _date_of(2023, 6, 29)
_console.log(f'year    = {_d.year}\nmonth   = {_d.month}\nday     = {_d.day}\nweekday = {_d.isoweekday()}')
_console.log(f'ISO 8601: {_date_to_string(_d)}')
_yonks_ago: _date = _date_of(12, 11, 10)
_console.log(f'ISO 8601 yonks: {_date_to_string(_yonks_ago)}')
_console.log(f'yonksAgo.year = {_yonks_ago.year}')
_list = (1700, 1800, 1900, 2000, 2100, 2400)
_length = len(_list)
_i = 0
while _i < _length:
    _y = _list_get(_list, _i)
    _i = _int_add(_i, 1)
    _console.log(f'{_y}: Jan 1 weekday {_date_of(_y, 1, 1).isoweekday()}, Mar 1 weekday {_date_of(_y, 3, 1).isoweekday()}')
_console.log(f'{_check_date(2024, 2, 29)} {_check_date(2023, 2, 29)}')
_t_0 = ('2024-02-29', '2023-02-29', '2023-2-1')


def _fn(s: str, /) -> str:
    try:
        return _date_to_string(_date_from_iso_string(s))
    except _TemporalError:
        return 'none'


_parsed: _Sequence[str] = _list_map(_t_0, _fn)
_console.log(_list_join(_parsed, ' ', lambda it: it))
_date_today()
_start: _date = _date_of(2010, 3, 21)
_first_years: int = _years_between(_start, _date_of(2012, 3, 20))
_console.log(f'First years between: {_first_years}')
_second_years: int = _years_between(_start, _date_of(2012, 3, 21))
_console.log(f'Second years between: {_second_years}')
_rdc: _date
try:
    _rdc = _date_from_iso_string('1596-03-31')
except _TemporalError as _t_1:
    raise BaseException('panic') from _t_1
_console.log(f'Happy birthday, René: day={_rdc.day}, month={_rdc.month}, year={_rdc.year}')
