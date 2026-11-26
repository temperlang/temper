from collections.abc import (
    Callable as _Callable,
    MutableSequence as _MutableSequence,
    Sequence as _Sequence,
)
from temper_core import (
    Failure as _Failure,
    LoggingConsole as _LoggingConsole,
    int_add as _int_add,
    int_mul as _int_mul,
    int_sub as _int_sub,
    list_filter as _list_filter,
    list_get as _list_get,
    list_join as _list_join,
    list_map as _list_map,
    listed_reduce_from as _listed_reduce_from,
    listed_sorted as _listed_sorted,
)
from typing import TypeVar as _TypeVar

__all__ = ()

_T = _TypeVar('_T')
_R = _TypeVar('_R')


class _FailureParamsError(_Failure, Exception):
    __slots__ = ()


class _Missing(_FailureParamsError):
    __slots__ = ('_key',)
    _key: str

    def __init__(self, /, key: str) -> None:
        self._key = key

    @property
    def key(self, /) -> str:
        return self._key

    @property
    def message(self, /) -> str:
        return f'missing (key: {self._key})'


def _find(key: str, /) -> int:
    if key == 'one':
        return 1
    if key == 'two':
        return 2
    raise _Missing(key)


def _map_all(xs: _Sequence[_T], f: _Callable[[_T], _R], /) -> _Sequence[_R]:
    out: _MutableSequence[_R] = []
    length = len(xs)
    i = 0
    while i < length:
        x = _list_get(xs, i)
        i = _int_add(i, 1)
        out.append(f(x))
    return tuple(out)


def _found(keys: _Sequence[str], /) -> _Sequence[int]:
    return _map_all(keys, lambda k: _find(k))


def _shown(keys: _Sequence[str], /) -> str:
    ns: _Sequence[int]
    caught: _Missing | None = None
    held: _Sequence[int] | None = None
    try:
        held = _found(keys)
    except _Missing as failure:
        caught = failure
    if held is None:  # pyright: ignore[reportUnnecessaryComparison]
        assert caught is not None
        e = caught
        return f'missing {e.key}'
    else:
        ns = held
    return _list_join(ns, ',', lambda n: str(n))


def _applied(f: _Callable[[int], int], n: int, /) -> int:
    return f(n)


def _failures(xs: _Sequence[_T], f: _Callable[[_T], _R], /) -> int:
    failed = 0
    length = len(xs)
    i = 0
    while i < length:
        x = _list_get(xs, i)
        i = _int_add(i, 1)
        try:
            f(x)
        except Exception as t_0:
            if not isinstance(t_0, _Failure):
                raise t_0
            failed = _int_add(failed, 1)
    return failed


def _sum_all(xs: _Sequence[_T], f: _Callable[[_T], int], /) -> int:
    total = 0
    first: _Failure | None = None
    length = len(xs)
    i = 0
    while i < length:
        x = _list_get(xs, i)
        i = _int_add(i, 1)
        try:
            total = _int_add(total, f(x))
        except Exception as e:
            if not isinstance(e, _Failure):
                raise e
            if first is None:  # pyright: ignore[reportUnnecessaryComparison]
                first = e
    failed: _Failure | None = first
    if failed is not None:
        raise failed
    return total


def _both(a: _Callable[[str], int], b: _Callable[[str], int], key: str, /) -> int:
    return _int_add(a(key), b(key))


class _Unknown(_FailureParamsError):
    __slots__ = ('_word',)
    _word: str

    def __init__(self, /, word: str) -> None:
        self._word = word

    @property
    def word(self, /) -> str:
        return self._word

    @property
    def message(self, /) -> str:
        return f'unknown (word: {self._word})'


def _word(key: str, /) -> int:
    if key == 'ten':
        return 10
    raise _Unknown(key)


