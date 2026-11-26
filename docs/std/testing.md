# `std/testing`: tests

`test` blocks run on `std/testing`, and you rarely import it yourself:

```temper
test("addition") {
  assert(1 + 1 == 2);                         // a default message on failure
  assert(2 * 2 == 4) { "multiplication too" } // or your own
}
// ✅
```

Assertions are *soft*: a failed `assert` records its message and the
test goes on, so one run reports every failure. To stop a test, name
its `Test` and call `fail`, which records its message and ends the test
as a failure. No `orelse` catches it, not even one in the code under
test, and since `fail` never returns, a null check that leaves through
it narrows what follows:

```temper
test("lookup") { test =>
  let n = find("one");
  if (n == null) { test.fail("one not found") }
  assert(n + 1 == 2);                         // `n` is an `Int` here
}
```

A test may throw any error, unhandled, as a call that throws a declared
failure does (`s.toInt32()`, `new Date(y, m, d)`): the error fails the
test, saying its message.

To check that something panics, put it in an `assertPanics` block,
which passes if the block panics. If the block returns, the test fails,
saying "expected a panic", or the message given before the block; an
error is no panic, so if the block throws one, the test fails too,
saying "but it threw". Like `assert`, it lets the test go on:

```temper
test("reads past the end") { test =>
  let ls = [1, 2];
  test.assertPanics { ls[5]; };
  test.assertPanics("index 2 is past the end") { ls[2]; };
}
```

Only a failure in the block is caught, and a panic there is any failure
but an error or a test's failure, the same on every backend, so a host's
own error, as connected code throws, counts too. A test's failure, a
`fail` in the block or a host test framework's failed assertion (JUnit's,
unittest's, Node's `assert`, …), still ends the test; on Rust, where a
failed assertion is a panic, a host's `assert!` counts as a panic. It's
for tests alone: what the block changed before it panicked
stays changed, maybe half done. Every backend and the interpreter catch
the panic in the test's own process.

A test ends only once the async work it started has run as far as it
can: no `async` block is ready to run and no I/O is in flight. So an
`assert` in an `async` block counts, even one after an `await`, and a
`fail` or a panic there fails the test that started the block, not the
next one. I/O that never ends keeps its test waiting, until `temper
test`'s `--timeout` (600 seconds by default) ends the run, saying so. A
block still awaiting a promise that nothing settles is left waiting, as
at a program's end:

```temper
test("later") {
  let pb = new PromiseBuilder<Int>();
  async { (): GeneratorResult<Empty> extends GeneratorFn =>
    let n = await pb.promise orelse -1;
    assert(n == 2);                           // checked before the test ends
  }
  pb.complete(2);
}
```

A test that panics (a list read past the end, `panic()`) fails alone,
with the panic's message, even after failed `assert`s or failing async
work, which are said after it where the runner shows more, and the
other tests still run: the runner
catches it, test by test, though no `orelse` does, nor `runTest` and
`processTestCases`. Each backend runs tests with its own framework (see [target languages](../backends/README.md)),
and `temper test` runs them on any backend or on the interpreter
([the command line](../tools/cli.md)).

| Member | What it does |
|---|---|
| `Test` | A running test: what `assert` reports to |
| `Test.assert(ok, message)` | Records a failure, with `message()`, unless `ok` |
| `Test.fail(message)` | Records `message` and ends the test as a failure, which no `orelse` catches; it never returns |
| `Test.assertHard(ok, message)` | Records a failure and ends the test, as `fail` does, unless `ok` |
| `Test.assertPanics(message) { … }` | Records a failure, with `message` (by default "expected a panic"), unless the block panics |
| `Test.softFailToHard()` | Ends the test, as `fail` does, if it has failures so far |
| `Test.passing`, `messages()` | Whether it passes so far, and its failure messages |
| `runTest(f)`, `runTestCases(cases)`, `processTestCases(cases)`, `reportTestResults(…)` | How backends and the interpreter run a module's tests |

Source: [testing.temper.md](../../lib/std/testing/testing.temper.md).
