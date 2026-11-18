import { wheels } from "./parts/regex.js";
import { Engine, Parts, State, firstId } from "./state.js";
import { Dot, RegexNode, oneOrMore } from "@temperlang/std/regex";

/** @returns {string} */
export function describe() {
  const state = new State(firstId);
  const engine = new Engine(wheels());
  const least = oneOrMore(/** @type {RegexNode} */ (/** @type {unknown} */ (Dot))).min;
  const parts = new Parts((engine.n + least) | 0);
  return `state ${state.id} engine ${engine.n} parts ${parts.count}`;
}