def _either(key: str, /) -> str:
    n: int
    caught: _FailureParamsError | None = None
    held: int | None = None
    try:
        held = _both(lambda x: _find(x), lambda x: _word(x), key)
    except _FailureParamsError as failure:
        caught = failure
    if held is None:  # pyright: ignore[reportUnnecessaryComparison]
        assert caught is not None
        e = caught
        t_1: str
        if isinstance(e, _Missing):
            t_1 = f'no {e.key}'
        else:
            t_0: str
            if isinstance(e, _Unknown):
                t_0 = f'unknown {e.word}'
            else:
                raise BaseException('panic')
            t_1 = t_0
        return t_1
    else:
        n = held
    return str(n)


def _parts(keys: _Sequence[str], /) -> str:
    found = _list_filter(keys, lambda k: _find(k) > 1)
    total = _listed_reduce_from(keys, 0, lambda n, k: _int_add(n, _find(k)))
    return f"{_list_join(found, ',', lambda k: k)} {total} {_list_join(keys, '+', lambda k: str(_find(k)))}"


def _map_twice(xs: _Sequence[_T], f: _Callable[[_T], _T], /) -> _Sequence[_T]:
    once = _map_all(xs, f)
    return _list_map(once, f)


def _up_to_two(n: int, /) -> int:
    if n > 2:
        raise _Missing(str(n))
    return _int_add(n, 1)


_console: _LoggingConsole = _LoggingConsole(__name__)
_doubled: _Sequence[int] = _map_all((1, 2, 3), lambda n: _int_mul(n, 2))
_safe: _Callable[[int], int] = lambda n: _int_add(n, 1)
_console.log(f"{_list_join(_doubled, ',', lambda n: str(n))} {_safe(1)}")
_named: _Sequence[int]
try:
    _named = _map_all(('two', 'one'), _find)
except _Missing:
    _named = ()
_console.log(f"{_shown(('one', 'two'))} {_shown(('one', 'six', 'ten'))} {len(_named)}")
_six: int
try:
    _six = _applied(lambda n: _int_mul(n, 3), 2)
except _Missing:
    _six = -1
_console.log(str(_six))
_some: int = _failures(('one', 'six', 'two', 'ten'), _find)
_none: int = _failures((1, 2), lambda n: n)
_console.log(f'{_some} {_none}')
_summed: int
try:
    _summed = _sum_all(('one', 'two'), _find)
except _Missing:
    _summed = -1
_unsummed: int
try:
    _unsummed = _sum_all(('six', 'one', 'ten'), _find)
except _Missing as _e:
    _console.log(f'first missing {_e.key}')
    _unsummed = -1
_console.log(f'{_summed} {_unsummed} {_sum_all((1, 2), _safe)}')
_twelve: int
try:
    _twelve = _both(_find, lambda k: 10, 'two')
except _Missing:
    _twelve = -1
_lost: int
try:
    _lost = _both(_find, _find, 'six')
except _Missing as _e2:
    _console.log(f'lost {_e2.key}')
    _lost = -1
_console.log(f'{_twelve} {_lost}')
_console.log(f"{_either('one')} {_either('ten')}")
_t_0: str
try:
    _t_0 = _parts(('one', 'two'))
except _Missing:
    _t_0 = '?'
_console.log(_t_0)
_t_1: str
try:
    _t_1 = _parts(('one', 'six'))
except _Missing as _e3:
    _t_1 = f'missing {_e3.key}'
_console.log(_t_1)
_console.log(_list_join(_listed_sorted((3, 1, 2), lambda a, b: _int_sub(a, b)), ',', lambda n: str(n)))
_bumped: _Sequence[int] = _list_map((1, 2), _safe)
_twice: _Sequence[int] = _map_twice((1, 2), _safe)
_failed: _Sequence[int]
try:
    _failed = _map_twice((1, 2), _up_to_two)
except _Missing as _e4:
    _console.log(f'missing {_e4.key}')
    _failed = ()
_console.log(f"{_list_join(_bumped, ',', lambda n: str(n))} {_list_join(_twice, ',', lambda n: str(n))} {len(_failed)}")
