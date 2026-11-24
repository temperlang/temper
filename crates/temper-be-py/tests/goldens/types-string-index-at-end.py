from temper_core import (
    LoggingConsole as _LoggingConsole,
    string_get as _string_get,
)

__all__ = ()


def _code_at(s: str, i: int, /) -> int:
    return _string_get(s, i)


_console: _LoggingConsole = _LoggingConsole(__name__)
_text: str = 'abc'
_console.log(str(_code_at(_text, 0)))
_console.log('before')
_console.log(str(_code_at(_text, len(_text))))
_console.log('not reached')
