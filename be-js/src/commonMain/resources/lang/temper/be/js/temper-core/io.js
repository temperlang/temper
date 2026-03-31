import { empty } from "./core.js";
import { createInterface } from "readline";

/**
 * @param {number} ms
 * @returns {Promise<Empty>}
 */
export function stdSleep(ms) {
  return new Promise(resolve => setTimeout(() => resolve(empty()), ms));
}

/**
 * @returns {Promise<string | null>}
 */
export function stdReadLine() {
  return new Promise(resolve => {
    if (typeof process !== 'undefined' && process.stdin) {
      const rl = createInterface({ input: process.stdin });
      rl.once('line', line => {
        rl.close();
        resolve(line);
      });
      rl.once('close', () => {
        resolve(null); // EOF
      });
    } else {
      resolve(null);
    }
  });
}
