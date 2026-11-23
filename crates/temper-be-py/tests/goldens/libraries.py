from abc import ABCMeta as _ABCMeta, abstractmethod as _abstractmethod
from collections.abc import (
    Callable as _Callable,
    MutableSequence as _MutableSequence,
    Sequence as _Sequence,
)
from temper_core import (
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    float64_to_string as _float64_to_string,
    float_cmp as _float_cmp,
    int_add as _int_add,
    int_div as _int_div,
    int_mul as _int_mul,
    int_sub as _int_sub,
    list_get as _list_get,
    list_join as _list_join,
    list_map as _list_map,
    string_to_float64 as _string_to_float64,
    string_to_int32 as _string_to_int32,
)
from typing import (
    ClassVar as _ClassVar,
    Generic as _Generic,
    TypeVar as _TypeVar,
)

__all__ = (
    'Grid',
    'parse_grid',
    'Casing',
    'Shape',
    'Dot',
    'Line',
    'Carry',
    'Tagged',
    'size_with',
    'Word',
    'Joiner',
    'Shown',
    'Tag',
    'Held',
    'Effect',
    'Plain',
    'Happened',
    'Bundle',
    'effects_of',
    'same_held',
    'Stepper',
    'Counting',
    'Ticks',
    'Sink',
    'Names',
    'Escapes',
    'Quoting',
)

_T = _TypeVar('_T')
_T_2 = _TypeVar('_T_2', bound='Shown')


class Grid:
    __slots__ = ('_ncols', '_values')
    _ncols: int
    _values: _Sequence[float]

    def __init__(self, /, ncols: int, values: _Sequence[float]) -> None:
        self._ncols = ncols
        self._values = values

    @property
    def ncols(self, /) -> int:
        return self._ncols

    @property
    def values(self, /) -> _Sequence[float]:
        return self._values

    def get(self, i: int, j: int, /) -> float:
        return _list_get(self._values, _int_add(_int_mul(i, self._ncols), j))

    @staticmethod
    def parse(s: str, /) -> 'Grid | None':
        try:
            return parse_grid(s)
        except Exception:
            return None


def parse_grid(s: str, /) -> Grid:
    if s == '':
        raise RuntimeError()
    return Grid(1, (_string_to_float64(s),))


class Casing:
    __slots__ = ('_name',)
    _name: str
    first: _ClassVar['Casing']
    last: _ClassVar['Casing']
    _mark: _ClassVar[str]

    def __init__(self, /, name: str) -> None:
        self._name = name

    @property
    def name(self, /) -> str:
        return self._name

    def eq(self, other: 'Casing', /) -> bool:
        return self._name == other._name

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, Casing) and self.eq(other)

    def to_string(self, /) -> str:
        return self._name

    def __str__(self, /) -> str:
        return self.to_string()

    @staticmethod
    def _named(name: str, /) -> 'Casing':
        return Casing(name)

    @property
    def marked(self, /) -> str:
        return Casing._mark + self._name


class Shape(metaclass=_ABCMeta):
    __slots__ = ()

    @property
    @_abstractmethod
    def size(self, /) -> int:
        ...


class Dot(Shape):
    __slots__ = ('_size',)
    _size: int

    def __init__(self, /, size: int) -> None:
        self._size = size

    @property
    def size(self, /) -> int:
        return self._size

    @property
    def bigger(self, /) -> 'Dot | None':
        if self._size < 3:
            return Dot(_int_add(self._size, 1))
        else:
            return None

    @property
    def grown(self, /) -> int | None:
        subject: 'Dot | None' = self.bigger
        if subject is None:
            return None
        else:
            return subject.scaled(10)

    def scaled(self, k: int, /) -> int:
        return _int_mul(self._size, k)


class Line(Shape):
    __slots__ = ('_size',)
    _size: int

    def __init__(self, /, size: int) -> None:
        self._size = size

    @property
    def size(self, /) -> int:
        return self._size


class Carry:
    __slots__ = ('_carrying', '_state')
    _carrying: bool
    _state: int

    def __init__(self, /, start: int) -> None:
        self._carrying = False
        self._state = 0
        if start != 0:
            self._carrying = True
            self._state = start

    @property
    def carrying(self, /) -> bool:
        return self._carrying

    @property
    def state(self, /) -> int:
        return self._state


class Tagged:
    __slots__ = ('_label',)
    _label: str | None

    def __init__(self, /, label: str | None) -> None:
        self._label = label

    @property
    def label(self, /) -> str | None:
        return self._label


