from temper_core import Failure as _Failure, LoggingConsole as _LoggingConsole

__all__ = ()


class _Problem(_Failure):
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


def _checked(n: int, /) -> int:
    try:
        p = _positive(n)
        if p == 1:
            return 0
    except _NotPositive as t_0:
        raise BaseException('panic') from t_0
    return n


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('before')
_console.log(f'after {_checked(-1)}')
