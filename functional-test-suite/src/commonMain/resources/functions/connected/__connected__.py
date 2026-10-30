class _connected:
    from ._support import Support

    def sum(i: int, j: int, bonus: int) -> int:
        # Separately, here's an example of where overriding name choices might
        # sometimes be handy.
        return _sum_of3(i, j, bonus)

    def prod(hidden: "_Hidden", j: int) -> int:
        return _connected.Support().prod(hidden.i, j)

    def length(string: str | None) -> int:
        return -1 if string is None else len(string)

    def twice(i: int) -> int:
        return i * 2

    class Counter:
        """Temper's connected Counter class, with the members Temper would generate."""

        def __init__(self) -> None:
            self._total = 0

        def add(self, i: int, /) -> None:
            self._total += i

        def total(self, /) -> int:
            return self._total
