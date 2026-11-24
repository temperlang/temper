import { listBuilderSet, listedJoin } from "@temperlang/core";

/**
 * @param {number[]} builder
 * @param {number} i
 * @param {number} value
 */
function put(builder, i, value) {
  listBuilderSet(builder, i, value);
}

/** @type {number[]} */
const builder = [];
builder.push(1);
builder.push(2);
put(builder, 1, 5);
console.log(listedJoin(builder, ", ", (n) => n.toString()));
console.log("before");
put(builder, -1, 0);
console.log("not reached");
