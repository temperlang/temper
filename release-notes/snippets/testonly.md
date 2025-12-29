### Extract test-only support code

Temper now not only extracts tests from production code; it also extracts
test-only support code.

```temper
test("something") { (test);;
  assertLength(test, "word", 4);
}

let assertLength(test: Test, string: String, expectedLength: Int) {
  // The `assert` macro automatically associates with the `test` parameter.
  assert(string.length == expectedLength);
}
```

Here, `assertLength` is neither exported nor accessed (even transitively) by
exported code, so it also gets extracted from production code according to
backend-specific unit test expectations.
