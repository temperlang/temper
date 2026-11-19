"""Host code using the generated `async_turns` package the standard way,
for `functional.rs`'s `runs`: it prints a line per check, which the test
compares with what it expects. mypy and pyright check it, strict, so a
bare `await` of a generated promise type-checks. It also passes
`py_builtin_params`'s constructor its parameters by name, which keep
their names though they're builtins' (`help=`, not `help_=`)."""

import asyncio
import threading
from collections.abc import Callable

import temper_core

temper_core.init_simple_logging()
with temper_core.top_level_turn():
    # Its top-level code logs.
    import async_turns  # noqa: F401  # pyright: ignore[reportUnusedImport]
temper_core.await_safe_to_exit()

from async_turns import collatz, fibonacci, later, ticks  # noqa: E402

# Outside the runner, blocking on a promise waits for it.
print("result", later(1).result(timeout=10))


# asyncio awaits a generated promise, bare: it's a `TemperFuture`.
async def awaited() -> int:
    value: int = await later(2)
    return value


print("asyncio", asyncio.run(awaited()))


# With the asyncio runner, steps run on the event loop's thread.
async def on_loop() -> None:
    threads: list[str] = []
    runner = temper_core.asyncio_runner()

    def run(step: Callable[[], None]) -> None:
        threads.append(threading.current_thread().name)
        runner(step)

    temper_core.set_async_runner(run)
    try:
        value = await later(3)
    finally:
        temper_core.set_async_runner(None)
    print("asyncio runner", value, sorted(set(threads)))


asyncio.run(on_loop())

# Blocking inside a turn, before the promise settles, raises.
try:
    with temper_core.top_level_turn():
        later(4).result(timeout=10)
except RuntimeError as e:
    print("in a turn:", "would deadlock" in str(e))

# Python's own loop steps a generator.
seen: list[int] = []
for _ in ticks(3, seen.append):
    pass
print("for", seen)


# A generator stepped again after a failed step is done.
def fail_at_one(i: int) -> None:
    if i == 1:
        raise ValueError("boom")


g = ticks(3, fail_at_one)
next(g)
try:
    next(g)
except ValueError:
    print("failed step")
print("then", next(g, "done"))


# Python's own loop takes a lazy generator's values, and stops early.
fib: list[int] = []
for n in fibonacci():
    if len(fib) == 8:
        break
    fib.append(n)
print("fibonacci", fib)
print("collatz", list(collatz(7)))

# A Temper failure is a `RuntimeError`, as notes/api-compat.md promises.
from async_turns import halve  # noqa: E402

try:
    halve(3)
    print("halve returned")
except RuntimeError:
    print("halve", halve(8), "caught")

# Parameters named like builtins keep their names, passed by name.
from py_builtin_params import Option  # noqa: E402

option = Option(help="how", min=1, max=9, type="flag")
print("by name", option.describe(), option.clamp(12))
