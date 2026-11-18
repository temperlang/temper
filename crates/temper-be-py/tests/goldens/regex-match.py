from collections.abc import Sequence as _Sequence_
from temper_core import (
    Bubble as _Bubble,
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    int_add as _int_add,
    list_get as _list_get,
    mapped_get as _mapped_get,
    string_count_between as _string_count_between,
)
from temper_std.regex import (
    Capture as _Capture,
    CodePoints as _CodePoints,
    CodeRange as _CodeRange,
    CodeSet as _CodeSet,
    Group as _Group,
    Match as _Match,
    Or as _Or,
    Regex as _Regex,
    RegexNode as _RegexNode,
    Repeat as _Repeat,
    Sequence as _Sequence,
    begin as _begin_,
    dot as _dot,
    entire as _entire,
    one_or_more as _one_or_more,
    word_boundary as _word_boundary,
)

__all__ = ()


def _check_group(group: _Group, /) -> None:
    name = group.name
    value = group.value
    begin = group.begin
    end = group.end
    size = str(_string_count_between(_text, begin, end))
    begin2 = str(_string_count_between(_text, 0, begin))
    _console.log(f"match group '{name}': '{value}' at {begin2} size {size}")


_console: _LoggingConsole = _LoggingConsole(__name__)
_regex: _Regex = _Sequence((_one_or_more(_CodeSet((_CodePoints('ab'),))), _CodePoints('c'))).compiled()
_full: _Regex = _entire(_regex.data).compiled()
_strings: _Sequence_[str] = ('abc', 'ac', 'bbabac', 'ab', 'dc', 'acc', 'cbc')
_length = len(_strings)
_i = 0
while _i < _length:
    _string = _list_get(_strings, _i)
    _i = _int_add(_i, 1)
    _console.log(f'found {_string}: {_boolean_to_string(_regex.found(_string))}')
    _console.log(f'matches {_string}: {_boolean_to_string(_full.found(_string))}')
_catcher: _Regex = _Sequence((_Repeat(_CodePoints('a'), 1, None, False), _Capture('intro', _Repeat(_CodePoints('b'), 1, None, False)), _CodePoints('c'), _Repeat(_Or((_Capture('option1', _CodePoints('d')), _Capture('alphaOption2', _CodePoints('e')))), 1, None, False))).compiled()
_text: str = '🌍aaabbceef'
_match: _Match = _catcher.find(_text)
_check_group(_match.full)
_check_group(_mapped_get(_match.groups, 'intro'))
_check_group(_mapped_get(_match.groups, 'alphaOption2'))
_thing: _Regex = _Repeat(_CodeSet((_CodeRange(97, 122),), False), 1, None, False).compiled()
_multi_text: str = '---abc---xyz---'
_first: _Match = _thing.find(_multi_text)
_second: _Match = _thing.find(_multi_text, _first.full.end)
_console.log(f'Found both {_first.full.value} and {_second.full.value}.')
_more_text: str = '🌍aaabbcee🌎abcd🌏'


def _fn(match: _Match, /) -> str:
    groups = match.groups
    try:
        option1 = _mapped_get(groups, 'option1').value
    except _Bubble:
        option1 = ''
    try:
        alpha_option2 = _mapped_get(groups, 'alphaOption2').value
    except _Bubble:
        alpha_option2 = ''
    begin = _string_count_between(_more_text, 0, match.full.begin)
    return f'-{option1}{alpha_option2}{begin}-'


_replaced: str = _catcher.replace(_more_text, _fn)
_console.log(f'replaced: {_replaced}')
_not_replaced: str = _catcher.replace('nope', lambda match: 'hi')
_console.log(f'not replaced: {_not_replaced}')
_no_match: str
try:
    _no_match = _catcher.find('not here').full.value
except _Bubble:
    _no_match = 'sorry'
