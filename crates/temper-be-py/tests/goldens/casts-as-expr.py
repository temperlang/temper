from abc import ABCMeta as _ABCMeta, abstractmethod as _abstractmethod
from temper_core import (
    Bubble as _Bubble,
    LoggingConsole as _LoggingConsole,
    cast_by_type as _cast_by_type,
    cast_none as _cast_none,
)

__all__ = ('thing0', 'thing1', 'thing2', 'thing3')


class _Apple(metaclass=_ABCMeta):
    __slots__ = ()

    @property
    @_abstractmethod
    def name(self, /) -> str:
        ...


class _Fuji(_Apple):
    __slots__ = ()

    @property
    def name(self, /) -> str:
        return 'Fuji'


class _Gala(_Apple):
    __slots__ = ()

    @property
    def name(self, /) -> str:
        return 'Gala'


_console: _LoggingConsole = _LoggingConsole(__name__)
_x: _Apple | None
_x = _Fuji()
_console.log(_cast_none(_x).name)
_console.log(_cast_by_type(_x, _Fuji).name)
_x = _Gala()
_t_0: str
try:
    _t_0 = _cast_by_type(_x, _Fuji).name
except _Bubble:
    _t_0 = 'Cast failed!'
_console.log(_t_0)
_y: object | None = _x
_t_1: str
try:
    _t_1 = _cast_by_type(_y, _Apple).name  # type: ignore[type-abstract]
except _Bubble:
    _t_1 = 'Cast from AnyValue failed!'
_console.log(_t_1)
_t_2: str
try:
    _t_2 = _cast_by_type(_y, _Gala).name
except _Bubble:
    _t_2 = 'Cast from AnyValue failed!'
_console.log(_t_2)
_t_3: str
try:
    _t_3 = _cast_by_type(_y, _Fuji).name
except _Bubble:
    _t_3 = 'Cast from AnyValue failed!'
_console.log(_t_3)
_x = None
_t_4: str
try:
    _cast_none(_x)
    _t_4 = 'Tasty nothing.'
except _Bubble:
    _t_4 = 'Was null!'
_console.log(_t_4)
thing0: object = False
thing1: object = 1
thing2: object = 'two'
thing3: object = 3.14
