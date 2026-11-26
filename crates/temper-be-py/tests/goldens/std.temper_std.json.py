from abc import ABCMeta as _ABCMeta, abstractmethod as _abstractmethod
from collections.abc import (
    MutableSequence as _MutableSequence,
    Sequence as _Sequence,
)
from temper_core import (
    Failure as _Failure,
    InvalidCodePoint as _InvalidCodePoint,
    MissingKey as _MissingKey,
    ParseError as _ParseError,
    cast_none as _cast_none,
    float64_to_int as _float64_to_int,
    float64_to_int64 as _float64_to_int64,
    float64_to_string as _float64_to_string,
    float_eq as _float_eq,
    int64_add as _int64_add,
    int64_mul as _int64_mul,
    int64_negate as _int64_negate,
    int64_sub as _int64_sub,
    int64_to_float64 as _int64_to_float64,
    int64_to_int32 as _int64_to_int32,
    int64_to_int32_unsafe as _int64_to_int32_unsafe,
    int_add as _int_add,
    int_div as _int_div,
    int_mul as _int_mul,
    int_sub as _int_sub,
    list_builder_remove_last as _list_builder_remove_last,
    list_builder_set as _list_builder_set,
    list_get as _list_get,
    list_get_or as _list_get_or,
    map_builder_set as _map_builder_set,
    mapped_for_each as _mapped_for_each,
    mapped_get as _mapped_get,
    mapped_has as _mapped_has,
    mapped_to_map as _mapped_to_map,
    require_string_index as _require_string_index,
    string_from_code_point as _string_from_code_point,
    string_get as _string_get,
    string_has_at_least as _string_has_at_least,
    string_next as _string_next,
    string_to_float64 as _string_to_float64,
    string_to_int32 as _string_to_int32,
    string_to_int64 as _string_to_int64,
    with_cause as _with_cause,
)
from types import MappingProxyType as _MappingProxyType
from typing import (
    ClassVar as _ClassVar,
    Generic as _Generic,
    TypeVar as _TypeVar,
)

__all__ = (
    'JsonError',
    'JsonSyntaxError',
    'JsonShapeError',
    'InterchangeContext',
    'NullInterchangeContext',
    'JsonParseErrorReceiver',
    'JsonProducer',
    'JsonSyntaxTree',
    'JsonObject',
    'JsonArray',
    'JsonBoolean',
    'JsonNull',
    'JsonString',
    'JsonNumeric',
    'JsonInt32',
    'JsonInt64',
    'JsonFloat64',
    'JsonNumericToken',
    'JsonTextProducer',
    'JsonSyntaxTreeProducer',
    'parse_json_to_producer',
    'parse_json',
    'JsonAdapter',
    'boolean_json_adapter',
    'float64_json_adapter',
    'int32_json_adapter',
    'int64_json_adapter',
    'string_json_adapter',
    'list_json_adapter',
    'OrNullJsonAdapter',
)

_T = _TypeVar('_T')


class JsonError(_Failure, Exception):
    __slots__ = ()


class JsonSyntaxError(JsonError):
    __slots__ = ('_problem',)
    _problem: str

    def __init__(self, /, problem: str) -> None:
        self._problem = problem

    @property
    def problem(self, /) -> str:
        return self._problem

    @property
    def message(self, /) -> str:
        return f'json syntax error (problem: {self._problem})'


class JsonShapeError(JsonError):
    __slots__ = ('_problem',)
    _problem: str

    def __init__(self, /, problem: str) -> None:
        self._problem = problem

    @property
    def problem(self, /) -> str:
        return self._problem

    @property
    def message(self, /) -> str:
        return f'json shape error (problem: {self._problem})'


class InterchangeContext(metaclass=_ABCMeta):
    __slots__ = ()

    @_abstractmethod
    def get_header(self, header_name: str, /) -> str | None:
        ...


class NullInterchangeContext(InterchangeContext):
    __slots__ = ()
    instance: _ClassVar['NullInterchangeContext']

    def get_header(self, header_name: str, /) -> str | None:
        return None


class JsonParseErrorReceiver(metaclass=_ABCMeta):
    __slots__ = ()

    @_abstractmethod
    def explain_json_error(self, explanation: str, /) -> None:
        ...


class JsonProducer(metaclass=_ABCMeta):
    __slots__ = ()

    @property
    @_abstractmethod
    def interchange_context(self, /) -> InterchangeContext:
        ...

    @_abstractmethod
    def start_object(self, /) -> None:
        ...

    @_abstractmethod
    def end_object(self, /) -> None:
        ...

    @_abstractmethod
    def object_key(self, key: str, /) -> None:
        ...

    @_abstractmethod
    def start_array(self, /) -> None:
        ...

    @_abstractmethod
    def end_array(self, /) -> None:
        ...

    @_abstractmethod
    def null_value(self, /) -> None:
        ...

    @_abstractmethod
    def boolean_value(self, x: bool, /) -> None:
        ...

    @_abstractmethod
    def int32_value(self, x: int, /) -> None:
        ...

    @_abstractmethod
    def int64_value(self, x: int, /) -> None:
        ...

    @_abstractmethod
    def float64_value(self, x: float, /) -> None:
        ...

    @_abstractmethod
    def numeric_token_value(self, x: str, /) -> None:
        ...

    @_abstractmethod
    def string_value(self, x: str, /) -> None:
        ...

    @property
    def parse_error_receiver(self, /) -> JsonParseErrorReceiver | None:
        return None


