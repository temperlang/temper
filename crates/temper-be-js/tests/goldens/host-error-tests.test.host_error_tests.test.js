import { endTest } from "@temperlang/core";
import { it } from "node:test";
import {
  Oops,
  hostFails,
  hostFailsTest,
} from "../host_error_tests.internal.js";
import { Test } from "@temperlang/std/testing";

it("host errors are panics", async function () {
  const test = new Test();
  let failure;
  try {
    /** @throws {Error} */
    function fn() {
      /** @type {number} */
      let r;
      try {
        r = hostFails(1);
      } catch (e$) {
        if (!(e$ instanceof Oops)) throw e$;
        r = -1;
      }
      test.assert(r !== -1, () => "expected r != -1");
    }
    test.assertPanics(fn, "host error 1");
    /** @throws {Error} */
    function fn3() {
      /** @type {number} */
      let r;
      try {
        r = hostFails(2);
      } catch (e$) {
        if (!(e$ instanceof Oops)) throw e$;
        r = -1;
      }
      test.assert(r !== -1, () => "expected r != -1");
    }
    test.assertPanics(fn3, "host error 2");
    /** @throws {Error} */
    function fn4() {
      /** @type {number} */
      let r;
      try {
        r = hostFails(3);
      } catch (e$) {
        if (!(e$ instanceof Oops)) throw e$;
        r = -1;
      }
      test.assert(r !== -1, () => "expected r != -1");
    }
    test.assertPanics(fn4, "host error 3");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("host test failure is no panic", async function () {
  const test = new Test();
  let failure;
  try {
    /** @throws {Error} */
    function fn() {
      hostFailsTest();
    }
    test.assertPanics(fn);
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});
