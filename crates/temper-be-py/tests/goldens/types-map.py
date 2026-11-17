from collections.abc import Mapping as _Mapping, Sequence as _Sequence
from temper_core import (
    LoggingConsole as _LoggingConsole,
    Pair as _Pair,
    boolean_to_string as _boolean_to_string,
    int_div as _int_div,
    list_get as _list_get,
    list_join as _list_join,
    list_map as _list_map,
    map_builder_set as _map_builder_set,
    map_constructor as _map_constructor,
    mapped_for_each as _mapped_for_each,
    mapped_has as _mapped_has,
    mapped_to_list as _mapped_to_list,
    mapped_to_list_with as _mapped_to_list_with,
    mapped_to_map as _mapped_to_map,
)
from types import MappingProxyType as _MappingProxyType
from typing import TypeVar as _TypeVar

__all__ = ()

_K = _TypeVar('_K')
_V = _TypeVar('_V')


def _list_keys(mapped: _Mapping[_K, _V], /) -> _Sequence[_K]:
    return _list_map(_mapped_to_list(mapped), lambda entry: entry.key)


def _message_defined(code: int, /) -> str:
    return _boolean_to_string(_mapped_has(_messages, code))


def _color_to_hex(color: _Sequence[bool], /) -> str:
    def fn(b: bool, /) -> str:
        if b:
            return 'FF'
        else:
            return '00'
    c = _list_join(color, '', fn)
    return f'#{c}'


def _print_map(name: str, map: _MappingProxyType[str, str], /) -> None:
    _console.log(f'--- {name} ---')
    def fn(key: str, value: str, /) -> None:
        _console.log(f'{key} = {value}')
    _mapped_for_each(map, fn)


def _print_map_builder(name: str, map: dict[str, str], /) -> None:
    _console.log(f'--- {name} ---')
    def fn(key: str, value: str, /) -> None:
        _console.log(f'{key} = {value}')
    _mapped_for_each(map, fn)


_console: _LoggingConsole = _LoggingConsole(__name__)
_messages: _MappingProxyType[int, str] = _map_constructor((_Pair(200, 'OK'), _Pair(404, 'Not Found'), _Pair(500, 'Internal Server Error')))
_message: str = _messages[200]
_console.log(f'Message: {_message}')
_console.log(f'Has 200: {_message_defined(200)}, 302: {_message_defined(302)}')
_t_0: str
try:
    _t_0 = _messages[302]
except Exception:
    _t_0 = 'Found'
_console.log(f'Fallback: {_t_0}')
_console.log(f"Codes: {_list_join(_list_keys(_messages), ' ', lambda it: str(it))}")
_console.log(f"Again: {_list_join(list(_messages.keys()), ' ', lambda it: str(it))}")
_console.log(f"Messages: {_list_join(list(_messages.values()), ', ', lambda it: it)}")
_groups: _Sequence[int] = _mapped_to_list_with(_messages, lambda key, value: _int_div(key, 100))
_console.log(f"Groups: {_list_join(_groups, ' ', lambda it: str(it))}")
_builder2: dict[str, _Sequence[str]] = {}
_map_builder_set(_builder2, 'Honduras', ('El Salvador', 'Guatemala', 'Nicaragua'))
_console.log(f"From builder: {_list_join(_builder2['Honduras'], ', ', lambda it: it)}")
_console.log(f'Key so far: {_list_get(_list_keys(_builder2), 0)}')
_map_builder_set(_builder2, 'El Salvador', ('Guatemala', 'Honduras'))
_map_builder_set(_builder2, 'Nicaragua', ('Costa Rica', 'Honduras'))
_map_builder_set(_builder2, 'Panama', ('Colombia', 'Costa Rica'))
try:
    _builder2.pop('Honduras')
except Exception:
    pass
_was = _builder2.pop('El Salvador')
_console.log(f"Was there: {_list_join(_was, ', ', lambda it: it)}")
_t_1: str
try:
    _t_1 = _list_get(_builder2.pop('El Salvador'), 0)
