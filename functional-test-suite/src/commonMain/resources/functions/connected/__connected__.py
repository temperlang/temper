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
