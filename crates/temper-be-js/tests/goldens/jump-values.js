import {
  isBubble,
  listedGet,
  stringSplit,
  stringToInt32,
} from "@temperlang/core";

/**
 * @param {number} n
 * @returns {number}
 */
function step(n) {
  steps = (steps + 1) | 0;
  console.log(`step ${n}`);
  return n;
}

/**
 * @param {number} a
 * @param {number} b
 * @returns {number}
 */
function pair(a, b) {
  return (Math.imul(a, 10) + b) | 0;
}

/**
 * @param {string} s
 * @returns {number}
 */
function parse(s) {
  return stringToInt32(s, null);
}

/**
 * @param {number | null} y
 * @returns {number | null}
 */
function next(y) {
  /** @type {number} */
  let x;
  if (y == null) {
    return null;
  } else {
    x = y;
  }
  return (x + 1) | 0;
}

/**
 * @param {readonly (number | null)[]} ys
 * @returns {number}
 */
function sumPresent(ys) {
  let sum = 0;
  {
    let i = 0;
    while (i < ys.length) {
      const t$1 = sum;
      /** @type {number} */
      let t$0;
      {
        /** @type {number} */
        let t;
        const subject = listedGet(/** @type {(number | null)[]} */ (ys), i);
        if (subject == null) {
          {
            i = (i + 1) | 0;
            continue;
          }
        } else {
          t = subject;
        }
        t$0 = t;
      }
      sum = (t$1 + t$0) | 0;
      i = (i + 1) | 0;
    }
  }
  return sum;
}

/**
 * @param {readonly string[]} words
 * @returns {number}
 */
function sumUntilBad(words) {
  let sum = 0;
  let i = 0;
  while (i < words.length) {
    const t$3 = sum;
    /** @type {number} */
    let t$2;
    {
      /** @type {number} */
      let t;
      /** @type {number | null} */
      let held = null;
      try {
        held = parse(listedGet(/** @type {string[]} */ (words), i));
      } catch (e$) {
        if (!isBubble(e$)) throw e$;
      }
      if (held == null) {
        break;
      } else {
        t = held;
      }
      t$2 = t;
    }
    sum = (t$3 + t$2) | 0;
    i = (i + 1) | 0;
  }
  return sum;
}

/**
 * @param {string} s
 * @returns {number}
 */
function parsedOr(s) {
  /** @type {number} */
  let t$4;
  {
    /** @type {number} */
    let t;
    /** @type {number | null} */
    let held = null;
    try {
      held = parse(s);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
    }
    if (held == null) {
      return -1;
    } else {
      t = held;
    }
    t$4 = t;
  }
  return t$4;
}

/**
 * @param {number} i
 * @returns {number | null}
 */
function after(i) {
  if (i < 3) {
    return (i + 1) | 0;
  } else {
    return null;
  }
}

/** @returns {string} */
function stepped() {
  let seen = "";
  {
    let i = 0;
    while (i < 6) {
      if (i === 1) {
        {
          i = after(i) ?? 9;
          continue;
        }
      }
      seen = `${seen}${i}`;
      i = after(i) ?? 9;
    }
  }
  return seen;
}

/**
 * @param {number | null} y
 * @returns {number}
 */
function paired(y) {
  const t$6 = step(1);
  /** @type {number} */
  let t$5;
  {
    /** @type {number} */
    let t;
    if (y == null) {
      return -2;
    } else {
      t = y;
    }
    t$5 = t;
  }
  return pair(t$6, t$5);
}

/**
 * @param {number | null} y
 * @param {number | null} z
 * @returns {readonly number[]}
 */
function listed(y, z) {
  /** @type {number} */
  let t$7;
  {
    /** @type {number} */
    let t;
    if (y == null) {
      return Object.freeze([]);
    } else {
      t = y;
    }
    t$7 = t;
  }
  const t$9 = t$7;
  /** @type {number} */
  let t$8;
  {
    /** @type {number} */
    let t2;
    if (z == null) {
      return Object.freeze([9]);
    } else {
      t2 = z;
    }
    t$8 = t2;
  }
  return Object.freeze([1, t$9, 3, t$8]);
}

/**
 * @param {number | null} y
 * @returns {string}
 */
function shown(y) {
  /** @type {number} */
  let t$10;
  {
    /** @type {number} */
    let t;
    if (y == null) {
      return "none";
    } else {
      t = y;
    }
    t$10 = t;
  }
  return `y=${t$10}`;
}

/**
 * @param {boolean} t
 * @param {number | null} y
 * @returns {string}
 */
function both(t, y) {
  /** @type {boolean} */
  let ok;
  if (t && y != null) {
    ok = true;
  } else {
    return "no";
  }
  return `ok ${ok}`;
}

