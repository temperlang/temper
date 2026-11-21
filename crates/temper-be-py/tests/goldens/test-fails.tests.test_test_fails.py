from collections.abc import Callable
from temper_core import Bubble, finish_async_work, int_add
from temper_std.testing import Test
from unittest import TestCase


def _tries(f: Callable[[], None], /) -> bool:
    finished = True
    try:
        f()
    except Bubble:
        finished = False
    return finished


def _find(key: str, /) -> int | None:
    if key == 'one':
        return 1
    else:
        return None


class TestTestFails(TestCase):
    def test_passes_before(self, /) -> None:
        'passes before'
        test: Test = Test()
        try:
            def fn() -> None:
                actual: int | None = _find('one')
                expected = 1
                test.assert_(actual == expected, lambda: f"expected find(\"one\") == ({expected}) not ({'null' if actual is None else str(actual)})")
            test.assert_(_tries(fn), lambda: 'expected tries { assert(find("one") == 1); }')
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_fails_past_an_orelse(self, /) -> None:
        'fails past an orelse'
        test: Test = Test()
        try:
            def fn() -> None:
                test.fail('failed on purpose')
            _tries(fn)
            test.fail('swallowed')
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_narrows_after_fail(self, /) -> None:
        'narrows after fail'
        test: Test = Test()
        try:
            n: int | None = _find('one')
            if n is None:
                test.fail('one not found')
            actual = int_add(n, 1)
            expected = 2
            test.assert_(actual == expected, lambda: f'expected n + 1 == ({expected}) not ({actual})')
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_fails_before_narrowing(self, /) -> None:
        'fails before narrowing'
        test: Test = Test()
        try:
            n: int | None = _find('two')
            if n is None:
                test.fail('two not found')
            test.assert_(int_add(n, 1) == 3, lambda: 'went on')
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_collects_soft_failures(self, /) -> None:
        'collects soft failures'
        test: Test = Test()
        try:
            test.assert_(int_add(1, 1) == 3, lambda: 'first')
            test.assert_(True, lambda: 'not shown')
            test.assert_(int_add(2, 2) == 5, lambda: 'second')
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_fails_hard_after_soft_failures(self, /) -> None:
        'fails hard after soft failures'
        test: Test = Test()
        try:
            test.assert_(False, lambda: 'soft before')
            test.fail('then hard')
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_fails_a_hard_assertion_past_an_orelse(self, /) -> None:
        'fails a hard assertion past an orelse'
        test: Test = Test()
        try:
            def fn() -> None:
                test.assert_hard(int_add(1, 1) == 3, lambda: 'hard assertion')
            _tries(fn)
            test.fail('swallowed')
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_hardens_soft_failures_past_an_orelse(self, /) -> None:
        'hardens soft failures past an orelse'
        test: Test = Test()
        try:
            test.assert_(False, lambda: 'soft only')
            def fn2() -> None:
                test.soft_fail_to_hard()
            _tries(fn2)
            test.fail('swallowed')
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_passes_after(self, /) -> None:
        'passes after'
        test: Test = Test()
        try:
            actual: int | None = _find('one')
            expected = 1
            test.assert_(actual == expected, lambda: f"expected find(\"one\") == ({expected}) not ({'null' if actual is None else str(actual)})")
        finally:
            finish_async_work()
            test.soft_fail_to_hard()
