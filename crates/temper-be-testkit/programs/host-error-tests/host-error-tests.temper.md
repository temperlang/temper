# Host error tests

`test.assertPanics` passes only on a panic: a host's own error, which
isn't one, passes it, as it passes every `orelse`, and fails the test,
saying what it said. Here a connected function's host code throws one;
on Rust and Lua, where a host's error is a panic as any error but a
bubble is, and on the interpreter, which has no host and runs the
Temper body, which panics, the test passes:

    @connected
    export let hostFails(n: Int): Int throws Bubble {
      if (n > 0) { panic() }
      bubble()
    }

    test("host error is no panic") { test =>
      test.assertPanics {
        let r = hostFails(1) orelse -1;
        assert(r != -1) { "caught" };
      };
    }

The failing test, with what its failure says:

```failures ~interp ~lua ~rust
host error is no panic: host bug 1
```
