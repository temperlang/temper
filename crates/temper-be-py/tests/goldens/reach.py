from temper_core import LoggingConsole as _LoggingConsole, int_add as _int_add

__all__ = ('shown',)


def shown(n: int, /) -> int:
    global _shown_count
    _shown_count = _int_add(_shown_count, 1)
    return _int_add(n, _base)


_console: _LoggingConsole = _LoggingConsole(__name__)
_shown_count: int = 0
_base: int = 1
_checked: int = shown(2)
_console.log(f'shown {shown(1)}')