def _tagged(label: str | None = None, /) -> Tagged:
    return Tagged(label)


def _weight(c: int, /) -> int:
    base = 0 if c == 42 else 6
    return _int_add(_int_add(1 if c > 50 else 2, base), c)


def _shape_name(s: Shape, /) -> str:
    t_1: str
    if isinstance(s, Dot):
        t_1 = 'dot'
    else:
        t_0: str
        if isinstance(s, Line):
            t_0 = 'line'
        else:
            raise BaseException('panic')
        t_1 = t_0
    name = t_1
    return name


def _shape_of(n: int, /) -> Shape:
    shape = Line(n) if n > 1 else Dot(n)
    return shape


def _sizes(s: Shape, /) -> _Sequence[int]:
    found = (s.size, s.size) if isinstance(s, Dot) else ()
    return found


def _rows(values: _Sequence[float], ncols: int, /) -> int:
    try:
        return _int_div(len(values), ncols)
    except Exception:
        raise BaseException('panic')


def _scaled(grid: Grid, /) -> Grid:
    if grid.ncols == 0:
        raise RuntimeError()
    return grid


def _cubes(grid: Grid, /) -> _Sequence[float]:
    try:
        more = _list_map(_scaled(grid).values, lambda x: x * x * x)
        return more
    except Exception:
        raise BaseException('panic')


def _each(values: _Sequence[float], f: _Callable[[float], None], /) -> None:
    i = 0
    while i < len(values):
        f(_list_get(values, i))
        i = _int_add(i, 1)


def _total(values: _Sequence[float], /) -> float:
    sum = 0.0
    def fn(x: float, /) -> None:
        nonlocal sum
        try:
            part = x / 2.0
            sum = sum + part
        except Exception:
            raise BaseException('panic')
    _each(values, fn)
    return sum


def _half(x: float, /) -> float:
    try:
        return x / 2.0
    except Exception:
        raise BaseException('panic')


def _cube(x: float, /) -> float:
    return x * 0.25


def _corner(grid: Grid, /) -> float:
    try:
        return grid.get(1, 1)
    except Exception:
        return -1.0


def _maybe(n: int, /) -> Grid | None:
    if n > 0:
        return Grid(n, (1.0,))
    else:
        return None


def _mapped(grid: Grid, /) -> _Sequence[float]:
    t_0: Grid
    try:
        t_0 = _scaled(grid)
    except Exception:
        raise BaseException('panic')
    t_1 = t_0.values
    def fn(x: float, /) -> float:
        if _float_cmp(x, 1.5) > 0:
            return x * 2.0
        else:
            return x
    return _list_map(t_1, fn)


def _byte_at(digits: _Sequence[int], short: bool, c: int, /) -> int:
    byte = _int_mul(_list_get(digits, c), 17) if short else _int_add(_int_mul(_list_get(digits, _int_mul(2, c)), 16), _list_get(digits, _int_add(_int_mul(2, c), 1)))
    return byte


def _ascending(digits: _Sequence[int], /) -> bool:
    if len(digits) > 1:
        return _int_add(_list_get(digits, 0), 1) == _list_get(digits, 1)
    else:
        return False


def _first_drop(digits: _Sequence[int], /) -> int:
    i = 1
    while i < len(digits) and _list_get(digits, _int_sub(i, 1)) <= _list_get(digits, i):
        i = _int_add(i, 1)
    return i


def _tick() -> int:
    global _ticks
    _ticks = _int_add(_ticks, 1)
    return _ticks


def _tick_if(short: bool, /) -> int:
    n = _tick() if short else _int_add(_int_mul(_tick(), 10), _tick())
    return n


def _ticked() -> bool:
    if _ticks > 0:
        return _int_add(_int_mul(_tick(), 10), _tick()) > 0
    else:
        return False


def _tick_while() -> int:
    i = 0
    while i < 3 and _tick() < _tick():
        i = _int_add(i, 1)
    return i


def size_with(items: _Sequence[_T], /) -> int:
    return _int_add(len(items), len(_extras))


def _text(n: int, /) -> str | None:
    if n > 0:
        return 'x'
    else:
        return None


def _count(n: int, /) -> int | None:
    if n > 0:
        return n
    else:
        return None


def _casing(n: int, /) -> Casing | None:
    if n > 0:
        return Casing.first
    else:
        return None


