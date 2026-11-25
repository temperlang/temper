import {
  failureBase,
  listedGet,
  listedJoin,
  panic,
  withCause,
} from "@temperlang/core";

/**
 * @param {string} key
 * @returns {number}
 * @throws {NotFound}
 */
function lookup(key) {
  if (key === "a") {
    return 1;
  }
  if (key === "b") {
    return 2;
  }
  throw new NotFound(key);
}

/**
 * @param {string} a
 * @param {string} b
 * @returns {number}
 * @throws {NotFound}
 */
function sum(a, b) {
  const x = lookup(a);
  const y = lookup(b);
  return (x + y) | 0;
}

/**
 * @param {string} a
 * @param {string} b
 * @returns {string}
 */
function shown(a, b) {
  /** @type {number} */
  let total;
  /** @type {NotFound | null} */
  let caught = null;
  /** @type {number | null} */
  let held = null;
  try {
    held = sum(a, b);
  } catch (failure) {
    if (!(failure instanceof NotFound)) throw failure;
    caught = failure;
  }
  if (held == null) {
    const e = /** @type {NotFound} */ (caught);
    return `no ${e.key}`;
  } else {
    total = held;
  }
  return total.toString();
}

/**
 * @param {string} text
 * @returns {number}
 * @throws {BadSign}
 */
function sign(text) {
  {
    if (text === "+") {
      return 1;
    } else if (text === "-") {
      return -1;
    } else {
      throw new BadSign(text);
    }
  }
}

/**
 * @param {string} text
 * @returns {number}
 * @throws {SignError}
 */
function parseSign(text) {
  if (text === "") {
    throw new EmptyText();
  }
  return sign(text);
}

/**
 * @param {SignError} e
 * @returns {string}
 */
function describe(e) {
  {
    if (e instanceof EmptyText) {
      return "empty";
    } else if (e instanceof BadSign) {
      return `bad sign ${/** @type {BadSign} */ (e).text}`;
    } else {
      panic();
    }
  }
}

/**
 * @param {NotFound} p
 * @returns {number}
 * @throws {NotFound}
 */
function rethrown(p) {
  throw p;
}

/**
 * @param {string} name
 * @returns {number}
 * @throws {ConfigError}
 */
function setting(name) {
  try {
    return lookup(name);
  } catch (e) {
    if (!(e instanceof NotFound)) throw e;
    throw withCause(new ConfigError(`${name}: missing ${e.key}`), e);
  }
}

/**
 * @param {string} a
 * @param {string} b
 * @returns {string}
 */
function pair(a, b) {
  try {
    const x = lookup(a);
    /** @type {number} */
    let y;
    try {
      y = lookup(b);
    } catch (e$) {
      if (!(e$ instanceof NotFound)) throw e$;
      y = 0;
    }
    return `${x}+${y}`;
  } catch (e) {
    if (!(e instanceof NotFound)) throw e;
    return `outer ${e.key}`;
  }
}

class FailuresError extends Error {}

failureBase(FailuresError);

class NotFound extends FailuresError {
  /** @type {string} */
  #key;

  /** @param {string} key */
  constructor(key) {
    super();
    this.#key = key;
  }

  /** @returns {string} */
  get key() {
    return this.#key;
  }

  /** @returns {string} */
  get message() {
    return `not found (key: ${this.#key})`;
  }
}

class SignError extends Error {}

failureBase(SignError);

class EmptyText extends SignError {
  /** @returns {string} */
  get message() {
    return "empty text";
  }
}

class BadSign extends SignError {
  /** @type {string} */
  #text;

  /** @param {string} text */
  constructor(text) {
    super();
    this.#text = text;
  }

  /** @returns {string} */
  get text() {
    return this.#text;
  }

  /** @returns {string} */
  get message() {
    return `bad sign (text: ${this.#text})`;
  }
}

class ConfigError extends FailuresError {
  /** @type {string} */
  #setting;

  /** @param {string} setting */
  constructor(setting) {
    super();
    this.#setting = setting;
  }

  /** @returns {string} */
  get setting() {
    return this.#setting;
  }

