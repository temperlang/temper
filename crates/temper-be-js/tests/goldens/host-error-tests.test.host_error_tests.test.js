import { finishAsyncWork, isBubble } from "@temperlang/core";
import { it } from "node:test";
import { hostFails } from "../host_error_tests.internal.js";
import { Test } from "@temperlang/std/testing";

it("host error is no panic", async function () {
  const test = new Test();
  try {
    function fn() {
      /** @type {number} */
      let r;
      try {
        r = hostFails(1);
      } catch (e$) {
        if (!isBubble(e$)) throw e$;
        r = -1;
      }
      test.assert(r !== -1, () => "caught");
    }
    test.assertPanics(fn);
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});
