    export let bad = ["bad"][0]; // any inlining across libs?

This is in a separate module in the same test-me library, so we can test how
names are managed in such cases.

    let { Dot, Repeat } = import("std/regex");
    export let checkMin(i: Int): Boolean {

Use both typed fields and constructor calls to replicate issues.

      let repeat: Repeat = new Repeat(Dot, 1, null);
      repeat.min == i
    }

This checks that we can run tests for multiple libraries at one go. And from
different modules in one library also.

    test("something") {
      assert(true) { "surprising" }
    }

