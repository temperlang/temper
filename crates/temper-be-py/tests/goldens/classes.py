from abc import ABCMeta as _ABCMeta, abstractmethod as _abstractmethod
from collections.abc import (
    Callable as _Callable,
    MutableSequence as _MutableSequence,
    Sequence as _Sequence,
)
from temper_core import (
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    cast_by_type as _cast_by_type,
    int_add as _int_add,
    int_mul as _int_mul,
    int_negate as _int_negate,
    int_sub as _int_sub,
    list_get as _list_get,
    list_join as _list_join,
)
from typing import (
    ClassVar as _ClassVar,
    Generic as _Generic,
    TypeVar as _TypeVar,
    cast as _cast,
)

__all__ = ('Shape', 'Rect', 'Square', 'Limited', 'Stepper')

_T = _TypeVar('_T')


class Shape(metaclass=_ABCMeta):
    __slots__ = ()

    @property
    @_abstractmethod
    def area(self, /) -> int:
        ...

    def describe(self, /) -> str:
        return f'shape of area {self.area}'


class Rect(Shape):
    __slots__ = ('_w', '_h')
    _w: int
    _h: int
    unit: _ClassVar['Rect']

    def __init__(self, /, w: int, h: int) -> None:
        self._w = w
        self._h = h

    @property
    def w(self, /) -> int:
        return self._w

    @property
    def h(self, /) -> int:
        return self._h

    @property
    def area(self, /) -> int:
        return _int_mul(self._w, self._h)

    def to_string(self, /) -> str:
        return f'Rect({self._w}, {self._h})'

    def __str__(self, /) -> str:
        return self.to_string()

    def scaled(self, k: int, /) -> 'Rect':
        return Rect(_int_mul(self._w, k), _int_mul(self._h, k))


class Square(Shape):
    __slots__ = ('_side',)
    _side: int

    def __init__(self, /, side: int) -> None:
        self._side = side

    @property
    def area(self, /) -> int:
        return _int_mul(self._side, self._side)

    def describe(self, /) -> str:
        return f'square of side {self._side}'


class _Counter:
    __slots__ = ('_count', '_hidden')
    _count: int
    _hidden: int

    def __init__(self, /) -> None:
        self._count = 0
        self._hidden = 0

    @property
    def count(self, /) -> int:
        return self._count

    @count.setter
    def count(self, value: int, /) -> None:
        self._count = value

    @property
    def doubled(self, /) -> int:
        return _int_mul(self._count, 2)

    @doubled.setter
    def doubled(self, value: int, /) -> None:
        self._count = _int_sub(value, self._count)

    @property
    def added(self, /) -> int:
        raise AttributeError('added getter unavailable')

    @added.setter
    def added(self, value: int, /) -> None:
        self._count = _int_add(self._count, value)

    def bump(self, /) -> '_Counter':
        self._count = _int_add(self._count, 1)
        self._hidden = _int_add(self._hidden, 1)
        return self

    def same(self, other: '_Counter', /) -> bool:
        return self._hidden == other._hidden

    @staticmethod
    def zero() -> '_Counter':
        return _Counter()


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

    def eq(self, other: '_Point', /) -> bool:
        if self._x == other._x:
            return self._y == other._y
        else:
            return False

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, _Point) and self.eq(other)


class _Loose:
    __slots__ = ('_n',)
    _n: int

    def __init__(self, /, n: int) -> None:
        self._n = n

    @property
    def n(self, /) -> int:
        return self._n

    def eq(self, other: '_Loose', /) -> bool:
        if self._n == other._n:
            return True
        else:
            return self._n == _int_negate(other._n)

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, _Loose) and self.eq(other)

    def mirrors(self, other: '_Loose', /) -> bool:
        if other == self:
            return self == other
        else:
            return False


def _maybe_loose(n: int, /) -> _Loose | None:
    if n == 0:
        return None
    else:
        return _Loose(n)


