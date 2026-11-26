import { dequeAdd, dequeConstructor, dequeRemoveFirst } from "@temperlang/core";

/** @type {string[]} */
const queue = dequeConstructor();
dequeAdd(queue, "a");
console.log(dequeRemoveFirst(queue));
console.log("before");
console.log(dequeRemoveFirst(queue));
console.log("not reached");
