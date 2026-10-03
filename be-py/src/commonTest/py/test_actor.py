"""
Host threads calling into @actor instances.

The classes below have the shape PyTranslator gives an @actor class (see
PyBackendTest.actorClass): an `_actor` slot made first in `__init__`, and
every method, getter, setter, constructor body and nested function inside
`with this._actor:`.
"""

import sys
import threading
import unittest as ut
from concurrent.futures import Future
from typing import Callable, List, Optional

import temper_core as rt
from temper_core import ActorLock, actor_steps, adapt_generator_factory

TIMEOUT = 10


class Account:
    _actor: ActorLock
    _balance: int
    _log: List[str]
    __slots__ = ("_actor", "_balance", "_log")

    def __init__(this) -> None:
        this._actor = ActorLock("Account")
        with this._actor:
            this._balance = 0
            this._log = []

    def _bump(this, n: int) -> None:
        with this._actor:
            # Read, yield the GIL, write: loses updates if two threads
            # interleave here.
            before = this._balance
            if n % 7 == 0:
                threading.Event().wait(0)
            this._balance = before + n

    def deposit(this, n: int) -> int:
        with this._actor:
            this._bump(n)
            return this._balance

    def withdraw(this, n: int) -> int:
        with this._actor:
            if n > this._balance:
                raise RuntimeError()  # Temper `bubble()` on py
            this._balance = this._balance - n
            return this._balance

    def each(this, xs: List[int], f: Callable[[int], None]) -> None:
        with this._actor:
            for x in xs:
                f(x)

    def total_via_callback(this, xs: List[int]) -> int:
        with this._actor:

            def fn(x: int) -> None:
                with this._actor:
                    this.deposit(x)

            this.each(xs, fn)
            return this._balance

    def call_other(this, other: "Account", before: Callable[[], None]) -> int:
        with this._actor:
            before()
            return other.deposit(1)

    def ping_back(this, other: "Pinger") -> int:
        with this._actor:
            return other.ping(this)

    def later(this, gate: "Future[None]", seen: List[str]) -> None:
        with this._actor:

            @actor_steps(this._actor)
            @adapt_generator_factory
            def fn(do_await):  # type: ignore[no-untyped-def]
                seen.append("step 1 balance=%d" % this._balance)
                yield do_await(gate)
                seen.append("step 2 balance=%d" % this._balance)

            rt.async_launch(fn)

    @property
    def balance(this) -> int:
        with this._actor:
            return this._balance


class Pinger:
    _actor: ActorLock
    __slots__ = ("_actor",)

    def __init__(this) -> None:
        this._actor = ActorLock("Pinger")

    def ping(this, a: Account) -> int:
        with this._actor:
            return a.balance


def run_threads(targets: List[Callable[[], None]]) -> List[threading.Thread]:
    threads = [threading.Thread(target=t, daemon=True) for t in targets]
    for t in threads:
        t.start()
    for t in threads:
        t.join(TIMEOUT)
    return threads


class TestActor(ut.TestCase):
    def setUp(self) -> None:
        self.old_interval = sys.getswitchinterval()
        sys.setswitchinterval(1e-6)

    def tearDown(self) -> None:
        sys.setswitchinterval(self.old_interval)

    def test_many_threads_exact_count(self) -> None:
        a = Account()
        n_threads, n_calls = 8, 5000

        def work() -> None:
            for _ in range(n_calls):
                a.deposit(7)

        threads = run_threads([work] * n_threads)
        self.assertFalse(any(t.is_alive() for t in threads))
        self.assertEqual(7 * n_threads * n_calls, a.balance)

    def test_self_call_and_callback_reenter(self) -> None:
        a = Account()
        self.assertEqual(6, a.total_via_callback([1, 2, 3]))

    def test_a_b_a_on_one_thread_runs_inline(self) -> None:
        a = Account()
        a.deposit(5)
        self.assertEqual(5, a.ping_back(Pinger()))

    def test_cycle_across_threads_panics(self) -> None:
        a, b = Account(), Account()
        both_in = threading.Barrier(2, timeout=TIMEOUT)
        results: List[str] = []

        def call(x: Account, y: Account) -> None:
            try:
                x.call_other(y, both_in.wait)
                results.append("ok")
            except RuntimeError as e:
                results.append(str(e))

        threads = run_threads([lambda: call(a, b), lambda: call(b, a)])
        self.assertFalse(any(t.is_alive() for t in threads), "deadlocked")
        self.assertEqual(
            ["actor call cycle: Account is waiting on this call", "ok"],
            sorted(results),
        )
        # Both actors were released.
        self.assertEqual(1, a.deposit(0) + b.deposit(0))

    def test_bubble_releases_the_actor(self) -> None:
        a = Account()
        a.deposit(3)
        with self.assertRaises(RuntimeError):
            a.withdraw(10)
        results: List[int] = []
        run_threads([lambda: results.append(a.withdraw(2))])
        self.assertEqual([1], results)

    def test_async_step_waits_for_host_turn(self) -> None:
        # A block launched during a turn awaits; a host thread then takes a
        # turn and settles the awaited promise inside it.  The resumed step
        # is a turn of the same actor, so it waits for the host's turn to end.
        a = Account()
        gate: Future[None] = Future()
        seen: List[str] = []
        a.later(gate, seen)
        for _ in range(200):
            if seen:
                break
            threading.Event().wait(0.01)
        self.assertEqual(["step 1 balance=0"], seen)

        def host_turn() -> None:
            with a._actor:
                gate.set_result(None)
                threading.Event().wait(0.2)
                a._balance = 100
                seen.append("host turn ends")

        run_threads([host_turn])
        for _ in range(200):
            if len(seen) == 3:
                break
            threading.Event().wait(0.01)
        self.assertEqual(
            ["step 1 balance=0", "host turn ends", "step 2 balance=100"], seen
        )

    def test_block_launched_by_plain_code_in_a_turn_is_bound(self) -> None:
        # A block launched during a turn by code that is not lexically
        # inside the actor still runs its steps as turns of that actor.
        a = Account()
        bound: List[Optional[ActorLock]] = []
        finished: Future[None] = Future()

        def block(do_await):  # type: ignore[no-untyped-def]
            bound.append(rt.current_actor())
            finished.set_result(None)
            if False:
                yield

        a.each([1], lambda x: rt.async_launch(adapt_generator_factory(block)))
        finished.result(TIMEOUT)
        self.assertEqual([a._actor], bound)
        self.assertIsNone(rt.current_actor())


if __name__ == "__main__":
    ut.main()
