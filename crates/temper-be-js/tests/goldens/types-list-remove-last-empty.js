import { listBuilderRemoveLast } from "@temperlang/core";

/** @type {string[]} */
const builder = [];
builder.push("a");
console.log(listBuilderRemoveLast(builder));
console.log("before");
console.log(listBuilderRemoveLast(builder));
console.log("not reached");
