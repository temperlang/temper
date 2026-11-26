from temper_core import int_add as _int_add, int_mul as _int_mul

__all__ = ('String', 'List', 'ignore', 'summary')


class String:
    __slots__ = ('_text',)
    _text: int

    def __init__(self, /, text: int) -> None:
        self._text = text

    @property
    def text(self, /) -> int:
        return self._text

    @property
    def doubled(self, /) -> int:
        return _int_mul(self._text, 2)


class List:
    __slots__ = ('_n',)
    _n: int

    def __init__(self, /, n: int) -> None:
        self._n = n

    @property
    def n(self, /) -> int:
        return self._n


def ignore(x: int, /) -> int:
    return _int_add(x, 1)


def summary() -> int:
    s = String(3)
    l = List(4)
    return _int_add(_int_add(_int_add(_int_add(s.text, s.doubled), l.n), ignore(10)), _boolean)


_boolean: int = 7
