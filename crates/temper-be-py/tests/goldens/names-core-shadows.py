from collections.abc import Sequence as _Sequence
from core_shadows.mine import summary as _summary
from temper_core import LoggingConsole as _LoggingConsole, int_add as _int_add

__all__ = ()


def _bump(console: int, /) -> int:
    return _int_add(console, 1)


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log(str(_summary()))
_s: str = "core's string"
_console.log(_s)
_xs: _Sequence[int] = (1, 2)
_console.log(str(len(_xs)))
_console.log(str(_bump(5)))