class _Boxed(_Generic[_T]):
    __slots__ = ('_value', '_tag')
    _value: _T
    _tag: int

    def __init__(self, /, value: _T, tag: int) -> None:
        self._value = value
        self._tag = tag

    @property
    def value(self, /) -> _T:
        return self._value

    @property
    def tag(self, /) -> int:
        return self._tag

    def eq(self, other: '_Boxed[_T]', /) -> bool:
        return self._tag == other._tag

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, _Boxed) and self.eq(_cast('_Boxed[_T]', other))

    def same_as(self, other: '_Boxed[_T]', /) -> bool:
        if self == other:
            return other == self
        else:
            return False


class _Holder:
    __slots__ = ('_loose', '_tag')
    _loose: _Loose
    _tag: str

    def __init__(self, /, loose: _Loose, tag: str) -> None:
        self._loose = loose
        self._tag = tag

    @property
    def loose(self, /) -> _Loose:
        return self._loose

    @property
    def tag(self, /) -> str:
        return self._tag

    def eq(self, other: '_Holder', /) -> bool:
        if self._loose == other._loose:
            return self._tag == other._tag
        else:
            return False

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, _Holder) and self.eq(other)


class _Version:
    __slots__ = ('_major', '_minor')
    _major: int
    _minor: int

    def __init__(self, /, major: int, minor: int) -> None:
        self._major = major
        self._minor = minor

    @property
    def major(self, /) -> int:
        return self._major

    @property
    def minor(self, /) -> int:
        return self._minor

    def compare_to(self, other: '_Version', /) -> int:
        if self._major != other._major:
            return self._major - other._major
        else:
            return self._minor - other._minor


class _Stock:
    __slots__ = ('_count', '_items_var', '_size')
    _count: int
    _items_var: _Sequence[int]
    _size: int

    def __init__(self, /, items: _Sequence[int], count: int | None = None) -> None:
        if count is None:
            count = len(items)
        self._count = count
        self._items_var = items
        self._size = len(items)

    @property
    def count(self, /) -> int:
        return self._count

    @property
    def items(self, /) -> _Sequence[int]:
        return self._items_var

    @items.setter
    def items(self, value: _Sequence[int], /) -> None:
        self._items_var = value

    @property
    def size(self, /) -> int:
        return self._size

    def size_now(self, /) -> int:
        return len(self.items)


def _shape(x: Shape, /) -> Shape:
    return x


class _Theme:
    __slots__ = ('_fg', '_bg', '_size', '_bold')
    _fg: str
    _bg: str
    _size: int
    _bold: bool

    def __init__(self, /, fg: str, bg: str, size: int | None = None, bold: bool | None = None) -> None:
        if size is None:
            size = 12
        if bold is None:
            bold = False
        self._fg = fg
        self._bg = bg
        self._size = size
        self._bold = bold

    @property
    def fg(self, /) -> str:
        return self._fg

    @property
    def bg(self, /) -> str:
        return self._bg

    @property
    def size(self, /) -> int:
        return self._size

    @property
    def bold(self, /) -> bool:
        return self._bold

    @property
    def weight(self, /) -> str:
        if self._bold:
            return 'bold'
        else:
            return 'plain'

    def to_string(self, /) -> str:
        return f'{self._fg}/{self._bg} {self._size} {self.weight}'

    def __str__(self, /) -> str:
        return self.to_string()


def _picked(t: _Theme, why: str, /) -> _Theme:
    _console.log(why)
    return t


def _noted(s: str, /) -> str:
    _console.log(s)
    return s


def _compared(eq: _Callable[[_Loose | None, _Loose | None], bool], /) -> str:
    ns = (0, 1, -1, 2)
    out: _MutableSequence[str] = []
    length = len(ns)
    i = 0
    while i < length:
        a = _list_get(ns, i)
        i = _int_add(i, 1)
        length2 = len(ns)
        i2 = 0
        while i2 < length2:
            b = _list_get(ns, i2)
            i2 = _int_add(i2, 1)
            out.append('T' if eq(_maybe_loose(a), _maybe_loose(b)) else 'f')
    return _list_join(out, '', lambda s: s)


