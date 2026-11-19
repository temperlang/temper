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

A test that panics (a list read past the end, `panic()`) fails alone,
with the panic's message, and the other tests still run: the runner
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
| `Test.softFailToHard()` | Ends the test, as `fail` does, if it has failures so far |
| `Test.passing`, `messages()` | Whether it passes so far, and its failure messages |
| `runTest(f)`, `runTestCases(cases)`, `processTestCases(cases)`, `reportTestResults(…)` | How backends and the interpreter run a module's tests |

Source: [testing.temper.md](../../lib/std/testing/testing.temper.md).
