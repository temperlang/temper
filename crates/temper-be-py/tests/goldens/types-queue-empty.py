from collections import deque as _deque
from temper_core import (
    LoggingConsole as _LoggingConsole,
    deque_remove_first as _deque_remove_first,
)

__all__ = ()

_console: _LoggingConsole = _LoggingConsole(__name__)
_queue: _deque[str] = _deque()
_queue.append('a')
_console.log(_deque_remove_first(_queue))
_console.log('before')
_console.log(_deque_remove_first(_queue))
_console.log('not reached')
