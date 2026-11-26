from temper_core import (
    Bubble as _Bubble,
    LoggingConsole as _LoggingConsole,
    ParseError as _ParseError,
    boolean_to_string as _boolean_to_string,
    int_add as _int_add,
    int_div as _int_div,
    string_to_int32 as _string_to_int32,
)

__all__ = ('Node', 'describe', 'first_word', 'stops')


class Node:
    __slots__ = ('_value', '_next')
    _value: int
    _next: 'Node | None'

    def __init__(self, /, value: int, next: 'Node | None') -> None:
        self._value = value
        self._next = next

    @property
    def value(self, /) -> int:
        return self._value

    @property
    def next(self, /) -> 'Node | None':
        return self._next

    def last(self, /) -> int:
        n: 'Node | None' = self._next
        if n is None:
            return self._value
        else:
            return n.last()


def describe(x: int | None, /) -> str:
    if x is None:
        return 'none'
    return f'some {_int_add(x, 1)}'


def first_word(s: str | None, /) -> str | None:
    if s is not None and s != '':
        return s
    else:
        return None


def _same(a: str | None, b: str | None, /) -> bool:
    return a == b


def _same_int(a: int | None, b: int | None, /) -> bool:
    return a == b


def _parse(s: str, /) -> int:
    try:
        return _string_to_int32(s)
    except _ParseError:
        return -1


def _halved(s: str, d: int, /) -> int:
    try:
        return _int_div(_string_to_int32(s), d)
    except _ParseError:
        return -1


def _positive(n: int, /) -> int:
    if n < 0:
        raise _Bubble()
    return n


def _checked(n: int, /) -> int:
    try:
        return _positive(n)
    except _Bubble as t_0:
        raise BaseException('panic') from t_0


def stops() -> int:
    raise BaseException('panic')
    return 1


def _or_zero(x: int | None, /) -> int:
    if x is None:
        return 0
    else:
        return x


def _reason(n: int, why: str | None, /) -> str:
    try:
        _positive(n)
        return 'ok'
    except _Bubble:
        return 'fails' if why is None else why


def _varying() -> None:
    maybe: int | None = None
    _console.log(_boolean_to_string(maybe is None))  # pyright: ignore[reportUnnecessaryComparison]
    maybe = 3
    _console.log(_boolean_to_string(maybe is not None))  # pyright: ignore[reportUnnecessaryComparison]


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log(describe(None))
_console.log(describe(41))
_console.log('nothing' if (_subject := first_word('hi')) is None else _subject)
_console.log('nothing' if (_subject2 := first_word('')) is None else _subject2)
_console.log(f"{_boolean_to_string(_same(None, None))} {_boolean_to_string(_same('a', None))} {_boolean_to_string(_same(None, 'a'))} {_boolean_to_string(_same('a', 'a'))}")
_console.log(f'{_boolean_to_string(_same_int(1, 2))} {_boolean_to_string(_same_int(3, 3))} {_boolean_to_string(_same_int(None, 3))}')
_console.log(f"{_parse('12')} {_parse('x')}")
_console.log(f"{_halved('12', 2)} {_halved('x', 2)}")
_console.log(str(_checked(7)))
_console.log(f'{_or_zero(None)} {_or_zero(5)}')
_varying()
_list: Node = Node(1, Node(2, Node(3, None)))
_console.log(str(_list.last()))
_tail: Node | None = _list.next
if _tail is not None:
    _console.log(str(_tail.value))
try:
    _positive(-1)
except _Bubble:
    _console.log('negative')
_fallback: int
try:
    _fallback = _positive(-2)
except _Bubble:
    _fallback = 0
_console.log(str(_fallback))
_console.log(f"{_reason(1, None)} {_reason(-1, None)} {_reason(-1, 'bad')}")