class JsonSyntaxTree(metaclass=_ABCMeta):
    __slots__ = ()

    @_abstractmethod
    def produce(self, p: JsonProducer, /) -> None:
        ...


class JsonObject(JsonSyntaxTree):
    __slots__ = ('_properties',)
    _properties: _MappingProxyType[str, _Sequence[JsonSyntaxTree]]

    def __init__(self, /, properties: _MappingProxyType[str, _Sequence[JsonSyntaxTree]]) -> None:
        self._properties = properties

    @property
    def properties(self, /) -> _MappingProxyType[str, _Sequence[JsonSyntaxTree]]:
        return self._properties

    def property_value_or_null(self, property_key: str, /) -> JsonSyntaxTree | None:
        tree_list = self._properties.get(property_key, ())
        last_index = _int_sub(len(tree_list), 1)
        if last_index >= 0:
            return _list_get(tree_list, last_index)
        else:
            return None

    def property_value_or_bubble(self, property_key: str, /) -> JsonSyntaxTree:
        subject: JsonSyntaxTree | None = self.property_value_or_null(property_key)
        if subject is None:
            raise JsonShapeError(f'expected property {property_key}')
        else:
            return subject

    def produce(self, p: JsonProducer, /) -> None:
        p.start_object()
        t_0 = self._properties
        def fn(k: str, vs: _Sequence[JsonSyntaxTree], /) -> None:
            length = len(vs)
            i = 0
            while i < length:
                v = _list_get(vs, i)
                i = _int_add(i, 1)
                p.object_key(k)
                v.produce(p)
        _mapped_for_each(t_0, fn)
        p.end_object()


class JsonArray(JsonSyntaxTree):
    __slots__ = ('_elements',)
    _elements: _Sequence[JsonSyntaxTree]

    def __init__(self, /, elements: _Sequence[JsonSyntaxTree]) -> None:
        self._elements = elements

    @property
    def elements(self, /) -> _Sequence[JsonSyntaxTree]:
        return self._elements

    def produce(self, p: JsonProducer, /) -> None:
        p.start_array()
        list_ = self._elements
        length = len(list_)
        i = 0
        while i < length:
            v = _list_get(list_, i)
            i = _int_add(i, 1)
            v.produce(p)
        p.end_array()


class JsonBoolean(JsonSyntaxTree):
    __slots__ = ('_content',)
    _content: bool

    def __init__(self, /, content: bool) -> None:
        self._content = content

    @property
    def content(self, /) -> bool:
        return self._content

    def produce(self, p: JsonProducer, /) -> None:
        p.boolean_value(self._content)


class JsonNull(JsonSyntaxTree):
    __slots__ = ()

    def produce(self, p: JsonProducer, /) -> None:
        p.null_value()


class JsonString(JsonSyntaxTree):
    __slots__ = ('_content',)
    _content: str

    def __init__(self, /, content: str) -> None:
        self._content = content

    @property
    def content(self, /) -> str:
        return self._content

    def produce(self, p: JsonProducer, /) -> None:
        p.string_value(self._content)


class JsonNumeric(JsonSyntaxTree):
    __slots__ = ()

    @_abstractmethod
    def as_json_numeric_token(self, /) -> str:
        ...

    @_abstractmethod
    def as_int32(self, /) -> int:
        ...

    @_abstractmethod
    def as_int64(self, /) -> int:
        ...

    @_abstractmethod
    def as_float64(self, /) -> float:
        ...


class JsonInt32(JsonNumeric):
    __slots__ = ('_content',)
    _content: int

    def __init__(self, /, content: int) -> None:
        self._content = content

    @property
    def content(self, /) -> int:
        return self._content

    def produce(self, p: JsonProducer, /) -> None:
        p.int32_value(self._content)

    def as_json_numeric_token(self, /) -> str:
        return str(self._content)

    def as_int32(self, /) -> int:
        return self._content

    def as_int32_safe(self, /) -> int:
        return self._content

    def as_int64(self, /) -> int:
        return self.as_int64_safe()

    def as_int64_safe(self, /) -> int:
        return self._content

    def as_float64(self, /) -> float:
        return self.as_float64_safe()

    def as_float64_safe(self, /) -> float:
        return float(self._content)


class JsonInt64(JsonNumeric):
    __slots__ = ('_content',)
    _content: int

    def __init__(self, /, content: int) -> None:
        self._content = content

    @property
    def content(self, /) -> int:
        return self._content

    def produce(self, p: JsonProducer, /) -> None:
        p.int64_value(self._content)

    def as_json_numeric_token(self, /) -> str:
        return str(self._content)

    def as_int32(self, /) -> int:
        return _int64_to_int32(self._content)

    def as_int64(self, /) -> int:
        return self._content

    def as_int64_safe(self, /) -> int:
        return self._content

    def as_float64(self, /) -> float:
        return _int64_to_float64(self._content)


