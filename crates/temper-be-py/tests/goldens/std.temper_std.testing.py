from collections.abc import (
    Callable as _Callable,
    MutableSequence as _MutableSequence,
    Sequence as _Sequence,
)
from temper_core import (
    Bubble as _Bubble,
    Pair as _Pair,
    int_add as _int_add,
    int_to_string as _int_to_string,
    list_get as _list_get,
    list_join as _list_join,
    list_map as _list_map,
    listed_reduce_from as _listed_reduce_from,
    string_get as _string_get,
    string_next as _string_next,
)
from typing import NoReturn as _NoReturn

__all__ = (
    'Test',
    'process_test_cases',
    'report_test_results',
    'run_test_cases',
    'run_test',
)


class Test:
    __slots__ = ('_failed_on_assert', '_passing', '_messages')
    _failed_on_assert: bool
    _passing: bool
    _messages: _MutableSequence[str]

    def __init__(self, /) -> None:
        self._failed_on_assert = False
        self._passing = True
        self._messages = []

    def assert_(self, success: bool, message: _Callable[[], str], /) -> None:
        if not success:
            self._passing = False
            self._messages.append(message())

    def fail(self, message: str, /) -> _NoReturn:
        self._passing = False
        self._messages.append(message)
        self._failed_on_assert = True
        raise AssertionError(str(self.messages_combined()))

    def assert_hard(self, success: bool, message: _Callable[[], str], /) -> None:
        if not success:
            self.fail(message())

    def soft_fail_to_hard(self, /) -> None:
        if self.has_unhandled_fail:
            self._failed_on_assert = True
            raise AssertionError(str(self.messages_combined()))

    @property
    def passing(self, /) -> bool:
        return self._passing

    def messages(self, /) -> _Sequence[str]:
        return tuple(self._messages)

    @property
    def failed_on_assert(self, /) -> bool:
        return self._failed_on_assert

    @property
    def has_unhandled_fail(self, /) -> bool:
        return not (self._failed_on_assert or self._passing)

    def messages_combined(self, /) -> str | None:
        if not self._messages:
            return None
        else:
            return _list_join(self._messages, ', ', lambda it: it)


def process_test_cases(test_cases: _Sequence[_Pair[str, _Callable[[Test], None]]], /) -> _Sequence[_Pair[str, _Sequence[str]]]:
    def fn(test_case: _Pair[str, _Callable[[Test], None]], /) -> _Pair[str, _Sequence[str]]:
        key = test_case.key
        fun = test_case.value
        test = Test()
        had_bubble = False
        try:
            fun(test)
        except _Bubble:
            had_bubble = True
        messages = test.messages()
        t_1: _Sequence[str]
        if test.passing and not had_bubble:
            t_1 = ()
        else:
            t_0: _Sequence[str]
            if had_bubble and not test.failed_on_assert:
                all_messages = list(messages)
                all_messages.append('Bubble')
                t_0 = tuple(all_messages)
            else:
                t_0 = messages
            t_1 = t_0
        failures = t_1
        return _Pair(key, failures)
    return _list_map(test_cases, fn)


def _escape_xml(s: str, /) -> str:
    sb = ['']
    end = len(s)
    emitted = 0
    i = 0
    while i - end < 0:
        c = _string_get(s, i)
        esc: str
        if c == 38:
            esc = '&amp;'
        elif c == 60:
            esc = '&lt;'
        elif c == 62:
            esc = '&gt;'
        elif c == 39:
            esc = '&#39;'
        elif c == 34:
            esc = '&#34;'
        elif c == 10 or (c == 13 or c == 9):
            i = _string_next(s, i)
            continue
        elif c < 32 or (c == 65534 or c == 65535):
            esc = f'[0x{_int_to_string(c, 16)}]'
        else:
            i = _string_next(s, i)
            continue
        sb.append(s[emitted : i])
        sb.append(esc)
        emitted = _string_next(s, i)
        i = _string_next(s, i)
    if emitted == 0:
        return s
    else:
        sb.append(s[emitted : end])
        return ''.join(sb)


def report_test_results(test_results: _Sequence[_Pair[str, _Sequence[str]]], write_line: _Callable[[str], None], /) -> None:
    write_line('<testsuites>')
    total = str(len(test_results))
    fails2 = str(_listed_reduce_from(test_results, 0, lambda fails, test_result: _int_add(fails, 0 if not test_result.value else 1)))
    totals = f"tests='{total}' failures='{fails2}'"
    write_line(f"  <testsuite name='suite' {totals} time='0.0'>")
    i = 0
    while i < len(test_results):
        test_result = _list_get(test_results, i)
        failure_messages = test_result.value
        name = _escape_xml(test_result.key)
        basics = f"name='{name}' classname='{name}' time='0.0'"
        if not failure_messages:
            write_line(f'    <testcase {basics} />')
        else:
            write_line(f'    <testcase {basics}>')
            message = _escape_xml(_list_join(failure_messages, ', ', lambda it: it))
            write_line(f"      <failure message='{message}' />")
            write_line('    </testcase>')
        i = _int_add(i, 1)
    write_line('  </testsuite>')
    write_line('</testsuites>')


def run_test_cases(test_cases: _Sequence[_Pair[str, _Callable[[Test], None]]], /) -> str:
    report = ['']
    t_0 = process_test_cases(test_cases)
    def fn(line: str, /) -> None:
        report.append(line)
        report.append('\n')
    report_test_results(t_0, fn)
    return ''.join(report)


def run_test(test_fun: _Callable[[Test], None], /) -> None:
    test = Test()
    try:
        test_fun(test)
    except _Bubble:
        test.assert_(False, lambda: 'bubble during test running')
    test.soft_fail_to_hard()