_console.log(f'No match says {_no_match}')
_or: _RegexNode = _Or((_CodePoints('ab'), _CodePoints('bc'))).compiled().data
_option1: str = 'ab'
_option2: str = 'bc'
_option_none: str = 'cb'
_console.log(f'or option 1: {_boolean_to_string(_or.found(_option1))}')
_console.log(f'or option 2: {_boolean_to_string(_or.found(_option2))}')
_console.log(f'or option none: {_boolean_to_string(_or.found(_option_none))}')
_begin: _Regex = _Sequence((_begin_, _CodePoints('a'))).compiled()
_begins_with_a_true: str = 'ab'
_begins_with_a_false: str = 'bab'
_console.log(f'begin true: {_boolean_to_string(_begin.found(_begins_with_a_true))}')
_console.log(f'begin false: {_boolean_to_string(_begin.found(_begins_with_a_false))}')
_negated_basic: _Regex = _entire(_Sequence((_CodePoints('a'), _CodeSet((_CodeRange(97, 99),), True))).compiled().data).compiled()
_nb_true: str = 'a🌍'
_nb_false: str = 'ac'
_console.log(f'negated basic true: {_boolean_to_string(_negated_basic.found(_nb_true))}')
_console.log(f'negated basic false: {_boolean_to_string(_negated_basic.found(_nb_false))}')
_simpler_lines: str = 'It was the best of times.\nIt was the worst of times.'
_console.log(_Repeat(_CodeSet((), True), 1, None, False).compiled().find(_simpler_lines).full.value)
_t_0: str
try:
    _t_0 = _Repeat(_CodeSet((), False), 1, None, False).compiled().find(_simpler_lines).full.value
except _Bubble:
    _t_0 = '-'
_console.log(_t_0)
_not_low: _Regex = _one_or_more(_CodeSet((_CodeRange(0, 47),), True)).compiled()
_console.log(f"not low: {_not_low.find('-/a🌍-').full.value}")
_console.log('not space or >: ' + _Repeat(_CodeSet((_CodePoints('\t'), _CodePoints('\n'), _CodePoints(' '), _CodePoints('>')), True), 1, None, False).compiled().find('ab>c').full.value)
_id_start: _RegexNode = _CodeSet((_CodeRange(97, 122), _CodeRange(65, 90), _CodePoints('_')), False).compiled().data
_id_continue: _Or = _Or((_id_start, _CodeSet((_CodeRange(48, 57),), False).compiled().data))
_word: _Regex = _Sequence((_word_boundary, _id_start, _Repeat(_id_continue, 0, None), _word_boundary)).compiled()
_expected: str = 'def_456'
_found: str = _word.find(f'123_abc {_expected} ghi_789').full.value
_console.log(f'Found id: {_found}')
_string_to_escape: str = '$(*^'
_regex_with_interpolated_string: _Regex = _Sequence((_dot, _Repeat(_CodePoints(_string_to_escape), 1, None, False), _dot)).compiled()
_string_to_escape_and_more: str = f'a{_string_to_escape}{_string_to_escape}b'
_escaped_match: _Match = _regex_with_interpolated_string.find(_string_to_escape_and_more)
_console.log(_escaped_match.full.value)
_own_full_match: _Match = _Sequence((_dot, _Capture('full', _dot))).compiled().find('ab')
_own_full: str = _mapped_get(_own_full_match.groups, 'full').value
_console.log(f'Own full: {_own_full} vs {_own_full_match.full.value}')
_combo_lines: str = 'It was the best\nof times.\r\n\rIt was the worst of times.'
_list = _Or((_CodePoints('\n'), _Sequence((_CodePoints('\r'), _Repeat(_CodePoints('\n'), 0, 1, False))))).compiled().split(_combo_lines)
_length2 = len(_list)
_i2 = 0
while _i2 < _length2:
    _line = _list_get(_list, _i2)
    _i2 = _int_add(_i2, 1)
    _console.log(f'{_line};')
