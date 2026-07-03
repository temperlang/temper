def sum(i: int, j: int, bonus: int | None = None) -> int:
    if bonus is None:
        bonus = 0
    return i + j + bonus


def prod(hidden: "_Hidden", j: int) -> int:
    from ._support import Support

    return Support().prod(hidden.i, j)
