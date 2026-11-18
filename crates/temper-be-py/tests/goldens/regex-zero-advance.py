from temper_core import (
    LoggingConsole as _LoggingConsole,
    string_next as _string_next,
)
from temper_std.regex import (
    CodePoints as _CodePoints,
    Or as _Or,
    Regex as _Regex,
    Repeat as _Repeat,
    Sequence as _Sequence,
    begin as _begin,
    space as _space,
)

__all__ = ('bulletize_list',)


def bulletize_list(text: str, /) -> str:
    replaced = _comma_item_start_regex.replace(text, lambda match: '\n- ')
    return replaced[_string_next(replaced, 0) : len(replaced)]


_console: _LoggingConsole = _LoggingConsole(__name__)
_comma_item_start_regex: _Regex = _Sequence((_Or((_begin, _CodePoints(','))), _Repeat(_space, 0, None, False))).compiled()
_console.log(bulletize_list('apples, bananas, cherries'))
