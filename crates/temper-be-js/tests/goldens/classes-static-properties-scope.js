import { type } from "@temperlang/core";

/** @returns {string} */
function readSimpleValue() {
  return Simple.value;
}

/** @returns {string} */
function readSimpleOther() {
  return Simple.other;
}

/** @type {string} */
let Simple$value;
/** @type {string} */
let Simple$other;

class Simple extends type() {
  /** @returns {string} */
  static get value() {
    return Simple$value;
  }

  /** @returns {string} */
  static get other() {
    return Simple$other;
  }
}

Simple$value = "foo";
Simple$other = "bar";
console.log(`Simple.value is ${readSimpleValue()}`);
console.log(`Simple.other is ${readSimpleOther()}`);
