from temper_core import (
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    int_add as _int_add,
    int_sub as _int_sub,
)

__all__ = ()


class _Ping:
    __slots__ = ('_peer', '_n', '_most')
    _peer: '_Pong'
    _n: int
    _most: int

    def __init__(self, /, peer: '_Pong') -> None:
        self._peer = peer
        self._n = 0
        self._most = 0

    @property
    def peer(self, /) -> '_Pong':
        return self._peer

    @property
    def n(self, /) -> int:
        return self._n

    @n.setter
    def n(self, value: int, /) -> None:
        self._n = value

    @property
    def most(self, /) -> int:
        return self._most

    @most.setter
    def most(self, value: int, /) -> None:
        self._most = value

    def ping(self, /) -> int:
        self._peer.pong(self)
        return self._n

    def pass_(self, /) -> int:
        return self._peer.bump(self, self._n)

    def record(self, /) -> bool:
        self._n = _int_add(self._n, 1)
        if self._n > self._most:
            self._most = self._n
        return self._n > _int_sub(self._most, 1)

    def around(self, /) -> int:
        return _int_add(_int_add(self._n, self._peer.bump(self, 10)), self._n)


class _Pong:
    __slots__ = ()

    def pong(self, a: _Ping, /) -> None:
        a.n = _int_add(a.n, 1)

    def bump(self, a: _Ping, by: int, /) -> int:
        a.n = _int_add(a.n, by)
        return a.n


_console: _LoggingConsole = _LoggingConsole(__name__)
_p: _Ping = _Ping(_Pong())
_console.log(f'ping={_p.ping()}')
_console.log(f'pass={_p.pass_()}')
_console.log(f'record={_boolean_to_string(_p.record())} most={_p.most}')
_console.log(f'around={_p.around()} n={_p.n}')
