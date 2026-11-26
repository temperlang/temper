__all__ = ('State', 'Engine', 'Parts', 'first_id', 'Spot')


class State:
    __slots__ = ('_id',)
    _id: int

    def __init__(self, /, id: int) -> None:
        self._id = id

    @property
    def id(self, /) -> int:
        return self._id


class Engine:
    __slots__ = ('_n',)
    _n: int

    def __init__(self, /, n: int) -> None:
        self._n = n

    @property
    def n(self, /) -> int:
        return self._n


class Parts:
    __slots__ = ('_count',)
    _count: int

    def __init__(self, /, count: int) -> None:
        self._count = count

    @property
    def count(self, /) -> int:
        return self._count


class Spot:
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


first_id: int = 1
