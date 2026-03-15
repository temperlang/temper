import { empty } from "./core.js";

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
      process.stdin.resume();
      process.stdin.setEncoding('utf8');
      if (process.stdin.isTTY && process.stdin.setRawMode) {
        process.stdin.setRawMode(true);
      }
      process.stdin.once('data', data => {
        const str = data.toString();
        // Ctrl+C in raw mode
        if (str === '\x03') {
          process.exit();
        }
        resolve(str.trim());
      });
    } else {
      resolve(null);
    }
  });
}