def _captured(s: str, /) -> str:
    found: Grid | None
    try:
        found = parse_grid(s)
    except Exception:
        found = None
    return _list_join(_list_map((1, 2), lambda x: found is None), ',', lambda b: _boolean_to_string(b))


def _bidi() -> str:
    return 'look\u202eout'


def _word(n: int, /) -> str:
    if n > 1:
        return 'b'
    else:
        return 'a'


class Word:
    __slots__ = ('_text',)
    _text: str

    def __init__(self, /, text: str) -> None:
        self._text = text

    @property
    def text(self, /) -> str:
        return self._text


class Joiner:
    __slots__ = ('_out',)
    _out: str

    def __init__(self, /) -> None:
        self._out = ''

    @property
    def out(self, /) -> str:
        return self._out

    @out.setter
    def out(self, value: str, /) -> None:
        self._out = value

    def add_word(self, w: Word, /) -> None:
        self._out = f'{self._out}w{w.text}'

    def add_flags(self, x: _Sequence[bool], /) -> None:
        self._out = f'{self._out}f{len(x)}'

    def add_texts(self, x: _Sequence[str], /) -> None:
        self._out = f'{self._out}t{len(x)}'

    def add_maybes(self, x: _Sequence[str | None], /) -> None:
        self._out = f'{self._out}m{len(x)}'


class Shown(metaclass=_ABCMeta):
    __slots__ = ()

    @_abstractmethod
    def to_string(self, /) -> str:
        ...


class Tag(Shown):
    __slots__ = ('_name',)
    _name: str

    def __init__(self, /, name: str) -> None:
        self._name = name

    @property
    def name(self, /) -> str:
        return self._name

    def to_string(self, /) -> str:
        return f'<{self._name}>'

    def __str__(self, /) -> str:
        return self.to_string()


class Held(_Generic[_T_2]):
    __slots__ = ('_item', '_next')
    _item: _T_2
    _next: 'Held[_T_2] | None'

    def __init__(self, /, item: _T_2, next: 'Held[_T_2] | None') -> None:
        self._item = item
        self._next = next

    @property
    def item(self, /) -> _T_2:
        return self._item

    @property
    def next(self, /) -> 'Held[_T_2] | None':
        return self._next

    def to_string(self, /) -> str:
        return f'Held({self._item.to_string()})'

    def __str__(self, /) -> str:
        return self.to_string()


class Effect(_Generic[_T], metaclass=_ABCMeta):
    __slots__ = ()


class Plain(Effect[_T], _Generic[_T]):
    __slots__ = ('_text',)
    _text: str

    def __init__(self, /, text: str) -> None:
        self._text = text

    @property
    def text(self, /) -> str:
        return self._text


class Happened(Effect[_T], _Generic[_T]):
    __slots__ = ('_event',)
    _event: _T

    def __init__(self, /, event: _T) -> None:
        self._event = event

    @property
    def event(self, /) -> _T:
        return self._event


class Bundle(_Generic[_T]):
    __slots__ = ('_effects', '_first')
    _effects: _Sequence[Effect[_T]]
    _first: _T

    def __init__(self, /, effects: _Sequence[Effect[_T]], first: _T) -> None:
        self._effects = effects
        self._first = first

    @property
    def effects(self, /) -> _Sequence[Effect[_T]]:
        return self._effects

    @property
    def first(self, /) -> _T:
        return self._first


def _join_effects(into: _MutableSequence[Effect[_T]], more: _Sequence[Effect[_T]], /) -> int:
    length = len(more)
    i = 0
    while i < length:
        e = _list_get(more, i)
        i = _int_add(i, 1)
        into.append(e)
    return len(into)


def effects_of(text: str, event: _T, other: _T, /) -> str:
    into: _MutableSequence[Effect[_T]] = []
    _join_effects(into, ())
    _join_effects(into, (Plain[_T](text),))
    _join_effects(into, (Happened(event), Plain[_T]('!'), Happened(other)))
    shown = ''
    list_ = tuple(into)
    length = len(list_)
    i = 0
    while i < length:
        e = _list_get(list_, i)
        i = _int_add(i, 1)
        if isinstance(e, Plain):
            shown = shown + e.text
        else:
            shown = f'{shown}*'
    return shown


def same_held(a: Held[_T_2] | None, b: Held[_T_2] | None, /) -> bool:
    left: Held[_T_2] | None = a
    right: Held[_T_2] | None = b
    return right is None if left is None else False if right is None else left is right