  /** @returns {string} */
  get message() {
    return `config error (setting: ${this.#setting})`;
  }
}

/** @type {number} */
let found;
try {
  found = lookup("a");
} catch (e$) {
  if (!(e$ instanceof NotFound)) throw e$;
  found = -1;
}
/** @type {number} */
let missing;
try {
  missing = lookup("z");
} catch (e$) {
  if (!(e$ instanceof NotFound)) throw e$;
  missing = -1;
}
console.log(`${found} ${missing}`);
/** @type {number} */
let n;
try {
  n = lookup("q");
} catch (e) {
  if (!(e instanceof NotFound)) throw e;
  console.log(`not found: ${e.key}`);
  n = 0;
}
/** @type {number} */
let m;
try {
  m = lookup("b");
} catch (e$) {
  if (!(e$ instanceof NotFound)) throw e$;
  m = -1;
}
console.log(`${n} ${m}`);
console.log(`${shown("a", "b")} ${shown("a", "x")} ${shown("y", "z")}`);
{
  /** @type {readonly string[]} */
  const list = Object.freeze(["+", "-", "", "x"]);
  const length = list.length;
  let i = 0;
  while (i < length) {
    const t = listedGet(/** @type {string[]} */ (list), i);
    i = (i + 1) | 0;
    /** @type {number} */
    let s;
    /** @type {SignError | null} */
    let caught = null;
    /** @type {number | null} */
    let held = null;
    try {
      held = parseSign(t);
    } catch (failure) {
      if (!(failure instanceof SignError)) throw failure;
      caught = failure;
    }
    if (held == null) {
      const e2 = /** @type {SignError} */ (caught);
      console.log(describe(e2));
      continue;
    } else {
      s = held;
    }
    console.log(s.toString());
  }
}
/** @type {number} */
let code;
try {
  code = parseSign("?");
} catch (e3) {
  if (!(e3 instanceof SignError)) throw e3;
  code = e3 instanceof EmptyText ? 0 : e3 instanceof BadSign ? 9 : panic();
}
console.log(code.toString());
let kept = new NotFound("none");
/** @type {number} */
let v;
try {
  v = lookup("k");
} catch (e4) {
  if (!(e4 instanceof NotFound)) throw e4;
  kept = e4;
  v = -1;
}
/** @type {readonly NotFound[]} */
const problems = Object.freeze([new NotFound("x"), kept]);
console.log(`${v} ${listedJoin(/** @type {NotFound[]} */ (problems), ",", (p) => p.key)}`);
/** @type {number} */
let w;
try {
  w = rethrown(kept);
} catch (e5) {
  if (!(e5 instanceof NotFound)) throw e5;
  console.log(`again ${e5.key}`);
  w = 0;
}
/** @type {number} */
let port;
try {
  port = setting("port");
} catch (e6) {
  if (!(e6 instanceof ConfigError)) throw e6;
  console.log(e6.setting);
  port = -1;
}
/** @type {number} */
let first;
try {
  first = setting("a");
} catch (e$) {
  if (!(e$ instanceof ConfigError)) throw e$;
  first = -1;
}
console.log(`${port} ${first}`);
console.log(`${pair("a", "z")} ${pair("z", "a")}`);
let total = 0;
{
  /** @type {readonly string[]} */
  const list2 = Object.freeze(["a", "b", "c", "a"]);
  const length2 = list2.length;
  let i2 = 0;
  while (i2 < length2) {
    const k = listedGet(/** @type {string[]} */ (list2), i2);
    i2 = (i2 + 1) | 0;
    const t$1 = total;
    /** @type {number} */
    let t$0;
    {
      /** @type {number} */
      let t2;
      /** @type {number | null} */
      let held2 = null;
      try {
        held2 = lookup(k);
      } catch (e$) {
        if (!(e$ instanceof NotFound)) throw e$;
      }
      if (held2 == null) {
        break;
      } else {
        t2 = held2;
      }
      t$0 = t2;
    }
    total = (t$1 + t$0) | 0;
  }
}
console.log(total.toString());