/**
 * @param {number} n
 * @returns {string}
 */
function sign(n) {
  /** @type {number} */
  let t$11;
  {
    /** @type {number} */
    let t;
    if (n > 0) {
      t = 1;
    } else if (n < 0) {
      t = -1;
    } else {
      return "zero";
    }
    t$11 = t;
  }
  return pair(0, t$11).toString();
}

/** @param {string | null} text */
function firstWord(text) {
  /** @type {string} */
  let t;
  if (text == null) {
    return;
  } else {
    t = text;
  }
  console.log(`first ${listedGet(/** @type {string[]} */ (stringSplit(t, " ")), 0)}`);
}

/** @returns {number} */
function early() {
  return 7;
}

/** @returns {number} */
function partly() {
  step(2);
  return step(3);
}

/**
 * @param {readonly (number | null)[]} ys
 * @returns {string}
 */
function doJumps(ys) {
  let seen = "";
  {
    let i = 0;
    while (i < ys.length) {
      /** @type {number} */
      let v;
      const subject = listedGet(/** @type {(number | null)[]} */ (ys), i);
      if (subject == null) {
        seen = `${seen}a`;
        if (i === 1) {
          {
            i = (i + 1) | 0;
            continue;
          }
        }
        seen = `${seen}b`;
        break;
      } else {
        v = subject;
      }
      seen = `${seen}${v}`;
      i = (i + 1) | 0;
    }
  }
  return seen;
}

/**
 * @param {number | null} y
 * @param {number} z
 * @returns {string}
 */
function doReturn(y, z) {
  /** @type {number} */
  let t$13;
  if (y == null) {
    /** @type {number} */
    let t$12;
    {
      if (z > 0) {
        if (z > 5) {
          return "big";
        }
        return "pos";
      }
      t$12 = 0;
    }
    t$13 = t$12;
  } else {
    t$13 = y;
  }
  const v = t$13;
  return `v${v}`;
}

/**
 * @param {number} a
 * @param {number} b
 * @param {number} c
 * @returns {number}
 */
function trio(a, b, c) {
  return (((a + b) | 0) + c) | 0;
}

/**
 * @param {readonly (number | null)[]} ys
 * @returns {number}
 */
function ordered(ys) {
  let sum = 0;
  {
    let i = 0;
    while (i < ys.length) {
      const t$16 = sum;
      const t$15 = step((10 + i) | 0);
      /** @type {number} */
      let t$14;
      {
        /** @type {number} */
        let t;
        const subject = listedGet(/** @type {(number | null)[]} */ (ys), i);
        if (subject == null) {
          step((20 + i) | 0);
          {
            i = (i + 1) | 0;
            continue;
          }
        } else {
          t = subject;
        }
        t$14 = t;
      }
      sum = (t$16 + trio(t$15, t$14, step((30 + i) | 0))) | 0;
      i = (i + 1) | 0;
    }
  }
  return sum;
}

/**
 * @param {readonly (number | null)[]} xs
 * @param {readonly (number | null)[]} ys
 * @returns {string}
 */
function nested(xs, ys) {
  let seen = "";
  {
    {
      let i = 0;
      while (i < xs.length) {
        /** @type {number} */
        let v;
        const subject = listedGet(/** @type {(number | null)[]} */ (xs), i);
        if (subject == null) {
          const subject2 = listedGet(/** @type {(number | null)[]} */ (ys), i);
          if (subject2 == null) {
            break;
          } else {
            v = subject2;
          }
        } else {
          v = subject;
        }
        seen = `${seen}${v}`;
        i = (i + 1) | 0;
      }
    }
  }
  return seen;
}

/**
 * @param {readonly (readonly (number | null)[])[]} rows
 * @returns {string}
 */
function gridded(rows) {
  let seen = "";
  {
    {
      let i = 0;
      outer: while (i < rows.length) {
        /** @type {readonly (number | null)[]} */
        const row = listedGet(/** @type {(readonly (number | null)[])[]} */ (rows), i);
        {
          let j = 0;
          while (j < row.length) {
            /** @type {number} */
            let v;
            const subject = listedGet(/** @type {(number | null)[]} */ (row), j);
            if (subject == null) {
              break outer;
            } else {
              v = subject;
            }
            /** @type {number} */
            let w;
            const subject2 = v < 0 ? null : v;
            if (subject2 == null) {
              seen = `${seen}-`;
              {
                i = (i + 1) | 0;
                continue outer;
              }
            } else {
              w = subject2;
            }
            seen = `${seen}${w}`;
            j = (j + 1) | 0;
          }
        }
        seen = `${seen}|`;
        i = (i + 1) | 0;
      }
    }
  }
  return seen;
}

