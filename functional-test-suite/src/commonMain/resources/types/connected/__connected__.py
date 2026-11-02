class _connected:
    class Counter:
        """Temper's connected Counter class, with the members Temper would generate."""

        def __init__(self) -> None:
            self._total = 0

        def add(self, i: int, /) -> None:
            self._total += i

        def total(self, /) -> int:
            return self._total
