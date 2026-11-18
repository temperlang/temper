import { twice } from "../testing.internal.js";
import { Test } from "@temperlang/std/testing";

let runs = 0;
const base = 10;

it("twice works", function () {
  const test = new Test();
  try {
    const x = twice(2);
    runs = (((runs + x) | 0) + base) | 0;
  } finally {
    test.softFailToHard();
  }
});

it("names: punctuation, 'quotes', and ünïcode", function () {
  const t = new Test();
  try {
    twice(runs);
  } finally {
    t.softFailToHard();
  }
});

it("locals a test assigns", function () {
  const test = new Test();
  try {
    let n = 0;
    {
      let i = 0;
      while (i < 3) {
        n = (n + twice(i)) | 0;
        i = (i + 1) | 0;
      }
    }
    runs = n;
  } finally {
    test.softFailToHard();
  }
});