class JsonFloat64(JsonNumeric):
    __slots__ = ('_content',)
    _content: float

    def __init__(self, /, content: float) -> None:
        self._content = content

    @property
    def content(self, /) -> float:
        return self._content

    def produce(self, p: JsonProducer, /) -> None:
        p.float64_value(self._content)

    def as_json_numeric_token(self, /) -> str:
        return _float64_to_string(self._content)

    def as_int32(self, /) -> int:
        return _float64_to_int(self._content)

    def as_int64(self, /) -> int:
        return _float64_to_int64(self._content)

    def as_float64(self, /) -> float:
        return self._content

    def as_float64_safe(self, /) -> float:
        return self._content


class JsonNumericToken(JsonNumeric):
    __slots__ = ('_content',)
    _content: str

    def __init__(self, /, content: str) -> None:
        self._content = content

    @property
    def content(self, /) -> str:
        return self._content

    def produce(self, p: JsonProducer, /) -> None:
        p.numeric_token_value(self._content)

    def as_json_numeric_token(self, /) -> str:
        return self._content

    def as_int32(self, /) -> int:
        try:
            return _string_to_int32(self._content)
        except _ParseError:
            return _float64_to_int(_string_to_float64(self._content))

    def as_int64(self, /) -> int:
        try:
            return _string_to_int64(self._content)
        except _ParseError:
            return _float64_to_int64(_string_to_float64(self._content))

    def as_float64(self, /) -> float:
        return _string_to_float64(self._content)


def _encode_hex4(cp: int, buffer: list[str], /) -> None:
    b0 = _int_div(cp, 4096) & 15
    b1 = _int_div(cp, 256) & 15
    b2 = _int_div(cp, 16) & 15
    b3 = cp & 15
    buffer.append(_list_get(_hex_digits, b0))
    buffer.append(_list_get(_hex_digits, b1))
    buffer.append(_list_get(_hex_digits, b2))
    buffer.append(_list_get(_hex_digits, b3))


def _encode_json_string(x: str, buffer: list[str], /) -> None:
    buffer.append('"')
    i = 0
    emitted = i
    while len(x) > i:
        cp = _string_get(x, i)
        replacement = '\\b' if cp == 8 else '\\t' if cp == 9 else '\\n' if cp == 10 else '\\f' if cp == 12 else '\\r' if cp == 13 else '\\"' if cp == 34 else '\\\\' if cp == 92 else '\\u' if cp < 32 or 55296 <= cp and cp <= 57343 else ''
        next_i = _string_next(x, i)
        if replacement != '':
            buffer.append(x[emitted : i])
            buffer.append(replacement)
            if replacement == '\\u':
                _encode_hex4(cp, buffer)
            emitted = next_i
        i = next_i
    buffer.append(x[emitted : i])
    buffer.append('"')


class JsonTextProducer(JsonProducer):
    __slots__ = ('_interchange_context', '_buffer', '_stack', '_well_formed')
    _interchange_context: InterchangeContext
    _buffer: list[str]
    _stack: _MutableSequence[int]
    _well_formed: bool

    def __init__(self, /, interchange_context: InterchangeContext | None = None) -> None:
        if interchange_context is None:
            interchange_context = NullInterchangeContext.instance
        self._interchange_context = interchange_context
        self._buffer = ['']
        self._stack = []
        self._stack.append(_json_state_no_value)
        self._well_formed = True

    @property
    def interchange_context(self, /) -> InterchangeContext:
        return self._interchange_context

    def _state(self, /) -> int:
        return _list_get_or(self._stack, _int_sub(len(self._stack), 1), -1)

    def _before_value(self, /) -> None:
        current_state = self._state()
        if current_state == _json_state_open_array:
            _list_builder_set(self._stack, _int_sub(len(self._stack), 1), _json_state_after_element)
        elif current_state == _json_state_after_element:
            self._buffer.append(',')
        elif current_state == _json_state_after_key:
            _list_builder_set(self._stack, _int_sub(len(self._stack), 1), _json_state_after_property)
        elif current_state == _json_state_no_value:
            _list_builder_set(self._stack, _int_sub(len(self._stack), 1), _json_state_one_value)
        elif current_state == _json_state_one_value or current_state == _json_state_after_property:
            self._well_formed = False

    def start_object(self, /) -> None:
        self._before_value()
        self._buffer.append('{')
        self._stack.append(_json_state_open_object)

    def end_object(self, /) -> None:
        self._buffer.append('}')
        current_state = self._state()
        if _json_state_open_object == current_state or _json_state_after_property == current_state:
            _list_builder_remove_last(self._stack)
        else:
            self._well_formed = False

    def object_key(self, key: str, /) -> None:
        current_state = self._state()
        if current_state == _json_state_open_object:
            pass
        elif current_state == _json_state_after_property:
            self._buffer.append(',')
        else:
            self._well_formed = False
        _encode_json_string(key, self._buffer)
        self._buffer.append(':')
        if current_state >= 0:
            _list_builder_set(self._stack, _int_sub(len(self._stack), 1), _json_state_after_key)

    def start_array(self, /) -> None:
        self._before_value()
        self._buffer.append('[')
        self._stack.append(_json_state_open_array)

    def end_array(self, /) -> None:
        self._buffer.append(']')
        current_state = self._state()
        if _json_state_open_array == current_state or _json_state_after_element == current_state:
            _list_builder_remove_last(self._stack)
        else:
            self._well_formed = False

    def null_value(self, /) -> None:
        self._before_value()
        self._buffer.append('null')

    def boolean_value(self, x: bool, /) -> None:
        self._before_value()
        self._buffer.append('true' if x else 'false')

    def int32_value(self, x: int, /) -> None:
        self._before_value()
        self._buffer.append(str(x))

    def int64_value(self, x: int, /) -> None:
        self._before_value()
        self._buffer.append(str(x))

    def float64_value(self, x: float, /) -> None:
        self._before_value()
        self._buffer.append(_float64_to_string(x))

    def numeric_token_value(self, x: str, /) -> None:
        self._before_value()
        self._buffer.append(x)

    def string_value(self, x: str, /) -> None:
        self._before_value()
        _encode_json_string(x, self._buffer)

    def to_json_string(self, /) -> str:
        if self._well_formed and (len(self._stack) == 1 and self._state() == _json_state_one_value):
            return ''.join(self._buffer)
        else:
            raise JsonSyntaxError('expected one JSON value')


