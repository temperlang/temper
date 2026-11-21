from collections.abc import Callable
from concurrent.futures import Future
from temper_core import (
    Bubble,
    TemperFuture,
    adapt_generator_factory,
    async_launch,
    complete_promise,
    finish_async_work,
    int_add,
    new_unbound_promise,
)
from temper_std.testing import Test
from typing import Any, Generator
from unittest import TestCase


class TestTestAsync(TestCase):
    def test_fails_in_an_async_block(self, /) -> None:
        'fails in an async block'
        test: Test = Test()
        try:
            @adapt_generator_factory
            def fn(do_await: Callable[[Future[Any]], Any], /) -> Generator[Any, Any, None]:
                if False:
                    yield
                test.assert_(int_add(1, 1) == 3, lambda: 'failed in async')
            async_launch(fn)
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_passes_in_an_async_block(self, /) -> None:
        'passes in an async block'
        test: Test = Test()
        try:
            @adapt_generator_factory
            def fn(do_await: Callable[[Future[Any]], Any], /) -> Generator[Any, Any, None]:
                if False:
                    yield
                test.assert_(int_add(1, 1) == 2, lambda: 'not shown')
            async_launch(fn)
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_fails_in_a_later_turn(self, /) -> None:
        'fails in a later turn'
        test: Test = Test()
        try:
            pb: TemperFuture[int] = new_unbound_promise()
            @adapt_generator_factory
            def fn(do_await: Callable[[Future[Any]], Any], /) -> Generator[Any, Any, None]:
                try:
                    n = yield do_await(pb)
                except Bubble:
                    n = -1
                test.assert_(n == 2, lambda: f'later turn got {n}')
            async_launch(fn)
            @adapt_generator_factory
            def fn3(do_await: Callable[[Future[Any]], Any], /) -> Generator[Any, Any, None]:
                if False:
                    yield
                complete_promise(pb, 1)
            async_launch(fn3)
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_passes_after_a_later_turn(self, /) -> None:
        'passes after a later turn'
        test: Test = Test()
        try:
            actual = int_add(1, 1)
            expected = 2
            test.assert_(actual == expected, lambda: f'expected 1 + 1 == ({expected}) not ({actual})')
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_fails_hard_in_an_async_block(self, /) -> None:
        'fails hard in an async block'
        test: Test = Test()
        try:
            @adapt_generator_factory
            def fn(do_await: Callable[[Future[Any]], Any], /) -> Generator[Any, Any, None]:
                if False:
                    yield
                test.fail('failed hard in async')
            async_launch(fn)
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_panics_in_an_async_block(self, /) -> None:
        'panics in an async block'
        test: Test = Test()
        try:
            @adapt_generator_factory
            def fn(do_await: Callable[[Future[Any]], Any], /) -> Generator[Any, Any, None]:
                if False:
                    yield
                raise BaseException('panic')
            async_launch(fn)
        finally:
            finish_async_work()
            test.soft_fail_to_hard()

    def test_passes_after(self, /) -> None:
        'passes after'
        test: Test = Test()
        try:
            actual = int_add(1, 1)
            expected = 2
            test.assert_(actual == expected, lambda: f'expected 1 + 1 == ({expected}) not ({actual})')
        finally:
            finish_async_work()
            test.soft_fail_to_hard()
