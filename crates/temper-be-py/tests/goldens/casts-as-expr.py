from abc import ABCMeta as _ABCMeta, abstractmethod as _abstractmethod
from temper_core import (
    LoggingConsole as _LoggingConsole,
    cast_by_type as _cast_by_type,
    cast_none as _cast_none,
)

__all__ = ('thing2', 'thing1', 'thing0', 'thing3')


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


def _describe(v: object | None, /) -> str:
    if v is not None and isinstance(v, _Fuji):
        return f'Fuji {v.name}'
    if v is not None and isinstance(v, _Apple):
        return v.name
    else:
        return 'other'


_console: _LoggingConsole = _LoggingConsole(__name__)
_x: _Apple | None
_x = _Fuji()
_console.log(_cast_none(_x).name)
_console.log(_cast_by_type(_x, _Fuji).name)
_x = _Gala()
_console.log(_cast_by_type(_x, _Fuji).name if _x is not None and isinstance(_x, _Fuji) else 'Cast failed!')  # pyright: ignore[reportUnnecessaryComparison]
_y: object | None = _x
_console.log(_cast_by_type(_y, _Apple).name)  # type: ignore[type-abstract]
_console.log(_cast_by_type(_y, _Gala).name)
_x = None
_console.log('Tasty nothing.' if _x is not None else 'Was null!')  # pyright: ignore[reportUnnecessaryComparison]
thing2: object = 'two'
thing1: object = 1
_console.log(_describe(_Fuji()))
_console.log(_describe(_Gala()))
_console.log(_describe(thing2))
_console.log(_describe(thing1))
_console.log(_describe(None))
thing0: object = False
thing3: object = 3.14
