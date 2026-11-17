from collections.abc import (
    MutableSequence as _MutableSequence,
    Sequence as _Sequence,
)
from temper_core import (
    LoggingConsole as _LoggingConsole,
    float64_sign as _float64_sign,
    float64_to_int_unsafe as _float64_to_int_unsafe,
    float64_to_string as _float64_to_string,
    int_sub as _int_sub,
    list_builder_sort as _list_builder_sort,
    list_get as _list_get,
    list_join as _list_join,
    listed_sorted as _listed_sorted,
)
from typing import Any as _Any, cast as _cast

__all__ = ()


class _Entry:
    __slots__ = ('_id', '_group')
    _id: int
    _group: int

    def __init__(self, /, id: int, group: int) -> None:
        self._id = id
        self._group = group

    @property
    def id(self, /) -> int:
        return self._id

    @property
    def group(self, /) -> int:
        return self._group

    def to_string(self, /) -> str:
        return f'{{ id: {self._id}, group: {self._group} }}'

    def __str__(self, /) -> str:
        return self.to_string()


def _munge_ints(ints: _Sequence[int], /) -> None:
    builder = list(ints)
    _list_builder_sort(_cast('list[_Any]', builder), lambda a, b: _int_sub(a, b))
    _console.log(_list_join(_listed_sorted(ints, lambda a, b: _int_sub(a, b)), ', ', lambda a: str(a)))
    _console.log(_list_join(builder, ', ', lambda a: str(a)))


def _munge_floats(floats: _Sequence[float], /) -> None:
    builder = list(floats)
    def compare_floats(a: float, b: float, /) -> int:
        return _float64_to_int_unsafe(_float64_sign(a - b))
    _list_builder_sort(_cast('list[_Any]', builder), compare_floats)
    _console.log(_list_join(_listed_sorted(floats, compare_floats), ', ', lambda a: _float64_to_string(a)))
    _console.log(_list_join(builder, ', ', lambda a: _float64_to_string(a)))


_console: _LoggingConsole = _LoggingConsole(__name__)
_entries: _MutableSequence[_Entry] = list((_Entry(0, 0), _Entry(1, 1), _Entry(2, 2), _Entry(3, 0), _Entry(4, 1), _Entry(5, 2), _Entry(6, 0), _Entry(7, 1), _Entry(8, 2)))
_sorted_entries: _Sequence[_Entry] = _listed_sorted(_entries, lambda a, b: _int_sub(a.group, b.group))
_console.log(_list_join(_sorted_entries, '\n', lambda a: a.to_string()))
_console.log(_list_get(_entries, 1).to_string())
_list_builder_sort(_cast('list[_Any]', _entries), lambda a, b: _int_sub(a.group, b.group))
_console.log(_list_join(_entries, '\n', lambda a: a.to_string()))
_munge_ints((2, 1))
_munge_floats((3.4, 1.2))
