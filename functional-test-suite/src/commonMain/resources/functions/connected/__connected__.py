def _connected():
    from ._support import Support

    def sum(i: int, j: int, bonus: int) -> int:
        return i + j + bonus

    def prod(hidden: "_Hidden", j: int) -> int:
        return Support().prod(hidden.i, j)

    global _sum, _prod
    _sum = sum
    _prod = prod


_connected()
del _connected
