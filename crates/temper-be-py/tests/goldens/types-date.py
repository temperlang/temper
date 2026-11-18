from datetime import date as _date
from temper_core import (
    LoggingConsole as _LoggingConsole,
    date_from_iso_string as _date_from_iso_string,
    date_of as _date_of,
    date_to_string as _date_to_string,
    date_today as _date_today,
    int_add as _int_add,
    list_get as _list_get,
    years_between as _years_between,
)

__all__ = ()

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
_date_today()
_start: _date = _date_of(2010, 3, 21)
_first_years: int = _years_between(_start, _date_of(2012, 3, 20))
_console.log(f'First years between: {_first_years}')
_second_years: int = _years_between(_start, _date_of(2012, 3, 21))
_console.log(f'Second years between: {_second_years}')
_rdc: _date = _date_from_iso_string('1596-03-31')
_console.log(f'Happy birthday, René: day={_rdc.day}, month={_rdc.month}, year={_rdc.year}')
