from collections.abc import Sequence as _Sequence
from temper_core import (
    Bubble as _Bubble,
    Failure as _Failure,
    LoggingConsole as _LoggingConsole,
    failure_cause as _failure_cause,
    int_add as _int_add,
    int_div as _int_div,
    list_get as _list_get,
    with_cause as _with_cause,
)

__all__ = (
    'FailuresInteropError',
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
    'early_cause',
    'sign_name',
    'mixed',
    'first_sign',
    'first_missing',
)


class FailuresInteropError(_Failure, Exception):
    __slots__ = ()


class NotFound(FailuresInteropError):
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


class SignError(FailuresInteropError):
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


class LookupFailed(FailuresInteropError):
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
        return f'{e.message} for {e.name}, from {why}'


def sign_message(text: str, /) -> str:
    try:
        return str(sign(text))
    except SignError as e:
        return e.message


def early_cause(name: str, /) -> str:
    try:
        return str(made_before(name))
    except LookupFailed as e:
        cause: _Failure | None = _failure_cause(e)
        return cause.message if cause is not None else 'nothing'


def sign_name(e: SignError, /) -> str:
    if isinstance(e, BadSign):
        return 'bad sign'
    elif isinstance(e, EmptyText):
        return 'empty text'
    else:
        raise BaseException('panic')


def mixed(key: str, n: int, /) -> int:
    try:
        return _int_add(lookup(key), _int_div(10, n))
    except (_Bubble, NotFound):
        return -1


def first_sign(texts: _Sequence[str], /) -> str:
    length = len(texts)
    i = 0
    while i < length:
        t = _list_get(texts, i)
        i = _int_add(i, 1)
        s: int
        caught: SignError | None = None
        held: int | None = None
        try:
            held = sign(t)
        except SignError as failure:
            caught = failure
        if held is None:  # pyright: ignore[reportUnnecessaryComparison]
            assert caught is not None
            e = caught
            if isinstance(e, BadSign):
                return f'bad {e.text}'
            continue
        else:
            s = held
        return str(s)
    return 'none'


def first_missing(keys: _Sequence[str], /) -> str:
    length = len(keys)
    i = 0
    while i < length:
        k = _list_get(keys, i)
        i = _int_add(i, 1)
        n: int
        caught: NotFound | None = None
        held: int | None = None
        try:
            held = lookup(k)
        except NotFound as failure:
            caught = failure
        if held is None:  # pyright: ignore[reportUnnecessaryComparison]
            assert caught is not None
            e = caught
            return e.key
        else:
            n = held
        if n < 0:
            break
    return 'none'


_console: _LoggingConsole = _LoggingConsole(__name__)
_console.log(f"{lookup('a')} {sign('-')} {setting('a')}")
_console.log(f"{mixed('a', 0)} {mixed('z', 1)} {mixed('a', 5)}")
_console.log(f"{explained('port')}; {explained('a')}; {sign_message('x')}")
_console.log(f"{early_cause('soon')}; {first_missing(('a', 'b', 'c'))} {first_missing(('a',))}")
_console.log(f"{first_sign(('', '+'))} {first_sign(('', '?'))} {first_sign(())}")
