from temper_core import LoggingConsole as _LoggingConsole

__all__ = ()


class _C:
    __slots__ = ('_x', '_y')
    _x: str
    _y: str

    def __init__(self, /, x: str, y: str) -> None:
        self._x = x
        self._y = y

    @property
    def x(self, /) -> str:
        return self._x

    @property
    def y(self, /) -> str:
        return self._y

    def echo(self, input: str, /) -> None:
        _console.log(input)


_console: _LoggingConsole = _LoggingConsole(__name__)
_c: _C = _C('Hello', 'World')
_console.log(f'{_c.x}, {_c.y}!')
_c.echo('Hello World, again!')
