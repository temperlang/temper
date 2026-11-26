from abc import ABCMeta as _ABCMeta, abstractmethod as _abstractmethod
from collections.abc import (
    Callable as _Callable,
    MutableSequence as _MutableSequence,
    Sequence as _Sequence,
)
from temper_core import (
    InvalidCodePoint as _InvalidCodePoint,
    Pair as _Pair,
    int_add as _int_add,
    list_get as _list_get,
    map_constructor as _map_constructor,
    string_from_code_point as _string_from_code_point,
    string_get as _string_get,
    string_next as _string_next,
)
from temper_core.regex import (
    regex_compile_formatted as _regex_compile_formatted,
    regex_compiled_find as _regex_compiled_find,
    regex_compiled_found as _regex_compiled_found,
    regex_compiled_replace as _regex_compiled_replace,
    regex_compiled_split as _regex_compiled_split,
    regex_formatter_push_capture_name as _regex_formatter_push_capture_name,
    regex_formatter_push_code_to as _regex_formatter_push_code_to,
)
from types import MappingProxyType as _MappingProxyType
from typing import ClassVar as _ClassVar

__all__ = (
    'Capture',
    'CodePart',
    'CodePoints',
    'CodeRange',
    'CodeSet',
    'Or',
    'Repeat',
    'Sequence',
    'Special',
    'begin',
    'dot',
    'end',
    'word_boundary',
    'SpecialSet',
    'digit',
    'space',
    'word',
    'Group',
    'Match',
    'Regex',
    'RegexNode',
    'entire',
    'one_or_more',
    'optional',
)


class RegexNode(metaclass=_ABCMeta):
    __slots__ = ()

    def compiled(self, /) -> 'Regex':
        return Regex(self)

    def found(self, text: str, /) -> bool:
        return self.compiled().found(text)

    def find(self, text: str, /) -> 'Match':
        return self.compiled().find(text)

    def replace(self, text: str, format: '_Callable[[Match], str]', /) -> str:
        return self.compiled().replace(text, format)

    def split(self, text: str, /) -> _Sequence[str]:
        return self.compiled().split(text)

    @_abstractmethod
    def eq(self, other: 'RegexNode', /) -> bool:
        ...


class Capture(RegexNode):
    __slots__ = ('_name', '_item')
    _name: str
    _item: RegexNode

    def __init__(self, /, name: str, item: RegexNode) -> None:
        self._name = name
        self._item = item

    @property
    def name(self, /) -> str:
        return self._name

    @property
    def item(self, /) -> RegexNode:
        return self._item

    def eq(self, other: RegexNode, /) -> bool:
        if isinstance(other, Capture):
            if self._name == other._name:
                return self._item.eq(other._item)
            else:
                return False
        else:
            return False

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, RegexNode) and self.eq(other)


class CodePart(RegexNode):
    __slots__ = ()


class CodePoints(CodePart):
    __slots__ = ('_value',)
    _value: str

    def __init__(self, /, value: str) -> None:
        self._value = value

    @property
    def value(self, /) -> str:
        return self._value

    def eq(self, other: RegexNode, /) -> bool:
        if isinstance(other, CodePoints):
            return self._value == other._value
        else:
            return False

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, RegexNode) and self.eq(other)


class CodeRange(CodePart):
    __slots__ = ('_min', '_max')
    _min: int
    _max: int

    def __init__(self, /, min: int, max: int) -> None:
        self._min = min
        self._max = max

    @property
    def min(self, /) -> int:
        return self._min

    @property
    def max(self, /) -> int:
        return self._max

    def eq(self, other: RegexNode, /) -> bool:
        if isinstance(other, CodeRange):
            if self._min == other._min:
                return self._max == other._max
            else:
                return False
        else:
            return False

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, RegexNode) and self.eq(other)


class CodeSet(RegexNode):
    __slots__ = ('_items', '_negated')
    _items: _Sequence[CodePart]
    _negated: bool

    def __init__(self, /, items: _Sequence[CodePart], negated: bool | None = None) -> None:
        if negated is None:
            negated = False
        self._items = items
        self._negated = negated

    @property
    def items(self, /) -> _Sequence[CodePart]:
        return self._items

    @property
    def negated(self, /) -> bool:
        return self._negated

    def eq(self, other: RegexNode, /) -> bool:
        if isinstance(other, CodeSet):
            if len(self._items) != len(other._items):
                return False
            i = 0
            while i < len(self._items):
                if not _list_get(self._items, i).eq(_list_get(other._items, i)):
                    return False
                i = _int_add(i, 1)
            return True
        else:
            return False

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, RegexNode) and self.eq(other)