/**
 * @param {readonly (readonly (number | null)[])[]} rows
 * @param {readonly (number | null)[]} keys
 * @returns {string}
 */
function firstBig(rows, keys) {
  let seen = "";
  {
    {
      let i = 0;
      outer: while (i < rows.length) {
        /** @type {number} */
        let t$18;
        {
          const subject = listedGet(/** @type {(number | null)[]} */ (keys), i);
          /** @type {number} */
          let t$20;
          if (subject == null) {
            /** @type {number} */
            let t$19;
            {
              /** @type {readonly (number | null)[]} */
              const row = listedGet(/** @type {(readonly (number | null)[])[]} */ (rows), i);
              let first = -1;
              {
                let j = 0;
                while (j < row.length) {
                  /** @type {number} */
                  let v;
                  const subject2 = listedGet(/** @type {(number | null)[]} */ (row), j);
                  if (subject2 == null) {
                    {
                      i = (i + 1) | 0;
                      continue outer;
                    }
                  } else {
                    v = subject2;
                  }
                  if (v > 5) {
                    break outer;
                  }
                  if (v > 2) {
                    first = v;
                    break;
                  }
                  j = (j + 1) | 0;
                }
              }
              t$19 = first;
            }
            t$20 = t$19;
          } else {
            t$20 = subject;
          }
          t$18 = t$20;
        }
        const k = t$18;
        seen = `${seen}${k},`;
        i = (i + 1) | 0;
      }
    }
  }
  return seen;
}

/**
 * @param {readonly string[]} words
 * @returns {string}
 */
function viaOrelse(words) {
  let seen = "";
  {
    let i = 0;
    while (i < words.length) {
      /** @type {number} */
      let n;
      /** @type {number | null} */
      let held = null;
      try {
        held = parse(listedGet(/** @type {string[]} */ (words), i));
      } catch (e$) {
        if (!isBubble(e$)) throw e$;
      }
      if (held == null) {
        seen = `${seen}!`;
        if (i > 2) {
          break;
        }
        {
          i = (i + 1) | 0;
          continue;
        }
      } else {
        n = held;
      }
      seen = `${seen}${n}`;
      i = (i + 1) | 0;
    }
  }
  return seen;
}

/**
 * @param {number} n
 * @returns {string}
 */
function viaIf(n) {
  /** @type {string} */
  let t$22;
  if (n > 0) {
    t$22 = "pos";
  } else {
    /** @type {string} */
    let t$21;
    {
      if (n < -5) {
        return "low";
      }
      t$21 = "neg";
    }
    t$22 = t$21;
  }
  const s = t$22;
  return `s=${s}`;
}

let steps = 0;
/** @type {string} */
let t$23;
{
  const part = next(1);
  t$23 = part == null ? "null" : part.toString();
}
/** @type {string} */
let t$24;
{
  const part2 = next(null);
  t$24 = part2 == null ? "null" : part2.toString();
}
console.log(`${t$23} ${t$24}`);
console.log(`${sumPresent(Object.freeze([1, null, 3]))} ${sumUntilBad(Object.freeze(["1", "2", "x", "4"]))}`);
console.log(`${parsedOr("12")} ${parsedOr("x")} ${stepped()}`);
console.log(`${paired(3)} ${paired(null)}`);
console.log(`${listed(2, 4).length} ${listed(null, 4).length} ${listedGet(/** @type {number[]} */ (listed(2, null)), 0)}`);
console.log(`${shown(5)} ${shown(null)}`);
console.log(`${both(true, 1)} ${both(true, null)} ${both(false, 1)}`);
console.log(`${sign(4)} ${sign(-4)} ${sign(0)}`);
firstWord("hello there");
firstWord(null);
console.log(`${doJumps(Object.freeze([1, null, 2, null, 3]))} ${doReturn(1, 0)} ${doReturn(null, 9)} ${doReturn(null, 1)} ${doReturn(null, 0)}`);
console.log(ordered(Object.freeze([1, null])).toString());
console.log(`${nested(Object.freeze([1, null, null, 4]), Object.freeze([0, 7, null, 0]))} ${viaOrelse(Object.freeze(["1", "x", "2", "y", "z", "3"]))}`);
console.log(`${gridded(Object.freeze([Object.freeze([1, 2]), Object.freeze([3, -1, 4]), Object.freeze([5, null, 6]), Object.freeze([7])]))} ${firstBig(Object.freeze([Object.freeze([1, 3]), Object.freeze([null, 1]), Object.freeze([4]), Object.freeze([9]), Object.freeze([1])]), Object.freeze([7, null, null, null, null]))}`);
console.log(`${viaIf(1)} ${viaIf(-1)} ${viaIf(-9)}`);
console.log(`${early()} ${partly()} ${steps}`);
