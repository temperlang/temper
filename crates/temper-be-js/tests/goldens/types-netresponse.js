import {
  adaptAwaiter,
  isBubble,
  netResponseGetBodyContent,
  netResponseGetStatus,
  runAsync,
} from "@temperlang/core";
import { NetRequest } from "@temperlang/std/net";

const url = `http://127.0.0.1:${28461}`;

/**
 * @param {(p: Promise<unknown>) => void} doAwait
 * @returns {Generator<any>}
 */
function* fn(doAwait) {
  try {
    const req = new NetRequest(url);
    req.post("[]", "application/json");
    const resp = yield doAwait(req.send());
    if (netResponseGetStatus(resp) === 200) {
      const body = (yield doAwait(netResponseGetBodyContent(resp))) ?? "missing";
      console.log(`Thanks, server.  I got ${body}.`);
    } else {
      console.log(`HTTP status was not 200: ${netResponseGetStatus(resp)}!`);
    }
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    console.log("failed");
  }
}

runAsync(adaptAwaiter(fn));