def _regex_node_list_eq(a: _Sequence[RegexNode], b: _Sequence[RegexNode], /) -> bool:
    if len(a) != len(b):
        return False
    i = 0
    while i < len(a):
        if not _list_get(a, i).eq(_list_get(b, i)):
            return False
        i = _int_add(i, 1)
    return True


class Or(RegexNode):
    __slots__ = ('_items',)
    _items: _Sequence[RegexNode]

    def __init__(self, /, items: _Sequence[RegexNode]) -> None:
        self._items = items

    @property
    def items(self, /) -> _Sequence[RegexNode]:
        return self._items

    def eq(self, other: RegexNode, /) -> bool:
        if isinstance(other, Or):
            return _regex_node_list_eq(self._items, other._items)
        else:
            return False

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, RegexNode) and self.eq(other)


class Repeat(RegexNode):
    __slots__ = ('_item', '_min', '_max', '_reluctant')
    _item: RegexNode
    _min: int
    _max: int | None
    _reluctant: bool

    def __init__(self, /, item: RegexNode, min: int, max: int | None, reluctant: bool | None = None) -> None:
        if reluctant is None:
            reluctant = False
        self._item = item
        self._min = min
        self._max = max
        self._reluctant = reluctant

    @property
    def item(self, /) -> RegexNode:
        return self._item

    @property
    def min(self, /) -> int:
        return self._min

    @property
    def max(self, /) -> int | None:
        return self._max

    @property
    def reluctant(self, /) -> bool:
        return self._reluctant

    def eq(self, other: RegexNode, /) -> bool:
        if isinstance(other, Repeat):
            if self._item.eq(other._item):
                if self._min == other._min:
                    if self._max == other._max:
                        return self._reluctant == other._reluctant
                    else:
                        return False
                else:
                    return False
            else:
                return False
        else:
            return False

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, RegexNode) and self.eq(other)


class Sequence(RegexNode):
    __slots__ = ('_items',)
    _items: _Sequence[RegexNode]

    def __init__(self, /, items: _Sequence[RegexNode]) -> None:
        self._items = items

    @property
    def items(self, /) -> _Sequence[RegexNode]:
        return self._items

    def eq(self, other: RegexNode, /) -> bool:
        if isinstance(other, Sequence):
            return _regex_node_list_eq(self._items, other._items)
        else:
            return False

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, RegexNode) and self.eq(other)


class Special(RegexNode):
    __slots__ = ()


class _Begin(Special):
    __slots__ = ()

    def eq(self, other: RegexNode, /) -> bool:
        return isinstance(other, _Begin)

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, RegexNode) and self.eq(other)


class _Dot(Special):
    __slots__ = ()

    def eq(self, other: RegexNode, /) -> bool:
        return isinstance(other, _Dot)

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, RegexNode) and self.eq(other)


class _End(Special):
    __slots__ = ()

    def eq(self, other: RegexNode, /) -> bool:
        return isinstance(other, _End)

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, RegexNode) and self.eq(other)


class _WordBoundary(Special):
    __slots__ = ()

    def eq(self, other: RegexNode, /) -> bool:
        return isinstance(other, _WordBoundary)

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, RegexNode) and self.eq(other)


class SpecialSet(CodePart, Special):
    __slots__ = ()


class _Digit(SpecialSet):
    __slots__ = ()

    def eq(self, other: RegexNode, /) -> bool:
        return isinstance(other, _Digit)

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, RegexNode) and self.eq(other)


class _Space(SpecialSet):
    __slots__ = ()

    def eq(self, other: RegexNode, /) -> bool:
        return isinstance(other, _Space)

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, RegexNode) and self.eq(other)


class _Word(SpecialSet):
    __slots__ = ()

    def eq(self, other: RegexNode, /) -> bool:
        return isinstance(other, _Word)

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, RegexNode) and self.eq(other)


