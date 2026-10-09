class _connected:
    from pyroaring import BitMap

    def new_bitset_connected() -> "_connected.BitMap":
        return _connected.BitMap()

    def bitset_add(bitset: "_connected.BitMap", i: int) -> None:
        bitset.add(i)

    def bitset_contains(bitset: "_connected.BitMap", i: int) -> bool:
        return i in bitset
