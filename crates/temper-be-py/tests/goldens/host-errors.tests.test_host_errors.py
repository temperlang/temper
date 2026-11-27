from collections import deque
from collections.abc import MutableSequence, Sequence
from temper_core import (
    Failure,
    deque_remove_first,
    end_test,
    int_add,
    list_builder_add,
    list_builder_remove_last,
    list_builder_set,
    list_get,
    listed_reduce,
    string_get,
)
from temper_std.regex import CodePoints
from temper_std.testing import Test
from unittest import TestCase


class _Problem(Failure, Exception):
    __slots__ = ()


class _Oops(_Problem):
    __slots__ = ()

    @property
    def message(self, /) -> str:
        return 'oops'


def _at(i: int, /) -> int:
    if i == 99:
        raise _Oops()
    return i


def _built() -> MutableSequence[int]:
    lb: MutableSequence[int] = []
    lb.append(1)
    return lb


def _read(ls: Sequence[int], i: int, /) -> int:
    return list_get(ls, _at(i))


def _write(lb: MutableSequence[int], i: int, /) -> int:
    list_builder_set(lb, _at(i), 3)
    return len(lb)


def _insert(lb: MutableSequence[int], i: int, /) -> int:
    list_builder_add(lb, 2, _at(i))
    return len(lb)


def _remove_last(lb: MutableSequence[int], i: int, /) -> int:
    _at(i)
    return list_builder_remove_last(lb)


def _remove_first(d: deque[int], i: int, /) -> int:
    _at(i)
    return deque_remove_first(d)


def _sum(ls: Sequence[int], i: int, /) -> int:
    _at(i)
    def fn(a: int, b: int, /) -> int:
        return int_add(a, b)
    return listed_reduce(ls, fn)


def _code_at(s: str, i: int, /) -> int:
    _at(i)
    return string_get(s, len(s))


def _replaced(i: int, /) -> str:
    _at(i)
    return CodePoints('b').compiled().replace('abc', lambda match: list_get(('x',), i))


class TestHostErrors(TestCase):
    def test_list_read(self, /) -> None:
        'list read'
        test: Test = Test()
        with end_test(test):
            try:
                r = _read((1, 2), 5)
            except _Oops:
                r = -1
            test.assert_(r != -1, lambda: 'caught')

    def test_list_read_at_the_end(self, /) -> None:
        'list read at the end'
        test: Test = Test()
        with end_test(test):
            ls = (1, 2)
            try:
                r = _read(ls, len(ls))
            except _Oops:
                r = -1
            test.assert_(r != -1, lambda: 'caught')

    def test_negative_index(self, /) -> None:
        'negative index'
        test: Test = Test()
        with end_test(test):
            try:
                r = _read(_built(), -1)
            except _Oops:
                r = -1
            test.assert_(r != -1, lambda: 'caught')

    def test_set(self, /) -> None:
        'set'
        test: Test = Test()
        with end_test(test):
            try:
                r = _write(_built(), 5)
            except _Oops:
                r = -1
            test.assert_(r != -1, lambda: 'caught')

    def test_set_at_the_end(self, /) -> None:
        'set at the end'
        test: Test = Test()
        with end_test(test):
            lb = _built()
            try:
                r = _write(lb, len(lb))
            except _Oops:
                r = -1
            test.assert_(r != -1, lambda: 'caught')

    def test_insert(self, /) -> None:
        'insert'
        test: Test = Test()
        with end_test(test):
            try:
                r = _insert(_built(), 5)
            except _Oops:
                r = -1
            test.assert_(r != -1, lambda: 'caught')

    def test_insert_past_the_end(self, /) -> None:
        'insert past the end'
        test: Test = Test()
        with end_test(test):
            lb = _built()
            try:
                r = _insert(lb, int_add(len(lb), 1))
            except _Oops:
                r = -1
            test.assert_(r != -1, lambda: 'caught')

    def test_insert_at_the_end_appends(self, /) -> None:
        'insert at the end appends'
        test: Test = Test()
        with end_test(test):
            lb = _built()
            try:
                n = _insert(lb, len(lb))
            except _Oops:
                n = -1
            test.assert_(n == 2 and list_get(lb, 1) == 2, lambda: "didn't append")

    def test_remove_last(self, /) -> None:
        'remove last'
        test: Test = Test()
        with end_test(test):
            try:
                r = _remove_last([], 0)
            except _Oops:
                r = -1
            test.assert_(r != -1, lambda: 'caught')

    def test_remove_first(self, /) -> None:
        'remove first'
        test: Test = Test()
        with end_test(test):
            try:
                r = _remove_first(deque(), 0)
            except _Oops:
                r = -1
            test.assert_(r != -1, lambda: 'caught')

    def test_reduce(self, /) -> None:
        'reduce'
        test: Test = Test()
        with end_test(test):
            try:
                r = _sum((), 0)
            except _Oops:
                r = -1
            test.assert_(r != -1, lambda: 'caught')

    def test_string_read(self, /) -> None:
        'string read'
        test: Test = Test()
        with end_test(test):
            try:
                r = _code_at('ab', 0)
            except _Oops:
                r = -1
            test.assert_(r != -1, lambda: 'caught')

    def test_replace_callback(self, /) -> None:
        'replace callback'
        test: Test = Test()
        with end_test(test):
            try:
                r = _replaced(3)
            except _Oops:
                r = ''
            test.assert_(r != '', lambda: 'caught')
