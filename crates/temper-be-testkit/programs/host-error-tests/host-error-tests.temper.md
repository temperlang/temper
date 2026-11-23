# Host error tests

`test.assertPanics` passes on a panic, which is any failure but a bubble
or a test's failure, on every backend, so a host's own error passes it,
as it passes every `orelse`. Here connected functions' host code throws
errors of several kinds (each backend's own, such as a thrown error of
its runtime's and one from a bad index or a null), and fails the way
its test framework does; the interpreter, which has no host, runs the
Temper bodies, which panic:

    @connected
    export let hostFails(n: Int): Int throws Bubble {
      if (n > 0) { panic() }
      bubble()
    }

    @connected
    export let hostFailsTest(): Void { panic() }

    test("host errors are panics") { test =>
      test.assertPanics("host error 1") { let r = hostFails(1) orelse -1; assert(r != -1); };
      test.assertPanics("host error 2") { let r = hostFails(2) orelse -1; assert(r != -1); };
      test.assertPanics("host error 3") { let r = hostFails(3) orelse -1; assert(r != -1); };
    }

A failure of the host's test framework, as a host's own assertion
throws, isn't a panic: it fails the test, saying what it said. Rust
alone can't tell one from a panic, as both are panics there.

    test("host test failure is no panic") { test =>
      test.assertPanics { hostFailsTest(); };
    }

The failing test, with what its failure says:

```failures ~interp ~rust
host test failure is no panic: host test failure
```
