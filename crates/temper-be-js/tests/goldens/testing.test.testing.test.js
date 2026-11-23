import { endTest } from "@temperlang/core";
import { it } from "node:test";
import { twice } from "../testing.internal.js";
import { Test } from "@temperlang/std/testing";

let runs = 0;
const base = 10;

it("twice works", async function () {
  const test = new Test();
  let failure;
  try {
    const x = twice(2);
    runs = (((runs + x) | 0) + base) | 0;
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("names: punctuation, 'quotes', and ünïcode", async function () {
  const t = new Test();
  let failure;
  try {
    twice(runs);
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(t, failure);
  }
});

it("locals a test assigns", async function () {
  const test = new Test();
  let failure;
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
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});
