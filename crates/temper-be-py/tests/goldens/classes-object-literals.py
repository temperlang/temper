from collections.abc import Sequence as _Sequence
from object_literals.extra_class import (
    Circle as _Circle,
    Rectangle as _Rectangle,
    Stringable as _Stringable,
)
from temper_core import (
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    list_join as _list_join,
    list_map as _list_map,
)

__all__ = ('Person',)


class Person(_Stringable):
    __slots__ = ('_name', '_age')
    _name: str
    _age: int

    def __init__(self, /, name: str, age: int | None = None) -> None:
        if age is None:
            age = -100
        self._name = name
        self._age = age

    @property
    def name(self, /) -> str:
        return self._name

    @property
    def age(self, /) -> int:
        return self._age

    def to_string(self, /) -> str:
        age_text = _list_join((0,), '', lambda unused: str(self._age))
        return f'Person {self._name} of age {age_text}'

    def __str__(self, /) -> str:
        return self.to_string()

    def eq(self, other: 'Person', /) -> bool:
        if self._name == other._name:
            return self._age == other._age
        else:
            return False

    def __eq__(self, other: object, /) -> bool:
        return isinstance(other, Person) and self.eq(other)


class _Whatever:
    __slots__ = ('_blah',)
    _blah: str

    def __init__(self, /, blech: int) -> None:
        self._blah = str(blech)

    @property
    def blah(self, /) -> str:
        return self._blah


class _Moreover:
    __slots__ = ('_blech', '_bling')
    _blech: int
    _bling: int

    def __init__(self, /, blech: int | None = None, bling: int | None = None) -> None:
        if blech is None:
            blech = 0
        if bling is None:
            bling = 0
        self._blech = blech
        self._bling = bling

    @property
    def blech(self, /) -> int:
        return self._blech

    @property
    def bling(self, /) -> int:
        return self._bling

    def to_string(self, /) -> str:
        return f'{self._blech} {self._bling}'

    def __str__(self, /) -> str:
        return self.to_string()


def _bother() -> None:
    what_i_am = _Moreover(1)  # pyright: ignore[reportUnusedVariable]
    moreover = _Moreover(2, 3)
    _console.log(f'Moreover: {moreover.to_string()}')
    moreover2 = _Moreover(4)
    _console.log(f'Moreover again: {moreover2.to_string()}')
    moreover3 = _Moreover(None, 7)
    _console.log(f'Moreover yet again: {moreover3.to_string()}')
    whatever = _Whatever(5)
    _console.log(f'Whatever: {whatever.blah}')


_console: _LoggingConsole = _LoggingConsole(__name__)
_person1: Person = Person('Alice', 50)
_person2: Person = Person('Bob', 45)
_circle1: _Circle = _Circle(2)
_circle2: _Circle = _Circle(1)
_things: _Sequence[_Stringable] = (_person1, _person2, _circle1, _circle2)


def _fn(thing: _Stringable, /) -> int:
    _console.log(thing.to_string())
    return 0


_list_map(_things, _fn)
_Whatever(456)
_Whatever(321)
_Rectangle(7, 8)
_bother()
_whatever: _Whatever = _Whatever(6)
_console.log(f'Whatever again: {_whatever.blah}')
_person_again: Person = Person('Carrie', 40)
_name: str = _person_again.name
_some_age: int = _person_again.age
_console.log(f'name is {_name} and age is {_some_age}')
_person_more: Person = Person(_name, 35)
_console.log(_person_more.to_string())
_twin: Person = Person(_person1.name, _person1.age)
_console.log(f'Some are twins: {_boolean_to_string(_person1 == _twin)}')
_console.log(f'All are twins: {_boolean_to_string(_person2 == _twin)}')
