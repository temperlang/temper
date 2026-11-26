import {
  InvalidCodePoint,
  stringBuilderAppendCodePoint,
  stringStep,
  type,
} from "@temperlang/core";

class StringBuilderOwner extends type() {
  /** @type {string[]} */
  #builder;

  /** @param {string[]} builder */
  constructor(builder) {
    super();
    this.#builder = builder;
  }

  /** @returns {string[]} */
  get builder() {
    return this.#builder;
  }
}

const builder = [""];
builder[0] += "Hi ";
stringBuilderAppendCodePoint(builder, 127759);
const source = "Wow!! Neato!";
const bangIndex = stringStep(source, 0, 3);
const spaceIndex = stringStep(source, bangIndex, 2);
builder[0] += source.substring(bangIndex, spaceIndex);
const built = builder[0];
builder[0] += "\n";
builder[0] += built;
console.log(builder[0]);
try {
  stringBuilderAppendCodePoint(builder, 1114112);
} catch (e$) {
  if (!(e$ instanceof InvalidCodePoint)) throw e$;
  console.log("High code blocked.");
}
try {
  stringBuilderAppendCodePoint(builder, 55296);
} catch (e) {
  if (!(e instanceof InvalidCodePoint)) throw e;
  console.log(`Surrogate 0x${e.codePoint.toString(16)} blocked.`);
}
console.log(builder[0]);
builder[0] = "";
builder[0] += "Back to square zero.";
console.log(builder[0]);
builder[0] = "";
builder[0] += "a??=b??/c??>d???e";
const marks = builder[0];
console.log(marks);
console.log(`Same: ${marks === "a??=b??/c??>d???e"}`);
builder[0] = "";
builder[0] += "??";
try {
  stringBuilderAppendCodePoint(builder, 1);
} catch (e$) {
  if (!(e$ instanceof InvalidCodePoint)) throw e$;
  console.log("No U+1.");
}
builder[0] += "0";
try {
  stringBuilderAppendCodePoint(builder, 8364);
} catch (e$) {
  if (!(e$ instanceof InvalidCodePoint)) throw e$;
  console.log("No euro.");
}
builder[0] += "1";
console.log(`Digits after escapes: ${builder[0] === "??\x010€1"}`);
const owner = new StringBuilderOwner([""]);
owner.builder[0] += "Hi from inside.";
console.log(owner.builder[0]);
