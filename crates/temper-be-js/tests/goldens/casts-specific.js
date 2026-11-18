import { isBubble, listedGet, requireInstanceOf, type } from "@temperlang/core";

/** @param {AlphaInterface} instance */
function checkB(instance) {
  /** @type {string} */
  let t$0;
  try {
    requireInstanceOf(instance, BravoClass);
    t$0 = "yep";
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    t$0 = "nope";
  }
  console.log(t$0);
}

/** @param {AlphaInterface} instance */
function checkIfB(instance) {
  console.log(instance instanceof BravoClass ? "yep" : "nope");
}

class AlphaInterface extends type() {}

class BravoClass extends type(AlphaInterface) {}

class CharlieClass extends type(AlphaInterface) {}

checkB(new BravoClass());
checkB(new CharlieClass());
/** @type {readonly AlphaInterface[]} */
const items = Object.freeze([new BravoClass(), new CharlieClass()]);
checkB(listedGet(/** @type {AlphaInterface[]} */ (items), 0));
checkB(listedGet(/** @type {AlphaInterface[]} */ (items), 1));
checkIfB(listedGet(/** @type {AlphaInterface[]} */ (items), 0));
checkIfB(listedGet(/** @type {AlphaInterface[]} */ (items), 1));