class _Codes:
    __slots__ = ()
    ampersand: _ClassVar[int]
    backslash: _ClassVar[int]
    caret: _ClassVar[int]
    carriage_return: _ClassVar[int]
    curly_left: _ClassVar[int]
    curly_right: _ClassVar[int]
    dash: _ClassVar[int]
    dot: _ClassVar[int]
    high_control_min: _ClassVar[int]
    high_control_max: _ClassVar[int]
    digit0: _ClassVar[int]
    digit9: _ClassVar[int]
    lower_a: _ClassVar[int]
    lower_z: _ClassVar[int]
    newline: _ClassVar[int]
    peso: _ClassVar[int]
    pipe: _ClassVar[int]
    plus: _ClassVar[int]
    question: _ClassVar[int]
    round_left: _ClassVar[int]
    round_right: _ClassVar[int]
    slash: _ClassVar[int]
    square_left: _ClassVar[int]
    square_right: _ClassVar[int]
    star: _ClassVar[int]
    tab: _ClassVar[int]
    tilde: _ClassVar[int]
    upper_a: _ClassVar[int]
    upper_z: _ClassVar[int]
    space: _ClassVar[int]
    surrogate_min: _ClassVar[int]
    surrogate_max: _ClassVar[int]
    supplemental_min: _ClassVar[int]
    uint16_max: _ClassVar[int]
    underscore: _ClassVar[int]


def _build_escape_needs() -> _Sequence[int]:
    escape_needs: _MutableSequence[int] = []
    code = 0
    while code <= 127:
        escape_needs.append(_needs_no_escape if code == _Codes.dash or (code == _Codes.space or (code == _Codes.underscore or (_Codes.digit0 <= code and code <= _Codes.digit9 or (_Codes.upper_a <= code and code <= _Codes.upper_z or _Codes.lower_a <= code and code <= _Codes.lower_z)))) else _needs_simple_escape if code == _Codes.ampersand or (code == _Codes.backslash or (code == _Codes.caret or (code == _Codes.curly_left or (code == _Codes.curly_right or (code == _Codes.dot or (code == _Codes.peso or (code == _Codes.pipe or (code == _Codes.plus or (code == _Codes.question or (code == _Codes.round_left or (code == _Codes.round_right or (code == _Codes.slash or (code == _Codes.square_left or (code == _Codes.square_right or (code == _Codes.star or code == _Codes.tilde))))))))))))))) else _needs_numeric_escape)
        code = _int_add(code, 1)
    return tuple(escape_needs)


class Group:
    __slots__ = ('_name', '_value', '_begin', '_end')
    _name: str
    _value: str
    _begin: int
    _end: int

    def __init__(self, /, name: str, value: str, begin: int, end: int) -> None:
        self._name = name
        self._value = value
        self._begin = begin
        self._end = end

    @property
    def name(self, /) -> str:
        return self._name

    @property
    def value(self, /) -> str:
        return self._value

    @property
    def begin(self, /) -> int:
        return self._begin

    @property
    def end(self, /) -> int:
        return self._end


class Match:
    __slots__ = ('_full', '_groups')
    _full: Group
    _groups: _MappingProxyType[str, Group]

    def __init__(self, /, full: Group, groups: _MappingProxyType[str, Group]) -> None:
        self._full = full
        self._groups = groups

    @property
    def full(self, /) -> Group:
        return self._full

    @property
    def groups(self, /) -> _MappingProxyType[str, Group]:
        return self._groups


class _RegexRefs:
    __slots__ = ('_code_points', '_group', '_match_', '_or_object')
    _code_points: CodePoints
    _group: Group
    _match_: Match
    _or_object: Or

    def __init__(self, /, code_points: CodePoints | None = None, group: Group | None = None, match: Match | None = None, or_object: Or | None = None) -> None:
        if code_points is None:
            code_points = CodePoints('')
        if group is None:
            group = Group('', '', 0, 0)
        if match is None:
            match = Match(group, _map_constructor((_Pair('', group),)))
        if or_object is None:
            or_object = Or(())
        self._code_points = code_points
        self._group = group
        self._match_ = match
        self._or_object = or_object

    @property
    def code_points(self, /) -> CodePoints:
        return self._code_points

    @property
    def group(self, /) -> Group:
        return self._group

    @property
    def match_(self, /) -> Match:
        return self._match_

    @property
    def or_object(self, /) -> Or:
        return self._or_object