except Exception:
    _t_1 = 'Gone'
_console.log(_t_1)
_neighbors: _MappingProxyType[str, _Sequence[str]] = _mapped_to_map(_builder2)
_console.log(f"From built: {_list_join(_neighbors['Nicaragua'], ', ', lambda it: it)}")
_map_of_none: _MappingProxyType[int, int] = _map_constructor(())
_map_of_one: _MappingProxyType[str, str] = _map_constructor((_Pair('jack', 'all trades'),))
_map_of_two: _MappingProxyType[int, str] = _map_constructor((_Pair(1, 'one'), _Pair(2, 'electric boogaloo')))
_console.log(f'mapOfNone: {len(_map_of_none)}')
_console.log(f'mapOfOne: {len(_map_of_one)}')
_console.log(f'mapOfTwo: {len(_map_of_two)}')
_eight_colors: dict[str, _Sequence[bool]] = {}
_map_builder_set(_eight_colors, 'black', (False, False, False))
_map_builder_set(_eight_colors, 'red', (True, False, False))
_map_builder_set(_eight_colors, 'green', (False, True, False))
_map_builder_set(_eight_colors, 'blue', (False, False, True))
_map_builder_set(_eight_colors, 'yellow', (True, True, False))
_map_builder_set(_eight_colors, 'cyan', (False, True, True))
_map_builder_set(_eight_colors, 'purple', (True, False, True))
_map_builder_set(_eight_colors, 'white', (True, True, True))
_console.log(f'with 3 bits you can draw {len(_eight_colors)} colors.')


def _fn9(color: str, bits: _Sequence[bool], /) -> None:
    found_by_get_or = _eight_colors.get(color, ())
    if len(found_by_get_or) != 3:
        _console.log(f'getOr("{color}") failed to find key')
    want = _color_to_hex(bits)
    got = _color_to_hex(found_by_get_or)
    if want != got:
        _console.log(f'getOr("{color}") found {got} but wanted {want}')


_mapped_for_each(_eight_colors, _fn9)
_map: _MappingProxyType[str, str] = _map_constructor((_Pair('key', 'value'), _Pair('other key', 'other value')))
_print_map('map', _map)
_builder: dict[str, str] = dict(_map)
_map_builder_set(_builder, 'added key', 'added value')
_map_builder_set(_builder, 'key', 'replaced value')
_print_map_builder('builder', _builder)
_print_map('map (same as above)', _map)
_new_map: _MappingProxyType[str, str] = _mapped_to_map(_builder)
_print_map('builder to map', _new_map)
_builder_as_mapped: _Mapping[str, str] = _builder
_new_map_from_mapped: _MappingProxyType[str, str] = _mapped_to_map(_builder_as_mapped)
_builder.pop('other key')
_map_builder_set(_builder, 'key', 'double replaced value')
_print_map('builder to map (same as above)', _new_map)
_print_map('builder to map via mapped (same as above)', _new_map_from_mapped)
_builder.clear()
_map_builder_set(_builder, 'something', 'alone')
_print_map_builder('recently cleared', _builder)
_may_or_may_not_copy_map: _MappingProxyType[str, str] = _mapped_to_map(_map)
_mapped: _Mapping[str, str] = _map
_may_or_may_not_copy_mapped: _MappingProxyType[str, str] = _mapped_to_map(_mapped)
_made_by: dict[str, str] = {}
_map_builder_set(_made_by, 'temper', 'temper systems')
_print_map_builder('made by', _made_by)
_made_by_and_jokes: dict[str, str] = dict(_made_by)
_map_builder_set(_made_by_and_jokes, 'milk', 'milking a cow')
_print_map_builder('made by (and a joke about milk)', _made_by_and_jokes)
_print_map_builder('made by (not changed from a change to a copy)', _made_by)
