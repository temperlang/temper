def _sum(i: int, j: int, bonus: int) -> int:
    return i + j + bonus


def _prod(hidden: "_Hidden", j: int) -> int:
    from ._support import Support

    return Support().prod(hidden.i, j)