class JsonSyntaxTreeProducer(JsonProducer, JsonParseErrorReceiver):
    __slots__ = ('_stack', '_error')
    _stack: _MutableSequence[_MutableSequence[JsonSyntaxTree]]
    _error: str | None

    def __init__(self, /) -> None:
        self._stack = []
        self._stack.append([])
        self._error = None

    @property
    def interchange_context(self, /) -> InterchangeContext:
        return NullInterchangeContext.instance

    def _store_value(self, v: JsonSyntaxTree, /) -> None:
        if not not self._stack:
            _list_get(self._stack, _int_sub(len(self._stack), 1)).append(v)

    def start_object(self, /) -> None:
        self._stack.append([])

    def end_object(self, /) -> None:
        if not self._stack:
            return
        ls = _list_builder_remove_last(self._stack)
        m: dict[str, _Sequence[JsonSyntaxTree]] = {}
        multis: dict[str, _MutableSequence[JsonSyntaxTree]] | None = None
        i = 0
        n = len(ls) & -2
        while i < n:
            t = i
            i = _int_add(t, 1)
            key_tree = _list_get(ls, t)
            if not isinstance(key_tree, JsonString):
                break
            key = key_tree.content
            t = i
            i = _int_add(t, 1)
            value = _list_get(ls, t)
            if _mapped_has(m, key):
                if multis is None:  # pyright: ignore[reportUnnecessaryComparison]
                    multis = {}
                mb: dict[str, _MutableSequence[JsonSyntaxTree]] = _cast_none(multis)
                if not _mapped_has(mb, key):
                    t_0: _Sequence[JsonSyntaxTree]
                    try:
                        t_0 = _mapped_get(m, key)
                    except _MissingKey as t_1:
                        raise BaseException('panic') from t_1
                    _map_builder_set(mb, key, list(t_0))
                t_2: _MutableSequence[JsonSyntaxTree]
                try:
                    t_2 = _mapped_get(mb, key)
                except _MissingKey as t_3:
                    raise BaseException('panic') from t_3
                t_2.append(value)
            else:
                _map_builder_set(m, key, (value,))
        multis = multis
        if multis is not None:
            def fn(k: str, vs: _MutableSequence[JsonSyntaxTree], /) -> None:
                _map_builder_set(m, k, tuple(vs))
            _mapped_for_each(multis, fn)
        self._store_value(JsonObject(_mapped_to_map(m)))

    def object_key(self, key: str, /) -> None:
        self._store_value(JsonString(key))

    def start_array(self, /) -> None:
        self._stack.append([])

    def end_array(self, /) -> None:
        if not self._stack:
            return
        ls = _list_builder_remove_last(self._stack)
        self._store_value(JsonArray(tuple(ls)))

    def null_value(self, /) -> None:
        self._store_value(JsonNull())

    def boolean_value(self, x: bool, /) -> None:
        self._store_value(JsonBoolean(x))

    def int32_value(self, x: int, /) -> None:
        self._store_value(JsonInt32(x))

    def int64_value(self, x: int, /) -> None:
        self._store_value(JsonInt64(x))

    def float64_value(self, x: float, /) -> None:
        self._store_value(JsonFloat64(x))

    def numeric_token_value(self, x: str, /) -> None:
        self._store_value(JsonNumericToken(x))

    def string_value(self, x: str, /) -> None:
        self._store_value(JsonString(x))

    def to_json_syntax_tree(self, /) -> JsonSyntaxTree:
        error: str | None = self._error
        if error is not None:
            raise JsonSyntaxError(error)
        if len(self._stack) != 1 or len(_list_get(self._stack, 0)) != 1:
            raise JsonSyntaxError('expected one JSON value')
        return _list_get(_list_get(self._stack, 0), 0)

    @property
    def json_error(self, /) -> str | None:
        return self._error

    @property
    def parse_error_receiver(self, /) -> JsonParseErrorReceiver | None:
        return self

    @property
    def parse_error_receiver_safe(self, /) -> JsonParseErrorReceiver:
        return self

    def explain_json_error(self, error: str, /) -> None:
        self._error = error


