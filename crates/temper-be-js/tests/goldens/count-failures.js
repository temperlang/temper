import { failureBase } from "@temperlang/core";

/**
 * @param {string} text
 * @returns {number}
 * @throws {BadCount}
 */
export function parseCount(text) {
  {
    if (text === "zero") {
      return 0;
    } else if (text === "two") {
      return 2;
    } else {
      throw new BadCount(text);
    }
  }
}

export class CountError extends Error {}

failureBase(CountError);

export class BadCount extends CountError {
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
    return `bad count (text: ${this.#text})`;
  }
}