class _RegexFormatter:
    __slots__ = ('_out',)
    _out: list[str]

    def __init__(self, /) -> None:
        self._out = ['']

    @staticmethod
    def regex_format(data: RegexNode, /) -> str:
        return _RegexFormatter().format(data)

    def format(self, regex: RegexNode, /) -> str:
        self._push_regex(regex)
        return ''.join(self._out)

    def _push_regex(self, regex: RegexNode, /) -> None:
        if isinstance(regex, Capture):
            self._push_capture(regex)
        elif isinstance(regex, CodePoints):
            self._push_code_points(regex, False)
        elif isinstance(regex, CodeRange):
            self._push_code_range(regex)
        elif isinstance(regex, CodeSet):
            self._push_code_set(regex)
        elif isinstance(regex, Or):
            self._push_or(regex)
        elif isinstance(regex, Repeat):
            self._push_repeat(regex)
        elif isinstance(regex, Sequence):
            self._push_sequence(regex)
        elif regex.eq(begin):
            self._out.append('^')
        elif regex.eq(dot):
            self._out.append('.')
        elif regex.eq(end):
            self._out.append('$')
        elif regex.eq(word_boundary):
            self._out.append('\\b')
        elif regex.eq(digit):
            self._out.append('\\d')
        elif regex.eq(space):
            self._out.append('\\s')
        elif regex.eq(word):
            self._out.append('\\w')

    def _push_capture(self, capture: Capture, /) -> None:
        self._out.append('(')
        _regex_formatter_push_capture_name(self, self._out, capture.name)
        self._push_regex(capture.item)
        self._out.append(')')

    def _push_code(self, code: int, inside_code_set: bool, /) -> None:
        special_escape = 'r' if code == _Codes.carriage_return else 'n' if code == _Codes.newline else 't' if code == _Codes.tab else ''
        if special_escape != '':
            self._out.append('\\')
            self._out.append(special_escape)
            return
        if code <= 127:
            escape_need = _list_get(_escape_needs, code)
            if escape_need == _needs_simple_escape or inside_code_set and code == _Codes.dash:
                self._out.append('\\')
                t_2 = self._out
                t_0: str
                try:
                    t_0 = _string_from_code_point(code)
                except _InvalidCodePoint as t_1:
                    raise BaseException('panic') from t_1
                t_2.append(t_0)
                return
            elif escape_need == _needs_no_escape:
                t_5 = self._out
                t_3: str
                try:
                    t_3 = _string_from_code_point(code)
                except _InvalidCodePoint as t_4:
                    raise BaseException('panic') from t_4
                t_5.append(t_3)
                return
        if code >= _Codes.supplemental_min or code > _Codes.high_control_max and not (_Codes.surrogate_min <= code and code <= _Codes.surrogate_max or code == _Codes.uint16_max):
            t_8 = self._out
            t_6: str
            try:
                t_6 = _string_from_code_point(code)
            except _InvalidCodePoint as t_7:
                raise BaseException('panic') from t_7
            t_8.append(t_6)
        else:
            _regex_formatter_push_code_to(self, self._out, code, inside_code_set)

    def _push_code_points(self, code_points: CodePoints, inside_code_set: bool, /) -> None:
        value = code_points.value
        index = 0
        while len(value) > index:
            self._push_code(_string_get(value, index), inside_code_set)
            index = _string_next(value, index)

    def _push_code_range(self, code_range: CodeRange, /) -> None:
        self._out.append('[')
        self._push_code_range_unwrapped(code_range)
        self._out.append(']')

    def _push_code_range_unwrapped(self, code_range: CodeRange, /) -> None:
        self._push_code(code_range.min, True)
        self._out.append('-')
        self._push_code(code_range.max, True)

    def _push_code_set(self, code_set: CodeSet, /) -> None:
        adjusted = self._adjust_code_set(code_set, _regex_refs)
        if isinstance(adjusted, CodeSet):
            if not adjusted.items:
                if adjusted.negated:
                    self._out.append('[\\s\\S]')
                else:
                    self._out.append('(?:$.)')
            else:
                self._out.append('[')
                if adjusted.negated:
                    self._out.append('^')
                i = 0
                while i < len(adjusted.items):
                    self._push_code_set_item(_list_get(adjusted.items, i))
                    i = _int_add(i, 1)
                self._out.append(']')
        else:
            self._push_regex(adjusted)

    def _adjust_code_set(self, code_set: CodeSet, regex_refs: _RegexRefs, /) -> RegexNode:
        return code_set

    def _push_code_set_item(self, code_part: CodePart, /) -> None:
        if isinstance(code_part, CodePoints):
            self._push_code_points(code_part, True)
        elif isinstance(code_part, CodeRange):
            self._push_code_range_unwrapped(code_part)
        elif isinstance(code_part, SpecialSet):
            self._push_regex(code_part)

    def _push_or(self, or_: Or, /) -> None:
        if not not or_.items:
            self._out.append('(?:')
            self._push_regex(_list_get(or_.items, 0))
            i = 1
            while i < len(or_.items):
                self._out.append('|')
                self._push_regex(_list_get(or_.items, i))
                i = _int_add(i, 1)
            self._out.append(')')

    def _push_repeat(self, repeat: Repeat, /) -> None:
        self._out.append('(?:')
        self._push_regex(repeat.item)
        self._out.append(')')
        min = repeat.min
        max: int | None = repeat.max
        if False:
            pass
        elif min == 0 and max == 1:
            self._out.append('?')
        elif min == 0 and max is None:
            self._out.append('*')
        elif min == 1 and max is None:
            self._out.append('+')
        else:
            self._out.append(f'{{{min}')
            if min != max:
                self._out.append(',')
                if max is not None:
                    self._out.append(str(max))
            self._out.append('}')
        if repeat.reluctant:
            self._out.append('?')

    def _push_sequence(self, sequence: Sequence, /) -> None:
        i = 0
        while i < len(sequence.items):
            self._push_regex(_list_get(sequence.items, i))
            i = _int_add(i, 1)

    def max_code(self, code_part: CodePart, /) -> int | None:
        if isinstance(code_part, CodePoints):
            value = code_part.value
            if not value:
                return None
            else:
                max = 0
                index = 0
                while len(value) > index:
                    next = _string_get(value, index)
                    if next > max:
                        max = next
                    index = _string_next(value, index)
                return max
        elif isinstance(code_part, CodeRange):
            return code_part.max
        elif code_part.eq(digit):
            return _Codes.digit9
        elif code_part.eq(space):
            return _Codes.space
        elif code_part.eq(word):
            return _Codes.lower_z
        else:
            return None


