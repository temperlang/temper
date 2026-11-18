from temper_core import (
    Bubble as _Bubble,
    LoggingConsole as _LoggingConsole,
    boolean_to_string as _boolean_to_string,
    string_from_code_point as _string_from_code_point,
    string_step as _string_step,
)

__all__ = ()


class _StringBuilderOwner:
    __slots__ = ('_builder',)
    _builder: list[str]

    def __init__(self, /, builder: list[str]) -> None:
        self._builder = builder

    @property
    def builder(self, /) -> list[str]:
        return self._builder


_console: _LoggingConsole = _LoggingConsole(__name__)
_builder: list[str] = ['']
_builder.append('Hi ')
_builder.append(_string_from_code_point(127759))
_source: str = 'Wow!! Neato!'
_bang_index: int = _string_step(_source, 0, 3)
_space_index: int = _string_step(_source, _bang_index, 2)
_builder.append(_source[_bang_index : _space_index])
_built: str = ''.join(_builder)
_builder.append('\n')
_builder.append(_built)
_console.log(''.join(_builder))
try:
    _builder.append(_string_from_code_point(1114112))
except _Bubble:
    _console.log('High code blocked.')
try:
    _builder.append(_string_from_code_point(55296))
except _Bubble:
    _console.log('Surrogate blocked.')
_console.log(''.join(_builder))
_builder.clear()
_builder.append('Back to square zero.')
_console.log(''.join(_builder))
_builder.clear()
_builder.append('a??=b??/c??>d???e')
_marks: str = ''.join(_builder)
_console.log(_marks)
_console.log(f"Same: {_boolean_to_string(_marks == 'a??=b??/c??>d???e')}")
_builder.clear()
_builder.append('??')
try:
    _builder.append(_string_from_code_point(1))
except _Bubble:
    _console.log('No U+1.')
_builder.append('0')
try:
    _builder.append(_string_from_code_point(8364))
except _Bubble:
    _console.log('No euro.')
_builder.append('1')
_console.log('Digits after escapes: ' + _boolean_to_string(''.join(_builder) == '??\x010€1'))
_owner: _StringBuilderOwner = _StringBuilderOwner([''])
_owner.builder.append('Hi from inside.')
_console.log(''.join(_owner.builder))
