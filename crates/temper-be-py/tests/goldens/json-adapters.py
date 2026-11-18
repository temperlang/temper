from abc import ABCMeta as _ABCMeta, abstractmethod as _abstractmethod
from collections.abc import Sequence as _Sequence
from temper_core import (
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    cast_by_type as _cast_by_type,
)
from temper_std.json import (
    InterchangeContext as _InterchangeContext,
    JsonAdapter as _JsonAdapter,
    JsonBoolean as _JsonBoolean,
    JsonNumeric as _JsonNumeric,
    JsonObject as _JsonObject,
    JsonProducer as _JsonProducer,
    JsonString as _JsonString,
    JsonSyntaxTree as _JsonSyntaxTree,
    JsonTextProducer as _JsonTextProducer,
    NullInterchangeContext as _NullInterchangeContext,
    OrNullJsonAdapter as _OrNullJsonAdapter,
    list_json_adapter as _list_json_adapter,
    parse_json as _parse_json,
    string_json_adapter as _string_json_adapter,
)
from typing import TypeVar as _TypeVar

__all__ = ()

_T = _TypeVar('_T')


class _Point:
    __slots__ = ('_x', '_y')
    _x: int
    _y: int

    def __init__(self, /, x: int, y: int) -> None:
        self._x = x
        self._y = y

    @property
    def x(self, /) -> int:
        return self._x

    @property
    def y(self, /) -> int:
        return self._y

    def encode_to_json(self, p: _JsonProducer, /) -> None:
        p.start_object()
        p.object_key('x')
        p.int32_value(self._x)
        p.object_key('y')
        p.int32_value(self._y)
        p.end_object()

    @staticmethod
    def decode_from_json(t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> '_Point':
        obj: _JsonObject = _cast_by_type(t, _JsonObject)
        x_json: _JsonNumeric = _cast_by_type(obj.property_value_or_bubble('x'), _JsonNumeric)  # type: ignore[type-abstract]
        x = x_json.as_int32()
        y_json: _JsonNumeric = _cast_by_type(obj.property_value_or_bubble('y'), _JsonNumeric)  # type: ignore[type-abstract]
        y = y_json.as_int32()
        return _Point(x, y)

    @staticmethod
    def json_adapter() -> '_JsonAdapter[_Point]':
        return _PointJsonAdapter()


class _PointJsonAdapter(_JsonAdapter[_Point]):
    __slots__ = ()

    def encode_to_json(self, x: _Point, p: _JsonProducer, /) -> None:
        x.encode_to_json(p)

    def decode_from_json(self, t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> _Point:
        return _Point.decode_from_json(t, ic)


class _Record:
    __slots__ = ('_name', '_active', '_score', '_big', '_nickname', '_points')
    _name: str
    _active: bool
    _score: float
    _big: int
    _nickname: str | None
    _points: _Sequence[_Point]

    def __init__(self, /, name: str, active: bool, score: float, big: int, nickname: str | None, points: _Sequence[_Point]) -> None:
        self._name = name
        self._active = active
        self._score = score
        self._big = big
        self._nickname = nickname
        self._points = points

    @property
    def name(self, /) -> str:
        return self._name

    @property
    def active(self, /) -> bool:
        return self._active

    @property
    def score(self, /) -> float:
        return self._score

    @property
    def big(self, /) -> int:
        return self._big

    @property
    def nickname(self, /) -> str | None:
        return self._nickname

    @property
    def points(self, /) -> _Sequence[_Point]:
        return self._points

    def encode_to_json(self, p: _JsonProducer, /) -> None:
        p.start_object()
        p.object_key('name')
        p.string_value(self._name)
        p.object_key('is-active')
        p.boolean_value(self._active)
        p.object_key('score')
        p.float64_value(self._score)
        p.object_key('big')
        p.int64_value(self._big)
        p.object_key('nickname')
        _OrNullJsonAdapter(_string_json_adapter()).encode_to_json(self._nickname, p)
        p.object_key('points')
        _list_json_adapter(_Point.json_adapter()).encode_to_json(self._points, p)
        p.end_object()

    @staticmethod
    def decode_from_json(t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> '_Record':
        obj: _JsonObject = _cast_by_type(t, _JsonObject)
        name_json: _JsonString = _cast_by_type(obj.property_value_or_bubble('name'), _JsonString)
        name = name_json.content
        active_json: _JsonBoolean = _cast_by_type(obj.property_value_or_bubble('is-active'), _JsonBoolean)
        active = active_json.content
        score_json: _JsonNumeric = _cast_by_type(obj.property_value_or_bubble('score'), _JsonNumeric)  # type: ignore[type-abstract]
        score = score_json.as_float64()
        big_json: _JsonNumeric = _cast_by_type(obj.property_value_or_bubble('big'), _JsonNumeric)  # type: ignore[type-abstract]
        big = big_json.as_int64()
        nickname: str | None = _OrNullJsonAdapter(_string_json_adapter()).decode_from_json(obj.property_value_or_bubble('nickname'), ic)
        points = _list_json_adapter(_Point.json_adapter()).decode_from_json(obj.property_value_or_bubble('points'), ic)
        return _Record(name, active, score, big, nickname, points)

    @staticmethod
    def json_adapter() -> '_JsonAdapter[_Record]':
        return _RecordJsonAdapter()


class _RecordJsonAdapter(_JsonAdapter[_Record]):
    __slots__ = ()

    def encode_to_json(self, x: _Record, p: _JsonProducer, /) -> None:
        x.encode_to_json(p)

    def decode_from_json(self, t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> _Record:
        return _Record.decode_from_json(t, ic)


class _Shape(metaclass=_ABCMeta):
    __slots__ = ()

    @_abstractmethod
    def encode_to_json(self, p: _JsonProducer, /) -> None:
        ...

    @staticmethod
    def decode_from_json(t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> '_Shape':
        obj: _JsonObject = _cast_by_type(t, _JsonObject)
        value_for_radius: _JsonSyntaxTree | None = obj.property_value_or_null('radius')
        if value_for_radius is not None:
            return _Circle.json_adapter().decode_from_json(obj, ic)
        if value_for_radius is None:
            return _Rect.json_adapter().decode_from_json(obj, ic)
        raise RuntimeError()

    @staticmethod
    def json_adapter() -> '_JsonAdapter[_Shape]':
        return _ShapeJsonAdapter()


class _Circle(_Shape):
    __slots__ = ('_radius',)
    _radius: int

    def __init__(self, /, radius: int) -> None:
        self._radius = radius

    @property
    def radius(self, /) -> int:
        return self._radius

    def encode_to_json(self, p: _JsonProducer, /) -> None:
        p.start_object()
        p.object_key('radius')
        p.int32_value(self._radius)
        p.end_object()

    @staticmethod
    def decode_from_json(t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> '_Circle':
        obj: _JsonObject = _cast_by_type(t, _JsonObject)
        radius_json: _JsonNumeric = _cast_by_type(obj.property_value_or_bubble('radius'), _JsonNumeric)  # type: ignore[type-abstract]
        radius = radius_json.as_int32()
        return _Circle(radius)

    @staticmethod
    def json_adapter() -> '_JsonAdapter[_Circle]':  # type: ignore[override]
        return _CircleJsonAdapter()


class _CircleJsonAdapter(_JsonAdapter[_Circle]):
    __slots__ = ()

    def encode_to_json(self, x: _Circle, p: _JsonProducer, /) -> None:
        x.encode_to_json(p)

    def decode_from_json(self, t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> _Circle:
        return _Circle.decode_from_json(t, ic)


class _Rect(_Shape):
    __slots__ = ('_width', '_height')
    _width: int
    _height: int

    def __init__(self, /, width: int, height: int) -> None:
        self._width = width
        self._height = height

    @property
    def width(self, /) -> int:
        return self._width

    @property
    def height(self, /) -> int:
        return self._height

    def encode_to_json(self, p: _JsonProducer, /) -> None:
        p.start_object()
        p.object_key('width')
        p.int32_value(self._width)
        p.object_key('height')
        p.int32_value(self._height)
        p.end_object()

    @staticmethod
    def decode_from_json(t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> '_Rect':
        obj: _JsonObject = _cast_by_type(t, _JsonObject)
        width_json: _JsonNumeric = _cast_by_type(obj.property_value_or_bubble('width'), _JsonNumeric)  # type: ignore[type-abstract]
        width = width_json.as_int32()
        height_json: _JsonNumeric = _cast_by_type(obj.property_value_or_bubble('height'), _JsonNumeric)  # type: ignore[type-abstract]
        height = height_json.as_int32()
        return _Rect(width, height)

    @staticmethod
    def json_adapter() -> '_JsonAdapter[_Rect]':  # type: ignore[override]
        return _RectJsonAdapter()


class _RectJsonAdapter(_JsonAdapter[_Rect]):
    __slots__ = ()

    def encode_to_json(self, x: _Rect, p: _JsonProducer, /) -> None:
        x.encode_to_json(p)

    def decode_from_json(self, t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> _Rect:
        return _Rect.decode_from_json(t, ic)


class _ShapeJsonAdapter(_JsonAdapter[_Shape]):
    __slots__ = ()

    def encode_to_json(self, x: _Shape, p: _JsonProducer, /) -> None:
        x.encode_to_json(p)

    def decode_from_json(self, t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> _Shape:
        return _Shape.decode_from_json(t, ic)


class _Message(metaclass=_ABCMeta):
    __slots__ = ()

    @_abstractmethod
    def encode_to_json(self, p: _JsonProducer, /) -> None:
        ...

    @staticmethod
    def decode_from_json(t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> '_Message':
        obj: _JsonObject = _cast_by_type(t, _JsonObject)
        value_for_kind: _JsonSyntaxTree | None = obj.property_value_or_null('kind')
        if value_for_kind is not None and isinstance(value_for_kind, _JsonString):
            subject = value_for_kind.content
            if subject == 'hello':
                return _Hello.json_adapter().decode_from_json(obj, ic)
            elif subject == 'bye':
                return _Bye.json_adapter().decode_from_json(obj, ic)
        raise RuntimeError()

    @staticmethod
    def json_adapter() -> '_JsonAdapter[_Message]':
        return _MessageJsonAdapter()


class _Hello(_Message):
    __slots__ = ('_text',)
    _text: str

    def __init__(self, /, text: str) -> None:
        self._text = text

    @property
    def text(self, /) -> str:
        return self._text

    def encode_to_json(self, p: _JsonProducer, /) -> None:
        p.start_object()
        p.object_key('text')
        p.string_value(self._text)
        p.object_key('kind')
        p.string_value('hello')
        p.end_object()

    @staticmethod
    def decode_from_json(t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> '_Hello':
        obj: _JsonObject = _cast_by_type(t, _JsonObject)
        text_json: _JsonString = _cast_by_type(obj.property_value_or_bubble('text'), _JsonString)
        text = text_json.content
        return _Hello(text)

    @staticmethod
    def json_adapter() -> '_JsonAdapter[_Hello]':  # type: ignore[override]
        return _HelloJsonAdapter()


class _HelloJsonAdapter(_JsonAdapter[_Hello]):
    __slots__ = ()

    def encode_to_json(self, x: _Hello, p: _JsonProducer, /) -> None:
        x.encode_to_json(p)

    def decode_from_json(self, t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> _Hello:
        return _Hello.decode_from_json(t, ic)


class _Bye(_Message):
    __slots__ = ('_text',)
    _text: str

    def __init__(self, /, text: str) -> None:
        self._text = text

    @property
    def text(self, /) -> str:
        return self._text

    def encode_to_json(self, p: _JsonProducer, /) -> None:
        p.start_object()
        p.object_key('text')
        p.string_value(self._text)
        p.object_key('kind')
        p.string_value('bye')
        p.end_object()

    @staticmethod
    def decode_from_json(t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> '_Bye':
        obj: _JsonObject = _cast_by_type(t, _JsonObject)
        text_json: _JsonString = _cast_by_type(obj.property_value_or_bubble('text'), _JsonString)
        text = text_json.content
        return _Bye(text)

    @staticmethod
    def json_adapter() -> '_JsonAdapter[_Bye]':  # type: ignore[override]
        return _ByeJsonAdapter()


class _ByeJsonAdapter(_JsonAdapter[_Bye]):
    __slots__ = ()

    def encode_to_json(self, x: _Bye, p: _JsonProducer, /) -> None:
        x.encode_to_json(p)

    def decode_from_json(self, t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> _Bye:
        return _Bye.decode_from_json(t, ic)


class _MessageJsonAdapter(_JsonAdapter[_Message]):
    __slots__ = ()

    def encode_to_json(self, x: _Message, p: _JsonProducer, /) -> None:
        x.encode_to_json(p)

    def decode_from_json(self, t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> _Message:
        return _Message.decode_from_json(t, ic)


class _Celsius:
    __slots__ = ('_degrees',)
    _degrees: int

    def __init__(self, /, degrees: int) -> None:
        self._degrees = degrees

    @property
    def degrees(self, /) -> int:
        return self._degrees

    def encode_to_json(self, p: _JsonProducer, /) -> None:
        p.int32_value(self._degrees)

    @staticmethod
    def decode_from_json(t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> '_Celsius':
        return _Celsius(_cast_by_type(t, _JsonNumeric).as_int32())  # type: ignore[type-abstract]

    @staticmethod
    def json_adapter() -> '_JsonAdapter[_Celsius]':
        return _CelsiusJsonAdapter()


class _CelsiusJsonAdapter(_JsonAdapter[_Celsius]):
    __slots__ = ()

    def encode_to_json(self, x: _Celsius, p: _JsonProducer, /) -> None:
        x.encode_to_json(p)

    def decode_from_json(self, t: _JsonSyntaxTree, ic: _InterchangeContext, /) -> _Celsius:
        return _Celsius.decode_from_json(t, ic)


def _encode(adapter: _JsonAdapter[_T], value: _T, /) -> str:
    p = _JsonTextProducer()
    adapter.encode_to_json(value, p)
    try:
        return p.to_json_string()
    except Exception:
        return 'bad JSON'


def _decode(adapter: _JsonAdapter[_T], text: str, /) -> _T:
    return adapter.decode_from_json(_parse_json(text), _NullInterchangeContext.instance)


def _round_trip(adapter: _JsonAdapter[_T], value: _T, /) -> None:
    text = _encode(adapter, value)
    try:
        back = _decode(adapter, text)
    except Exception:
        raise BaseException('panic')
    again = _encode(adapter, back)
    _console.log(text if again == text else f'{text} became {again}')


_console: _LoggingConsole = _LoggingConsole(__name__)
_round_trip(_Point.json_adapter(), _Point(1, -2))
_round_trip(_Record.json_adapter(), _Record('Ada', True, 1.5, 123456789012, None, (_Point(3, 4),)))
_shapes: _Sequence[_Shape] = (_Circle(2), _Rect(3, 4))
_round_trip(_list_json_adapter(_Shape.json_adapter()), _shapes)
_messages: _Sequence[_Message] = (_Hello('hi'), _Bye('so long'))
_round_trip(_list_json_adapter(_Message.json_adapter()), _messages)
_round_trip(_Celsius.json_adapter(), _Celsius(21))
_shape: _Shape
try:
    _shape = _decode(_Shape.json_adapter(), '{"height": 6, "width": 5}')
except Exception:
    raise BaseException('panic')
if isinstance(_shape, _Rect):
    _console.log(f'rect {_shape.width}x{_shape.height}')
else:
    _console.log('not a rect')
_record: _Record
try:
    _record = _decode(_Record.json_adapter(), '{"name": "Bo", "is-active": false, "score": 2, "big": 7, "nickname": "B", "points": []}')
except Exception:
    raise BaseException('panic')
_console.log(f"{_record.name} {_boolean_to_string(_record.active)} {'none' if (_subject := _record.nickname) is None else _subject} {len(_record.points)}")
_t_0: str
try:
    _decode(_Point.json_adapter(), '{"x": 1}')
    _t_0 = 'decoded'
except Exception:
    _t_0 = 'no y'
_console.log(_t_0)
_t_1: str
try:
    _decode(_Message.json_adapter(), '{"kind": "what", "text": "?"}')
    _t_1 = 'decoded'
except Exception:
    _t_1 = 'no such kind'
_console.log(_t_1)