class Stepper(_Generic[_T], metaclass=_ABCMeta):
    __slots__ = ()

    @_abstractmethod
    def step(self, /) -> _Sequence[_T]:
        ...


class Counting(Stepper[_T], _Generic[_T]):
    __slots__ = ()

    @property
    @_abstractmethod
    def count(self, /) -> int:
        ...

    @count.setter
    @_abstractmethod
    def count(self, c: int, /) -> None:
        ...

    @_abstractmethod
    def made(self, /) -> _T:
        ...

    def step(self, /) -> _Sequence[_T]:
        self.count = _int_add(self.count, 1)
        return (self.made(),)


class Ticks(Counting[str]):
    __slots__ = ('_n',)
    _n: int

    def __init__(self, /) -> None:
        self._n = 0

    @property
    def count(self, /) -> int:
        return self._n

    @count.setter
    def count(self, c: int, /) -> None:
        self._n = c

    def made(self, /) -> str:
        return f'tick{self._n}'

    def step(self, /) -> _Sequence[str]:
        return Counting[str].step(self)


class Sink(_Generic[_T], metaclass=_ABCMeta):
    __slots__ = ()

    @_abstractmethod
    def take(self, e: _T, /) -> int:
        ...


class Names(Sink[Tag]):
    __slots__ = ()

    def take(self, e: Tag, /) -> int:
        if e.name == 'abc':
            return 3
        else:
            return 0


class Escapes(metaclass=_ABCMeta):
    __slots__ = ()

    @_abstractmethod
    def escape(self, s: str | None, /) -> str:
        ...

    def escape_flag(self, b: bool, /) -> str:
        return self.escape('T' if b else 'F')


class Quoting(Escapes):
    __slots__ = ()

    def escape(self, s: str | None, /) -> str:
        return f"'{'null' if s is None else s}'"


def _problem(n: int, /) -> str | None:
    t_0: str | None
    if n == 0:
        t_0 = None
    else:
        raise BaseException('panic')
    p: str | None = t_0
    return p


def _message(n: int, /) -> str | None:
    t_2: str | None
    if n == 0:
        t_2 = None
    else:
        t_1: str | None
        if n == 1:
            t_1 = 'one'
        else:
            t_0: str | None
            if n == 2:
                t_0 = None
            else:
                raise BaseException('panic')
            t_1 = t_0
        t_2 = t_1
    p: str | None = t_2
    return p


def _sum_parsed(xs: _Sequence[str], /) -> int:
    total = 0
    i = 0
    while i < len(xs):
        x = _list_get(xs, i)
        n: int
        held: int | None = None
        try:
            held = _string_to_int32(x)
        except Exception:
            pass
        if held is None:  # pyright: ignore[reportUnnecessaryComparison]
            i = _int_add(i, 1)
            continue
        else:
            n = held
        total = _int_add(total, n)
        i = _int_add(i, 1)
    length = len(xs)
    i = 0
    while i < length:
        x = _list_get(xs, i)
        i = _int_add(i, 1)
        m: int
        held = None
        try:
            held = _string_to_int32(x)
        except Exception:
            pass
        if held is None:  # pyright: ignore[reportUnnecessaryComparison]
            continue
        else:
            m = held
        total = _int_add(total, _int_mul(m, 10))
    return total


def _backwards(xs: _Sequence[int], /) -> int:
    t = 0
    i = len(xs)
    while True:
        i = _int_sub(i, 1)
        if not i >= 0:
            break
        t = _int_add(_int_mul(t, 10), _list_get(xs, i))
    return t


