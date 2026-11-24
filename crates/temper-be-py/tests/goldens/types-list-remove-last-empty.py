from collections.abc import MutableSequence as _MutableSequence
from temper_core import (
    LoggingConsole as _LoggingConsole,
    list_builder_remove_last as _list_builder_remove_last,
)

__all__ = ()

_console: _LoggingConsole = _LoggingConsole(__name__)
_builder: _MutableSequence[str] = []
_builder.append('a')
_console.log(_list_builder_remove_last(_builder))
_console.log('before')
_console.log(_list_builder_remove_last(_builder))
_console.log('not reached')
