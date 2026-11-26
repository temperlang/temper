from collections.abc import Callable
from concurrent.futures import Future
from temper_core import (
    BrokenPromise,
    TemperFuture,
    adapt_generator_factory,
    async_launch,
    complete_promise,
    end_test,
    int_add,
    list_get,
    new_unbound_promise,
)
from temper_std.testing import Test
from typing import Any, Generator
from unittest import TestCase


class TestTestAsync(TestCase):
    def test_fails_in_an_async_block(self, /) -> None:
        'fails in an async block'
        test: Test = Test()
        with end_test(test):
            @adapt_generator_factory
            def fn(do_await: Callable[[Future[Any]], Any], /) -> Generator[Any, Any, None]:
                if False:
                    yield
                test.assert_(int_add(1, 1) == 3, lambda: 'failed in async')
            async_launch(fn)

    def test_passes_in_an_async_block(self, /) -> None:
        'passes in an async block'
        test: Test = Test()
        with end_test(test):
            @adapt_generator_factory
            def fn(do_await: Callable[[Future[Any]], Any], /) -> Generator[Any, Any, None]:
                if False:
                    yield
                test.assert_(int_add(1, 1) == 2, lambda: 'not shown')
            async_launch(fn)

    def test_fails_in_a_later_turn(self, /) -> None:
        'fails in a later turn'
        test: Test = Test()
        with end_test(test):
            pb: TemperFuture[int] = new_unbound_promise()
            @adapt_generator_factory
            def fn(do_await: Callable[[Future[Any]], Any], /) -> Generator[Any, Any, None]:
                try:
                    n = yield do_await(pb)
                except BrokenPromise:
                    n = -1
                test.assert_(n == 2, lambda: f'later turn got {n}')
            async_launch(fn)
            @adapt_generator_factory
            def fn3(do_await: Callable[[Future[Any]], Any], /) -> Generator[Any, Any, None]:
                if False:
                    yield
                complete_promise(pb, 1)
            async_launch(fn3)

    def test_passes_after_a_later_turn(self, /) -> None:
        'passes after a later turn'
        test: Test = Test()
        with end_test(test):
            actual = int_add(1, 1)
            expected = 2
            test.assert_(actual == expected, lambda: f'expected 1 + 1 == ({expected}) not ({actual})')

    def test_fails_hard_in_an_async_block(self, /) -> None:
        'fails hard in an async block'
        test: Test = Test()
        with end_test(test):
            @adapt_generator_factory
            def fn(do_await: Callable[[Future[Any]], Any], /) -> Generator[Any, Any, None]:
                if False:
                    yield
                test.fail('failed hard in async')
            async_launch(fn)

    def test_dies_in_an_async_block(self, /) -> None:
        'dies in an async block'
        test: Test = Test()
        with end_test(test):
            @adapt_generator_factory
            def fn(do_await: Callable[[Future[Any]], Any], /) -> Generator[Any, Any, None]:
                if False:
                    yield
                raise BaseException('panic')
            async_launch(fn)

    def test_passes_after(self, /) -> None:
        'passes after'
        test: Test = Test()
        with end_test(test):
            actual = int_add(1, 1)
            expected = 2
            test.assert_(actual == expected, lambda: f'expected 1 + 1 == ({expected}) not ({actual})')

    def test_waits_on_nothing(self, /) -> None:
        'waits on nothing'
        test: Test = Test()
        with end_test(test):
            never: TemperFuture[int] = new_unbound_promise()
            @adapt_generator_factory
            def fn(do_await: Callable[[Future[Any]], Any], /) -> Generator[Any, Any, None]:
                try:
                    n = yield do_await(never)
                except BrokenPromise:
                    n = -1
                test.assert_(False, lambda: f'ran {n}')
            async_launch(fn)

    def test_own_failure_wins(self, /) -> None:
        'own failure wins'
        test: Test = Test()
        with end_test(test):
            test.assert_(False, lambda: 'soft failure')
            @adapt_generator_factory
            def fn2(do_await: Callable[[Future[Any]], Any], /) -> Generator[Any, Any, None]:
                if False:
                    yield
                test.fail('async failure')
            async_launch(fn2)
            ls = (1,)
            actual = list_get(ls, 5)
            expected = 1
            test.assert_(actual == expected, lambda: f'expected ls[5] == ({expected}) not ({actual})')
