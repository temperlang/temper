from temper_core import int_add as _int_add

__all__ = ('wrap', 'Pad')


def wrap(text: str, open: str | None = None, close: str | None = None, /) -> str:
    if open is None:
        open = '['
    if close is None:
        close = open
    return open + text + close


class Pad:
    __slots__ = ('_left', '_right')
    _left: int
    _right: int

    def __init__(self, /, left: int | None = None, right: int | None = None) -> None:
        if left is None:
            left = 1
        if right is None:
            right = 2
        self._left = left
        self._right = right

    @property
    def left(self, /) -> int:
        return self._left

    @property
    def right(self, /) -> int:
        return self._right

    def apply(self, text: str, fill: str | None = None, /) -> str:
        if fill is None:
            fill = '.'
        out = text
        i = 0
        while i < self._left:
            out = fill + out
            i = _int_add(i, 1)
        i = 0
        while i < self._right:
            out = out + fill
            i = _int_add(i, 1)
        return out
