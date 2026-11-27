from temper_core import Failure as _Failure

__all__ = ('TemporalError', 'InvalidDate', 'InvalidIsoDate')


class TemporalError(_Failure):
    __slots__ = ()


class InvalidDate(TemporalError):
    __slots__ = ('_year', '_month', '_day')
    _year: int
    _month: int
    _day: int

    def __init__(self, /, year: int, month: int, day: int) -> None:
        self._year = year
        self._month = month
        self._day = day

    @property
    def year(self, /) -> int:
        return self._year

    @property
    def month(self, /) -> int:
        return self._month

    @property
    def day(self, /) -> int:
        return self._day

    @property
    def message(self, /) -> str:
        return f'invalid date (year: {self._year}, month: {self._month}, day: {self._day})'


class InvalidIsoDate(TemporalError):
    __slots__ = ('_text',)
    _text: str

    def __init__(self, /, text: str) -> None:
        self._text = text

    @property
    def text(self, /) -> str:
        return self._text

    @property
    def message(self, /) -> str:
        return f'invalid iso date (text: {self._text})'
