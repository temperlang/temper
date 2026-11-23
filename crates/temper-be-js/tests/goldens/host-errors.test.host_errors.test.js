import {
  bubble,
  dequeConstructor,
  dequeRemoveFirst,
  endTest,
  isBubble,
  listBuilderAdd,
  listBuilderRemoveLast,
  listBuilderSet,
  listedGet,
  listedReduce,
  stringGet,
} from "@temperlang/core";
import { it } from "node:test";
import { CodePoints, Match, RegexNode } from "@temperlang/std/regex";
import { Test } from "@temperlang/std/testing";

/**
 * @param {number} i
 * @returns {number}
 */
function at(i) {
  if (i === 99) {
    bubble();
  }
  return i;
}

/** @returns {number[]} */
function built() {
  /** @type {number[]} */
  const lb = [];
  lb.push(1);
  return lb;
}

/**
 * @param {readonly number[]} ls
 * @param {number} i
 * @returns {number}
 */
function read(ls, i) {
  return listedGet(/** @type {number[]} */ (ls), at(i));
}

/**
 * @param {number[]} lb
 * @param {number} i
 * @returns {number}
 */
function write(lb, i) {
  listBuilderSet(lb, at(i), 3);
  return lb.length;
}

/**
 * @param {number[]} lb
 * @param {number} i
 * @returns {number}
 */
function insert(lb, i) {
  listBuilderAdd(lb, 2, at(i));
  return lb.length;
}

/**
 * @param {number[]} lb
 * @param {number} i
 * @returns {number}
 */
function removeLast(lb, i) {
  at(i);
  return listBuilderRemoveLast(lb);
}

/**
 * @param {number[]} d
 * @param {number} i
 * @returns {number}
 */
function removeFirst(d, i) {
  at(i);
  return dequeRemoveFirst(d);
}

/**
 * @param {readonly number[]} ls
 * @param {number} i
 * @returns {number}
 */
function sum(ls, i) {
  at(i);
  return listedReduce(/** @type {number[]} */ (ls), (a, b) => (a + b) | 0);
}

/**
 * @param {string} s
 * @param {number} i
 * @returns {number}
 */
function codeAt(s, i) {
  at(i);
  return stringGet(s, s.length);
}

/**
 * @param {number} i
 * @returns {string}
 */
function replaced(i) {
  at(i);
  return /** @type {RegexNode} */ (/** @type {unknown} */ (new CodePoints("b"))).compiled().replace("abc", (match) => listedGet(/** @type {string[]} */ (Object.freeze(["x"])), i));
}

it("list read", async function () {
  const test = new Test();
  let failure;
  try {
    /** @type {number} */
    let r;
    try {
      r = read(Object.freeze([1, 2]), 5);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
      r = -1;
    }
    test.assert(r !== -1, () => "caught");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("list read at the end", async function () {
  const test = new Test();
  let failure;
  try {
    /** @type {readonly number[]} */
    const ls = Object.freeze([1, 2]);
    /** @type {number} */
    let r;
    try {
      r = read(ls, ls.length);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
      r = -1;
    }
    test.assert(r !== -1, () => "caught");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("negative index", async function () {
  const test = new Test();
  let failure;
  try {
    /** @type {number} */
    let r;
    try {
      r = read(built(), -1);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
      r = -1;
    }
    test.assert(r !== -1, () => "caught");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("set", async function () {
  const test = new Test();
  let failure;
  try {
    /** @type {number} */
    let r;
    try {
      r = write(built(), 5);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
      r = -1;
    }
    test.assert(r !== -1, () => "caught");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("set at the end", async function () {
  const test = new Test();
  let failure;
  try {
    /** @type {number[]} */
    const lb = built();
    /** @type {number} */
    let r;
    try {
      r = write(lb, lb.length);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
      r = -1;
    }
    test.assert(r !== -1, () => "caught");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("insert", async function () {
  const test = new Test();
  let failure;
  try {
    /** @type {number} */
    let r;
    try {
      r = insert(built(), 5);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
      r = -1;
    }
    test.assert(r !== -1, () => "caught");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("insert past the end", async function () {
  const test = new Test();
  let failure;
  try {
    /** @type {number[]} */
    const lb = built();
    /** @type {number} */
    let r;
    try {
      r = insert(lb, (lb.length + 1) | 0);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
      r = -1;
    }
    test.assert(r !== -1, () => "caught");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("insert at the end appends", async function () {
  const test = new Test();
  let failure;
  try {
    /** @type {number[]} */
    const lb = built();
    /** @type {number} */
    let n;
    try {
      n = insert(lb, lb.length);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
      n = -1;
    }
    test.assert(n === 2 && listedGet(lb, 1) === 2, () => "didn't append");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("remove last", async function () {
  const test = new Test();
  let failure;
  try {
    /** @type {number} */
    let r;
    try {
      r = removeLast([], 0);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
      r = -1;
    }
    test.assert(r !== -1, () => "caught");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("remove first", async function () {
  const test = new Test();
  let failure;
  try {
    /** @type {number} */
    let r;
    try {
      r = removeFirst(dequeConstructor(), 0);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
      r = -1;
    }
    test.assert(r !== -1, () => "caught");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("reduce", async function () {
  const test = new Test();
  let failure;
  try {
    /** @type {number} */
    let r;
    try {
      r = sum(Object.freeze([]), 0);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
      r = -1;
    }
    test.assert(r !== -1, () => "caught");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("string read", async function () {
  const test = new Test();
  let failure;
  try {
    /** @type {number} */
    let r;
    try {
      r = codeAt("ab", 0);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
      r = -1;
    }
    test.assert(r !== -1, () => "caught");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("replace callback", async function () {
  const test = new Test();
  let failure;
  try {
    /** @type {string} */
    let r;
    try {
      r = replaced(3);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
      r = "";
    }
    test.assert(r !== "", () => "caught");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});
