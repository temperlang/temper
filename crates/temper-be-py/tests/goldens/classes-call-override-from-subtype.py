from abc import ABCMeta as _ABCMeta, abstractmethod as _abstractmethod
from temper_core import (
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
)

__all__ = ()


class _Slappy(metaclass=_ABCMeta):
    __slots__ = ()

    @property
    @_abstractmethod
    def thingums(self, /) -> int:
        ...


class _Slippy(_Slappy):
    __slots__ = ()

    def check_overridden(self, /) -> bool:
        return self.thingums == 1

    def check_own(self, /) -> bool:
        return self.thingumses == 2

    @property
    def thingums(self, /) -> int:
        return 3

    @property
    def thingumses(self, /) -> int:
        return 4


class _A(metaclass=_ABCMeta):
    __slots__ = ()

    def a(self, /) -> str:
        return 'A wins!'

    @property
    def thing(self, /) -> str:
        return 'A has thing!'


class _B(_A):
    __slots__ = ()


class _C(_A):
    __slots__ = ()

    def a(self, /) -> str:
        return 'C wins!'

    @property
    def thing(self, /) -> str:
        return 'C has thing!'


class _D(_B, _C):
    __slots__ = ()

    def a(self, /) -> str:
        return _C.a(self)

    @property
    def thing(self, /) -> str:
        return 'C has thing!'


class _ASplit(metaclass=_ABCMeta):
    __slots__ = ()

    def a(self, /) -> str:
        return 'A wins!'


class _BSplit(_ASplit):
    __slots__ = ()


class _CSplit(metaclass=_ABCMeta):
    __slots__ = ()

    def a(self, /) -> str:
        return 'C wins!'


class _DSplit(_BSplit, _CSplit):
    __slots__ = ()

    def a(self, /) -> str:
        return _CSplit.a(self)


class _ADeep(metaclass=_ABCMeta):
    __slots__ = ()

    def a(self, /) -> str:
        return 'A wins!'


class _B1Deep(_ADeep):
    __slots__ = ()

    def a(self, /) -> str:
        return 'B1 wins!'


class _B2Deep(_B1Deep):
    __slots__ = ()


class _CDeep(_ADeep):
    __slots__ = ()


class _DDeep(_B2Deep, _CDeep):
    __slots__ = ()

    def a(self, /) -> str:
        return _B1Deep.a(self)


class _EDeep(_CDeep, _B2Deep):
    __slots__ = ()

    def a(self, /) -> str:
        return _ADeep.a(self)


_console: _LoggingConsole = _LoggingConsole(__name__)
_slippy: _Slippy = _Slippy()
_slappy: _Slappy = _slippy
_console.log(str(_slippy.thingums))
_console.log(str(_slappy.thingums))
_console.log(str(_slippy.thingumses))
_console.log(_boolean_to_string(_slippy.check_overridden()))
_console.log(_boolean_to_string(_slippy.check_own()))
_console.log(_D().a())
_console.log(_D().thing)
_console.log(_DSplit().a())
_console.log(_DDeep().a())
_console.log(_EDeep().a())
