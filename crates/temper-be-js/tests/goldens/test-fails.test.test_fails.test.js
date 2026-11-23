import { endTest, isBubble } from "@temperlang/core";
import { it } from "node:test";
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

it("passes before", async function () {
  const test = new Test();
  let failure;
  try {
    function fn() {
      {
        const actual = find("one");
        const expected = 1;
        test.assert(actual === expected, () => `expected find("one") == (${expected}) not (${actual == null ? "null" : actual.toString()})`);
      }
    }
    test.assert(tries(fn), () => 'expected tries { assert(find("one") == 1); }');
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("fails past an orelse", async function () {
  const test = new Test();
  let failure;
  try {
    function fn() {
      test.fail("failed on purpose");
    }
    tries(fn);
    test.fail("swallowed");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("narrows after fail", async function () {
  const test = new Test();
  let failure;
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
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("fails before narrowing", async function () {
  const test = new Test();
  let failure;
  try {
    const n = find("two");
    if (n == null) {
      test.fail("two not found");
    }
    test.assert(((n + 1) | 0) === 3, () => "went on");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("collects soft failures", async function () {
  const test = new Test();
  let failure;
  try {
    test.assert(((1 + 1) | 0) === 3, () => "first");
    test.assert(true, () => "not shown");
    test.assert(((2 + 2) | 0) === 5, () => "second");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("fails hard after soft failures", async function () {
  const test = new Test();
  let failure;
  try {
    test.assert(false, () => "soft before");
    test.fail("then hard");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("fails a hard assertion past an orelse", async function () {
  const test = new Test();
  let failure;
  try {
    function fn() {
      test.assertHard(((1 + 1) | 0) === 3, () => "hard assertion");
    }
    tries(fn);
    test.fail("swallowed");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("hardens soft failures past an orelse", async function () {
  const test = new Test();
  let failure;
  try {
    test.assert(false, () => "soft only");
    function fn2() {
      test.softFailToHard();
    }
    tries(fn2);
    test.fail("swallowed");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("passes after", async function () {
  const test = new Test();
  let failure;
  try {
    {
      const actual = find("one");
      const expected = 1;
      test.assert(actual === expected, () => `expected find("one") == (${expected}) not (${actual == null ? "null" : actual.toString()})`);
    }
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});
