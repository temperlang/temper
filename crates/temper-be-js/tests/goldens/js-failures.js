import {
  divIntInt,
  failureBase,
  failureCause,
  isBubble,
  panic,
  withCause,
} from "@temperlang/core";

/**
 * @param {string} key
 * @returns {number}
 * @throws {NotFound}
 */
export function lookup(key) {
  if (key === "a") {
    return 1;
  }
  throw new NotFound(key);
}

/**
 * @param {string} text
 * @returns {number}
 * @throws {SignError}
 */
export function sign(text) {
  {
    if (text === "+") {
      return 1;
    } else if (text === "-") {
      return -1;
    } else if (text === "") {
      throw new EmptyText();
    } else {
      throw new BadSign(text);
    }
  }
}

/**
 * @param {string} name
 * @returns {number}
 * @throws {LookupFailed}
 */
export function setting(name) {
  try {
    return lookup(name);
  } catch (failure) {
    if (!(failure instanceof NotFound)) throw failure;
    throw withCause(new LookupFailed(name), failure);
  }
}

/**
 * @param {string} name
 * @returns {number}
 * @throws {LookupFailed}
 */
export function madeBefore(name) {
  const failure = new LookupFailed(name);
  try {
    return lookup(name);
  } catch (failure2) {
    if (!(failure2 instanceof NotFound)) throw failure2;
    throw withCause(failure, failure2);
  }
}

/**
 * @param {string} name
 * @returns {number}
 * @throws {LookupFailed}
 */
export function thrownAgain(name) {
  try {
    return setting(name);
  } catch (e) {
    if (!(e instanceof LookupFailed)) throw e;
    /** @type {number} */
    let t$0;
    try {
      t$0 = lookup("q");
    } catch (failure) {
      if (!(failure instanceof NotFound)) throw failure;
      throw withCause(e, failure);
    }
    return t$0;
  }
}

/**
 * @param {number} n
 * @returns {number}
 * @throws {NotFound}
 */
export function risky(n) {
  if (n > 1) {
    panic();
  }
  return lookup("a");
}

/**
 * @param {string} name
 * @returns {string}
 */
export function explained(name) {
  try {
    return setting(name).toString();
  } catch (e) {
    if (!(e instanceof LookupFailed)) throw e;
    const cause = failureCause(e);
    const why = cause != null ? cause.message : "nothing";
    return `${e.message} for ${e.name_}, from ${why}`;
  }
}

/**
 * @param {string} text
 * @returns {string}
 */
export function signMessage(text) {
  try {
    return sign(text).toString();
  } catch (e) {
    if (!(e instanceof SignError)) throw e;
    return e.message;
  }
}

/**
 * @param {SignError} e
 * @returns {string}
 */
export function signName(e) {
  {
    if (e instanceof BadSign) {
      return "bad sign";
    } else if (e instanceof EmptyText) {
      return "empty text";
    } else {
      panic();
    }
  }
}

/**
 * @param {string} key
 * @param {number} n
 * @returns {number}
 */
export function mixed(key, n) {
  try {
    return (lookup(key) + divIntInt(10, n)) | 0;
  } catch (e$) {
    if (!(isBubble(e$) || e$ instanceof NotFound)) throw e$;
    return -1;
  }
}

export class JsFailuresError extends Error {}

failureBase(JsFailuresError);

export class NotFound extends JsFailuresError {
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

export class SignError extends JsFailuresError {}

export class BadSign extends SignError {
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

export class EmptyText extends SignError {
  /** @returns {string} */
  get message() {
    return "no text";
  }
}

export class LookupFailed extends JsFailuresError {
  /** @type {string} */
  #name_;

  /** @param {string} name */
  constructor(name) {
    super();
    this.#name_ = name;
  }

  /** @returns {string} */
  get name_() {
    return this.#name_;
  }

  /** @returns {string} */
  get message() {
    return `lookup failed (name: ${this.#name_})`;
  }
}

console.log(`${lookup("a")} ${sign("-")} ${setting("a")}`);
console.log(`${mixed("a", 0)} ${mixed("z", 1)} ${mixed("a", 5)}`);
console.log(`${explained("port")}; ${explained("a")}; ${signMessage("x")}`);
