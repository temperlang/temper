from collections.abc import MutableSequence as _MutableSequence
from temper_core import (
    LoggingConsole as _LoggingConsole,
    list_builder_set as _list_builder_set,
    list_join as _list_join,
)

__all__ = ()


def _put(builder: _MutableSequence[int], i: int, value: int, /) -> None:
    _list_builder_set(builder, i, value)


_console: _LoggingConsole = _LoggingConsole(__name__)
_builder: _MutableSequence[int] = []
_builder.append(1)
_builder.append(2)
_put(_builder, 1, 5)
_console.log(_list_join(_builder, ', ', lambda n: str(n)))
_console.log('before')
_put(_builder, -1, 0)
_console.log('not reached')
