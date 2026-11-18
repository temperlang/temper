import builtins as _builtins
from collections.abc import (
    Callable as _Callable,
    MutableSequence as _MutableSequence,
    Sequence as _Sequence,
)
from temper_core import (
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string_,
    float_cmp as _float_cmp,
    int_add as _int_add,
    int_negate as _int_negate,
    int_sub as _int_sub,
    list_builder_add as _list_builder_add,
    list_builder_add_all as _list_builder_add_all,
    list_builder_set as _list_builder_set,
    list_builder_sort as _list_builder_sort,
    list_builder_splice as _list_builder_splice,
    list_filter as _list_filter,
    list_get as _list_get,
    list_get_or as _list_get_or,
    list_join as _list_join,
    list_map as _list_map,
    list_slice as _list_slice,
    listed_sorted as _listed_sorted,
    listed_to_list as _listed_to_list,
    string_split as _string_split,
)
from typing import Any as _Any, TypeVar as _TypeVar, cast as _cast

__all__ = ()

_T = _TypeVar('_T')


def _report_length(list: _Sequence[_T], /) -> None:
    _console.log(f'Length: {len(list)}')


def _report_split(string: str, sep: str, /) -> None:
    strings = _string_split(string, sep)
    len = str(_builtins.len(strings))
    _console.log(f"Split {_list_join(strings, ', ', lambda x: x)} - {len}")


def _something(callback: _Callable[[_Sequence[str]], None], /) -> None:
    callback(('Called', 'with', 'listed.'))


def _run_something() -> None:
    def fn(words: _Sequence[str], /) -> None:
        _console.log(_list_join(words, ' ', lambda word: word))
    _something(fn)


_console: _LoggingConsole = _LoggingConsole(__name__)
_report_length((4, 5, 6))
_ls: _Sequence[int] = _listed_sorted((5, 6, 7, 8, 9, 0, 1, 2, 3, 4), lambda i, j: _int_sub(i, j))
_console.log(f"All  {_list_join(_ls, ', ', lambda x: str(x))}")
_console.log(f"Even {_list_join(_list_filter(_ls, lambda x: x & 1 == 0), ', ', lambda x: str(x))}")
_console.log(f"Odd  {_list_join(_list_filter(_ls, lambda x: x & 1 != 0), ', ', lambda x: str(x))}")
_console.log(f"Neg  {_list_join(_list_map(_ls, lambda x: _int_negate(x)), ', ', lambda x: str(x))}")
_listed: _Sequence[int] = _ls
_console.log(f"Neg  {_list_join(_list_map(_listed, lambda x: _int_negate(x)), ', ', lambda x: str(x))}")
_listed_list: _Sequence[int] = _listed_to_list(_listed)
_console.log(f'Listed list length is {len(_listed_list)}')
_console.log('Looping')
_n: int = len(_ls)
_i = 0
while _i < _n:
    _console.log(f'  {_list_get(_ls, _i)}')
    _i = _int_add(_i, 1)
_report_split('a ; b ; c', ' ; ')
_report_split('def', '')
_report_split(' g  h i', ' ')
_report_split(' ', ' ')
_report_split('', ' ')
_report_split('', '')
_report_split('🌍', '')
_t_0 = ((), ('bar',), ('bar', 'baz'), ('bar', 'baz', 'boo'), ('bar', 'baz', 'boo', 'far'), ('bar', 'baz', 'boo', 'far', 'faz'), ('far', 'faz', 'foo', 'bar', 'baz', 'boo'))


def _fn11(list: _Sequence[str], /) -> _MutableSequence[str]:
    builder = _builtins.list(list)
    _list_builder_sort(_cast('_builtins.list[_Any]', builder), lambda a, b: (a > b) - (a < b))
    return builder


_triangle_of_foo_bar: _MutableSequence[_MutableSequence[str]] = list(_list_map(_t_0, _fn11))
_j = 0
while _j < len(_triangle_of_foo_bar):
    _row = _list_get(_triangle_of_foo_bar, _j)
    _console.log(f"Fwd  [{_list_join(_row, ' ', lambda x: x)}]")
    _row.reverse()
    _j = _int_add(_j, 1)
