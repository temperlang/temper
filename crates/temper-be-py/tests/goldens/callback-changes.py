from collections.abc import (
    MutableSequence as _MutableSequence,
    Sequence as _Sequence,
)
from temper_core import (
    LoggingConsole as _LoggingConsole,
    int_add as _int_add,
    int_mul as _int_mul,
    int_sub as _int_sub,
    list_builder_add_all as _list_builder_add_all,
    list_builder_remove_last as _list_builder_remove_last,
    list_builder_set as _list_builder_set,
    list_builder_sort as _list_builder_sort,
    list_builder_splice as _list_builder_splice,
    list_filter as _list_filter,
    list_join as _list_join,
    list_map as _list_map,
    listed_reduce as _listed_reduce,
    listed_reduce_from as _listed_reduce_from,
    map_builder_set as _map_builder_set,
    mapped_for_each as _mapped_for_each,
    mapped_has as _mapped_has,
    mapped_to_list_with as _mapped_to_list_with,
)
from typing import Any as _Any, cast as _cast

__all__ = ()


def _show(ls: _Sequence[int], /) -> str:
    return _list_join(ls, ',', lambda n: str(n))


def _words() -> _MutableSequence[str]:
    w: _MutableSequence[str] = []
    i = 0
    while i < 3:
        w.append(f'w{i}')
        i = _int_add(i, 1)
    return w


def _reduce_from_growing() -> int:
    g: _MutableSequence[int] = list((1, 2))
    def fn(sum: int, n: int, /) -> int:
        if len(g) < 4:
            g.append(9)
        return _int_add(_int_mul(sum, 10), n)
    return _listed_reduce_from(g, 0, fn)


def _map_shrinking() -> int:
    w = _words()
    def fn(s: str, /) -> str:
        if len(w) > 1:
            _list_builder_remove_last(w)
        return s
    return len(_list_map(w, fn))


def _reduce_shrinking() -> int:
    w = _words()
    def fn(all: str, s: str, /) -> str:
        if len(w) > 1:
            _list_builder_remove_last(w)
        return all
    all2 = _listed_reduce(w, fn)
    if not all2:
        return 0
    else:
        return 1


def _filter_growing() -> int:
    g: _MutableSequence[int] = list((1, 2))
    def fn(n: int, /) -> bool:
        if len(g) < 4:
            g.append(9)
        return True
    return len(_list_filter(g, fn))


def _join_growing() -> int:
    g: _MutableSequence[int] = list((1, 2))
    def fn(n: int, /) -> str:
        if len(g) < 4:
            g.append(9)
        return str(n)
    joined = _list_join(g, ',', fn)
    if not joined:
        return 0
    else:
        return 1


def _letters() -> dict[str, int]:
    m: dict[str, int] = {}
    _map_builder_set(m, f'a{1}', 1)
    _map_builder_set(m, f'b{2}', 2)
    _map_builder_set(m, f'c{3}', 3)
    return m


def _for_each_removing() -> int:
    m = _letters()
    sum = 0
    def fn(k: str, v: int, /) -> None:
        nonlocal sum
        if _mapped_has(m, 'c3'):
            try:
                m.pop('c3')
            except Exception:
                pass
        sum = _int_add(sum, v)
    _mapped_for_each(m, fn)
    return sum


def _for_each_adding() -> int:
    m = _letters()
    count = 0
    def fn(k: str, v: int, /) -> None:
        nonlocal count
        count = _int_add(count, 1)
        if len(m) < 30:
            _map_builder_set(m, f'k{len(m)}', 1)
    _mapped_for_each(m, fn)
    return count


def _to_list_with_removing() -> int:
    m = _letters()
    def fn(k: str, v: int, /) -> int:
        if _mapped_has(m, 'c3'):
            try:
                m.pop('c3')
            except Exception:
                pass
        return v
    return len(_mapped_to_list_with(m, fn))


_console: _LoggingConsole = _LoggingConsole(__name__)
_b: _MutableSequence[int] = list((1, 2))
_list_builder_add_all(_b, _b)
_c: _MutableSequence[int] = list((1, 2))
_list_builder_add_all(_c, _c, 1)
_d: _MutableSequence[int] = list((1, 2, 3))
_removed: _Sequence[int] = _list_builder_splice(_d, 1, 1, _d)
_console.log(f'self {_show(_b)} {_show(_c)} {_show(_d)} {_show(_removed)}')
_e: _MutableSequence[int] = list((1, 2))
_kept: _Sequence[int] = _list_filter(_e, lambda n: n > 0)
_e.append(3)
_console.log(f'filter {_show(_kept)} {_show(_e)}')
_f: _MutableSequence[int] = list((3, 1, 2))
_peeks: int = 0


def _fn2(a: int, z: int, /) -> int:
    global _peeks
    _peeks = _int_add(_peeks, len(_f))
    return _int_sub(a, z)


_list_builder_sort(_cast('list[_Any]', _f), _fn2)
_console.log(f'peeking {_show(_f)}')
_t_0: int
try:
    _t_0 = _reduce_from_growing()
except Exception:
    _t_0 = -1
_t_1: int
try:
    _t_1 = _map_shrinking()
except Exception:
    _t_1 = -1
_t_2: int
try:
    _t_2 = _reduce_shrinking()
except Exception:
    _t_2 = -1
_t_3: int
try:
    _t_3 = _filter_growing()
except Exception:
    _t_3 = -1
_t_4: int
try:
    _t_4 = _join_growing()
except Exception:
    _t_4 = -1
_t_5: int
try:
    _t_5 = _for_each_removing()
except Exception:
    _t_5 = -1
_t_6: int
try:
    _t_6 = _for_each_adding()
except Exception:
    _t_6 = -1
_t_7: int
try:
    _t_7 = _to_list_with_removing()
except Exception:
    _t_7 = -1
_walked: _Sequence[int] = (_t_0, _t_1, _t_2, _t_3, _t_4, _t_5, _t_6, _t_7)
_console.log(f'walks {_show(_walked)}')
_t: _MutableSequence[int] = list((3, 1, 2))


def _fn3(a: int, z: int, /) -> int:
    _list_builder_set(_t, 0, 99)
    return _int_sub(a, z)


_list_builder_sort(_cast('list[_Any]', _t), _fn3)
_u: _MutableSequence[int] = list((3, 1, 2))


def _fn4(a: int, z: int, /) -> int:
    last = _list_builder_remove_last(_u)
    _u.append(_int_add(last, 10))
    return _int_sub(a, z)


_list_builder_sort(_cast('list[_Any]', _u), _fn4)
_console.log(f'same length {_show(_t)} {_show(_u)}')
_s: _MutableSequence[int] = list((3, 1, 2))


def _fn5(a: int, z: int, /) -> int:
    if len(_s) < 40:
        _s.append(0)
    return _int_sub(a, z)


_list_builder_sort(_cast('list[_Any]', _s), _fn5)
_console.log(f'sorted {len(_s)}')
