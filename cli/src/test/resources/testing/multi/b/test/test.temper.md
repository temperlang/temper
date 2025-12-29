Also include access to regex from multiple modules, since we had a naming bug in
this.

    let { Dot, Repeat } = import("std/regex");
    let { checkMin } = import("..");
    test("regex") {
      assert(checkMin(new Repeat(Dot, 1, null).min));
    }