class Regex:
    __slots__ = ('_data', '_compiled')
    _data: RegexNode
    _compiled: object

    def __init__(self, /, data: RegexNode) -> None:
        self._data = data
        formatted = _RegexFormatter.regex_format(data)
        self._compiled = _regex_compile_formatted(data, formatted)

    @property
    def data(self, /) -> RegexNode:
        return self._data

    def found(self, text: str, /) -> bool:
        return _regex_compiled_found(self, self._compiled, text)

    def find(self, text: str, begin: int | None = None, /) -> Match:
        if begin is None:
            begin = 0
        return _regex_compiled_find(self, self._compiled, text, begin, _regex_refs)

    def replace(self, text: str, format: _Callable[[Match], str], /) -> str:
        return _regex_compiled_replace(self, self._compiled, text, format, _regex_refs)

    def split(self, text: str, /) -> _Sequence[str]:
        return _regex_compiled_split(self, self._compiled, text, _regex_refs)


def entire(item: RegexNode, /) -> RegexNode:
    return Sequence((begin, item, end))


def one_or_more(item: RegexNode, reluctant: bool | None = None, /) -> Repeat:
    if reluctant is None:
        reluctant = False
    return Repeat(item, 1, None, reluctant)


def optional(item: RegexNode, reluctant: bool | None = None, /) -> Repeat:
    if reluctant is None:
        reluctant = False
    return Repeat(item, 0, 1, reluctant)


begin: Special = _Begin()
dot: Special = _Dot()
end: Special = _End()
word_boundary: Special = _WordBoundary()
digit: SpecialSet = _Digit()
space: SpecialSet = _Space()
word: SpecialSet = _Word()
_Codes.ampersand = 38
_Codes.backslash = 92
_Codes.caret = 94
_Codes.carriage_return = 13
_Codes.curly_left = 123
_Codes.curly_right = 125
_Codes.dash = 45
_Codes.dot = 46
_Codes.high_control_min = 127
_Codes.high_control_max = 159
_Codes.digit0 = 48
_Codes.digit9 = 57
_Codes.lower_a = 97
_Codes.lower_z = 122
_Codes.newline = 10
_Codes.peso = 36
_Codes.pipe = 124
_Codes.plus = 43
_Codes.question = 63
_Codes.round_left = 40
_Codes.round_right = 41
_Codes.slash = 47
_Codes.square_left = 91
_Codes.square_right = 93
_Codes.star = 42
_Codes.tab = 9
_Codes.tilde = 42
_Codes.upper_a = 65
_Codes.upper_z = 90
_Codes.space = 32
_Codes.surrogate_min = 55296
_Codes.surrogate_max = 57343
_Codes.supplemental_min = 65536
_Codes.uint16_max = 65535
_Codes.underscore = 95
_needs_no_escape: int = 0
_needs_simple_escape: int = 2
_needs_numeric_escape: int = 1
_escape_needs: _Sequence[int] = _build_escape_needs()
_regex_refs: _RegexRefs = _RegexRefs()
