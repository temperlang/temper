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
test goes on, so one run reports every failure. Each backend runs tests
with its own framework (see [target languages](../backends/README.md)),
and `temper test` runs them on any backend or on the interpreter
([the command line](../tools/cli.md)).

| Member | What it does |
|---|---|
| `Test` | A running test: what `assert` reports to |
| `Test.assert(ok, message)` | Records a failure, with `message()`, unless `ok` |
| `Test.assertHard(ok, message)` | The same, but stops the test |
| `Test.passing`, `messages()` | Whether it passes so far, and its failure messages |
| `runTest(f)`, `runTestCases(cases)`, `processTestCases(cases)`, `reportTestResults(…)` | How backends and the interpreter run a module's tests |

Source: [testing.temper.md](../../lib/std/testing/testing.temper.md).