def _store_json_error(out: JsonProducer, explanation: str, /) -> None:
    subject: JsonParseErrorReceiver | None = out.parse_error_receiver
    if subject is None:
        pass
    else:
        subject.explain_json_error(explanation)


def _expected_token_error(source_text: str, i: int, out: JsonProducer, short_explanation: str, /) -> None:
    gotten = f'`{source_text[i : len(source_text)]}`' if len(source_text) > i else 'end-of-file'
    _store_json_error(out, f'Expected {short_explanation}, but got {gotten}')


def _skip_json_spaces(source_text: str, i: int, /) -> int:
    while len(source_text) > i:
        subject = _string_get(source_text, i)
        if subject == 9 or (subject == 10 or (subject == 13 or subject == 32)):
            pass
        else:
            break
        i = _string_next(source_text, i)
    return i


def _decode_hex_unsigned(source_text: str, start: int, limit: int, /) -> int:
    n = 0
    i = start
    while i - limit < 0:
        cp = _string_get(source_text, i)
        digit: int
        if 48 <= cp and cp <= 57:
            digit = _int_sub(cp, 48)
        elif 65 <= cp and cp <= 70:
            digit = _int_add(_int_sub(cp, 65), 10)
        elif 97 <= cp and cp <= 102:
            digit = _int_add(_int_sub(cp, 97), 10)
        else:
            return -1
        n = _int_add(_int_mul(n, 16), digit)
        i = _string_next(source_text, i)
    return n


def _parse_json_string_to(source_text: str, i: int, sb: list[str], err_out: JsonProducer, /) -> int:
    if not len(source_text) > i or _string_get(source_text, i) != 34:
        _expected_token_error(source_text, i, err_out, '"')
        return -1
    i = _string_next(source_text, i)
    lead_surrogate = -1
    consumed = i
    while len(source_text) > i:
        cp = _string_get(source_text, i)
        if cp == 34:
            break
        i_next = _string_next(source_text, i)
        end = len(source_text)
        need_to_flush = False
        decoded_cp: int
        if cp != 92:
            decoded_cp = cp
        else:
            need_to_flush = True
            if not len(source_text) > i_next:
                _expected_token_error(source_text, i_next, err_out, 'escape sequence')
                return -1
            esc0 = _string_get(source_text, i_next)
            i_next = _string_next(source_text, i_next)
            if esc0 == 34 or (esc0 == 92 or esc0 == 47):
                decoded_cp = esc0
            elif esc0 == 98:
                decoded_cp = 8
            elif esc0 == 102:
                decoded_cp = 12
            elif esc0 == 110:
                decoded_cp = 10
            elif esc0 == 114:
                decoded_cp = 13
            elif esc0 == 116:
                decoded_cp = 9
            elif esc0 == 117:
                t_0: int
                if _string_has_at_least(source_text, i_next, end, 4):
                    start_hex = i_next
                    i_next = _string_next(source_text, i_next)
                    i_next = _string_next(source_text, i_next)
                    i_next = _string_next(source_text, i_next)
                    i_next = _string_next(source_text, i_next)
                    t_0 = _decode_hex_unsigned(source_text, start_hex, i_next)
                else:
                    t_0 = -1
                hex = t_0
                if hex < 0:
                    _expected_token_error(source_text, i_next, err_out, 'four hex digits')
                    return -1
                decoded_cp = hex
            else:
                _expected_token_error(source_text, i_next, err_out, 'escape sequence')
                return -1
        if lead_surrogate >= 0:
            need_to_flush = True
            lead = lead_surrogate
            if 56320 <= decoded_cp and decoded_cp <= 57343:
                lead_surrogate = -1
                decoded_cp = _int_add(65536, _int_mul(_int_sub(lead, 55296), 1024) | _int_sub(decoded_cp, 56320))
        elif 55296 <= decoded_cp and decoded_cp <= 56319:
            need_to_flush = True
        if need_to_flush:
            sb.append(source_text[consumed : i])
            if lead_surrogate >= 0:
                try:
                    sb.append(_string_from_code_point(lead_surrogate))
                except _InvalidCodePoint as t_1:
                    raise BaseException('panic') from t_1
            if 55296 <= decoded_cp and decoded_cp <= 56319:
                lead_surrogate = decoded_cp
            else:
                lead_surrogate = -1
                try:
                    sb.append(_string_from_code_point(decoded_cp))
                except _InvalidCodePoint as t_2:
                    raise BaseException('panic') from t_2
            consumed = i_next
        i = i_next
    if not len(source_text) > i or _string_get(source_text, i) != 34:
        _expected_token_error(source_text, i, err_out, '"')
        return -1
    else:
        if lead_surrogate >= 0:
            try:
                sb.append(_string_from_code_point(lead_surrogate))
            except _InvalidCodePoint as t_3:
                raise BaseException('panic') from t_3
        else:
            sb.append(source_text[consumed : i])
        i = _string_next(source_text, i)
        return i


