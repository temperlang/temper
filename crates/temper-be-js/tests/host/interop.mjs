// Host code using the generated `async-turns` package the standard way,
// for `functional.rs`'s `runs`: it prints a line per check, which the
// test compares with what it expects. tsc checks it, strict, with the
// generated code. JS can't block, so there's no blocking check.

// Its top-level code logs, and starts its `async` blocks.
import { Odd, collatz, fibonacci, halve, later, ticks } from "./async-turns/index.js";

// `await` takes a generated promise; the module's blocks have run by then.
/** @type {number} */
const value = await later(1);
console.log(`await ${value}`);

// `for…of` steps a generator.
/** @type {number[]} */
const seen = [];
for (const _ of ticks(3, (i) => { seen.push(i); })) {
  // Each step reports through the callback.
}
console.log(`for…of ${seen.join(",")}`);

// A generator stepped again after a failed step is done.
const g = ticks(3, (i) => {
  if (i === 1) {
    throw new Error("boom");
  }
});
g.next();
try {
  g.next();
} catch {
  console.log("failed step");
}
console.log(`then done ${g.next().done}`);

// `for…of` takes a lazy generator's values, and stops early.
/** @type {number[]} */
const fib = [];
for (const n of fibonacci()) {
  if (fib.length === 8) {
    break;
  }
  fib.push(n);
}
console.log(`fibonacci ${fib.join(",")}`);
console.log(`collatz ${[...collatz(7)].join(",")}`);

// A declared failure is an `Error` of its own class.
try {
  console.log(`halve returned ${halve(3)}`);
} catch (e) {
  console.log(e instanceof Odd ? `halve ${halve(8)} caught` : "halve threw");
}
