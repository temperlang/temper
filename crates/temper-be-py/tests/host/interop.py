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

# A declared failure is an exception of its own class.
from async_turns import Odd, halve  # noqa: E402

try:
    halve(3)
    print("halve returned")
except Odd:
    print("halve", halve(8), "caught")

# Parameters named like builtins keep their names, passed by name.
from py_builtin_params import Option  # noqa: E402

option = Option(help="how", min=1, max=9, type="flag")
print("by name", option.describe(), option.clamp(12))

# Declared failures are exceptions under the library's root, caught by
# their own types, the root, or a sealed interface's.
from failures_interop import (  # noqa: E402
    BadSign,
    FailuresInteropError,
    LookupFailed,
    NotFound,
    SignError,
    lookup,
    made_before,
    risky,
    setting,
    sign,
    sign_name,
    thrown_again,
)

try:
    lookup("z")
except NotFound as e:
    print(f"NotFound {e.key}: {e}")


def raised(call: Callable[[], object]) -> BaseException | None:
    try:
        call()
    except BaseException as e:
        return e
    return None


failed = raised(lambda: lookup("y"))
print(f"base {isinstance(failed, FailuresInteropError)}, Exception {isinstance(failed, Exception)}")

for text in ["x", ""]:
    try:
        sign(text)
    except SignError as e:
        print(f"sign {type(e).__name__}: {e}")
        if isinstance(e, BadSign):
            assert e.text == "x"

try:
    setting("port")
except LookupFailed as e:
    cause = e.__cause__
    assert isinstance(cause, NotFound)
    print(f"LookupFailed {e}, caused by {type(cause).__name__} {cause}")

try:
    made_before("early")
except LookupFailed as e:
    print(f"made before {e}, caused by {e.__cause__}")

try:
    thrown_again("first")
except LookupFailed as e:
    print(f"thrown again {e}, caused by {e.__cause__}")

try:
    try:
        risky(2)
    except Exception:
        print("panic caught")
except BaseException:
    print("panic passes")


# A type of host code's own under a sealed one, which Temper's exhaustive
# `when` over it doesn't know, panics there.
class Rogue(SignError):
    __slots__ = ()

    @property
    def message(self) -> str:
        return "rogue"


print(f"known subtype {sign_name(BadSign('?'))}")
try:
    try:
        sign_name(Rogue())
    except Exception:
        print("unknown subtype caught")
except BaseException as e:
    print(f"unknown subtype panics: {e}")