def _parse_json_object(source_text: str, i: int, out: JsonProducer, /) -> int:
    if not len(source_text) > i or _string_get(source_text, i) != 123:
        _expected_token_error(source_text, i, out, "'{'")
        return -1
    out.start_object()
    i = _skip_json_spaces(source_text, _string_next(source_text, i))
    if len(source_text) > i and _string_get(source_text, i) != 125:
        while True:
            key_buffer = ['']
            after_key = _parse_json_string_to(source_text, i, key_buffer, out)
            if not after_key >= 0:
                return -1
            out.object_key(''.join(key_buffer))
            i = _skip_json_spaces(source_text, _require_string_index(after_key))
            if len(source_text) > i and _string_get(source_text, i) == 58:
                i = _string_next(source_text, i)
                after_property_value = _parse_json_value(source_text, i, out)
                if not after_property_value >= 0:
                    return -1
                i = _require_string_index(after_property_value)
            else:
                _expected_token_error(source_text, i, out, "':'")
                return -1
            i = _skip_json_spaces(source_text, i)
            if len(source_text) > i and _string_get(source_text, i) == 44:
                i = _skip_json_spaces(source_text, _string_next(source_text, i))
                continue
            else:
                break
    if len(source_text) > i and _string_get(source_text, i) == 125:
        out.end_object()
        return _string_next(source_text, i)
    else:
        _expected_token_error(source_text, i, out, "'}'")
        return -1


def _parse_json_array(source_text: str, i: int, out: JsonProducer, /) -> int:
    if not len(source_text) > i or _string_get(source_text, i) != 91:
        _expected_token_error(source_text, i, out, "'['")
        return -1
    out.start_array()
    i = _skip_json_spaces(source_text, _string_next(source_text, i))
    if len(source_text) > i and _string_get(source_text, i) != 93:
        while True:
            after_element_value = _parse_json_value(source_text, i, out)
            if not after_element_value >= 0:
                return -1
            i = _require_string_index(after_element_value)
            i = _skip_json_spaces(source_text, i)
            if len(source_text) > i and _string_get(source_text, i) == 44:
                i = _skip_json_spaces(source_text, _string_next(source_text, i))
                continue
            else:
                break
    if len(source_text) > i and _string_get(source_text, i) == 93:
        out.end_array()
        return _string_next(source_text, i)
    else:
        _expected_token_error(source_text, i, out, "']'")
        return -1


def _parse_json_string(source_text: str, i: int, out: JsonProducer, /) -> int:
    sb = ['']
    after = _parse_json_string_to(source_text, i, sb, out)
    if after >= 0:
        out.string_value(''.join(sb))
    return after


def _after_substring(string: str, in_string: int, substring: str, /) -> int:
    i = in_string
    j = 0
    while len(substring) > j:
        if not len(string) > i:
            return -1
        if _string_get(string, i) != _string_get(substring, j):
            return -1
        i = _string_next(string, i)
        j = _string_next(substring, j)
    return i


def _parse_json_boolean(source_text: str, i: int, out: JsonProducer, /) -> int:
    ch0 = _string_get(source_text, i) if len(source_text) > i else 0
    end = len(source_text)
    keyword: str | None
    n: int
    if ch0 == 102:
        keyword = 'false'
        n = 5
    elif ch0 == 116:
        keyword = 'true'
        n = 4
    else:
        keyword = None
        n = 0
    if keyword is not None:
        if _string_has_at_least(source_text, i, end, n):
            after = _after_substring(source_text, i, keyword)
            if after >= 0:
                out.boolean_value(n == 4)
                return after
    _expected_token_error(source_text, i, out, '`false` or `true`')
    return -1


def _parse_json_null(source_text: str, i: int, out: JsonProducer, /) -> int:
    after = _after_substring(source_text, i, 'null')
    if after >= 0:
        out.null_value()
        return after
    _expected_token_error(source_text, i, out, '`null`')
    return -1


