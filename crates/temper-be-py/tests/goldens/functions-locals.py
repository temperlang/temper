from collections.abc import (
    Callable as _Callable,
    MutableSequence as _MutableSequence,
    Sequence as _Sequence,
)
from temper_core import (
    LoggingConsole as _LoggingConsole,
    int_add as _int_add,
    int_mul as _int_mul,
    list_filter as _list_filter,
    list_get as _list_get,
    list_join as _list_join,
    list_map as _list_map,
)
from typing import Generic as _Generic, TypeVar as _TypeVar

__all__ = ('thresh', 'thresh2')

_T = _TypeVar('_T')


def _show(x: _Sequence[int], /) -> str:
    s: _MutableSequence[str] = []
    s.append('[')
    c = ''
    i = 0
    while i < len(x):
        s.append(c)
        s.append(str(_list_get(x, i)))
        c = ', '
        i = _int_add(i, 1)
    s.append(']')
    return _list_join(s, '', lambda v: v)


def _nest_block(b: _Callable[[], _T], /) -> _T:
    return b()


def _nest_block_void(b: _Callable[[], None], /) -> None:
    b()


def _constant_capture(x: _Sequence[int], a: int, /) -> None:
    b = _int_mul(a, 1)
    out = _list_map(x, lambda n: _int_add(n, b))
    _console.log(f'constantCapture = {_show(out)}')


def _constant_capture_nested(x: _Sequence[int], a: int, /) -> None:
    b = _int_mul(a, 1)
    out = _nest_block(lambda: _list_map(x, lambda n: _int_add(n, b)))
    _console.log(f'constantCaptureNested = {_show(out)}')


def _mutable_capture(x: _Sequence[int], /) -> None:
    sum = 0
    def fn(n: int, /) -> int:
        nonlocal sum
        sum = _int_add(sum, n)
        return sum
    out = _list_map(x, fn)
    _console.log(f'mutableCapture = {_show(out)}; sum={sum}')


def _mutable_capture_nested(x: _Sequence[int], /) -> None:
    sum = 0
    def fn() -> _Sequence[int]:
        def fn2(n: int, /) -> int:
            nonlocal sum
            sum = _int_add(sum, n)
            return sum
        return _list_map(x, fn2)
    out = _nest_block(fn)
    _console.log(f'mutableCaptureNested = {_show(out)}; sum={sum}')


def thresh(values: _Sequence[int], value: int, /) -> None:
    value = _int_add(value, 1)
    result = _list_filter(values, lambda it: it > value)
    _console.log(f'thresh length = {len(result)}')


def thresh2(values: _Sequence[int], value: int, /) -> None:
    value = value
    value = _int_add(value, 1)
    result = _list_filter(values, lambda it: it > value)
    _console.log(f'thresh2 length = {len(result)}')


def _self_recursion(x: _Sequence[int], /) -> None:
    def mapper(i: int, out: _MutableSequence[int], /) -> None:
        if i < len(x):
            out.append(_int_mul(_list_get(x, i), 2))
            mapper(_int_add(i, 1), out)
    out: _MutableSequence[int] = []
    mapper(0, out)
    _console.log(f'selfRecursion = {_show(out)}')


def _self_recursion_nested(x: _Sequence[int], /) -> None:
    def mapper(i: int, out: _MutableSequence[int], /) -> None:
        def fn2() -> None:
            if i < len(x):
                out.append(_int_mul(_list_get(x, i), 2))
                mapper(_int_add(i, 1), out)
        _nest_block_void(fn2)
    out2: _MutableSequence[int] = []
    mapper(0, out2)
    _console.log(f'selfRecursionNested = {_show(out2)}')


def _mutual_recursion(x: _Sequence[int], /) -> None:
    def walker(i: int, out: _MutableSequence[int], /) -> None:
        if i < len(x):
            putter(i, out)
    def putter(i: int, out: _MutableSequence[int], /) -> None:
        out.append(_int_mul(_list_get(x, i), 2))
        walker(_int_add(i, 1), out)
    out: _MutableSequence[int] = []
    walker(0, out)
    _console.log(f'mutualRecursion = {_show(out)}')


class _Hub(_Generic[_T]):
    __slots__ = ('_handlers',)
    _handlers: _MutableSequence[_Callable[[_T], None]]

    def __init__(self, /) -> None:
        self._handlers = []

    def on_action(self, handler: _Callable[[_T], None], /) -> None:
        self._handlers.append(handler)

    def action_performed(self, item: _T, /) -> None:
        i = 0
        while i < len(self._handlers):
            _list_get(self._handlers, i)(item)
            i = _int_add(i, 1)


def _gather_handlers(hub: _Hub[int], /) -> _MutableSequence[int]:
    items: _MutableSequence[int] = []
    i = 0
    while i < 3:
        i2 = i
        def make_fn(i2: int, /) -> _Callable[[int], None]:
            def fn(item: int, /) -> None:
                items.append(_int_add(item, i2))
            return fn
        hub.on_action(make_fn(i2))
        i = _int_add(i, 1)
    return items


_console: _LoggingConsole = _LoggingConsole(__name__)
_constant_capture((1, 2, 3), 1)
_constant_capture_nested((1, 2, 3), 1)
_mutable_capture((1, 2, 3))
_mutable_capture_nested((1, 2, 3))
thresh((4, 5, 6), 4)
thresh2((4, 5, 6), 4)
_self_recursion((1, 2, 3))
_self_recursion_nested((1, 2, 3))
_mutual_recursion((1, 2, 3))
_hub: _Hub[int] = _Hub[int]()
_items: _MutableSequence[int] = _gather_handlers(_hub)
_hub.action_performed(1)
