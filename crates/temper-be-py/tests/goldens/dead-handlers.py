from temper_core import LoggingConsole as _LoggingConsole, int_add as _int_add

__all__ = ()


def _caught(n: int, /) -> int:
    return _int_add(n, 1)


def _or_else(n: int, /) -> int:
    return _int_add(n, 2)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log(str(_caught(1)))
_console.log(str(_or_else(1)))
_console.log('caught')
_console.log('or else')
