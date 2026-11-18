from modules.modules import sum_ as _sum_
from temper_core import int_mul as _int_mul

__all__ = ('sum_twice',)


def sum_twice(n: int, /) -> int:
    return _int_mul(_sum_(n), 2)