def _type_named(n: int, /) -> type:
    if n == 0:
        return type(None)
    elif n == 1:
        return bool
    elif n == 2:
        return int
    elif n == 3:
        return int
    elif n == 4:
        return float
    else:
        return str


def _same(t: type, /) -> type:
    return t


class _Sql:
    __slots__ = ('_parts',)
    _parts: _MutableSequence[str]

    def __init__(self, /) -> None:
        self._parts = []

    def append_safe(self, s: str, /) -> None:
        self._parts.append(s)

    def append_string(self, x: str | None, /) -> None:
        self._parts.append('NULL' if x is None else f"'{x}'")

    def append_int(self, x: int, /) -> None:
        self._parts.append(str(x))

    def append_shape(self, x: Shape, /) -> None:
        self._parts.append(f'shape {x.area}')

    def append_rect(self, x: Rect, /) -> None:
        self._parts.append(f'rect {x.to_string()}')

    @property
    def accumulated(self, /) -> str:
        return _list_join(self._parts, '', lambda p: p)


class Limited(metaclass=_ABCMeta):
    __slots__ = ()

    @_abstractmethod
    def next(self, n: int, /) -> int:
        ...

    @_abstractmethod
    def too_far(self, /) -> int:
        ...


class Stepper(Limited):
    __slots__ = ('_limit',)
    _limit: int

    def __init__(self, /, limit: int) -> None:
        self._limit = limit

    @property
    def limit(self, /) -> int:
        return self._limit

    def next(self, n: int, /) -> int:
        if n < self._limit:
            return _int_add(n, 1)
        else:
            return self.too_far()

    def too_far(self, /) -> int:
        raise BaseException('panic')


class _Failing(_Generic[_T], metaclass=_ABCMeta):
    __slots__ = ()

    @_abstractmethod
    def fail(self, /) -> _T:
        ...


class _IntFailing(_Failing[int]):
    __slots__ = ()

    def fail(self, /) -> int:
        raise BaseException('panic')


class _AnyFailing(_Failing[_T], _Generic[_T]):
    __slots__ = ()

    def fail(self, /) -> _T:
        raise BaseException('panic')


def _or_fail(ok: bool, x: _T, f: _Failing[_T], /) -> _T:
    if ok:
        return x
    else:
        return f.fail()