def _parse_json_number(source_text: str, i: int, out: JsonProducer, /) -> int:
    is_negative = False
    start_of_number = i
    if len(source_text) > i and _string_get(source_text, i) == 45:
        is_negative = True
        i = _string_next(source_text, i)
    digit0 = _string_get(source_text, i) if len(source_text) > i else -1
    if digit0 < 48 or 57 < digit0:
        error = 'JSON value' if not is_negative and digit0 != 46 else 'digit'
        _expected_token_error(source_text, i, out, error)
        return -1
    i = _string_next(source_text, i)
    n_digits = 1
    tentative_float64 = float(_int_sub(digit0, 48))
    tentative_int64 = _int_sub(digit0, 48)
    overflow_int64 = False
    if 48 != digit0:
        while len(source_text) > i:
            possible_digit = _string_get(source_text, i)
            if 48 <= possible_digit and possible_digit <= 57:
                i = _string_next(source_text, i)
                n_digits = _int_add(n_digits, 1)
                next_digit = _int_sub(possible_digit, 48)
                tentative_float64 = tentative_float64 * 10.0 + float(next_digit)
                old_int64 = tentative_int64
                tentative_int64 = _int64_add(_int64_mul(tentative_int64, 10), next_digit)
                if tentative_int64 < old_int64:
                    if _int64_sub(_min_int64, old_int64) == _int64_negate(next_digit) and (is_negative and old_int64 > 0):
                        pass
                    else:
                        overflow_int64 = True
            else:
                break
    n_digits_after_point = 0
    if len(source_text) > i and 46 == _string_get(source_text, i):
        i = _string_next(source_text, i)
        after_point = i
        while len(source_text) > i:
            possible_digit = _string_get(source_text, i)
            if 48 <= possible_digit and possible_digit <= 57:
                i = _string_next(source_text, i)
                n_digits = _int_add(n_digits, 1)
                n_digits_after_point = _int_add(n_digits_after_point, 1)
                tentative_float64 = tentative_float64 * 10.0 + float(_int_sub(possible_digit, 48))
            else:
                break
        if i == after_point:
            _expected_token_error(source_text, i, out, 'digit')
            return -1
    n_exponent_digits = 0
    if len(source_text) > i and 101 == _string_get(source_text, i) | 32:
        i = _string_next(source_text, i)
        if not len(source_text) > i:
            _expected_token_error(source_text, i, out, 'sign or digit')
            return -1
        after_e = _string_get(source_text, i)
        if after_e == 43 or after_e == 45:
            i = _string_next(source_text, i)
        while len(source_text) > i:
            possible_digit = _string_get(source_text, i)
            if 48 <= possible_digit and possible_digit <= 57:
                i = _string_next(source_text, i)
                n_exponent_digits = _int_add(n_exponent_digits, 1)
            else:
                break
        if n_exponent_digits == 0:
            _expected_token_error(source_text, i, out, 'exponent digit')
            return -1
    after_exponent = i  # pyright: ignore[reportUnusedVariable]
    if n_exponent_digits == 0 and (n_digits_after_point == 0 and not overflow_int64):
        value = _int64_negate(tentative_int64) if is_negative else tentative_int64
        if -2147483648 <= value and value <= 2147483647:
            out.int32_value(_int64_to_int32_unsafe(value))
        else:
            out.int64_value(value)
        return i
    numeric_token_string = source_text[start_of_number : i]
    double_value = float('nan')
    if n_exponent_digits != 0 or n_digits_after_point != 0:
        try:
            double_value = _string_to_float64(numeric_token_string)
        except _ParseError:
            double_value = float('nan')
    if not _float_eq(double_value, -float('inf')) and (not _float_eq(double_value, float('inf')) and not _float_eq(double_value, float('nan'))):
        out.float64_value(double_value)
    else:
        out.numeric_token_value(numeric_token_string)
    return i


def _parse_json_value(source_text: str, i: int, out: JsonProducer, /) -> int:
    i = _skip_json_spaces(source_text, i)
    if not len(source_text) > i:
        _expected_token_error(source_text, i, out, 'JSON value')
        return -1
    subject = _string_get(source_text, i)
    if subject == 123:
        return _parse_json_object(source_text, i, out)
    elif subject == 91:
        return _parse_json_array(source_text, i, out)
    elif subject == 34:
        return _parse_json_string(source_text, i, out)
    elif subject == 116 or subject == 102:
        return _parse_json_boolean(source_text, i, out)
    elif subject == 110:
        return _parse_json_null(source_text, i, out)
    else:
        return _parse_json_number(source_text, i, out)


def parse_json_to_producer(source_text: str, out: JsonProducer, /) -> None:
    i = 0
    after_value = _parse_json_value(source_text, i, out)
    if after_value >= 0:
        i = _skip_json_spaces(source_text, after_value)
        if len(source_text) > i and out.parse_error_receiver is not None:
            _store_json_error(out, f'Extraneous JSON `{source_text[i : len(source_text)]}`')


def parse_json(source_text: str, /) -> JsonSyntaxTree:
    p = JsonSyntaxTreeProducer()
    parse_json_to_producer(source_text, p)
    return p.to_json_syntax_tree()


class JsonAdapter(_Generic[_T], metaclass=_ABCMeta):
    __slots__ = ()

    @_abstractmethod
    def encode_to_json(self, x: _T, p: JsonProducer, /) -> None:
        ...

    @_abstractmethod
    def decode_from_json(self, t: JsonSyntaxTree, ic: InterchangeContext, /) -> _T:
        ...


def _shape_error(wanted: str, /) -> JsonShapeError:
    return JsonShapeError(f'expected {wanted}')


class _BooleanJsonAdapter(JsonAdapter[bool]):
    __slots__ = ()

    def encode_to_json(self, x: bool, p: JsonProducer, /) -> None:
        p.boolean_value(x)

    def decode_from_json(self, t: JsonSyntaxTree, ic: InterchangeContext, /) -> bool:
        if isinstance(t, JsonBoolean):
            return t.content
        else:
            raise _shape_error('Boolean')


