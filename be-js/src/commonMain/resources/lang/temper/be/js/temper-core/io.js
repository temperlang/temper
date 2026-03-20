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
/** @returns {number} */
export function stdTermCols() {
  if (typeof process !== 'undefined' && process.stdout && process.stdout.columns) {
    return process.stdout.columns;
  }
  return 80;
}

/** @returns {number} */
export function stdTermRows() {
  if (typeof process !== 'undefined' && process.stdout && process.stdout.rows) {
    return process.stdout.rows;
  }
  return 24;
}

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