_console: _LoggingConsole = _LoggingConsole(__name__)
Rect.unit = Rect(1, 1)
_r: Rect = Rect(2, 3)
_console.log(_r.to_string())
_console.log(str(_r.area))
_s: Shape = _shape(_r)
_console.log(_s.describe())
_q: Shape = _shape(Square(4))
_console.log(_q.describe())
_console.log(str(_q.area))
_console.log(str(_r.scaled(2).scaled(3).area))
_console.log(Rect.unit.scaled(5).to_string())
_c: _Counter = _Counter.zero()
_c.bump().bump()
_console.log(str(_c.count))
_c.doubled = 10
_console.log(str(_c.doubled))
_console.log(_boolean_to_string(_c.same(_Counter().bump().bump())))
_c.added = 5
_t = 2
_c.added = _t
_t2 = _t
_c.added = _t2
_added: int = _t2
_console.log(f'{_added} {_c.count}')
_console.log(f'{_boolean_to_string(_Point(1, 2) == _Point(1, 2))} {_boolean_to_string(_Point(1, 2) != _Point(2, 1))}')
_console.log(_boolean_to_string(_Holder(_Loose(3), 'a') == _Holder(_Loose(-3), 'a')))
_loose: _Loose = _Loose(2)
_t_1 = _boolean_to_string(_loose.mirrors(_Loose(-2)))
_t_0 = _boolean_to_string(False if (_left := _maybe_loose(-2)) is None else _left == _loose)
_left2 = _loose
_right: _Loose | None = _maybe_loose(0)
_console.log(f'{_t_1} {_t_0} {_boolean_to_string(False if _right is None else _left2 == _right)} {_boolean_to_string(not (False if (_left3 := _maybe_loose(0)) is None else _left3 == _loose))}')
_subject: _Loose | None = _maybe_loose(-2)
_matched: str = 'loose' if (False if _subject is None else _subject == _loose) else 'other'
_console.log(_matched)
_b1: _Boxed[str] = _Boxed('x', 1)
_b3: _Boxed[str] = _Boxed('z', 2)
_console.log(f"{_b1.value} {_boolean_to_string(_b1 == _Boxed('y', 1))} {_boolean_to_string(_b1 != _b3)} {_boolean_to_string(_b1.same_as(_Boxed('y', 1)))} {_boolean_to_string(_b3.same_as(_b1))}")
_console.log(f'{_boolean_to_string(_Version(1, 2).compare_to(_Version(1, 3)) < 0)} {_boolean_to_string(_Version(2, 0).compare_to(_Version(1, 9)) >= 0)}')
_console.log(f'{_boolean_to_string(isinstance(_q, Square))} {_boolean_to_string(isinstance(_s, Square))}')
_sq: Square = _cast_by_type(_q, Square)
_console.log(str(_sq.area))
if isinstance(_s, Rect):
    _console.log(str(_s.w))
_st: _Stock = _Stock((4, 5))
_st.items = (1, 2, 3)
_console.log(f'{_st.count} {_st.size} {_st.size_now()} {len(_st.items)}')
_plain: _Theme = _Theme('black', 'white')
_spread = _plain
_loud: _Theme = _Theme(_spread.fg, _spread.bg, 20, True)
_spread2 = _loud
_dark: _Theme = _Theme(_plain.bg, _plain.fg, _spread2.size, _spread2.bold)
_console.log(f'{_plain.to_string()} | {_loud.to_string()} | {_dark.to_string()}')
_spread3 = _picked(_dark, 'old')
_arg = _spread3.bg
_arg2 = _noted('red')
_arg3 = _spread3.bold
_mixed: _Theme = _Theme(_arg2, _arg, 9, _arg3)
_console.log(_mixed.to_string())
_console.log(_compared(lambda a, b: b is None if a is None else False if b is None else a == b))
_console.log(_compared(lambda a, b: not (b is None if a is None else False if b is None else a == b)))
_console.log(_compared(lambda a, b: a is None if b is None else False if a is None else b == a))


def _fn4(a: _Loose | None, b: _Loose | None, /) -> bool:
    if a is None:
        return False
    if b is None:
        return False
    return a.mirrors(b)


_console.log(_compared(_fn4))
_favorite: type = _same(int)
_named: int = 0
_i = 0
while _i < 6:
    _type_named(_i)
    _named = _int_add(_named, 1)
    _i = _int_add(_i, 1)
_console.log(f'{_named} types')
_sql: _Sql = _Sql()
_sql.append_string('it')
_sql.append_string(None)
_sql.append_int(3)
_sql.append_shape(Square(2))
_sql.append_rect(Rect(1, 2))
_console.log(_sql.accumulated)
_seven: int = 7
_accumulator = _Sql()
_accumulator.append_safe('SELECT ')
_accumulator.append_int(_seven)
_accumulator.append_safe(', ')
_accumulator.append_string('x')
_accumulator.append_safe(' FROM ')
_accumulator.append_rect(Rect(2, 2))
_console.log(_accumulator.accumulated)
_stepper: Limited = Stepper(3)
_console.log(f'next {_stepper.next(1)}')
_console.log(f"{_or_fail(True, 4, _IntFailing())} {_or_fail(True, 's', _AnyFailing())}")
