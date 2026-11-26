from temper_core import (
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    cast_by_type as _cast_by_type,
    int_add as _int_add,
    list_get as _list_get,
)
from temper_std.json import (
    JsonArray as _JsonArray,
    JsonError as _JsonError,
    JsonNull as _JsonNull,
    JsonNumeric as _JsonNumeric,
    JsonObject as _JsonObject,
    JsonSyntaxError as _JsonSyntaxError,
    JsonSyntaxTree as _JsonSyntaxTree,
    JsonSyntaxTreeProducer as _JsonSyntaxTreeProducer,
    JsonTextProducer as _JsonTextProducer,
    NullInterchangeContext as _NullInterchangeContext,
    OrNullJsonAdapter as _OrNullJsonAdapter,
    int32_json_adapter as _int32_json_adapter,
    int64_json_adapter as _int64_json_adapter,
    parse_json as _parse_json,
    parse_json_to_producer as _parse_json_to_producer,
)

__all__ = ()

_console: _LoggingConsole = _LoggingConsole(__name__)
_list = ('[]', ' [] ', ' [ ] ', '[1]', 'null')
_length = len(_list)
_i = 0
while _i < _length:
    _s = _list_get(_list, _i)
    _i = _int_add(_i, 1)
    _json_value: _JsonSyntaxTree | None
    try:
        _json_value = _parse_json(_s)
    except _JsonSyntaxError:
        _json_value = None
    _console.log(f'`{_s}` is empty array: {_boolean_to_string(_json_value is not None and isinstance(_json_value, _JsonArray) and not _json_value.elements)}')
_list2 = ('[', ']', '[,]', '[]]', '[1,]', '[1,,2]')
_length2 = len(_list2)
_i2 = 0
while _i2 < _length2:
    _s2 = _list_get(_list2, _i2)
    _i2 = _int_add(_i2, 1)
    _failed = False
    _p = _JsonSyntaxTreeProducer()
    _parse_json_to_producer(_s2, _p)
    try:
        _p.to_json_syntax_tree()
    except _JsonSyntaxError:
        _failed = True
    _error: str | None = _p.json_error
    _console.log(f"`{_s2}` has{'' if _failed else ' no'} error: {'MISSING ERROR' if _error is None else _error}")
_list3 = ('[1,]', '"a"', '7')
_length3 = len(_list3)
_i3 = 0
while _i3 < _length3:
    _s3 = _list_get(_list3, _i3)
    _i3 = _int_add(_i3, 1)
    try:
        _tree = _parse_json(_s3)
        _decoded = str(_int32_json_adapter().decode_from_json(_tree, _NullInterchangeContext.instance))
    except _JsonError as _e:
        _decoded = _e.message
    _console.log(_decoded)
_select_code_points: str = '\x00\x01\x02\x03\x04\x05\x06\x07\x08\t\n\x0b\x0c\r\x0e\x0f\x10\x11\x12\x13\x14\x15\x16\x17\x18\x19\x1a\x1b\x1c\x1d\x1e\x1f -𐀀--퟿\u2028\u2029\x85'
_p2 = _JsonTextProducer(_NullInterchangeContext.instance)
_p2.string_value(_select_code_points)
_t_0: str
try:
    _t_0 = _p2.to_json_string()
except _JsonSyntaxError:
    _t_0 = '?!'
_console.log(f'string with select code points: {_t_0}')
_p3 = _JsonTextProducer(_NullInterchangeContext.instance)
_p3.start_object()
_int_or_null_adapter = _OrNullJsonAdapter(_int32_json_adapter())
_x: int | None = None
_y: int | None = 123
_p3.object_key('x')
_int_or_null_adapter.encode_to_json(_x, _p3)
_p3.object_key('y')
_int_or_null_adapter.encode_to_json(_y, _p3)
_p3.end_object()
_console.log(_p3.to_json_string())
_s4 = '{"x":null,"y":123,"z":123456789012}'
_ast = _parse_json(_s4)
_obj: _JsonObject = _cast_by_type(_ast, _JsonObject)
_x_ast = _obj.property_value_or_bubble('x')
_y_ast = _obj.property_value_or_bubble('y')
_console.log(f'for x, got JSON null: {_boolean_to_string(isinstance(_x_ast, _JsonNull))}')
_console.log(f'for y, got JSON numeric: {_boolean_to_string(isinstance(_y_ast, _JsonNumeric))}')
_int_or_null_adapter2 = _OrNullJsonAdapter(_int32_json_adapter())
_x2: int | None = _int_or_null_adapter2.decode_from_json(_x_ast, _NullInterchangeContext.instance)
_y2: int | None = _int_or_null_adapter2.decode_from_json(_y_ast, _NullInterchangeContext.instance)
_console.log(f'x ?? -1 -> {-1 if _x2 is None else _x2}')
_console.log(f'y ?? -1 -> {-1 if _y2 is None else _y2}')
_z_ast = _obj.property_value_or_bubble('z')
_int64_or_null_adapter = _OrNullJsonAdapter(_int64_json_adapter())
_z: int | None = _int64_or_null_adapter.decode_from_json(_z_ast, _NullInterchangeContext.instance)
_console.log(f'z ?? -1 -> {str(-1 if _z is None else _z)}')
_normal_json: _JsonTextProducer = _JsonTextProducer()
_parse_json_to_producer('{\n  "foo": "bar",\n  "baz": [\n    "boo",\n    { "f": 1, "a": null, "r": 123.0 }\n  ]\n}\n', _normal_json)
_console.log('Normalized:')
_console.log(_normal_json.to_json_string())
