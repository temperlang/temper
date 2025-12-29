In config, we specified imports for accessing remote sources. From ordinary
Temper files, we just import using the library name.

    let { ... } = import("std/regex");
    let { parse } = import("temper-regex-parser");

For now, just make a small test to verify our import.

    test("parse regex") {
      let regex = parse("a.*b");
      assert(do { regex as Sequence; true } orelse false);
    }
