class _connected:
    from ._support import Support

    def sum(i: int, j: int, bonus: int) -> int:
        return i + j + bonus

    def prod(hidden: "_Hidden", j: int) -> int:
        return _connected.Support().prod(hidden.i, j)
