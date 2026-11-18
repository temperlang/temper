from abc import ABCMeta as _ABCMeta, abstractmethod as _abstractmethod
from collections.abc import Sequence as _Sequence
from temper_core import (
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    int_add as _int_add,
    int_div as _int_div,
    int_mul as _int_mul,
    list_get as _list_get,
)

__all__ = ()


class _C:
    __slots__ = ()

    def _helper(self, x: int, /) -> int:
        return _int_add(x, 1)

    def method1(self, x: int, /) -> int:
        return self._helper(_int_mul(x, 2))

    def method2(self, x: int, /) -> int:
        return self._helper(_int_mul(x, 3))


class _Money:
    __slots__ = ('_cents', '_spent')
    _cents: int
    _spent: int

    def __init__(self, /, cents: int) -> None:
        self._cents = cents
        self._spent = 0

    def _whole(self, /) -> int:
        return _int_div(self._cents, 100)

    @property
    def _rounded(self, /) -> int:
        return _int_div(_int_add(self._cents, 50), 100)

    def plus(self, other: '_Money', /) -> '_Money':
        return _Money(_int_add(self._cents, other._cents))

    def same_whole(self, other: '_Money', /) -> bool:
        return self._whole() == other._whole()

    def same_rounded(self, other: '_Money', /) -> bool:
        return self._rounded == other._rounded

    def spend_from(self, other: '_Money', /) -> None:
        other._spent = _int_add(other._spent, 1)

    @property
    def times_spent(self, /) -> int:
        return self._spent

    def to_string(self, /) -> str:
        return f'{self._cents} cents'

    def __str__(self, /) -> str:
        return self.to_string()


class _Sink(metaclass=_ABCMeta):
    __slots__ = ()

    @_abstractmethod
    def end(self, /) -> int:
        ...


class _TenSink(_Sink):
    __slots__ = ()

    def end(self, /) -> int:
        return 10


class _Note:
    __slots__ = ('_body',)
    _body: str

    def __init__(self, /, body: str) -> None:
        self._body = body

    @property
    def body(self, /) -> str:
        return self._body

    def text(self, more: str, /) -> '_Note':
        return _Note(self._body + more)


class _Label:
    __slots__ = ('_end',)
    _end: int

    def __init__(self, /, end: int) -> None:
        self._end = end

    @property
    def text(self, /) -> str:
        return 'label'

    def close(self, sink: _Sink, /) -> int:
        return _int_add(sink.end(), self._end)

    def noted(self, more: str, /) -> str:
        return _Note(self.text).text(more).body


class _Tally:
    __slots__ = ('_depth', '_size')
    _depth: int
    _size: int

    def __init__(self, /, size: int, parent: '_Tally | None', children: '_Sequence[_Tally]') -> None:
        self._depth = 0 if parent is None else _int_add(parent._depth, 1)
        sum = size
        i = 0
        while i < len(children):
            sum = _int_add(sum, _list_get(children, i)._size)
            i = _int_add(i, 1)
        self._size = _int_add(sum, self._depth)

    @property
    def depth(self, /) -> int:
        return self._depth

    @property
    def total(self, /) -> int:
        return self._size


_console: _LoggingConsole = _LoggingConsole(__name__)
_c: _C = _C()
_console.log(f'method1(10)={_c.method1(10)}')
_console.log(f'method2(10)={_c.method2(10)}')
_a: _Money = _Money(150)
_b: _Money = _Money(175)
_console.log(f'plus: {_a.plus(_b).to_string()}')
_console.log(f'same whole: {_boolean_to_string(_a.same_whole(_b))}')
_console.log(f'same rounded: {_boolean_to_string(_a.same_rounded(_b))}')
_a.spend_from(_b)
_a.spend_from(_b)
_console.log(f'b spent: {_b.times_spent}')
_label: _Label = _Label(1)
_console.log(f'close: {_label.close(_TenSink())}')
_console.log(f"noted: {_label.noted('!')}")
_leaf: _Tally = _Tally(2, None, ())
_mid: _Tally = _Tally(3, _leaf, (_leaf, _leaf))
_top: _Tally = _Tally(1, _mid, (_mid, _leaf))
_console.log(f'depths: {_leaf.depth} {_mid.depth} {_top.depth}')
_console.log(f'totals: {_leaf.total} {_mid.total} {_top.total}')