def boolean_json_adapter() -> JsonAdapter[bool]:
    return _BooleanJsonAdapter()


class _Float64JsonAdapter(JsonAdapter[float]):
    __slots__ = ()

    def encode_to_json(self, x: float, p: JsonProducer, /) -> None:
        p.float64_value(x)

    def decode_from_json(self, t: JsonSyntaxTree, ic: InterchangeContext, /) -> float:
        if isinstance(t, JsonNumeric):
            try:
                return t.as_float64()
            except _ParseError as failure:
                raise _with_cause(_shape_error('Float64'), failure)
        else:
            raise _shape_error('Float64')


def float64_json_adapter() -> JsonAdapter[float]:
    return _Float64JsonAdapter()


class _Int32JsonAdapter(JsonAdapter[int]):
    __slots__ = ()

    def encode_to_json(self, x: int, p: JsonProducer, /) -> None:
        p.int32_value(x)

    def decode_from_json(self, t: JsonSyntaxTree, ic: InterchangeContext, /) -> int:
        if isinstance(t, JsonNumeric):
            try:
                return t.as_int32()
            except _ParseError as failure:
                raise _with_cause(_shape_error('Int'), failure)
        else:
            raise _shape_error('Int')


def int32_json_adapter() -> JsonAdapter[int]:
    return _Int32JsonAdapter()


class _Int64JsonAdapter(JsonAdapter[int]):
    __slots__ = ()

    def encode_to_json(self, x: int, p: JsonProducer, /) -> None:
        p.int64_value(x)

    def decode_from_json(self, t: JsonSyntaxTree, ic: InterchangeContext, /) -> int:
        if isinstance(t, JsonNumeric):
            try:
                return t.as_int64()
            except _ParseError as failure:
                raise _with_cause(_shape_error('Int64'), failure)
        else:
            raise _shape_error('Int64')


def int64_json_adapter() -> JsonAdapter[int]:
    return _Int64JsonAdapter()


class _StringJsonAdapter(JsonAdapter[str]):
    __slots__ = ()

    def encode_to_json(self, x: str, p: JsonProducer, /) -> None:
        p.string_value(x)

    def decode_from_json(self, t: JsonSyntaxTree, ic: InterchangeContext, /) -> str:
        if isinstance(t, JsonString):
            return t.content
        else:
            raise _shape_error('String')


def string_json_adapter() -> JsonAdapter[str]:
    return _StringJsonAdapter()


class _ListJsonAdapter(JsonAdapter[_Sequence[_T]], _Generic[_T]):
    __slots__ = ('_adapter_for_t',)
    _adapter_for_t: JsonAdapter[_T]

    def __init__(self, /, adapter_for_t: JsonAdapter[_T]) -> None:
        self._adapter_for_t = adapter_for_t

    def encode_to_json(self, x: _Sequence[_T], p: JsonProducer, /) -> None:
        p.start_array()
        length = len(x)
        i = 0
        while i < length:
            el = _list_get(x, i)
            i = _int_add(i, 1)
            self._adapter_for_t.encode_to_json(el, p)
        p.end_array()

    def decode_from_json(self, t: JsonSyntaxTree, ic: InterchangeContext, /) -> _Sequence[_T]:
        b: _MutableSequence[_T] = []
        t_0: _Sequence[JsonSyntaxTree]
        if isinstance(t, JsonArray):
            t_0 = t.elements
        else:
            raise _shape_error('List')
        elements = t_0
        n = len(elements)
        i = 0
        while i < n:
            el = _list_get(elements, i)
            i = _int_add(i, 1)
            b.append(self._adapter_for_t.decode_from_json(el, ic))
        return tuple(b)


def list_json_adapter(adapter_for_t: JsonAdapter[_T], /) -> JsonAdapter[_Sequence[_T]]:
    return _ListJsonAdapter(adapter_for_t)


class OrNullJsonAdapter(JsonAdapter[_T | None], _Generic[_T]):
    __slots__ = ('_adapter_for_t',)
    _adapter_for_t: JsonAdapter[_T]

    def __init__(self, /, adapter_for_t: JsonAdapter[_T]) -> None:
        self._adapter_for_t = adapter_for_t

    def encode_to_json(self, x: _T | None, p: JsonProducer, /) -> None:
        if x is None:
            p.null_value()
        else:
            self._adapter_for_t.encode_to_json(x, p)

    def decode_from_json(self, t: JsonSyntaxTree, ic: InterchangeContext, /) -> _T | None:
        if isinstance(t, JsonNull):
            return None
        else:
            return self._adapter_for_t.decode_from_json(t, ic)


NullInterchangeContext.instance = NullInterchangeContext()
_json_state_open_object: int = 0
_json_state_after_key: int = 1
_json_state_after_property: int = 2
_json_state_open_array: int = 3
_json_state_after_element: int = 4
_json_state_no_value: int = 5
_json_state_one_value: int = 6
_hex_digits: _Sequence[str] = ('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
_min_int64: int = _int64_negate(-9223372036854775808)