_console: _LoggingConsole = _LoggingConsole(__name__)
Casing.first = Casing('first')
Casing.last = Casing._named('last')  # pyright: ignore[reportPrivateUsage]
Casing._mark = '#'  # pyright: ignore[reportPrivateUsage]
_ticks: int = 0
_extras: _Sequence[int] = (1,)
_grid: Grid = Grid(2, (1.0, 2.0, 3.0, 4.0))
_console.log(f'{_rows(_grid.values, 2)} {_float64_to_string(_half(3.0))} {_float64_to_string(_cube(8.0))}')
_console.log(_float64_to_string(_corner(_grid)))
_console.log(_float64_to_string(_total(_grid.values)))
_none: str | None = _text(0)
_some: int | None = _count(3)
_console.log(f"{'null' if (_part := _none) is None else _part} {'null' if (_part2 := _some) is None else str(_part2)} {'null' if (_part3 := None if (_subject := _maybe(0)) is None else _subject.ncols) is None else str(_part3)}")
_console.log(f"{_boolean_to_string(_maybe(1) is not None)} {_boolean_to_string(_maybe(0) is not None)} {_boolean_to_string(Grid.parse('') is None)}")
_console.log(Casing.first.to_string())
_console.log(_list_join(_mapped(_grid), ',', lambda x: _float64_to_string(x)))
_console.log(_list_join(_cubes(_grid), ',', lambda x: _float64_to_string(x)))
_t_1 = _boolean_to_string(False if (_left := _casing(1)) is None else _left == Casing.first)
_t_0 = _boolean_to_string(False if (_left2 := _casing(0)) is None else _left2 == Casing.first)
_left3: Casing | None = _casing(0)
_right: Casing | None = _casing(0)
_console.log(f'{_t_1} {_t_0} {_boolean_to_string(not (_right is None if _left3 is None else False if _right is None else _left3 == _right))}')
_console.log(f"{_captured('')} {_captured('2.5')}")
_console.log(_boolean_to_string(_bidi() != 'lookout'))
_t_2 = _word(1)
_t_3 = _word(2)
_t_9 = (_t_2 > _t_3) - (_t_2 < _t_3)
_t_4 = _word(2)
_t_5 = _word(1)
_t_8 = (_t_4 > _t_5) - (_t_4 < _t_5)
_t_6 = _word(2)
_t_7 = _word(3)
_console.log(f'{_t_9} {_t_8} {(_t_6 > _t_7) - (_t_6 < _t_7)}')
_named: str = _word(2)
_console.log(_boolean_to_string(_named == _word(3) and f'{_named}!' != _named))
_digits: _Sequence[int] = (1, 2, 4, 3)
_console.log(f'{_byte_at(_digits, True, 1)} {_byte_at(_digits, False, 0)} {_boolean_to_string(_ascending(_digits))} {_first_drop(_digits)} {size_with(_digits)}')
_console.log(f'{_tick_if(True)} {_tick_if(False)} {_boolean_to_string(_ticked())} {_tick_while()}')
_console.log(f"{_shape_name(_shape_of(1))} {_shape_name(_shape_of(2))} {len(_sizes(_shape_of(1)))} {len(_sizes(_shape_of(2)))} {'null' if (_part4 := Dot(1).grown) is None else str(_part4)} {'null' if (_part5 := Dot(3).grown) is None else str(_part5)} {Casing.last.to_string()} {Casing.first.marked}")
_carry: Carry = Carry(3)
_console.log(f"{_boolean_to_string(_carry.carrying)} {_carry.state} {_boolean_to_string(Carry(0).carrying)} {'null' if (_part6 := _tagged().label) is None else _part6} {'null' if (_part7 := _tagged('x').label) is None else _part7} {_weight(42)} {_weight(60)}")
_b: _MutableSequence[bool] = []
_b.append(True)
_b.append(False)
_flags: _Sequence[bool] = tuple(_b)
_joiner: Joiner = Joiner()
_joiner.add_word(Word('a'))
_joiner.add_flags((True,))
_joiner.add_texts(('x', 'y'))
_maybes: _Sequence[str | None] = (None, 'z')
_joiner.add_maybes(_maybes)
_console.log(_joiner.out)
_held: Held[Tag] = Held(Tag('a'), None)
_other: Held[Tag] = Held(Tag('b'), _held)
_nobody: Held[Tag] | None = None
_console.log(f"{_held.to_string()} {(Tag('none') if (_subject2 := None if (_subject3 := _other.next) is None else _subject3.item) is None else _subject2).to_string()} {_boolean_to_string(same_held(_held, _held))} {_boolean_to_string(same_held(_held, _other))} {_boolean_to_string(same_held(_nobody, _nobody))} {_boolean_to_string(same_held(_nobody, _held))}")
_console.log(f"{effects_of('ab', 1, 2)} {Bundle((), '2').first} {len(_flags)}")
_counter: Ticks = Ticks()
_stepper: Counting[str] = _counter
_stepper.step()
_console.log(f"{_list_get(_stepper.step(), 0)} {_counter.count} {Names().take(Tag('abc'))} {Quoting().escape_flag(True)}")
_console.log(f"{'none' if (_subject4 := _problem(0)) is None else _subject4} {'none' if (_subject5 := _message(1)) is None else _subject5} {'none' if (_subject6 := _message(2)) is None else _subject6} {_sum_parsed(('1', 'x', '2'))} {_backwards((1, 2, 3))}")
