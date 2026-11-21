from temper_core import LoggingConsole as _LoggingConsole

__all__ = ()


def _boom(n: int, /) -> int:
    if n > 0:
        raise BaseException('panic')
    return n


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log('before')
_boom(1)
_console.log('after')