_triangle_of_foo_bar.reverse()
_j2 = 0
while _j2 < len(_triangle_of_foo_bar):
    _row2 = _list_get(_triangle_of_foo_bar, _j2)
    _console.log(f"Back [{_list_join(_row2, ' ', lambda x: x)}]")
    _j2 = _int_add(_j2, 1)
_message_builder: _MutableSequence[str] = []
_message_builder.append('hello')
_list_builder_set(_message_builder, 0, 'hi')
_list_builder_add_all(_message_builder, ('there', "y'all"))
_list_builder_add(_message_builder, 'say', 0)
_copy_that: _MutableSequence[str] = list(_message_builder)
_list_builder_add_all(_message_builder, ('more', 'than'), 1)
_list_builder_add_all(_copy_that, _message_builder)
_console.log(_list_join(_message_builder, ' ', lambda x: x))
_console.log(_list_join(_listed_sorted(_copy_that, lambda a, b: (b > a) - (b < a)), ' ', lambda x: x))
_console.log(_list_join(_copy_that, ' ', lambda x: x))
_old_last: str = _copy_that.pop()
_console.log(f"Removed {_old_last} so {_list_join(_copy_that, ' ', lambda x: x)}")
_next_last: _Sequence[str] = _list_builder_splice(_copy_that, _int_sub(len(_copy_that), 1), 1)
_old_words: _Sequence[str] = _list_builder_splice(_copy_that, 4, 3, _next_last)
_list_builder_splice(_copy_that, None, 0, _old_words)
_list_builder_splice(_copy_that, len(_copy_that), None, ('and', 'the', 'end'))
_console.log(_list_join(_copy_that, ' ', lambda x: x))
_list_builder_splice(_copy_that, None, None, ('gone',))
_console.log(_list_join(_copy_that, ' ', lambda x: x))
_message_builder.clear()
_message_builder.append('Bye')
_console.log(_list_join(_message_builder, ' ', lambda x: x))
_i2 = -2
while _i2 <= 10:
    _console.log(f"Slice({_i2}, {_int_add(_i2, 2)}): [{_list_join(_list_slice(_ls, _i2, _int_add(_i2, 2)), ', ', lambda x: str(x))}]")
    _i2 = _int_add(_i2, 1)
_console.log(f'Outside fallback: {_list_get_or(_ls, len(_ls), 123)}')


def _boolean_to_string(b: bool, /) -> str:
    return _boolean_to_string_(b)


_ls2 = (True, False)
_joined = _list_join(_ls2, ', ', _boolean_to_string)
_inverted = _list_map(_ls2, lambda b: not b)


def _fn25(b: bool, /) -> str:
    if b:
        return 'not false'
    else:
        return 'not true'


_joined_and_inverted = _list_join(_inverted, ', ', _fn25)
_console.log(f'list = [{_joined}]')
_console.log(f'list (each inverted) = [{_joined_and_inverted}]')
_only_true = _list_filter(_ls2, lambda b: b)
_only_false = _list_filter(_ls2, lambda b: not b)
_console.log(f"list (== true only) = [{_list_join(_only_true, ', ', _boolean_to_string)}]")
_console.log(f"list (== false only) = [{_list_join(_only_false, ', ', _boolean_to_string)}]")
_sizes = (0.25, 3.0)


def _fn28(f: float, /) -> str:
    if _float_cmp(f, 1.0) < 0:
        return 'small'
    else:
        return 'big'


_labels = _list_map(_sizes, _fn28)
_console.log(f"sizes = [{_list_join(_labels, ', ', lambda s: s)}]")
_run_something()
_a_list_builder: _MutableSequence[str] = []
_a_list_builder.append('first')
_built_first = tuple(_a_list_builder)
_a_list_builder.append('second')
_built_second = _listed_to_list(_a_list_builder)
_a_list_builder.append('third')
_console.log(f"builtFirst has {_list_join(_built_first, ', ', lambda s: s)}")
_console.log(f"builtSecond has {_list_join(_built_second, ', ', lambda s: s)}")
