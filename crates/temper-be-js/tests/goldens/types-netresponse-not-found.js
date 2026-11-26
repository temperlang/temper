import {
  BrokenPromise,
  adaptAwaiter,
  netResponseGetBodyContent,
  netResponseGetStatus,
  runAsync,
} from "@temperlang/core";
import { NetRequest } from "@temperlang/std/net";

const url = `http://127.0.0.1:${28461}/missing`;

/**
 * @param {(p: Promise<unknown>) => void} doAwait
 * @returns {Generator<any>}
 */
function* fn(doAwait) {
  try {
    const resp = yield doAwait(new NetRequest(url).send());
    console.log(`status ${netResponseGetStatus(resp)}`);
    const body = (yield doAwait(netResponseGetBodyContent(resp))) ?? "missing";
    console.log(`body ${body}`);
  } catch (e$) {
    if (!(e$ instanceof BrokenPromise)) throw e$;
    console.log("broken promise");
  }
  try {
    yield doAwait(new NetRequest("http://127.0.0.1:1/").send());
    console.log("port 1 answered");
  } catch (e) {
    if (!(e instanceof BrokenPromise)) throw e;
    console.log(`no response from port 1: ${/** @type {Error} */ (/** @type {unknown} */ (e)).message}`);
  }
}

runAsync(adaptAwaiter(fn));
