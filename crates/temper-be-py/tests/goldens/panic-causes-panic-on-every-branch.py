from temper_core import Failure as _Failure, LoggingConsole as _LoggingConsole

__all__ = ()


class _Problem(_Failure, Exception):
    __slots__ = ()


class _NotPositive(_Problem):
    __slots__ = ('_n',)
    _n: int

    def __init__(self, /, n: int) -> None:
        self._n = n

    @property
    def n(self, /) -> int:
        return self._n

    @property
    def message(self, /) -> str:
        return f'not positive (n: {self._n})'


def _positive(n: int, /) -> int:
    if n <= 0:
        raise _NotPositive(n)
    return n


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('before')
_k: int
try:
    _k = _positive(1)
except _NotPositive:
    _k = 0
if _k > 0:
    raise BaseException('panic')
else:
    try:
        _positive(-1)
    except _NotPositive as _t_0:
        raise BaseException('panic') from _t_0
    raise BaseException('panic')
_console.log('after')
