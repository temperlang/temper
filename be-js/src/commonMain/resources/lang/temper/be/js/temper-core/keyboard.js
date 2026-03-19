/**
 * @returns {Promise<string | null>}
 */
export function stdNextKeypress() {
  return new Promise(resolve => {
    if (typeof process !== 'undefined' && process.stdin && process.stdin.isTTY) {
      process.stdin.resume();
      process.stdin.setEncoding('utf8');
      process.stdin.setRawMode(true);
      process.stdin.once('data', data => {
        process.stdin.setRawMode(false);
        process.stdin.pause();
        const str = data.toString();
        if (str === '\x1b[A') resolve("ArrowUp");
        else if (str === '\x1b[B') resolve("ArrowDown");
        else if (str === '\x1b[C') resolve("ArrowRight");
        else if (str === '\x1b[D') resolve("ArrowLeft");
        else if (str === '\x1b') resolve("Escape");
        else if (str === '\r' || str === '\n') resolve("Enter");
        else resolve(str);
      });
    } else {
      resolve(null);
    }
  });
}
