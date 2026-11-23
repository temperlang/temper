from abc import ABCMeta as _ABCMeta, abstractmethod as _abstractmethod
from temper_core import LoggingConsole as _LoggingConsole, int_mul as _int_mul

__all__ = ()


class _Shape(metaclass=_ABCMeta):
    __slots__ = ()

    @property
    @_abstractmethod
    def size(self, /) -> int:
        ...


class _Dot(_Shape):
    __slots__ = ('_size',)
    _size: int

    def __init__(self, /, size: int) -> None:
        self._size = size

    @property
    def size(self, /) -> int:
        return self._size


class _Line(_Shape):
    __slots__ = ('_size',)
    _size: int

    def __init__(self, /, size: int) -> None:
        self._size = size

    @property
    def size(self, /) -> int:
        return self._size


def _shape_of(n: int, /) -> _Shape:
    if n > 1:
        return _Line(n)
    else:
        return _Dot(n)


def _named(s: _Shape, /) -> str:
    t_1: str
    if isinstance(s, _Dot):
        t_1 = 'dot'
    else:
        t_0: str
        if isinstance(s, _Line):
            t_0 = 'line'
        else:
            raise BaseException('panic')
        t_1 = t_0
    name = t_1
    return name


def _pair(s: _Shape, t: _Shape, /) -> str:
    if isinstance(s, _Dot):
        if isinstance(t, _Dot):
            return 'dd'
        elif isinstance(t, _Line):
            return 'dl'
        else:
            raise BaseException('panic')
    elif isinstance(s, _Line):
        return 'l'
    else:
        raise BaseException('panic')


def _doubled(n: int, /) -> int:
    return _int_mul(n, 2)


def _code(s: _Shape, early: bool, /) -> int:
    if early:
        t_1: int
        if isinstance(s, _Dot):
            t_1 = 1
        else:
            t_0: int
            if isinstance(s, _Line):
                t_0 = 2
            else:
                raise BaseException('panic')
            t_1 = t_0
        return t_1
    if s.size > 1:
        if isinstance(s, _Dot):
            return 10
        elif isinstance(s, _Line):
            return 20
        else:
            raise BaseException('panic')
    else:
        t_3: int
        if isinstance(s, _Dot):
            t_3 = 3
        else:
            t_2: int
            if isinstance(s, _Line):
                t_2 = 4
            else:
                raise BaseException('panic')
            t_3 = t_2
        return _doubled(t_3)


class _Namer:
    __slots__ = ()

    def name(self, s: _Shape, /) -> str:
        if isinstance(s, _Dot):
            return 'a dot'
        elif isinstance(s, _Line):
            return 'a line'
        else:
            raise BaseException('panic')


_console: _LoggingConsole = _LoggingConsole(__name__)
_dot: _Shape = _shape_of(1)
_line: _Shape = _shape_of(2)
_console.log(f'{_named(_dot)} {_named(_line)} {_pair(_dot, _line)} {_pair(_line, _dot)}')
_console.log(f'{_code(_line, True)} {_code(_dot, False)} {_code(_line, False)} {_Namer().name(_line)}')
