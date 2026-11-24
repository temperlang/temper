from temper_core import (
    Bubble as _Bubble,
    Failure as _Failure,
    LoggingConsole as _LoggingConsole,
    failure_cause as _failure_cause,
    int_add as _int_add,
    int_div as _int_div,
    with_cause as _with_cause,
)

__all__ = (
    'PyFailuresError',
    'NotFound',
    'SignError',
    'BadSign',
    'EmptyText',
    'LookupFailed',
    'lookup',
    'sign',
    'setting',
    'made_before',
    'thrown_again',
    'risky',
    'explained',
    'sign_message',
    'mixed',
)


class PyFailuresError(_Failure, Exception):
    __slots__ = ()


class NotFound(PyFailuresError):
    __slots__ = ('_key',)
    _key: str

    def __init__(self, /, key: str) -> None:
        self._key = key

    @property
    def key(self, /) -> str:
        return self._key

    @property
    def message(self, /) -> str:
        return f'not found (key: {self._key})'


class SignError(PyFailuresError):
    __slots__ = ()


class BadSign(SignError):
    __slots__ = ('_text',)
    _text: str

    def __init__(self, /, text: str) -> None:
        self._text = text

    @property
    def text(self, /) -> str:
        return self._text

    @property
    def message(self, /) -> str:
        return f'bad sign (text: {self._text})'


class EmptyText(SignError):
    __slots__ = ()

    @property
    def message(self, /) -> str:
        return 'no text'


class LookupFailed(PyFailuresError):
    __slots__ = ('_name',)
    _name: str

    def __init__(self, /, name: str) -> None:
        self._name = name

    @property
    def name(self, /) -> str:
        return self._name

    @property
    def message(self, /) -> str:
        return f'lookup failed (name: {self._name})'


def lookup(key: str, /) -> int:
    if key == 'a':
        return 1
    raise NotFound(key)


def sign(text: str, /) -> int:
    if text == '+':
        return 1
    elif text == '-':
        return -1
    elif text == '':
        raise EmptyText()
    else:
        raise BadSign(text)


def setting(name: str, /) -> int:
    try:
        return lookup(name)
    except NotFound as failure:
        raise LookupFailed(name) from failure


def made_before(name: str, /) -> int:
    failure = LookupFailed(name)
    try:
        return lookup(name)
    except NotFound as failure2:
        raise _with_cause(failure, failure2)


def thrown_again(name: str, /) -> int:
    try:
        return setting(name)
    except LookupFailed as e:
        try:
            return lookup('q')
        except NotFound as failure:
            raise _with_cause(e, failure)


def risky(n: int, /) -> int:
    if n > 1:
        raise BaseException('panic')
    return lookup('a')


def explained(name: str, /) -> str:
    try:
        return str(setting(name))
    except LookupFailed as e:
        cause: _Failure | None = _failure_cause(e)
        why = cause.message if cause is not None else 'nothing'
        return f'{e.message}, from {why}'


def sign_message(text: str, /) -> str:
    try:
        return str(sign(text))
    except SignError as e:
        return e.message


def mixed(key: str, n: int, /) -> int:
    try:
        return _int_add(lookup(key), _int_div(10, n))
    except (_Bubble, NotFound):
        return -1


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log(f"{lookup('a')} {sign('-')} {setting('a')}")
_console.log(f"{mixed('a', 0)} {mixed('z', 1)} {mixed('a', 5)}")
_console.log(f"{explained('port')}; {explained('a')}; {sign_message('x')}")
