import {
  dequeAdd,
  dequeConstructor,
  dequeGetIsEmpty,
  dequeRemoveFirst,
  modIntInt,
} from "@temperlang/core";

function checkEmpty() {
  console.log(dequeGetIsEmpty(queue) ? "Empty" : "Not empty");
}

/** @type {string[]} */
const queue = dequeConstructor();
checkEmpty();
if (!dequeGetIsEmpty(queue)) {
  console.log(dequeRemoveFirst(queue));
}
dequeAdd(queue, "foo");
dequeAdd(queue, "bar");
console.log(dequeRemoveFirst(queue));
dequeAdd(queue, "baz");
console.log(dequeRemoveFirst(queue));
dequeAdd(queue, "boo");
checkEmpty();
while (!dequeGetIsEmpty(queue)) {
  console.log(dequeRemoveFirst(queue));
}
checkEmpty();
/** @type {(number | null)[]} */
const numbers = dequeConstructor();
let sum = 0;
{
  let i = 0;
  while (i < 100) {
    dequeAdd(numbers, i);
    dequeAdd(numbers, null);
    if (modIntInt(i, 3) === 0) {
      sum = (sum + (dequeRemoveFirst(numbers) ?? -1000)) | 0;
      sum = (sum + (dequeRemoveFirst(numbers) ?? 1)) | 0;
    }
    i = (i + 1) | 0;
  }
}
let order = true;
let expected = 34;
while (!dequeGetIsEmpty(numbers)) {
  const n = dequeRemoveFirst(numbers);
  if (n != null) {
    if (n !== expected) {
      order = false;
    }
    expected = (expected + 1) | 0;
  }
}
console.log(`sum ${sum}, in order ${order}, through ${expected}`);
