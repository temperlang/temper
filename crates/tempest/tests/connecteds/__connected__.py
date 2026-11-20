class _connected:
    from pyroaring import BitMap

    class Bitset:
        """Temper's connected Bitset class, with the members Temper would generate."""

        def __init__(self) -> None:
            self._roaring = _connected.BitMap()

        def add(self, i: int, /) -> None:
            self._roaring.add(i)

        def contains(self, i: int, /) -> bool:
            return i in self._roaring
