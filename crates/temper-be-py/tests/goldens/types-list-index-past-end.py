from collections.abc import Sequence as _Sequence
from temper_core import (
    LoggingConsole as _LoggingConsole,
    list_get as _list_get,
)

__all__ = ()


def _at(listed: _Sequence[str], i: int, /) -> str:
    return _list_get(listed, i)


_console: _LoggingConsole = _LoggingConsole(__name__)
_letters: _Sequence[str] = ('a', 'b', 'c')
_console.log(_at(_letters, 2))
_console.log('before')
_console.log(_at(_letters, 3))
_console.log('not reached')
