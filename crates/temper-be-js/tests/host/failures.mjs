// Host code using the generated `failures-interop` package's declared
// failures the standard way, for `functional.rs`'s `runs`: it catches
// them with `instanceof` by their own types, the library's root, a sealed
// interface's abstract class, and `Error`, reads their fields, `message`,
// `name`, `stack`, and `cause`, and lets a panic pass a `catch` that
// checks the type. It prints a line per check, which the test compares
// with what it expects. tsc checks it, strict, with the generated code.

// Its top-level code logs.
import {
  BadSign,
  FailuresInteropError,
  LookupFailed,
  NotFound,
  SignError,
  lookup,
  madeBefore,
  risky,
  setting,
  sign,
  signName,
  thrownAgain,
} from "./failures-interop/index.js";

try {
  lookup("z");
} catch (e) {
  if (!(e instanceof NotFound)) throw e;
  console.log(`NotFound ${e.key}: ${e.message}`);
}

try {
  lookup("y");
} catch (e) {
  if (!(e instanceof FailuresInteropError)) throw e;
  console.log(
    `base ${e instanceof NotFound}, Error ${e instanceof Error}, name ${e.name}, ${String(e)}`,
  );
}

for (const text of ["x", ""]) {
  try {
    sign(text);
  } catch (e) {
    if (!(e instanceof SignError)) throw e;
    console.log(`sign ${e.name}: ${e.message}`);
    if (e instanceof BadSign && e.text !== "x") {
      throw new Error("text");
    }
  }
}

try {
  setting("port");
} catch (e) {
  if (!(e instanceof LookupFailed)) throw e;
  const cause = e.cause;
  if (!(cause instanceof Error)) throw new Error("no cause");
  console.log(`LookupFailed ${e.name_} ${e.message}, caused by ${cause.name} ${cause.message}`);
}

try {
  madeBefore("early");
} catch (e) {
  if (!(e instanceof LookupFailed) || !(e.cause instanceof Error)) throw e;
  console.log(`made before ${e.message}, caused by ${e.cause.message}`);
}

try {
  thrownAgain("first");
} catch (e) {
  if (!(e instanceof LookupFailed) || !(e.cause instanceof Error)) throw e;
  console.log(`thrown again ${e.message}, caused by ${e.cause.message}`);
}

// A failure has its stack, from where it was made, headed by its name
// and message.
try {
  lookup("w");
} catch (e) {
  if (!(e instanceof Error)) throw e;
  const stack = e.stack ?? "";
  const top = stack.split("\n").find((l) => l.trim().startsWith("at ")) ?? "";
  console.log(`stack ${stack.split("\n")[0]}, at ${top.trim().split(" ")[1]}`);
}

try {
  try {
    risky(2);
  } catch (e) {
    if (!(e instanceof FailuresInteropError)) throw e;
    console.log("panic caught");
  }
} catch (e) {
  console.log(`panic passes, ${e instanceof FailuresInteropError}`);
}

// A type of host code's own under a sealed one, which Temper's exhaustive
// `when` over it doesn't know, panics there.
class Rogue extends SignError {}

console.log(`known subtype ${signName(new BadSign("?"))}`);
try {
  signName(new Rogue());
  console.log("unknown subtype passes");
} catch (e) {
  const failure = e instanceof FailuresInteropError;
  console.log(`unknown subtype panics, ${failure}, ${e instanceof Error ? e.message : e}`);
}
