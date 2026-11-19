import { isBubble } from "@temperlang/core";
import { Test } from "@temperlang/std/testing";

/**
 * @param {() => void} f
 * @returns {boolean}
 */
function tries(f) {
  let finished = true;
  try {
    f();
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    finished = false;
  }
  return finished;
}

/**
 * @param {string} key
 * @returns {number | null}
 */
function find(key) {
  if (key === "one") {
    return 1;
  } else {
    return null;
  }
}

it("passes before", function () {
  const test = new Test();
  try {
    function fn() {
      {
        const actual = find("one");
        const expected = 1;
        test.assert(actual === expected, () => `expected find("one") == (${expected}) not (${actual == null ? "null" : actual.toString()})`);
      }
    }
    test.assert(tries(fn), () => 'expected tries { assert(find("one") == 1); }');
  } finally {
    test.softFailToHard();
  }
});

it("fails past an orelse", function () {
  const test = new Test();
  try {
    function fn() {
      test.fail("failed on purpose");
    }
    tries(fn);
    test.fail("swallowed");
  } finally {
    test.softFailToHard();
  }
});

it("narrows after fail", function () {
  const test = new Test();
  try {
    const n = find("one");
    if (n == null) {
      test.fail("one not found");
    }
    {
      const actual = (n + 1) | 0;
      const expected = 2;
      test.assert(actual === expected, () => `expected n + 1 == (${expected}) not (${actual})`);
    }
  } finally {
    test.softFailToHard();
  }
});

it("fails before narrowing", function () {
  const test = new Test();
  try {
    const n = find("two");
    if (n == null) {
      test.fail("two not found");
    }
    test.assert(((n + 1) | 0) === 3, () => "went on");
  } finally {
    test.softFailToHard();
  }
});

it("collects soft failures", function () {
  const test = new Test();
  try {
    test.assert(((1 + 1) | 0) === 3, () => "first");
    test.assert(true, () => "not shown");
    test.assert(((2 + 2) | 0) === 5, () => "second");
  } finally {
    test.softFailToHard();
  }
});

it("fails hard after soft failures", function () {
  const test = new Test();
  try {
    test.assert(false, () => "soft before");
    test.fail("then hard");
  } finally {
    test.softFailToHard();
  }
});

it("fails a hard assertion past an orelse", function () {
  const test = new Test();
  try {
    function fn() {
      test.assertHard(((1 + 1) | 0) === 3, () => "hard assertion");
    }
    tries(fn);
    test.fail("swallowed");
  } finally {
    test.softFailToHard();
  }
});

it("hardens soft failures past an orelse", function () {
  const test = new Test();
  try {
    test.assert(false, () => "soft only");
    function fn2() {
      test.softFailToHard();
    }
    tries(fn2);
    test.fail("swallowed");
  } finally {
    test.softFailToHard();
  }
});

it("passes after", function () {
  const test = new Test();
  try {
    {
      const actual = find("one");
      const expected = 1;
      test.assert(actual === expected, () => `expected find("one") == (${expected}) not (${actual == null ? "null" : actual.toString()})`);
    }
  } finally {
    test.softFailToHard();
  }
});
