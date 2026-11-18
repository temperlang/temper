from collections import deque as _deque
from collections.abc import (
    Callable as _Callable,
    MutableSequence as _MutableSequence,
    Sequence as _Sequence,
)
from temper_core import (
    DenseBitVector as _DenseBitVector,
    LoggingConsole as _LoggingConsole,
    cast_none as _cast_none,
    dense_bit_vector_set as _dense_bit_vector_set,
    deque_remove_first as _deque_remove_first,
    int_add as _int_add,
    int_mul as _int_mul,
    int_sub as _int_sub,
    list_get as _list_get,
    list_slice as _list_slice,
    string_split as _string_split,
)
from typing import Generic as _Generic, TypeVar as _TypeVar

__all__ = (
    'Change',
    'Patch',
    'change_type_addition',
    'change_type_deletion',
    'format_patch',
    'change_type_unchanged',
    'diff',
)

_T = _TypeVar('_T')


class Change(_Generic[_T]):
    __slots__ = ('_type_', '_left_index', '_right_index', '_items')
    _type_: str
    _left_index: int
    _right_index: int
    _items: _Sequence[_T]

    def __init__(self, /, type: str, left_index: int, right_index: int, items: _Sequence[_T]) -> None:
        self._type_ = type
        self._left_index = left_index
        self._right_index = right_index
        self._items = items

    @property
    def type_(self, /) -> str:
        return self._type_

    @property
    def left_index(self, /) -> int:
        return self._left_index

    @property
    def right_index(self, /) -> int:
        return self._right_index

    @property
    def items(self, /) -> _Sequence[_T]:
        return self._items


class Patch(_Generic[_T]):
    __slots__ = ('_changes',)
    _changes: _Sequence[Change[_T]]

    def __init__(self, /, changes: _Sequence[Change[_T]]) -> None:
        self._changes = changes

    @property
    def changes(self, /) -> _Sequence[Change[_T]]:
        return self._changes


def format_patch(patch: Patch[str], /) -> str:
    formatted = ''
    changes = patch.changes
    n = len(changes)
    i = 0
    while i < n:
        change = _list_get(changes, i)
        change_type = change.type_
        items = change.items
        prefix = '+' if change_type == change_type_addition else '-' if change_type == change_type_deletion else ' '
        n_items = len(items)
        j = 0
        while j < n_items:
            item = _list_get(items, j)
            formatted = f'{formatted}{prefix}{item}\n'
            j = _int_add(j, 1)
        i = _int_add(i, 1)
    return formatted


class _DiffPath:
    __slots__ = ('_left_index', '_right_index', '_change_type', '_previous')
    _left_index: int
    _right_index: int
    _change_type: str
    _previous: '_DiffPath | None'

    def __init__(self, /, left_index: int, right_index: int, change_type: str, previous: '_DiffPath | None') -> None:
        self._left_index = left_index
        self._right_index = right_index
        self._change_type = change_type
        self._previous = previous

    @property
    def left_index(self, /) -> int:
        return self._left_index

    @property
    def right_index(self, /) -> int:
        return self._right_index

    @property
    def change_type(self, /) -> str:
        return self._change_type

    @property
    def previous(self, /) -> '_DiffPath | None':
        return self._previous


def diff(left: _Sequence[_T], right: _Sequence[_T], eq: _Callable[[_T, _T], bool], /) -> Patch[_T]:
    left_length = len(left)
    right_length = len(right)
    reached_row_length = _int_add(left_length, 1)
    reached_col_length = _int_add(right_length, 1)
    reached = _DenseBitVector(_int_mul(reached_row_length, reached_col_length))
    cost_zero_edges: _deque[_DiffPath | None] = _deque()
    cost_zero_edges.append(None)
    cost_one_edges: _deque[_DiffPath] = _deque()
    while True:
        diff_path: _DiffPath | None = _deque_remove_first(cost_zero_edges) if not not cost_zero_edges else _deque_remove_first(cost_one_edges) if not not cost_one_edges else None
        left_index = 0 if (subject := None if diff_path is None else diff_path.left_index) is None else subject
        right_index = 0 if (subject := None if diff_path is None else diff_path.right_index) is None else subject
        if left_index == left_length and right_index == right_length:
            path_elements: _MutableSequence[_DiffPath] = []
            path_element: _DiffPath | None = diff_path
            while path_element is not None:  # pyright: ignore[reportUnnecessaryComparison]
                path: _DiffPath = _cast_none(path_element)
                path_elements.append(path)
                path_element = path.previous
            path_elements.reverse()
            left_patch_index = 0
            right_patch_index = 0
            changes: _MutableSequence[Change[_T]] = []
            n = len(path_elements)
            i = 0
            end: int
            while i < n:
                change_type = _list_get(path_elements, i).change_type
                end = _int_add(i, 1)
                while end < n and change_type == _list_get(path_elements, end).change_type:
                    end = _int_add(end, 1)
                n_items = _int_sub(end, i)
                items: _Sequence[_T]
                n_left_items = 0
                n_right_items = 0
                if change_type == change_type_addition:
                    items = _list_slice(right, right_patch_index, _int_add(right_patch_index, n_items))
                    n_right_items = n_items
                else:
                    items = _list_slice(left, left_patch_index, _int_add(left_patch_index, n_items))
                    if change_type == change_type_unchanged:
                        n_right_items = n_items
                    n_left_items = n_items
                changes.append(Change(change_type, left_patch_index, right_patch_index, items))
                left_patch_index = _int_add(left_patch_index, n_left_items)
                right_patch_index = _int_add(right_patch_index, n_right_items)
                i = end
            return Patch(tuple(changes))
        if left_index < left_length:
            if right_index < right_length:
                same_reached_index = _int_add(_int_add(left_index, 1), _int_mul(_int_add(right_index, 1), reached_row_length))
                if not reached.get(same_reached_index) and eq(_list_get(left, left_index), _list_get(right, right_index)):
                    _dense_bit_vector_set(reached, same_reached_index, True)
                    cost_zero_edges.append(_DiffPath(_int_add(left_index, 1), _int_add(right_index, 1), change_type_unchanged, diff_path))
            del_reached_index = _int_add(_int_add(left_index, 1), _int_mul(right_index, reached_row_length))
            if not reached.get(del_reached_index):
                _dense_bit_vector_set(reached, del_reached_index, True)
                cost_one_edges.append(_DiffPath(_int_add(left_index, 1), right_index, change_type_deletion, diff_path))
        if right_index < right_length:
            add_reached_index = _int_add(left_index, _int_mul(_int_add(right_index, 1), reached_row_length))
            if not reached.get(add_reached_index):
                _dense_bit_vector_set(reached, add_reached_index, True)
                cost_one_edges.append(_DiffPath(left_index, _int_add(right_index, 1), change_type_addition, diff_path))


_console: _LoggingConsole = _LoggingConsole(__name__)
change_type_addition: str = 'Addition'
change_type_deletion: str = 'Deletion'
change_type_unchanged: str = 'Unchanged'
_console.log(format_patch(diff(_string_split('Hello,\nWorld!', '\n'), _string_split('Hello,\nCosmos!', '\n'), lambda a, b: a == b)))
